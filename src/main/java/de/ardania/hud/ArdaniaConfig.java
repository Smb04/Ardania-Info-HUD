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
 * <p>Verwaltet alle Benutzereinstellungen in {@code config/ardania_hud.json}:</p>
 * <ul>
 *   <li>6 ergonomische Bildschirmpositionen (Snap-Presets nach DIN EN ISO 9241) sowie freie Positionierung</li>
 *   <li>HUD-Skalierung (75% bis 125% im Menue, validiert 70% bis 150%) und Hintergrund-Transparenz</li>
 *   <li>Konfigurierbare Zeilenreihenfolge ({@link HudRow})</li>
 *   <li>Job-Fortschrittsstil (Prozent, 2-Pixel-Balken, Aus)</li>
 *   <li>Unterserver-Sichtbarkeitsprofile (Hauptserver, Mine, Event, Creative)</li>
 *   <li>Synchronisations-Intervalle, Server-Override und Mehrsprachigkeit (DE, EN, ES, FR)</li>
 * </ul>
 */
public final class ArdaniaConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Erstellt eine Konfigurationsinstanz mit Standardwerten.
     */
    public ArdaniaConfig() {
    }

    /**
     * Liefert den Standard-Dateipfad der JSON-Konfiguration ({@code config/ardania_hud.json}).
     *
     * @return Pfad zur Konfigurationsdatei
     */
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

    /**
     * Vordefinierte Ankerpositionen fuer das HUD-Overlay.
     */
    public enum HudPosition {
        /** Rechts mittig (klassischer Hypixel-Scoreboard-Stil). */
        MIDDLE_RIGHT,
        /** Oben rechts. */
        TOP_RIGHT,
        /** Unten rechts. */
        BOTTOM_RIGHT,
        /** Links mittig. */
        MIDDLE_LEFT,
        /** Oben links. */
        TOP_LEFT,
        /** Unten links. */
        BOTTOM_LEFT;

        /**
         * Prueft, ob diese Position am linken Bildschirmrand liegt.
         *
         * @return {@code true} bei linker Positionierung
         */
        public boolean isLeft() {
            return this == MIDDLE_LEFT || this == TOP_LEFT || this == BOTTOM_LEFT;
        }
    }

    /**
     * Unterstuetzte Oberflaechensprachen fuer HUD und Konfigurationsmenue.
     */
    public enum HudLanguage {
        /** Automatische Erkennung anhand der Minecraft-Client-Sprache. */
        AUTO,
        /** Deutsch. */
        DE_DE,
        /** Englisch (US). */
        EN_US,
        /** Spanisch. */
        ES_ES,
        /** Franzoesisch. */
        FR_FR
    }

    /**
     * Verfuegbare Informationszeilen innerhalb der HUD-Box.
     */
    public enum HudRow {
        /** Kontostand in Euronen. */
        MONEY,
        /** Netto-Gewinn und Stundenlohn in der aktuellen Sitzung. */
        SESSION_STATS,
        /** Aktuelle ArdaPoints. */
        POINTS,
        /** Spielzeit (kombiniert oder je Unterserver). */
        PLAYTIME,
        /** Laufzeit der aktuellen Spielsitzung. */
        SESSION_TIMER,
        /** Aktive Jobs mit Level und XP-Fortschritt. */
        JOBS
    }

    /**
     * Darstellungsart fuer den Job-XP-Fortschritt.
     */
    public enum JobProgressStyle {
        /** Kompakte Prozentzahl vor dem Job-Level (z. B. {@code 80% [24]}). */
        PERCENT,
        /** Subtiler 2-Pixel-Fortschrittsbalken unterhalb der Job-Zeile. */
        BAR,
        /** Keine XP-Fortschrittsanzeige (nur Jobname und Level). */
        NONE
    }

    /**
     * Sichtbarkeitsmodus des HUDs auf einem bestimmten Unterserver.
     */
    public enum ServerVisibility {
        /** Dauerhaft sichtbar. */
        SHOW,
        /** Nur sichtbar beim Druecken der Tab-Taste. */
        TAB_ONLY,
        /** Auf diesem Unterserver ausgeblendet. */
        HIDE
    }

    /**
     * Manuelle Korrektur der Unterserver-Erkennung. {@link #AUTO} verwendet die
     * automatische Erkennung ueber Tablisten-Header und weitere Signale.
     */
    public enum ServerOverride {
        /** Automatische Erkennung. */
        AUTO,
        /** Erzwingt Hauptserver. */
        HAUPTSERVER,
        /** Erzwingt Fallback-Server. */
        FALLBACK,
        /** Erzwingt Minenwelten. */
        MINE,
        /** Erzwingt Eventserver. */
        EVENT,
        /** Erzwingt Creative-Server. */
        CREATIVE
    }

    /** Schemaversion fuer Migrationspruefungen. */
    public int configVersion = 3;
    /** Globaler Hauptschalter zum Aktivieren oder Deaktivieren des HUDs. */
    public boolean enabled = true;
    /** Konfigurierte Anzeige- und Menuesprache. */
    public HudLanguage language = HudLanguage.AUTO;
    /** Ausgewaehlte Ankerposition (falls {@link #useCustomPosition} {@code false} ist). */
    public HudPosition position = HudPosition.MIDDLE_RIGHT;
    /** Zeigt den Euronen-Kontostand im HUD an. */
    public boolean showMoney = true;
    /** Zeigt die ArdaPoints im HUD an. */
    public boolean showPoints = true;
    /** Zeigt die Spielzeit im HUD an. */
    public boolean showPlaytime = true;
    /** Zeigt den laufenden Session-Timer im HUD an. */
    public boolean showSessionTimer = false;
    /** Zeigt Netto-Gewinn und Stundenlohn der aktuellen Sitzung im HUD an. */
    public boolean showSessionStats = false;
    /** Zeigt die Jobliste im HUD an. */
    public boolean showJobs = true;
    /** Abgeleitete Legacy-Option fuer Job-XP-Anzeige ({@code jobProgressStyle != NONE}). */
    public boolean showJobXp = true;
    /** Darstellungsmodus fuer den Job-XP-Fortschritt. */
    public JobProgressStyle jobProgressStyle = JobProgressStyle.PERCENT;

    /** Ob eine frei verschobene benutzerdefinierte Position aktiv ist. */
    public boolean useCustomPosition = false;
    /** Benutzerdefinierte X-Koordinate in skalierten Pixeln ({@code -1} = nicht gesetzt). */
    public int customX = -1;
    /** Benutzerdefinierte Y-Koordinate in skalierten Pixeln ({@code -1} = nicht gesetzt). */
    public int customY = -1;
    /** Relative horizontale Bildschirmposition (0.0 bis 1.0) fuer Aufloesungswechsel. */
    public float customXRatio = -1.0f;
    /** Relative vertikale Bildschirmposition (0.0 bis 1.0) fuer Aufloesungswechsel. */
    public float customYRatio = -1.0f;

    /**
     * Erzeugt die Standard-Reihenfolge der HUD-Zeilen.
     *
     * @return Veraenderbare Liste der {@link HudRow}-Eintraege
     */
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

    /** Aktuelle Reihenfolge der HUD-Zeilen. */
    public List<HudRow> rowOrder = createDefaultRowOrder();

    /** Sichtbarkeitsmodus auf dem Hauptserver. */
    public ServerVisibility visibilityHauptserver = ServerVisibility.SHOW;
    /** Sichtbarkeitsmodus auf dem Mine-Server. */
    public ServerVisibility visibilityMine = ServerVisibility.SHOW;
    /** Sichtbarkeitsmodus auf dem Creative-Server. */
    public ServerVisibility visibilityCreative = ServerVisibility.HIDE;
    /** Sichtbarkeitsmodus auf dem Eventserver. */
    public ServerVisibility visibilityEventserver = ServerVisibility.TAB_ONLY;
    /** Sichtbarkeitsmodus auf dem Fallback-Server. */
    public ServerVisibility visibilityFallback = ServerVisibility.TAB_ONLY;

    /** Manueller Override der Unterserver-Erkennung. */
    public ServerOverride serverTypeOverride = ServerOverride.AUTO;

    /** Skalierungsfaktor des HUDs (0.7 bis 1.5; im Menue 0.75 bis 1.25). */
    public float hudScale = 1.0f;
    /** Deckkraft des HUD-Hintergrunds (0.0 = transparent bis 1.0 = deckend). */
    public float backgroundOpacity = 0.5f;
    /** Formatiert groessere Geldbetraege kompakt mit {@code k} bzw. {@code M}. */
    public boolean compactNumbers = false;

    /** Addiert ausgewaehlte Welt-Spielzeiten zu einer Gesamtsumme ({@code true}) oder listet sie einzeln auf. */
    public boolean combinePlaytime = true;
    /** Beruecksichtigt die Spielzeit des Hauptservers. */
    public boolean playtimeHauptserver = true;
    /** Beruecksichtigt die Spielzeit des Creative-Servers. */
    public boolean playtimeCreative = true;
    /** Beruecksichtigt die Spielzeit des Eventservers. */
    public boolean playtimeEventserver = true;
    /** Beruecksichtigt die Spielzeit des Mine-Servers. */
    public boolean playtimeMine = true;

    /** Blendet das HUD nach Ablauf von {@link #autoHideDurationSeconds} automatisch seitlich aus. */
    public boolean autoHide = false;
    /** Anzeigedauer in Sekunden bei aktivem Auto-Ausblenden oder Tab-Einblendung (1 bis 60 s). */
    public int autoHideDurationSeconds = 5;
    /** Blendet das HUD beim Druecken der Tab-Taste ein. */
    public boolean showOnTab = true;

    /** Fuehrt beim Betreten eines Ardania-Unterservers automatisch einen Komplettabgleich aus. */
    public boolean autoSyncOnJoin = true;
    /** Aktiviert ereignis- und intervallbasierte Hintergrundabfragen. */
    public boolean autoSyncPeriodic = true;
    /** Mindestabstand in Sekunden fuer automatische {@code /geld}-Abfragen. */
    public int moneySyncCooldownSeconds = 30;
    /** Abfrageintervall in Sekunden fuer {@code /ardapoints me}. */
    public int pointsSyncIntervalSeconds = 60;
    /** Abfrageintervall in Minuten fuer {@code /ontime}. */
    public int playtimeSyncIntervalMinutes = 60;

    /**
     * Liefert die aktuell geladene Konfigurationsinstanz.
     *
     * @return Zentrale {@link ArdaniaConfig}-Instanz
     */
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

    /**
     * Liefert die konfigurierte HUD-Sichtbarkeit fuer einen bestimmten Unterserver.
     *
     * @param serverType Aktuell erkannter Unterserver
     * @return Zugehoeriges {@link ServerVisibility}-Profil
     */
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

    /**
     * Speichert eine benutzerdefinierte HUD-Position inklusive relativer Bildschirmverhaeltnisse.
     *
     * @param x            Gewuenschte X-Koordinate in skalierten Pixeln
     * @param y            Gewuenschte Y-Koordinate in skalierten Pixeln
     * @param screenWidth  Verfuegbare Bildschirmbreite in skalierten Pixeln
     * @param screenHeight Verfuegbare Bildschirmhoehe in skalierten Pixeln
     * @param hudWidth     Breite der HUD-Box in skalierten Pixeln
     * @param hudHeight    Hoehe der HUD-Box in skalierten Pixeln
     */
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

    /**
     * Verschiebt eine HUD-Zeile innerhalb der konfigurierten {@link #rowOrder}.
     *
     * @param row       Zu verschiebende Zeile
     * @param direction Schrittweite (z. B. {@code -1} nach oben, {@code +1} nach unten)
     */
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

    /**
     * Setzt freie Position, Skalierung und Zeilenreihenfolge auf das Standard-Preset zurueck.
     */
    public void resetLayoutToPreset() {
        this.useCustomPosition = false;
        this.customX = -1;
        this.customY = -1;
        this.customXRatio = -1.0f;
        this.customYRatio = -1.0f;
        this.hudScale = 1.0f;
        this.rowOrder = createDefaultRowOrder();
    }

    /**
     * Prueft und normalisiert saemtliche Konfigurationswerte in gueltige Wertebereiche.
     */
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

    /**
     * Laedt die Konfiguration aus {@link #getDefaultConfigPath()} oder legt eine neue Standarddatei an.
     */
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

    /**
     * Validiert und speichert die aktuelle Konfiguration als UTF-8-JSON auf der Festplatte.
     */
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
