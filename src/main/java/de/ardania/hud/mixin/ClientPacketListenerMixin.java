package de.ardania.hud.mixin;

import de.ardania.hud.TabListSnapshot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faengt den Tablisten-Header und -Footer ab.
 *
 * <p>Minecraft bietet fuer diese Daten keinen oeffentlichen Getter. Der Mixin
 * ist bewusst nicht-destruktiv: Er injiziert nur am Ende der Vanilla-Methode
 * und veraendert weder das Paket noch den weiteren Ablauf.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void ardania$captureTabList(ClientboundTabListPacket packet, CallbackInfo callbackInfo) {
        if (packet != null) {
            TabListSnapshot.update(packet.header(), packet.footer());
        }
    }
}
