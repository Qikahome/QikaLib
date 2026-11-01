package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public class ItemDisplayOverrideIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:item_display_override");

    private static final Map<String, MapCodec<? extends IOpHandler>> OPS = new HashMap<>();

    public interface IOpHandler {
        String name();

        /**
         * 对展示物品列表执行操作。{@code filter} 是 op 级的作用范围过滤：
         * 列表中只有匹配 filter 的物品才会被处理，不匹配的跳过；{@code null} 表示处理全部。
         */
        void apply(List<ItemStack> items, @Nullable Ingredient filter);
    }

    public static void registerOp(String name, MapCodec<? extends IOpHandler> codec) {
        OPS.put(name, codec);
    }

    // ---- Built-in ops ----

    private record RemoveHandler(Ingredient toRemove) implements IOpHandler {
        public static final MapCodec<RemoveHandler> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Ingredient.CODEC.fieldOf("value").forGetter(RemoveHandler::toRemove))
                        .apply(instance, RemoveHandler::new));

        @Override
        public String name() {
            return "remove";
        }

        @Override
        public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            if (filter == null)
                items.removeIf(toRemove);
            else
                items.removeIf(s -> toRemove.test(s) && filter.test(s));
        }
    }

    private record AddAllHandler(Ingredient source) implements IOpHandler {
        public static final MapCodec<AddAllHandler> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Ingredient.CODEC.fieldOf("value").forGetter(AddAllHandler::source))
                        .apply(instance, AddAllHandler::new));

        @Override
        public String name() {
            return "add_all";
        }

        @Override
        public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            for (ItemStack stack : IngredientDisplays.stacks(source))
                items.add(stack.copy());
        }
    }

    private record AddHandler(ItemStack stack) implements IOpHandler {
        public static final MapCodec<AddHandler> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        ItemStack.CODEC.fieldOf("value").forGetter(AddHandler::stack))
                        .apply(instance, AddHandler::new));

        @Override
        public String name() {
            return "add";
        }

        @Override
        public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            items.add(stack.copy());
        }
    }

    private record ModifyCountHandler(String operation, int value) implements IOpHandler {
        public static final MapCodec<ModifyCountHandler> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codec.STRING.fieldOf("operation").forGetter(ModifyCountHandler::operation),
                        Codec.INT.fieldOf("value").forGetter(ModifyCountHandler::value))
                        .apply(instance, ModifyCountHandler::new));

        @Override
        public String name() {
            return "modify_count";
        }

        @Override
        public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (filter != null && !filter.test(item))
                    continue;
                int count = switch (operation) {
                    case "set" -> value;
                    case "add" -> item.getCount() + value;
                    case "multiply" -> item.getCount() * value;
                    default -> item.getCount();
                };
                item.setCount(Math.max(1, count));
            }
        }
    }

    static {
        registerOp("remove", RemoveHandler.CODEC);
        registerOp("add_all", AddAllHandler.CODEC);
        registerOp("add", AddHandler.CODEC);
        registerOp("modify_count", ModifyCountHandler.CODEC);
    }

    // ---- Instance ----

    private final List<OpEntry> ops;

    public static final MapCodec<ItemDisplayOverrideIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(i -> i.ingredient),
                    OpEntry.LIST_CODEC.fieldOf("ops").forGetter(i -> i.ops))
                    .apply(v, ItemDisplayOverrideIngredient::new));
    public static final CustomIngredientSerializer<ItemDisplayOverrideIngredient> SERIALIZER = new SimpleIngredientSerializer<>(
            ID, CODEC);

    private ItemDisplayOverrideIngredient(Ingredient ingredient, List<OpEntry> ops) {
        super(ingredient, false);
        this.ops = ops;
    }

    @Override
    public Stream<Holder<Item>> items() {
        return ingredient.items();
    }

    @Override
    public SlotDisplay display() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : IngredientDisplays.stacks(ingredient))
            result.add(stack.copy());
        for (OpEntry op : ops)
            op.handler.apply(result, op.filter);
        return IngredientDisplays.of(result);
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    // ---- Op entry ----

    private record OpEntry(@Nullable Ingredient filter, IOpHandler handler) {
        static String name(OpEntry entry) {
            return entry.handler.name();
        }

        public static final Codec<OpEntry> CODEC = Codec.STRING
                .flatXmap(name -> OPS.containsKey(name)
                        ? DataResult.success(name)
                        : DataResult.error(() -> "Unknown item display op: " + name),
                        DataResult::success)
                .dispatch("op", OpEntry::name, OpEntry::codecFor);
        public static final Codec<List<OpEntry>> LIST_CODEC = CODEC.listOf();

        @SuppressWarnings("unchecked")
        private static MapCodec<OpEntry> codecFor(String name) {
            MapCodec<? extends IOpHandler> handlerCodec = OPS.get(name);
            if (handlerCodec == null)
                throw new IllegalArgumentException("Unknown item display op: " + name);
            RecordCodecBuilder<OpEntry, IOpHandler> handlerField = ((MapCodec<IOpHandler>) handlerCodec)
                    .forGetter(OpEntry::handler);
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC.optionalFieldOf("filter").forGetter(op -> Optional.ofNullable(op.filter)),
                    handlerField)
                    .apply(instance, (filter, handler) -> new OpEntry(filter.orElse(null), handler)));
        }
    }
}
