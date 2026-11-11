package de.ardania.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reine, Minecraft-unabhaengige Erkennung des aktuellen Ardania-Unterservers.
 *
 * <p>Die wichtigste Quelle ist der Tablisten-Header. Auf Ardania.de steht dort
 * beispielsweise {@code Hauptserver 6}, {@code Minenwelten 4} oder
 * {@code Creative 2}. Die Zahl zaehlt den eigenen Spieler mit.</p>
 *
 * <p>Die Tabliste ist netzwerkweit: Sie zeigt Spieler aller Unterserver. Spieler
 * auf anderen Welten tragen deren Tag, Spieler auf der eigenen Welt (inklusive
 * des eigenen Eintrags) bleiben ungetaggt. Fuer jeden Servertyp gilt daher
 * {@code delta = headerZahl - tagZahl}. Der eigene Server hat ein positives
 * Delta, alle anderen sollten bei 0 liegen. Zusaetzlich muss das Delta
 * mindestens {@code 1 + gleicheWeltUngetaggt} betragen, weil der eigene Spieler
 * und alle anderen Spieler der eigenen Welt ungetaggt sind.</p>
 *
 * <p>Zusaetzlich werden weitere, schwaechere Signale ausgewertet, falls der
 * Header (noch) nicht vorliegt oder mehrere Deltas gleichauf liegen:</p>
 * <ul>
 *   <li>Server-Tags anderer Tablisten-Eintraege</li>
 *   <li>Spieler, die sich im selben Level (also auf demselben Unterserver) befinden</li>
 *   <li>Creative-Gamemode</li>
 *   <li>Verfuegbare Befehle im Brigadier-Baum (nur Tie-Breaker)</li>
 * </ul>
 */
public final class ArdaniaServerDetector {

    /**
     * Quelle, aus der der erkannte Servertyp stammt. Wird im Debug-Befehl angezeigt.
     */
    public enum Source {
        /** Manuelle Festlegung ueber die Einstellung {@code Server-Erkennung}. */
        OVERRIDE,
        /** Eindeutiger Hinweis oder alleinige Server-Nennung im Tablist-Header/-Footer. */
        HEADER,
        /** Berechnung ueber das groesste positive Delta aus Header-Zahlen und Tablist-Tags. */
        HEADER_COUNTS,
        /** Server-Tag am eigenen Tablisten-Eintrag. */
        SELF_TAG,
        /** Server-Tags anderer Spieler in derselben geladenen Welt. */
        SAME_WORLD,
        /** Auswertung verfuegbarer Serverbefehle im Brigadier-Befehlsbaum. */
        COMMANDS,
        /** Erkannter Creative-Spielmodus. */
        CREATIVE,
        /** Beibehaltener vorheriger Servertyp bei unvollstaendigem Zwischenzustand. */
        RETAINED,
        /** Kein verlaessliches Erkennungssignal vorhanden. */
        NONE
    }

