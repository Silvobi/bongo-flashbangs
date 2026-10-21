package pl.bongo.flashbangs;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;

public final class BongoFlashbangs implements ModInitializer {
    // Leave time for the client to finish fading before removing its render targets.
    private static final int CLEANUP_MARGIN_TICKS = 10;
    public static final double FLASH_RADIUS = FlashExposure.MAX_RADIUS;
    public static final float WEAK_THROW_SPEED = 0.2285625f;
    public static final float NORMAL_THROW_SPEED = 1.35f;
    public static final int THROW_COOLDOWN_TICKS = 30;
    private static final Identifier COOLDOWN = Identifier.fromNamespaceAndPath("bongoflashbangs", "throw");
    private final Map<UUID, ActiveFlash> active = new HashMap<>();
    private final java.util.Set<FlashGrenade> thrown = new java.util.HashSet<>();
    private record ActiveFlash(Identifier effect, FlashStrength strength, int visualEnds,
                               int expires, Object dimension) {}

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(Commands.literal("bongoflashbangs")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("test")
                    .executes(ctx -> flash(java.util.List.of(ctx.getSource().getPlayerOrException()), FlashStrength.FULL))
                    .then(testCommand(FlashStrength.WEAK))
                    .then(testCommand(FlashStrength.MEDIUM))
                    .then(testCommand(FlashStrength.FULL))
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> flash(EntityArgument.getPlayers(ctx, "players"), FlashStrength.FULL))))
                .then(Commands.literal("clear")
                    .executes(ctx -> clear(java.util.List.of(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> clear(EntityArgument.getPlayers(ctx, "players")))))
                .then(Commands.literal("give")
                    .executes(ctx -> give(java.util.List.of(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> give(EntityArgument.getPlayers(ctx, "players")))))));
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getItemInHand(hand);
            if (!isFlashbang(stack)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level))
                return InteractionResult.PASS;
            if (player.isSpectator() || player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
            var grenade = new FlashGrenade(level, serverPlayer, stack.copyWithCount(1), this::detonate);
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), -5.0f,
                player.isShiftKeyDown() ? WEAK_THROW_SPEED : NORMAL_THROW_SPEED, 0.0f);
            if (!level.addFreshEntity(grenade)) return InteractionResult.FAIL;
            if (!grenade.attachModel()) { grenade.discard(); return InteractionResult.FAIL; }
            thrown.add(grenade);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.8f);
            player.getCooldowns().addCooldown(COOLDOWN, THROW_COOLDOWN_TICKS);
            stack.consume(1, player);
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // A fuse keeps running even if an entity enters a loaded chunk outside simulation range.
            thrown.removeIf(grenade -> { grenade.checkFuse(); return grenade.isRemoved(); });
            var iterator = active.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var player = server.getPlayerList().getPlayer(entry.getKey());
                var flash = entry.getValue();
                if (player == null || !player.isAlive() || server.getTickCount() >= flash.expires()
                    || !player.level().dimension().equals(flash.dimension())) {
                    if (player != null) {
                        if (!player.isAlive() || !player.level().dimension().equals(flash.dimension())) FlashRinging.stop(player);
                        removeEffects(player);
                    }
                    iterator.remove();
                }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> removeEffects(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            active.remove(handler.player.getUUID());
            FlashSoundMute.clear(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                FlashRinging.stop(player);
                removeEffects(player);
            }
            active.clear();
            FlashSoundMute.clearAll();
            thrown.forEach(FlashGrenade::discard);
            thrown.clear();
        });
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            testCommand(FlashStrength strength) {
        return Commands.literal(strength.commandName())
            .executes(ctx -> flash(java.util.List.of(ctx.getSource().getPlayerOrException()), strength))
            .then(Commands.argument("players", EntityArgument.players())
                .executes(ctx -> flash(EntityArgument.getPlayers(ctx, "players"), strength)));
    }

    private int flash(Collection<ServerPlayer> players, FlashStrength strength) {
        for (var player : players) {
            applyFlash(player, strength, true);
            player.sendSystemMessage(Component.literal("BongoFlashbangs: test " + strength.commandName()
                + " (resource pack required)."));
        }
        return players.size();
    }

    private void applyFlash(ServerPlayer player, FlashStrength strength, boolean force) {
        // Alternating chain IDs resets GPU memory for ordinary repeated flashes.
        var old = active.get(player.getUUID());
        int now = player.level().getServer().getTickCount();
        if (!force && old != null && old.dimension().equals(player.level().dimension())
            && now < old.visualEnds() && old.strength().ordinal() > strength.ordinal()) return;
        var next = strength.effect(old != null && old.effect().equals(strength.effect(false)));
        if (strength != FlashStrength.FULL) FlashRinging.stop(player);
        removeEffects(player);
        player.addPostEffect(next);
        player.sendPostEffects();
        int visualEnds = now + strength.durationTicks();
        active.put(player.getUUID(), new ActiveFlash(next, strength, visualEnds,
            visualEnds + CLEANUP_MARGIN_TICKS, player.level().dimension()));
        if (strength == FlashStrength.FULL) {
            FlashSoundMute.begin(player, now);
            FlashRinging.play(player);
        }
    }

    public static ItemStack flashbangItem() {
        var stack = new ItemStack(Items.SNOWBALL, 4);
        var data = new CompoundTag();
        data.putBoolean("bongoflashbangs_flashbang", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Flashbang"));
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("bongoflashbangs", "flashbang"));
        stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(1.5f, java.util.Optional.of(COOLDOWN)));
        return stack;
    }

    public static boolean isFlashbang(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return stack.is(Items.SNOWBALL) && data != null
            && data.copyTag().getBooleanOr("bongoflashbangs_flashbang", false);
    }

    private int give(Collection<ServerPlayer> players) {
        for (var player : players) {
            var stack = flashbangItem();
            if (!player.getInventory().add(stack)) player.spawnAtLocation((ServerLevel) player.level(), stack);
        }
        return players.size();
    }

    private void detonate(FlashGrenade grenade) {
        var world = (ServerLevel) grenade.level();
        var origin = grenade.flashOrigin();
        world.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 1.0f, 1.0f, 1.0f),
            origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
        world.playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIREWORK_ROCKET_BLAST,
            SoundSource.PLAYERS, 1.0f, 1.4f);
        for (var player : world.players()) {
            if (!player.isAlive() || player.isSpectator() || player.getEyePosition().distanceToSqr(origin) > FLASH_RADIUS * FLASH_RADIUS)
                continue;
            var strength = FlashExposure.strength(player.getEyePosition(), player.getLookAngle(), origin);
            if (strength.isEmpty()) continue;
            var sight = world.clip(new ClipContext(origin, player.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (sight.getType() == HitResult.Type.MISS) applyFlash(player, strength.get(), false);
        }
    }

    private int clear(Collection<ServerPlayer> players) {
        for (var player : players) {
            FlashRinging.stop(player);
            removeEffects(player);
            active.remove(player.getUUID());
        }
        return players.size();
    }

    private static void removeEffects(ServerPlayer player) {
        FlashSoundMute.clear(player);
        boolean changed = false;
        for (var strength : FlashStrength.values()) {
            changed |= player.removePostEffect(strength.effect(false));
            changed |= player.removePostEffect(strength.effect(true));
        }
        if (changed) player.sendPostEffects();
    }
}
