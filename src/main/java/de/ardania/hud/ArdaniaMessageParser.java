package de.ardania.hud;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wertet Chat- und Action-Bar-Nachrichten von Ardania.de aus und aktualisiert den {@link ArdaniaState}.
 */
public final class ArdaniaMessageParser {

    private static final String NUMBER = "(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)";
    private static final String CURRENCY = "(?:Euronen?|€|\\$)";
    private static final String LETTERS = "[\\p{L}\\-]+";

    private static final Pattern LEGACY_FORMATTING = Pattern.compile("\u00A7[0-9A-FK-ORXa-fk-orx]");

    // ------------------------------------------------------------------ Geld Pattern

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

    // ------------------------------------------------------------------ ArdaPoints Pattern

    private static final String POINTS_CURRENCY = "(?:Arda(?:nia)?[\\s-]?Points?|Punkte)";

    private static final Pattern ARDAPOINTS_BESITZ = Pattern.compile(
            "(?iu)(?:\\[ArdaniaPoints\\]\\s*)?(?:Du\\s+besitzt|Deine\\s+Arda(?:nia)?Points?:?)\\s*([0-9.,]+)\\s*" + POINTS_CURRENCY);

    private static final Pattern ARDAPOINTS_RECEIVED = Pattern.compile(
            "(?iu)(?:Du\\s+hast|Erhalten:?|Belohnung:?|Vote:?)\\s*.*?([0-9.,]+)\\s*" + POINTS_CURRENCY + "\\s*(?:erhalten|bekommen|gutgeschrieben)?");

    private static final Pattern ARDAPOINTS_INCREMENT = Pattern.compile(
            "(?iu)(?<![\\p{L}\\d])\\+([0-9.,]+)\\s*" + POINTS_CURRENCY);

    private static final Pattern ARDAPOINTS_ABSOLUTE = Pattern.compile(
            "(?iu)" + POINTS_CURRENCY + "\\s*:?\\s*([0-9.,]+)");

    // ------------------------------------------------------------------ Jobs Pattern

    private static final Pattern JOBS_HEADER = Pattern.compile(
            "(?iu)\\[ArdaniaJobs\\]\\s+Spieler:");

    private static final Pattern JOBS_STATS_LINE = Pattern.compile(
            "(?iu)(?:[|:;#=\\-\\s]*)\\b(?:Lv|Lvl|Level|Stufe)\\.?\\s*(\\d+)\\s+(?:for|f\u00FCr|als)\\s+(" + LETTERS + ")\\s*:");

    private static final Pattern JOB_PARENTHESIZED = Pattern.compile(
            "(?iu)(" + LETTERS + ")\\s*\\(\\s*(?:Lv|Lvl|Level|Stufe)\\.?\\s*:?\\s*(\\d+)\\s*\\)");

    private static final Pattern JOB_LABELED = Pattern.compile(
            "(?iu)(?:Job|Beruf)\\s*:\\s*(" + LETTERS + ")\\s*[-:|]?\\s*(?:Lv|Lvl|Level|Stufe)\\.?\\s*:?\\s*(\\d+)");

    // ------------------------------------------------------------------ Fehler-Meldungen bei unbekannten Befehlen
    private static final Pattern UNKNOWN_COMMAND_ERROR = Pattern.compile(
            "(?iu)(?:Unknown\\s+command|Unbekannter\\s+Befehl|Befehl\\s+(?:wurde\\s+)?nicht\\s+gefunden|command\\s+does\\s+not\\s+exist|Dieser\\s+Befehl\\s+existiert\\s+nicht|Befehl\\s+unbekannt|An\\s+error\\s+occurred\\s+while\\s+executing|Konnte\\s+Befehl\\s+nicht|ardapoints|ardaniapoints|\\[ardania\\]\\s*fehler|fehler:\\s*der\\s*befehl)");

    // ------------------------------------------------------------------ Ontime Pattern

