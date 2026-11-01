package qikahome.qikalib.lib.recipe;

/**
 * 标记接口：配方需要按原料的 {@code consume} / {@code outputModify} 钩子处理
 * 剩余物与产物（即实现方会调用 {@code SelfHandlingIngredient} 的静态入口）。
 */
public interface IHandlingRecipe {
}
