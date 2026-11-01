package qikahome.qikalib.compat.jei;

import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import qikahome.qikalib.lib.recipe.ItemApplicationRecipe;

@JeiPlugin
public class QikaLibJEIPlugin implements IModPlugin {
    private static final Identifier ID = Identifier.parse("qikalib:jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new ItemApplicationRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // 优先使用 fabric-recipe-api 同步到客户端的配方缓存
        Collection<RecipeHolder<ItemApplicationRecipe>> recipes = ItemApplicationRecipe.getClientRecipes();

        if (recipes == null || recipes.isEmpty()) {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) {
                return;
            }
            recipes = server.getRecipeManager().getAllOfType(ItemApplicationRecipe.TYPE);
        }

        registration.addRecipes(ItemApplicationRecipeCategory.TYPE,
                recipes.stream().map(RecipeHolder::value).toList());
    }
}
