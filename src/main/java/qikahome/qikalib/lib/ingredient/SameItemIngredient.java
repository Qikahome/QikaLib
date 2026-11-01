package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.component.ItemLore;
import qikahome.qikalib.lib.handling.HandlingContext;

/**
 * 同一分组内的格子必须放同一种物品。
 *
 * <p>匹配期通过 {@link HandlingContext} 记录本组第一次见到的物品，后续格子必须一致；
 * 无上下文（JEI / 配方书展示等）时降级为只校验内层原料。
 */
public class SameItemIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:same_item");
    public static final MapCodec<SameItemIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    Codec.INT.fieldOf("group").forGetter(i -> i.group))
                    .apply(v, SameItemIngredient::new));
    public static final CustomIngredientSerializer<SameItemIngredient> SERIALIZER = new SimpleIngredientSerializer<>(ID,
            CODEC);

    private final HandlingContext.Key<Item> key;
    protected final int group;
    /** {@link #getMatchingStacks()} 的缓存，内层原料解析后固定。 */
    @Nullable
    private List<ItemStack> items;

    public SameItemIngredient(Ingredient ingredient, int group) {
        super(ingredient, true);
        this.group = group;
        this.key = new HandlingContext.Key<>(Item.class, ID.withSuffix(String.valueOf(group)));
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null)
            return false;
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
    public List<ItemStack> getMatchingStacks() {
        if (items == null) {
            ItemStack[] inner = ingredient.getItems();
            List<ItemStack> decorated = new ArrayList<>(inner.length);
            for (ItemStack original : inner) {
                if (original.isEmpty()) {
                    decorated.add(original);
                    continue;
                }
                ItemStack stack = original.copy();
                ItemLore lore = stack.get(DataComponents.LORE);
                List<Component> lines = lore == null ? new ArrayList<>() : new ArrayList<>(lore.lines());
                lines.add(Component.translatable("ingredient.qikalib.same_item.requires_same_group")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("ingredient.qikalib.same_item.group", group)
                        .withStyle(ChatFormatting.DARK_GRAY));
                stack.set(DataComponents.LORE, new ItemLore(lines));
                decorated.add(stack);
            }
            items = decorated;
        }
        return items;
    }

    @Override
    public CustomIngredientSerializer<SameItemIngredient> getSerializer() {
        return SERIALIZER;
    }
}
