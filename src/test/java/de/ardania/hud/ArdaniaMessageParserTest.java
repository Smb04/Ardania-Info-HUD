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

    // ------------------------------------------------------------------ Euronen absolut (/geld)

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

    // ------------------------------------------------------------------ [ArdaBank] Buchungen (+ / -)

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

    // ------------------------------------------------------------------ ArdaPoints

    @Test
    void ardapointsFromScreenshot() {
        state.setArdaPoints(10);
        
        // Zeile 1 aus Screenshot:
        assertTrue(parser.parse("[ArdaniaPoints] Du besitzt 2,578 ArdaniaPoints", false));
        assertEquals(2578, state.getArdaPoints());

        // Zeile 2 aus Login-Begrüßung:
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

    // ------------------------------------------------------------------ Jobs

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

    // ------------------------------------------------------------------ Ontime (/ontime Screenshot)

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
        // 23237 + 13 + 38 + 438 = 23726 min = 16d 11h 26m
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
}
