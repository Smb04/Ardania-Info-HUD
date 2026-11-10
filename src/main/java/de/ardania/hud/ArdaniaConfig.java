package de.ardania.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistente Konfigurationsverwaltung fuer das Ardania Info HUD.
 *
 * <p>Verwaltet alle Benutzereinstellungen:</p>
 * <ul>
 *   <li>6 ergonomische Bildschirmpositionen (Snap-Presets nach DIN EN ISO 9241)</li>
 *   <li>HUD-Skalierung (75% bis 125%) und Hintergrund-Transparenz</li>
 *   <li>Freie Zeilenreihenfolge (Drag-and-Drop / Move-Up / Move-Down)</li>
 *   <li>Job-Fortschrittsstil (Prozent, 2-Pixel-Balken, Aus)</li>
 *   <li>Unterserver-Sichtbarkeitsprofile (Hauptserver, Mine, Event, Creative)</li>
 *   <li>Synchronisations-Intervalle und Mehrsprachigkeit (DE, EN, ES, FR)</li>
 * </ul>
 */
public final class ArdaniaConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path getDefaultConfigPath() {
        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
                return FabricLoader.getInstance().getConfigDir().resolve("ardania_hud.json");
            }
        } catch (Throwable ignored) {
        }
        return Path.of("config", "ardania_hud.json");
    }

    private static ArdaniaConfig INSTANCE = new ArdaniaConfig();

    public enum HudPosition {
        MIDDLE_RIGHT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        MIDDLE_LEFT,
        TOP_LEFT,
        BOTTOM_LEFT;

        public boolean isLeft() {
            return this == MIDDLE_LEFT || this == TOP_LEFT || this == BOTTOM_LEFT;
        }
    }

    public enum HudLanguage {
        AUTO,
        DE_DE,
        EN_US,
        ES_ES,
        FR_FR
    }

    public enum HudRow {
        MONEY,
        SESSION_STATS,
        POINTS,
        PLAYTIME,
        SESSION_TIMER,
        JOBS
    }

    public enum JobProgressStyle {
        PERCENT,
        BAR,
        NONE
    }

    public enum ServerVisibility {
        SHOW,
        TAB_ONLY,
        HIDE
    }

    /**
     * Manuelle Korrektur der Unterserver-Erkennung. {@link #AUTO} verwendet die
     * automatische Erkennung ueber Tablisten-Header und weitere Signale.
     */
    public enum ServerOverride {
        AUTO,
        HAUPTSERVER,
        FALLBACK,
        MINE,
        EVENT,
        CREATIVE
    }

    public int configVersion = 3;
    public boolean enabled = true;
    public HudLanguage language = HudLanguage.AUTO;
    public HudPosition position = HudPosition.MIDDLE_RIGHT;
    public boolean showMoney = true;
    public boolean showPoints = true;
    public boolean showPlaytime = true;
    public boolean showSessionTimer = false;
    public boolean showSessionStats = false;
    public boolean showJobs = true;
    public boolean showJobXp = true;
    public JobProgressStyle jobProgressStyle = JobProgressStyle.PERCENT;

    public boolean useCustomPosition = false;
    public int customX = -1;
    public int customY = -1;
    public float customXRatio = -1.0f;
    public float customYRatio = -1.0f;

    public static List<HudRow> createDefaultRowOrder() {
        return new ArrayList<>(List.of(
                HudRow.MONEY,
                HudRow.SESSION_STATS,
                HudRow.POINTS,
                HudRow.PLAYTIME,
                HudRow.SESSION_TIMER,
                HudRow.JOBS
        ));
    }

    public List<HudRow> rowOrder = createDefaultRowOrder();

    public ServerVisibility visibilityHauptserver = ServerVisibility.SHOW;
    public ServerVisibility visibilityMine = ServerVisibility.SHOW;
    public ServerVisibility visibilityCreative = ServerVisibility.HIDE;
    public ServerVisibility visibilityEventserver = ServerVisibility.TAB_ONLY;
    public ServerVisibility visibilityFallback = ServerVisibility.TAB_ONLY;

    public ServerOverride serverTypeOverride = ServerOverride.AUTO;

    public float hudScale = 1.0f;
    public float backgroundOpacity = 0.5f;
    public boolean compactNumbers = false;

    public boolean combinePlaytime = true;
    public boolean playtimeHauptserver = true;
    public boolean playtimeCreative = true;
    public boolean playtimeEventserver = true;
    public boolean playtimeMine = true;

    public boolean autoHide = false;
    public int autoHideDurationSeconds = 5;
    public boolean showOnTab = true;

    public boolean autoSyncOnJoin = true;
    public boolean autoSyncPeriodic = true;
    public int moneySyncCooldownSeconds = 30;
    public int pointsSyncIntervalSeconds = 60;
    public int playtimeSyncIntervalMinutes = 60;

    public static ArdaniaConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Wandelt den konfigurierten Override in einen Servertyp um.
     *
     * @return Fixierter Servertyp oder {@link ArdaniaSyncManager.ServerType#UNKNOWN} fuer Automatik
     */
    public ArdaniaSyncManager.ServerType getServerTypeOverride() {
        if (this.serverTypeOverride == null || this.serverTypeOverride == ServerOverride.AUTO) {
            return ArdaniaSyncManager.ServerType.UNKNOWN;
        }
        return switch (this.serverTypeOverride) {
            case HAUPTSERVER -> ArdaniaSyncManager.ServerType.HAUPTSERVER;
            case FALLBACK -> ArdaniaSyncManager.ServerType.FALLBACK;
            case MINE -> ArdaniaSyncManager.ServerType.MINE;
            case EVENT -> ArdaniaSyncManager.ServerType.EVENT;
            case CREATIVE -> ArdaniaSyncManager.ServerType.CREATIVE;
            default -> ArdaniaSyncManager.ServerType.UNKNOWN;
        };
    }

    public ServerVisibility getVisibilityForServer(ArdaniaSyncManager.ServerType serverType) {
        if (serverType == null) return ServerVisibility.SHOW;
        return switch (serverType) {
            case HAUPTSERVER -> visibilityHauptserver != null ? visibilityHauptserver : ServerVisibility.SHOW;
            case MINE -> visibilityMine != null ? visibilityMine : ServerVisibility.SHOW;
            case CREATIVE -> visibilityCreative != null ? visibilityCreative : ServerVisibility.HIDE;
            case EVENT -> visibilityEventserver != null ? visibilityEventserver : ServerVisibility.TAB_ONLY;
            case FALLBACK -> visibilityFallback != null ? visibilityFallback : ServerVisibility.TAB_ONLY;
            default -> ServerVisibility.SHOW;
        };
    }

    public void updateCustomPosition(int x, int y, int screenWidth, int screenHeight, int hudWidth, int hudHeight) {
        this.useCustomPosition = true;
        int maxX = Math.max(0, screenWidth - hudWidth);
        int maxY = Math.max(0, screenHeight - hudHeight);
        int clampedX = Math.max(0, Math.min(x, maxX));
        int clampedY = Math.max(0, Math.min(y, maxY));
        this.customX = clampedX;
        this.customY = clampedY;
        this.customXRatio = maxX > 0 ? (float) clampedX / (float) maxX : 0.0f;
        this.customYRatio = maxY > 0 ? (float) clampedY / (float) maxY : 0.0f;
    }

    public void moveRow(HudRow row, int direction) {
        validate();
        int idx = this.rowOrder.indexOf(row);
        if (idx < 0) return;
        int targetIdx = idx + direction;
        if (targetIdx < 0 || targetIdx >= this.rowOrder.size()) return;
        HudRow other = this.rowOrder.get(targetIdx);
        this.rowOrder.set(targetIdx, row);
        this.rowOrder.set(idx, other);
    }

    public void resetLayoutToPreset() {
        this.useCustomPosition = false;
        this.customX = -1;
        this.customY = -1;
        this.customXRatio = -1.0f;
        this.customYRatio = -1.0f;
        this.hudScale = 1.0f;
        this.rowOrder = createDefaultRowOrder();
    }

    public void validate() {
        if (this.configVersion < 3) {
            if (this.jobProgressStyle == null) {
                this.jobProgressStyle = this.showJobXp ? JobProgressStyle.PERCENT : JobProgressStyle.NONE;
            }
            this.configVersion = 3;
        }
        if (this.language == null) {
            this.language = HudLanguage.AUTO;
        }
        if (this.position == null) {
            this.position = HudPosition.MIDDLE_RIGHT;
        }
        if (this.useCustomPosition) {
            if (this.customXRatio >= 0.0f) {
                this.customXRatio = Math.clamp(this.customXRatio, 0.0f, 1.0f);
            }
            if (this.customYRatio >= 0.0f) {
                this.customYRatio = Math.clamp(this.customYRatio, 0.0f, 1.0f);
            }
            if (this.customX < 0 && this.customXRatio < 0.0f) {
                this.useCustomPosition = false;
            }
        }
        if (this.jobProgressStyle == null) {
            this.jobProgressStyle = JobProgressStyle.PERCENT;
        }
        this.showJobXp = (this.jobProgressStyle != JobProgressStyle.NONE);

        if (this.visibilityHauptserver == null) this.visibilityHauptserver = ServerVisibility.SHOW;
        if (this.visibilityMine == null) this.visibilityMine = ServerVisibility.SHOW;
        if (this.visibilityCreative == null) this.visibilityCreative = ServerVisibility.HIDE;
        if (this.visibilityEventserver == null) this.visibilityEventserver = ServerVisibility.TAB_ONLY;
        if (this.visibilityFallback == null) this.visibilityFallback = ServerVisibility.TAB_ONLY;
        if (this.serverTypeOverride == null) this.serverTypeOverride = ServerOverride.AUTO;

        if (this.rowOrder == null || this.rowOrder.isEmpty()) {
            this.rowOrder = new java.util.ArrayList<>(java.util.List.of(
                    HudRow.MONEY,
                    HudRow.SESSION_STATS,
                    HudRow.POINTS,
                    HudRow.PLAYTIME,
                    HudRow.SESSION_TIMER,
                    HudRow.JOBS
            ));
        } else {
            // Remove duplicates and nulls
            java.util.List<HudRow> cleaned = new java.util.ArrayList<>();
            for (HudRow row : this.rowOrder) {
                if (row != null && !cleaned.contains(row)) {
                    cleaned.add(row);
                }
            }
            for (HudRow r : HudRow.values()) {
                if (!cleaned.contains(r)) {
                    cleaned.add(r);
                }
            }
            this.rowOrder = cleaned;
        }

        this.hudScale = Math.clamp(this.hudScale, 0.7f, 1.5f);
        this.backgroundOpacity = Math.clamp(this.backgroundOpacity, 0.0f, 1.0f);
        this.autoHideDurationSeconds = Math.clamp(this.autoHideDurationSeconds, 1, 60);
        this.moneySyncCooldownSeconds = Math.clamp(this.moneySyncCooldownSeconds, 5, 600);
        this.pointsSyncIntervalSeconds = Math.clamp(this.pointsSyncIntervalSeconds, 10, 1800);
        this.playtimeSyncIntervalMinutes = Math.clamp(this.playtimeSyncIntervalMinutes, 1, 720);
    }

    public static void load() {
        Path configPath = getDefaultConfigPath();
        if (!Files.exists(configPath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            ArdaniaConfig loaded = GSON.fromJson(reader, ArdaniaConfig.class);
            if (loaded != null) {
                loaded.validate();
                INSTANCE = loaded;
            }
        } catch (IOException | JsonParseException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte Konfiguration nicht laden, verwende Standardwerte.", e);
        }
    }

    public static void save() {
        INSTANCE.validate();
        Path configPath = getDefaultConfigPath();
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte Konfiguration nicht speichern.", e);
        }
    }
}
