package qikahome.qikalib.lib.condition;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.util.IntRange;

/**
 * 按游戏规则值判断的配方/原料条件：{@code {"type": "qikalib:gamerule", "rule": "...", "value": ...}}。
 *
 * <p>{@code value} 可以是精确整数，也可以是区间字符串（{@link IntRange} 的语法）。
 */
public class GameRuleCondition implements ICondition {
    public static final Identifier ID = Identifier.parse("qikalib:gamerule");
    /** JsonMore 时代的旧 id。 */
    public static final Identifier LEGACY_ID = Identifier.parse("jsonmore:gamerule");

    private static MapCodec<GameRuleCondition> createCodec() {
        return RecordCodecBuilder.mapCodec(
                v -> v.group(
                        Identifier.CODEC.fieldOf("rule").forGetter(c -> c.ruleName),
                        IntRange.CODEC.optionalFieldOf("value").forGetter(c -> c.valueRange))
                        .apply(v, GameRuleCondition::new));
    }

    public static final MapCodec<GameRuleCondition> CODEC = createCodec();
    /** 旧 id 的独立 codec 实例（条件注册表不允许同一个实例挂两个 id）。 */
    public static final MapCodec<GameRuleCondition> LEGACY_CODEC = createCodec();

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<GameRuleCondition>> HOLDER = QikaLib.CONDITION_CODECS
            .register("gamerule", () -> CODEC);

    public static void register() {
    }

    private final Identifier ruleName;
    private final Optional<IntRange> valueRange;

    public GameRuleCondition(Identifier ruleName, Optional<IntRange> valueRange) {
        this.ruleName = ruleName;
        this.valueRange = valueRange;
    }

    @Override
    public boolean test(IContext context) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null)
            return false;

        GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(ruleName);
        if (rule == null)
            return false;

        if (!valueRange.isPresent()) {
            return server.getGameRules().get(rule) instanceof Boolean bool ? bool : false;
        }

        return server.getGameRules().get(rule) instanceof Number num
                ? valueRange.get().contains(num.intValue())
                : false;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
