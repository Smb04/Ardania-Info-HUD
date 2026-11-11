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
 *   <li>Jobs &amp; Berufsfortschritt (inkl. Prozent- und XP-Werte sowie Level 30 [MAX])</li>
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

    /**
     * Unveraenderlicher Datensatz fuer einen einzelnen Beruf und dessen Fortschritt.
     *
     * @param name      Berufsname (z. B. {@code Miner})
     * @param level     Aktuelles Job-Level
     * @param isMax     {@code true}, wenn die Maximalstufe erreicht wurde
     * @param currentXp Aktuelle Erfahrungspunkte im laufenden Level
     * @param maxXp     Benoetigte Erfahrungspunkte fuer das naechste Level
     */
    public record JobEntry(String name, int level, boolean isMax, double currentXp, double maxXp) {
        /**
         * Erstellt einen Berufseintrag ohne explizite XP-Angaben.
         *
         * @param name  Berufsname
         * @param level Aktuelles Level (ab 30 automatisch {@code MAX})
         */
        public JobEntry(String name, int level) {
            this(name, level, level >= 30, 0.0, 0.0);
        }

        /**
         * Erstellt einen Berufseintrag mit explizitem Max-Flag, ohne XP-Angaben.
         *
         * @param name  Berufsname
         * @param level Aktuelles Level
         * @param isMax Ob die Maximalstufe erreicht wurde
         */
        public JobEntry(String name, int level, boolean isMax) {
            this(name, level, isMax, 0.0, 0.0);
        }

        /**
         * Formatiert das Level oder {@code "MAX"} fuer die Anzeige.
         *
         * @return Anzeigetext fuer das Job-Level
         */
        public String displayLevel() {
            return (isMax || level >= 30) ? "MAX" : String.valueOf(level);
        }

        /**
         * Prueft, ob gueltige XP-Fortschrittsdaten fuer diesen Beruf vorliegen.
         *
         * @return {@code true}, falls {@code maxXp > 0} und nicht auf Maximalstufe
         */
        public boolean hasXpInfo() {
            return maxXp > 0.0 && currentXp >= 0.0 && !isMax && level < 30;
        }

        /**
         * Berechnet den gerundeten XP-Fortschritt in Prozent.
         *
         * @return Prozentwert zwischen {@code 0} und {@code 100}
         */
        public int xpPercent() {
            if (!hasXpInfo()) return 0;
            return (int) Math.round((currentXp / maxXp) * 100.0);
        }

        /**
         * Erzeugt das rechtsbuendige Badge fuer die HUD-Zeile (z. B. {@code 80% [24]} oder {@code [MAX]}).
         *
         * @param showXp Ob die Prozentzahl vor dem Level angezeigt werden soll
         * @return Formatiertes Level-Badge
         */
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

    /**
     * Formatierter Spielzeit-Eintrag fuer einen einzelnen Unterserver.
     *
     * @param displayName   Anzeigename des Unterservers
     * @param formattedTime Formatierte Zeitangabe (z. B. {@code 17d 22h 30m})
     */
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

    /**
     * Erstellt einen neuen, zurueckgesetzten Zustandsspeicher.
     */
    public ArdaniaState() {
        reset();
    }

    /**
     * Liefert die zentrale Singleton-Instanz des Zustandsspeichers.
     *
     * @return Zentrale {@link ArdaniaState}-Instanz
     */
    public static ArdaniaState getInstance() {
        return INSTANCE;
    }

    /**
     * Setzt den absoluten Euronen-Kontostand.
     *
     * @param amount Neuer Kontostand
     */
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

    /**
     * Addiert oder subtrahiert eine inkrementelle Betragsaenderung zum Euronen-Kontostand.
     *
     * @param delta Aenderungsbetrag (positiv oder negativ)
     */
    public synchronized void addEuronen(BigDecimal delta) {
        this.euronen = scale(this.euronen.add(delta));
        this.euronenKnown = true;
        this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
    }

    /**
     * Gibt den aktuellen Euronen-Kontostand zurueck.
     *
     * @return Skalierter Kontostand
     */
    public synchronized BigDecimal getEuronen() {
        return this.euronen;
    }

    /**
     * Gibt an, ob bereits ein Kontostand vom Server empfangen wurde.
     *
     * @return {@code true}, sobald ein gueltiger Kontostand bekannt ist
     */
    public synchronized boolean isEuronenKnown() {
        return this.euronenKnown;
    }

    /**
     * Formatiert den aktuellen Euronen-Kontostand im deutschen Zahlenformat.
     *
     * @return Formatierter Kontostand (z. B. {@code 30.214,80})
     */
    public synchronized String getFormattedEuronen() {
        return formatMoney(this.euronen);
    }

    /**
     * Setzt den absoluten ArdaPoints-Kontostand.
     *
     * @param points Neuer Punktestand (mindestens {@code 0})
     */
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

    /**
     * Addiert erhaltene ArdaPoints zum aktuellen Stand.
     *
     * @param points Hinzuzufuegende Punkte
     */
    public synchronized void addArdaPoints(long points) {
        this.ardaPoints = Math.max(0, this.ardaPoints + points);
        this.ardaPointsKnown = true;
        this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
    }

    /**
     * Gibt den aktuellen ArdaPoints-Stand zurueck.
     *
     * @return Aktuelle ArdaPoints
     */
    public synchronized long getArdaPoints() {
        return this.ardaPoints;
    }

    /**
     * Gibt an, ob bereits ein ArdaPoints-Wert bekannt ist.
     *
     * @return {@code true}, sobald ArdaPoints synchronisiert oder gecacht wurden
     */
    public synchronized boolean isArdaPointsKnown() {
        return this.ardaPointsKnown;
    }

    /**
     * Formatiert die aktuellen ArdaPoints oder liefert {@code "?"}, falls noch unbekannt.
     *
     * @return Formatierte Punktezahl oder {@code "?"}
     */
    public synchronized String getFormattedArdaPoints() {
        return this.ardaPointsKnown ? formatInteger(this.ardaPoints) : "?";
    }

    /**
     * Fuegt einen Beruf ohne XP-Details hinzu oder aktualisiert ihn.
     *
     * @param jobName Berufsname
     * @param level   Aktuelles Level
     */
    public synchronized void addOrUpdateJob(String jobName, int level) {
        addOrUpdateJob(jobName, level, level >= 30, 0.0, 0.0);
    }

    /**
     * Fuegt einen Beruf mit explizitem Max-Flag ohne XP-Details hinzu oder aktualisiert ihn.
     *
     * @param jobName Berufsname
     * @param level   Aktuelles Level
     * @param isMax   Ob Maximalstufe erreicht wurde
     */
    public synchronized void addOrUpdateJob(String jobName, int level, boolean isMax) {
        addOrUpdateJob(jobName, level, isMax, 0.0, 0.0);
    }

    /**
     * Fuegt einen Beruf inklusive aktueller XP-Werte hinzu oder aktualisiert ihn.
     *
     * @param jobName   Berufsname
     * @param level     Aktuelles Level
     * @param isMax     Ob Maximalstufe erreicht wurde
     * @param currentXp Aktuelle Erfahrungspunkte
     * @param maxXp     Benoetigte Erfahrungspunkte fuer das Level-Up
     */
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

    /**
     * Entfernt alle bisher gespeicherten Berufseintraege.
     */
    public synchronized void clearJobs() {
        this.jobs.clear();
    }

    /**
     * Liefert eine unveraenderliche Momentaufnahme aller gespeicherten Berufe.
     *
     * @return Liste der {@link JobEntry}-Eintraege
     */
    public synchronized List<JobEntry> getJobs() {
        return Collections.unmodifiableList(new ArrayList<>(this.jobs.values()));
    }

    /**
     * Liefert den Zeitstempel der letzten relevanten Wertaenderung (ausgenommen reines Spielzeit-Ticking).
     *
     * @return Zeitstempel in Millisekunden
     */
    public long getLastNonPlaytimeUpdateTimestamp() {
        return this.lastNonPlaytimeUpdateTimestamp;
    }

    /**
     * Liefert den Zeitstempel des letzten manuellen Ausblendens ueber den Toggle-Key.
     *
     * @return Zeitstempel in Millisekunden
     */
    public long getLastManualHideTimestamp() {
        return this.lastManualHideTimestamp;
    }

    /**
     * Markiert eine Aktualisierung, sodass das HUD beim Auto-Ausblenden (Slide) eingeblendet wird.
     */
    public void notifyStatUpdated() {
        this.lastNonPlaytimeUpdateTimestamp = System.currentTimeMillis();
    }

    /**
     * Blendet das HUD im Auto-Hide-Modus sofort aus und setzt das Slide-Zeitfenster zurueck.
     */
    public void hideHudNow() {
        this.lastNonPlaytimeUpdateTimestamp = 0;
        this.lastManualHideTimestamp = System.currentTimeMillis();
    }

    /**
     * Prueft, ob das HUD aufgrund einer aktuellen Wertaenderung im Auto-Hide-Modus noch sichtbar bleibt.
     *
     * @param now               Aktueller Zeitstempel in Millisekunden
     * @param displayDurationMs Konfigurierte Anzeigedauer in Millisekunden
     * @return {@code true}, solange die Anzeigedauer noch nicht abgelaufen ist
     */
    public boolean isAutoHideActive(long now, long displayDurationMs) {
        long lastUpdate = this.lastNonPlaytimeUpdateTimestamp;
        return lastUpdate > 0 && (now - lastUpdate < displayDurationMs);
    }

    /**
     * Speichert oder aktualisiert die Spielzeit fuer einen einzelnen Unterserver.
     *
     * @param serverName Name des Unterservers (z. B. {@code hauptserver}, {@code mine})
     * @param days       Tage
     * @param hours      Stunden
     * @param minutes    Minuten
     */
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

    /**
     * Loescht alle gespeicherten Server-Spielzeiten und die laufende Minutenbasis.
     */
    public synchronized void clearServerPlaytimes() {
        this.serverPlaytimes.clear();
        this.basePlaytimeMinutes = -1;
        this.playtimeBaseTimestamp = -1;
    }

    /**
     * Gibt an, ob bereits mindestens eine unterstuetzte Server-Spielzeit vorliegt.
     *
     * @return {@code true}, sobald Spielzeitdaten geladen oder synchronisiert wurden
     */
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

    /**
     * Liefert die aktuelle Gesamtspielzeit ueber alle unterstuetzten Ardania-Welten in Minuten.
     *
     * @return Gesamtminuten oder {@code -1}, falls noch keine Spielzeit bekannt ist
     */
    public synchronized long getCurrentTotalMinutes() {
        if (this.basePlaytimeMinutes < 0) {
            return -1;
        }
        return this.basePlaytimeMinutes + getElapsedMinutesSinceSync();
    }

    /**
     * Berechnet die gefilterte Gesamtspielzeit anhand der ausgewaehlten Unterserver.
     *
     * @param includeHauptserver Ob der Hauptserver einbezogen wird
     * @param includeCreative    Ob der Creative-Server einbezogen wird
     * @param includeEventserver Ob der Eventserver einbezogen wird
     * @param includeMine        Ob der Mine-Server einbezogen wird
     * @return Gefilterte Minuten oder {@code -1}, falls noch unbekannt
     */
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

    /**
     * Formatiert die gesamte bekannte Spielzeit aller unterstuetzten Welten.
     *
     * @return Formatierte Spielzeit (z. B. {@code 17d 22h 30m}) oder {@code "?"}
     */
    public synchronized String getFormattedPlaytime() {
        return formatMinutes(getCurrentTotalMinutes());
    }

    /**
     * Formatiert die Spielzeit gemaess den in {@code config} aktivierten Welten.
     *
     * @param config Benutzerkonfiguration mit Weltfiltern
     * @return Formatierte Spielzeit oder {@code "?"}
     */
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

    /**
     * Liefert die einzelnen, in {@code config} aktivierten Welten inklusive formatierter Spielzeit.
     *
     * @param config Benutzerkonfiguration mit Weltfiltern
     * @return Liste der ausgewaehlten {@link ServerPlaytimeEntry}-Eintraege
     */
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

    /**
     * Formatiert eine Minutenanzahl lesbar in Tage, Stunden und Minuten ({@code Xd Yh Zm}).
     *
     * @param totalMin Minutenanzahl
     * @return Formatierte Zeit oder {@code "?"} bei negativen Werten
     */
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

    /**
     * Startet die Session-Messung fuer Gewinn und Sitzungsdauer zum angegebenen Zeitpunkt.
     *
     * @param now Startzeitpunkt in Millisekunden
     */
    public synchronized void startSession(long now) {
        this.sessionStartTimestamp = now;
        if (this.euronenKnown) {
            this.sessionStartEuronen = this.euronen;
        }
        if (this.ardaPointsKnown) {
            this.sessionStartPoints = this.ardaPoints;
        }
    }

    /**
     * Berechnet die Euronen-Differenz seit Beginn der aktuellen Sitzung.
     *
     * @return Netto-Gewinn bzw. -Verlust in der Sitzung
     */
    public synchronized BigDecimal getSessionEuronenDelta() {
        if (!this.euronenKnown || this.sessionStartEuronen == null) {
            return scale(BigDecimal.ZERO);
        }
        return scale(this.euronen.subtract(this.sessionStartEuronen));
    }

    /**
     * Berechnet die ArdaPoints-Differenz seit Beginn der aktuellen Sitzung.
     *
     * @return Punktveraenderung in der aktuellen Sitzung
     */
    public synchronized long getSessionPointsDelta() {
        if (!this.ardaPointsKnown || this.sessionStartPoints < 0) {
            return 0L;
        }
        return this.ardaPoints - this.sessionStartPoints;
    }

    /**
     * Formatiert Session-Gewinn und optionalen Stundenlohn im Standardformat.
     *
     * @param now Aktueller Zeitstempel in Millisekunden
     * @return Formatierter Session-Verdienst
     */
    public synchronized String getFormattedSessionStats(long now) {
        return getFormattedSessionStats(now, false);
    }

    /**
     * Formatiert Session-Gewinn und ab 60 Sekunden Sitzungsdauer den hochgerechneten Stundenlohn.
     *
     * @param now     Aktueller Zeitstempel in Millisekunden
     * @param compact Ob grosse Betraege kompakt mit {@code k}/{@code M} formatiert werden sollen
     * @return Formatierter Session-Verdienst (z. B. {@code +1.234,50 (+3.400,00/h)})
     */
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

    /**
     * Formatiert die Dauer der laufenden Spielsitzung ({@code Xh Ym Zs} bzw. {@code Ym Zs}).
     *
     * @param now Aktueller Zeitstempel in Millisekunden
     * @return Formatierte Sitzungsdauer
     */
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

    /**
     * Setzt alle gespeicherten Statistiken und Sitzungswerte vollstaendig zurueck.
     */
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

    /**
     * Liefert den Standard-Dateipfad des persistenten Zustandscaches ({@code config/ardania_hud_cache.json}).
     *
     * @return Pfad zur Cache-Datei
     */
    public static Path getDefaultCachePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("ardania_hud_cache.json");
    }

    /**
     * Speichert den aktuellen Zustand in {@link #getDefaultCachePath()}.
     */
    public synchronized void saveCache() {
        try {
            saveCache(getDefaultCachePath());
        } catch (Exception e) {
            ArdaniaClientMod.LOGGER.warn("Konnte HUD-Cache nicht speichern.", e);
        }
    }

    /**
     * Speichert den aktuellen Zustand als JSON in die angegebene Datei.
     *
     * @param cachePath Zielpfad fuer die Cache-Datei
     */
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

    /**
     * Laedt den Zustand aus {@link #getDefaultCachePath()}, sofern vorhanden.
     */
    public synchronized void loadCache() {
        try {
            loadCache(getDefaultCachePath());
        } catch (Exception e) {
            ArdaniaClientMod.LOGGER.warn("Konnte HUD-Cache nicht laden.", e);
        }
    }

    /**
     * Laedt zuvor zwischengespeicherte Statistiken aus der angegebenen JSON-Datei.
     *
     * @param cachePath Pfad zur Cache-Datei
     */
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

    /**
     * Initialisiert Cache, Session-Timer und Auto-Hide-Zeitstempel beim Serverbeitritt.
     */
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

    /**
     * Speichert den Cache und setzt sitzungsbezogene Zeitstempel beim Trennen zurueck.
     */
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

    /**
     * Formatiert einen Geldbetrag mit deutschem Tausenderpunkt und zwei Nachkommastellen.
     *
     * @param value Zu formatierender Betrag
     * @return Formatierter Geldwert (z. B. {@code 1.234,50})
     */
    public static String formatMoney(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", GERMAN_SYMBOLS);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /**
     * Formatiert Geldbetraege ab 1.000 kompakt mit {@code k} bzw. {@code M}.
     *
     * @param value Zu formatierender Betrag
     * @return Kompakt formatierter Geldwert (z. B. {@code 31,5k})
     */
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

    /**
     * Parst eine XP-Zahlenangabe unabhaengig von deutschem oder englischem Dezimaltrennzeichen.
     *
     * @param raw Roher Zahlenstring
     * @return Geparster {@code double}-Wert (oder {@code 0.0} bei ungueltiger Eingabe)
     */
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

    /**
     * Formatiert eine Ganzzahl mit deutschem Tausender-Trennzeichen.
     *
     * @param value Ganzzahlwert
     * @return Formatierte Zahl (z. B. {@code 4.918})
     */
    public static String formatInteger(long value) {
        return new DecimalFormat("#,##0", GERMAN_SYMBOLS).format(value);
    }

    /**
     * Parst eine deutsch oder gemischt formatierte Zahl in ein {@link BigDecimal}.
     *
     * @param raw Roher Zahlenstring (z. B. {@code 30.214,80})
     * @return Geparstes {@link BigDecimal}
     */
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
