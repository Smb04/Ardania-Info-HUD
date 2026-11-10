package de.ardania.hud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArdaniaMessageParserTest {

    private ArdaniaState state;
    private ArdaniaMessageParser parser;

    @BeforeEach
    void setUp() {
        state = new ArdaniaState();
        parser = new ArdaniaMessageParser(state);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "[ArdaBank] Dein Kontostand: 38.283,13 Euronen | 38283.13",
            "Kontostand: 1.250,50 Euronen                    | 1250.50",
            "Geld: 125,00 Euronen                            | 125.00",
            "\u00A76Kontostand: \u00A7e0,02 Euronen          | 0.02",
            "Dein Guthaben: 1.000.000 Euronen                | 1000000.00",
            "Kontostand: 7 €                                 | 7.00",
    })
    void absoluteBalance(String message, String expected) {
        state.setEuronen(new BigDecimal("999"));
        assertTrue(parser.parse(message, false));
        assertEquals(new BigDecimal(expected), state.getEuronen());
        assertTrue(state.isEuronenKnown());
    }

    @Test
    void ardaBankPositiveDelta() {
        state.setEuronen(new BigDecimal("100.00"));
        assertTrue(parser.parse("[ArdaBank] Buchung: +50,00 Euronen", false));
        assertEquals(new BigDecimal("150.00"), state.getEuronen());
    }

    @Test
    void ardaBankNegativeDelta() {
        state.setEuronen(new BigDecimal("100.00"));
        assertTrue(parser.parse("[ArdaBank] Buchung: -25,00 Euronen", false));
        assertEquals(new BigDecimal("75.00"), state.getEuronen());
    }

    @Test
    void ardaBankTransferDeduction() {
        state.setEuronen(new BigDecimal("100.00"));
        assertTrue(parser.parse("[ArdaBank] Du hast 30,00 Euronen an Spieler123 ueberwiesen", false));
        assertEquals(new BigDecimal("70.00"), state.getEuronen());
    }

    @Test
    void ardaBankCredit() {
        state.setEuronen(new BigDecimal("100.00"));
        assertTrue(parser.parse("[ArdaBank] Dir wurden 15,50 Euronen gutgeschrieben", false));
        assertEquals(new BigDecimal("115.50"), state.getEuronen());
    }

    @Test
    void actionBarTriggerIdentified() {
        assertTrue(parser.isActionBarIncome("+0.15$ +0.42XP"));
        assertTrue(parser.isActionBarIncome("+0,02 Euronen"));
    }

    @Test
    void testIsSilentServerResponse() {
        assertTrue(parser.isSilentServerResponse("[ArdaBank] Dein Kontostand: 38.283,13 Euronen"));
        assertTrue(parser.isSilentServerResponse("[ArdaniaPoints] Du besitzt 2,578 ArdaniaPoints"));
        assertTrue(parser.isSilentServerResponse("[ArdaniaJobs] Spieler: PX_Sven"));
        assertTrue(parser.isSilentServerResponse("|||||||||||||||||||||||||||| Level 24 for Mobhunter: 156.26/3120xp"));
        assertFalse(parser.isSilentServerResponse("+0.15$ +0.42XP"));
        assertFalse(parser.isSilentServerResponse("Hallo im Chat"));
    }

    @Test
    void testUnknownCommandErrorSuppression() {
        assertTrue(parser.isUnknownCommandError("Unknown command. Type \"/help\" for help."));
        assertTrue(parser.isUnknownCommandError("Unbekannter Befehl. Schreibe /help fuer Hilfe."));
        assertTrue(parser.isUnknownCommandError("Befehl wurde nicht gefunden."));
        assertFalse(parser.isUnknownCommandError("Hallo zusammen"));
    }

    @Test
    void ardapointsFromScreenshot() {
        state.setArdaPoints(10);

        assertTrue(parser.parse("[ArdaniaPoints] Du besitzt 2,578 ArdaniaPoints", false));
        assertEquals(2578, state.getArdaPoints());

        state.setArdaPoints(0);
        assertTrue(parser.parse("Deine ArdaPoints: 2578 Punkte", false));
        assertEquals(2578, state.getArdaPoints());
    }

    @Test
    void dailyRewardAddsArdaPoints() {
        state.setArdaPoints(100);
        assertTrue(parser.parse("Du hast 25 Ardapoints erhalten", false));
        assertEquals(125, state.getArdaPoints());
    }

    @Test
    void voteRewardAddsArdaPoints() {
        state.setArdaPoints(50);
        assertTrue(parser.parse("Danke f\u00FCrs Voten! Du hast 25 ArdaPoints erhalten.", false));
        assertEquals(75, state.getArdaPoints());
        assertTrue(parser.isVoteNotification("Danke f\u00FCrs Voten! Du hast 25 ArdaPoints erhalten."));
    }

    @Test
    void voteGoalRewardAddsArdaPointsAndTriggersSync() {
        state.setArdaPoints(100);
        assertTrue(parser.parse("Das Voteziel wurde erreicht! Jeder Spieler erh\u00E4lt 50 ArdaPoints!", false));
        assertEquals(150, state.getArdaPoints());

        assertTrue(parser.parse("Das Vote-Ziel von 100 Votes wurde erreicht! Alle Spieler erhalten 25 ArdaPoints.", false));
        assertEquals(175, state.getArdaPoints());

        assertTrue(parser.parse("Dir wurden 50 ArdaPoints f\u00FCr das Voteziel gutgeschrieben.", false));
        assertEquals(225, state.getArdaPoints());

        assertTrue(parser.parse("F\u00FCr das Erreichen des Voteziels bekommst du 30 ArdaPoints!", false));
        assertEquals(255, state.getArdaPoints());

        assertTrue(parser.isVoteNotification("Das Voteziel wurde erreicht!"));
        assertTrue(parser.isVoteNotification("Das Vote-Ziel wurde erreicht!"));
        assertFalse(parser.isPointsBalanceResponse("Das Voteziel wurde erreicht! Jeder Spieler erh\u00E4lt 50 ArdaPoints!"));
        assertTrue(parser.isPointsBalanceResponse("[ArdaniaPoints] Du besitzt 2,578 ArdaniaPoints"));
        assertTrue(parser.isPointsBalanceResponse("\u00A7a[ArdaniaPoints] \u00A77Du besitzt aktuell: \u00A7e2.578 ArdaniaPoints"));
    }

    @Test
    void parseJobsStatsFromScreenshot() {
        parser.parse("\u00A7a[ArdaniaJobs] \u00A7bSpieler: PX_Sven", false);
        parser.parse("|||||||||||||||||||||||||||| Level 24 for Mobhunter: 156.26/3120xp", false);
        parser.parse("|||||||||||||||||||||||||||| Level 12 for Farmer: 1208.04/1788xp", false);
        parser.parse("|||||||||||||||||||||||||||| Level 21 for Miner: 1461.84/2783xp", false);

        List<ArdaniaState.JobEntry> jobs = state.getJobs();
        assertEquals(3, jobs.size());
        assertEquals("Mobhunter", jobs.get(0).name());
        assertEquals(24, jobs.get(0).level());
    }

    @Test
    void parseAllOtherJobTypes() {
        parser.parse("[ArdaniaJobs] Spieler: PX_Sven", false);
        parser.parse("|||||||||||||||||||||||||||| Level 15 for Fischer: 200/500xp", false);
        parser.parse("|||||||||||||||||||||||||||| Level 30 for Handwerker: 100/1000xp", false);
        parser.parse("|||||||||||||||||||||||||||| Level 10 for Hexenmeister: 50/300xp", false);
        parser.parse("|||||||||||||||||||||||||||| Level 42 for Holzf\u00E4ller: 500/2000xp", false);
        parser.parse("|||||||||||||||||||||||||||| Level 8 for Schaufler: 10/100xp", false);

        List<ArdaniaState.JobEntry> jobs = state.getJobs();
        assertEquals(5, jobs.size());
        assertEquals("Fischer", jobs.get(0).name());
        assertEquals(15, jobs.get(0).level());
        assertEquals("Handwerker", jobs.get(1).name());
        assertEquals(30, jobs.get(1).level());
        assertEquals("Hexenmeister", jobs.get(2).name());
        assertEquals(10, jobs.get(2).level());
        assertEquals("Holzf\u00E4ller", jobs.get(3).name());
        assertEquals(42, jobs.get(3).level());
        assertEquals("Schaufler", jobs.get(4).name());
        assertEquals(8, jobs.get(4).level());
    }

    @Test
    void parseOntimeMultiLineFullBlock() {
        String fullChatBlock = "Name: PX_Sven\n" +
                "Beigetreten am: 23.02.2026 15:01\n" +
                "Hauptserver: 16 Tage 3 Stunden 17 Minute\n" +
                "Creative: 0 Tage 0 Stunden 13 Minute\n" +
                "Eventserver: 0 Tage 0 Stunden 38 Minute\n" +
                "Mine: 0 Tage 7 Stunden 18 Minute\n" +
                "-----------------------------------------------------";

        assertTrue(parser.parse(fullChatBlock, false));
        assertTrue(state.isPlaytimeKnown());
        assertEquals(23726, state.getCurrentTotalMinutes());
        assertEquals("16d 11h 26m", state.getFormattedPlaytime());
    }

    @Test
    void foreignOntimeBlockIsIgnored() {
        state.updateServerPlaytime("Hauptserver", 16, 3, 17);
        state.updateServerPlaytime("Mine", 0, 7, 18);
        long before = state.getCurrentTotalMinutes();

        String foreignBlock = "Name: LordSandukhan\n" +
                "Beigetreten am: 12.04.2015 18:03\n" +
                "Hauptserver: 1384 Tage 19 Stunden 44 Minute\n" +
                "Creative: 7 Tage 0 Stunden 41 Minute\n" +
                "Eventserver: 2 Tage 18 Stunden 36 Minute\n" +
                "Mine: 0 Tage 21 Stunden 50 Minute\n" +
                "Skyblock: 0 Tage 2 Stunden 34 Minute\n" +
                "-----------------------------------------------------";

        assertFalse(parser.parse(foreignBlock, false, "PX_Sven"));
        assertEquals(before, state.getCurrentTotalMinutes());
        assertEquals("16d 10h 35m", state.getFormattedPlaytime());
    }

    @Test
    void fullOntimeBlockPrunesStaleAndUnknownKeys() {
        state.updateServerPlaytime("Hauptserver", 1, 0, 0);
        state.updateServerPlaytime("Skyblock", 5, 0, 0);
        state.updateServerPlaytime("alter-server", 9, 0, 0);

        String ownBlock = "Name: PX_Sven\n" +
                "Beigetreten am: 23.02.2026 15:01\n" +
                "Hauptserver: 16 Tage 3 Stunden 17 Minute\n" +
                "Creative: 0 Tage 0 Stunden 13 Minute\n" +
                "Eventserver: 0 Tage 0 Stunden 38 Minute\n" +
                "Mine: 0 Tage 7 Stunden 18 Minute\n" +
                "Skyblock: 0 Tage 2 Stunden 34 Minute\n" +
                "-----------------------------------------------------";

        assertTrue(parser.parse(ownBlock, false, "PX_Sven"));
        // Nur die vier unterstuetzten Welten zaehlen, Skyblock/stale Keys fallen weg.
        assertEquals(23726, state.getCurrentTotalMinutes());
        assertEquals("16d 11h 26m", state.getFormattedPlaytime());
    }

    @Test
    void singleServerLineDoesNotWipeOtherWorlds() {
        String ownBlock = "Name: PX_Sven\n" +
                "Beigetreten am: 23.02.2026 15:01\n" +
                "Hauptserver: 16 Tage 3 Stunden 17 Minute\n" +
                "Creative: 0 Tage 0 Stunden 13 Minute\n" +
                "Eventserver: 0 Tage 0 Stunden 38 Minute\n" +
                "Mine: 0 Tage 7 Stunden 18 Minute\n" +
                "-----------------------------------------------------";
        assertTrue(parser.parse(ownBlock, false, "PX_Sven"));

        assertTrue(parser.parse("Mine: 0 Tage 8 Stunden 0 Minute", false, "PX_Sven"));

        assertEquals(23768, state.getCurrentTotalMinutes());
        assertEquals("16d 12h 8m", state.getFormattedPlaytime());
    }

    @Test
    void ontimeSilentSuppression() {
        assertTrue(parser.isSilentServerResponse("-----------------------------------------------------"));
        assertTrue(parser.isSilentServerResponse("Name: PX_Sven"));
        assertTrue(parser.isSilentServerResponse("Beigetreten am: 23.02.2026 15:01"));
        assertTrue(parser.isSilentServerResponse("Hauptserver: 16 Tage 3 Stunden 5 Minute"));
        assertTrue(parser.isSilentServerResponse("Creative: 0 Tage 0 Stunden 13 Minute"));
        assertTrue(parser.isSilentServerResponse("Eventserver: 0 Tage 0 Stunden 38 Minute"));
        assertTrue(parser.isSilentServerResponse("Mine: 0 Tage 7 Stunden 10 Minute"));
    }

    @Test
    void ardapointsErrorSuppressed() {
        assertTrue(parser.isUnknownCommandError("ardapoints me ist kein gueltiger Befehl"));
        assertTrue(parser.isUnknownCommandError("[Ardania] Fehler: Der Befehl /ardapoints existiert hier nicht"));
        assertTrue(parser.isUnknownCommandError("Du kannst diesen befehl in dieser Welt nicht benutzen"));
        assertTrue(parser.isUnknownCommandError("Du kannst diesen Befehl in dieser Welt nicht benutzen."));
        assertTrue(parser.isUnknownCommandError("\u00A7cDu kannst diesen Befehl in dieser Welt nicht benutzen!"));
    }

    @Test
    void parseJobWithMaxLevel() {
        state.clearJobs();
        assertTrue(parser.parse("      Max   Level 30 for Mobhunter", false));
        assertTrue(parser.parse("\u00A7a      Max   \u00A77Level \u00A7f30 \u00A77for \u00A7fHandwerker", false));
        assertTrue(parser.parse("|||||||||||||||||||||||||||| MAX for Schaufler: 3120/3120xp", false));
        assertTrue(parser.parse("|||||||||||||||||||||||||||| Level MAX for Miner: 3120/3120xp", false));
        assertTrue(parser.parse("|||||||||||||||||||||||||||| Level 30 for Farmer: 3120/3120xp", false));
        assertTrue(parser.parse("|||||||||||||||||||||||||||| Stufe MAX f\u00FCr Holzf\u00E4ller: 3120/3120xp", false));
        assertTrue(parser.parse("Job: Fischer - MAX", false));

        List<ArdaniaState.JobEntry> jobs = state.getJobs();
        assertEquals(7, jobs.size());

        for (ArdaniaState.JobEntry j : jobs) {
            assertTrue(j.isMax(), "Job " + j.name() + " should be MAX");
            assertEquals(30, j.level(), "Job " + j.name() + " level should be 30");
            assertEquals("MAX", j.displayLevel(), "Job " + j.name() + " displayLevel should be MAX");
        }

        assertTrue(parser.isSilentServerResponse("      Max   Level 30 for Mobhunter"));
        assertTrue(parser.isSilentServerResponse("\u00A7a      Max   \u00A77Level \u00A7f30 \u00A77for \u00A7fMobhunter"));
        assertTrue(parser.isSilentServerResponse("|||||||||||||||||||||||||||| MAX for Mobhunter: 3120/3120xp"));
        assertTrue(parser.isSilentServerResponse("|||||||||||||||||||||||||||| Level MAX for Miner: 3120/3120xp"));
    }

    @Test
    void parseMixedMaxAndNormalJobsFromTesterScreenshot() {
        parser.parse("[ArdaniaJobs] Spieler: Shadstorm", false);
        assertTrue(parser.parse("      Max   Level 30 for Mobhunter", false));
        assertTrue(parser.parse("\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F Level 28 for Miner: 1461.84/2783xp", false));
        assertTrue(parser.parse("\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502 Level 23 for Hexenmeister: 500/2000xp", false));

        assertTrue(parser.isSilentServerResponse("\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F\u258F Level 28 for Miner: 1461.84/2783xp"));
        assertTrue(parser.isSilentServerResponse("\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502\u2502 Level 23 for Hexenmeister: 500/2000xp"));

        List<ArdaniaState.JobEntry> jobs = state.getJobs();
        assertEquals(3, jobs.size());

        assertEquals("Mobhunter", jobs.get(0).name());
        assertTrue(jobs.get(0).isMax());
        assertEquals("MAX", jobs.get(0).displayLevel());

        assertEquals("Miner", jobs.get(1).name());
        assertFalse(jobs.get(1).isMax());
        assertEquals("28", jobs.get(1).displayLevel());

        assertEquals("Hexenmeister", jobs.get(2).name());
        assertFalse(jobs.get(2).isMax());
        assertEquals("23", jobs.get(2).displayLevel());
    }

    @Test
    void autoHideAndManualHideStateTransitions() {
        state.reset();
        long durationMs = 5000L;

        state.notifyStatUpdated();
        long now = System.currentTimeMillis();
        assertTrue(state.isAutoHideActive(now, durationMs));

        // Nach Ablauf der Anzeigedauer gilt das HUD als ausgeblendet
        assertFalse(state.isAutoHideActive(now + 5001L, durationMs));

        // Manuelles Ausblenden während der Anzeigezeit beendet die Sichtbarkeit sofort
        state.notifyStatUpdated();
        assertTrue(state.isAutoHideActive(System.currentTimeMillis(), durationMs));
        state.hideHudNow();
        assertFalse(state.isAutoHideActive(System.currentTimeMillis(), durationMs));
    }

    @Test
    void statUpdateTimestampExcludesPlaytimeAndUnchangedValues() throws InterruptedException {
        state.reset();
        assertEquals(0, state.getLastNonPlaytimeUpdateTimestamp());

        state.updateServerPlaytime("Hauptserver", 1, 0, 0);
        assertEquals(0, state.getLastNonPlaytimeUpdateTimestamp());

        state.setEuronen(BigDecimal.valueOf(100));
        long t1 = state.getLastNonPlaytimeUpdateTimestamp();
        assertTrue(t1 > 0);

        Thread.sleep(5);
        state.setEuronen(new BigDecimal("100.00"));
        assertEquals(t1, state.getLastNonPlaytimeUpdateTimestamp());

        state.updateServerPlaytime("Mine", 0, 1, 0);
        assertEquals(t1, state.getLastNonPlaytimeUpdateTimestamp());

        Thread.sleep(5);
        state.setArdaPoints(50);
        long t2 = state.getLastNonPlaytimeUpdateTimestamp();
        assertTrue(t2 > t1);

        Thread.sleep(5);
        state.setArdaPoints(50);
        assertEquals(t2, state.getLastNonPlaytimeUpdateTimestamp());

        Thread.sleep(5);
        state.addOrUpdateJob("Miner", 30, true);
        long t3 = state.getLastNonPlaytimeUpdateTimestamp();
        assertTrue(t3 > t2);

        Thread.sleep(5);
        state.addOrUpdateJob("Miner", 30, true);
        assertEquals(t3, state.getLastNonPlaytimeUpdateTimestamp());
    }

    @Test
    void jobNotificationDetected() {
        assertTrue(parser.isJobNotification("[ArdaniaJobs] Du bist jetzt Level 25 als Miner!"));
        assertTrue(parser.isJobNotification("[ArdaniaJobs] Du hast den Job Fischer angenommen."));
        assertFalse(parser.isJobNotification("[ArdaniaJobs] Spieler: PX_Sven"));
        assertFalse(parser.isJobNotification("|||||||||||||||||||||||||||| Level 24 for Mobhunter: 156.26/3120xp"));
    }

    @Test
    void tabDisplayLogicRespectsConfiguredDuration() {
        long pressStart = 1000L;

        for (int seconds : new int[]{3, 5, 8, 10, 15}) {
            long durationMs = seconds * 1000L;

            assertFalse(ArdaniaHudRenderer.isTabDisplayActive(false, true, pressStart, 1500L, durationMs));
            assertTrue(ArdaniaHudRenderer.isTabDisplayActive(true, true, pressStart, pressStart + durationMs + 5000L, durationMs));

            assertTrue(ArdaniaHudRenderer.isTabDisplayActive(true, false, pressStart, pressStart + 150L, durationMs));
            assertTrue(ArdaniaHudRenderer.isTabDisplayActive(true, false, pressStart, pressStart + durationMs - 1L, durationMs));
            assertFalse(ArdaniaHudRenderer.isTabDisplayActive(true, false, pressStart, pressStart + durationMs + 1L, durationMs));
        }
    }

    @Test
    void stateCachePersistenceAndPartialFallback(@TempDir Path tempDir) {
        Path cacheFile = tempDir.resolve("ardania_hud_cache.json");

        state.setEuronen(new BigDecimal("46571.90"));
        state.setArdaPoints(4987745);
        state.addOrUpdateJob("Mobhunter", 30, true);
        state.addOrUpdateJob("Miner", 28, false);
        state.updateServerPlaytime("Hauptserver", 10, 5, 30);
        state.updateServerPlaytime("Mine", 1, 2, 15);
        state.saveCache(cacheFile);

        ArdaniaState loadedState = new ArdaniaState();
        ArdaniaMessageParser loadedParser = new ArdaniaMessageParser(loadedState);
        loadedState.loadCache(cacheFile);

        assertTrue(loadedState.isEuronenKnown());
        assertEquals(new BigDecimal("46571.90"), loadedState.getEuronen());
        assertTrue(loadedState.isArdaPointsKnown());
        assertEquals(4987745, loadedState.getArdaPoints());
        assertTrue(loadedState.isPlaytimeKnown());
        assertEquals("11d 7h 45m", loadedState.getFormattedPlaytime());

        List<ArdaniaState.JobEntry> jobs = loadedState.getJobs();
        assertEquals(2, jobs.size());
        assertEquals("Mobhunter", jobs.get(0).name());
        assertTrue(jobs.get(0).isMax());
        assertEquals("MAX", jobs.get(0).displayLevel());
        assertEquals("Miner", jobs.get(1).name());
        assertEquals("28", jobs.get(1).displayLevel());

        // Wenn auf einem Unterserver (z. B. Mine/Nether) nur /geld verfuegbar ist,
        // bleiben die zwischengespeicherten Jobs, ArdaPoints und Spielzeiten erhalten:
        assertTrue(loadedParser.parse("[ArdaBank] Dein Kontostand: 47.000,00 Euronen", false));
        assertFalse(loadedParser.parse("Unbekannter Befehl.", false));
        assertEquals(new BigDecimal("47000.00"), loadedState.getEuronen());
        assertEquals(4987745, loadedState.getArdaPoints());
        assertEquals(2, loadedState.getJobs().size());
        assertEquals("11d 7h 45m", loadedState.getFormattedPlaytime());
    }

    @Test
    void localizationSupportsGermanEnglishSpanishAndFrench() {
        var deMap = ArdaniaI18n.getLanguageMap(ArdaniaConfig.HudLanguage.DE_DE);
        var enMap = ArdaniaI18n.getLanguageMap(ArdaniaConfig.HudLanguage.EN_US);
        var esMap = ArdaniaI18n.getLanguageMap(ArdaniaConfig.HudLanguage.ES_ES);
        var frMap = ArdaniaI18n.getLanguageMap(ArdaniaConfig.HudLanguage.FR_FR);

        assertFalse(deMap.isEmpty());
        assertEquals(deMap.keySet(), enMap.keySet());
        assertEquals(deMap.keySet(), esMap.keySet());
        assertEquals(deMap.keySet(), frMap.keySet());

        assertEquals("Spielzeit", ArdaniaI18n.getString(ArdaniaConfig.HudLanguage.DE_DE, "hud.ardania_hud.playtime"));
        assertEquals("Playtime", ArdaniaI18n.getString(ArdaniaConfig.HudLanguage.EN_US, "hud.ardania_hud.playtime"));
        assertEquals("Tiempo de juego", ArdaniaI18n.getString(ArdaniaConfig.HudLanguage.ES_ES, "hud.ardania_hud.playtime"));
        assertEquals("Temps de jeu", ArdaniaI18n.getString(ArdaniaConfig.HudLanguage.FR_FR, "hud.ardania_hud.playtime"));

        assertEquals("Job", ArdaniaI18n.getString(ArdaniaConfig.HudLanguage.DE_DE, "hud.ardania_hud.job"));
        assertEquals("Job", ArdaniaI18n.getString(ArdaniaConfig.HudLanguage.EN_US, "hud.ardania_hud.job"));
        assertEquals("Trabajo", ArdaniaI18n.getString(ArdaniaConfig.HudLanguage.ES_ES, "hud.ardania_hud.job"));
        assertEquals("M\u00E9tier", ArdaniaI18n.getString(ArdaniaConfig.HudLanguage.FR_FR, "hud.ardania_hud.job"));

        assertEquals(ArdaniaConfig.HudLanguage.DE_DE, ArdaniaI18n.resolveLanguage(ArdaniaConfig.HudLanguage.AUTO, "de_de"));
        assertEquals(ArdaniaConfig.HudLanguage.DE_DE, ArdaniaI18n.resolveLanguage(ArdaniaConfig.HudLanguage.AUTO, "de_at"));
        assertEquals(ArdaniaConfig.HudLanguage.EN_US, ArdaniaI18n.resolveLanguage(ArdaniaConfig.HudLanguage.AUTO, "en_gb"));
        assertEquals(ArdaniaConfig.HudLanguage.ES_ES, ArdaniaI18n.resolveLanguage(ArdaniaConfig.HudLanguage.AUTO, "es_mx"));
        assertEquals(ArdaniaConfig.HudLanguage.FR_FR, ArdaniaI18n.resolveLanguage(ArdaniaConfig.HudLanguage.AUTO, "fr_ca"));
        assertEquals(ArdaniaConfig.HudLanguage.FR_FR, ArdaniaI18n.resolveLanguage(ArdaniaConfig.HudLanguage.FR_FR, "de_de"));
    }

    @Test
    void playtimeFilteringAndSeparateServerDisplay() {
        String fullChatBlock = "Name: PX_Sven\n" +
                "Beigetreten am: 23.02.2026 15:01\n" +
                "Hauptserver: 17 Tage 9 Stunden 21 Minute\n" +
                "Creative: 0 Tage 0 Stunden 13 Minute\n" +
                "Eventserver: 0 Tage 0 Stunden 38 Minute\n" +
                "Mine: 0 Tage 12 Stunden 10 Minute\n" +
                "-----------------------------------------------------";

        assertTrue(parser.parse(fullChatBlock, false));

        // Alle 4 Server kombiniert: 17d 9h 21m (25041m) + 13m + 38m + 12h 10m (730m) = 25822m = 17d 22h 22m
        assertEquals(25822L, state.getFilteredTotalMinutes(true, true, true, true));
        assertEquals("17d 22h 22m", ArdaniaState.formatMinutes(state.getFilteredTotalMinutes(true, true, true, true)));

        // Nur Hauptserver + Mine: 25041m + 730m = 25771m = 17d 21h 31m
        assertEquals(25771L, state.getFilteredTotalMinutes(true, false, false, true));
        assertEquals("17d 21h 31m", ArdaniaState.formatMinutes(state.getFilteredTotalMinutes(true, false, false, true)));

        // Nur Hauptserver: 25041m = 17d 9h 21m
        assertEquals(25041L, state.getFilteredTotalMinutes(true, false, false, false));
        assertEquals("17d 9h 21m", ArdaniaState.formatMinutes(state.getFilteredTotalMinutes(true, false, false, false)));
    }

    @Test
    void sessionTimerAndPositionLeftRight() {
        state.startSession(1_000L);
        assertEquals("0m 0s", state.getFormattedSessionTime(1_000L));
        assertEquals("2m 5s", state.getFormattedSessionTime(1_000L + 125_000L));
        assertEquals("1h 1m 1s", state.getFormattedSessionTime(1_000L + 3_661_000L));

        assertFalse(ArdaniaConfig.HudPosition.MIDDLE_RIGHT.isLeft());
        assertFalse(ArdaniaConfig.HudPosition.TOP_RIGHT.isLeft());
        assertFalse(ArdaniaConfig.HudPosition.BOTTOM_RIGHT.isLeft());
        assertTrue(ArdaniaConfig.HudPosition.MIDDLE_LEFT.isLeft());
        assertTrue(ArdaniaConfig.HudPosition.TOP_LEFT.isLeft());
        assertTrue(ArdaniaConfig.HudPosition.BOTTOM_LEFT.isLeft());
    }

    @Test
    void tabServerTagDistinguishesServerTagsFromGuildTags() {
        assertEquals("H", ArdaniaMessageParser.extractTabServerTag("[H] [Bier] PX_Sven"));
        assertEquals("M", ArdaniaMessageParser.extractTabServerTag("\u00A77[Gilde] \u00A7aSpieler123 \u00A78[\u00A7eM\u00A78]"));
        assertEquals("F", ArdaniaMessageParser.extractTabServerTag("[F] [ARDA] Tester"));
        assertEquals("E", ArdaniaMessageParser.extractTabServerTag("[ClanTag] EventPlayer [E]"));
        assertEquals("C", ArdaniaMessageParser.extractTabServerTag("[C] [Builder] Sven"));

        // Reine Gildentags oder Rang-Tags ohne [H]/[F]/[M]/[E]/[C] duerfen nicht als Server erkannt werden
        assertNull(ArdaniaMessageParser.extractTabServerTag("[Bier] PX_Sven"));
        assertNull(ArdaniaMessageParser.extractTabServerTag("[ARDA] Spieler123"));
        assertNull(ArdaniaMessageParser.extractTabServerTag("[HM] Spieler456"));
        assertNull(ArdaniaMessageParser.extractTabServerTag("[F1] Spieler789"));
        assertNull(ArdaniaMessageParser.extractTabServerTag("[+] PX_Sven"));
    }

    @Test
    void fallbackBrigadierUnknownCommandErrorsAreDetectedAndBlocked() {
        assertTrue(parser.isUnknownCommandError("Unknown or incomplete command. See below for error"));
        assertTrue(parser.isUnknownCommandError("geld<--[HERE]"));
        assertTrue(parser.isUnknownCommandError("jobs stats<--[HERE]"));
        assertTrue(parser.isUnknownCommandError("Unbekannter oder unvollst\u00E4ndiger Befehl, siehe unten f\u00FCr Fehler"));
        assertTrue(parser.isUnknownCommandError("geld<--[HIER]"));
        assertTrue(parser.isUnknownCommandError("jobs stats<--[HIER]"));

        assertEquals("geld", parser.extractFailedCommandFromContext("geld<--[HERE]"));
        assertEquals("jobs stats", parser.extractFailedCommandFromContext("jobs stats<--[HERE]"));
        assertEquals("ardapoints me", parser.extractFailedCommandFromContext("ardapoints me<--[HIER]"));
        assertEquals("ontime", parser.extractFailedCommandFromContext("ontime<--[HERE]"));
        assertNull(parser.extractFailedCommandFromContext("Unknown or incomplete command. See below for error"));
    }

    @Test
    void jobXpParsingAndPercentagesWork() {
        String jobsStats = "[ArdaniaJobs] Spieler: PX_Sven\n" +
                "Level 24 for Mobhunter: 2497.42/3120xp\n" +
                "Level 13 for Farmer: 73.57/1897xp\n" +
                "Level 22 for Miner: 2199.94/2895xp";

        assertTrue(parser.parse(jobsStats, false));

        List<ArdaniaState.JobEntry> jobs = state.getJobs();
        assertEquals(3, jobs.size());

        ArdaniaState.JobEntry mobhunter = jobs.stream().filter(j -> j.name().equals("Mobhunter")).findFirst().orElseThrow();
        assertEquals(24, mobhunter.level());
        assertEquals(2497.42, mobhunter.currentXp(), 0.01);
        assertEquals(3120.0, mobhunter.maxXp(), 0.01);
        assertTrue(mobhunter.hasXpInfo());
        assertEquals(80, mobhunter.xpPercent()); // 2497.42 / 3120 = 80.04% -> 80%
        assertEquals("80% [24]", mobhunter.formatBadge(true));
        assertEquals("[24]", mobhunter.formatBadge(false));

        ArdaniaState.JobEntry farmer = jobs.stream().filter(j -> j.name().equals("Farmer")).findFirst().orElseThrow();
        assertEquals(13, farmer.level());
        assertEquals(4, farmer.xpPercent()); // 73.57 / 1897 = 3.87% -> 4%
        assertEquals("4% [13]", farmer.formatBadge(true));

        ArdaniaState.JobEntry miner = jobs.stream().filter(j -> j.name().equals("Miner")).findFirst().orElseThrow();
        assertEquals(22, miner.level());
        assertEquals(76, miner.xpPercent()); // 2199.94 / 2895 = 75.99% -> 76%
        assertEquals("76% [22]", miner.formatBadge(true));
    }

    @Test
    void sessionStatsCalculationAndNumberFormatting() {
        state.setEuronen(new java.math.BigDecimal("1000.00"));
        state.setArdaPoints(500);

        long t0 = 1_000_000L;
        state.startSession(t0);

        assertEquals(new java.math.BigDecimal("0.00"), state.getSessionEuronenDelta());
        assertEquals(0L, state.getSessionPointsDelta());

        // Geld und Punkte verdienen
        state.setEuronen(new java.math.BigDecimal("1250.50"));
        state.setArdaPoints(550);

        assertEquals(new java.math.BigDecimal("250.50"), state.getSessionEuronenDelta());
        assertEquals(50L, state.getSessionPointsDelta());

        // Nach 1 Stunde (3600s = 3_600_000ms):
        String stats = state.getFormattedSessionStats(t0 + 3_600_000L);
        assertTrue(stats.contains("+250,50"));
        assertTrue(stats.contains("+250,50/h"));

        // Kompakte Zahlen
        assertEquals("30,2k", ArdaniaState.formatCompactMoney(new java.math.BigDecimal("30214.80")));
        assertEquals("1,5M", ArdaniaState.formatCompactMoney(new java.math.BigDecimal("1500000.00")));
        assertEquals("500,00", ArdaniaState.formatCompactMoney(new java.math.BigDecimal("500.00")));
    }
}
