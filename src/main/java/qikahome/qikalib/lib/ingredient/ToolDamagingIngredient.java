package qikahome.qikalib.lib.ingredient;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class ToolDamagingIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:tool_damaging");
    public static final MapCodec<ToolDamagingIngredient> CODEC = RecordCodecBuilder
            .mapCodec(v -> v.group(getIngredientField(), Codec.INT.fieldOf("damage").forGetter(i -> i.damage)).apply(v,
                    ToolDamagingIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<ToolDamagingIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), ToolDamagingIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<ToolDamagingIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), ToolDamagingIngredient::createType);

    private final int damage;

    public ToolDamagingIngredient(Ingredient ingredient, int damage) {
        super(ingredient, false);
        this.damage = damage;
    }

    public static IngredientType<ToolDamagingIngredient> createType() {
        return new IngredientType<>(CODEC);
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
        copy.hurtAndBreak(damage, level, entity, item -> breaked[0] = true);
        if (breaked[0])
            copy = remainder;
        return copy;
    }

    @Override
    public Stream<ItemStack> getItems() {
        return super.getItems().map(stack -> {
            stack = stack.copy();
            if (stack.getMaxDamage() >= damage)
                stack.setDamageValue(stack.getMaxDamage() - damage);
            return stack;
        });
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
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}
