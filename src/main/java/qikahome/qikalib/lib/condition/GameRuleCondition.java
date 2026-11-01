package qikahome.qikalib.lib.condition;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
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
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:gamerule");
    /** JsonMore 时代的旧 id。 */
    public static final ResourceLocation LEGACY_ID = ResourceLocation.parse("jsonmore:gamerule");
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<GameRuleCondition>> TYPE = QikaLib.CONDITION_CODECS
            .register(ID.getPath(), GameRuleCondition::createCodec);
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<GameRuleCondition>> LEGACY_TYPE = QikaLib.LEGACY_CONDITION_CODECS
            .register(ID.getPath(), GameRuleCondition::createCodec);

    private final String ruleName;
    private final Optional<IntRange> valueRange;

    public GameRuleCondition(String ruleName, Optional<IntRange> valueRange) {
        this.ruleName = ruleName;
        this.valueRange = valueRange;
    }

    public static MapCodec<GameRuleCondition> createCodec() {
        return RecordCodecBuilder.mapCodec(
                v -> v.group(
                        Codec.STRING.fieldOf("rule").forGetter(c -> c.ruleName),
                        IntRange.CODEC.optionalFieldOf("value").forGetter(c -> c.valueRange))
                        .apply(v, GameRuleCondition::new));
    }

    @Override
    public boolean test(IContext context) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null)
            return false;

        GameRules.Key<?> foundKey = findKey(ruleName);
        if (foundKey == null)
            return false;

        GameRules.Value<?> value = server.getGameRules().getRule(foundKey);
        if (value == null)
            return false;

        if (valueRange.isEmpty()) {
            if (value instanceof GameRules.BooleanValue bv)
                return bv.get();
            return false;
        }

        if (value instanceof GameRules.IntegerValue iv) {
            return valueRange.get().contains(iv.get());
        }
        return false;
    }

    private static GameRules.Key<?> findKey(String name) {
        for (var entry : GameRules.GAME_RULE_TYPES.entrySet()) {
            if (entry.getKey().toString().equals(name))
                return entry.getKey();
        }
        return null;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return TYPE.get();
    }

    public static void register() {
    }
}
