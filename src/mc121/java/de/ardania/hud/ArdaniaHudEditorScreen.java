package de.ardania.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Interaktiver In-Game HUD-Editor zum freien Verschieben des Overlays per Maus (Drag & Drop)
 * und Pfeiltasten fuer Minecraft 1.21.x.
 *
 * <p>Die Eingabe wird bewusst direkt ueber GLFW abgefragt. Ab Minecraft 1.21.9
 * werden bei geoeffnetem Screen weder {@code MouseHandler.isLeftPressed()} noch
 * die alten {@code mouseClicked(double,int,int)}-Signaturen aktualisiert bzw.
 * aufgerufen. GLFW funktioniert dagegen in allen 1.21-Versionen identisch.</p>
 *
 * <p>Unterstuetzt magnetisches Einrasten (Snap-to-Edge & Center), Pfeiltasten-Feinjustierung
 * sowie eine automatisch ausweichende Toolbar (Auto-Dodge).</p>
 */
public final class ArdaniaHudEditorScreen extends Screen {

    private final Screen parent;
    private final ArdaniaConfig config;
    private List<HudLayout.LineSpec> previewLines;

    private boolean draggingHud = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean wasLeftDown = false;

    private int arrowDirX = 0;
    private int arrowDirY = 0;
    private long nextArrowRepeatTime = 0L;
    private boolean configDirty = false;

    private Button resetBtn;
    private Button scaleBtn;
    private Button doneBtn;

    private int lastHudBoxX = 0;
    private int lastHudBoxY = 0;
    private int lastHudBoxWidth = 0;
    private int lastHudBoxHeight = 0;
    private int snapGuideX = -1;
    private int snapGuideY = -1;

    /**
     * Erstellt einen neuen HUD-Editor-Bildschirm.
     *
     * @param parent Uebergeordneter Bildschirm, zu dem beim Schliessen zurueckgekehrt wird
     */
    public ArdaniaHudEditorScreen(Screen parent) {
        super(ArdaniaI18n.translate("config.ardania_hud.editor.title"));
        this.parent = parent;
        this.config = ArdaniaConfig.getInstance();
    }

    @Override
    protected void init() {
        this.previewLines = HudLayout.buildPreviewLines(this.config);

        int btnWidth = 104;
        int btnHeight = 20;
        int gap = 6;
        int totalW = (btnWidth * 3) + (gap * 2);
        int startX = (this.width - totalW) / 2;
        int startY = this.height - 30;

        this.resetBtn = Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.editor.reset"),
                btn -> {
                    this.config.resetLayoutToPreset();
                    ArdaniaConfig.save();
                    updateButtonPositions();
                }
        ).bounds(startX, startY, btnWidth, btnHeight).build();

        this.scaleBtn = Button.builder(
                getScaleText(),
                btn -> {
                    this.config.hudScale = switch (Math.round(this.config.hudScale * 100.0f)) {
                        case 75 -> 0.9f;
                        case 90 -> 1.0f;
                        case 100 -> 1.1f;
                        case 110 -> 1.25f;
                        case 125 -> 0.75f;
                        default -> 1.0f;
                    };
                    btn.setMessage(getScaleText());
                    ArdaniaConfig.save();
                    updateButtonPositions();
                }
        ).bounds(startX + btnWidth + gap, startY, btnWidth, btnHeight).build();

