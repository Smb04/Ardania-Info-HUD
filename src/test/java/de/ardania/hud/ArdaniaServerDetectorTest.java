package de.ardania.hud;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer die reine Unterserver-Erkennung. Die Faelle bilden reale
 * Ardania-Tablisten ab, insbesondere den Header {@code Hauptserver 6} sowie
 * die globale Tabliste mit Header-Zahlen und Welt-Tags.
 */
class ArdaniaServerDetectorTest {

    private static final long NOW = 1_000L;

    private static ArdaniaServerDetector.Input input(
            String header,
            Map<ArdaniaSyncManager.ServerType, Integer> tagCounts,
            List<String> sameWorldTags,
            boolean money,
            boolean jobs
    ) {
        return input(header, "", tagCounts, sameWorldTags, 0, 0, 10, money, jobs);
    }

    private static ArdaniaServerDetector.Input input(
            String header,
            String footer,
            Map<ArdaniaSyncManager.ServerType, Integer> tagCounts,
            List<String> sameWorldTags,
            int untaggedOthers,
            int sameWorldUntagged,
            int tabListSize,
            boolean money,
            boolean jobs
    ) {
        return new ArdaniaServerDetector.Input(
                header,
                footer,
                tagCounts,
                sameWorldTags,
                untaggedOthers,
                sameWorldUntagged,
                null,
                tabListSize,
                true,
                money,
                jobs,
                false,
                ArdaniaSyncManager.ServerType.UNKNOWN,
                null
        );
    }

    private static ArdaniaServerDetector.Detection evaluate(ArdaniaServerDetector.Input input) {
        return ArdaniaServerDetector.evaluate(input, NOW);
    }

    private static Map<ArdaniaSyncManager.ServerType, Integer> tags(
            ArdaniaSyncManager.ServerType type, int count) {
        EnumMap<ArdaniaSyncManager.ServerType, Integer> map =
                new EnumMap<>(ArdaniaSyncManager.ServerType.class);
        map.put(type, count);
        return map;
    }

    private static Map<ArdaniaSyncManager.ServerType, Integer> tags(Object... entries) {
        EnumMap<ArdaniaSyncManager.ServerType, Integer> map =
                new EnumMap<>(ArdaniaSyncManager.ServerType.class);
        for (int i = 0; i < entries.length; i += 2) {
            map.put((ArdaniaSyncManager.ServerType) entries[i], (Integer) entries[i + 1]);
        }
        return map;
    }

