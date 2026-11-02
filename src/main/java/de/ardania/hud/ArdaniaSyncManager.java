package de.ardania.hud;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ArdaniaSyncManager {

    public enum ServerType {
        HAUPTSERVER,
        FALLBACK,
        MINE,
        EVENT,
        CREATIVE,
        UNKNOWN;

        public static ServerType fromTag(String tag) {
            if (tag == null) return UNKNOWN;
            return switch (tag) {
                case "H" -> HAUPTSERVER;
                case "F" -> FALLBACK;
                case "M" -> MINE;
                case "E" -> EVENT;
                case "C" -> CREATIVE;
                default -> UNKNOWN;
            };
        }
    }

    private static final ArdaniaSyncManager INSTANCE = new ArdaniaSyncManager();

    private static final long INTER_COMMAND_DELAY_MS = 1_500;
    private static final long ERROR_SUPPRESSION_WINDOW_MS = 12_000;
    private static final long MANUAL_CMD_VIEW_WINDOW_MS = 10_000;
    private static final long MANUAL_SYNC_COOLDOWN_MS = 5_000;
    private static final long JOBS_COOLDOWN_MS = 10_000;
    private static final long POINTS_TRIGGER_COOLDOWN_MS = 5_000;

    private long lastMoneySyncTime = 0;
    private long lastPointsSyncTime = 0;
    private long lastOntimeSyncTime = 0;
    private long lastJobsSyncTime = 0;
    private long lastManualSyncTime = 0;
    private long lastCommandSentTime = 0;
    private String lastSentCommand = null;

    private boolean sendingAutoCommand = false;

    private long lastManualMoneyCmdTime = 0;
    private long lastManualPointsCmdTime = 0;
    private long lastManualJobsCmdTime = 0;
    private long lastManualOntimeCmdTime = 0;

    private boolean pendingMoneyQuery = false;
    private boolean pendingPointsQuery = false;
    private boolean pendingJobsQuery = false;
    private boolean pendingInitialJoinSync = false;
    private long joinTimestamp = 0;

    private Object lastKnownLevel = null;
    private ServerType currentServerType = ServerType.UNKNOWN;
    private final Set<String> blockedCommandsOnCurrentWorld = new HashSet<>();
    private final Deque<String> commandQueue = new ArrayDeque<>();

    private ArdaniaSyncManager() {}

    public static ArdaniaSyncManager getInstance() {
        return INSTANCE;
    }

    public synchronized ServerType getCurrentServerType() {
        return this.currentServerType;
    }

    public synchronized void triggerMoneySync() {
        this.pendingMoneyQuery = true;
    }

    public synchronized void triggerPointsSync() {
        this.pendingPointsQuery = true;
    }

    public synchronized void triggerJobsSync() {
        this.pendingJobsQuery = true;
    }

    public synchronized void onServerJoin() {
        this.pendingInitialJoinSync = true;
        this.joinTimestamp = System.currentTimeMillis();
        this.blockedCommandsOnCurrentWorld.clear();
        this.currentServerType = ServerType.UNKNOWN;
    }

    public synchronized void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            this.commandQueue.clear();
            this.blockedCommandsOnCurrentWorld.clear();
            this.pendingMoneyQuery = false;
            this.pendingPointsQuery = false;
            this.pendingJobsQuery = false;
            this.pendingInitialJoinSync = false;
            this.lastKnownLevel = null;
            this.currentServerType = ServerType.UNKNOWN;
            return;
        }

        if (client.level != null && client.level != this.lastKnownLevel) {
            boolean wasInWorld = (this.lastKnownLevel != null);
            this.lastKnownLevel = client.level;
            this.blockedCommandsOnCurrentWorld.clear();
            if (wasInWorld && ArdaniaConfig.getInstance().autoSyncOnJoin) {
                this.pendingInitialJoinSync = true;
                this.joinTimestamp = now;
            }
        }

        this.currentServerType = detectServerTypeFromTabList(client);
        if (this.currentServerType == ServerType.FALLBACK) {
            this.commandQueue.clear();
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
                if (cmd != null && !this.blockedCommandsOnCurrentWorld.contains(cmd)) {
                    if (!isCommandSupportedByServer(conn, cmd)) {
                        this.blockedCommandsOnCurrentWorld.add(cmd);
                        return;
                    }
                    this.lastSentCommand = cmd;
                    this.lastCommandSentTime = now;
                    this.sendingAutoCommand = true;
                    try {
                        conn.sendCommand(cmd);
                    } finally {
                        this.sendingAutoCommand = false;
                    }
                }
            }
            return;
        }

        if (this.pendingPointsQuery && (now - this.lastPointsSyncTime >= POINTS_TRIGGER_COOLDOWN_MS)) {
            queueCommand("ardapoints me");
            this.lastPointsSyncTime = now;
            this.pendingPointsQuery = false;
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
            this.pendingPointsQuery = false;
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

        this.currentServerType = detectServerTypeFromTabList(client);
        if (this.currentServerType == ServerType.FALLBACK) {
            return;
        }

        this.lastManualSyncTime = now;
        this.lastMoneySyncTime = now;
        this.lastPointsSyncTime = now;
        this.lastOntimeSyncTime = now;
        this.lastJobsSyncTime = now;
        this.pendingMoneyQuery = false;
        this.pendingPointsQuery = false;
        this.pendingJobsQuery = false;

        this.commandQueue.clear();
        queueCommand("geld");
        queueCommand("ardapoints me");
        queueCommand("jobs stats");
        queueCommand("ontime");
    }

    private void queueCommand(String cmd) {
        if (this.blockedCommandsOnCurrentWorld.contains(cmd)) {
            return;
        }
        if ((this.currentServerType == ServerType.MINE || this.currentServerType == ServerType.CREATIVE)
                && ("ardapoints me".equals(cmd) || "jobs stats".equals(cmd))) {
            return;
        }
        if (!this.commandQueue.contains(cmd)) {
            this.commandQueue.add(cmd);
        }
    }

    private ServerType detectServerTypeFromTabList(Minecraft client) {
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null) {
            return ServerType.UNKNOWN;
        }

        Collection<PlayerInfo> onlinePlayers = conn.getOnlinePlayers();
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            return ServerType.UNKNOWN;
        }

        GameProfile selfProfile = client.player.getGameProfile();
        Set<GameProfile> sameWorldProfiles = new HashSet<>();
        if (client.level != null) {
            for (Player p : client.level.players()) {
                if (p != null && p.getGameProfile() != null && !p.getGameProfile().equals(selfProfile)) {
                    sameWorldProfiles.add(p.getGameProfile());
                }
            }
        }

        Set<String> remoteTags = new HashSet<>();
        for (PlayerInfo info : onlinePlayers) {
            if (info == null || info.getProfile() == null) {
                continue;
            }
            GameProfile profile = info.getProfile();
            if (profile.equals(selfProfile)) {
                continue;
            }
            String tag = extractPlayerInfoServerTag(info);
            if (tag == null) {
                continue;
            }
            if (sameWorldProfiles.contains(profile)) {
                return ServerType.fromTag(tag);
            }
            remoteTags.add(tag);
        }

        if (!isCommandSupportedByServer(conn, "geld") && !isCommandSupportedByServer(conn, "jobs stats")) {
            return ServerType.FALLBACK;
        }

        if (client.player != null && client.player.isCreative()) {
            return ServerType.CREATIVE;
        }

        // Wenn andere Spieler in der Tabliste [H] tragen, befindet sich der Spieler selbst nicht auf dem Hauptserver
        if (remoteTags.contains("H")) {
            if (!isCommandSupportedByServer(conn, "geld")) {
                return ServerType.FALLBACK;
            }
            return ServerType.MINE;
        } else if (!remoteTags.isEmpty()) {
            return ServerType.HAUPTSERVER;
        }

        return ServerType.UNKNOWN;
    }

    private boolean isCommandSupportedByServer(ClientPacketListener conn, String cmd) {
        if (conn == null || cmd == null) {
            return false;
        }
        try {
            var dispatcher = conn.getCommands();
            if (dispatcher == null || dispatcher.getRoot() == null) {
                return true;
            }
            var root = dispatcher.getRoot();
            if (root.getChildren() == null || root.getChildren().isEmpty()) {
                return true;
            }
            int spaceIdx = cmd.indexOf(' ');
            String baseCmd = (spaceIdx > 0) ? cmd.substring(0, spaceIdx) : cmd;
            return root.getChild(baseCmd) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static String extractPlayerInfoServerTag(PlayerInfo info) {
        if (info.getTabListDisplayName() != null) {
            String tag = ArdaniaMessageParser.extractTabServerTag(info.getTabListDisplayName().getString());
            if (tag != null) {
                return tag;
            }
        }
        PlayerTeam team = info.getTeam();
        if (team != null) {
            String combined = team.getPlayerPrefix().getString() + " " + team.getPlayerSuffix().getString();
            return ArdaniaMessageParser.extractTabServerTag(combined);
        }
        return null;
    }

    public synchronized void onPlayerManualCommand(String command) {
        if (this.sendingAutoCommand || command == null) {
            return;
        }
        String cmd = command.toLowerCase(Locale.ROOT).trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        long now = System.currentTimeMillis();
        if (cmd.startsWith("geld") || cmd.startsWith("money") || cmd.startsWith("bal")) {
            this.lastManualMoneyCmdTime = now;
        } else if (cmd.startsWith("ardapoints") || cmd.startsWith("points") || cmd.startsWith("ap")) {
            this.lastManualPointsCmdTime = now;
        } else if (cmd.startsWith("jobs")) {
            this.lastManualJobsCmdTime = now;
        } else if (cmd.startsWith("ontime") || cmd.startsWith("playtime") || cmd.startsWith("spielzeit")) {
            this.lastManualOntimeCmdTime = now;
        }
    }

    public synchronized boolean shouldSuppressMessage(String rawMessage, ArdaniaMessageParser parser) {
        long now = System.currentTimeMillis();

        if (parser.isPointsBalanceResponse(rawMessage)) {
            return (now - this.lastManualPointsCmdTime) > MANUAL_CMD_VIEW_WINDOW_MS;
        }
        if (parser.isMoneyBalanceResponse(rawMessage)) {
            return (now - this.lastManualMoneyCmdTime) > MANUAL_CMD_VIEW_WINDOW_MS;
        }
        if (parser.isJobsStatsResponse(rawMessage)) {
            return (now - this.lastManualJobsCmdTime) > MANUAL_CMD_VIEW_WINDOW_MS;
        }
        if (parser.isOntimeResponse(rawMessage)) {
            return (now - this.lastManualOntimeCmdTime) > MANUAL_CMD_VIEW_WINDOW_MS;
        }
        if (parser.isUnknownCommandError(rawMessage)) {
            String failedCmd = parser.extractFailedCommandFromContext(rawMessage);
            if (failedCmd != null) {
                this.blockedCommandsOnCurrentWorld.add(failedCmd);
                this.commandQueue.remove(failedCmd);
            }
            if ((now - this.lastCommandSentTime) <= ERROR_SUPPRESSION_WINDOW_MS) {
                if (this.lastSentCommand != null) {
                    this.blockedCommandsOnCurrentWorld.add(this.lastSentCommand);
                }
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isSuppressingResponses() {
        long now = System.currentTimeMillis();
        return (now - this.lastCommandSentTime <= ERROR_SUPPRESSION_WINDOW_MS);
    }
}