    /**
     * Ergebnis einer Erkennungsrunde inklusive aller Rohdaten fuer den Debug-Befehl.
     *
     * @param type              Erkannter Unterserver-Typ
     * @param confidence        Konfidenzwert der Erkennung (0 bis 100 Prozent)
     * @param source            Primaere Quelle der Erkennung
     * @param headerText        Gelesener Tablist-Header
     * @param footerText        Gelesener Tablist-Footer
     * @param headerCounts      Aus Header/Footer geparste Spielerzahlen je Servertyp
     * @param tagCounts         Gezaehlte Server-Tags anderer Spieler in der Tabliste
     * @param sameWorldTags     Server-Tags von Spielern in derselben Welt
     * @param untaggedOthers    Anzahl ungetaggter anderer Spieler in der Tabliste
     * @param sameWorldUntagged Anzahl ungetaggter anderer Spieler in derselben Welt
     * @param tabListSize       Gesamtgroesse der Tabliste
     * @param commandTreeReady  Ob der Brigadier-Befehlsbaum vollstaendig geladen ist
     * @param moneyAvailable    Ob {@code /geld} im Befehlsbaum verfuegbar ist
     * @param jobsAvailable     Ob {@code /jobs} im Befehlsbaum verfuegbar ist
     * @param creative          Ob sich der lokale Spieler im Creative-Modus befindet
     * @param timestamp         Zeitstempel dieser Erkennungsrunde in Millisekunden
     */
    public record Detection(
            ArdaniaSyncManager.ServerType type,
            double confidence,
            Source source,
            String headerText,
            String footerText,
            Map<ArdaniaSyncManager.ServerType, Integer> headerCounts,
            Map<ArdaniaSyncManager.ServerType, Integer> tagCounts,
            List<String> sameWorldTags,
            int untaggedOthers,
            int sameWorldUntagged,
            int tabListSize,
            boolean commandTreeReady,
            boolean moneyAvailable,
            boolean jobsAvailable,
            boolean creative,
            long timestamp
    ) {
        /**
         * Normalisiert nullwertige Text-, Map- und Zaehlerfelder beim Erzeugen.
         */
        public Detection {
            headerText = headerText != null ? headerText : "";
            footerText = footerText != null ? footerText : "";
            headerCounts = copyCounts(headerCounts);
            tagCounts = copyCounts(tagCounts);
            sameWorldTags = sameWorldTags != null ? List.copyOf(sameWorldTags) : List.of();
            untaggedOthers = Math.max(0, untaggedOthers);
            sameWorldUntagged = Math.max(0, sameWorldUntagged);
        }

        /**
         * Mindest-Delta, das der eigene Servertyp haben muss: der eigene Spieler
         * plus alle ungetaggten Spieler der eigenen Welt.
         *
         * @return Erwartetes Mindest-Delta
         */
        public int expectedMinDelta() {
            return 1 + this.sameWorldUntagged;
        }

        /**
         * Delta eines Servertyps: Header-Zahl minus Tag-Zahl.
         *
         * @param type Zu pruefender Servertyp
         * @return Berechnetes Delta
         */
        public int delta(ArdaniaSyncManager.ServerType type) {
            if (type == null) {
                return 0;
            }
            int header = this.headerCounts.getOrDefault(type, 0);
            int tagged = this.tagCounts.getOrDefault(type, 0);
            return header - tagged;
        }

        private static Map<ArdaniaSyncManager.ServerType, Integer> copyCounts(
                Map<ArdaniaSyncManager.ServerType, Integer> source) {
            EnumMap<ArdaniaSyncManager.ServerType, Integer> copy =
                    new EnumMap<>(ArdaniaSyncManager.ServerType.class);
            if (source != null) {
                copy.putAll(source);
            }
            return Collections.unmodifiableMap(copy);
        }

        /**
         * Erzeugt ein leeres Erkennungsergebnis fuer den Zustand {@link ArdaniaSyncManager.ServerType#UNKNOWN}.
         *
         * @param now Zeitstempel in Millisekunden
         * @return Leere {@link Detection}-Instanz
         */
        public static Detection unknown(long now) {
            return new Detection(
                    ArdaniaSyncManager.ServerType.UNKNOWN,
                    0.0,
                    Source.NONE,
                    "",
                    "",
                    new EnumMap<>(ArdaniaSyncManager.ServerType.class),
                    new EnumMap<>(ArdaniaSyncManager.ServerType.class),
                    List.of(),
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    now
            );
        }
    }

    /**
     * Eingabedaten fuer eine Erkennungsrunde.
     *
     * @param headerText        Aktueller Tablist-Header
     * @param footerText        Aktueller Tablist-Footer
     * @param tagCounts         Anzahl der Server-Tags anderer Spieler je Servertyp
     * @param sameWorldTags     Server-Tags von Spielern in derselben Welt
     * @param untaggedOthers    Anzahl ungetaggter anderer Tablist-Spieler
     * @param sameWorldUntagged Anzahl ungetaggter Spieler in derselben Welt
     * @param selfTag           Server-Tag am eigenen Tablist-Eintrag (falls vorhanden)
     * @param tabListSize       Gesamtgroesse der Tabliste
     * @param commandTreeReady  Ob der Brigadier-Befehlsbaum bereit ist
     * @param moneyAvailable    Ob {@code /geld} verfuegbar ist
     * @param jobsAvailable     Ob {@code /jobs} verfuegbar ist
     * @param creative          Ob der Spieler im Creative-Modus ist
     * @param override          Manuell gesetzter Server-Override
     * @param previous          Vorherige Erkennung fuer Hysterese und Beibehaltung
     */
    public record Input(
            String headerText,
            String footerText,
            Map<ArdaniaSyncManager.ServerType, Integer> tagCounts,
            List<String> sameWorldTags,
            int untaggedOthers,
            int sameWorldUntagged,
            String selfTag,
            int tabListSize,
            boolean commandTreeReady,
            boolean moneyAvailable,
            boolean jobsAvailable,
            boolean creative,
            ArdaniaSyncManager.ServerType override,
            Detection previous
    ) {
        /**
         * Normalisiert nullwertige Eingabefelder und stellt nicht-negative Zaehler sicher.
         */
        public Input {
            headerText = headerText != null ? headerText : "";
            footerText = footerText != null ? footerText : "";
            tagCounts = tagCounts != null ? tagCounts : Map.of();
            sameWorldTags = sameWorldTags != null ? List.copyOf(sameWorldTags) : List.of();
            untaggedOthers = Math.max(0, untaggedOthers);
            sameWorldUntagged = Math.max(0, sameWorldUntagged);
        }
    }

