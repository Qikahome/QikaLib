package qikahome.qikalib.lib.handling;

import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.resources.Identifier;

/**
 * 匹配期跨格校验的上下文载体（语义见 CONTRACT.md §2）。
 *
 * <p>键是"容器槽位"，且必须幂等：同一个槽位在一次匹配里被测试多次，不能当成多个格子。
 * 值的类型由 {@link Key#type()} 区分 —— 不同类型必然是不同键，否则取用时无法安全转型。
 *
 * <p>本接口只约定"按键读写、没有值时读回 null"。存储结构与作用域由实现决定，
 * 例如 {@link HandlingContextStack} 是分层的（读取会穿透下层，写入只落在当前层）。
 */
public interface HandlingContext {
    public static record Key<V>(Class<?> type, Identifier id) {
    }

    public static final HandlingContext INVALID_INSTANCE = new HandlingContext() {

        @Override
        public <V> V get(Key<V> key) {
            return null;
        }

        @Override
        public <V> V set(Key<V> key, V value) {
            return null;
        }

        @Override
        public boolean isValid() {
            return false;
        }

    };

    /**
     * 获取一个值。
     *
     * @param <V> 值的类型
     * @param key 键
     * @return 该键当前的值；没有则为 null
     */
    @Nullable
    <V> V get(Key<V> key);

    /**
     * 设置一个值；传入 null 表示清除该键。
     *
     * @param <V>   值的类型
     * @param key   要设置的键
     * @param value 要设置的值；null 表示清除
     * @return 该键原来的值（即调用前 {@link #get} 会返回的值），没有则为 null
     */
    @Nullable
    <V> V set(Key<V> key, V value);

    /**
     * 获取一个值，或者如果不存在的话造一个
     *
     * @param <V>      值的类型
     * @param key      键
     * @param supplier 如果对应的值不存在时的值提供者（不允许返回 null）
     * @return 值
     */
    @Nonnull
    default <V> V getOrAdd(Key<V> key, Supplier<V> supplier) {
        var value = get(key);
        if (value == null)
            set(key, supplier.get());
        return get(key);
    }

    /**
     * 这个context是否可用
     *
     * @return 可用性
     */
    default boolean isValid() {
        return true;
    }
}
