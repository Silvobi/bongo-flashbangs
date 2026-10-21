package pl.bongo.flashbangs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

/** Uses the recipe loaded from the mod JAR, real crafting matches and a server-side player. */
public final class CraftingAndThrowVerification {
    public static void run(ServerLevel world) {
        var recipeKey = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("bongoflashbangs", "flashbang"));
        var manager = world.getServer().getRecipeManager();
        check(manager.byKey(recipeKey).isPresent(), "recipe loaded from server data");
        var advancement = world.getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("bongoflashbangs", "recipes/misc/flashbang"));
        check(advancement != null, "recipe-book unlock advancement loaded");
        Item[] ingredients = {Items.GUNPOWDER, Items.GLOWSTONE_DUST, Items.IRON_INGOT};
        int arrangements = 0;
        // Every occupied-slot arrangement in both the inventory grid and the crafting table.
        for (int width : new int[]{2, 3}) {
            int slots = width * width;
            for (int a = 0; a < slots; a++) for (int b = 0; b < slots; b++) for (int c = 0; c < slots; c++) {
                if (a == b || a == c || b == c) continue;
                List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(slots, ItemStack.EMPTY));
                stacks.set(a, new ItemStack(ingredients[0]));
                stacks.set(b, new ItemStack(ingredients[1]));
                stacks.set(c, new ItemStack(ingredients[2]));
                var input = CraftingInput.of(width, width, stacks);
                var recipe = manager.getRecipeFor(RecipeType.CRAFTING, input, world).orElseThrow();
                check(recipe.id().equals(recipeKey), "correct recipe selected");
                var result = recipe.value().assemble(input);
                check(result.getCount() == 2 && BongoFlashbangs.isFlashbang(result), "two functional flashbangs");
                check(ItemStack.isSameItemSameComponents(result, BongoFlashbangs.flashbangItem()),
                    "crafting result has exactly the command item's model, name, marker and cooldown");
                arrangements++;
            }
        }
        for (var invalid : List.of(
            List.of(new ItemStack(Items.GUNPOWDER), new ItemStack(Items.GLOWSTONE_DUST)),
            List.of(new ItemStack(Items.GUNPOWDER), new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.IRON_NUGGET)),
            List.of(new ItemStack(Items.GUNPOWDER), new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIRT)),
            List.of(new ItemStack(Items.GUNPOWDER), new ItemStack(Items.GUNPOWDER), new ItemStack(Items.IRON_INGOT)))) {
            List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(4, ItemStack.EMPTY));
            for (int i = 0; i < invalid.size(); i++) stacks.set(i, invalid.get(i));
            var match = manager.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(2, 2, stacks), world);
            check(match.isEmpty() || !match.get().id().equals(recipeKey), "missing/wrong/extra ingredients rejected");
        }

        var player = new ServerPlayer(world.getServer(), world,
            new GameProfile(UUID.randomUUID(), "ThrowProbe"), ClientInformation.createDefault());
        var grenade = new FlashGrenade(world, new Vec3(0, 110, 0), BongoFlashbangs.flashbangItem(), g -> {});
        // Reproduce the server/client distinction: ordinary delta is zero, reported movement isn't.
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(false);
        player.setKnownMovement(new Vec3(0.2, 0.42, -0.1));
        grenade.shootFromRotation(player, 0, 0, 0, 1.35f, 0);
        near(grenade.getDeltaMovement().y, 0.42, "jumping adds upward momentum");
        near(grenade.getDeltaMovement().x, 0.2, "horizontal movement inherited");
        near(grenade.getDeltaMovement().z, 1.35 - 0.1, "forward speed plus inherited motion");
        player.setKnownMovement(new Vec3(0, -0.3, 0));
        grenade.shootFromRotation(player, 0, 0, 0, 1.35f, 0);
        near(grenade.getDeltaMovement().y, -0.3, "falling adds downward momentum");
        player.setOnGround(true);
        grenade.shootFromRotation(player, 0, 0, 0, 1.35f, 0);
        near(grenade.getDeltaMovement().y, 0, "grounded throw ignores vertical motion");
        player.setOnGround(false);
        player.setKnownMovement(Vec3.ZERO);
        grenade.shootFromRotation(player, 0, 0, -5, BongoFlashbangs.WEAK_THROW_SPEED, 0);
        double aimedVerticalSpeed = grenade.getDeltaMovement().y;
        player.setKnownMovement(new Vec3(0, 0.42, 0));
        grenade.shootFromRotation(player, 0, 0, -5, BongoFlashbangs.WEAK_THROW_SPEED, 0);
        near(grenade.getDeltaMovement().y - aimedVerticalSpeed, 0.42,
            "jump velocity adds to aimed crouching throw");
        grenade.discard();
        System.out.println("BONGO_CRAFT_THROW_PASS: " + arrangements
            + " crafting arrangements in 2x2/3x3, exact 2-flashbang result, invalid ingredients rejected, recipe-book data, jump/fall/grounded/aimed throws using client-reported movement.");
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 0.00001, message + ": " + actual + " vs " + expected);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