    private static final String SERVER_LABEL =
            "hauptserver|minenwelten|minenwelt|minenserver|minen-server|mine|bergwerk|"
                    + "creative|kreativwelten|kreativwelt|kreativ|"
                    + "eventserver|event(?:-?server)?|fallback(?:server)?";

    /**
     * Erkennt Eintraege wie {@code Hauptserver 6}, {@code Minenwelten: 4},
     * {@code [H] 12} oder {@code Creative 2/50}.
     */
    private static final Pattern COUNT_ENTRY = Pattern.compile(
            "(?iu)\\b(" + SERVER_LABEL + ")\\b(?:\\s*-?\\s*server)?\\s*[:=\\-\\u2013\\u2014\\u00BB>|]?\\s*(\\d{1,5})\\b");

    private static final Pattern SHORT_TAG_COUNT = Pattern.compile(
            "(?iu)\\[\\s*([HFMEC])\\s*]\\s*[:=\\-\\u2013]?\\s*(\\d{1,5})\\b");

    private static final Pattern SERVER_MENTION = Pattern.compile(
            "(?iu)\\b(" + SERVER_LABEL + ")\\b");

    private static final Pattern EXPLICIT_HINT = Pattern.compile(
            "(?iu)(?:du\\s+bist\\s+(?:jetzt\\s+)?(?:auf|im)(?:\\s+(?:dem|der|den|einem|einer))?|"
                    + "aktueller\\s+server|aktuelle\\s+welt|"
                    + "verbunden\\s+mit|befindest\\s+dich\\s+(?:auf|im)(?:\\s+(?:dem|der|den|einem|einer))?)"
                    + "\\s*[:=\\-\\u2013]?\\s*(" + SERVER_LABEL + ")");

    private static final double MIN_CONFIDENCE = 35.0;

    private ArdaniaServerDetector() {
    }

