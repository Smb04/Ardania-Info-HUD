package de.ardania.hud;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public void notifyStatUpdated() {
        this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
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
