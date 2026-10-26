package de.ardania.hud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
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
        assertTrue(parser.parse("Danke fürs Voten! Du hast 25 ArdaPoints erhalten.", false));
        assertEquals(75, state.getArdaPoints());
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
    }

    @Test
    void parseJobWithMaxLevel() {
        state.clearJobs();
        assertTrue(parser.parse("|||||||||||||||||||||||||||| MAX for Mobhunter: 3120/3120xp", false));
        assertTrue(parser.parse("|||||||||||||||||||||||||||| Level MAX for Miner: 3120/3120xp", false));
        assertTrue(parser.parse("|||||||||||||||||||||||||||| Level 30 for Farmer: 3120/3120xp", false));
        assertTrue(parser.parse("|||||||||||||||||||||||||||| Stufe MAX f\u00FCr Holzf\u00E4ller: 3120/3120xp", false));
        assertTrue(parser.parse("Job: Fischer - MAX", false));

        List<ArdaniaState.JobEntry> jobs = state.getJobs();
        assertEquals(5, jobs.size());

        for (ArdaniaState.JobEntry j : jobs) {
            assertTrue(j.isMax(), "Job " + j.name() + " should be MAX");
            assertEquals(30, j.level(), "Job " + j.name() + " level should be 30");
            assertEquals("MAX", j.displayLevel(), "Job " + j.name() + " displayLevel should be MAX");
        }

        assertTrue(parser.isSilentServerResponse("|||||||||||||||||||||||||||| MAX for Mobhunter: 3120/3120xp"));
        assertTrue(parser.isSilentServerResponse("|||||||||||||||||||||||||||| Level MAX for Miner: 3120/3120xp"));
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
}
