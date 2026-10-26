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

public final class ArdaniaConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ardania_hud.json");

    private static ArdaniaConfig INSTANCE = new ArdaniaConfig();

    public enum HudPosition {
        MIDDLE_RIGHT,
        TOP_RIGHT,
        BOTTOM_RIGHT
    }

    public boolean enabled = true;
    public HudPosition position = HudPosition.MIDDLE_RIGHT;
    public boolean showMoney = true;
    public boolean showPoints = true;
    public boolean showPlaytime = true;
    public boolean showJobs = true;

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

    public void validate() {
        if (this.position == null) {
            this.position = HudPosition.MIDDLE_RIGHT;
        }
        this.autoHideDurationSeconds = Math.clamp(this.autoHideDurationSeconds, 1, 60);
        this.moneySyncCooldownSeconds = Math.clamp(this.moneySyncCooldownSeconds, 5, 600);
        this.pointsSyncIntervalSeconds = Math.clamp(this.pointsSyncIntervalSeconds, 10, 1800);
        this.playtimeSyncIntervalMinutes = Math.clamp(this.playtimeSyncIntervalMinutes, 1, 720);
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
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
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte Konfiguration nicht speichern.", e);
        }
    }
}