    /**
     * Wertet eine Erkennungsrunde aus.
     *
     * @param input Eingabedaten aus Tabliste, Befehlsbaum und Spielerzustand
     * @param now   Aktueller Zeitstempel
     * @return Erkennungsergebnis (niemals {@code null})
     */
    public static Detection evaluate(Input input, long now) {
        if (input == null) {
            return Detection.unknown(now);
        }

        ArdaniaSyncManager.ServerType override = input.override();
        if (override != null && override != ArdaniaSyncManager.ServerType.UNKNOWN) {
            return build(override, 100.0, Source.OVERRIDE, input, parseHeaderCounts(input), now);
        }

        Map<ArdaniaSyncManager.ServerType, Integer> headerCounts = parseHeaderCounts(input);

        // 1. Eine explizite Formulierung im Header hat immer Vorrang.
        ArdaniaSyncManager.ServerType explicit = parseExplicitHint(input.headerText(), input.footerText());
        if (explicit != null) {
            return build(explicit, 100.0, Source.HEADER, input, headerCounts, now);
        }

        // 2. Header-Zahlen: Ein einzelner Typ oder ein eindeutig groesstes,
        //    plausibles Delta bestimmt den Server direkt. Andere Signale -
        //    insbesondere ein evtl. veralteter eigener Tablisten-Tag - duerfen
        //    ein klares Header-Ergebnis nicht ueberstimmen.
        if (!headerCounts.isEmpty()) {
            if (headerCounts.size() == 1) {
                Map.Entry<ArdaniaSyncManager.ServerType, Integer> only =
                        headerCounts.entrySet().iterator().next();
                return build(only.getKey(), 95.0, Source.HEADER, input, headerCounts, now);
            }

            int expectedMinDelta = 1 + input.sameWorldUntagged();
            int maxDelta = maxPositiveDelta(headerCounts, input.tagCounts());
            if (maxDelta > 0) {
                List<ArdaniaSyncManager.ServerType> bestCandidates = new ArrayList<>();
                for (Map.Entry<ArdaniaSyncManager.ServerType, Integer> entry : headerCounts.entrySet()) {
                    int delta = entry.getValue() - input.tagCounts().getOrDefault(entry.getKey(), 0);
                    if (delta == maxDelta && delta >= expectedMinDelta) {
                        bestCandidates.add(entry.getKey());
                    }
                }
                if (bestCandidates.size() == 1) {
                    return build(bestCandidates.get(0), 100.0, Source.HEADER_COUNTS, input, headerCounts, now);
                }
            }
        }

        // 3. Ohne Header-Zahlen zaehlt eine eindeutige Nennung im Header/Footer.
        ArdaniaSyncManager.ServerType soleMention = findSoleMention(input.headerText(), input.footerText());
        if (soleMention != null) {
            return build(soleMention, 95.0, Source.HEADER, input, headerCounts, now);
        }

        // 4. Nur bei mehrdeutigem/fehlendem Header werden die uebrigen Signale
        //    gewichtet. Der eigene Tag ist dann die staerkste Einzelquelle.
        EnumMap<ArdaniaSyncManager.ServerType, Double> scores = new EnumMap<>(ArdaniaSyncManager.ServerType.class);
        EnumMap<ArdaniaSyncManager.ServerType, Source> sources = new EnumMap<>(ArdaniaSyncManager.ServerType.class);
        EnumMap<ArdaniaSyncManager.ServerType, Double> sourceWeights = new EnumMap<>(ArdaniaSyncManager.ServerType.class);

        // 4a. Mehrdeutige Header-Zahlen als Score (Tie-Breaker bzw. schwacher Favorit).
        if (!headerCounts.isEmpty()) {
            int expectedMinDelta = 1 + input.sameWorldUntagged();
            int maxDelta = maxPositiveDelta(headerCounts, input.tagCounts());
            for (Map.Entry<ArdaniaSyncManager.ServerType, Integer> entry : headerCounts.entrySet()) {
                int delta = entry.getValue() - input.tagCounts().getOrDefault(entry.getKey(), 0);
                if (delta <= 0) {
                    // Ein Delta von 0 bedeutet: Alle Spieler dieses Typs sind
                    // getaggt, also befinden sie sich auf einer anderen Welt.
                    continue;
                }
                boolean consistent = delta >= expectedMinDelta;
                if (delta == maxDelta) {
                    // Groesstes positives Delta: klarer Favorit.
                    add(scores, sources, sourceWeights, entry.getKey(),
                            consistent ? 95.0 : 60.0, Source.HEADER_COUNTS);
                } else if (consistent) {
                    // Positiv und plausibel, aber nicht das groesste Delta.
                    add(scores, sources, sourceWeights, entry.getKey(), 45.0, Source.HEADER_COUNTS);
                }
            }
        }

        // 4b. Eigener Tablisten-Tag (falls das Plugin den eigenen Eintrag taggt).
        ArdaniaSyncManager.ServerType selfType = ArdaniaSyncManager.ServerType.fromTag(input.selfTag());
        if (selfType != ArdaniaSyncManager.ServerType.UNKNOWN) {
            add(scores, sources, sourceWeights, selfType, 160.0, Source.SELF_TAG);
        }

        // 4c. Andere Spieler im selben Level sind definitiv auf demselben Unterserver.
        Set<String> uniqueSameWorldTags = new LinkedHashSet<>(input.sameWorldTags());
        for (String tag : uniqueSameWorldTags) {
            ArdaniaSyncManager.ServerType type = ArdaniaSyncManager.ServerType.fromTag(tag);
            if (type != ArdaniaSyncManager.ServerType.UNKNOWN) {
                add(scores, sources, sourceWeights, type, 55.0, Source.SAME_WORLD);
            }
        }

        // 4d. Creative-Gamemode ist ein starkes Indiz fuer den Creative-Server.
        if (input.creative()) {
            add(scores, sources, sourceWeights, ArdaniaSyncManager.ServerType.CREATIVE, 80.0, Source.CREATIVE);
        }

        // 4e. Befehlsbaum nur als schwaches Hilfssignal verwenden.
        if (input.commandTreeReady()) {
            if (!input.moneyAvailable() && !input.jobsAvailable()) {
                add(scores, sources, sourceWeights, ArdaniaSyncManager.ServerType.FALLBACK, 60.0, Source.COMMANDS);
            } else if (input.moneyAvailable() && !input.jobsAvailable()) {
                add(scores, sources, sourceWeights, ArdaniaSyncManager.ServerType.MINE, 40.0, Source.COMMANDS);
            } else if (input.moneyAvailable()) {
                add(scores, sources, sourceWeights, ArdaniaSyncManager.ServerType.HAUPTSERVER, 15.0, Source.COMMANDS);
            }
        }

        // 5. Bestes Ergebnis bestimmen. Bei Gleichstand bleibt der bisherige Typ stabil.
        ArdaniaSyncManager.ServerType best = null;
        double bestScore = 0.0;
        for (Map.Entry<ArdaniaSyncManager.ServerType, Double> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                best = entry.getKey();
            }
        }