    @Test
    void detectsHauptserverFromRealScreenshotHeader() {
        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Willkommen auf Ardania\nHauptserver 6",
                Map.of(),
                List.of(),
                true,
                true
        ));

        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER, detection.source());
        assertTrue(detection.confidence() >= 85.0);
    }

    @Test
    void detectsMineFromHeader() {
        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Willkommen auf Ardania\nMinenwelten 4",
                Map.of(),
                List.of(),
                true,
                false
        ));

        assertEquals(ArdaniaSyncManager.ServerType.MINE, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER, detection.source());
    }

    @Test
    void parsesPluralMinenwelten() {
        Map<ArdaniaSyncManager.ServerType, Integer> counts =
                ArdaniaServerDetector.parseHeaderCounts("Hauptserver 3 | Minenwelten 2", "");

        assertEquals(3, counts.get(ArdaniaSyncManager.ServerType.HAUPTSERVER));
        assertEquals(2, counts.get(ArdaniaSyncManager.ServerType.MINE));
    }

    @Test
    void doesNotRecognizeHauptweltenAsLabel() {
        Map<ArdaniaSyncManager.ServerType, Integer> counts =
                ArdaniaServerDetector.parseHeaderCounts("Hauptwelten 2", "");

        assertTrue(counts.isEmpty(), "Hauptwelten ist kein reales Ardania-Label");
    }

    @Test
    void detectsOwnServerFromHeaderCountDifference() {
        Map<ArdaniaSyncManager.ServerType, Integer> tagCounts = tags(
                ArdaniaSyncManager.ServerType.HAUPTSERVER, 41,
                ArdaniaSyncManager.ServerType.MINE, 7,
                ArdaniaSyncManager.ServerType.CREATIVE, 3
        );

        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Hauptserver 42 | Minenwelten 7 | Creative 3",
                tagCounts,
                List.of(),
                true,
                true
        ));

        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER_COUNTS, detection.source());
        assertEquals(42, detection.headerCounts().get(ArdaniaSyncManager.ServerType.HAUPTSERVER));
        assertEquals(7, detection.headerCounts().get(ArdaniaSyncManager.ServerType.MINE));
    }

    @Test
    void detectsMineFromRealDebugScreenshot() {
        // Debug-Fall: Header H=3, M=2; Tags H=2, M=0; Tabliste 5;
        // ein weiterer ungetaggter Spieler in derselben Welt.
        // Deltas: H=3-2=1, M=2-0=2. Erwartetes Minimum: 1+1=2.
        // Nur Mine erfuellt das Minimum und hat zugleich das groesste Delta.
        Map<ArdaniaSyncManager.ServerType, Integer> tagCounts = tags(
                ArdaniaSyncManager.ServerType.HAUPTSERVER, 2,
                ArdaniaSyncManager.ServerType.MINE, 0
        );

        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Hauptserver 3 | Minenwelten 2",
                "",
                tagCounts,
                List.of(),
                1,
                1,
                5,
                true,
                false
        ));

        assertEquals(ArdaniaSyncManager.ServerType.MINE, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER_COUNTS, detection.source());
        assertEquals(1, detection.delta(ArdaniaSyncManager.ServerType.HAUPTSERVER));
        assertEquals(2, detection.delta(ArdaniaSyncManager.ServerType.MINE));
        assertEquals(2, detection.expectedMinDelta());
        assertEquals(5, detection.tabListSize());
    }

    @Test
    void staleSelfTagDoesNotOverrideClearHeaderDelta() {
        // Realfall: Spieler ist auf dem Hauptserver, der eigene Tablisten-Eintrag
        // traegt aber noch [M]. Header H=5/M=1, Tags nur M=2 -> Delta H=5, M=-1.
        // Der eigene Tag darf das klare Header-Ergebnis nicht ueberstimmen.
        Map<ArdaniaSyncManager.ServerType, Integer> tagCounts = tags(
                ArdaniaSyncManager.ServerType.HAUPTSERVER, 0,
                ArdaniaSyncManager.ServerType.MINE, 2
        );

        ArdaniaServerDetector.Input input = new ArdaniaServerDetector.Input(
                "Willkommen auf Ardania | Hauptserver 5 | Minenwelten 1",
                "discord.ardania.de | www.ardania.de",
                tagCounts,
                List.of(),
                8,
                0,
                "M",
                11,
                true,
                true,
                true,
                false,
                ArdaniaSyncManager.ServerType.UNKNOWN,
                null
        );

        ArdaniaServerDetector.Detection detection = evaluate(input);

        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER_COUNTS, detection.source());
        assertNotEquals(ArdaniaServerDetector.Source.SELF_TAG, detection.source());
        assertTrue(detection.confidence() >= 85.0);
        assertEquals(5, detection.delta(ArdaniaSyncManager.ServerType.HAUPTSERVER));
        assertEquals(-1, detection.delta(ArdaniaSyncManager.ServerType.MINE));
    }

    @Test
    void detectsHauptserverAsCounterpartOfDebugScreenshot() {
        // Gleicher Header, aber auf dem Hauptserver: H-Spieler sind ungetaggt,
        // Mine-Spieler tragen [M]. Deltas: H=3, M=0 -> Hauptserver.
        Map<ArdaniaSyncManager.ServerType, Integer> tagCounts = tags(
                ArdaniaSyncManager.ServerType.HAUPTSERVER, 0,
                ArdaniaSyncManager.ServerType.MINE, 2
        );

        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Hauptserver 3 | Minenwelten 2",
                "",
                tagCounts,
                List.of(),
                1,
                1,
                5,
                true,
                true
        ));

        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER_COUNTS, detection.source());
        assertEquals(3, detection.delta(ArdaniaSyncManager.ServerType.HAUPTSERVER));
        assertEquals(0, detection.delta(ArdaniaSyncManager.ServerType.MINE));
    }

    @Test
    void deltaZeroDoesNotBeatPositiveDelta() {
        // Delta 0 (alle Spieler eines Typs getaggt) darf keinen positiven
        // Score erhalten, solange ein anderer Typ ein positives Delta hat.
        Map<ArdaniaSyncManager.ServerType, Integer> tagCounts = tags(
                ArdaniaSyncManager.ServerType.HAUPTSERVER, 3,
                ArdaniaSyncManager.ServerType.MINE, 0
        );

        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Hauptserver 3 | Minenwelten 2",
                "",
                tagCounts,
                List.of(),
                0,
                0,
                5,
                true,
                false
        ));

        assertEquals(ArdaniaSyncManager.ServerType.MINE, detection.type());
        assertEquals(0, detection.delta(ArdaniaSyncManager.ServerType.HAUPTSERVER));
        assertEquals(2, detection.delta(ArdaniaSyncManager.ServerType.MINE));
    }

    @Test
    void commandsCannotOverrideClearHeaderDelta() {
        // Selbst wenn der Befehlsbaum auf Fallback hindeutet (geld/jobs fehlen),
        // darf ein klares Header-Delta nicht ueberstimmt werden.
        Map<ArdaniaSyncManager.ServerType, Integer> tagCounts = tags(
                ArdaniaSyncManager.ServerType.HAUPTSERVER, 0,
                ArdaniaSyncManager.ServerType.MINE, 2
        );

        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Hauptserver 10 | Minenwelten 2",
                "",
                tagCounts,
                List.of(),
                0,
                0,
                12,
                false,
                false
        ));

        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER_COUNTS, detection.source());
        assertTrue(detection.confidence() >= 85.0);
    }

    @Test
    void detectsMineWhenAllOthersAreTaggedHauptserver() {
        // Regression: Frueher wurde aus "alle anderen tragen [H]" faelschlich
        // abgeleitet, der Spieler sei auf dem Hauptserver.
        ArdaniaServerDetector.Detection detection = evaluate(input(
                "",
                tags(ArdaniaSyncManager.ServerType.HAUPTSERVER, 10),
                List.of(),
                true,
                false
        ));

        assertEquals(ArdaniaSyncManager.ServerType.MINE, detection.type());
        assertEquals(ArdaniaServerDetector.Source.COMMANDS, detection.source());
    }

    @Test
    void parsesShortTagCounts() {
        Map<ArdaniaSyncManager.ServerType, Integer> tagCounts = tags(
                ArdaniaSyncManager.ServerType.HAUPTSERVER, 11,
                ArdaniaSyncManager.ServerType.MINE, 3
        );

        ArdaniaServerDetector.Detection detection = evaluate(input(
                "[H] 12 [M] 3",
                tagCounts,
                List.of(),
                true,
                true
        ));

        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER_COUNTS, detection.source());
    }

    @Test
    void parsesHeaderCountsWithDifferentSeparators() {
        Map<ArdaniaSyncManager.ServerType, Integer> counts =
                ArdaniaServerDetector.parseHeaderCounts(
                        "Hauptserver: 42 | Mine: 7 | Creative: 3 | Event: 1", "");

        assertEquals(42, counts.get(ArdaniaSyncManager.ServerType.HAUPTSERVER));
        assertEquals(7, counts.get(ArdaniaSyncManager.ServerType.MINE));
        assertEquals(3, counts.get(ArdaniaSyncManager.ServerType.CREATIVE));
        assertEquals(1, counts.get(ArdaniaSyncManager.ServerType.EVENT));
    }

    @Test
    void headerCountsTakePriorityOverFooter() {
        Map<ArdaniaSyncManager.ServerType, Integer> counts =
                ArdaniaServerDetector.parseHeaderCounts("Hauptserver 3", "Minenwelten 2");

        assertEquals(3, counts.get(ArdaniaSyncManager.ServerType.HAUPTSERVER));
        assertNull(counts.get(ArdaniaSyncManager.ServerType.MINE));
    }

    @Test
    void footerIsUsedWhenHeaderHasNoCounts() {
        Map<ArdaniaSyncManager.ServerType, Integer> counts =
                ArdaniaServerDetector.parseHeaderCounts("Willkommen", "Minenwelten 2");

        assertEquals(2, counts.get(ArdaniaSyncManager.ServerType.MINE));
    }

    @Test
    void detectsExplicitHeaderHint() {
        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Du bist auf dem Hauptserver",
                Map.of(),
                List.of(),
                true,
                true
        ));

        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, detection.type());
        assertEquals(ArdaniaServerDetector.Source.HEADER, detection.source());
    }

    @Test
    void creativeModeWinsWithoutHeader() {
        ArdaniaServerDetector.Input input = new ArdaniaServerDetector.Input(
                "", "", Map.of(), List.of(), 0, 0, null, 10,
                true, true, true, true,
                ArdaniaSyncManager.ServerType.UNKNOWN, null
        );

        ArdaniaServerDetector.Detection detection = evaluate(input);

        assertEquals(ArdaniaSyncManager.ServerType.CREATIVE, detection.type());
        assertEquals(ArdaniaServerDetector.Source.CREATIVE, detection.source());
    }

    @Test
    void missingCommandsIndicateFallback() {
        ArdaniaServerDetector.Detection detection = evaluate(input(
                "",
                Map.of(),
                List.of(),
                false,
                false
        ));

        assertEquals(ArdaniaSyncManager.ServerType.FALLBACK, detection.type());
        assertEquals(ArdaniaServerDetector.Source.COMMANDS, detection.source());
    }

    @Test
    void manualOverrideWinsOverHeader() {
        ArdaniaServerDetector.Input input = new ArdaniaServerDetector.Input(
                "Hauptserver 6", "", Map.of(), List.of(), 0, 0, null, 10,
                true, true, true, false,
                ArdaniaSyncManager.ServerType.MINE, null
        );

        ArdaniaServerDetector.Detection detection = evaluate(input);

        assertEquals(ArdaniaSyncManager.ServerType.MINE, detection.type());
        assertEquals(ArdaniaServerDetector.Source.OVERRIDE, detection.source());
        assertEquals(100.0, detection.confidence());
    }

    @Test
    void retainsLastTypeWhenTabListIsIncomplete() {
        ArdaniaServerDetector.Detection previous = evaluate(input(
                "Minenwelten 4",
                Map.of(),
                List.of(),
                true,
                false
        ));
        assertEquals(ArdaniaSyncManager.ServerType.MINE, previous.type());

        ArdaniaServerDetector.Input withoutEvidence = new ArdaniaServerDetector.Input(
                "", "", Map.of(), List.of(), 0, 0, null, 0,
                false, false, false, false,
                ArdaniaSyncManager.ServerType.UNKNOWN, previous
        );

        ArdaniaServerDetector.Detection detection = evaluate(withoutEvidence);

        assertEquals(ArdaniaSyncManager.ServerType.MINE, detection.type());
        assertEquals(ArdaniaServerDetector.Source.RETAINED, detection.source());
    }

    @Test
    void returnsUnknownWithoutAnyEvidence() {
        ArdaniaServerDetector.Input empty = new ArdaniaServerDetector.Input(
                "", "", Map.of(), List.of(), 0, 0, null, 0,
                false, false, false, false,
                ArdaniaSyncManager.ServerType.UNKNOWN, null
        );

        ArdaniaServerDetector.Detection detection = evaluate(empty);

        assertEquals(ArdaniaSyncManager.ServerType.UNKNOWN, detection.type());
        assertEquals(ArdaniaServerDetector.Source.NONE, detection.source());
    }

    @Test
    void emptyTagMapDoesNotBreakDetection() {
        ArdaniaServerDetector.Detection detection = evaluate(input(
                "Hauptserver 1",
                Map.of(),
                List.of(),
                true,
                true
        ));

        assertNotNull(detection.headerCounts());
        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, detection.type());
    }
}
