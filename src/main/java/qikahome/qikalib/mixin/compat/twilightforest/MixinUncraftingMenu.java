package qikahome.qikalib.mixin.compat.twilightforest;

import net.minecraft.world.item.crafting.Recipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import qikahome.qikalib.lib.recipe.IHandlingRecipe;
import twilightforest.inventory.UncraftingMenu;

/**
 * 暮色森林拆解台不接受 {@link IHandlingRecipe}：这类配方的剩余物/产物不是原版语义，
 * 强拆会复制或吞物品。
 */
@Mixin(UncraftingMenu.class)
public class MixinUncraftingMenu {

    @Inject(method = "isRecipeSupported", at = @At("HEAD"), cancellable = true, remap = false)
    private static void qikalib$rejectHandlingRecipe(Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (recipe instanceof IHandlingRecipe) {
            cir.setReturnValue(false);
        }
    }
}
