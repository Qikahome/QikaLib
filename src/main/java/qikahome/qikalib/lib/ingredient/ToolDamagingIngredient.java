package qikahome.qikalib.lib.ingredient;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class ToolDamagingIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:tool_damaging");
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
        boolean[] breaked = { false };
        // 原版 ItemStack#hurtAndBreak(int, ServerLevel, ServerPlayer, Consumer) 的第三参是 ServerPlayer
        copy.hurtAndBreak(damage, level, entity instanceof ServerPlayer serverPlayer ? serverPlayer : null,
                item -> breaked[0] = true);
        if (breaked[0])
            copy = remainder;
        return copy;
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return super.getMatchingStacks().stream().map(stack -> {
            stack = stack.copy();
            if (stack.getMaxDamage() >= damage)
                stack.setDamageValue(stack.getMaxDamage() - damage);
            return stack;
        }).toList();
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null)
            return false;
        if (super.test(stack)) {
            int maxDamage = stack.getMaxDamage();
            if (stack.isDamageableItem() && maxDamage - stack.getDamageValue() >= damage)
                return true;
        }
        return false;
    }

    @Override
    public CustomIngredientSerializer<ToolDamagingIngredient> getSerializer() {
        return SERIALIZER;
    }
}
