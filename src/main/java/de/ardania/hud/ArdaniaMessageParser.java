package de.ardania.hud;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-basierter Parser fuer Chat-, System- und Actionbar-Nachrichten auf Ardania.de.
 *
 * <p>Verarbeitet:</p>
 * <ul>
 *   <li>Absoluten Kontostand (z. B. aus {@code /geld}, {@code /money}, {@code /konto})</li>
 *   <li>Inkrementelle Einnahmen (z. B. Job-Actionbar {@code +0,02 Euronen})</li>
 *   <li>Bank-Transaktionen (Gutschriften, Abzuege, Ueberweisungen)</li>
 *   <li>ArdaPoints (Punktestand und Voteziel-Benachrichtigungen)</li>
 *   <li>Berufe & Job-Level (z. B. {@code Miner [Lv. 24]} oder {@code [MAX]})</li>
 *   <li>Spielzeit-Meldungen (Tage, Stunden, Minuten pro Unterserver aus {@code /ontime})</li>
 *   <li>Server-Tags aus Tablist-Namen (z. B. {@code [H]}, {@code [F]}, {@code [M]})</li>
 *   <li>Fehlermeldungen unbekannter Befehle zur automatischen Unterdrueckung</li>
 * </ul>
 */
public final class ArdaniaMessageParser {

    private static final String NUMBER = "(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)";
    private static final String CURRENCY = "(?:Euronen?|€|\\$)";
    private static final String LETTERS = "[\\p{L}\\-]+";

    private static final Pattern LEGACY_FORMATTING = Pattern.compile("\u00A7[0-9A-FK-ORXa-fk-orx]");

    private static final Pattern TAB_SERVER_TAG = Pattern.compile("(?<![\\p{L}\\d])\\[([HFMEC])\\](?![\\p{L}\\d])");

    private static final Pattern ABSOLUTE_BALANCE = Pattern.compile(
            "(?iu)(?:\\[ArdaBank\\]\\s*)?(?:Dein\\s+)?(?:Kontostand|Guthaben|Geld|Konto|Verm\u00F6gen|Balance)\\s*:?\\s*" + NUMBER + "\\s*" + CURRENCY);

    private static final Pattern ARDABANK_DELTA = Pattern.compile(
            "(?iu)\\[(?:ArdaBank|Ardania[^\\]]*)\\]\\s*.*?([+-])\\s*" + NUMBER + "\\s*" + CURRENCY);

    private static final Pattern ARDABANK_DEDUCT = Pattern.compile(
            "(?iu)\\[(?:ArdaBank|Ardania[^\\]]*)\\]\\s*.*?(" + NUMBER + ")\\s*" + CURRENCY + ".*?(?:abgebucht|gezahlt|\u00FCberwiesen|ueberwiesen|bezahlt|ausgegeben)");

    private static final Pattern ARDABANK_INCOME = Pattern.compile(
            "(?iu)\\[(?:ArdaBank|Ardania[^\\]]*)\\]\\s*.*?(?:Dir\\s+wurden|Du\\s+hast)?\\s*(" + NUMBER + ")\\s*" + CURRENCY + ".*?(?:gutgeschrieben|\u00FCberwiesen|ueberwiesen|erhalten)");

    private static final Pattern ACTIONBAR_INCOME_TRIGGER = Pattern.compile(
            "(?iu)(?<![\\p{L}\\d])\\+\\s*" + NUMBER + "\\s*(?:Euronen?|€|\\$)(?!\\s*XP)");

    private static final String POINTS_CURRENCY = "(?:Arda(?:nia)?[\\s-]?Points?|Punkte)";

    private static final Pattern ARDAPOINTS_BESITZ = Pattern.compile(
            "(?iu)(?:\\[Arda(?:nia)?Points\\]\\s*)?(?:Du\\s+besitzt(?:\\s+aktuell|\\s+derzeit)?\\s*:?|Deine?\\s+(?:Arda(?:nia)?Points?|Punktestand)\\s*:?)\\s*([0-9.,]+)(?:\\s*" + POINTS_CURRENCY + ")?");