    /**
     * Erfasst beliebige Serverzeilen aus /ontime:
     * z. B. "Hauptserver: 16 Tage 3 Stunden 17 Minute", "Mine: 0 Tage 7 Stunden 18 Minute"
     */
    private static final Pattern ONTIME_SERVER_LINE = Pattern.compile(
            "(?iu)(?:^|\\n|\\r|\\s*)([\\p{L}\\-]+):\\s*(?:(\\d+)\\s*Tage?)?\\s*(?:(\\d+)\\s*Stunden?)?\\s*(?:(\\d+)\\s*Minute(?:n)?)?");

    private static final Pattern ONTIME_LINE_SUPPRESS = Pattern.compile(
            "(?iu)(?:[-=~_]{4,}|Name:\\s*|Beigetreten\\s+am:\\s*|Hauptserver:|Mine:|Creative:|Eventserver:)");

    private final ArdaniaState state;

    public ArdaniaMessageParser(ArdaniaState state) {
        this.state = state;
    }

    public boolean parse(String rawMessage, boolean overlay) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String message = normalize(rawMessage);

        boolean changed = parseMoney(message);
        changed |= parseArdaPoints(message);
        changed |= parseJob(message);
        changed |= parseOntime(message);
        return changed;
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

    public boolean isUnknownCommandError(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        return UNKNOWN_COMMAND_ERROR.matcher(normalize(rawMessage)).find();
    }

    public boolean isActionBarIncome(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return false;
        return ACTIONBAR_INCOME_TRIGGER.matcher(normalize(rawMessage)).find();
    }

    // ------------------------------------------------------------------ Euronen

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

    // ------------------------------------------------------------------ ArdaPoints

    private boolean parseArdaPoints(String message) {
        Matcher besitzt = ARDAPOINTS_BESITZ.matcher(message);
        if (besitzt.find()) {
            this.state.setArdaPoints(parsePoints(besitzt.group(1)));
            return true;
        }

        Matcher rec = ARDAPOINTS_RECEIVED.matcher(message);
        if (rec.find()) {
            this.state.addArdaPoints(parsePoints(rec.group(1)));
            return true;
        }

        Matcher inc = ARDAPOINTS_INCREMENT.matcher(message);
        if (inc.find()) {
            this.state.addArdaPoints(parsePoints(inc.group(1)));
            return true;
        }

        Matcher abs = ARDAPOINTS_ABSOLUTE.matcher(message);
        if (abs.find()) {
            this.state.setArdaPoints(parsePoints(abs.group(1)));
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

    // ------------------------------------------------------------------ Job

    private boolean parseJob(String message) {
        if (JOBS_HEADER.matcher(message).find()) {
            this.state.clearJobs();
            return true;
        }

        Matcher statsMatcher = JOBS_STATS_LINE.matcher(message);
        if (statsMatcher.find()) {
            int level = Integer.parseInt(statsMatcher.group(1));
            String jobName = statsMatcher.group(2);
            this.state.addOrUpdateJob(jobName, level);
            return true;
        }

        Matcher m = JOB_PARENTHESIZED.matcher(message);
        if (m.find()) {
            return setJob(m.group(1), m.group(2));
        }

        m = JOB_LABELED.matcher(message);
        if (m.find()) {
            return setJob(m.group(1), m.group(2));
        }

        return false;
    }

    private boolean setJob(String name, String level) {
        try {
            this.state.addOrUpdateJob(name, Integer.parseInt(level));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Ontime

    private boolean parseOntime(String message) {
        Matcher m = ONTIME_SERVER_LINE.matcher(message);
        boolean matchedAny = false;

        // WICHTIG: while-Schleife, um ALLE Zeilen in einer mehrzeiligen Server-Meldung einzulesen!
        while (m.find()) {
            // Nur auswerten, wenn mindestens ein Zeitwert (Tage, Stunden oder Minuten) existiert!
            if (m.group(2) == null && m.group(3) == null && m.group(4) == null) {
                continue; // Überspringt Zeilen wie "Name: PX_Sven"
            }

            String serverName = m.group(1);
            long days = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
            long hours = m.group(3) != null ? Long.parseLong(m.group(3)) : 0;
            long minutes = m.group(4) != null ? Long.parseLong(m.group(4)) : 0;

            this.state.updateServerPlaytime(serverName, days, hours, minutes);
            matchedAny = true;
        }

        return matchedAny;
    }

    // ------------------------------------------------------------------ Hilfsmethoden

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
