package qikahome.qikalib.lib.ingredient;

import static qikahome.qikalib.QikaLib.LOGGER;

import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import qikahome.qikalib.lib.handling.HandlingContext;

/**
 * 原料基类：在普通 {@link Ingredient} 之上加两个钩子 —— 剩余物结算（{@code consume}）
 * 与产物改写（{@code outputModify}）。语义见 CONTRACT.md §1。
 *
 * <p>{@code ingredient} 字段是"内层原料"，泛型上不加约束：派生类可以是
 * "包一层普通原料"（本类），也可以是独立实现（{@code TrueIngredient} 等）。
 */
public abstract class SelfHandlingIngredient implements ICustomIngredient {
    protected static final MapCodec<Ingredient> INGREDIENT = Ingredient.CODEC.fieldOf("ingredient");

    protected static <T extends SelfHandlingIngredient> RecordCodecBuilder<T, Ingredient> getIngredientField() {
        return INGREDIENT.forGetter(ing -> ing.ingredient);
    }

    protected final Ingredient ingredient;

    /**
     * 本次匹配/组装使用的上下文。
     *
     * <p>由配方层在调用 {@code super.matches} / {@code super.assemble} 之前绑定、返回之前解绑；
     * 未绑定时为 {@link HandlingContext#INVALID_INSTANCE}。不放在线程/全局上 ——
     * 上下文的作用域就是这个配方这一次调用、这些原料实例。
     */
    @Nonnull
    private HandlingContext currentContext = HandlingContext.INVALID_INSTANCE;

    /** 本类自身是否需要匹配上下文（不含内层原料）。 */
    private final boolean requiresContext;

    public SelfHandlingIngredient(Ingredient ingredient) {
        this(ingredient, false);
    }

    /**
     * @param ingredient      内层原料
     * @param requiresContext 本类自身是否读取 {@link #getContext()}；包装类传 false 即可，
     *                        内层原料的需求会自动沿链传上来
     */
    public SelfHandlingIngredient(Ingredient ingredient, boolean requiresContext) {
        this.ingredient = ingredient;
        this.requiresContext = requiresContext;
    }

    /**
     * 消耗逻辑
     *
     * @param ingredient 要消耗的配方材料
     * @param stack      要消耗的物品栈（容器中的原始引用）
     * @return 消耗后的返还物品（由调用者决定如何处理）
     */
    public static ItemStack consume(Ingredient ingredient, ItemStack stack, ServerLevel level,
            @Nullable LivingEntity entity) {
        if (stack.isEmpty())
            return stack;
        if (ingredient.getCustomIngredient() instanceof SelfHandlingIngredient selfHandlingIngredient)
            return selfHandlingIngredient.consume(stack, level, entity);
        return vanillaConsume(stack);
    }

    /**
     * 消耗逻辑
     *
     * @param stack 要消耗的物品栈（会直接修改，必须传入容器中的原始引用）
     * @return 消耗后的返还物品
     */
    public ItemStack consume(ItemStack stack, ServerLevel level, @Nullable LivingEntity entity) {
        return consume(ingredient, stack, level, entity);
    }

    /**
     * 工具方法：实现原版默认消耗逻辑（返还容器物品）。
     */
    protected static ItemStack vanillaConsume(ItemStack stack) {
        if (stack.isEmpty())
            return stack;
        return stack.getCraftingRemainder() instanceof ItemStackTemplate notnull ? notnull.create() : ItemStack.EMPTY;
    }

    /**
     * 根据匹配的输入物品，修改配方输出物品。默认不做任何修改。
     */
    public static void outputModify(Ingredient ingredient, ItemStack matched, ItemStack output) {
        if (matched.isEmpty())
            return;
        if (ingredient.getCustomIngredient() instanceof SelfHandlingIngredient selfHandlingIngredient)
            selfHandlingIngredient.outputModify(matched, output);
    }

    /**
     * 根据匹配的输入物品，修改配方输出物品。默认不做任何修改。
     */
    public void outputModify(ItemStack matched, ItemStack output) {
        outputModify(ingredient, matched, output);
    }

    /**
     * 本实例当前绑定的上下文；未绑定时返回 {@link HandlingContext#INVALID_INSTANCE}（永不为 null）。
     */
    @Nonnull
    public HandlingContext getContext() {
        return currentContext;
    }

    /**
     * 这个原料是否需要匹配上下文。包装类构造时传 false 即可，嵌套原料的需求会自动传上来。
     */
    public boolean requiresContext() {
        return requiresContext
                || (ingredient.getCustomIngredient() instanceof SelfHandlingIngredient self && self.requiresContext());
    }

    /**
     * {@link #requiresContext()} 的静态入口，接受任意 {@link Ingredient}。
     */
    public static boolean requiresContext(Ingredient ingredient) {
        return ingredient.getCustomIngredient() instanceof SelfHandlingIngredient self && self.requiresContext();
    }

    /**
     * 给（可能不是本库原料的）{@code ingredient} 绑定上下文，并沿内层原料链向下传播。
     */
    public static void setContext(Ingredient ingredient, @Nonnull HandlingContext context) {
        if (ingredient != null && ingredient.getCustomIngredient() instanceof SelfHandlingIngredient self)
            self.setContext(context);
    }

    /**
     * 绑定本实例的上下文，并向下传播到嵌套的自定义原料。
     */
    public void setContext(@Nonnull HandlingContext context) {
        // 同一上下文重复绑定是幂等的：形状配方的同一个 key 会在多个格子里复用同一个 Ingredient 实例。
        if (this.currentContext == context)
            return;
        if (this.currentContext.isValid())
            LOGGER.error("尝试绑定上下文{}，但上一个上下文{}未被解绑，这是异常的调用顺序或状态泄露。", context, currentContext,
                    new IllegalStateException());
        this.currentContext = context;
        setContext(ingredient, context);
    }

    /**
     * 解绑（可能不是本库原料的）{@code ingredient} 的上下文。
     */
    public static void invalidContext(Ingredient ingredient) {
        if (ingredient != null && ingredient.getCustomIngredient() instanceof SelfHandlingIngredient self)
            self.invalidContext();
    }

    /** 解绑本实例的上下文，并向下传播。 */
    public void invalidContext() {
        this.currentContext = HandlingContext.INVALID_INSTANCE;
        invalidContext(ingredient);
    }

    @Override
    public Stream<Holder<Item>> items() {
        return ingredient.items();
    }

    @Override
    public SlotDisplay display() {
        return ingredient.display();
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return ingredient.test(stack);
    }

    @Override
    public boolean isSimple() {
        return false;
    }
}
