package de.ardania.hud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArdaniaLayoutAndConfigTest {

    private ArdaniaConfig config;
    private ArdaniaState state;

    @BeforeEach
    void setUp() {
        config = new ArdaniaConfig();
        config.validate();
        state = new ArdaniaState();
    }

    @Test
    void testConfigValidationAndMigration() {
        config.configVersion = 2;
        config.showJobXp = true;
        config.jobProgressStyle = null;
        config.rowOrder = null;
        config.visibilityCreative = null;

        config.validate();

        assertEquals(3, config.configVersion);
        assertEquals(ArdaniaConfig.JobProgressStyle.PERCENT, config.jobProgressStyle);
        assertTrue(config.showJobXp);
        assertNotNull(config.rowOrder);
        assertEquals(6, config.rowOrder.size());
        assertEquals(ArdaniaConfig.ServerVisibility.HIDE, config.visibilityCreative);
    }

    @Test
    void testRowReordering() {
        state.setEuronen(new BigDecimal("100.00"));
        state.setArdaPoints(50);
        state.addOrUpdateJob("Miner", 10);

        // Put JOBS first, then MONEY, then POINTS
        config.rowOrder = new ArrayList<>(List.of(
                ArdaniaConfig.HudRow.JOBS,
                ArdaniaConfig.HudRow.MONEY,
                ArdaniaConfig.HudRow.POINTS
        ));

        List<HudLayout.LineSpec> lines = HudLayout.buildLines(state, config, 1000L);
        // lines[0] is Header "ARDANIA"
        assertEquals("ARDANIA", lines.get(0).left().getString());

        // lines[1] should be Job (Miner)
        assertTrue(lines.get(1).left().getString().contains("Miner"));
        // lines[2] should be Money (100)
        assertTrue(lines.get(2).left().getString().contains("100"));
        // lines[3] should be Points (50)
        assertTrue(lines.get(3).left().getString().contains("50"));
    }

    @Test
    void testProgressBarGeneration() {
        state.addOrUpdateJob("Mobhunter", 24, false, 2497.42, 3120.0);
        state.addOrUpdateJob("Miner", 30, true, 0.0, 0.0);

        config.jobProgressStyle = ArdaniaConfig.JobProgressStyle.BAR;

        List<HudLayout.LineSpec> lines = HudLayout.buildLines(state, config, 1000L);

        // Header + Jobs header + 2 jobs = 4 lines
        HudLayout.LayoutResult layout = HudLayout.calculate(lines, config, 1.0f, 800, 600, comp -> comp.getString().length() * 6);
        assertNotNull(layout.bars());
        assertEquals(2, layout.bars().size());

        // Mobhunter bar fill ratio approx 0.80
        HudLayout.RenderableBar mobhunterBar = layout.bars().get(0);
        assertEquals(0.8004f, mobhunterBar.fillRatio(), 0.01f);
        assertEquals(0xFF55FF55, mobhunterBar.fillColor());

        // Miner max bar fill ratio 1.0, gold color
        HudLayout.RenderableBar minerBar = layout.bars().get(1);
        assertEquals(1.0f, minerBar.fillRatio(), 0.001f);
        assertEquals(0xFFFFAA00, minerBar.fillColor());
    }

    @Test
    void testFixedPresetPositionsAndClamping() {
        List<HudLayout.LineSpec> lines = HudLayout.buildPreviewLines(config);

        config.position = ArdaniaConfig.HudPosition.TOP_LEFT;
        HudLayout.LayoutResult topLeft = HudLayout.calculate(lines, config, 1.0f, 800, 600, comp -> comp.getString().length() * 6);
        assertEquals(HudLayout.EDGE_MARGIN, topLeft.boxX());
        assertEquals(20, topLeft.boxY());

        config.position = ArdaniaConfig.HudPosition.MIDDLE_RIGHT;
        HudLayout.LayoutResult midRight = HudLayout.calculate(lines, config, 1.0f, 800, 600, comp -> comp.getString().length() * 6);
        assertEquals(800 - midRight.boxWidth() - HudLayout.EDGE_MARGIN, midRight.boxX());
        assertEquals((600 - midRight.boxHeight()) / 2, midRight.boxY());

        config.position = ArdaniaConfig.HudPosition.BOTTOM_RIGHT;
        HudLayout.LayoutResult botRight = HudLayout.calculate(lines, config, 1.0f, 800, 600, comp -> comp.getString().length() * 6);
        assertEquals(800 - botRight.boxWidth() - HudLayout.EDGE_MARGIN, botRight.boxX());
        assertEquals(600 - botRight.boxHeight() - 20, botRight.boxY());

        // Clamping on very small viewport: box should not exceed bounds
        HudLayout.LayoutResult clamped = HudLayout.calculate(lines, config, 1.0f, 50, 50, comp -> comp.getString().length() * 6);
        assertTrue(clamped.boxX() >= 0);
        assertTrue(clamped.boxY() >= 0);
    }

    @Test
    void testMoveRowHelper() {
        assertEquals(ArdaniaConfig.HudRow.MONEY, config.rowOrder.get(0));
        assertEquals(ArdaniaConfig.HudRow.SESSION_STATS, config.rowOrder.get(1));

        config.moveRow(ArdaniaConfig.HudRow.SESSION_STATS, -1);
        assertEquals(ArdaniaConfig.HudRow.SESSION_STATS, config.rowOrder.get(0));
        assertEquals(ArdaniaConfig.HudRow.MONEY, config.rowOrder.get(1));
    }

    @Test
    void testServerVisibilityResolution() {
        config.enabled = true;
        config.visibilityCreative = ArdaniaConfig.ServerVisibility.HIDE;
        config.visibilityEventserver = ArdaniaConfig.ServerVisibility.TAB_ONLY;
        config.visibilityHauptserver = ArdaniaConfig.ServerVisibility.SHOW;

        assertFalse(HudLayout.isVisibleOnServer(config, ArdaniaSyncManager.ServerType.CREATIVE, false));
        assertFalse(HudLayout.isVisibleOnServer(config, ArdaniaSyncManager.ServerType.CREATIVE, true));

        assertFalse(HudLayout.isVisibleOnServer(config, ArdaniaSyncManager.ServerType.EVENT, false));
        assertTrue(HudLayout.isVisibleOnServer(config, ArdaniaSyncManager.ServerType.EVENT, true));

        assertTrue(HudLayout.isVisibleOnServer(config, ArdaniaSyncManager.ServerType.HAUPTSERVER, false));
        assertTrue(HudLayout.isVisibleOnServer(config, ArdaniaSyncManager.ServerType.HAUPTSERVER, true));

        config.enabled = false;
        assertFalse(HudLayout.isVisibleOnServer(config, ArdaniaSyncManager.ServerType.HAUPTSERVER, true));
    }

    @Test
    void testCustomPositionAndClamping() {
        List<HudLayout.LineSpec> lines = HudLayout.buildPreviewLines(config);

        config.useCustomPosition = true;
        config.customXRatio = 0.5f;
        config.customYRatio = 0.25f;

        HudLayout.LayoutResult customResult = HudLayout.calculate(lines, config, 1.0f, 800, 600, comp -> comp.getString().length() * 6);
        int maxX = 800 - customResult.boxWidth();
        int maxY = 600 - customResult.boxHeight();

        assertEquals(Math.round(0.5f * maxX), customResult.boxX());
        assertEquals(Math.round(0.25f * maxY), customResult.boxY());

        // Extreme values clamped
        config.customX = 9999;
        config.customY = 9999;
        config.customXRatio = -1.0f;
        config.customYRatio = -1.0f;
        HudLayout.LayoutResult clampedResult = HudLayout.calculate(lines, config, 1.0f, 800, 600, comp -> comp.getString().length() * 6);
        assertEquals(maxX, clampedResult.boxX());
        assertEquals(maxY, clampedResult.boxY());
    }
}
