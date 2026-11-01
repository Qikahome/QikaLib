package qikahome.qikalib.lib.ingredient;

import javax.annotation.Nullable;

import org.antlr.v4.Tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.fml.ModList;
import qikahome.qikalib.compat.tconstruct.TConstructCompat;

public class ToolDamagingIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:tool_damaging");
    private final int damage;

    public ToolDamagingIngredient(Ingredient ingredient, int damage) {
        super(ingredient, false);
        this.damage = damage;
    }

    @Override
    public ItemStack consume(ItemStack stack) {
        if (stack.isEmpty())
            return stack;
        if (stack.getCount() > 1)
            throw new IllegalArgumentException("ToolDamagingIngredient only consumes single items");
        ItemStack copy = stack.copy();
        ItemStack remainder = super.consume(stack);
        if (copy.hurt(damage, RandomSource.create(), null)) {
            copy = remainder;
        }
        return copy;
    }

    @Override
    public ItemStack[] getItems() {
        ItemStack[] items = super.getItems();
        for (int i = 0; i < items.length; i++) {
            items[i] = items[i].copy();
            if (items[i].getMaxDamage()>=damage){
                items[i].setDamageValue(items[i].getMaxDamage()-damage);
            }
        }
        return items;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (super.test(stack)) {
            ModList modList = ModList.get();
            int maxDamage = stack.getMaxDamage();
            if (modList.isLoaded("tconstruct"))
                maxDamage = TConstructCompat.getRealMaxDamage(stack, maxDamage);
            if (stack.isDamageableItem() && maxDamage - stack.getDamageValue() >= damage)
                return true;
        }
        return false;
    }

    @Override
    public IIngredientSerializer<ToolDamagingIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.add("ingredient", ingredient.toJson());
        json.addProperty("damage", damage);
        return json;
    }

    public static class Serializer implements IIngredientSerializer<ToolDamagingIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public ToolDamagingIngredient parse(FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int damage = buffer.readVarInt();
            return new ToolDamagingIngredient(ingredient, damage);
        }

        @Override
        public ToolDamagingIngredient parse(JsonObject json) {
            if (!json.has("ingredient")) {
                throw new JsonParseException("Tool damaging ingredient must have 'ingredient' field");
            }

            int damage = GsonHelper.getAsInt(json, "damage", 1);
            Ingredient ingredient;
            if (json.has("remainder_override")) {
                ingredient = RemainderOverrideIngredient.Serializer.INSTANCE.parse(json);
            } else {
                JsonElement ingredientJson = json.get("ingredient");
                ingredient = Ingredient.fromJson(ingredientJson);
            }

            return new ToolDamagingIngredient(ingredient, damage);
        }

        @Override
        public void write(FriendlyByteBuf buffer, ToolDamagingIngredient ingredient) {
            ingredient.ingredient.toNetwork(buffer);
            buffer.writeVarInt(ingredient.damage);
        }
    }
}
