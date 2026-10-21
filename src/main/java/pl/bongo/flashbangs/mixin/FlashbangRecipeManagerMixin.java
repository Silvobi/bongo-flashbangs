package pl.bongo.flashbangs.mixin;

import java.util.Optional;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.flashbangs.BongoFlashbangs;

@Mixin(RecipeManager.class)
public abstract class FlashbangRecipeManagerMixin {
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
        at = @At("HEAD"), cancellable = true)
    private void bongoflashbangs$rejectRecipe(RecipeType<?> type, RecipeInput input, Level level,
                                            CallbackInfoReturnable<Optional<?>> cir) {
        if (bongoflashbangs$containsFlashbang(input)) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
        at = @At("HEAD"), cancellable = true)
    private void bongoflashbangs$rejectCachedRecipe(RecipeType<?> type, RecipeInput input, Level level,
            RecipeHolder<?> previous, CallbackInfoReturnable<Optional<?>> cir) {
        if (bongoflashbangs$containsFlashbang(input)) cir.setReturnValue(Optional.empty());
    }

    @Unique private static boolean bongoflashbangs$containsFlashbang(RecipeInput input) {
        for (int i = 0; i < input.size(); i++) if (BongoFlashbangs.isFlashbang(input.getItem(i))) return true;
        return false;
    }
}
