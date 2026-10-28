package de.ardania.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArdaniaState {

    private static final ArdaniaState INSTANCE = new ArdaniaState();
    private static final DecimalFormatSymbols GERMAN_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.GERMANY);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class CacheData {
        String euronen;
        boolean euronenKnown;
        long ardaPoints;
        boolean ardaPointsKnown;
        List<JobEntry> jobs = new ArrayList<>();
        Map<String, Long> serverPlaytimes = new LinkedHashMap<>();
    }

    public record JobEntry(String name, int level, boolean isMax) {
        public JobEntry(String name, int level) {
            this(name, level, level >= 30);
        }

        public String displayLevel() {
            return (isMax || level >= 30) ? "MAX" : String.valueOf(level);
        }
    }

    private BigDecimal euronen;
    private boolean euronenKnown;
    private long ardaPoints;
    private boolean ardaPointsKnown;

    private final Map<String, JobEntry> jobs = new LinkedHashMap<>();
    private final Map<String, Long> serverPlaytimes = new LinkedHashMap<>();
    private long basePlaytimeMinutes = -1;
    private long playtimeBaseTimestamp = -1;

    private volatile long lastNonPlaytimeUpdateTimestamp = 0;
    private volatile long lastManualHideTimestamp = 0;

    public ArdaniaState() {
        reset();
    }

    public static ArdaniaState getInstance() {
        return INSTANCE;
    }

    public synchronized void setEuronen(BigDecimal amount) {
        BigDecimal scaled = scale(amount);
        boolean changed = !this.euronenKnown || this.euronen.compareTo(scaled) != 0;
        this.euronen = scaled;
        this.euronenKnown = true;
        if (changed) {
            this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
        }
    }

    public synchronized void addEuronen(BigDecimal delta) {
        this.euronen = scale(this.euronen.add(delta));
        this.euronenKnown = true;
        this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
    }

    public synchronized BigDecimal getEuronen() {
        return this.euronen;
    }

    public synchronized boolean isEuronenKnown() {
        return this.euronenKnown;
    }

    public synchronized String getFormattedEuronen() {
        return formatMoney(this.euronen);
    }

    public synchronized void setArdaPoints(long points) {
        long sanitized = Math.max(0, points);
        boolean changed = !this.ardaPointsKnown || this.ardaPoints != sanitized;
        this.ardaPoints = sanitized;
        this.ardaPointsKnown = true;
        if (changed) {
            this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
        }
    }

    public synchronized void addArdaPoints(long points) {
        this.ardaPoints = Math.max(0, this.ardaPoints + points);
        this.ardaPointsKnown = true;
        this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
    }

    public synchronized long getArdaPoints() {
        return this.ardaPoints;
    }

    public synchronized boolean isArdaPointsKnown() {
        return this.ardaPointsKnown;
    }

    public synchronized String getFormattedArdaPoints() {
        return this.ardaPointsKnown ? formatInteger(this.ardaPoints) : "?";
    }

    public synchronized void addOrUpdateJob(String jobName, int level) {
        addOrUpdateJob(jobName, level, level >= 30);
    }

    public synchronized void addOrUpdateJob(String jobName, int level, boolean isMax) {
        if (jobName == null || jobName.isBlank()) return;
        String trimmed = jobName.trim();
        int sanitizedLevel = Math.max(0, level);
        boolean max = isMax || sanitizedLevel >= 30;
        JobEntry entry = new JobEntry(trimmed, sanitizedLevel, max);
        JobEntry previous = this.jobs.put(trimmed, entry);
        if (!entry.equals(previous)) {
            this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
        }
    }

    public synchronized void clearJobs() {
        this.jobs.clear();
    }

    public synchronized List<JobEntry> getJobs() {
        return Collections.unmodifiableList(new ArrayList<>(this.jobs.values()));
    }

    public long getLastNonPlaytimeUpdateTimestamp() {
        return this.lastNonPlaytimeUpdateTimestamp;
    }

    public long getLastManualHideTimestamp() {
        return this.lastManualHideTimestamp;
    }

    public void notifyStatUpdated() {
        this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
    }

    public void hideHudNow() {
        this.lastNonPlaytimeUpdateTimestamp = 0;
        this.lastManualHideTimestamp = System.currentTimeMillis();
    }

    public boolean isAutoHideActive(long now, long displayDurationMs) {
        long lastUpdate = this.lastNonPlaytimeUpdateTimestamp;
        return lastUpdate > 0 && (now - lastUpdate < displayDurationMs);
    }

    public synchronized void updateServerPlaytime(String serverName, long days, long hours, long minutes) {
        if (serverName == null || serverName.isBlank()) return;
        long serverMinutes = (days * 24 * 60) + (hours * 60) + minutes;
        this.serverPlaytimes.put(serverName.trim().toLowerCase(Locale.ROOT), serverMinutes);

        long totalSum = 0;
        for (long mins : this.serverPlaytimes.values()) {
            totalSum += mins;
        }
        this.basePlaytimeMinutes = totalSum;
        this.playtimeBaseTimestamp = System.currentTimeMillis();
    }

    public synchronized void clearServerPlaytimes() {
        this.serverPlaytimes.clear();
        this.basePlaytimeMinutes = -1;
        this.playtimeBaseTimestamp = -1;
    }

    public synchronized boolean isPlaytimeKnown() {
        return this.basePlaytimeMinutes >= 0;
    }

    public synchronized long getCurrentTotalMinutes() {
        if (this.basePlaytimeMinutes < 0) {
            return -1;
        }
        if (this.playtimeBaseTimestamp <= 0) {
            return this.basePlaytimeMinutes;
        }
        long elapsedMinutes = (System.currentTimeMillis() - this.playtimeBaseTimestamp) / 60_000L;
        return this.basePlaytimeMinutes + Math.max(0, elapsedMinutes);
    }

    public synchronized String getFormattedPlaytime() {
        long totalMin = getCurrentTotalMinutes();
        if (totalMin < 0) {
            return "?";
        }
        long days = totalMin / (24 * 60);
        long hours = (totalMin % (24 * 60)) / 60;
        long minutes = totalMin % 60;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    public synchronized void reset() {
        this.euronen = scale(BigDecimal.ZERO);
        this.euronenKnown = false;
        this.ardaPoints = 0;
        this.ardaPointsKnown = false;
        this.jobs.clear();
        this.serverPlaytimes.clear();
        this.basePlaytimeMinutes = -1;
        this.playtimeBaseTimestamp = -1;
        this.lastNonPlaytimeUpdateTimestamp = 0;
        this.lastManualHideTimestamp = 0;
    }

    public static Path getDefaultCachePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("ardania_hud_cache.json");
    }

    public synchronized void saveCache() {
        try {
            saveCache(getDefaultCachePath());
        } catch (Exception e) {
            ArdaniaClientMod.LOGGER.warn("Konnte HUD-Cache nicht speichern.", e);
        }
    }

    public synchronized void saveCache(Path cachePath) {
        if (cachePath == null) {
            return;
        }
        CacheData data = new CacheData();
        data.euronen = this.euronen != null ? this.euronen.toPlainString() : "0.00";
        data.euronenKnown = this.euronenKnown;
        data.ardaPoints = this.ardaPoints;
        data.ardaPointsKnown = this.ardaPointsKnown;
        data.jobs = new ArrayList<>(this.jobs.values());
        data.serverPlaytimes = new LinkedHashMap<>(this.serverPlaytimes);

        try {
            Path parent = cachePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(cachePath, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte HUD-Cache nicht speichern.", e);
        }
    }

    public synchronized void loadCache() {
        try {
            loadCache(getDefaultCachePath());
        } catch (Exception e) {
            ArdaniaClientMod.LOGGER.warn("Konnte HUD-Cache nicht laden.", e);
        }
    }

    public synchronized void loadCache(Path cachePath) {
        if (cachePath == null || !Files.exists(cachePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
            CacheData data = GSON.fromJson(reader, CacheData.class);
            if (data == null) {
                return;
            }
            if (data.euronenKnown && data.euronen != null) {
                try {
                    this.euronen = scale(new BigDecimal(data.euronen));
                    this.euronenKnown = true;
                } catch (NumberFormatException ignored) {
                }
            }
            if (data.ardaPointsKnown) {
                this.ardaPoints = Math.max(0, data.ardaPoints);
                this.ardaPointsKnown = true;
            }
            if (data.jobs != null && !data.jobs.isEmpty()) {
                this.jobs.clear();
                for (JobEntry entry : data.jobs) {
                    if (entry != null && entry.name() != null && !entry.name().isBlank()) {
                        String trimmed = entry.name().trim();
                        int sanitizedLevel = Math.max(0, entry.level());
                        boolean max = entry.isMax() || sanitizedLevel >= 30;
                        this.jobs.put(trimmed, new JobEntry(trimmed, sanitizedLevel, max));
                    }
                }
            }
            if (data.serverPlaytimes != null && !data.serverPlaytimes.isEmpty()) {
                this.serverPlaytimes.clear();
                long totalSum = 0;
                for (Map.Entry<String, Long> entry : data.serverPlaytimes.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && entry.getValue() >= 0) {
                        String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
                        long mins = entry.getValue();
                        this.serverPlaytimes.put(key, mins);
                        totalSum += mins;
                    }
                }
                if (!this.serverPlaytimes.isEmpty()) {
                    this.basePlaytimeMinutes = totalSum;
                    if (this.playtimeBaseTimestamp <= 0) {
                        this.playtimeBaseTimestamp = System.currentTimeMillis();
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte HUD-Cache nicht laden.", e);
        }
    }

    public synchronized void onServerJoin() {
        loadCache();
        if (this.basePlaytimeMinutes >= 0) {
            this.playtimeBaseTimestamp = System.currentTimeMillis();
        }
        notifyStatUpdated();
    }

    public synchronized void onServerDisconnect() {
        saveCache();
        this.playtimeBaseTimestamp = -1;
        this.lastNonPlaytimeUpdateTimestamp = 0;
        this.lastManualHideTimestamp = 0;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static String formatMoney(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", GERMAN_SYMBOLS);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    public static String formatInteger(long value) {
        return new DecimalFormat("#,##0", GERMAN_SYMBOLS).format(value);
    }

    public static BigDecimal parseGermanNumber(String raw) {
        String clean = raw.replaceAll("[\\s\\u00A0\\u202F]", "").trim();

        if (clean.contains(".") && clean.contains(",")) {
            clean = clean.replace(".", "").replace(',', '.');
        } else if (clean.contains(",")) {
            clean = clean.replace(',', '.');
        } else if (clean.contains(".")) {
            int firstDot = clean.indexOf('.');
            int lastDot = clean.lastIndexOf('.');
            if (firstDot != lastDot) {
                clean = clean.replace(".", "");
            } else {
                int decimals = clean.length() - lastDot - 1;
                if (decimals == 3 && !clean.startsWith("0")) {
                    clean = clean.replace(".", "");
                }
            }
        }

        return new BigDecimal(clean);
    }
}
