package qikahome.qikalib.mixin.compat.twilightforest;

import java.util.List;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.world.item.crafting.CraftingRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import qikahome.qikalib.lib.recipe.IHandlingRecipe;
import twilightforest.compat.jei.JEICompat;
import twilightforest.compat.jei.categories.JEIUncraftingCategory;

/** 与 {@link MixinUncraftingMenu} 对应：把 {@link IHandlingRecipe} 从拆解台的 JEI 列表里剔除。 */
@Mixin(JEICompat.class)
public class MixinTwilightForestJEICompat {

    @Redirect(method = "registerRecipes", at = @At(value = "INVOKE", target = "Lmezz/jei/api/registration/IRecipeRegistration;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"), remap = false)
    private void qikalib$filterRecipes(IRecipeRegistration registration, RecipeType<CraftingRecipe> recipeType,
            List<CraftingRecipe> recipes) {
        if (recipeType == JEIUncraftingCategory.UNCRAFTING)
            recipes.removeIf(r -> r instanceof IHandlingRecipe);
        registration.addRecipes(recipeType, recipes);
    }
}
