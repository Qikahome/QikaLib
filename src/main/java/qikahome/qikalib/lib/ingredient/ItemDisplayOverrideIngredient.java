package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;

public class ItemDisplayOverrideIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:item_display_override");

    private static final Map<String, IOpSerializer> OPS = new HashMap<>();

    public interface IOpSerializer {
        IOpHandler deserialize(JsonObject data);
    }

    public interface IOpHandler {
        /**
         * 对展示物品列表执行操作。{@code filter} 是 op 级的作用范围过滤：
         * 列表中只有匹配 filter 的物品才会被处理，不匹配的跳过；{@code null} 表示处理全部。
         */
        void apply(List<ItemStack> items, @Nullable Ingredient filter);
        void toJson(JsonObject out);
    }

    public static void registerOp(String name, IOpSerializer serializer) {
        OPS.put(name, serializer);
    }

    // ---- Built-in ops ----

    private record RemoveHandler(Ingredient toRemove) implements IOpHandler {
        @Override public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            if (filter == null) items.removeIf(toRemove);
            else items.removeIf(s -> toRemove.test(s) && filter.test(s));
        }
        @Override public void toJson(JsonObject out) { out.add("value", toRemove.toJson()); }
    }
    private record AddAllHandler(Ingredient source) implements IOpHandler {
        @Override public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            for (ItemStack stack : source.getItems()) items.add(stack.copy());
        }
        @Override public void toJson(JsonObject out) { out.add("value", source.toJson()); }
    }
    private record AddHandler(ItemStack stack) implements IOpHandler {
        @Override public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            items.add(stack.copy());
        }
        @Override public void toJson(JsonObject out) {
            out.add("value", ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).getOrThrow(false, s -> {}));
        }
    }
    private record ModifyNbtHandler(CompoundTag nbt, String mode) implements IOpHandler {
        @Override public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (filter != null && !filter.test(item)) continue;
                if ("replace".equals(mode)) item.setTag(nbt.copy());
                else item.getOrCreateTag().merge(nbt);
            }
        }
        @Override public void toJson(JsonObject out) {
            out.addProperty("mode", mode);
            out.add("value", CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, nbt).getOrThrow(false, s -> {}));
        }
    }
    private record ModifyCountHandler(String operation, int value) implements IOpHandler {
        @Override public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (filter != null && !filter.test(item)) continue;
                int count = switch (operation) {
                    case "set" -> value;
                    case "add" -> item.getCount() + value;
                    case "multiply" -> item.getCount() * value;
                    default -> item.getCount();
                };
                item.setCount(Math.max(1, count));
            }
        }
        @Override public void toJson(JsonObject out) {
            out.addProperty("operation", operation);
            out.addProperty("value", value);
        }
    }

    static {
        registerOp("remove", data -> new RemoveHandler(
                Ingredient.fromJson(data.get("value"))));
        registerOp("add_all", data -> new AddAllHandler(
                Ingredient.fromJson(data.get("value"))));
        registerOp("add", data -> new AddHandler(
                ItemStack.CODEC.parse(JsonOps.INSTANCE, data.get("value"))
                        .getOrThrow(false, s -> { throw new JsonParseException("Invalid item stack: " + s); })));
        registerOp("modify_nbt", data -> new ModifyNbtHandler(
                CompoundTag.CODEC.parse(JsonOps.INSTANCE, data.get("value"))
                        .getOrThrow(false, s -> { throw new JsonParseException("Invalid compound tag: " + s); }),
                GsonHelper.getAsString(data, "mode", "merge")));
        registerOp("modify_count", data -> new ModifyCountHandler(
                GsonHelper.getAsString(data, "operation", "set"),
                GsonHelper.getAsInt(data, "value")));
    }

    // ---- Instance ----

    private final List<OpEntry> ops;
    @Nullable private ItemStack[] cachedDisplayStacks;

    private ItemDisplayOverrideIngredient(Ingredient ingredient, List<OpEntry> ops, @Nullable ItemStack[] cachedDisplayStacks) {
        super(ingredient, false);
        this.ops = ops;
        this.cachedDisplayStacks = cachedDisplayStacks;
    }

    @Override
    public ItemStack[] getItems() {
        if (cachedDisplayStacks == null) {
            ItemStack[] base = ingredient.getItems();
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : base) result.add(stack.copy());
            for (OpEntry op : ops) op.handler.apply(result, op.filter);
            cachedDisplayStacks = result.toArray(new ItemStack[0]);
        }
        return cachedDisplayStacks.clone();
    }

    @Override public IIngredientSerializer<ItemDisplayOverrideIngredient> getSerializer() { return Serializer.INSTANCE; }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.add("ingredient", ingredient.toJson());
        JsonArray arr = new JsonArray();
        for (OpEntry op : ops) {
            JsonObject obj = new JsonObject();
            obj.addProperty("op", op.name);
            if (op.filter != null) obj.add("filter", op.filter.toJson());
            op.handler.toJson(obj);
            arr.add(obj);
        }
        json.add("ops", arr);
        return json;
    }

    // ---- Op entry ----

    private record OpEntry(String name, @Nullable Ingredient filter, IOpHandler handler) {}

    // ---- Serializer ----

    public static class Serializer implements IIngredientSerializer<ItemDisplayOverrideIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public ItemDisplayOverrideIngredient parse(FriendlyByteBuf buffer) {
            // 网络传输的是服务端应用 ops 后的展示物品列表，不再传输 op 配置
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int size = buffer.readVarInt();
            ItemStack[] stacks = new ItemStack[size];
            for (int i = 0; i < size; i++) stacks[i] = buffer.readItem();
            return new ItemDisplayOverrideIngredient(ingredient, List.of(), stacks);
        }

        @Override
        public ItemDisplayOverrideIngredient parse(JsonObject json) {
            if (!json.has("ingredient"))
                throw new JsonParseException("ItemDisplayOverrideIngredient must have 'ingredient' field");
            if (!json.has("ops"))
                throw new JsonParseException("ItemDisplayOverrideIngredient must have 'ops' array");

            Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
            JsonArray opsArray = GsonHelper.getAsJsonArray(json, "ops");
            List<OpEntry> ops = new ArrayList<>();

            for (JsonElement elem : opsArray) {
                JsonObject opObj = elem.getAsJsonObject();
                String name = GsonHelper.getAsString(opObj, "op");
                Ingredient filter = opObj.has("filter") ? Ingredient.fromJson(opObj.get("filter")) : null;

                IOpSerializer serializer = OPS.get(name);
                if (serializer == null)
                    throw new JsonParseException("Unknown op: " + name);

                IOpHandler handler = serializer.deserialize(opObj);
                ops.add(new OpEntry(name, filter, handler));
            }

            return new ItemDisplayOverrideIngredient(ingredient, ops, null);
        }

        @Override
        public void write(FriendlyByteBuf buffer, ItemDisplayOverrideIngredient ingredient) {
            ingredient.ingredient.toNetwork(buffer);
            ItemStack[] stacks = ingredient.getItems();
            buffer.writeVarInt(stacks.length);
            for (ItemStack stack : stacks) buffer.writeItemStack(stack, false);
        }
    }
}
