package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.Utils;

public class CountedIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:counted");
    /** {@code remainder_multiply}：布尔值 true = 按 count 乘 / false = 不乘，数字 = 直接作为倍率。 */
    private static final Codec<Either<Boolean, Integer>> REMAINDER_MULTIPLY_CODEC = Codec.either(Codec.BOOL,
            Codec.INT);
    public static final MapCodec<CountedIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    Codec.INT.fieldOf("count").forGetter(i -> i.count),
                    REMAINDER_MULTIPLY_CODEC.optionalFieldOf("remainder_multiply")
                            .forGetter(CountedIngredient::remainderMultiplyJson))
                    .apply(v, (ingredient, count, multiply) -> new CountedIngredient(ingredient, count,
                            multiply.map(e -> e.map(b -> b ? count : 1, n -> n)).orElse(count))));
    public static final DeferredHolder<IngredientType<?>, IngredientType<CountedIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), () -> new IngredientType<>(CODEC));

    private final int count;
    /** 剩余物数量倍率；默认等于 {@link #count}，可用 JSON 的 {@code remainder_multiply} 覆盖。 */
    private final int remainderMultiply;
    private SlotDisplay cachedDisplay;

    public CountedIngredient(Ingredient ingredient, int count) {
        this(ingredient, count, count);
    }

    public CountedIngredient(Ingredient ingredient, int count, int remainderMultiply) {
        super(ingredient, false);
        this.count = count;
        this.remainderMultiply = remainderMultiply;
    }

    /** {@code remainder_multiply} 接受数字或布尔值；等于 count 时省略。 */
    private Optional<Either<Boolean, Integer>> remainderMultiplyJson() {
        if (remainderMultiply == count)
            return Optional.empty();
        return Optional.of(Either.right(remainderMultiply));
    }

    @Override
    public ItemStack consume(ItemStack stack, ServerLevel level, @Nullable LivingEntity entity) {
        if (stack.isEmpty())
            return stack;
        ItemStack remainder = super.consume(stack, level, entity).copy();
        if (count == 0) {
            ItemStack stack2 = stack.copy();
            stack2.setCount(1);
            return stack2;
        }
        stack.shrink(count - 1);
        remainder.setCount(remainder.getCount() * remainderMultiply);
        return remainder;
    }

    @Override
    public SlotDisplay display() {
        if (cachedDisplay == null) {
            List<ItemStack> list = ingredient.display().resolveForStacks(Utils.displayContext()).stream()
                    .map(this::applyCountAndLore)
                    .toList();
            cachedDisplay = new SlotDisplay.Composite(list.stream()
                    .map(ItemStackTemplate::fromNonEmptyStack)
                    .map(SlotDisplay.ItemStackSlotDisplay::new)
                    .map(t -> (SlotDisplay) t)
                    .toList());
        }
        return cachedDisplay;
    }

    private ItemStack applyCountAndLore(ItemStack stack) {
        stack = stack.copy();
        if (count > 0) {
            stack.setCount(count);
        } else {
            ItemLore lore = stack.get(DataComponents.LORE);
            Component noConsume = Component.translatable("ui.qikalib.no_consume");
            if (lore == null) {
                stack.set(DataComponents.LORE, new ItemLore(new ArrayList<>(List.of(noConsume))));
            } else if (!lore.lines().contains(noConsume)) {
                var lines = new ArrayList<>(lore.lines());
                lines.add(noConsume);
                stack.set(DataComponents.LORE, new ItemLore(lines));
            }
        }
        return stack;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (super.test(stack))
            if (stack.getCount() >= count)
                return true;
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}
