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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class ItemDisplayOverrideIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:item_display_override");

    private static final Map<String, MapCodec<? extends IOpHandler>> OPS = new HashMap<>();

    /** 先校验 op 是否已注册，避免 dispatch 对未注册 op 取到 null codec 后抛 NPE */
    private static final Codec<String> OP_NAME_CODEC = Codec.STRING.flatXmap(
            name -> OPS.containsKey(name)
                    ? DataResult.success(name)
                    : DataResult.error(() -> "Unknown item display override op: \"" + name + "\""),
            name -> DataResult.success(name));

    private static final Codec<IOpHandler> HANDLER_CODEC = OP_NAME_CODEC.dispatch("op", IOpHandler::name, OPS::get);

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
            for (ItemStack stack : source.getItems())
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
    @Nullable
    private List<ItemStack> cachedDisplayStacks;

    public static final MapCodec<ItemDisplayOverrideIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(i -> i.ingredient),
                    OpEntry.LIST_CODEC.fieldOf("ops").forGetter(i -> i.ops))
                    .apply(v, ItemDisplayOverrideIngredient::new));
    /** 网络传输只发送应用 ops 后的展示物品列表，不发送 op 配置 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemDisplayOverrideIngredient> NETWORK_STREAM_CODEC = StreamCodec
            .of(ItemDisplayOverrideIngredient::toNetwork, ItemDisplayOverrideIngredient::fromNetwork);
    public static final DeferredHolder<IngredientType<?>, IngredientType<ItemDisplayOverrideIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), ItemDisplayOverrideIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<ItemDisplayOverrideIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), ItemDisplayOverrideIngredient::createType);

    private ItemDisplayOverrideIngredient(Ingredient ingredient, List<OpEntry> ops) {
        super(ingredient, false);
        this.ops = ops;
    }

    private ItemDisplayOverrideIngredient(Ingredient ingredient, Stream<ItemStack> displayStacks) {
        super(ingredient, false);
        this.ops = List.of();
        this.cachedDisplayStacks = displayStacks.toList();
    }

    public static IngredientType<ItemDisplayOverrideIngredient> createType() {
        return new IngredientType<>(CODEC, NETWORK_STREAM_CODEC);
    }

    @Override
    public Stream<ItemStack> getItems() {
        if (cachedDisplayStacks == null) {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : ingredient.getItems())
                result.add(stack.copy());
            for (OpEntry op : ops)
                op.handler.apply(result, op.filter);
            cachedDisplayStacks = result;
        }
        return cachedDisplayStacks.stream();
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    // ---- Network ----

    private static void toNetwork(RegistryFriendlyByteBuf buf, ItemDisplayOverrideIngredient ingredient) {
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient.ingredient);
        List<ItemStack> stacks = ingredient.getItems().toList();
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
