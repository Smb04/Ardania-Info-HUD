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

/**
 * Thread-sicherer zentraler Zustandsspeicher fuer alle Ardania-spezifischen Daten.
 *
 * <p>Verwaltet:</p>
 * <ul>
 *   <li>Euronen (Kontostand mit exakter {@link BigDecimal}-Arithmetik)</li>
 *   <li>ArdaPoints (Punkte und Voteziel-Gutschriften)</li>
 *   <li>Jobs & Berufsfortschritt (inkl. Prozent- und XP-Werte sowie Level 30 [MAX])</li>
 *   <li>Server-Spielzeiten (aufgeteilt nach Hauptserver, Mine, Event, Creative)</li>
 *   <li>Sitzungsstatistiken (Netto-Gewinn, Stundenlohn und Sitzungsdauer)</li>
 *   <li>Persistenter JSON-Zustands-Cache zur sofortigen Anzeige bei Spielstart</li>
 * </ul>
 */
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

    public record JobEntry(String name, int level, boolean isMax, double currentXp, double maxXp) {
        public JobEntry(String name, int level) {
            this(name, level, level >= 30, 0.0, 0.0);
        }

        public JobEntry(String name, int level, boolean isMax) {
            this(name, level, isMax, 0.0, 0.0);
        }

        public String displayLevel() {
            return (isMax || level >= 30) ? "MAX" : String.valueOf(level);
        }

        public boolean hasXpInfo() {
            return maxXp > 0.0 && currentXp >= 0.0 && !isMax && level < 30;
        }

        public int xpPercent() {
            if (!hasXpInfo()) return 0;
            return (int) Math.round((currentXp / maxXp) * 100.0);
        }

        public String formatBadge(boolean showXp) {
            if (isMax || level >= 30) {
                return "[MAX]";
            }
            if (showXp && hasXpInfo()) {
                return xpPercent() + "% [" + level + "]";
            }
            return "[" + level + "]";
        }
    }

    public record ServerPlaytimeEntry(String displayName, String formattedTime) {
    }

    private BigDecimal euronen;
    private boolean euronenKnown;
    private long ardaPoints;
    private boolean ardaPointsKnown;

    private final Map<String, JobEntry> jobs = new LinkedHashMap<>();
    private final Map<String, Long> serverPlaytimes = new LinkedHashMap<>();
    private long basePlaytimeMinutes = -1;
    private long playtimeBaseTimestamp = -1;
    private long sessionStartTimestamp = -1;
    private BigDecimal sessionStartEuronen = null;
    private long sessionStartPoints = -1;

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
        if (this.sessionStartEuronen == null) {
            this.sessionStartEuronen = scaled;
        }
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
        if (this.sessionStartPoints < 0) {
            this.sessionStartPoints = sanitized;
        }
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
        addOrUpdateJob(jobName, level, level >= 30, 0.0, 0.0);
    }

    public synchronized void addOrUpdateJob(String jobName, int level, boolean isMax) {
        addOrUpdateJob(jobName, level, isMax, 0.0, 0.0);
    }

    public synchronized void addOrUpdateJob(String jobName, int level, boolean isMax, double currentXp, double maxXp) {
        if (jobName == null || jobName.isBlank()) return;
        String trimmed = jobName.trim();
        int sanitizedLevel = Math.max(0, level);
        boolean max = isMax || sanitizedLevel >= 30;
        JobEntry entry = new JobEntry(trimmed, sanitizedLevel, max, currentXp, maxXp);
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
        this.serverPlaytimes.put(normalizeServerKey(serverName), Math.max(0L, serverMinutes));

        refreshPlaytimeTotals();
    }

    /**
     * Ersetzt die komplette Spielzeit-Tabelle durch einen vollstaendigen
     * {@code /ontime}-Bericht.
     *
     * <p>Im Gegensatz zu {@link #updateServerPlaytime(String, long, long, long)}
     * werden hier veraltete oder fremde Schluessel (z. B. ein ehemaliger
     * {@code skyblock}-Eintrag) entfernt. Nur so kann eine zuvor durch fremde
     * {@code /ontime}-Abfragen aufgeblaehte Summe wieder sinken.</p>
     *
     * @param playtimes vollstaendige Zuordnung Servername -&gt; Minuten
     */
    public synchronized void replaceServerPlaytimes(Map<String, Long> playtimes) {
        this.serverPlaytimes.clear();
        if (playtimes != null) {
            for (Map.Entry<String, Long> entry : playtimes.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = normalizeServerKey(entry.getKey());
                if (key.isEmpty()) {
                    continue;
                }
                this.serverPlaytimes.put(key, Math.max(0L, entry.getValue()));
            }
        }
        if (this.serverPlaytimes.isEmpty()) {
            this.basePlaytimeMinutes = -1;
            this.playtimeBaseTimestamp = -1;
        } else {
            refreshPlaytimeTotals();
        }
    }

    private void refreshPlaytimeTotals() {
        if (hasSupportedPlaytimeKey()) {
            this.basePlaytimeMinutes = sumSupportedPlaytimeMinutes();
            this.playtimeBaseTimestamp = System.currentTimeMillis();
        } else {
            this.basePlaytimeMinutes = -1;
            this.playtimeBaseTimestamp = -1;
        }
    }

    private boolean hasSupportedPlaytimeKey() {
        for (String key : this.serverPlaytimes.keySet()) {
            if (isSupportedServerKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeServerKey(String serverName) {
        return serverName == null ? "" : serverName.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Summiert nur die vom HUD unterstuetzten Welten. Weitere Welten (z. B.
     * {@code Skyblock}) duerfen die Gesamtspielzeit nicht verfaelschen.
     */
    private long sumSupportedPlaytimeMinutes() {
        long total = 0;
        for (Map.Entry<String, Long> entry : this.serverPlaytimes.entrySet()) {
            if (isSupportedServerKey(entry.getKey()) && entry.getValue() != null) {
                total += Math.max(0L, entry.getValue());
            }
        }
        return total;
    }

    private static boolean isSupportedServerKey(String key) {
        return "hauptserver".equals(key)
                || "creative".equals(key)
                || "eventserver".equals(key)
                || "mine".equals(key);
    }

    public synchronized void clearServerPlaytimes() {
        this.serverPlaytimes.clear();
        this.basePlaytimeMinutes = -1;
        this.playtimeBaseTimestamp = -1;
    }

    public synchronized boolean isPlaytimeKnown() {
        return this.basePlaytimeMinutes >= 0;
    }

    private long getElapsedMinutesSinceSync() {
        if (this.playtimeBaseTimestamp <= 0) {
            return 0;
        }
        long elapsedMinutes = (System.currentTimeMillis() - this.playtimeBaseTimestamp) / 60_000L;
        return Math.max(0, elapsedMinutes);
    }

    public synchronized long getCurrentTotalMinutes() {
        if (this.basePlaytimeMinutes < 0) {
            return -1;
        }
        return this.basePlaytimeMinutes + getElapsedMinutesSinceSync();
    }

    public synchronized long getFilteredTotalMinutes(
            boolean includeHauptserver,
            boolean includeCreative,
            boolean includeEventserver,
            boolean includeMine
    ) {
        if (this.basePlaytimeMinutes < 0) {
            return -1;
        }
        if (!includeHauptserver && !includeCreative && !includeEventserver && !includeMine) {
            return 0;
        }
        long sum = 0;
        for (Map.Entry<String, Long> entry : this.serverPlaytimes.entrySet()) {
            String key = entry.getKey();
            long mins = entry.getValue() != null ? entry.getValue() : 0L;
            if (isServerIncluded(key, includeHauptserver, includeCreative, includeEventserver, includeMine)) {
                sum += mins;
            }
        }
        return sum + getElapsedMinutesSinceSync();
    }

    private static boolean isServerIncluded(
            String key,
            boolean includeHauptserver,
            boolean includeCreative,
            boolean includeEventserver,
            boolean includeMine
    ) {
        return switch (key) {
            case "hauptserver" -> includeHauptserver;
            case "creative" -> includeCreative;
            case "eventserver" -> includeEventserver;
            case "mine" -> includeMine;
            default -> false;
        };
    }

    public synchronized String getFormattedPlaytime() {
        return formatMinutes(getCurrentTotalMinutes());
    }

    public synchronized String getFormattedPlaytime(ArdaniaConfig config) {
        if (config == null) {
            return getFormattedPlaytime();
        }
        long mins = getFilteredTotalMinutes(
                config.playtimeHauptserver,
                config.playtimeCreative,
                config.playtimeEventserver,
                config.playtimeMine
        );
        return formatMinutes(mins);
    }

    public synchronized List<ServerPlaytimeEntry> getSelectedServerPlaytimes(ArdaniaConfig config) {
        List<ServerPlaytimeEntry> result = new ArrayList<>();
        boolean known = isPlaytimeKnown();

        if (config.playtimeHauptserver) {
            String val = known ? formatMinutes(this.serverPlaytimes.getOrDefault("hauptserver", 0L)) : "?";
            result.add(new ServerPlaytimeEntry("Hauptserver", val));
        }
        if (config.playtimeCreative) {
            String val = known ? formatMinutes(this.serverPlaytimes.getOrDefault("creative", 0L)) : "?";
            result.add(new ServerPlaytimeEntry("Creative", val));
        }
        if (config.playtimeEventserver) {
            String val = known ? formatMinutes(this.serverPlaytimes.getOrDefault("eventserver", 0L)) : "?";
            result.add(new ServerPlaytimeEntry("Eventserver", val));
        }
        if (config.playtimeMine) {
            String val = known ? formatMinutes(this.serverPlaytimes.getOrDefault("mine", 0L)) : "?";
            result.add(new ServerPlaytimeEntry("Mine", val));
        }
        return result;
    }

    public static String formatMinutes(long totalMin) {
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

    public synchronized void startSession(long now) {
        this.sessionStartTimestamp = now;
        if (this.euronenKnown) {
            this.sessionStartEuronen = this.euronen;
        }
        if (this.ardaPointsKnown) {
            this.sessionStartPoints = this.ardaPoints;
        }
    }

    public synchronized BigDecimal getSessionEuronenDelta() {
        if (!this.euronenKnown || this.sessionStartEuronen == null) {
            return scale(BigDecimal.ZERO);
        }
        return scale(this.euronen.subtract(this.sessionStartEuronen));
    }

    public synchronized long getSessionPointsDelta() {
        if (!this.ardaPointsKnown || this.sessionStartPoints < 0) {
            return 0L;
        }
        return this.ardaPoints - this.sessionStartPoints;
    }

    public synchronized String getFormattedSessionStats(long now) {
        return getFormattedSessionStats(now, false);
    }

    public synchronized String getFormattedSessionStats(long now, boolean compact) {
        BigDecimal delta = getSessionEuronenDelta();
        String sign = delta.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        String deltaStr = sign + (compact ? formatCompactMoney(delta) : formatMoney(delta));

        long durationMs = now - this.sessionStartTimestamp;
        if (durationMs >= 60_000L) {
            double hours = durationMs / 3_600_000.0;
            BigDecimal perHour = scale(delta.divide(BigDecimal.valueOf(hours), 2, RoundingMode.HALF_UP));
            String signPerHour = perHour.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            String perHourStr = compact ? formatCompactMoney(perHour) : formatMoney(perHour);
            return deltaStr + " (" + signPerHour + perHourStr + "/h)";
        }
        return deltaStr;
    }

    public synchronized String getFormattedSessionTime(long now) {
        if (this.sessionStartTimestamp <= 0) {
            this.sessionStartTimestamp = now;
        }
        long totalSeconds = Math.max(0L, (now - this.sessionStartTimestamp) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        return minutes + "m " + seconds + "s";
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
        this.sessionStartTimestamp = -1;
        this.sessionStartEuronen = null;
        this.sessionStartPoints = -1;
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
                        this.jobs.put(trimmed, new JobEntry(trimmed, sanitizedLevel, max, entry.currentXp(), entry.maxXp()));
                    }
                }
            }
            if (data.serverPlaytimes != null && !data.serverPlaytimes.isEmpty()) {
                this.serverPlaytimes.clear();
                for (Map.Entry<String, Long> entry : data.serverPlaytimes.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && entry.getValue() >= 0) {
                        String key = normalizeServerKey(entry.getKey());
                        if (!key.isEmpty()) {
                            this.serverPlaytimes.put(key, entry.getValue());
                        }
                    }
                }
                if (!this.serverPlaytimes.isEmpty()) {
                    refreshPlaytimeTotals();
                }
            }
        } catch (IOException | JsonParseException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte HUD-Cache nicht laden.", e);
        }
    }

    public synchronized void onServerJoin() {
        loadCache();
        long now = System.currentTimeMillis();
        if (this.basePlaytimeMinutes >= 0) {
            this.playtimeBaseTimestamp = now;
        }
        if (this.sessionStartTimestamp <= 0) {
            this.sessionStartTimestamp = now;
        }
        notifyStatUpdated();
    }

    public synchronized void onServerDisconnect() {
        saveCache();
        this.playtimeBaseTimestamp = -1;
        this.sessionStartTimestamp = -1;
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

    public static String formatCompactMoney(BigDecimal value) {
        if (value == null) return "0";
        double val = value.doubleValue();
        double abs = Math.abs(val);
        if (abs >= 1_000_000.0) {
            return String.format(Locale.GERMANY, "%.1fM", val / 1_000_000.0);
        } else if (abs >= 1_000.0) {
            return String.format(Locale.GERMANY, "%.1fk", val / 1_000.0);
        }
        return formatMoney(value);
    }

    public static double parseXpNumber(String raw) {
        if (raw == null || raw.isBlank()) return 0.0;
        String clean = raw.trim().replace(" ", "");
        if (clean.contains(",") && clean.contains(".")) {
            if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
                clean = clean.replace(".", "").replace(',', '.');
            } else {
                clean = clean.replace(",", "");
            }
        } else if (clean.contains(",")) {
            clean = clean.replace(',', '.');
        }
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
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
