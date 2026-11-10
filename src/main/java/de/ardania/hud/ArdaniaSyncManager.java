package de.ardania.hud;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Verwaltet die passive, ratenbegrenzte Synchronisation mit dem Server Ardania.de.
 *
 * <p>Funktionsumfang:</p>
 * <ul>
 *   <li>Anti-Spam Befehls-Warteschlange mit 1.500 ms Mindestabstand zwischen Anfragen</li>
 *   <li>AFK-Schutz: Vollstaendiger Stopp aller automatischen Abfragen bei {@code /afk}
 *       bis zur Bewegung von mindestens 10 Bloecken euklidischer Distanz</li>
 *   <li>Fallback-Server-Erkennung ([F]): Unterbindet Hintergrundabfragen nach Server-Neustarts</li>
 *   <li>Dynamische Unterserver-Erkennung ueber den Tablisten-Header (z. B. {@code Hauptserver 6},
 *       {@code Minenwelten 4}) mit Konfidenz, Hysterese und manuellem Override</li>
 *   <li>Fehler-Stummschaltung: Faengt Syntaxfehler lautlos ab, falls ein Befehl unvollstaendig ist</li>
 * </ul>
 */
public final class ArdaniaSyncManager {

    /**
     * Typ des Unterservers innerhalb des Ardania-BungeeCord-Netzwerks.
     */
    public enum ServerType {
        HAUPTSERVER,
        FALLBACK,
        MINE,
        EVENT,
        CREATIVE,
        UNKNOWN;

        /**
         * Löst ein Tablist-Prefix (z. B. "H", "F", "M") in den entsprechenden ServerType auf.
         *
         * @param tag Einzelner Buchstabe des Server-Tags
         * @return Erkannter ServerType oder {@link #UNKNOWN}
         */
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
    private static final long AUTO_SYNC_SUPPRESSION_WINDOW_MS = 3_500;
    private static final long ERROR_SUPPRESSION_WINDOW_MS = 3_000;
    private static final long MANUAL_CMD_VIEW_WINDOW_MS = 10_000;
    private static final long MANUAL_CMD_GRACE_PERIOD_MS = 2_500;
    private static final long FOREIGN_PLAYER_QUERY_WINDOW_MS = 15_000;
    private static final long MANUAL_SYNC_COOLDOWN_MS = 5_000;
    private static final long JOBS_COOLDOWN_MS = 10_000;
    private static final long POINTS_TRIGGER_COOLDOWN_MS = 5_000;
    private static final long AFK_TIMEOUT_MS = 5 * 60_000L;
    private static final double AFK_MOVEMENT_THRESHOLD = 10.0;
    private static final double AFK_MOVEMENT_THRESHOLD_SQ = AFK_MOVEMENT_THRESHOLD * AFK_MOVEMENT_THRESHOLD;

    /** Mindestabstand zwischen zwei Erkennungsrunden (ausser der Header aendert sich). */
    private static final long DETECTION_THROTTLE_MS = 500L;
    /** Ab dieser Konfidenz wird ein Servertyp sofort uebernommen (keine Hysterese). */
    private static final double IMMEDIATE_ACCEPT_CONFIDENCE = 85.0;

    private boolean afkActive = false;
    private double afkStartX = Double.NaN;
    private double afkStartY = Double.NaN;
    private double afkStartZ = Double.NaN;

    private long lastMoneySyncTime = 0;
    private long lastPointsSyncTime = 0;
    private long lastOntimeSyncTime = 0;
    private long lastJobsSyncTime = 0;
    private long lastManualSyncTime = 0;
    private long lastCommandSentTime = 0;
    private String lastSentCommand = null;

    private long lastAutoMoneySentTime = 0;
    private long lastAutoPointsSentTime = 0;
    private long lastAutoJobsSentTime = 0;
    private long lastAutoOntimeSentTime = 0;

    private double lastPlayerX = Double.NaN;
    private double lastPlayerY = Double.NaN;
    private double lastPlayerZ = Double.NaN;
    private float lastPlayerYaw = Float.NaN;
    private float lastPlayerPitch = Float.NaN;
    private long lastActivityTime = 0;

    private boolean sendingAutoCommand = false;

    private long lastManualMoneyCmdTime = 0;
    private long lastManualPointsCmdTime = 0;
    private long lastManualJobsCmdTime = 0;
    private long lastManualOntimeCmdTime = 0;
    private long lastAnyManualCommandTime = 0;
    private long lastForeignPlayerQueryTime = 0;

    private boolean pendingMoneyQuery = false;
    private boolean pendingPointsQuery = false;
    private boolean pendingJobsQuery = false;
    private boolean pendingInitialJoinSync = false;
    private long joinTimestamp = 0;

