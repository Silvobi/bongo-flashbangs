package pl.bongo.flashbangs;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;

/** Per-recipient sound suppression during the full flash's two-second white hold. */
public final class FlashSoundMute {
    public static final int WHITE_HOLD_TICKS = 40;
    private static final Map<UUID, Muted> muted = new ConcurrentHashMap<>();
    private record Muted(ServerPlayer player, int until, Object dimension) {}

    private FlashSoundMute() {}

    public static void begin(ServerPlayer player, int now) {
        muted.put(player.getUUID(), new Muted(player, now + WHITE_HOLD_TICKS, player.level().dimension()));
        stopSounds(player);
    }

    public static void clear(ServerPlayer player) { muted.remove(player.getUUID()); }
    public static void clearAll() { muted.clear(); }

    public static boolean isMuted(ServerPlayer player, int now) {
        var state = muted.get(player.getUUID());
        if (state == null) return false;
        if (state.player() != player || !player.isAlive() || now >= state.until()
            || !state.dimension().equals(player.level().dimension())) {
            muted.remove(player.getUUID(), state);
            return false;
        }
        return true;
    }

    private static void stopSounds(ServerPlayer player) {
        // Null name and source mean all currently playing sounds, including client-local audio.
        player.connection.send(new ClientboundStopSoundPacket(null, null));
    }

    /** Null drops a sound; bundles retain every other packet in the original order. */
    public static Packet<?> filter(ServerPlayer player, Packet<?> packet) {
        if (!isMuted(player, player.level().getServer().getTickCount())) return packet;
        return filterMuted(packet);
    }

    private static Packet<?> filterMuted(Packet<?> packet) {
        if (packet instanceof ClientboundSoundPacket sound)
            return sound.getSound().value().location().equals(FlashRinging.ID) ? packet : null;
        if (packet instanceof ClientboundSoundEntityPacket sound)
            return sound.getSound().value().location().equals(FlashRinging.ID) ? packet : null;
        if (!(packet instanceof ClientboundBundlePacket bundle)) return packet;
        var retained = new ArrayList<Packet<? super ClientGamePacketListener>>();
        boolean changed = false;
        for (var child : bundle.subPackets()) {
            Packet<?> filtered = filterMuted(child);
            changed |= filtered != child;
            if (filtered != null) {
                @SuppressWarnings("unchecked")
                var clientPacket = (Packet<? super ClientGamePacketListener>) filtered;
                retained.add(clientPacket);
            }
        }
        if (!changed) return packet;
        return retained.isEmpty() ? null : new ClientboundBundlePacket(retained);
    }
}
