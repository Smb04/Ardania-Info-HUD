package de.ardania.hud;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            "(?imu)(?:[|:;#=\\-\\s]*)(?:\\b(?:Lv|Lvl|Level|Stufe)\\.?\\s*)?(\\d+|MAX)\\s+(?:for|f\u00FCr|als)\\s+(" + LETTERS + ")\\s*(?::|$)");

    private static final Pattern JOB_PARENTHESIZED = Pattern.compile(
            "(?iu)(" + LETTERS + ")\\s*\\(\\s*(?:(?:Lv|Lvl|Level|Stufe)\\.?\\s*:?\\s*)?(\\d+|MAX)\\s*\\)");

    private static final Pattern JOB_LABELED = Pattern.compile(
            "(?iu)(?:Job|Beruf)\\s*:\\s*(" + LETTERS + ")\\s*[-:|]?\\s*(?:(?:Lv|Lvl|Level|Stufe)\\.?\\s*:?\\s*)?(\\d+|MAX)");

    private static final Pattern UNKNOWN_COMMAND_ERROR = Pattern.compile(
            "(?iu)(?:Unknown\\s+(?:or\\s+incomplete\\s+)?command|Unbekannter\\s+(?:oder\\s+unvollst(?:\u00E4|ae)ndiger\\s+)?Befehl|Befehl\\s+unbekannt|See\\s+below\\s+for\\s+error|Siehe\\s+unten\\s+f(?:\u00FC|ue)r|Comando\\s+desconocido|Commande\\s+inconnue|<--\\[[^\\]]+\\]|(?:^|\\s)(?:geld|money|ardapoints(?:\\s+me)?|jobs(?:\\s+stats)?|ontime)<--|command\\s+does\\s+not\\s+exist|An\\s+error\\s+occurred\\s+while\\s+executing|befehle?\\b.*\\bnicht\\b|\\bnicht\\b.*\\bbefehle?\\b|dieser\\s+welt\\s+nicht|diesem\\s+server\\s+nicht|hier\\s+nicht\\s+(?:benutzen|verwenden|nutzen|erlaubt|verf\u00FCgbar|verfuegbar|m\u00F6glich|moeglich|ausf\u00FChren|ausfuehren)|keine\\s+(?:berechtigung|rechte)|no\\s+permission|ardapoints\\s+me|\\[ardania\\]\\s*fehler|fehler:\\s*der\\s*befehl)");

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
            matched |= setJob(statsMatcher.group(2), statsMatcher.group(1));
        }
        if (matched) {
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

    private boolean setJob(String name, String levelOrMax) {
        if (name == null || name.isBlank() || levelOrMax == null) {
            return false;
        }
        if ("MAX".equalsIgnoreCase(levelOrMax.trim())) {
            this.state.addOrUpdateJob(name.trim(), 30, true);
            return true;
        }
        try {
            int level = Integer.parseInt(levelOrMax.trim());
            this.state.addOrUpdateJob(name.trim(), level, level >= 30);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean parseOntime(String message) {
        Matcher m = ONTIME_SERVER_LINE.matcher(message);
        boolean matchedAny = false;

        while (m.find()) {
            // Zeilen ohne Zeitangabe (z. B. "Name: PX_Sven") ignorieren
            if (m.group(2) == null && m.group(3) == null && m.group(4) == null) {
                continue;
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
