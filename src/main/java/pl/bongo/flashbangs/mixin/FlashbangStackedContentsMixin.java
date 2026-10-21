package pl.bongo.flashbangs.mixin;

import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.flashbangs.BongoFlashbangs;

@Mixin(StackedItemContents.class)
public abstract class FlashbangStackedContentsMixin {
    @Inject(method = "accountStack(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"), cancellable = true)
    private void bongoflashbangs$excludeFromRecipeCounts(ItemStack stack, int count, CallbackInfo ci) {
        if (BongoFlashbangs.isFlashbang(stack)) ci.cancel();
    }
}
