package qikahome.qikalib.compat.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import qikahome.qikalib.lib.recipe.ItemApplicationRecipe;

@JeiPlugin
public class QikaLibJEIPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.parse("qikalib:jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new ItemApplicationRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();
        List<RecipeHolder<ItemApplicationRecipe>> recipes = recipeManager.getAllRecipesFor(ItemApplicationRecipe.TYPE);
        registration.addRecipes(ItemApplicationRecipeCategory.TYPE, recipes.stream().map(RecipeHolder::value).toList());
    }
}
