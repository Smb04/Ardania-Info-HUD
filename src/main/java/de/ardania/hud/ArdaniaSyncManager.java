package de.ardania.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class ArdaniaSyncManager {

    private static final ArdaniaSyncManager INSTANCE = new ArdaniaSyncManager();

    private static final long INTER_COMMAND_DELAY_MS = 1_500;
    private static final long SUPPRESSION_WINDOW_MS = 8_000;
    private static final long MANUAL_SYNC_COOLDOWN_MS = 5_000;
    private static final long JOBS_COOLDOWN_MS = 10_000;

    private long lastMoneySyncTime = 0;
    private long lastPointsSyncTime = 0;
    private long lastOntimeSyncTime = 0;
    private long lastJobsSyncTime = 0;
    private long lastManualSyncTime = 0;
    private long lastCommandSentTime = 0;

    private boolean pendingMoneyQuery = false;
    private boolean pendingJobsQuery = false;
    private boolean pendingInitialJoinSync = false;
    private long joinTimestamp = 0;

    private final Deque<String> commandQueue = new ArrayDeque<>();

    private ArdaniaSyncManager() {}

    public static ArdaniaSyncManager getInstance() {
        return INSTANCE;
    }

    public synchronized void triggerMoneySync() {
        this.pendingMoneyQuery = true;
    }

    public synchronized void triggerJobsSync() {
        this.pendingJobsQuery = true;
    }

    public synchronized void onServerJoin() {
        this.pendingInitialJoinSync = true;
        this.joinTimestamp = System.currentTimeMillis();
    }

    public synchronized void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            this.commandQueue.clear();
            this.pendingMoneyQuery = false;
            this.pendingJobsQuery = false;
            this.pendingInitialJoinSync = false;
            return;
        }

        if (this.pendingInitialJoinSync && (now - this.joinTimestamp >= 2_500)) {
            this.pendingInitialJoinSync = false;
            forceFullSync(client);
        }

        // Befehle aus der Queue gestaffelt absenden, um Rate-Limits zu vermeiden
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

        if (this.pendingMoneyQuery && (now - this.lastMoneySyncTime >= moneyCooldown)) {
            queueCommand("geld");
            this.lastMoneySyncTime = now;
            this.pendingMoneyQuery = false;
        }

        if (this.pendingJobsQuery && (now - this.lastJobsSyncTime >= JOBS_COOLDOWN_MS)) {
            queueCommand("jobs stats");
            this.lastJobsSyncTime = now;
            this.pendingJobsQuery = false;
        }

        if (now - this.lastPointsSyncTime >= pointsInterval) {
            queueCommand("ardapoints me");
            this.lastPointsSyncTime = now;
        }

        if (now - this.lastOntimeSyncTime >= playtimeInterval) {
            queueCommand("ontime");
            this.lastOntimeSyncTime = now;
        }
    }

    public synchronized void forceFullSync(Minecraft client) {
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastManualSyncTime < MANUAL_SYNC_COOLDOWN_MS) {
            return;
        }

        this.lastManualSyncTime = now;
        this.lastMoneySyncTime = now;
        this.lastPointsSyncTime = now;
        this.lastOntimeSyncTime = now;
        this.lastJobsSyncTime = now;
        this.pendingMoneyQuery = false;
        this.pendingJobsQuery = false;

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

    public synchronized void onPlayerManualCommand(String command) {
        if (command == null) {
            return;
        }
        String cmd = command.toLowerCase(Locale.ROOT).trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (cmd.startsWith("ontime")
                || cmd.startsWith("geld")
                || cmd.startsWith("money")
                || cmd.startsWith("bal")
                || cmd.startsWith("ardapoints")
                || cmd.startsWith("jobs")) {
            this.lastCommandSentTime = 0;
        }
    }

    public synchronized boolean isSuppressingResponses() {
        long now = System.currentTimeMillis();
        return (now - this.lastCommandSentTime <= SUPPRESSION_WINDOW_MS);
    }
}
