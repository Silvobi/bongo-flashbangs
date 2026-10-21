package pl.bongo.flashbangs;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** The audio asset contains its own two-second silent lead-in; no delayed server task. */
public final class FlashRinging {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("bongoflashbangs", "earring");
    private static final Holder<SoundEvent> SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(ID));

    private FlashRinging() {}

    public static void play(ServerPlayer player) {
        // Only this connection receives it. The sound follows the recipient instead of the explosion.
        player.connection.send(new ClientboundSoundEntityPacket(SOUND, SoundSource.MASTER,
            player, 1.0f, 1.0f, player.getRandom().nextLong()));
    }

    public static void stop(ServerPlayer player) {
        player.connection.send(new ClientboundStopSoundPacket(ID, null));
    }
}
