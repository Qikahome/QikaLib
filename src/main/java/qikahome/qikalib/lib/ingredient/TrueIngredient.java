package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
//import net.minecraftforge.registries.ForgeRegistries;

public class TrueIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:true");
    public static final TrueIngredient INSTANCE = new TrueIngredient();

    private TrueIngredient() {
        super(Stream.empty());
    }

    private static final ItemStack ANYTHING_STACK;

    static {
        ANYTHING_STACK = new ItemStack(Items.STICK);
        ANYTHING_STACK.setHoverName(Component.translatable("ingredient.qikalib.true"));
    }

    @Override
    public ItemStack[] getItems() {
        return new ItemStack[] { ANYTHING_STACK.copy() }; // 注意要copy，避免修改原实例
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        // true = 接受任意物品；null 不是物品栈，不算"任意"
        return stack != null;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        return json;
    }

    public static class Serializer implements IIngredientSerializer<TrueIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public TrueIngredient parse(FriendlyByteBuf buffer) {
            return TrueIngredient.INSTANCE;
        }

        @Override
        public TrueIngredient parse(JsonObject json) {
            return TrueIngredient.INSTANCE;
        }

        @Override
        public void write(FriendlyByteBuf buffer, TrueIngredient ingredient) {
        }
    }
}
