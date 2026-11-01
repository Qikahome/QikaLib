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

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.Utils;

public class ItemDisplayOverrideIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:item_display_override");

    private static final Map<String, MapCodec<? extends IOpHandler>> OPS = new HashMap<>();

    /** 未知 op 的占位 handler，仅用于让 codec 以 DataResult.error 收场。 */
    private static final IOpHandler UNKNOWN_OP = new IOpHandler() {
        @Override
        public String name() {
            return "unknown";
        }

        @Override
        public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
        }
    };

    private static MapCodec<? extends IOpHandler> opCodec(String name) {
        MapCodec<? extends IOpHandler> codec = OPS.get(name);
        if (codec != null) {
            return codec;
        }
        return MapCodec.unit(UNKNOWN_OP).flatXmap(
                handler -> DataResult.<IOpHandler>error(() -> "Unknown op '" + name + "'"),
                handler -> DataResult.<IOpHandler>error(() -> "Unknown op '" + name + "'"));
    }

    private static final Codec<IOpHandler> HANDLER_CODEC = Codec.STRING.dispatch("op", IOpHandler::name,
            ItemDisplayOverrideIngredient::opCodec);

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
            for (ItemStack stack : source.display().resolveForStacks(Utils.displayContext()))
                items.add(stack.copy());
        }
    }

    private record AddHandler(ItemStackTemplate stack) implements IOpHandler {
        public static final MapCodec<AddHandler> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        ItemStackTemplate.CODEC.fieldOf("value").forGetter(AddHandler::stack))
                        .apply(instance, AddHandler::new));

        @Override
        public String name() {
            return "add";
        }

        @Override
        public void apply(List<ItemStack> items, @Nullable Ingredient filter) {
            items.add(stack.create());
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
    @Nullable
    private List<ItemStack> cachedDisplayStacks;

    public static final MapCodec<ItemDisplayOverrideIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(i -> i.ingredient),
                    OpEntry.LIST_CODEC.fieldOf("ops").forGetter(i -> i.ops))
                    .apply(v, ItemDisplayOverrideIngredient::new));
    /** 网络传输只发送应用 ops 后的展示物品列表，不发送 op 配置 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemDisplayOverrideIngredient> NETWORK_STREAM_CODEC = StreamCodec
            .of(ItemDisplayOverrideIngredient::toNetwork,
                    ItemDisplayOverrideIngredient::fromNetwork);
    public static final DeferredHolder<IngredientType<?>, IngredientType<ItemDisplayOverrideIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC, NETWORK_STREAM_CODEC));

    private ItemDisplayOverrideIngredient(Ingredient ingredient, List<OpEntry> ops) {
        super(ingredient, false);
        this.ops = ops;
    }

    private ItemDisplayOverrideIngredient(Ingredient ingredient, Stream<ItemStack> displayStacks) {
        super(ingredient, false);
        this.ops = List.of();
        this.cachedDisplayStacks = displayStacks.toList();
    }

    private List<ItemStack> computeDisplayStacks() {
        if (cachedDisplayStacks == null) {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : ingredient.display().resolveForStacks(Utils.displayContext()))
                result.add(stack.copy());
            for (OpEntry op : ops)
                op.handler.apply(result, op.filter);
            cachedDisplayStacks = result;
        }
        return cachedDisplayStacks;
    }

    @Override
    public Stream<Holder<Item>> items() {
        return ingredient.items();
    }

    @Override
    public SlotDisplay display() {
        return new SlotDisplay.Composite(computeDisplayStacks().stream()
                .map(ItemStackTemplate::fromNonEmptyStack)
                .map(SlotDisplay.ItemStackSlotDisplay::new)
                .map(t -> (SlotDisplay) t)
                .toList());
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    // ---- Network ----

    private static void toNetwork(RegistryFriendlyByteBuf buf, ItemDisplayOverrideIngredient ingredient) {
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient.ingredient);
        List<ItemStack> stacks = ingredient.computeDisplayStacks();
        buf.writeVarInt(stacks.size());
        for (ItemStack stack : stacks)
            ItemStack.STREAM_CODEC.encode(buf, stack);
    }

    private static ItemDisplayOverrideIngredient fromNetwork(RegistryFriendlyByteBuf buf) {
        Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
        int size = buf.readVarInt();
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
            stacks.add(ItemStack.STREAM_CODEC.decode(buf));
        return new ItemDisplayOverrideIngredient(ingredient, stacks.stream());
    }

    // ---- Op entry ----

    private record OpEntry(@Nullable Ingredient filter, IOpHandler handler) {
        public static final Codec<OpEntry> CODEC = RecordCodecBuilder.create(
                v -> v.group(
                        Ingredient.CODEC.optionalFieldOf("filter").forGetter(op -> Optional.ofNullable(op.filter)),
                        HANDLER_CODEC.fieldOf("handler").forGetter(OpEntry::handler))
                        .apply(v, (filter, handler) -> new OpEntry(filter.orElse(null), handler)));
        public static final Codec<List<OpEntry>> LIST_CODEC = CODEC.listOf();
    }

    public static void register() {
    }
}
