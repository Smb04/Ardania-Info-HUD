package de.ardania.hud;

import net.minecraft.network.chat.Component;

/**
 * Zwischenspeicher fuer den zuletzt empfangenen Tablisten-Header und -Footer.
 *
 * <p>Der Tablisten-Header von Ardania.de nennt den aktuellen Unterserver
 * (z. B. {@code Hauptserver 6} oder {@code Minenwelten 4}). Da Minecraft fuer
 * diese Daten keinen oeffentlichen Getter anbietet, werden sie ueber einen
 * Mixin in {@code ClientPacketListener#handleTabListCustomisation} mitgeschnitten.</p>
 */
public final class TabListSnapshot {

    private static volatile String header = "";
    private static volatile String footer = "";
    private static volatile long updatedAt = 0L;

    private TabListSnapshot() {
    }

    /**
     * Aktualisiert den Snapshot. Der Zeitstempel aendert sich nur, wenn sich der
     * sichtbare Text tatsaechlich unterscheidet, damit die Erkennung nicht bei
     * jedem identischen Header-Paket neu rechnet.
     */
    public static void update(Component headerComponent, Component footerComponent) {
        String newHeader = headerComponent != null ? headerComponent.getString() : "";
        String newFooter = footerComponent != null ? footerComponent.getString() : "";
        if (!newHeader.equals(header) || !newFooter.equals(footer)) {
            header = newHeader;
            footer = newFooter;
            updatedAt = System.currentTimeMillis();
        }
    }

    public static String getHeader() {
        return header;
    }

    public static String getFooter() {
        return footer;
    }

    public static long getUpdatedAt() {
        return updatedAt;
    }

    public static void reset() {
        header = "";
        footer = "";
        updatedAt = 0L;
    }
}
