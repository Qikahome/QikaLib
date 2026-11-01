package qikahome.qikalib.lib.ingredient;

import static qikahome.qikalib.QikaLib.LOGGER;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import qikahome.qikalib.lib.handling.HandlingContext;

/**
 * 原料基类：在普通 {@link Ingredient} 之上加两个钩子 —— 剩余物结算（{@code consume}）
 * 与产物改写（{@code outputModify}）。语义见 CONTRACT.md §1。
 *
 * <p>Fabric 侧的 {@link CustomIngredient} 通过 {@link CustomIngredient#toVanilla()} 转成原版
 * {@link Ingredient}，因此静态入口都需要先经 {@link Ingredients#unwrap(Ingredient)} 取回自定义原料。
 *
 * <p>{@code ingredient} 字段是"内层原料"，泛型上不加约束：派生类可以是
 * "包一层普通原料"（本类），也可以是独立实现（{@code TrueIngredient} 等）。
 */
public abstract class SelfHandlingIngredient implements CustomIngredient {
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
    public static ItemStack consume(Ingredient ingredient, ItemStack stack) {
        if (stack.isEmpty())
            return stack;
        if (Ingredients.unwrap(ingredient) instanceof SelfHandlingIngredient selfHandlingIngredient)
            return selfHandlingIngredient.consume(stack);
        return vanillaConsume(stack);
    }

    /**
     * 消耗逻辑
     *
     * @param stack 要消耗的物品栈（会直接修改，必须传入容器中的原始引用）
     * @return 消耗后的返还物品
     */
    public ItemStack consume(ItemStack stack) {
        return consume(ingredient, stack);
    }

    /**
     * 工具方法：实现原版默认消耗逻辑（返还容器物品）。
     * 子类可在 consume 方法中调用此方法，简化代码。
     *
     * @param stack 要消耗的物品栈
     * @return 消耗后的返还物品
     */
    protected static ItemStack vanillaConsume(ItemStack stack) {
        if (stack.isEmpty())
            return stack;
        // 1.20.1 的物品栈没有 Forge 的 getCraftingRemainingItem()，改用 Item 上的同名方法。
        if (!stack.getItem().hasCraftingRemainingItem())
            return ItemStack.EMPTY;
        return new ItemStack(stack.getItem().getCraftingRemainingItem());
    }

    /**
     * 根据匹配的输入物品，修改配方输出物品。默认不做任何修改。
     *
     * @param ingredient 对应的配方材料
     * @param matched    匹配到的输入物品
     * @param output     输出物品（可直接修改其属性，如设置数量、NBT等）
     */
    public static void outputModify(Ingredient ingredient, ItemStack matched, ItemStack output) {
        if (matched.isEmpty())
            return;
        if (Ingredients.unwrap(ingredient) instanceof SelfHandlingIngredient selfHandlingIngredient)
            selfHandlingIngredient.outputModify(matched, output);
    }

    /**
     * 根据匹配的输入物品，修改配方输出物品。默认不做任何修改。
     *
     * @param matched 匹配到的输入物品
     * @param output  输出物品（可直接修改其属性，如设置数量、NBT等）
     */
    public void outputModify(ItemStack matched, ItemStack output) {
        outputModify(ingredient, matched, output);
    }

    /**
     * 本实例当前绑定的上下文。
     *
     * <p>未绑定时返回 {@link HandlingContext#INVALID_INSTANCE}（永不为 null）；调用方应先看
     * {@link HandlingContext#isValid()}，无效即按 CONTRACT.md §2 的"无上下文一律跳过跨格校验"处理。
     *
     * @return 当前上下文，不会为 null
     */
    @Nonnull
    public HandlingContext getContext() {
        return currentContext;
    }

    /**
     * 这个原料是否需要匹配上下文。
     *
     * <p>结果是"构造时声明的 {@code requiresContext}"或"内层原料链上任意一个需要" ——
     * 包装类在构造时传 false 就好，嵌套的自定义原料会自动把需求传上来。
     *
     * @return 是否需要上下文
     */
    public boolean requiresContext() {
        return requiresContext || (Ingredients.unwrap(ingredient) instanceof SelfHandlingIngredient selfHandlingIngredient
                && selfHandlingIngredient.requiresContext());
    }

    /**
     * {@link #requiresContext()} 的静态入口，接受任意 {@link Ingredient}。
     *
     * @param ingredient 配方材料，可以是任意 {@link Ingredient}
     * @return 是否需要上下文；非本库原料一律 false
     */
    public static boolean requiresContext(Ingredient ingredient) {
        return Ingredients.unwrap(ingredient) instanceof SelfHandlingIngredient selfHandlingIngredient
                && selfHandlingIngredient.requiresContext();
    }

    /**
     * 给（可能不是本库原料的）{@code ingredient} 绑定上下文。
     *
     * <p>只有 {@link SelfHandlingIngredient} 会接收，普通 {@link Ingredient} 静默忽略；
     * 会沿"内层原料"链向下传播，保证嵌套的自定义原料在 {@code test} 里也读得到。
     *
     * @param ingredient 配方材料，可以是任意 {@link Ingredient}
     * @param context    本次匹配/组装的上下文，不允许为 null
     */
    public static void setContext(Ingredient ingredient, @Nonnull HandlingContext context) {
        if (Ingredients.unwrap(ingredient) instanceof SelfHandlingIngredient selfHandlingIngredient)
            selfHandlingIngredient.setContext(context);
    }

    /**
     * 绑定本实例的上下文，并向下传播到嵌套的自定义原料。
     *
     * <p>由配方层在调用 {@code super.matches} / {@code super.assemble} 之前调用，返回之前用
     * {@link #invalidContext()} 解绑。若上一个上下文<b>不同</b>且仍然有效，说明上次没有成对解绑，记一条 error；
     * 绑定同一个实例则视为幂等（形状配方同一个 key 在多个格子复用同一实例）。
     *
     * <p>不支持嵌套：本方法不保存上一层上下文，各入口之间不应互相进入（需要回滚粒度时，
     * 用上下文自己的栈，而不是在这里嵌套）。
     *
     * @param context 本次匹配/组装的上下文，不允许为 null
     */
    public void setContext(@Nonnull HandlingContext context) {
        // 同一上下文重复绑定是幂等的：形状配方的同一个 key 会在多个格子里复用同一个 Ingredient 实例，
        // 遍历 getIngredients() 时必然重复；这不是泄漏，也不该重复向下传播。
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
     *
     * <p>与 {@link #setContext(Ingredient, HandlingContext)} 成对使用，通常放在 {@code finally} 里。
     *
     * @param ingredient 配方材料，可以是任意 {@link Ingredient}
     */
    public static void invalidContext(Ingredient ingredient) {
        if (Ingredients.unwrap(ingredient) instanceof SelfHandlingIngredient selfHandlingIngredient)
            selfHandlingIngredient.invalidContext();
    }

    /** 解绑本实例的上下文，并向下传播；之后 {@link #getContext()} 回到 {@link HandlingContext#INVALID_INSTANCE}。 */
    public void invalidContext() {
        this.currentContext = HandlingContext.INVALID_INSTANCE;
        invalidContext(ingredient);
    }

    /**
     * Forge 原版的展示物品入口（返回数组）。Fabric 接口只要求 {@link #getMatchingStacks()}，
     * 这里保留该方法供各子类照常覆写，并由基类统一转换成列表。
     */
    public ItemStack[] getItems() {
        return ingredient.getItems();
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return List.of(getItems());
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return ingredient.test(stack);
    }
}
