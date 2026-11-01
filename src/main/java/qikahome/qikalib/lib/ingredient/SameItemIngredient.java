package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.lib.handling.HandlingContext;

/**
 * 同一分组内必须放同一种物品的原料（键是"容器槽位"，见 CONTRACT.md §2）。
 *
 * <p>{@code group} 用来区分不同的"同一物品"约束；同一个组在不同格子里的物品必须完全相同。
 */
public class SameItemIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:same_item");
    public static final MapCodec<SameItemIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(i -> i.ingredient),
                    Codec.INT.fieldOf("group").forGetter(i -> i.group))
                    .apply(v, SameItemIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<SameItemIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), SameItemIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<SameItemIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), SameItemIngredient::createType);

    private final HandlingContext.Key<Item> key;
    protected final int group;
    /** {@link #getItems()} 的缓存，内层原料解析后固定。 */
    @Nullable
    private List<ItemStack> items;

    public SameItemIngredient(Ingredient ingredient, int group) {
        super(ingredient, true);
        this.group = group;
        this.key = new HandlingContext.Key<>(Item.class, ID.withSuffix(String.valueOf(group)));
    }

    public static IngredientType<SameItemIngredient> createType() {
        return new IngredientType<>(CODEC);
    }

    @Override
    public boolean test(ItemStack stack) {
        if (super.test(stack)) {
            var context = getContext();
            return !context.isValid()
                    || stack.is(context.getOrAdd(key, stack::getItem));
        }
        return false;
    }

    /**
     * 在 JEI/配方书里给每个候选物品加两行说明（分组语义 + 分组号）。
     * 结果缓存：内层原料解析后不再变化，重复调用不重复造栈。
     */
    @Override
    public Stream<ItemStack> getItems() {
        if (items == null) {
            ItemStack[] inner = ingredient.getItems();
            List<ItemStack> decorated = new ArrayList<>(inner.length);
            for (ItemStack stack : inner) {
                if (!stack.isEmpty()) {
                    stack = stack.copy();
                    List<Component> lines = new ArrayList<>();
                    ItemLore existing = stack.get(DataComponents.LORE);
                    if (existing != null)
                        lines.addAll(existing.lines());
                    lines.add(Component.translatable("ingredient.qikalib.same_item.requires_same_group")
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("ingredient.qikalib.same_item.group", group)
                            .withStyle(ChatFormatting.DARK_GRAY));
                    stack.set(DataComponents.LORE, new ItemLore(lines));
                }
                decorated.add(stack);
            }
            items = decorated;
        }
        return items.stream().map(ItemStack::copy);
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}
