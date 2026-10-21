package pl.bongo.flashbangs.mixin;

import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.flashbangs.BongoFlashbangs;

@Mixin(HappyGhast.class)
public abstract class FlashbangHappyGhastMixin {
    // Adult goals use a tag predicate; the baby brain's food sensor calls isFood.
    @Inject(method = {"isFood(Lnet/minecraft/world/item/ItemStack;)Z",
        "lambda$registerGoals$0(Lnet/minecraft/world/item/ItemStack;)Z"},
        at = @At("HEAD"), cancellable = true)
    private void bongoflashbangs$notGhastFood(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (BongoFlashbangs.isFlashbang(stack)) cir.setReturnValue(false);
    }
}