    private java.util.function.LongSupplier timeSupplier = System::currentTimeMillis;

    private Object lastKnownLevel = null;
    private ServerType currentServerType = ServerType.UNKNOWN;
    private final Set<String> blockedCommandsOnCurrentWorld = new HashSet<>();
    private final Deque<String> commandQueue = new ArrayDeque<>();

    private ArdaniaServerDetector.Detection lastDetection = null;
    private long lastDetectionRunTime = 0L;
    private long lastSeenTabListTimestamp = -1L;
    private ServerType candidateServerType = ServerType.UNKNOWN;
    private int candidateConfirmations = 0;

    private ArdaniaSyncManager() {}

    /**
     * Liefert die Singleton-Instanz des Sync-Managers.
     *
     * @return Zentrale Instanz
     */
    public static ArdaniaSyncManager getInstance() {
        return INSTANCE;
    }

    /**
     * Gibt den aktuell erkannten Servertyp des Spielers zurueck.
     *
     * @return Aktueller ServerType
     */
    public synchronized ServerType getCurrentServerType() {
        return this.currentServerType;
    }

    /**
     * Liefert das Ergebnis der letzten Erkennungsrunde inklusive Rohdaten.
     *
     * <p>Wird vom Debug-Befehl verwendet, um nachvollziehbar zu machen, warum
     * ein bestimmter Unterserver erkannt wurde.</p>
     *
     * @return Letzte Erkennung oder {@code null}, falls noch keine stattfand
     */
    public synchronized ArdaniaServerDetector.Detection getLastDetection() {
        return this.lastDetection;
    }

