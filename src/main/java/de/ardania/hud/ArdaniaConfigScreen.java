package de.ardania.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Dreistufiger Konfigurations-Bildschirm fuer moderne Minecraft-Versionen (ModMenu &amp; {@code /ardaniahud config}).
 *
 * <p>Bietet drei Reiter (Tabs):</p>
 * <ul>
 *   <li>Anzeige &amp; Layout (HUD ein/aus, 6 Ankerpositionen, Skalierung, Hintergrund, Sprache, Kompakte Zahlen, Anzeigefelder, HUD-Editor)</li>
 *   <li>Spielzeit &amp; Welten (Spielzeit ein/aus, Zusammenrechnen, Session-Timer, Sichtbarkeitsprofile, Weltenauswahl)</li>
 *   <li>Sync &amp; Slide (Auto-Sync, Abfrage-Intervalle, Sync beim Beitreten, Server-Erkennung, Auto-Ausblenden, Anzeigedauer, Bei Tab anzeigen)</li>
 * </ul>
 */
public final class ArdaniaConfigScreen extends Screen {

    private enum ConfigTab {
        GENERAL,
        PLAYTIME,
        SYNC
    }

    private final Screen parent;
    private final ArdaniaConfig config;
    private ConfigTab currentTab = ConfigTab.GENERAL;

    /**
     * Erstellt den Konfigurations-Bildschirm.
     *
     * @param parent Uebergeordneter Bildschirm, zu dem beim Schliessen zurueckgekehrt wird (kann {@code null} sein)
     */
    public ArdaniaConfigScreen(Screen parent) {
        super(ArdaniaI18n.translate("config.ardania_hud.title"));
        this.parent = parent;
        this.config = ArdaniaConfig.getInstance();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonHeight = 20;

        int tabWidth = 104;
        int tabGap = 4;
        int tabsStartX = centerX - ((tabWidth * 3 + tabGap * 2) / 2);
        int tabY = 26;

        Button generalTabBtn = Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.tab.general"),
                btn -> switchTab(ConfigTab.GENERAL)
        ).bounds(tabsStartX, tabY, tabWidth, buttonHeight).build();
        generalTabBtn.active = (this.currentTab != ConfigTab.GENERAL);
        addRenderableWidget(generalTabBtn);