        ArdaniaSyncManager.ServerType previousType = input.previous() != null
                ? input.previous().type()
                : ArdaniaSyncManager.ServerType.UNKNOWN;
        if (previousType != ArdaniaSyncManager.ServerType.UNKNOWN
                && scores.getOrDefault(previousType, 0.0) >= bestScore - 5.0) {
            best = previousType;
            bestScore = scores.getOrDefault(previousType, bestScore);
        }

        if (best == null || best == ArdaniaSyncManager.ServerType.UNKNOWN || bestScore < MIN_CONFIDENCE) {
            if (previousType != ArdaniaSyncManager.ServerType.UNKNOWN) {
                return build(previousType, Math.min(60.0, Math.max(35.0, bestScore)), Source.RETAINED, input, headerCounts, now);
            }
            return build(ArdaniaSyncManager.ServerType.UNKNOWN, 0.0, Source.NONE, input, headerCounts, now);
        }

        Source source = sources.getOrDefault(best, Source.NONE);
        return build(best, Math.min(100.0, bestScore), source, input, headerCounts, now);
    }

    /**
     * Parst alle Servertyp-Zahlen aus dem Header. Der Footer wird nur
     * ausgewertet, wenn der Header keine Zahlen enthaelt, damit sich beide
     * Quellen nicht widersprechen.
     *
     * @param input Eingabedaten mit Header- und Footer-Text
     * @return Map {@code ServerType -> Spielerzahl} (nie {@code null})
     */
    public static Map<ArdaniaSyncManager.ServerType, Integer> parseHeaderCounts(Input input) {
        return parseHeaderCounts(input != null ? input.headerText() : "", input != null ? input.footerText() : "");
    }

    /**
     * Parst Servertyp-Zahlen direkt aus Header- und Footer-Text.
     *
     * @param headerText Roher Tablist-Header
     * @param footerText Roher Tablist-Footer (Fallback, falls im Header keine Zahlen stehen)
     * @return Map {@code ServerType -> Spielerzahl} (nie {@code null})
     */
    public static Map<ArdaniaSyncManager.ServerType, Integer> parseHeaderCounts(String headerText, String footerText) {
        EnumMap<ArdaniaSyncManager.ServerType, Integer> counts = new EnumMap<>(ArdaniaSyncManager.ServerType.class);
        collectCounts(headerText, counts);
        if (counts.isEmpty()) {
            collectCounts(footerText, counts);
        }
        return counts;
    }

    private static void collectCounts(String text, Map<ArdaniaSyncManager.ServerType, Integer> counts) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = COUNT_ENTRY.matcher(text);
        while (matcher.find()) {
            ArdaniaSyncManager.ServerType type = typeForLabel(matcher.group(1));
            int count = parseCount(matcher.group(2));
            if (type != ArdaniaSyncManager.ServerType.UNKNOWN) {
                counts.merge(type, count, Math::max);
            }
        }

        Matcher shortMatcher = SHORT_TAG_COUNT.matcher(text);
        while (shortMatcher.find()) {
            ArdaniaSyncManager.ServerType type = ArdaniaSyncManager.ServerType.fromTag(shortMatcher.group(1));
            int count = parseCount(shortMatcher.group(2));
            if (type != ArdaniaSyncManager.ServerType.UNKNOWN) {
                counts.merge(type, count, Math::max);
            }
        }
    }

    private static int parseCount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Sucht einen eindeutigen Servertyp im Header/Footer, auch ohne Zahl.
     */
    static ArdaniaSyncManager.ServerType findSoleMention(String headerText, String footerText) {
        Set<ArdaniaSyncManager.ServerType> mentions = new LinkedHashSet<>();
        collectMentions(headerText, mentions);
        collectMentions(footerText, mentions);
        if (mentions.size() == 1) {
            return mentions.iterator().next();
        }
        return null;
    }

    private static void collectMentions(String text, Set<ArdaniaSyncManager.ServerType> mentions) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = SERVER_MENTION.matcher(text);
        while (matcher.find()) {
            ArdaniaSyncManager.ServerType type = typeForLabel(matcher.group(1));
            if (type != ArdaniaSyncManager.ServerType.UNKNOWN) {
                mentions.add(type);
            }
        }
    }

    /**
     * Sucht explizite Formulierungen wie "Du bist auf dem Hauptserver".
     */
    static ArdaniaSyncManager.ServerType parseExplicitHint(String headerText, String footerText) {
        ArdaniaSyncManager.ServerType fromHeader = parseExplicitHint(headerText);
        if (fromHeader != null) {
            return fromHeader;
        }
        return parseExplicitHint(footerText);
    }

    private static ArdaniaSyncManager.ServerType parseExplicitHint(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = EXPLICIT_HINT.matcher(text);
        if (matcher.find()) {
            ArdaniaSyncManager.ServerType type = typeForLabel(matcher.group(1));
            return type != ArdaniaSyncManager.ServerType.UNKNOWN ? type : null;
        }
        return null;
    }

    static ArdaniaSyncManager.ServerType typeForLabel(String label) {
        if (label == null) {
            return ArdaniaSyncManager.ServerType.UNKNOWN;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.startsWith("haupt")) {
            return ArdaniaSyncManager.ServerType.HAUPTSERVER;
        }
        if (lower.startsWith("minen") || lower.equals("mine") || lower.startsWith("bergwerk")) {
            return ArdaniaSyncManager.ServerType.MINE;
        }
        if (lower.startsWith("creative") || lower.startsWith("kreativ")) {
            return ArdaniaSyncManager.ServerType.CREATIVE;
        }
        if (lower.startsWith("event")) {
            return ArdaniaSyncManager.ServerType.EVENT;
        }
        if (lower.startsWith("fallback")) {
            return ArdaniaSyncManager.ServerType.FALLBACK;
        }
        return ArdaniaSyncManager.ServerType.UNKNOWN;
    }

    /**
     * Groesstes positives Delta {@code headerZahl - tagZahl} ueber alle im
     * Header genannten Servertypen. Liefert 0, wenn kein Typ ein positives
     * Delta besitzt.
     */
    private static int maxPositiveDelta(
            Map<ArdaniaSyncManager.ServerType, Integer> headerCounts,
            Map<ArdaniaSyncManager.ServerType, Integer> tagCounts
    ) {
        int max = 0;
        for (Map.Entry<ArdaniaSyncManager.ServerType, Integer> entry : headerCounts.entrySet()) {
            int delta = entry.getValue() - tagCounts.getOrDefault(entry.getKey(), 0);
            if (delta > max) {
                max = delta;
            }
        }
        return max;
    }

    private static void add(
            Map<ArdaniaSyncManager.ServerType, Double> scores,
            Map<ArdaniaSyncManager.ServerType, Source> sources,
            Map<ArdaniaSyncManager.ServerType, Double> sourceWeights,
            ArdaniaSyncManager.ServerType type,
            double weight,
            Source source
    ) {
        if (type == null || type == ArdaniaSyncManager.ServerType.UNKNOWN) {
            return;
        }
        scores.merge(type, weight, Double::sum);
        if (weight > sourceWeights.getOrDefault(type, -1.0)) {
            sourceWeights.put(type, weight);
            sources.put(type, source);
        }
    }

    private static Detection build(
            ArdaniaSyncManager.ServerType type,
            double confidence,
            Source source,
            Input input,
            Map<ArdaniaSyncManager.ServerType, Integer> headerCounts,
            long now
    ) {
        return new Detection(
                type,
                confidence,
                source,
                input.headerText(),
                input.footerText(),
                headerCounts,
                input.tagCounts(),
                new ArrayList<>(input.sameWorldTags()),
                input.untaggedOthers(),
                input.sameWorldUntagged(),
                input.tabListSize(),
                input.commandTreeReady(),
                input.moneyAvailable(),
                input.jobsAvailable(),
                input.creative(),
                now
        );
    }
}
