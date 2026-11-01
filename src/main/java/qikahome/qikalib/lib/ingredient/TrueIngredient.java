package qikahome.qikalib.lib.ingredient;

import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import qikahome.qikalib.QikaLib;

public class TrueIngredient implements CustomIngredient {
    public static final ResourceLocation ID = new ResourceLocation(QikaLib.MODID, "true");
    public static final ResourceLocation LEGACY_ID = new ResourceLocation(QikaLib.LEGACY_MODID, "true");
    public static final TrueIngredient INSTANCE = new TrueIngredient();

    private TrueIngredient() {
    }

    private static final ItemStack ANYTHING_STACK;

    static {
        ANYTHING_STACK = new ItemStack(Items.STICK);
        ANYTHING_STACK.setHoverName(Component.translatable("ingredient.qikalib.true"));
    }

    /** 展示物品入口（保持 Forge 原版的数组形式，供 NotIngredient 复用）。 */
    public ItemStack[] getItems() {
        return new ItemStack[] { ANYTHING_STACK.copy() }; // 注意要copy，避免修改原实例
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return List.of(getItems());
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        // true = 接受任意物品；null 不是物品栈，不算"任意"
        return stack != null;
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements CustomIngredientSerializer<TrueIngredient> {
        public static final Serializer INSTANCE = new Serializer(ID);
        /** 旧 id 的独立实例（Fabric 的注册表以 identifier 为键，同一实例不能挂两个 id）。 */
        public static final Serializer LEGACY_INSTANCE = new Serializer(LEGACY_ID);

        private final ResourceLocation id;

        public Serializer(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation getIdentifier() {
            return id;
        }

        @Override
        public TrueIngredient read(JsonObject json) {
            return TrueIngredient.INSTANCE;
        }

        @Override
        public void write(JsonObject json, TrueIngredient ingredient) {
        }

        @Override
        public TrueIngredient read(FriendlyByteBuf buffer) {
            return TrueIngredient.INSTANCE;
        }

        @Override
        public void write(FriendlyByteBuf buffer, TrueIngredient ingredient) {
        }
    }
}