    private static final Pattern ARDAPOINTS_RECEIVED = Pattern.compile(
            "(?iu)(?:(?:Du\\s+(?:hast|erh\u00E4ltst|erhaeltst|bekommst)|(?:erh\u00E4ltst|erhaeltst|bekommst)\\s+du|Dir\\s+wurden|(?:Jeder|Alle)(?:\\s+Spieler)?\\s+(?:erh\u00E4lt|erhaelt|erhalten|bekommt|bekommen)|Erhalten:?|Belohnung:?|Vote(?:-?ziels?)?).*?([0-9.,]+)\\s*" + POINTS_CURRENCY + "|([0-9.,]+)\\s*" + POINTS_CURRENCY + "\\s+(?:erhalten|bekommen|gutgeschrieben|gewonnen))");

    private static final Pattern ARDAPOINTS_INCREMENT = Pattern.compile(
            "(?iu)(?<![\\p{L}\\d])\\+\\s*([0-9.,]+)\\s*" + POINTS_CURRENCY);

    private static final Pattern VOTE_GOAL_NOTIFICATION = Pattern.compile(
            "(?iu)(?:Vote\\s*-?\\s*ziel|VoteParty|Community\\s*-?\\s*ziel|Danke\\s+f(?:\u00FC|ue)rs\\s+Voten)");

    private static final Pattern JOBS_HEADER = Pattern.compile(
            "(?iu)\\[ArdaniaJobs\\]\\s+Spieler:");

    private static final Pattern JOBS_NOTIFICATION = Pattern.compile(
            "(?iu)\\[ArdaniaJobs\\]");

    private static final Pattern JOBS_STATS_LINE = Pattern.compile(
            "(?imu)(?:[|:;#=\\-\\s]*)(?:\\b(?:Lv|Lvl|Level|Stufe)\\.?\\s*)?(\\d+|MAX)\\s+(?:for|f\u00FCr|als)\\s+(" + LETTERS + ")\\s*(?::(?:\\s*([0-9.,]+)\\s*/\\s*([0-9.,]+)(?:\\s*xp)?)?|$)");

    private static final Pattern JOB_PARENTHESIZED = Pattern.compile(
            "(?iu)(" + LETTERS + ")\\s*\\(\\s*(?:(?:Lv|Lvl|Level|Stufe)\\.?\\s*:?\\s*)?(\\d+|MAX)\\s*\\)");

    private static final Pattern JOB_LABELED = Pattern.compile(
            "(?iu)(?:Job|Beruf)\\s*:\\s*(" + LETTERS + ")\\s*[-:|]?\\s*(?:(?:Lv|Lvl|Level|Stufe)\\.?\\s*:?\\s*)?(\\d+|MAX)");

    private static final Pattern UNKNOWN_COMMAND_ERROR = Pattern.compile(
            "(?iu)(?:Unknown\\s+(?:or\\s+incomplete\\s+)?command|Unbekannter\\s+(?:oder\\s+unvollst(?:\u00E4|ae)ndiger\\s+)?Befehl|Befehl\\s+unbekannt|befehl\\s+(?:wurde\\s+)?nicht\\s+gefunden|kein\\s+g(?:\u00FC|ue)ltiger\\s+befehl|See\\s+below\\s+for\\s+error|Siehe\\s+unten\\s+f(?:\u00FC|ue)r|Comando\\s+desconocido|Commande\\s+inconnue|<--\\[[^\\]]+\\]|(?:^|\\s)(?:geld|money|ardapoints(?:\\s+me)?|jobs(?:\\s+stats)?|ontime)<--|command\\s+does\\s+not\\s+exist|An\\s+error\\s+occurred\\s+while\\s+executing|dieser\\s+welt\\s+nicht|diesem\\s+server\\s+nicht|hier\\s+nicht\\s+(?:benutzen|verwenden|nutzen|erlaubt|verf\u00FCgbar|verfuegbar|m\u00F6glich|moeglich|ausf\u00FChren|ausfuehren|existiert)|existiert\\s+hier\\s+nicht|ardapoints\\s+me|fehler:\\s*der\\s*befehl)");

