package qikahome.qikalib.compat.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import qikahome.qikalib.lib.recipe.ItemApplicationRecipe;

@OnlyIn(Dist.CLIENT)
@JeiPlugin
public class QikaLibJEIPlugin implements IModPlugin {
    private static final ResourceLocation ID = new ResourceLocation("qikalib:jei_plugin");

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
        List<ItemApplicationRecipe> recipes = recipeManager.getAllRecipesFor(ItemApplicationRecipe.TYPE);
        registration.addRecipes(ItemApplicationRecipeCategory.TYPE, recipes);
    }
}
