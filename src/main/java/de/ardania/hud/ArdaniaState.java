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

/**
 * Thread-sicherer Zustand des Ardania-HUDs.
 */
public final class ArdaniaState {

    private static final ArdaniaState INSTANCE = new ArdaniaState();

    /** Deutsches Zahlenformat: Punkt als Tausendertrenner, Komma als Dezimaltrenner. */
    private static final DecimalFormatSymbols GERMAN_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.GERMANY);

    public record JobEntry(String name, int level) {}

    private BigDecimal euronen;
    private boolean euronenKnown;
    private long ardaPoints;
    private boolean ardaPointsKnown;
    
    // LinkedHashMap, um die Reihenfolge der Jobs beizubehalten
    private final Map<String, Integer> jobs = new LinkedHashMap<>();

    // Spielzeit-Tracking aller Server (Hauptserver, Mine, Creative, Eventserver etc.)
    private final Map<String, Long> serverPlaytimes = new LinkedHashMap<>();
    private long basePlaytimeMinutes = -1;
    private long playtimeBaseTimestamp = -1;

    /** Fuer Tests oeffentlich; im Spiel wird {@link #getInstance()} verwendet. */
    public ArdaniaState() {
        reset();
    }

    public static ArdaniaState getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ Euronen

    public synchronized void setEuronen(BigDecimal amount) {
        this.euronen = scale(amount);
        this.euronenKnown = true;
    }

    public synchronized void addEuronen(BigDecimal delta) {
        this.euronen = scale(this.euronen.add(delta));
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

    // ------------------------------------------------------------------ ArdaPoints

    public synchronized void setArdaPoints(long points) {
        this.ardaPoints = Math.max(0, points);
        this.ardaPointsKnown = true;
    }

    public synchronized void addArdaPoints(long points) {
        this.ardaPoints = Math.max(0, this.ardaPoints + points);
        this.ardaPointsKnown = true;
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

    // ------------------------------------------------------------------ Jobs

    public synchronized void addOrUpdateJob(String jobName, int level) {
        if (jobName == null || jobName.isBlank()) return;
        this.jobs.put(jobName.trim(), Math.max(0, level));
    }

    public synchronized void clearJobs() {
        this.jobs.clear();
    }

    public synchronized List<JobEntry> getJobs() {
        List<JobEntry> list = new ArrayList<>(this.jobs.size());
        for (Map.Entry<String, Integer> entry : this.jobs.entrySet()) {
            list.add(new JobEntry(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableList(list);
    }

    // ------------------------------------------------------------------ Spielzeit

    /**
     * Aktualisiert oder addiert die Spielzeit fuer eine bestimmte Welt/Server (z. B. "Hauptserver", "Mine", "Creative").
     */
    public synchronized void updateServerPlaytime(String serverName, long days, long hours, long minutes) {
        if (serverName == null || serverName.isBlank()) return;
        long serverMinutes = (days * 24 * 60) + (hours * 60) + minutes;
        this.serverPlaytimes.put(serverName.trim().toLowerCase(Locale.ROOT), serverMinutes);
        
        // Summe aller Server als Basis setzen
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

    /**
     * Berechnet die aktuelle Gesamtspielzeit (Basis + lokale Online-Zeit seit letztem Abgleich).
     */
    public synchronized long getCurrentTotalMinutes() {
        if (this.basePlaytimeMinutes < 0) {
            return -1;
        }
        long elapsedMinutes = (System.currentTimeMillis() - this.playtimeBaseTimestamp) / 60_000L;
        return this.basePlaytimeMinutes + Math.max(0, elapsedMinutes);
    }

    /**
     * Liefert eine kompakte formatierte Spielzeit z. B. "16d 3h 5m" oder "3h 12m".
     */
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

    // ------------------------------------------------------------------ Allgemein

    /** Setzt alles zurueck (z. B. beim Verlassen des Servers). */
    public synchronized void reset() {
        this.euronen = scale(BigDecimal.ZERO);
        this.euronenKnown = false;
        this.ardaPoints = 0;
        this.ardaPointsKnown = false;
        this.jobs.clear();
        this.serverPlaytimes.clear();
        this.basePlaytimeMinutes = -1;
        this.playtimeBaseTimestamp = -1;
    }

    // ------------------------------------------------------------------ Hilfsmethoden

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
