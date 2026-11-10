package de.ardania.hud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ArdaniaSyncManagerTest {

    private ArdaniaSyncManager sync;
    private ArdaniaState state;
    private ArdaniaMessageParser parser;
    private AtomicLong fakeTime;

    @BeforeEach
    void setUp() {
        sync = ArdaniaSyncManager.getInstance();
        sync.resetForTesting();
        state = new ArdaniaState();
        parser = new ArdaniaMessageParser(state);
        fakeTime = new AtomicLong(100_000L);
        sync.setTimeSupplierForTesting(fakeTime::get);
    }

    @Test
    void autoCommandSuppressesResponseWhileManualCommandShowsResponse() {
        long t0 = fakeTime.get();
        sync.setLastCommandSentForTesting("geld", t0);

        String bankMsg = "[ArdaBank] Dein Kontostand: 1.250,50 Euronen";
        assertTrue(parser.isMoneyBalanceResponse(bankMsg));

        // Automatische Abfrage -> Nachricht wird unterdrückt (true)
        assertTrue(sync.shouldSuppressMessage(bankMsg, parser));

        // Spieler tippt selbst /geld im Chat ein
        fakeTime.addAndGet(1_000L); // 1s später
        sync.onPlayerManualCommand("/geld");

        // Antwort auf manuellen Befehl -> Nachricht wird NICHT unterdrückt (false), Spieler sieht sie
        assertFalse(sync.shouldSuppressMessage(bankMsg, parser));

        // Nach Ablauf des Ansichtsfensters (10 Sekunden) und neuem Auto-Sync wird die nächste Antwort wieder unterdrückt
        fakeTime.addAndGet(11_000L);
        sync.setLastCommandSentForTesting("geld", fakeTime.get());
        assertTrue(sync.shouldSuppressMessage(bankMsg, parser));
    }

    @Test
    void pointsJobsAndOntimeManualSuppressionWindowsWork() {
        String pointsMsg = "[ArdaniaPoints] Du besitzt 3,500 ArdaPoints";
        String jobsMsg = "[ArdaniaJobs] Spieler: PX_Sven\nLevel 24 for Mobhunter: 2497/3120xp";
        String ontimeMsg = "Hauptserver: 5 Tage 10 Stunden 20 Minute";

        // Automatische Hintergrundbefehle wurden gesendet -> Antworten werden unterdrückt
        long t0 = fakeTime.get();
        sync.setLastCommandSentForTesting("ardapoints me", t0);
        sync.setLastCommandSentForTesting("jobs stats", t0);
        sync.setLastCommandSentForTesting("ontime", t0);
        assertTrue(sync.shouldSuppressMessage(pointsMsg, parser));
        assertTrue(sync.shouldSuppressMessage(jobsMsg, parser));
        assertTrue(sync.shouldSuppressMessage(ontimeMsg, parser));

        // Spieler tippt manuell ein
        sync.onPlayerManualCommand("/ardapoints me");
        sync.onPlayerManualCommand("/jobs stats");
        sync.onPlayerManualCommand("/ontime");

        assertFalse(sync.shouldSuppressMessage(pointsMsg, parser));
        assertFalse(sync.shouldSuppressMessage(jobsMsg, parser));
        assertFalse(sync.shouldSuppressMessage(ontimeMsg, parser));

        // 11 Sekunden später bei erneutem Auto-Sync
        long t1 = fakeTime.addAndGet(11_000L);
        sync.setLastCommandSentForTesting("ardapoints me", t1);
        sync.setLastCommandSentForTesting("jobs stats", t1);
        sync.setLastCommandSentForTesting("ontime", t1);
        assertTrue(sync.shouldSuppressMessage(pointsMsg, parser));
        assertTrue(sync.shouldSuppressMessage(jobsMsg, parser));
        assertTrue(sync.shouldSuppressMessage(ontimeMsg, parser));
    }

    @Test
    void moderatorCommandsAndSeparatorLinesAreNeverSuppressedOutsideAutoSync() {
        // Im normalen Spielbetrieb (ohne aktiven Auto-Sync in den letzten 3,5s) dürfen
        // Trennlinien, Name:-Zeilen, Server-Zeilen oder Moderator-Fehlermeldungen NIEMALS verschluckt werden!
        assertFalse(sync.shouldSuppressMessage("-----------------------------------------------------", parser));
        assertFalse(sync.shouldSuppressMessage("=====================================================", parser));
        assertFalse(sync.shouldSuppressMessage("Name: VerdächtigerSpieler", parser));
        assertFalse(sync.shouldSuppressMessage("Beigetreten am: 01.01.2026 12:00", parser));
        assertFalse(sync.shouldSuppressMessage("Hauptserver: Online", parser));
        assertFalse(sync.shouldSuppressMessage("[Ardania] Fehler: Spieler ist nicht online!", parser));
        assertFalse(sync.shouldSuppressMessage("Du kannst Befehle für diesen Spieler nicht ausführen.", parser));

        // Selbst wenn vor 500ms ein Auto-Sync lief, deaktiviert jeder manuelle Moderator-Befehl sofort die Unterdrückung:
        long t0 = fakeTime.get();
        sync.setLastCommandSentForTesting("ontime", t0);
        fakeTime.addAndGet(500L);
        sync.onPlayerManualCommand("/whois VerdächtigerSpieler");

        assertTrue(sync.isForeignPlayerQueryActive());
        assertFalse(sync.shouldSuppressMessage("-----------------------------------------------------", parser));
        assertFalse(sync.shouldSuppressMessage("Name: VerdächtigerSpieler", parser));
        assertFalse(sync.shouldSuppressMessage("Kontostand: 50.000,00 Euronen", parser));
        assertFalse(sync.shouldSuppressMessage("ban VerdächtigerSpieler<--[HERE]", parser));
    }

    @Test
    void sendingAutoCommandFlagPreventsManualCommandRegistration() {
        List<String> sent = new ArrayList<>();
        sync.queueCommandForTesting("geld");

        // Simuliere: Wenn die Mod intern sendCommand aufruft, ruft der Mixin-Hook onPlayerManualCommand auf
        boolean dispatched = sync.dispatchNextCommand(cmd -> {
            sent.add(cmd);
            // Simulierter Fabric Message API Event-Trigger:
            sync.onPlayerManualCommand(cmd);
        }, cmd -> true);

        assertTrue(dispatched);
        assertEquals(List.of("geld"), sent);

        // Die Antwort darf trotzdem unterdrückt werden, weil sendingAutoCommand aktiv war!
        String bankMsg = "[ArdaBank] Dein Kontostand: 1.000 Euronen";
        assertTrue(sync.shouldSuppressMessage(bankMsg, parser));
    }

    @Test
    void commandQueueStaggeringRateLimit() {
        List<String> dispatched = new ArrayList<>();
        sync.queueCommandForTesting("geld");
        sync.queueCommandForTesting("ontime");

        // 1. Befehl wird sofort versendet
        assertTrue(sync.dispatchNextCommand(dispatched::add, cmd -> true));
        assertEquals(List.of("geld"), dispatched);

        // Sofort danach: 500ms vergangen -> Rate-Limit (1500ms erforderlich) greift
        fakeTime.addAndGet(500L);
        assertFalse(sync.dispatchNextCommand(dispatched::add, cmd -> true));
        assertEquals(1, dispatched.size());

        // 1600ms seit erstem Befehl: 2. Befehl wird versendet
        fakeTime.addAndGet(1_100L); // jetzt 1600ms vergangen
        assertTrue(sync.dispatchNextCommand(dispatched::add, cmd -> true));
        assertEquals(List.of("geld", "ontime"), dispatched);

        // Queue ist nun leer
        assertFalse(sync.dispatchNextCommand(dispatched::add, cmd -> true));
    }

    @Test
    void subServerFilteringBlocksDisallowedCommands() {
        sync.setCurrentServerTypeForTesting(ArdaniaSyncManager.ServerType.MINE);
        sync.queueCommandForTesting("geld");
        sync.queueCommandForTesting("ardapoints me");
        sync.queueCommandForTesting("jobs stats");
        sync.queueCommandForTesting("ontime");

        // Auf MINE dürfen ardapoints und jobs stats gar nicht in die Queue!
        var queue = sync.getCommandQueueForTesting();
        assertEquals(2, queue.size());
        assertTrue(queue.contains("geld"));
        assertTrue(queue.contains("ontime"));
        assertFalse(queue.contains("ardapoints me"));
        assertFalse(queue.contains("jobs stats"));
    }

    @Test
    void commandTreeUnsupportedCommandIsBlockedImmediately() {
        sync.queueCommandForTesting("geld");

        // Befehlsbaum-Prüfung schlägt fehl (z. B. auf Fallback-Server)
        boolean dispatched = sync.dispatchNextCommand(cmd -> fail("Darf nicht gesendet werden"), cmd -> false);
        assertFalse(dispatched);

        // "geld" ist nun auf der aktuellen Welt gesperrt
        assertTrue(sync.getBlockedCommandsForTesting().contains("geld"));

        // Weiterer Versuch, "geld" zu queuen, wird direkt ignoriert
        sync.queueCommandForTesting("geld");
        assertTrue(sync.getCommandQueueForTesting().isEmpty());
    }

    @Test
    void brigadierErrorContextExtractsAndBlocksFailedCommand() {
        long t0 = fakeTime.get();
        sync.setLastCommandSentForTesting("jobs stats", t0);
        sync.queueCommandForTesting("jobs stats");

        String errorHeader = "Unknown or incomplete command. See below for error";
        String errorContext = "jobs stats<--[HERE]";

        assertTrue(parser.isUnknownCommandError(errorHeader));
        assertTrue(parser.isUnknownCommandError(errorContext));

        assertTrue(sync.shouldSuppressMessage(errorHeader, parser));
        assertTrue(sync.shouldSuppressMessage(errorContext, parser));

        // "jobs stats" wurde aus dem Context extrahiert und gesperrt
        assertTrue(sync.getBlockedCommandsForTesting().contains("jobs stats"));
        assertFalse(sync.getCommandQueueForTesting().contains("jobs stats"));
    }

    @Test
    void afkCommandTogglesAfkStateAndSuppressesCommands() {
        assertFalse(sync.isAfkActive());
        assertFalse(sync.isPlayerAfk());

        // 1. Manuelles /afk toggled AFK ein
        sync.onAfkCommand(null);
        assertTrue(sync.isAfkActive());
        assertTrue(sync.isPlayerAfk());

        // Versuche Befehle einzureihen -> werden unterdrueckt
        sync.queueCommandForTesting("geld");
        sync.queueCommandForTesting("ardapoints me");
        sync.queueCommandForTesting("jobs stats");
        sync.queueCommandForTesting("ontime");
        assertTrue(sync.getCommandQueueForTesting().isEmpty());

        // 2. Erneutes /afk toggled AFK wieder aus
        sync.onAfkCommand(null);
        assertFalse(sync.isAfkActive());
        assertFalse(sync.isPlayerAfk());

        // Nun koennen Befehle wieder eingereiht werden
        sync.queueCommandForTesting("geld");
        assertEquals(List.of("geld"), new ArrayList<>(sync.getCommandQueueForTesting()));
    }

    @Test
    void afkMovementThresholdRequiresTenBlocksToExit() {
        sync.enterAfkState(100.0, 64.0, 100.0);
        assertTrue(sync.isAfkActive());

        // Bewegung unter 10 Bloecken (z. B. 5 Bloecke x-Achse)
        boolean exitedEarly = sync.checkAfkMovement(105.0, 64.0, 100.0);
        assertFalse(exitedEarly);
        assertTrue(sync.isAfkActive());

        // Diagonale Bewegung unter 10 Bloecken (dx=6, dz=6 -> dist = sqrt(72) ~ 8.48 Bloecke)
        boolean exitedDiagonalSmall = sync.checkAfkMovement(106.0, 64.0, 106.0);
        assertFalse(exitedDiagonalSmall);
        assertTrue(sync.isAfkActive());

        // Diagonale Bewegung >= 10 Bloecken (dx=6, dz=8 -> dist = sqrt(36+64) = 10.0 Bloecke)
        boolean exitedThreshold = sync.checkAfkMovement(106.0, 64.0, 108.0);
        assertTrue(exitedThreshold);
        assertFalse(sync.isAfkActive());
    }

    @Test
    void fallbackServerSuppressesAllQueuedCommands() {
        sync.setCurrentServerTypeForTesting(ArdaniaSyncManager.ServerType.FALLBACK);

        // Auf dem Fallback Server darf kein einziger Befehl in die Queue
        sync.queueCommandForTesting("geld");
        sync.queueCommandForTesting("ardapoints me");
        sync.queueCommandForTesting("jobs stats");
        sync.queueCommandForTesting("ontime");

        assertTrue(sync.getCommandQueueForTesting().isEmpty());
    }

    @Test
    void serverTypeFromTagResolution() {
        assertEquals(ArdaniaSyncManager.ServerType.HAUPTSERVER, ArdaniaSyncManager.ServerType.fromTag("H"));
        assertEquals(ArdaniaSyncManager.ServerType.FALLBACK, ArdaniaSyncManager.ServerType.fromTag("F"));
        assertEquals(ArdaniaSyncManager.ServerType.MINE, ArdaniaSyncManager.ServerType.fromTag("M"));
        assertEquals(ArdaniaSyncManager.ServerType.EVENT, ArdaniaSyncManager.ServerType.fromTag("E"));
        assertEquals(ArdaniaSyncManager.ServerType.CREATIVE, ArdaniaSyncManager.ServerType.fromTag("C"));
        assertEquals(ArdaniaSyncManager.ServerType.UNKNOWN, ArdaniaSyncManager.ServerType.fromTag("INVALID"));
        assertEquals(ArdaniaSyncManager.ServerType.UNKNOWN, ArdaniaSyncManager.ServerType.fromTag(null));
    }
}
