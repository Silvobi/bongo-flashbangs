package pl.bongo.flashbangs;

import com.mojang.authlib.GameProfile;
import com.mojang.math.Transformation;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class PhysicsAndSafetyVerification {
    public static void run(ServerLevel world) throws Exception {
        var player = new ServerPlayer(world.getServer(), world, new GameProfile(UUID.randomUUID(), "PhysicsProbe"), ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(world.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
            CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {}
        };
        player.setPos(40.5, 115, 4.5);
        player.setPose(Pose.CROUCHING);
        player.refreshDimensions();
        player.setShiftKeyDown(true);
        player.setOnGround(true);
        player.setKnownMovement(Vec3.ZERO);
        player.setXRot(-85);
        var vertical = new FlashGrenade(world, player, BongoFlashbangs.flashbangItem(), g -> {});
        vertical.shootFromRotation(player, -85, 0, -5, BongoFlashbangs.WEAK_THROW_SPEED, 0);
        check(vertical.getAttributeValue(Attributes.AIR_DRAG_MODIFIER) == 0, "zero air drag");
        double apex = vertical.flashOrigin().y;
        int peakTick = 0;
        for (int tick = 1; tick <= 16; tick++) {
            vertical.tick();
            check(!vertical.isRemoved(), "height probe stays in loaded air");
            if (vertical.flashOrigin().y > apex) { apex = vertical.flashOrigin().y; peakTick = tick; }
        }
        double height = apex - player.getY();
        check(Math.abs(height - 2.25) < 0.005, "native physics weak-throw height: " + height);
        vertical.discard();

        var slow = new FlashGrenade(world, new Vec3(40.5, 120, 4.5), BongoFlashbangs.flashbangItem(), g -> {});
        var fast = new FlashGrenade(world, new Vec3(40.5, 125, 4.5), BongoFlashbangs.flashbangItem(), g -> {});
        slow.setDeltaMovement(0.2, 0.1, 0);
        fast.setDeltaMovement(1, 0.2, 0);
        check(slow.attachModel() && fast.attachModel(), "rotating models attached");
        try {
            var initial = fast.modelRotation();
            var aligned = initial.transform(new Vector3f(0, 1, 0));
            check(aligned.dot(new Vector3f(1, 0.2f, 0).normalize()) > 0.999, "model initially points along velocity");
            slow.tick(); fast.tick();
            check(fast.spinAngle() > slow.spinAngle() && slow.spinAngle() > 0, "faster flight spins faster");
            check(Math.abs(initial.dot(fast.modelRotation())) < 0.99999, "model transform actually rotates");
            double horizontal = fast.getDeltaMovement().x;
            for (int i = 0; i < 12; i++) {
                fast.tick();
                check(Math.abs(fast.getDeltaMovement().x - horizontal) < 0.000001, "horizontal velocity preserved in air");
                verifyRotatedBounds(fast);
            }
            fast.setDeltaMovement(0, -0.5, 0.6);
            fast.tick();
            verifyRotatedBounds(fast);
        } finally { slow.discard(); fast.discard(); }

        var flash = BongoFlashbangs.flashbangItem();
        var snow = new ItemStack(Items.SNOWBALL);
        var ghast = new HappyGhast(EntityTypes.HAPPY_GHAST, world);
        var tempt = HappyGhast.class.getDeclaredMethod("lambda$registerGoals$0", ItemStack.class);
        tempt.setAccessible(true);
        for (boolean baby : new boolean[]{false, true}) {
            ghast.setAge(baby ? -24000 : 0);
            check(ghast.isFood(snow) && !ghast.isFood(flash), "normal ghast food retained, flash excluded");
            check((boolean) tempt.invoke(ghast, snow) && !(boolean) tempt.invoke(ghast, flash), "actual ghast temptation predicate");
        }
        check(snow.is(ItemTags.HAPPY_GHAST_FOOD) && snow.is(ItemTags.HAPPY_GHAST_TEMPT_ITEMS), "ordinary snowball temptation tags unchanged");
        check(flash.is(Items.SNOWBALL), "flash remains a snowball");
        var snowIngredient = Ingredient.of(Items.SNOWBALL);
        check(snowIngredient.test(snow) && !snowIngredient.test(flash), "ingredient predicates distinguish flashbang");
        var contents = new StackedItemContents();
        contents.accountStack(flash);
        check(!contents.canCraft(List.of(snowIngredient), null), "recipe-book/stack counts exclude flashbang");
        contents.accountStack(snow);
        check(contents.canCraft(List.of(snowIngredient), null), "recipe-book accepts ordinary snowball");
        var normalInput = CraftingInput.of(2, 2, List.of(snow.copy(), snow.copy(), snow.copy(), snow.copy()));
        var mixedInput = CraftingInput.of(2, 2, List.of(snow.copy(), snow.copy(), snow.copy(), flash.copyWithCount(1)));
        var manager = world.getServer().getRecipeManager();
        var snowRecipe = manager.getRecipeFor(RecipeType.CRAFTING, normalInput, world).orElseThrow();
        check(snowRecipe.value().assemble(normalInput).is(Items.SNOW_BLOCK), "ordinary snowblock recipe retained");
        check(!snowRecipe.value().matches(mixedInput, world), "direct shaped recipe cannot use flashbang");
        check(manager.getRecipeFor(RecipeType.CRAFTING, mixedInput, world).isEmpty(), "manual crafting blocked");
        check(manager.getRecipeFor(RecipeType.CRAFTING, mixedInput, world, snowRecipe).isEmpty(), "cached recipe-holder path blocked");
        check(manager.getRecipeFor(RecipeType.CRAFTING, mixedInput, world, snowRecipe.id()).isEmpty(), "cached recipe-key path blocked");
        var cached = RecipeManager.createCheck(RecipeType.CRAFTING);
        check(cached.getRecipeFor(normalInput, world).isPresent() && cached.getRecipeFor(mixedInput, world).isEmpty(), "automated crafting cached check");

        check(flash.get(DataComponents.USE_COOLDOWN).seconds() == 1.5f, "item cooldown is 1.5 seconds");
        player.setItemSlot(EquipmentSlot.MAINHAND, flash.copy());
        check(UseItemCallback.EVENT.invoker().interact(player, world, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS_SERVER, "real throw handler succeeds");
        var held = player.getMainHandItem();
        int remaining = held.getCount();
        check(UseItemCallback.EVENT.invoker().interact(player, world, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
            && held.getCount() == remaining, "second immediate throw rejected without consuming item");
        for (int i = 0; i < 29; i++) player.getCooldowns().tick();
        check(player.getCooldowns().isOnCooldown(held), "cooldown lasts through tick 29");
        player.getCooldowns().tick();
        check(!player.getCooldowns().isOnCooldown(held), "cooldown ends at tick 30");
        System.out.println("BONGO_PHYSICS_SAFETY_PASS: native weak apex=" + height + " m after " + peakTick
            + " ticks, zero drag, speed-dependent centered rotation within body, adult/baby ghast exclusion, recipe/book/cached/crafter guards, 30-tick throw cooldown.");
    }

    private static void verifyRotatedBounds(FlashGrenade grenade) throws Exception {
        var factory = Display.class.getDeclaredMethod("createTransformation", SynchedEntityData.class);
        factory.setAccessible(true);
        var matrix = ((Transformation) factory.invoke(null, grenade.model().getEntityData())).getMatrix();
        Vec3 seat = grenade.getPassengerRidingPosition(grenade.model()).subtract(grenade.position());
        Vector3f center = matrix.transformPosition(new Vector3f(
            (float) FlashbangModelBounds.centerInDisplaySpace().x,
            (float) FlashbangModelBounds.centerInDisplaySpace().y,
            (float) FlashbangModelBounds.centerInDisplaySpace().z));
        check(Math.abs(center.x) < 0.00001 && Math.abs(center.z) < 0.00001
            && Math.abs(center.y + seat.y - grenade.getBbHeight() * 0.5) < 0.00001, "rotation pivots around grenade center");
        for (double x : new double[]{FlashbangModelBounds.MIN.x, FlashbangModelBounds.MAX.x})
            for (double y : new double[]{FlashbangModelBounds.MIN.y, FlashbangModelBounds.MAX.y})
                for (double z : new double[]{FlashbangModelBounds.MIN.z, FlashbangModelBounds.MAX.z}) {
                    var point = matrix.transformPosition(new Vector3f((float) -(x - 8) / 16,
                        (float) (y - 8) / 16, (float) -(z - 8) / 16));
                    check(Math.abs(point.x) <= grenade.getBbWidth() * 0.5 && Math.abs(point.z) <= grenade.getBbWidth() * 0.5
                        && point.y + seat.y >= 0 && point.y + seat.y <= grenade.getBbHeight(), "rotated model stays inside collision body");
                }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
