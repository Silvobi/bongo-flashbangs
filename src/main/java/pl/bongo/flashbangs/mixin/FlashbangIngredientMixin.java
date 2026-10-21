package pl.bongo.flashbangs.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.flashbangs.BongoFlashbangs;

@Mixin(Ingredient.class)
public abstract class FlashbangIngredientMixin {
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void bongoflashbangs$notAnIngredient(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (BongoFlashbangs.isFlashbang(stack)) cir.setReturnValue(false);
    }
}
