package de.ardania.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Modulare Konfigurationsdatei (.minecraft/config/ardania_hud.json)
 * Ermoeglicht anderen Spielern, das HUD nach ihren Wuenschen anzupassen.
 */
public final class ArdaniaConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ardania_hud.json").toFile();

    private static ArdaniaConfig INSTANCE = new ArdaniaConfig();

    public enum HudPosition {
        MIDDLE_RIGHT,
        TOP_RIGHT,
        BOTTOM_RIGHT
    }

    // Anzeige-Optionen
    public boolean enabled = true;
    public HudPosition position = HudPosition.MIDDLE_RIGHT;
    public boolean showMoney = true;
    public boolean showPoints = true;
    public boolean showPlaytime = true;
    public boolean showJobs = true;

    // Auto-Ausblenden & Slide-Animation
    public boolean autoHide = false;
    public int autoHideDurationSeconds = 5;
    public boolean showOnTab = true;

    // Automatisierungs- & Synchronisierungs-Intervalle (in Sekunden)
    public boolean autoSyncOnJoin = true;
    public boolean autoSyncPeriodic = true;
    public int moneySyncCooldownSeconds = 30;     // z. B. 10s, 30s, 60s
    public int pointsSyncIntervalSeconds = 60;    // z. B. 30s, 60s, 120s
    public int playtimeSyncIntervalMinutes = 60;  // z. B. 15m, 30m, 60m

    public static ArdaniaConfig getInstance() {
        return INSTANCE;
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ArdaniaConfig loaded = GSON.fromJson(reader, ArdaniaConfig.class);
            if (loaded != null) {
                INSTANCE = loaded;
            }
        } catch (IOException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte Konfiguration nicht laden, verwende Standardwerte.", e);
        }
    }

    public static void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte Konfiguration nicht speichern.", e);
        }
    }
}