        Button playtimeTabBtn = Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.tab.playtime"),
                btn -> switchTab(ConfigTab.PLAYTIME)
        ).bounds(tabsStartX + tabWidth + tabGap, tabY, tabWidth, buttonHeight).build();
        playtimeTabBtn.active = (this.currentTab != ConfigTab.PLAYTIME);
        addRenderableWidget(playtimeTabBtn);

        Button syncTabBtn = Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.tab.sync"),
                btn -> switchTab(ConfigTab.SYNC)
        ).bounds(tabsStartX + (tabWidth + tabGap) * 2, tabY, tabWidth, buttonHeight).build();
        syncTabBtn.active = (this.currentTab != ConfigTab.SYNC);
        addRenderableWidget(syncTabBtn);

        int colWidth = 156;
        int spacing = 24;
        int startY = 56;
        int leftX = centerX - colWidth - 4;
        int rightX = centerX + 4;

        int contentBottom = switch (this.currentTab) {
            case GENERAL -> initGeneralTab(leftX, rightX, startY, colWidth, buttonHeight, spacing);
            case PLAYTIME -> initPlaytimeTab(leftX, rightX, startY, colWidth, buttonHeight, spacing);
            case SYNC -> initSyncTab(leftX, rightX, startY, colWidth, buttonHeight, spacing);
        };

        int doneY = Math.max(contentBottom + 12, this.height - 28);
        addRenderableWidget(Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.done"),
                btn -> onClose()
        ).bounds(centerX - 100, doneY, 200, buttonHeight).build());
    }

    private void switchTab(ConfigTab tab) {
        this.currentTab = tab;
        this.clearWidgets();
        this.init();
    }

    private int initGeneralTab(int leftX, int rightX, int startY, int colWidth, int buttonHeight, int spacing) {
        int yLeft = startY;

        addRenderableWidget(Button.builder(
                getEnabledText(),
                btn -> {
                    config.enabled = !config.enabled;
                    if (config.enabled) {
                        ArdaniaState.getInstance().notifyStatUpdated();
                    }
                    btn.setMessage(getEnabledText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getPositionText(),
                btn -> {
                    config.useCustomPosition = false;
                    config.position = switch (config.position) {
                        case MIDDLE_RIGHT -> ArdaniaConfig.HudPosition.TOP_RIGHT;
                        case TOP_RIGHT -> ArdaniaConfig.HudPosition.BOTTOM_RIGHT;
                        case BOTTOM_RIGHT -> ArdaniaConfig.HudPosition.MIDDLE_LEFT;
                        case MIDDLE_LEFT -> ArdaniaConfig.HudPosition.TOP_LEFT;
                        case TOP_LEFT -> ArdaniaConfig.HudPosition.BOTTOM_LEFT;
                        case BOTTOM_LEFT -> ArdaniaConfig.HudPosition.MIDDLE_RIGHT;
                    };
                    btn.setMessage(getPositionText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getScaleText(),
                btn -> {
                    config.hudScale = switch (Math.round(config.hudScale * 100.0f)) {
                        case 75 -> 0.9f;
                        case 90 -> 1.0f;
                        case 100 -> 1.1f;
                        case 110 -> 1.25f;
                        case 125 -> 0.75f;
                        default -> 1.0f;
                    };
                    btn.setMessage(getScaleText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getOpacityText(),
                btn -> {
                    config.backgroundOpacity = switch ((int) Math.round(config.backgroundOpacity * 100.0f)) {
                        case 0 -> 0.25f;
                        case 25 -> 0.50f;
                        case 50 -> 0.75f;
                        case 75 -> 1.0f;
                        default -> 0.0f;
                    };
                    btn.setMessage(getOpacityText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getLanguageText(),
                btn -> {
                    config.language = switch (config.language) {
                        case AUTO -> ArdaniaConfig.HudLanguage.DE_DE;
                        case DE_DE -> ArdaniaConfig.HudLanguage.EN_US;
                        case EN_US -> ArdaniaConfig.HudLanguage.ES_ES;
                        case ES_ES -> ArdaniaConfig.HudLanguage.FR_FR;
                        case FR_FR -> ArdaniaConfig.HudLanguage.AUTO;
                    };
                    ArdaniaConfig.save();
                    this.clearWidgets();
                    this.init();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.compact_numbers", config.compactNumbers),
                btn -> {
                    config.compactNumbers = !config.compactNumbers;
                    btn.setMessage(getToggleText("config.ardania_hud.compact_numbers", config.compactNumbers));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        int yRight = startY;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_money", config.showMoney),
                btn -> {
                    config.showMoney = !config.showMoney;
                    btn.setMessage(getToggleText("config.ardania_hud.show_money", config.showMoney));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_points", config.showPoints),
                btn -> {
                    config.showPoints = !config.showPoints;
                    btn.setMessage(getToggleText("config.ardania_hud.show_points", config.showPoints));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_jobs", config.showJobs),
                btn -> {
                    config.showJobs = !config.showJobs;
                    btn.setMessage(getToggleText("config.ardania_hud.show_jobs", config.showJobs));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getJobProgressStyleText(),
                btn -> {
                    config.jobProgressStyle = switch (config.jobProgressStyle) {
                        case PERCENT -> ArdaniaConfig.JobProgressStyle.BAR;
                        case BAR -> ArdaniaConfig.JobProgressStyle.NONE;
                        case NONE -> ArdaniaConfig.JobProgressStyle.PERCENT;
                    };
                    config.showJobXp = (config.jobProgressStyle != ArdaniaConfig.JobProgressStyle.NONE);
                    btn.setMessage(getJobProgressStyleText());
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_session_stats", config.showSessionStats),
                btn -> {
                    config.showSessionStats = !config.showSessionStats;
                    btn.setMessage(getToggleText("config.ardania_hud.show_session_stats", config.showSessionStats));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.open_editor"),
                btn -> ArdaniaClientMod.openScreen(this.minecraft, new ArdaniaHudEditorScreen(this))
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        return Math.max(yLeft, yRight) + buttonHeight;
    }

    private int initPlaytimeTab(int leftX, int rightX, int startY, int colWidth, int buttonHeight, int spacing) {
        int yLeft = startY;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_playtime", config.showPlaytime),
                btn -> {
                    config.showPlaytime = !config.showPlaytime;
                    btn.setMessage(getToggleText("config.ardania_hud.show_playtime", config.showPlaytime));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.combine_playtime", config.combinePlaytime),
                btn -> {
                    config.combinePlaytime = !config.combinePlaytime;
                    btn.setMessage(getToggleText("config.ardania_hud.combine_playtime", config.combinePlaytime));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_session", config.showSessionTimer),
                btn -> {
                    config.showSessionTimer = !config.showSessionTimer;
                    btn.setMessage(getToggleText("config.ardania_hud.show_session", config.showSessionTimer));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getServerVisibilityText("config.ardania_hud.visibility.creative", config.visibilityCreative),
                btn -> {
                    config.visibilityCreative = switch (config.visibilityCreative) {
                        case HIDE -> ArdaniaConfig.ServerVisibility.TAB_ONLY;
                        case TAB_ONLY -> ArdaniaConfig.ServerVisibility.SHOW;
                        case SHOW -> ArdaniaConfig.ServerVisibility.HIDE;
                    };
                    btn.setMessage(getServerVisibilityText("config.ardania_hud.visibility.creative", config.visibilityCreative));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getServerVisibilityText("config.ardania_hud.visibility.event", config.visibilityEventserver),
                btn -> {
                    config.visibilityEventserver = switch (config.visibilityEventserver) {
                        case TAB_ONLY -> ArdaniaConfig.ServerVisibility.SHOW;
                        case SHOW -> ArdaniaConfig.ServerVisibility.HIDE;
                        case HIDE -> ArdaniaConfig.ServerVisibility.TAB_ONLY;
                    };
                    btn.setMessage(getServerVisibilityText("config.ardania_hud.visibility.event", config.visibilityEventserver));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getServerVisibilityText("config.ardania_hud.visibility.mine", config.visibilityMine),
                btn -> {
                    config.visibilityMine = switch (config.visibilityMine) {
                        case SHOW -> ArdaniaConfig.ServerVisibility.TAB_ONLY;
                        case TAB_ONLY -> ArdaniaConfig.ServerVisibility.HIDE;
                        case HIDE -> ArdaniaConfig.ServerVisibility.SHOW;
                    };
                    btn.setMessage(getServerVisibilityText("config.ardania_hud.visibility.mine", config.visibilityMine));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        int yRight = startY;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.server.hauptserver", config.playtimeHauptserver),
                btn -> {
                    config.playtimeHauptserver = !config.playtimeHauptserver;
                    btn.setMessage(getToggleText("config.ardania_hud.server.hauptserver", config.playtimeHauptserver));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.server.creative", config.playtimeCreative),
                btn -> {
                    config.playtimeCreative = !config.playtimeCreative;
                    btn.setMessage(getToggleText("config.ardania_hud.server.creative", config.playtimeCreative));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.server.eventserver", config.playtimeEventserver),
                btn -> {
                    config.playtimeEventserver = !config.playtimeEventserver;
                    btn.setMessage(getToggleText("config.ardania_hud.server.eventserver", config.playtimeEventserver));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.server.mine", config.playtimeMine),
                btn -> {
                    config.playtimeMine = !config.playtimeMine;
                    btn.setMessage(getToggleText("config.ardania_hud.server.mine", config.playtimeMine));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());

        return Math.max(yLeft, yRight) + buttonHeight;
    }

    private int initSyncTab(int leftX, int rightX, int startY, int colWidth, int buttonHeight, int spacing) {
        int yLeft = startY;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.auto_sync", config.autoSyncPeriodic),
                btn -> {
                    config.autoSyncPeriodic = !config.autoSyncPeriodic;
                    btn.setMessage(getToggleText("config.ardania_hud.auto_sync", config.autoSyncPeriodic));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getMoneyIntervalText(),
                btn -> {
                    config.moneySyncCooldownSeconds = switch (config.moneySyncCooldownSeconds) {
                        case 15 -> 30;
                        case 30 -> 60;
                        case 60 -> 120;
                        default -> 15;
                    };
                    btn.setMessage(getMoneyIntervalText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getPointsIntervalText(),
                btn -> {
                    config.pointsSyncIntervalSeconds = switch (config.pointsSyncIntervalSeconds) {
                        case 30 -> 60;
                        case 60 -> 120;
                        case 120 -> 300;
                        default -> 30;
                    };
                    btn.setMessage(getPointsIntervalText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getPlaytimeIntervalText(),
                btn -> {
                    config.playtimeSyncIntervalMinutes = switch (config.playtimeSyncIntervalMinutes) {
                        case 15 -> 30;
                        case 30 -> 60;
                        case 60 -> 120;
                        default -> 15;
                    };
                    btn.setMessage(getPlaytimeIntervalText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.sync_join", config.autoSyncOnJoin),
                btn -> {
                    config.autoSyncOnJoin = !config.autoSyncOnJoin;
                    btn.setMessage(getToggleText("config.ardania_hud.sync_join", config.autoSyncOnJoin));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getServerOverrideText(),
                btn -> {
                    config.serverTypeOverride = switch (config.serverTypeOverride) {
                        case AUTO -> ArdaniaConfig.ServerOverride.HAUPTSERVER;
                        case HAUPTSERVER -> ArdaniaConfig.ServerOverride.FALLBACK;
                        case FALLBACK -> ArdaniaConfig.ServerOverride.MINE;
                        case MINE -> ArdaniaConfig.ServerOverride.EVENT;
                        case EVENT -> ArdaniaConfig.ServerOverride.CREATIVE;
                        case CREATIVE -> ArdaniaConfig.ServerOverride.AUTO;
                    };
                    btn.setMessage(getServerOverrideText());
                    ArdaniaConfig.save();
                    ArdaniaState.getInstance().notifyStatUpdated();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());

        int yRight = startY;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.auto_hide", config.autoHide),
                btn -> {
                    config.autoHide = !config.autoHide;
                    btn.setMessage(getToggleText("config.ardania_hud.auto_hide", config.autoHide));
                    ArdaniaState.getInstance().notifyStatUpdated();
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getAutoHideDurationText(),
                btn -> {
                    config.autoHideDurationSeconds = switch (config.autoHideDurationSeconds) {
                        case 3 -> 5;
                        case 5 -> 8;
                        case 8 -> 10;
                        case 10 -> 15;
                        default -> 3;
                    };
                    btn.setMessage(getAutoHideDurationText());
                    ArdaniaState.getInstance().notifyStatUpdated();
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_on_tab", config.showOnTab),
                btn -> {
                    config.showOnTab = !config.showOnTab;
                    btn.setMessage(getToggleText("config.ardania_hud.show_on_tab", config.showOnTab));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());

        return Math.max(yLeft, yRight) + buttonHeight;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, ArdaniaI18n.translate("config.ardania_hud.title"), this.width / 2, 10, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        ArdaniaConfig.save();
        if (config.enabled) {
            ArdaniaState.getInstance().notifyStatUpdated();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private Component getEnabledText() {
        return getToggleText("config.ardania_hud.enabled", config.enabled);
    }

    private Component getOpacityText() {
        int percent = Math.round(config.backgroundOpacity * 100.0f);
        return ArdaniaI18n.translate("config.ardania_hud.opacity")
                .append(": " + percent + "%");
    }

    private Component getLanguageText() {
        return ArdaniaI18n.translate("config.ardania_hud.language")
                .append(": ")
                .append(ArdaniaI18n.translate("config.ardania_hud.language." + config.language.name().toLowerCase()));
    }

    private Component getServerOverrideText() {
        return ArdaniaI18n.translate("config.ardania_hud.server_override")
                .append(": ")
                .append(ArdaniaI18n.translate(
                        "config.ardania_hud.server_override." + config.serverTypeOverride.name().toLowerCase()));
    }

    private Component getScaleText() {
        int percent = Math.round(config.hudScale * 100.0f);
        return ArdaniaI18n.translate("config.ardania_hud.scale").append(": " + percent + "%");
    }

    private Component getPositionText() {
        if (config.useCustomPosition) {
            return ArdaniaI18n.translate("config.ardania_hud.position")
                    .append(": ")
                    .append(ArdaniaI18n.translate("config.ardania_hud.position.custom"));
        }
        return ArdaniaI18n.translate("config.ardania_hud.position")
                .append(": ")
                .append(ArdaniaI18n.translate("config.ardania_hud.position." + config.position.name().toLowerCase()));
    }

    private Component getJobProgressStyleText() {
        String modeKey = switch (config.jobProgressStyle) {
            case PERCENT -> "config.ardania_hud.job_xp.percent";
            case BAR -> "config.ardania_hud.job_xp.bar";
            case NONE -> "config.ardania_hud.off";
        };
        return ArdaniaI18n.translate("config.ardania_hud.show_job_xp")
                .append(": ")
                .append(ArdaniaI18n.translate(modeKey));
    }

    private Component getServerVisibilityText(String worldKey, ArdaniaConfig.ServerVisibility visibility) {
        String modeKey = switch (visibility) {
            case SHOW -> "config.ardania_hud.visibility.show";
            case TAB_ONLY -> "config.ardania_hud.visibility.tab_only";
            case HIDE -> "config.ardania_hud.visibility.hide";
        };
        return ArdaniaI18n.translate(worldKey)
                .append(": ")
                .append(ArdaniaI18n.translate(modeKey));
    }

    private Component getMoneyIntervalText() {
        return ArdaniaI18n.translate("config.ardania_hud.money_interval")
                .append(": ")
                .append(config.moneySyncCooldownSeconds + "s");
    }

    private Component getPointsIntervalText() {
        return ArdaniaI18n.translate("config.ardania_hud.points_interval")
                .append(": ")
                .append(config.pointsSyncIntervalSeconds + "s");
    }

    private Component getPlaytimeIntervalText() {
        return ArdaniaI18n.translate("config.ardania_hud.playtime_interval")
                .append(": ")
                .append(config.playtimeSyncIntervalMinutes + "m");
    }

    private Component getAutoHideDurationText() {
        return ArdaniaI18n.translate("config.ardania_hud.auto_hide_duration")
                .append(": ")
                .append(config.autoHideDurationSeconds + "s");
    }

    private Component getToggleText(String key, boolean value) {
        return ArdaniaI18n.translate(key)
                .append(": ")
                .append(ArdaniaI18n.translate(value ? "config.ardania_hud.on" : "config.ardania_hud.off"));
    }
}
