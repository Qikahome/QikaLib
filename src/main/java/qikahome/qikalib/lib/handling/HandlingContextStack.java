package qikahome.qikalib.lib.handling;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * 上下文栈：每层是一张 key → value 表。
 *
 * <ul>
 *   <li><b>读取</b>：从栈顶向栈底找第一个存在该键的层，内层作用域因此自动继承外层的值；</li>
 *   <li><b>写入</b>：只落在栈顶那一层，{@code pop()} 之后自然丢弃，外层不受影响；
 *       写入 null 表示<b>在当前层清除</b>该键（遮住下层），{@code pop()} 之后下层的值会重新透出来；</li>
 *   <li><b>栈空</b>：{@link #isValid()} 为 false，调用方据此跳过跨格校验。</li>
 * </ul>
 *
 * <p>实例由配方在匹配/组装期间自己持有，并通过
 * {@code SelfHandlingIngredient.setContext(...)} 绑到本次参与匹配的原料上 ——
 * 不是单例、不是线程级共享，因此这里也没有任何同步（{@link ArrayDeque} / {@link HashMap} 都是无锁的）。
 * 典型用法是每个候选摆放 {@code push()} 一层，失败回滚时 {@code pop()}。
 */
public final class HandlingContextStack implements HandlingContext {
    /** 栈顶是 {@code first} 那一端（{@code push} 插在这里，迭代顺序即栈顶 → 栈底）。 */
    private final Deque<Map<HandlingContext.Key<?>, Object>> layers = new ArrayDeque<>();

    public HandlingContextStack() {
    }

    /**
     * 压入一层空上下文。
     *
     * @return this，便于链式调用
     */
    public HandlingContextStack push() {
        layers.push(new HashMap<>());
        return this;
    }

    /**
     * 弹出栈顶一层，该层的写入全部丢弃。
     *
     * @return this，便于链式调用
     * @throws IllegalStateException 栈为空（{@code push} / {@code pop} 没有配对）
     */
    public HandlingContextStack pop() {
        if (layers.isEmpty())
            throw new IllegalStateException("HandlingContextStack.pop() 时栈为空，push/pop 没有配对");
        layers.pop();
        return this;
    }

    /** 当前层数（调试/断言用）。 */
    public int depth() {
        return layers.size();
    }

    /**
     * 清空所有层，回到"未 push"的状态（{@link #isValid()} 变回 false）。
     *
     * <p>复用同一个栈实例时用来重置；空栈调用是 no-op。
     * 放在最外层 {@code finally} 里可以兜住漏掉的 {@link #pop()}。
     *
     * @return this，便于链式调用
     */
    public HandlingContextStack clear() {
        layers.clear();
        return this;
    }

    @Override
    public boolean isValid() {
        return !layers.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public <V> V get(HandlingContext.Key<V> key) {
        // 用 containsKey 而不是 value != null：null 是合法的"显式没有值"，要能遮住下层
        for (Map<HandlingContext.Key<?>, Object> layer : layers) {
            if (layer.containsKey(key))
                return (V) layer.get(key);
        }
        return null;
    }

    @Override
    @Nullable
    public <V> V set(HandlingContext.Key<V> key, V value) {
        Map<HandlingContext.Key<?>, Object> top = layers.peek();
        if (top == null)
            return null;
        // 返回"读穿透"意义上的旧值，而不是只看栈顶那一层的 put 返回值：
        // 键只存在于下层时，put 会返回 null，但调用方看到的上一个有效值其实是下层那个。
        V previous = get(key);
        // null 也照常写进栈顶：表示"本层没有这个值"，遮住下层；pop() 之后下层复活
        top.put(key, value);
        return previous;
    }
}