    /**
     * Prueft, ob der Brigadier-Befehlsbaum bereits vollstaendig synchronisiert ist.
     *
     * <p>Beim Betreten eines Servers kann {@code getCommands()} kurzzeitig leer
     * oder unvollstaendig sein. Fehlende Ardania-Befehle sind daher erst dann
     * ein verwertbares Signal, wenn Vanilla-Befehle vorhanden sind.</p>
     */
    private boolean isCommandTreeReady(ClientPacketListener conn) {
        if (conn == null) {
            return false;
        }
        try {
            var dispatcher = conn.getCommands();
            if (dispatcher == null || dispatcher.getRoot() == null) {
                return false;
            }
            var root = dispatcher.getRoot();
            if (root.getChildren() == null || root.getChildren().isEmpty()) {
                return false;
            }
            // Vanilla-Befehle sind ein verlaesslicher Hinweis auf einen synchronisierten Baum.
            if (root.getChild("help") != null || root.getChild("me") != null) {
                return true;
            }
            // Ein sehr kleiner Baum ist meist noch unvollstaendig.
            return root.getChildren().size() >= 8;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Gibt an, ob der AFK-Schutzmodus aktuell aktiv ist.
     *
     * @return true, falls automatische Abfragen blockiert sind
     */
    public synchronized boolean isAfkActive() {
        return this.afkActive;
    }

    /**
     * Aktiviert den AFK-Schutz und speichert die Ausgangsposition des Spielers.
     *
     * @param x X-Koordinate beim Start von /afk
     * @param y Y-Koordinate beim Start von /afk
     * @param z Z-Koordinate beim Start von /afk
     */
    public synchronized void enterAfkState(double x, double y, double z) {
        this.afkActive = true;
        this.afkStartX = x;
        this.afkStartY = y;
        this.afkStartZ = z;
        this.commandQueue.clear();
        this.pendingMoneyQuery = false;
        this.pendingPointsQuery = false;
        this.pendingJobsQuery = false;
    }

    /**
     * Hebt den AFK-Schutz auf und reaktiviert den regulaeren Abfragezyklus.
     */
    public synchronized void exitAfkState() {
        this.afkActive = false;
        this.afkStartX = Double.NaN;
        this.afkStartY = Double.NaN;
        this.afkStartZ = Double.NaN;
        this.lastActivityTime = now();
    }

    /**
     * Prueft, ob sich der Spieler um mindestens 10 Bloecke (euklidisch) von der
     * AFK-Startposition wegbewegt hat. Falls ja, wird der AFK-Status aufgehoben.
     *
     * @param currentX Aktuelle X-Koordinate
     * @param currentY Aktuelle Y-Koordinate
     * @param currentZ Aktuelle Z-Koordinate
     * @return true, falls der AFK-Modus durch die Bewegung beendet wurde
     */
    public synchronized boolean checkAfkMovement(double currentX, double currentY, double currentZ) {
        if (!this.afkActive || Double.isNaN(this.afkStartX)) {
            return false;
        }
        double dx = currentX - this.afkStartX;
        double dy = currentY - this.afkStartY;
        double dz = currentZ - this.afkStartZ;
        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
        if (distSq >= AFK_MOVEMENT_THRESHOLD_SQ) {
            exitAfkState();
            return true;
        }
        return false;
    }

    /**
     * Behandelt das Senden des Befehls {@code /afk}.
     *
     * @param player Lokaler Spieler zur Ermittlung der Koordinaten
     */
    public synchronized void onAfkCommand(Player player) {
        if (this.afkActive) {
            exitAfkState();
        } else if (player != null) {
            enterAfkState(player.getX(), player.getY(), player.getZ());
        } else {
            this.afkActive = true;
            this.commandQueue.clear();
        }
    }

    /**
     * Prueft, ob der Spieler entweder im manuellen AFK-Modus ist oder seit
     * mindestens 5 Minuten inaktiv war.
     *
     * @return true, falls der Spieler AFK ist
     */
    public synchronized boolean isPlayerAfk() {
        if (this.afkActive) {
            return true;
        }
        if (this.lastActivityTime <= 0) {
            return false;
        }
        return (now() - this.lastActivityTime) >= AFK_TIMEOUT_MS;
    }

    public synchronized void triggerMoneySync() {
        this.pendingMoneyQuery = true;
        this.lastActivityTime = now();
    }

    public synchronized void triggerPointsSync() {
        this.pendingPointsQuery = true;
        this.lastActivityTime = now();
    }

    public synchronized void triggerJobsSync() {
        this.pendingJobsQuery = true;
        this.lastActivityTime = now();
    }

    /**
     * Initialisiert den Synchronisationszustand beim Betreten des Servers.
     */
    public synchronized void onServerJoin() {
        long currentNow = now();
        this.pendingInitialJoinSync = true;
        this.joinTimestamp = currentNow;
        this.lastActivityTime = currentNow;
        this.blockedCommandsOnCurrentWorld.clear();
        resetDetectionState();
    }

    /**
     * Setzt den Synchronisations- und Erkennungszustand beim Verlassen des Servers zurueck.
     */
    public synchronized void onServerDisconnect() {
        this.commandQueue.clear();
        this.blockedCommandsOnCurrentWorld.clear();
        this.pendingMoneyQuery = false;
        this.pendingPointsQuery = false;
        this.pendingJobsQuery = false;
        this.pendingInitialJoinSync = false;
        this.lastKnownLevel = null;
        resetDetectionState();
        TabListSnapshot.reset();
    }

    private long now() {
        return this.timeSupplier.getAsLong();
    }

    /**
     * Haupt-Tick-Methode, die pro Client-Frame ausgefuehrt wird.
     *
     * <p>Behandelt Unterserver-Erkennung, AFK-Ueberwachung, die gestaffelte
     * Abarbeitung der Befehlswarteschlange sowie periodische Sync-Intervalle.</p>
     *
     * @param client Minecraft-Client-Instanz
     */
    public synchronized void tick(Minecraft client) {
        long now = now();
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            this.commandQueue.clear();
            this.blockedCommandsOnCurrentWorld.clear();
            this.pendingMoneyQuery = false;
            this.pendingPointsQuery = false;
            this.pendingJobsQuery = false;
            this.pendingInitialJoinSync = false;
            this.lastKnownLevel = null;
            resetDetectionState();
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

        updateServerDetection(client, now, false);
        if (this.currentServerType == ServerType.FALLBACK) {
            this.commandQueue.clear();
            this.pendingMoneyQuery = false;
            this.pendingPointsQuery = false;
            this.pendingJobsQuery = false;
            return;
        }

        if (this.afkActive) {
            if (client.player != null) {
                checkAfkMovement(client.player.getX(), client.player.getY(), client.player.getZ());
            }
            if (this.afkActive) {
                this.commandQueue.clear();
                this.pendingMoneyQuery = false;
                this.pendingPointsQuery = false;
                this.pendingJobsQuery = false;
                return;
            }
        }

        updatePlayerActivity(client.player, now);

        if (this.pendingInitialJoinSync && (now - this.joinTimestamp >= 2_500)) {
            this.pendingInitialJoinSync = false;
            forceFullSync(client);
        }

        // Befehle aus der Queue gestaffelt absenden, um Rate-Limits zu vermeiden.
        // Waehrend der Spieler im Chat tippt oder kurz nach einem eigenen manuellen Befehl
        // wird pausiert, um Server-Anti-Spam/Cooldowns fuer Moderator-Befehle nicht auszuloesen.
        if (!this.commandQueue.isEmpty()) {
            if (!isChatScreenOpen(client) && (now - this.lastAnyManualCommandTime >= MANUAL_CMD_GRACE_PERIOD_MS)) {
                dispatchNextCommand(conn::sendCommand, cmd -> isCommandSupportedByServer(conn, cmd));
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

        if (isPlayerAfk()) {
            return;
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

    private void updatePlayerActivity(Player player, long now) {
        if (player == null) return;
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        if (Double.isNaN(this.lastPlayerX)
                || Math.abs(x - this.lastPlayerX) > 0.01
                || Math.abs(y - this.lastPlayerY) > 0.01
                || Math.abs(z - this.lastPlayerZ) > 0.01
                || Math.abs(yaw - this.lastPlayerYaw) > 0.1f
                || Math.abs(pitch - this.lastPlayerPitch) > 0.1f) {
            this.lastPlayerX = x;
            this.lastPlayerY = y;
            this.lastPlayerZ = z;
            this.lastPlayerYaw = yaw;
            this.lastPlayerPitch = pitch;
            this.lastActivityTime = now;
        }
    }

    /**
     * Erzwingt einen sofortigen Komplettabgleich aller Werte (Geld, Spielzeit, Punkte, Jobs).
     *
     * <p>Wird typischerweise durch die Taste {@code J} oder 2,5 Sekunden nach
     * Serverbeitritt aufgerufen.</p>
     *
     * @param client Minecraft-Client-Instanz
     */
    public synchronized void forceFullSync(Minecraft client) {
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            return;
        }

        long now = now();
        if (now - this.lastManualSyncTime < MANUAL_SYNC_COOLDOWN_MS) {
            return;
        }

        if (this.afkActive) {
            return;
        }

        updateServerDetection(client, now, true);
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
        queueCommand("ontime");
        queueCommand("ardapoints me");
        queueCommand("jobs stats");
    }

    private void queueCommand(String cmd) {
        if (this.afkActive || this.currentServerType == ServerType.FALLBACK) {
            return;
        }
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

    /**
     * Aktualisiert die Unterserver-Erkennung. Standardmaessig gedrosselt, damit
     * nicht jeder Client-Frame Tabliste und Befehlsbaum neu auswertet. Aendert
     * sich der Tablisten-Header, wird sofort neu erkannt.
     */
    private void updateServerDetection(Minecraft client, long now, boolean force) {
        long snapshotTime = TabListSnapshot.getUpdatedAt();
        boolean snapshotChanged = snapshotTime != this.lastSeenTabListTimestamp;
        if (!force && !snapshotChanged
                && this.lastDetection != null
                && (now - this.lastDetectionRunTime) < DETECTION_THROTTLE_MS) {
            return;
        }

        this.lastSeenTabListTimestamp = snapshotTime;
        this.lastDetectionRunTime = now;
        ArdaniaServerDetector.Detection detection = detectServerTypeFromTabList(client, now);
        this.lastDetection = detection;
        applyDetection(detection, now);
    }

    /**
     * Uebernimmt ein Erkennungsergebnis mit Hysterese: Eindeutige Ergebnisse
     * werden sofort uebernommen, unsichere erst nach zwei Bestaetigungen.
     */
    private void applyDetection(ArdaniaServerDetector.Detection detection, long now) {
        if (detection == null) {
            return;
        }
        ServerType detected = detection.type();
        if (detected == ServerType.UNKNOWN) {
            // Letzten bekannten Typ behalten, statt bei unvollstaendiger Tabliste auf UNKNOWN zu fallen.
            return;
        }
        if (detected == this.currentServerType) {
            this.candidateServerType = detected;
            this.candidateConfirmations = 0;
            return;
        }

        if (detection.confidence() >= IMMEDIATE_ACCEPT_CONFIDENCE) {
            acceptServerType(detected, now);
            return;
        }

        if (detected == this.candidateServerType) {
            this.candidateConfirmations++;
        } else {
            this.candidateServerType = detected;
            this.candidateConfirmations = 1;
        }

        if (this.candidateConfirmations >= 2) {
            acceptServerType(detected, now);
        }
    }

    private void acceptServerType(ServerType type, long now) {
        ServerType previous = this.currentServerType;
        if (previous == type) {
            return;
        }
        this.currentServerType = type;
        this.candidateServerType = ServerType.UNKNOWN;
        this.candidateConfirmations = 0;

        // Beim Unterserverwechsel sind alte Sperren und wartende Befehle ungueltig.
        this.commandQueue.clear();
        this.blockedCommandsOnCurrentWorld.clear();

        // Nach einem Fallback (Server-Neustart) den regulaeren Sync erneut anstossen.
        if (previous == ServerType.FALLBACK && type != ServerType.FALLBACK
                && ArdaniaConfig.getInstance().autoSyncOnJoin) {
            this.pendingInitialJoinSync = true;
            this.joinTimestamp = now;
        }

        if (ArdaniaClientMod.DEBUG) {
            ArdaniaClientMod.LOGGER.info(
                    "[ArdaniaHUD] Unterserver erkannt: {} -> {} (Quelle {}, Konfidenz {})",
                    previous,
                    type,
                    this.lastDetection != null ? this.lastDetection.source() : "?",
                    this.lastDetection != null
                            ? String.format(Locale.ROOT, "%.0f%%", this.lastDetection.confidence())
                            : "?");
        }
    }

    /**
     * Setzt die Erkennungsfelder zurueck (Disconnect, Serverwechsel, Tests).
     * Der Tablisten-Snapshot bleibt erhalten, damit ein Host-Alias weiterhin
     * ueber den Header erkannt werden kann.
     */
    private void resetDetectionState() {
        this.currentServerType = ServerType.UNKNOWN;
        this.lastDetection = null;
        this.lastDetectionRunTime = 0L;
        this.lastSeenTabListTimestamp = -1L;
        this.candidateServerType = ServerType.UNKNOWN;
        this.candidateConfirmations = 0;
    }

    /**
     * Baut die Eingabedaten fuer den Detektor aus Tabliste, Befehlsbaum und Spielerzustand.
     */
    private ArdaniaServerDetector.Detection detectServerTypeFromTabList(Minecraft client, long now) {
        ClientPacketListener conn = client.getConnection();
        if (conn == null || client.player == null) {
            return ArdaniaServerDetector.Detection.unknown(now);
        }

        EnumMap<ServerType, Integer> tagCounts = new EnumMap<>(ServerType.class);
        Set<String> sameWorldTags = new LinkedHashSet<>();
        GameProfile selfProfile = client.player.getGameProfile();
        Set<GameProfile> sameWorldProfiles = new HashSet<>();
        if (client.level != null) {
            for (Player p : client.level.players()) {
                if (p != null && p.getGameProfile() != null && !p.getGameProfile().equals(selfProfile)) {
                    sameWorldProfiles.add(p.getGameProfile());
                }
            }
        }

        String selfTag = null;
        int tabListSize = 0;
        int untaggedOthers = 0;
        int sameWorldUntagged = 0;
        Collection<PlayerInfo> onlinePlayers = conn.getOnlinePlayers();
        if (onlinePlayers != null) {
            for (PlayerInfo info : onlinePlayers) {
                if (info == null || info.getProfile() == null) {
                    continue;
                }
                tabListSize++;
                GameProfile profile = info.getProfile();
                String tag = extractPlayerInfoServerTag(info);
                if (profile.equals(selfProfile)) {
                    selfTag = tag;
                    continue;
                }
                boolean sameWorld = sameWorldProfiles.contains(profile);
                ServerType tagType = ServerType.fromTag(tag);
                if (tagType == ServerType.UNKNOWN) {
                    // Ungetaggte Spieler gehoeren zur eigenen Welt oder sind
                    // ohne Weltangabe in der globalen Tabliste sichtbar.
                    untaggedOthers++;
                    if (sameWorld) {
                        sameWorldUntagged++;
                    }
                    continue;
                }
                tagCounts.merge(tagType, 1, Integer::sum);
                if (sameWorld) {
                    sameWorldTags.add(tag);
                }
            }
        }

        boolean commandTreeReady = isCommandTreeReady(conn);
        boolean moneyAvailable = isCommandSupportedByServer(conn, "geld");
        boolean jobsAvailable = isCommandSupportedByServer(conn, "jobs stats");

        ArdaniaServerDetector.Input input = new ArdaniaServerDetector.Input(
                TabListSnapshot.getHeader(),
                TabListSnapshot.getFooter(),
                tagCounts,
                new ArrayList<>(sameWorldTags),
                untaggedOthers,
                sameWorldUntagged,
                selfTag,
                tabListSize,
                commandTreeReady,
                moneyAvailable,
                jobsAvailable,
                isPlayerCreative(client),
                ArdaniaConfig.getInstance().getServerTypeOverride(),
                this.lastDetection
        );
        return ArdaniaServerDetector.evaluate(input, now);
    }

    private static boolean isPlayerCreative(Minecraft client) {
        if (client == null || client.player == null) {
            return false;
        }
        // Direkte Methodenpruefung ueber alle bekannten Mapping-Namen hinweg:
        // - "isCreative": Mojang Mappings / MC 26.x
        // - "method_7337": Fabric Intermediary fuer 1.21.0 bis 1.21.4
        // - "method_68878": Fabric Intermediary fuer 1.21.5 bis 1.21.11
        for (String methodName : new String[] { "isCreative", "method_7337", "method_68878" }) {
            try {
                Method m = client.player.getClass().getMethod(methodName);
                Object res = m.invoke(client.player);
                if (res instanceof Boolean b && b) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        // Fallback ueber InteractionManager / GameMode
        try {
            if (client.gameMode != null) {
                for (String mName : new String[] { "getPlayerMode", "getCurrentGameMode", "method_2920", "isCreative", "method_8386" }) {
                    try {
                        Method m = client.gameMode.getClass().getMethod(mName);
                        Object res = m.invoke(client.gameMode);
                        if (res instanceof Boolean b && b) {
                            return true;
                        }
                        if (res != null && "CREATIVE".equalsIgnoreCase(res.toString())) {
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isCommandSupportedByServer(ClientPacketListener conn, String cmd) {
        if (conn == null || cmd == null) {
            return false;
        }
        int spaceIdx = cmd.indexOf(' ');
        String baseCmd = (spaceIdx > 0) ? cmd.substring(0, spaceIdx) : cmd;

        // Befehle wie "ontime", die haeufig als BungeeCord/Proxy-Befehle existieren
        // und nicht im Downstream-Brigadier-Baum des Spigot-Servers deklariert sind,
        // duerfen nicht faelschlicherweise vor dem ersten Senden geblockt werden.
        if ("ontime".equalsIgnoreCase(baseCmd) || "playtime".equalsIgnoreCase(baseCmd) || "spielzeit".equalsIgnoreCase(baseCmd)) {
            return true;
        }

        try {
            var dispatcher = conn.getCommands();
            if (dispatcher == null || dispatcher.getRoot() == null) {
                return false;
            }
            var root = dispatcher.getRoot();
            if (root.getChildren() == null || root.getChildren().isEmpty()) {
                return false;
            }
            return root.getChild(baseCmd) != null;
        } catch (Throwable t) {
            return false;
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

    private static boolean isChatScreenOpen(Minecraft client) {
        if (client == null) return false;
        try {
            for (String fName : new String[] { "screen", "field_1755" }) {
                try {
                    var f = client.getClass().getField(fName);
                    Object scr = f.get(client);
                    if (scr instanceof net.minecraft.client.gui.screens.ChatScreen) {
                        return true;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (client.gui != null) {
                for (String mName : new String[] { "screen", "getScreen" }) {
                    try {
                        Method m = client.gui.getClass().getMethod(mName);
                        Object scr = m.invoke(client.gui);
                        if (scr instanceof net.minecraft.client.gui.screens.ChatScreen) {
                            return true;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void recordAutoCommandSent(String cmd, long time) {
        this.lastSentCommand = cmd;
        this.lastCommandSentTime = time;
        if (cmd == null) return;
        String lower = cmd.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("geld") || lower.startsWith("money") || lower.startsWith("bal")) {
            this.lastAutoMoneySentTime = time;
        } else if (lower.startsWith("ardapoints") || lower.startsWith("points") || lower.startsWith("ap")) {
            this.lastAutoPointsSentTime = time;
        } else if (lower.startsWith("jobs")) {
            this.lastAutoJobsSentTime = time;
        } else if (lower.startsWith("ontime") || lower.startsWith("playtime") || lower.startsWith("spielzeit")) {
            this.lastAutoOntimeSentTime = time;
        }
    }

    public synchronized boolean dispatchNextCommand(java.util.function.Consumer<String> dispatcher, java.util.function.Predicate<String> supportCheck) {
        long now = now();
        if (this.commandQueue.isEmpty()) {
            return false;
        }
        if (now - this.lastCommandSentTime < INTER_COMMAND_DELAY_MS) {
            return false;
        }
        String cmd = this.commandQueue.poll();
        if (cmd == null || this.blockedCommandsOnCurrentWorld.contains(cmd)) {
            return false;
        }
        if (supportCheck != null && !supportCheck.test(cmd)) {
            this.blockedCommandsOnCurrentWorld.add(cmd);
            return false;
        }
        recordAutoCommandSent(cmd, now);
        this.sendingAutoCommand = true;
        try {
            if (dispatcher != null) {
                dispatcher.accept(cmd);
            }
        } finally {
            this.sendingAutoCommand = false;
        }
        return true;
    }

    /**
     * Registriert manuell vom Spieler eingegebene Chatbefehle, um entsprechende
     * Serverantworten im Chat sichtbar zu lassen statt sie stummzuschalten.
     *
     * @param command Der ausgefuehrte Befehl (mit oder ohne fuehrendem '/')
     */
    public synchronized void onPlayerManualCommand(String command) {
        if (this.sendingAutoCommand || command == null) {
            return;
        }
        String cmd = command.toLowerCase(Locale.ROOT).trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (cmd.isEmpty()) {
            return;
        }
        long now = now();
        this.lastAnyManualCommandTime = now;

        if (cmd.startsWith("geld") || cmd.startsWith("money") || cmd.startsWith("bal")) {
            this.lastManualMoneyCmdTime = now;
            if (cmd.indexOf(' ') > 0) {
                this.lastForeignPlayerQueryTime = now;
            }
        } else if (cmd.startsWith("ardapoints") || cmd.startsWith("points") || cmd.startsWith("ap")) {
            this.lastManualPointsCmdTime = now;
            String rest = cmd.replaceFirst("^(?:ardapoints|points|ap)\\s*", "");
            if (!rest.isEmpty() && !rest.equals("me")) {
                this.lastForeignPlayerQueryTime = now;
            }
        } else if (cmd.startsWith("jobs")) {
            this.lastManualJobsCmdTime = now;
            String rest = cmd.replaceFirst("^jobs\\s*", "");
            if (!rest.isEmpty() && !rest.equals("stats")) {
                this.lastForeignPlayerQueryTime = now;
            }
        } else if (cmd.startsWith("ontime") || cmd.startsWith("playtime") || cmd.startsWith("spielzeit")) {
            this.lastManualOntimeCmdTime = now;
            if (cmd.indexOf(' ') > 0) {
                this.lastForeignPlayerQueryTime = now;
            }
        } else if (cmd.startsWith("whois") || cmd.startsWith("seen") || cmd.startsWith("check")
                || cmd.startsWith("cmi ") || cmd.startsWith("eco ") || cmd.startsWith("baninfo")
                || cmd.startsWith("history") || cmd.startsWith("warnings") || cmd.startsWith("alts")) {
            this.lastForeignPlayerQueryTime = now;
        }
    }

    /**
     * Gibt an, ob der Spieler (z. B. als Moderator) kuerzlich einen Befehl zur Abfrage
     * eines fremden Spielers ausgefuehrt hat, sodass dessen Werte nicht in das eigene HUD
     * uebernommen werden duerfen.
     *
     * @return true, falls eine Fremdspieler-Abfrage aktiv ist
     */
    public synchronized boolean isForeignPlayerQueryActive() {
        if (this.lastForeignPlayerQueryTime <= 0) {
            return false;
        }
        return (now() - this.lastForeignPlayerQueryTime) <= FOREIGN_PLAYER_QUERY_WINDOW_MS;
    }

    /**
     * Prueft, ob eine eingehende Chat-Nachricht stummgeschaltet werden soll.
     *
     * <p>Antworten werden ausschliesslich dann unterdrueckt, wenn sie innerhalb
     * des kurzen Zeitfensters nach einer automatisierten Hintergrundabfrage des Mods
     * eintreffen und kein manueller Spieler-/Moderator-Befehl dazwischenlag.</p>
     *
     * @param rawMessage Roher Nachrichteninhalt
     * @param parser     Parser zur Erkennung von Antwortmustern
     * @return true, falls die Nachricht nicht im Chat erscheinen soll
     */
    public synchronized boolean shouldSuppressMessage(String rawMessage, ArdaniaMessageParser parser) {
        long now = now();

        if (parser.isPointsBalanceResponse(rawMessage)) {
            return this.lastAutoPointsSentTime > 0
                    && (now - this.lastAutoPointsSentTime) <= AUTO_SYNC_SUPPRESSION_WINDOW_MS
                    && this.lastAutoPointsSentTime >= this.lastAnyManualCommandTime
                    && (this.lastManualPointsCmdTime <= 0 || (now - this.lastManualPointsCmdTime) > MANUAL_CMD_VIEW_WINDOW_MS);
        }
        if (parser.isMoneyBalanceResponse(rawMessage)) {
            return this.lastAutoMoneySentTime > 0
                    && (now - this.lastAutoMoneySentTime) <= AUTO_SYNC_SUPPRESSION_WINDOW_MS
                    && this.lastAutoMoneySentTime >= this.lastAnyManualCommandTime
                    && (this.lastManualMoneyCmdTime <= 0 || (now - this.lastManualMoneyCmdTime) > MANUAL_CMD_VIEW_WINDOW_MS);
        }
        if (parser.isJobsStatsResponse(rawMessage)) {
            return this.lastAutoJobsSentTime > 0
                    && (now - this.lastAutoJobsSentTime) <= AUTO_SYNC_SUPPRESSION_WINDOW_MS
                    && this.lastAutoJobsSentTime >= this.lastAnyManualCommandTime
                    && (this.lastManualJobsCmdTime <= 0 || (now - this.lastManualJobsCmdTime) > MANUAL_CMD_VIEW_WINDOW_MS);
        }
        if (parser.isOntimeResponse(rawMessage)) {
            return this.lastAutoOntimeSentTime > 0
                    && (now - this.lastAutoOntimeSentTime) <= AUTO_SYNC_SUPPRESSION_WINDOW_MS
                    && this.lastAutoOntimeSentTime >= this.lastAnyManualCommandTime
                    && (this.lastManualOntimeCmdTime <= 0 || (now - this.lastManualOntimeCmdTime) > MANUAL_CMD_VIEW_WINDOW_MS);
        }
        if (parser.isUnknownCommandError(rawMessage)) {
            if (parser.hasForeignCommandErrorContext(rawMessage)) {
                return false;
            }
            String failedCmd = parser.extractFailedCommandFromContext(rawMessage);
            if (failedCmd != null) {
                this.blockedCommandsOnCurrentWorld.add(failedCmd);
                this.commandQueue.remove(failedCmd);
            }
            if (this.lastCommandSentTime > 0
                    && (now - this.lastCommandSentTime) <= ERROR_SUPPRESSION_WINDOW_MS
                    && this.lastCommandSentTime >= this.lastAnyManualCommandTime) {
                if (this.lastSentCommand != null) {
                    this.blockedCommandsOnCurrentWorld.add(this.lastSentCommand);
                }
                if (this.blockedCommandsOnCurrentWorld.contains("geld")
                        && this.blockedCommandsOnCurrentWorld.contains("jobs stats")) {
                    this.commandQueue.clear();
                    this.currentServerType = ServerType.FALLBACK;
                }
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isSuppressingResponses() {
        long now = now();
        return this.lastCommandSentTime > 0
                && (now - this.lastCommandSentTime <= ERROR_SUPPRESSION_WINDOW_MS)
                && this.lastCommandSentTime >= this.lastAnyManualCommandTime;
    }

    // --- Package-private & Public Helfer fuer Unit Tests ---

    public synchronized void setTimeSupplierForTesting(java.util.function.LongSupplier supplier) {
        this.timeSupplier = (supplier != null) ? supplier : System::currentTimeMillis;
    }

    public synchronized void resetForTesting() {
        this.lastMoneySyncTime = 0;
        this.lastPointsSyncTime = 0;
        this.lastOntimeSyncTime = 0;
        this.lastJobsSyncTime = 0;
        this.lastManualSyncTime = 0;
        this.lastCommandSentTime = 0;
        this.lastSentCommand = null;
        this.lastAutoMoneySentTime = 0;
        this.lastAutoPointsSentTime = 0;
        this.lastAutoJobsSentTime = 0;
        this.lastAutoOntimeSentTime = 0;
        this.sendingAutoCommand = false;
        this.lastManualMoneyCmdTime = 0;
        this.lastManualPointsCmdTime = 0;
        this.lastManualJobsCmdTime = 0;
        this.lastManualOntimeCmdTime = 0;
        this.lastAnyManualCommandTime = 0;
        this.lastForeignPlayerQueryTime = 0;
        this.pendingMoneyQuery = false;
        this.pendingPointsQuery = false;
        this.pendingJobsQuery = false;
        this.pendingInitialJoinSync = false;
        this.joinTimestamp = 0;
        this.lastKnownLevel = null;
        this.currentServerType = ServerType.UNKNOWN;
        this.lastPlayerX = Double.NaN;
        this.lastPlayerY = Double.NaN;
        this.lastPlayerZ = Double.NaN;
        this.lastPlayerYaw = Float.NaN;
        this.lastPlayerPitch = Float.NaN;
        this.lastActivityTime = 0;
        this.afkActive = false;
        this.afkStartX = Double.NaN;
        this.afkStartY = Double.NaN;
        this.afkStartZ = Double.NaN;
        this.blockedCommandsOnCurrentWorld.clear();
        this.commandQueue.clear();
        resetDetectionState();
        TabListSnapshot.reset();
        this.timeSupplier = System::currentTimeMillis;
    }

    public synchronized void setCurrentServerTypeForTesting(ServerType type) {
        this.currentServerType = type;
    }

    public synchronized void setLastCommandSentForTesting(String cmd, long time) {
        recordAutoCommandSent(cmd, time);
    }

    public synchronized void setAfkActiveForTesting(boolean active, double x, double y, double z) {
        this.afkActive = active;
        this.afkStartX = x;
        this.afkStartY = y;
        this.afkStartZ = z;
    }

    public synchronized Set<String> getBlockedCommandsForTesting() {
        return new HashSet<>(this.blockedCommandsOnCurrentWorld);
    }

    public synchronized Deque<String> getCommandQueueForTesting() {
        return new ArrayDeque<>(this.commandQueue);
    }

    public synchronized void queueCommandForTesting(String cmd) {
        queueCommand(cmd);
    }

    public synchronized boolean isSendingAutoCommandForTesting() {
        return this.sendingAutoCommand;
    }
}