    /**
     * Eine Zeile eines {@code /ontime}-Berichts. Bewusst an den Zeilenanfang und
     * das Zeilenende gebunden, damit normale Chatnachrichten (z. B.
     * "Ich bin Mine: 3 Stunden ...") nicht als Spielzeit interpretiert werden.
     */
    private static final Pattern ONTIME_SERVER_LINE = Pattern.compile(
            "(?im)^[\\p{Z}\\s]*([\\p{L}\\-]+)[\\p{Z}\\s]*:[\\p{Z}\\s]*"
                    + "(?:(\\d+)\\s*Tage?)?\\s*(?:(\\d+)\\s*Stunden?)?\\s*(?:(\\d+)\\s*Minute(?:n)?)?"
                    + "[\\p{Z}\\s]*$");

    private static final Pattern ONTIME_LINE_SUPPRESS = Pattern.compile(
            "(?iu)(?:[-=~_]{4,}|Name:\\s*|Beigetreten\\s+am:\\s*|Hauptserver:|Mine:|Creative:|Eventserver:)");

    /** Kopfzeilen eines vollstaendigen {@code /ontime}-Berichts. */
    private static final Pattern ONTIME_BLOCK_HEADER = Pattern.compile(
            "(?im)(?:^[\\p{Z}\\s]*Name[\\p{Z}\\s]*:|Beigetreten[\\p{Z}\\s]+am[\\p{Z}\\s]*:|^[\\p{Z}\\s]*[-=~_]{10,}[\\p{Z}\\s]*$)");

    /** Spielername aus der {@code Name:}-Zeile eines {@code /ontime}-Berichts. */
    private static final Pattern ONTIME_NAME_LINE = Pattern.compile(
            "(?im)^[\\p{Z}\\s]*Name[\\p{Z}\\s]*:[\\p{Z}\\s]*([\\p{L}\\d_]{1,32})[\\p{Z}\\s]*$");

    private final ArdaniaState state;

    public ArdaniaMessageParser(ArdaniaState state) {
        this.state = state;
    }

    public boolean parse(String rawMessage, boolean overlay) {
        return parse(rawMessage, overlay, null);
    }

    /**
     * Parst eine eingehende Nachricht.
     *
     * @param rawMessage         Roher Nachrichteninhalt
     * @param overlay            true, wenn die Nachricht aus der Actionbar stammt
     * @param expectedPlayerName Name des lokalen Spielers. Wird ein
     *                           {@code /ontime}-Bericht fuer einen anderen Spieler
     *                           erkannt, wird er komplett ignoriert.
     * @return true, wenn mindestens ein Wert uebernommen wurde
     */
    public boolean parse(String rawMessage, boolean overlay, String expectedPlayerName) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String message = normalize(rawMessage);

