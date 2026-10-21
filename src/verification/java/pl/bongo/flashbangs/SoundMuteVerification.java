package pl.bongo.flashbangs;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;

/** Tests the production Connection mixin on actual outgoing Netty packets, not just its predicate. */
public final class SoundMuteVerification {
    public static void run(ServerLevel world) throws Exception {
        var server = world.getServer();
        var player = new ServerPlayer(server, world, new GameProfile(UUID.randomUUID(), "SoundProbe"), ClientInformation.createDefault());
        var other = new ServerPlayer(server, world, new GameProfile(UUID.randomUUID(), "SoundOther"), ClientInformation.createDefault());
        var connection = connection(world, player);
        var peer = connection(world, other);
        var channel = new EmbeddedChannel(connection);
        var peerChannel = new EmbeddedChannel(peer);
        try {
            var source = server.createCommandSourceStack().withEntity(player);
            var commands = server.getCommands().getDispatcher();
            var sound = new ClientboundSoundPacket(Holder.direct(SoundEvents.STONE_HIT), SoundSource.BLOCKS, 0, 100, 0, 1, 1, 42);
            var entitySound = new ClientboundSoundEntityPacket(Holder.direct(SoundEvents.STONE_HIT), SoundSource.PLAYERS, player, 1, 1, 42);
            var chat = new ClientboundSystemChatPacket(Component.literal("keep this packet"), false);
            connection.send(sound);
            check(drain(channel).equals(List.of(sound)), "unflashed player hears sound");
            int now = server.getTickCount();
            commands.execute("bongoflashbangs test full", source);
            var startPackets = drain(channel);
            check(startPackets.stream().filter(ClientboundStopSoundPacket.class::isInstance).count() == 1
                && startPackets.stream().anyMatch(p -> p instanceof ClientboundStopSoundPacket stop
                && stop.getName() == null && stop.getSource() == null), "full flash sends exactly one immediate stop-all packet");
            var ring = startPackets.stream().filter(p -> p instanceof ClientboundSoundEntityPacket s
                && s.getSound().value().location().equals(FlashRinging.ID)).map(ClientboundSoundEntityPacket.class::cast).findFirst().orElseThrow();
            check(startPackets.stream().filter(p -> p instanceof ClientboundSoundEntityPacket s
                && s.getSound().value().location().equals(FlashRinging.ID)).count() == 1, "exactly one ring starts with full flash");
            check(ring.getId() == player.getId() && ring.getSource() == SoundSource.MASTER
                && ring.getPitch() == 1 && ring.getVolume() == 1, "ring follows recipient at original pitch and volume");
            int stopIndex = -1;
            for (int i = 0; i < startPackets.size(); i++) if (startPackets.get(i) instanceof ClientboundStopSoundPacket s
                && s.getName() == null) stopIndex = i;
            check(stopIndex >= 0 && stopIndex < startPackets.indexOf(ring), "stop-all precedes ring start");
            check(drain(peerChannel).isEmpty(), "ring is not broadcast to another player");
            var ringAtPosition = new ClientboundSoundPacket(ring.getSound(), SoundSource.MASTER, 0, 100, 0, 1, 1, 42);
            connection.send(ring);
            connection.send(ringAtPosition);
            check(drain(channel).equals(List.of(ring, ringAtPosition)), "ring exception bypasses mute for both sound packet types");
            var unrelated = new ClientboundSoundEntityPacket(Holder.direct(SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath("bongoflashbangs", "other_sound"))), SoundSource.MASTER, player, 1, 1, 42);
            connection.send(unrelated);
            check(drain(channel).isEmpty(), "exception applies only to ring identifier");
            connection.send(sound);
            player.connection.send(entitySound);
            check(drain(channel).isEmpty(), "position/entity sounds blocked through actual Connection and player listener");
            AtomicBoolean suppressedCallback = new AtomicBoolean();
            connection.send(sound, future -> suppressedCallback.set(future.isSuccess()), true);
            check(drain(channel).isEmpty() && suppressedCallback.get(), "suppressed sound completes callback without sending a packet");
            peer.send(sound);
            check(drain(peerChannel).equals(List.of(sound)), "other player still receives same sound");
            AtomicBoolean callback = new AtomicBoolean();
            connection.send(chat, future -> callback.set(future.isSuccess()), true);
            check(drain(channel).equals(List.of(chat)) && callback.get(), "unrelated packet and callback retained");
            var stop = new ClientboundStopSoundPacket(null, null);
            connection.send(new ClientboundBundlePacket(List.of(sound, chat, entitySound, ring, stop)));
            var bundlePackets = drain(channel);
            check(bundlePackets.size() == 1 && bundlePackets.getFirst() instanceof ClientboundBundlePacket, "mixed bundle retained");
            var retained = new ArrayList<Packet<? super ClientGamePacketListener>>();
            ((ClientboundBundlePacket) bundlePackets.getFirst()).subPackets().forEach(retained::add);
            check(retained.equals(List.of(chat, ring, stop)), "bundle retains ring and non-sound packets in order");
            connection.send(new ClientboundBundlePacket(List.of(sound, entitySound)));
            check(drain(channel).isEmpty(), "sound-only bundle dropped");
            check(FlashSoundMute.isMuted(player, now + 39), "still muted before fade begins");
            check(drain(channel).isEmpty(), "checking active mute sends no repeated stop-sound packets");
            check(!FlashSoundMute.isMuted(player, now + 40), "unmuted exactly when white fade begins");
            connection.send(sound);
            check(drain(channel).equals(List.of(sound)), "new sound resumes; dropped sounds are not replayed");

            // An older hold is replaced by the new full flash's complete two-second hold.
            FlashSoundMute.begin(player, now - 20);
            commands.execute("bongoflashbangs test full", source);
            check(FlashSoundMute.isMuted(player, now + 25), "full reflash resets mute deadline");
            var retrigger = drain(channel);
            check(retrigger.stream().filter(p -> p instanceof ClientboundSoundEntityPacket s
                && s.getSound().value().location().equals(FlashRinging.ID)).count() == 1, "full reflash restarts ring once");
            commands.execute("bongoflashbangs clear", source);
            check(!FlashSoundMute.isMuted(player, now), "clear restores sounds immediately");
            check(drain(channel).stream().anyMatch(p -> p instanceof ClientboundStopSoundPacket s
                && FlashRinging.ID.equals(s.getName())), "clear stops ring explicitly");
            for (String strength : new String[]{"weak", "medium"}) {
                commands.execute("bongoflashbangs test " + strength, source);
                check(!FlashSoundMute.isMuted(player, now), strength + " doesn't mute audio");
                check(drain(channel).stream().noneMatch(p -> p instanceof ClientboundSoundEntityPacket s
                    && s.getSound().value().location().equals(FlashRinging.ID)), strength + " does not start ring");
                connection.send(sound);
                check(drain(channel).equals(List.of(sound)), strength + " sound forwarded");
            }
            commands.execute("bongoflashbangs test full", source);
            commands.execute("bongoflashbangs test weak", source);
            check(!FlashSoundMute.isMuted(player, now), "forced weak test cancels old full mute");
            commands.execute("bongoflashbangs test full", source);
            player.setHealth(0);
            check(!FlashSoundMute.isMuted(player, now), "death ends mute");
            player.setHealth(20);
            commands.execute("bongoflashbangs clear", source);
            System.out.println("BONGO_SOUND_PASS: real Connection mixin / Netty output, one immediate stop with no tick repeats, recipient-only ring after stop-all with exact-ID mute exception, mixed bundles, other sounds blocked, callbacks, tick-39/40 boundary, full ring retrigger, clear stops ring, weak/medium and death.");
        } finally {
            FlashSoundMute.clear(player);
            FlashSoundMute.clear(other);
            channel.finishAndReleaseAll();
            peerChannel.finishAndReleaseAll();
        }
    }

    private static Connection connection(ServerLevel world, ServerPlayer player) {
        var connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public PacketListener getPacketListener() { return player.connection; }
        };
        new ServerGamePacketListenerImpl(world.getServer(), connection, player,
            CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return connection;
    }

    private static List<Packet<?>> drain(EmbeddedChannel channel) {
        channel.runPendingTasks();
        var result = new ArrayList<Packet<?>>();
        Object packet;
        while ((packet = channel.readOutbound()) != null) result.add((Packet<?>) packet);
        channel.checkException();
        return result;
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
