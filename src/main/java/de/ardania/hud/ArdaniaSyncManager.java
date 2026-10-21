package de.ardania.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Verwaltet extrem sparsame Hintergrund-Abfragen (Silent Sync) fuer Kontostand und ArdaPoints.
 *
 * <p>Regeln:
 * <ul>
 *   <li><b>Geld:</b> Fruehestens alle 30 Sekunden per {@code /geld} abfragen.</li>
 *   <li><b>ArdaPoints:</b> Automatisch minuetlich (alle 60 Sekunden) per {@code /ardapoints me} abfragen.</li>
 *   <li><b>Gestaffeltes Senden:</b> Mindestens 1,5 Sekunden Abstand zwischen Chat-Befehlen.</li>
 * </ul>
 * </p>
 */
public final class ArdaniaSyncManager {

    private static final ArdaniaSyncManager INSTANCE = new ArdaniaSyncManager();

    /** Mindestabstand zwischen Geld-Abfragen: 30 Sekunden. */
    private static final long MONEY_COOLDOWN_MS = 30_000;

    /** Minuetliche ArdaPoints-Abfrage: 60 Sekunden. */
    private static final long POINTS_PERIOD_MS = 60_000;

    /** Stuendlicher Ontime-Abgleich: 60 Minuten. */
    private static final long ONTIME_PERIOD_MS = 3_600_000;

    /** Mindestabstand zwischen zwei beliebigen Chat-Befehlen aus der Queue. */
    private static final long INTER_COMMAND_DELAY_MS = 1_500;

    /** Zeitfenster nach einem stillen Befehl, in dem Server-Antworten im Chat unterdrueckt werden (8 Sekunden). */
    private static final long SUPPRESSION_WINDOW_MS = 8_000;

    private long lastMoneySyncTime = 0;
    private long lastPointsSyncTime = 0;
    private long lastOntimeSyncTime = 0;
    private long lastCommandSentTime = 0;

    private boolean pendingMoneyQuery = false;
    private boolean pendingInitialJoinSync = false;
    private long joinTimestamp = 0;

    // Warteschlange fuer gestaffeltes Senden
    private final Deque<String> commandQueue = new ArrayDeque<>();

    private ArdaniaSyncManager() {}

    public static ArdaniaSyncManager getInstance() {
        return INSTANCE;
    }

    /** Wird aufgerufen, wenn ein Einnahmen-Event (+0.15$) in der Action-Bar reinkommt. */
    public synchronized void triggerMoneySync() {
        this.pendingMoneyQuery = true;
    }

    /**
     * Wird beim Server-Join aufgerufen: plant nach 2 Sekunden einen vollstaendigen Sync ein.
     */
    public synchronized void onServerJoin() {
        this.pendingInitialJoinSync = true;
        this.joinTimestamp = System.currentTimeMillis();
    }

    /**
     * Wird jeden Client-Tick aufgerufen.
     */
    public synchronized void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            this.commandQueue.clear();
            this.pendingMoneyQuery = false;
            this.pendingInitialJoinSync = false;
            return;
        }

        // Initialer Join-Sync mit 2,5s Verzoegerung (wartet Login-Nachrichten und Spawn ab)
        if (this.pendingInitialJoinSync) {
            if (now - this.joinTimestamp >= 2_500) {
                this.pendingInitialJoinSync = false;
                forceFullSync(client);
            }
        }

        // 1. Queue abarbeiten mit mindestens 1,5s Pause zwischen Befehlen
        if (!this.commandQueue.isEmpty()) {
            if (now - this.lastCommandSentTime >= INTER_COMMAND_DELAY_MS) {
                String cmd = this.commandQueue.poll();
                if (cmd != null) {
                    conn.sendCommand(cmd);
                    this.lastCommandSentTime = now;
                }
            }
            return;
        }

        ArdaniaConfig config = ArdaniaConfig.getInstance();
        if (!config.autoSyncPeriodic) {
            return;
        }

        long moneyCooldown = Math.max(5, config.moneySyncCooldownSeconds) * 1000L;
        long pointsInterval = Math.max(10, config.pointsSyncIntervalSeconds) * 1000L;
        long playtimeInterval = Math.max(1, config.playtimeSyncIntervalMinutes) * 60_000L;

        // 2. Geld-Sync: fruehestens nach Cooldown, wenn eine Aenderung registriert wurde
        if (this.pendingMoneyQuery) {
            if (now - this.lastMoneySyncTime >= moneyCooldown) {
                queueCommand("geld");
                this.lastMoneySyncTime = now;
                this.pendingMoneyQuery = false;
            }
        }

        // 3. ArdaPoints: automatisch nach eingestelltem Intervall abfragen
        if (now - this.lastPointsSyncTime >= pointsInterval) {
            queueCommand("ardapoints me");
            this.lastPointsSyncTime = now;
        }

        // 4. Ontime: nach eingestelltem Intervall abgleichen
        if (now - this.lastOntimeSyncTime >= playtimeInterval) {
            queueCommand("ontime");
            this.lastOntimeSyncTime = now;
        }
    }

    /**
     * Manueller Sync ueber Taste J (mit 10s Schutz).
     */
    public synchronized void forceFullSync(Minecraft client) {
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastMoneySyncTime < 10_000) {
            return;
        }

        this.lastMoneySyncTime = now;
        this.lastPointsSyncTime = now;
        this.lastOntimeSyncTime = now;
        this.pendingMoneyQuery = false;

        this.commandQueue.clear();
        this.commandQueue.add("geld");
        this.commandQueue.add("ardapoints me");
        this.commandQueue.add("jobs stats");
        this.commandQueue.add("ontime");
    }

    private void queueCommand(String cmd) {
        if (!this.commandQueue.contains(cmd)) {
            this.commandQueue.add(cmd);
        }
    }

    /**
     * Wird aufgerufen, wenn der Spieler selbst einen Befehl im Chat abschickt.
     * Hebt die Unterdrueckung auf, damit der Spieler seine manuellen Abfragen sieht!
     */
    public synchronized void onPlayerManualCommand(String command) {
        String cmd = command.toLowerCase().trim();
        if (cmd.startsWith("ontime") || cmd.startsWith("geld") || cmd.startsWith("ardapoints") || cmd.startsWith("jobs")) {
            this.lastCommandSentTime = 0; // Unterdrueckung sofort deaktivieren!
        }
    }

    public synchronized boolean isSuppressingResponses() {
        long now = System.currentTimeMillis();
        return (now - this.lastCommandSentTime <= SUPPRESSION_WINDOW_MS);
    }
}
