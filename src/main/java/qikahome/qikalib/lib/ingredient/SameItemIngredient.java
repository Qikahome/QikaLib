package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.Utils;
import qikahome.qikalib.lib.handling.HandlingContext;

/**
 * 同一分组内必须放同一种物品的原料。
 *
 * <p>匹配期通过 {@link HandlingContext}（键 = 分组 + 容器槽位）记录该组第一次见到的物品，
 * 后续格子必须与它相同；没有上下文（JEI/配方书预览）时一律降级为通过。
 */
public class SameItemIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:same_item");

    public static final MapCodec<SameItemIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    Codec.INT.fieldOf("group").forGetter(i -> i.group))
                    .apply(v, SameItemIngredient::new));

    public static final DeferredHolder<IngredientType<?>, IngredientType<SameItemIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC));

    private final HandlingContext.Key<Item> key;
    protected final int group;
    /** {@link #display()} 的缓存，内层原料解析后固定。 */
    @Nullable
    private SlotDisplay cachedDisplay;

    public SameItemIngredient(Ingredient ingredient, int group) {
        super(ingredient, true);
        this.group = group;
        this.key = new HandlingContext.Key<>(Item.class, Identifier.parse(ID.toString() + group));
    }

    @Override
    public boolean test(ItemStack stack) {
        if (super.test(stack)) {
            var context = getContext();
            return !context.isValid()
                    || stack.getItem() == context.getOrAdd(key, stack::getItem);
        }
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    @Override
    public Stream<Holder<Item>> items() {
        return ingredient.items();
    }

    /**
     * 在 JEI/配方书里给每个候选物品加两行说明（分组语义 + 分组号）。
     * 结果缓存：内层原料解析后不再变化，重复调用不重复造栈。
     */
    @Override
    public SlotDisplay display() {
        if (cachedDisplay == null) {
            List<ItemStack> decorated = new ArrayList<>();
            for (ItemStack inner : ingredient.display().resolveForStacks(Utils.displayContext())) {
                if (inner.isEmpty())
                    continue;
                ItemStack stack = inner.copy();
                List<Component> lines = new ArrayList<>();
                ItemLore existing = stack.get(DataComponents.LORE);
                if (existing != null)
                    lines.addAll(existing.lines());
                lines.add(Component.translatable("ingredient.qikalib.same_item.requires_same_group")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("ingredient.qikalib.same_item.group", group)
                        .withStyle(ChatFormatting.DARK_GRAY));
                stack.set(DataComponents.LORE, new ItemLore(lines));
                decorated.add(stack);
            }
            cachedDisplay = new SlotDisplay.Composite(decorated.stream()
                    .map(ItemStackTemplate::fromNonEmptyStack)
                    .map(SlotDisplay.ItemStackSlotDisplay::new)
                    .map(t -> (SlotDisplay) t)
                    .toList());
        }
        return cachedDisplay;
    }

    public static void register() {
    }
}
