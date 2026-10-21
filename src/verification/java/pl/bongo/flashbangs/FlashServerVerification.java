package pl.bongo.flashbangs;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual server player/post-effect methods; only outgoing network transport is replaced. */
public final class FlashServerVerification {
    public static void run(ServerLevel world) throws Exception {
        var server = world.getServer();
        var mod = FabricLoader.getInstance().getEntrypoints("main", ModInitializer.class).stream()
            .filter(BongoFlashbangs.class::isInstance).map(BongoFlashbangs.class::cast).findFirst().orElseThrow();
        Method detonate = BongoFlashbangs.class.getDeclaredMethod("detonate", FlashGrenade.class);
        detonate.setAccessible(true);
        var profile = new GameProfile(UUID.randomUUID(), "FlashProbe");
        var player = new ServerPlayer(server, world, profile, ClientInformation.createDefault());
        List<Packet<?>> sent = new ArrayList<>();
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) { sent.add(packet); }
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) { sent.add(packet); }
        };
        var source = server.createCommandSourceStack().withEntity(player);
        var dispatcher = server.getCommands().getDispatcher();
        var keep = Identifier.fromNamespaceAndPath("verification", "keep");
        player.addPostEffect(keep);
        var grenade = new FlashGrenade(world, new Vec3(40.5, 105, 4.5), BongoFlashbangs.flashbangItem(), g -> {});
        var origin = grenade.flashOrigin();
        BlockPos wall = BlockPos.containing(origin.add(-5, 0, 0));
        world.players().add(player); // A synchronous probe; never ticked or written to player data.
        try {
            for (var strength : FlashStrength.values()) {
                check(dispatcher.execute("bongoflashbangs test " + strength.commandName(), source) == 1, "self test command");
                check(player.getPostEffects().contains(strength.effect(false)), "profile selected by command");
                check(dispatcher.execute("bongoflashbangs test " + strength.commandName() + " @s", source) == 1,
                    "profile command with selector");
                check(player.getPostEffects().contains(strength.effect(true)), "retrigger alternates target lifecycle");
                check(player.getPostEffects().size() == 2 && player.getPostEffects().contains(keep), "one own chain; other effect preserved");
                dispatcher.execute("bongoflashbangs clear", source);
                check(player.getPostEffects().equals(List.of(keep)), "clear removes all own profiles only");
            }
            check(dispatcher.execute("bongoflashbangs test", source) == 1, "legacy test command");
            check(player.getPostEffects().contains(FlashStrength.FULL.effect(false)), "legacy command chooses full");
            dispatcher.execute("bongoflashbangs clear", source);

            double[] distances = {10, 17, 22, 25};
            FlashStrength[] expected = {FlashStrength.FULL, FlashStrength.MEDIUM, FlashStrength.WEAK, null};
            for (int i = 0; i < distances.length; i++) {
                position(player, origin, distances[i], -90);
                detonate.invoke(mod, grenade);
                checkProfile(player, keep, expected[i], "detonation at " + distances[i] + " m");
                dispatcher.execute("bongoflashbangs clear", source);
            }
            double[] sideDistances = {1.9, 5.5, 16, 18};
            for (int i = 0; i < sideDistances.length; i++) {
                position(player, origin, sideDistances[i], -45);
                detonate.invoke(mod, grenade);
                checkProfile(player, keep, expected[i], "side detonation at " + sideDistances[i] + " m");
                dispatcher.execute("bongoflashbangs clear", source);
            }
            position(player, origin, 10, -90);
            world.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            detonate.invoke(mod, grenade);
            checkProfile(player, keep, null, "wall blocks full exposure");
            world.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);

            position(player, origin, 10, 90);
            detonate.invoke(mod, grenade);
            checkProfile(player, keep, null, "turning away avoids distant flash");
            position(player, origin, 0.75, 90);
            detonate.invoke(mod, grenade);
            checkProfile(player, keep, FlashStrength.FULL, "close rear flash");
            position(player, origin, 22, -90);
            detonate.invoke(mod, grenade);
            checkProfile(player, keep, FlashStrength.FULL, "weak detonation does not cut active full flash");
            dispatcher.execute("bongoflashbangs test weak", source);
            checkProfile(player, keep, FlashStrength.WEAK, "explicit test can override current severity");
            check(!sent.isEmpty(), "real player network methods emitted packets");
            System.out.println("BONGO_FLASH_SERVER_PASS: all profile commands, selectors, alternation, preservation of other effects, detonation severity, wall occlusion, rear exposure and weaker reflash handling.");
        } finally {
            world.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            dispatcher.execute("bongoflashbangs clear", source);
            world.players().remove(player);
            grenade.discard();
        }
    }

    private static void position(ServerPlayer player, Vec3 origin, double distance, float yaw) {
        player.setPos(origin.x - distance, origin.y - player.getEyeHeight(), origin.z);
        player.setYRot(yaw);
        player.setXRot(0);
    }

    private static void checkProfile(ServerPlayer player, Identifier keep, FlashStrength expected, String message) {
        var effects = player.getPostEffects();
        check(effects.contains(keep) && effects.size() == (expected == null ? 1 : 2)
            && (expected == null || effects.contains(expected.effect(false)) || effects.contains(expected.effect(true))),
            message + ": " + effects);
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
