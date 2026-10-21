package pl.bongo.flashbangs.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.flashbangs.FlashSoundMute;

@Mixin(Connection.class)
public abstract class ConnectionSoundMixin {
    @Shadow private Channel channel;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"), cancellable = true)
    private void bongoflashbangs$filterSound(Packet<?> packet, ChannelFutureListener listener,
                                            boolean flush, CallbackInfo ci) {
        var connection = (Connection) (Object) this;
        if (!(connection.getPacketListener() instanceof ServerGamePacketListenerImpl handler)) return;
        var filtered = FlashSoundMute.filter(handler.player, packet);
        if (filtered == packet) return;
        ci.cancel();
        if (filtered != null) connection.send(filtered, listener, flush);
        else if (listener != null && channel != null) channel.newSucceededFuture().addListener(listener);
    }
}