        boolean changed = parseMoney(message);
        changed |= parseArdaPoints(message);
        changed |= parseJob(message);
        changed |= parseOntime(message, expectedPlayerName);
        return changed;
    }

    public static String extractTabServerTag(String rawTabEntry) {
        if (rawTabEntry == null || rawTabEntry.isBlank()) {
            return null;
        }
        String clean = normalize(rawTabEntry);
        Matcher m = TAB_SERVER_TAG.matcher(clean);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public boolean isSilentServerResponse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        String m = normalize(rawMessage);

        return ABSOLUTE_BALANCE.matcher(m).find()
                || ARDAPOINTS_BESITZ.matcher(m).find()
                || JOBS_HEADER.matcher(m).find()
                || JOBS_STATS_LINE.matcher(m).find()
                || ONTIME_LINE_SUPPRESS.matcher(m).find();
    }

    public boolean isMoneyBalanceResponse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        return ABSOLUTE_BALANCE.matcher(normalize(rawMessage)).find();
    }

    public boolean isPointsBalanceResponse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        return ARDAPOINTS_BESITZ.matcher(normalize(rawMessage)).find();
    }

    public boolean isJobsStatsResponse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        String m = normalize(rawMessage);
        return JOBS_HEADER.matcher(m).find() || JOBS_STATS_LINE.matcher(m).find();
    }

    public boolean isOntimeResponse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        return ONTIME_LINE_SUPPRESS.matcher(normalize(rawMessage)).find();
    }

    public boolean isUnknownCommandError(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        return UNKNOWN_COMMAND_ERROR.matcher(normalize(rawMessage)).find();
    }

    public String extractFailedCommandFromContext(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return null;
        String m = normalize(rawMessage).toLowerCase(java.util.Locale.ROOT);
        if (!m.contains("<--")) return null;
        if (m.contains("geld")) return "geld";
        if (m.contains("jobs")) return "jobs stats";
        if (m.contains("ardapoints")) return "ardapoints me";
        if (m.contains("ontime")) return "ontime";
        return null;
    }

    /**
     * Prueft, ob eine Brigadier-Fehlermeldung explizit auf einen fremden (nicht vom Mod stammenden)
     * Befehl hinweist (z. B. {@code ban<--[HERE]} oder {@code tp<--[HIER]}).
     *
     * @param rawMessage Roher Nachrichteninhalt
     * @return true, falls ein Brigadier-Fehlerkontext fuer einen fremden Befehl vorliegt
     */
    public boolean hasForeignCommandErrorContext(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        String m = normalize(rawMessage).toLowerCase(java.util.Locale.ROOT);
        return m.contains("<--") && extractFailedCommandFromContext(rawMessage) == null;
    }

    public boolean isActionBarIncome(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        return ACTIONBAR_INCOME_TRIGGER.matcher(normalize(rawMessage)).find();
    }

    public boolean isJobNotification(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        String m = normalize(rawMessage);
        return JOBS_NOTIFICATION.matcher(m).find()
                && !JOBS_HEADER.matcher(m).find()
                && !JOBS_STATS_LINE.matcher(m).find();
    }

    public boolean isVoteNotification(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        return VOTE_GOAL_NOTIFICATION.matcher(normalize(rawMessage)).find();
    }

    private boolean parseMoney(String message) {
        Matcher absolute = ABSOLUTE_BALANCE.matcher(message);
        if (absolute.find()) {
            return apply(() -> this.state.setEuronen(ArdaniaState.parseGermanNumber(absolute.group(1))));
        }

        Matcher bankDelta = ARDABANK_DELTA.matcher(message);
        if (bankDelta.find()) {
            String sign = bankDelta.group(1);
            BigDecimal amount = safeParse(bankDelta.group(2));
            if (amount != null) {
                BigDecimal delta = "-".equals(sign) ? amount.negate() : amount;
                this.state.addEuronen(delta);
                return true;
            }
        }

        Matcher deduct = ARDABANK_DEDUCT.matcher(message);
        if (deduct.find()) {
            BigDecimal amount = safeParse(deduct.group(1));
            if (amount != null) {
                this.state.addEuronen(amount.negate());
                return true;
            }
        }

        Matcher inc = ARDABANK_INCOME.matcher(message);
        if (inc.find()) {
            BigDecimal amount = safeParse(inc.group(1));
            if (amount != null) {
                this.state.addEuronen(amount);
                return true;
            }
        }

        return false;
    }

    private boolean parseArdaPoints(String message) {
        Matcher besitzt = ARDAPOINTS_BESITZ.matcher(message);
        if (besitzt.find()) {
            this.state.setArdaPoints(parsePoints(besitzt.group(1)));
            return true;
        }

        Matcher inc = ARDAPOINTS_INCREMENT.matcher(message);
        if (inc.find()) {
            this.state.addArdaPoints(parsePoints(inc.group(1)));
            return true;
        }

        Matcher rec = ARDAPOINTS_RECEIVED.matcher(message);
        if (rec.find()) {
            String rawPoints = rec.group(1) != null ? rec.group(1) : rec.group(2);
            this.state.addArdaPoints(parsePoints(rawPoints));
            return true;
        }

        return false;
    }

    private static long parsePoints(String raw) {
        if (raw == null) return 0;
        String clean = raw.replaceAll("[^0-9]", "");
        if (clean.isBlank()) return 0;
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean parseJob(String message) {
        boolean matched = false;
        if (JOBS_HEADER.matcher(message).find()) {
            this.state.clearJobs();
            matched = true;
        }

        Matcher statsMatcher = JOBS_STATS_LINE.matcher(message);
        while (statsMatcher.find()) {
            matched |= setJob(statsMatcher.group(2), statsMatcher.group(1), statsMatcher.group(3), statsMatcher.group(4));
        }
        if (matched) {
            return true;
        }

        Matcher m = JOB_PARENTHESIZED.matcher(message);
        if (m.find()) {
            return setJob(m.group(1), m.group(2), null, null);
        }

        m = JOB_LABELED.matcher(message);
        if (m.find()) {
            return setJob(m.group(1), m.group(2), null, null);
        }

        return false;
    }

    private boolean setJob(String name, String levelOrMax) {
        return setJob(name, levelOrMax, null, null);
    }

    private boolean setJob(String name, String levelOrMax, String curXpStr, String maxXpStr) {
        if (name == null || name.isBlank() || levelOrMax == null) {
            return false;
        }
        double curXp = ArdaniaState.parseXpNumber(curXpStr);
        double maxXp = ArdaniaState.parseXpNumber(maxXpStr);

        if ("MAX".equalsIgnoreCase(levelOrMax.trim())) {
            this.state.addOrUpdateJob(name.trim(), 30, true, curXp, maxXp);
            return true;
        }
        try {
            int level = Integer.parseInt(levelOrMax.trim());
            this.state.addOrUpdateJob(name.trim(), level, level >= 30, curXp, maxXp);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean parseOntime(String message, String expectedPlayerName) {
        java.util.LinkedHashMap<String, Long> entries = new java.util.LinkedHashMap<>();
        Matcher m = ONTIME_SERVER_LINE.matcher(message);

        while (m.find()) {
            // Zeilen ohne Zeitangabe (z. B. "Name: PX_Sven") ignorieren
            if (m.group(2) == null && m.group(3) == null && m.group(4) == null) {
                continue;
            }

            String serverName = m.group(1);
            long days = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
            long hours = m.group(3) != null ? Long.parseLong(m.group(3)) : 0;
            long minutes = m.group(4) != null ? Long.parseLong(m.group(4)) : 0;
            long totalMinutes = (days * 24 * 60) + (hours * 60) + minutes;
            entries.put(serverName.trim().toLowerCase(java.util.Locale.ROOT), totalMinutes);
        }

        if (entries.isEmpty()) {
            return false;
        }

        // Fremdspieler-Abfrage (/ontime <Spieler>) niemals in den eigenen Cache
        // uebernehmen. Ohne diese Pruefung wuerde z. B. "/ontime LordSandukhan"
        // die eigenen Spielzeiten mit 1384 Tagen ueberschreiben.
        String reportedName = extractOntimePlayerNameFromNormalized(message);
        if (reportedName != null && expectedPlayerName != null && !expectedPlayerName.isBlank()
                && !reportedName.equalsIgnoreCase(expectedPlayerName.trim())) {
            return false;
        }

        boolean fullBlock = entries.size() > 1 || ONTIME_BLOCK_HEADER.matcher(message).find();
        if (fullBlock) {
            // Vollstaendiger Bericht: veraltete Schluessel verschwinden, die Summe
            // entspricht wieder exakt der Serverantwort.
            this.state.replaceServerPlaytimes(entries);
        } else {
            // Einzelne Zeile (z. B. aus einem aufgeteilten Bericht): nur upserten,
            // damit andere Welten nicht verloren gehen.
            java.util.Map.Entry<String, Long> only = entries.entrySet().iterator().next();
            long totalMinutes = only.getValue();
            this.state.updateServerPlaytime(
                    only.getKey(),
                    totalMinutes / (24 * 60),
                    (totalMinutes % (24 * 60)) / 60,
                    totalMinutes % 60);
        }
        return true;
    }

    /**
     * Liest den Spielernamen aus der {@code Name:}-Zeile eines
     * {@code /ontime}-Berichts.
     *
     * @param rawMessage Roher Nachrichteninhalt
     * @return Spielername oder {@code null}, wenn keine Name-Zeile vorhanden ist
     */
    public String extractOntimePlayerName(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }
        return extractOntimePlayerNameFromNormalized(normalize(rawMessage));
    }

    private static String extractOntimePlayerNameFromNormalized(String message) {
        Matcher m = ONTIME_NAME_LINE.matcher(message);
        return m.find() ? m.group(1).trim() : null;
    }

    static String normalize(String raw) {
        return LEGACY_FORMATTING.matcher(raw).replaceAll("")
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .trim();
    }

    private static BigDecimal safeParse(String raw) {
        try {
            return ArdaniaState.parseGermanNumber(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean apply(Runnable action) {
        try {
            action.run();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
