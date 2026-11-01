package qikahome.qikalib.lib.ingredient;

import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public class ToolDamagingIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:tool_damaging");
    public static final MapCodec<ToolDamagingIngredient> CODEC = RecordCodecBuilder
            .mapCodec(v -> v.group(getIngredientField(), Codec.INT.fieldOf("damage").forGetter(i -> i.damage)).apply(v,
                    ToolDamagingIngredient::new));
    public static final CustomIngredientSerializer<ToolDamagingIngredient> SERIALIZER = new SimpleIngredientSerializer<>(
            ID, CODEC);

    private final int damage;

    public ToolDamagingIngredient(Ingredient ingredient, int damage) {
        super(ingredient, false);
        this.damage = damage;
    }

    @Override
    public ItemStack consume(ItemStack stack, ServerLevel level, @Nullable LivingEntity entity) {
        if (stack.isEmpty())
            return stack;
        if (stack.getCount() > 1)
            throw new IllegalArgumentException("ToolDamagingIngredient only consumes single items");
        ItemStack copy = stack.copy();
        ItemStack remainder = super.consume(stack, level, entity);
        boolean[] broken = { false };
        // 26.x 的 hurtAndBreak 第三参是 ServerPlayer，按实际类型降级。
        copy.hurtAndBreak(damage, level, entity instanceof ServerPlayer serverPlayer ? serverPlayer : null,
                item -> broken[0] = true);
        if (broken[0])
            copy = remainder;
        return copy;
    }

    @Override
    public Stream<Holder<Item>> items() {
        return super.items();
    }

    @Override
    public SlotDisplay display() {
        List<ItemStack> stacks = IngredientDisplays.stacks(ingredient);
        for (ItemStack stack : stacks) {
            if (stack.getMaxDamage() >= damage)
                stack.setDamageValue(stack.getMaxDamage() - damage);
        }
        return IngredientDisplays.of(stacks);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (super.test(stack)) {
            int maxDamage = stack.getMaxDamage();
            if (stack.isDamageableItem() && maxDamage - stack.getDamageValue() >= damage)
                return true;
        }
        return false;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