        this.doneBtn = Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.done"),
                btn -> onClose()
        ).bounds(startX + (btnWidth + gap) * 2, startY, btnWidth, btnHeight).build();

        addRenderableWidget(this.resetBtn);
        addRenderableWidget(this.scaleBtn);
        addRenderableWidget(this.doneBtn);

        updateButtonPositions();
    }

    private Component getScaleText() {
        return ArdaniaI18n.translate("config.ardania_hud.scale")
                .append(": ")
                .append(Math.round(this.config.hudScale * 100.0f) + "%");
    }

    private void updateButtonPositions() {
        if (this.resetBtn == null || this.scaleBtn == null || this.doneBtn == null) return;
        int btnWidth = 104;
        int gap = 6;
        int totalW = (btnWidth * 3) + (gap * 2);
        int startX = (this.width - totalW) / 2;

        boolean hudInBottom = (this.lastHudBoxY + this.lastHudBoxHeight) > (this.height - 56);
        int btnY = hudInBottom ? 12 : (this.height - 30);

        this.resetBtn.setX(startX);
        this.resetBtn.setY(btnY);
        this.scaleBtn.setX(startX + btnWidth + gap);
        this.scaleBtn.setY(btnY);
        this.doneBtn.setX(startX + (btnWidth + gap) * 2);
        this.doneBtn.setY(btnY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        updateDragState(mouseX, mouseY);
        updateArrowNudges();

        // WICHTIG: In 1.21.x zeichnet Screen.render() den (geblurrten) Menue-
        // Hintergrund und die Buttons. Wird es zuletzt aufgerufen, liegt der
        // Blur ueber der HUD-Vorschau. Deshalb zuerst den Screen rendern und
        // die Vorschau anschliessend darueberlegen.
        super.render(graphics, mouseX, mouseY, delta);

        // Magnetische Cyan-Hilfslinien
        if (this.snapGuideX >= 0) {
            graphics.fill(this.snapGuideX, 0, this.snapGuideX + 1, this.height, 0xAA00E5FF);
        }
        if (this.snapGuideY >= 0) {
            graphics.fill(0, this.snapGuideY, this.width, this.snapGuideY + 1, 0xAA00E5FF);
        }

        // HUD zeichnen
        int alpha = (int) (Math.clamp(this.config.backgroundOpacity, 0.0f, 1.0f) * 255.0f);
        int bgColor = (alpha << 24) & 0xFF000000;
        int headerAlpha = Math.min(255, alpha + 0x15);
        int headerBgColor = (headerAlpha << 24) & 0xFF000000;

        float requestedScale = Math.clamp(this.config.hudScale, 0.7f, 1.5f);
        boolean scaled = ArdaniaHudRenderer.pushAndScale(graphics, requestedScale);
        float effectiveScale = scaled ? requestedScale : 1.0f;

        int scaledWidth = Math.max(1, Math.round(this.width / effectiveScale));
        int scaledHeight = Math.max(1, Math.round(this.height / effectiveScale));

        HudLayout.LayoutResult layout = HudLayout.calculate(
                this.previewLines,
                this.config,
                1.0f,
                scaledWidth,
                scaledHeight,
                this.font::width
        );

        this.lastHudBoxX = Math.round(layout.boxX() * effectiveScale);
        this.lastHudBoxY = Math.round(layout.boxY() * effectiveScale);
        this.lastHudBoxWidth = Math.round(layout.boxWidth() * effectiveScale);
        this.lastHudBoxHeight = Math.round(layout.boxHeight() * effectiveScale);
        updateButtonPositions();

        try {
            graphics.fill(layout.boxX(), layout.boxY(), layout.boxX() + layout.boxWidth(), layout.boxY() + layout.boxHeight(), bgColor);
            graphics.fill(layout.boxX(), layout.boxY(), layout.boxX() + layout.boxWidth(), layout.boxY() + layout.headerHeight(), headerBgColor);

            for (HudLayout.RenderableBar bar : layout.bars()) {
                graphics.fill(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height(), bar.trackColor());
                int filledWidth = (int) Math.round(bar.width() * Math.clamp(bar.fillRatio(), 0.0f, 1.0f));
                if (filledWidth > 0) {
                    graphics.fill(bar.x(), bar.y(), bar.x() + filledWidth, bar.y() + bar.height(), bar.fillColor());
                }
            }

            for (HudLayout.RenderableLine line : layout.lines()) {
                if (line.centered()) {
                    ArdaniaHudRenderer.drawText(graphics, this.font, line.left(), line.leftX(), line.y(), 0xFFFFFFFF, true);
                } else {
                    ArdaniaHudRenderer.drawText(graphics, this.font, line.left(), line.leftX(), line.y(), 0xFFFFFFFF, true);
                    if (line.right() != null) {
                        ArdaniaHudRenderer.drawText(graphics, this.font, line.right(), line.rightX(), line.y(), 0xFFFFFFFF, true);
                    }
                }
            }
        } finally {
            if (scaled) {
                ArdaniaHudRenderer.popPose(graphics);
            }
        }

        // Umrandung um HUD
        int outlineColor = this.draggingHud ? 0xFF00E5FF : 0x88AAAAAA;
        drawHollowRect(graphics, this.lastHudBoxX - 1, this.lastHudBoxY - 1, this.lastHudBoxWidth + 2, this.lastHudBoxHeight + 2, outlineColor);

        // Anleitungstext
        boolean buttonsAtTop = (this.resetBtn != null && this.resetBtn.getY() <= 20);
        int infoY = buttonsAtTop ? (this.height - 28) : 12;
        Component infoMsg = ArdaniaI18n.translate("config.ardania_hud.editor.info");
        int infoW = this.font.width(infoMsg);
        ArdaniaHudRenderer.drawText(graphics, this.font, infoMsg, (this.width - infoW) / 2, infoY, 0xFFE0E0E0, true);
    }

    private static void drawHollowRect(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void updateDragState(int mouseX, int mouseY) {
        boolean leftDown = isLeftMouseDown();
        if (leftDown && !this.wasLeftDown) {
            if (!isMouseOverButtons(mouseX, mouseY) && isMouseOverHud(mouseX, mouseY)) {
                startDragging(mouseX, mouseY);
            }
        } else if (leftDown && this.draggingHud) {
            handleDrag(mouseX, mouseY, isShiftDown());
        } else if (!leftDown && this.wasLeftDown) {
            if (this.draggingHud) {
                stopDragging();
            }
        }
        this.wasLeftDown = leftDown;
    }

    /**
     * Fragt die Pfeiltasten direkt ueber GLFW ab. Das funktioniert auch auf
     * 1.21.9+, wo die alten {@code keyPressed(int,int,int)}-Events nicht mehr
     * ausgeliefert werden.
     */
    private void updateArrowNudges() {
        int dx = (isKeyDown(GLFW.GLFW_KEY_RIGHT) ? 1 : 0) - (isKeyDown(GLFW.GLFW_KEY_LEFT) ? 1 : 0);
        int dy = (isKeyDown(GLFW.GLFW_KEY_DOWN) ? 1 : 0) - (isKeyDown(GLFW.GLFW_KEY_UP) ? 1 : 0);

        if (dx == 0 && dy == 0) {
            if (this.configDirty) {
                ArdaniaConfig.save();
                this.configDirty = false;
            }
            this.arrowDirX = 0;
            this.arrowDirY = 0;
            this.nextArrowRepeatTime = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        boolean directionChanged = dx != this.arrowDirX || dy != this.arrowDirY;
        if (directionChanged || now >= this.nextArrowRepeatTime) {
            int step = isShiftDown() ? 5 : 1;
            nudge(dx * step, dy * step);
            this.arrowDirX = dx;
            this.arrowDirY = dy;
            this.nextArrowRepeatTime = now + (directionChanged ? 300L : 60L);
        }
    }

    private static boolean isLeftMouseDown() {
        long window = currentGlfwWindow();
        return window != 0L
                && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isKeyDown(int key) {
        long window = currentGlfwWindow();
        return window != 0L && GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static long currentGlfwWindow() {
        try {
            return GLFW.glfwGetCurrentContext();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private void startDragging(int mouseX, int mouseY) {
        if (isMouseOverButtons(mouseX, mouseY)) {
            return;
        }
        if (isMouseOverHud(mouseX, mouseY)) {
            this.draggingHud = true;
            this.dragOffsetX = mouseX - this.lastHudBoxX;
            this.dragOffsetY = mouseY - this.lastHudBoxY;
        }
    }

    private void handleDrag(int mouseX, int mouseY, boolean shiftDown) {
        if (!this.draggingHud) return;

        float scale = Math.clamp(this.config.hudScale, 0.7f, 1.5f);
        int hudW = this.lastHudBoxWidth;
        int hudH = this.lastHudBoxHeight;

        int rawX = mouseX - this.dragOffsetX;
        int rawY = mouseY - this.dragOffsetY;

        this.snapGuideX = -1;
        this.snapGuideY = -1;

        int finalX = rawX;
        int finalY = rawY;

        if (!shiftDown) {
            int snapMargin = 7;
            if (Math.abs(rawX - HudLayout.EDGE_MARGIN) <= snapMargin) {
                finalX = HudLayout.EDGE_MARGIN;
                this.snapGuideX = HudLayout.EDGE_MARGIN;
            }
            int rightEdge = this.width - hudW - HudLayout.EDGE_MARGIN;
            if (Math.abs(rawX - rightEdge) <= snapMargin) {
                finalX = rightEdge;
                this.snapGuideX = this.width - HudLayout.EDGE_MARGIN;
            }
            int centerX = (this.width - hudW) / 2;
            if (Math.abs(rawX - centerX) <= snapMargin) {
                finalX = centerX;
                this.snapGuideX = this.width / 2;
            }

            if (Math.abs(rawY - HudLayout.EDGE_MARGIN) <= snapMargin) {
                finalY = HudLayout.EDGE_MARGIN;
                this.snapGuideY = HudLayout.EDGE_MARGIN;
            }
            int bottomEdge = this.height - hudH - HudLayout.EDGE_MARGIN;
            if (Math.abs(rawY - bottomEdge) <= snapMargin) {
                finalY = bottomEdge;
                this.snapGuideY = this.height - HudLayout.EDGE_MARGIN;
            }
            int centerY = (this.height - hudH) / 2;
            if (Math.abs(rawY - centerY) <= snapMargin) {
                finalY = centerY;
                this.snapGuideY = this.height / 2;
            }
        }

        int maxScreenX = Math.max(0, this.width - hudW);
        int maxScreenY = Math.max(0, this.height - hudH);
        finalX = Math.clamp(finalX, 0, maxScreenX);
        finalY = Math.clamp(finalY, 0, maxScreenY);

        int scaledX = Math.round(finalX / scale);
        int scaledY = Math.round(finalY / scale);
        int scaledMaxX = Math.max(1, Math.round(maxScreenX / scale));
        int scaledMaxY = Math.max(1, Math.round(maxScreenY / scale));

        this.config.useCustomPosition = true;
        this.config.customX = scaledX;
        this.config.customY = scaledY;
        this.config.customXRatio = (float) scaledX / (float) scaledMaxX;
        this.config.customYRatio = (float) scaledY / (float) scaledMaxY;
    }

    private void stopDragging() {
        if (this.draggingHud) {
            this.draggingHud = false;
            this.snapGuideX = -1;
            this.snapGuideY = -1;
            ArdaniaConfig.save();
        }
    }

    private void nudge(int dx, int dy) {
        float scale = Math.clamp(this.config.hudScale, 0.7f, 1.5f);
        int hudW = this.lastHudBoxWidth;
        int hudH = this.lastHudBoxHeight;
        int maxScreenX = Math.max(0, this.width - hudW);
        int maxScreenY = Math.max(0, this.height - hudH);

        int currentX = this.lastHudBoxX;
        int currentY = this.lastHudBoxY;

        int newX = Math.clamp(currentX + dx, 0, maxScreenX);
        int newY = Math.clamp(currentY + dy, 0, maxScreenY);

        int scaledX = Math.round(newX / scale);
        int scaledY = Math.round(newY / scale);
        int scaledMaxX = Math.max(1, Math.round(maxScreenX / scale));
        int scaledMaxY = Math.max(1, Math.round(maxScreenY / scale));

        this.config.useCustomPosition = true;
        this.config.customX = scaledX;
        this.config.customY = scaledY;
        this.config.customXRatio = (float) scaledX / (float) scaledMaxX;
        this.config.customYRatio = (float) scaledY / (float) scaledMaxY;
        this.configDirty = true;
    }

    private boolean isMouseOverHud(double mouseX, double mouseY) {
        int margin = 4;
        return mouseX >= (this.lastHudBoxX - margin) &&
                mouseX <= (this.lastHudBoxX + this.lastHudBoxWidth + margin) &&
                mouseY >= (this.lastHudBoxY - margin) &&
                mouseY <= (this.lastHudBoxY + this.lastHudBoxHeight + margin);
    }

    private boolean isMouseOverButtons(double mouseX, double mouseY) {
        if (this.resetBtn != null && this.resetBtn.isMouseOver(mouseX, mouseY)) return true;
        if (this.scaleBtn != null && this.scaleBtn.isMouseOver(mouseX, mouseY)) return true;
        if (this.doneBtn != null && this.doneBtn.isMouseOver(mouseX, mouseY)) return true;
        return false;
    }

    @Override
    public void onClose() {
        stopDragging();
        if (this.configDirty) {
            ArdaniaConfig.save();
            this.configDirty = false;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
