package qikahome.qikalib.lib.ingredient;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.FabricIngredient;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * {@link FabricIngredient} 是通过 mixin 接口注入到 {@code Ingredient} 上的，
 * 而 {@code Ingredient} 是 final 类且编译期并不实现该接口，因此必须经 {@code Object} 中转才能强转。
 */
public final class Ingredients {
    private Ingredients() {
    }

    @Nullable
    public static CustomIngredient unwrap(Ingredient ingredient) {
        return ((FabricIngredient) (Object) ingredient).getCustomIngredient();
    }
}
