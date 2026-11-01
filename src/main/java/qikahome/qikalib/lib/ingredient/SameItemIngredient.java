package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import qikahome.qikalib.lib.handling.HandlingContext;

/**
 * 同组物品必须一致：匹配期用 {@link HandlingContext} 记录"组 → 物品"，
 * 后续格子与已记录值比较，不一致则 {@code test} 返回 false。
 */
public class SameItemIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:same_item");
    public static final MapCodec<SameItemIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    Codec.INT.fieldOf("group").forGetter(i -> i.group))
                    .apply(v, SameItemIngredient::new));
    public static final CustomIngredientSerializer<SameItemIngredient> SERIALIZER = new SimpleIngredientSerializer<>(ID,
            CODEC);

    private final HandlingContext.Key<Item> key;
    protected final int group;

    public SameItemIngredient(Ingredient ingredient, int group) {
        super(ingredient, true);
        this.group = group;
        this.key = new HandlingContext.Key<>(Item.class, ID.withSuffix(String.valueOf(group)));
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
    public Stream<Holder<Item>> items() {
        return ingredient.items();
    }

    /**
     * 在 JEI/配方书里给每个候选物品加两行说明（分组语义 + 分组号）。
     */
    @Override
    public SlotDisplay display() {
        List<ItemStack> decorated = new ArrayList<>();
        for (ItemStack inner : IngredientDisplays.stacks(ingredient)) {
            if (inner.isEmpty()) {
                decorated.add(inner);
                continue;
            }
            ItemStack stack = inner.copy();
            ItemLore lore = stack.get(DataComponents.LORE);
            List<Component> lines = new ArrayList<>(lore == null ? List.of() : lore.lines());
            lines.add(Component.translatable("ingredient.qikalib.same_item.requires_same_group")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("ingredient.qikalib.same_item.group", group)
                    .withStyle(ChatFormatting.DARK_GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lines));
            decorated.add(stack);
        }
        return IngredientDisplays.of(decorated);
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
