package qikahome.qikalib.lib.condition;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.util.IntRange;

/**
 * 资源条件 {@code qikalib:gamerule}：按游戏规则名（可带取值范围）决定资源是否加载。
 *
 * <p>26.x 的 GameRule 已改为注册表（{@code BuiltInRegistries.GAME_RULE}），
 * 这里按 {@code rule} 字符串匹配规则 id。
 */
public class GameRuleCondition implements ResourceCondition {
    public static final Identifier ID = Identifier.parse("qikalib:gamerule");
    /** JsonMore 时代的旧 id。 */
    public static final Identifier LEGACY_ID = Identifier.parse("jsonmore:gamerule");

    public static final MapCodec<GameRuleCondition> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    Codec.STRING.fieldOf("rule").forGetter(c -> c.ruleName),
                    IntRange.CODEC.optionalFieldOf("value").forGetter(c -> c.valueRange))
                    .apply(v, GameRuleCondition::new));
    public static final ResourceConditionType<GameRuleCondition> TYPE = ResourceConditionType.create(ID, CODEC);

    public static void register() {
        ResourceConditions.register(TYPE);
        ResourceConditions.register(ResourceConditionType.create(LEGACY_ID, CODEC));
    }

    private final String ruleName;
    private final Optional<IntRange> valueRange;

    public GameRuleCondition(String ruleName, Optional<IntRange> valueRange) {
        this.ruleName = ruleName;
        this.valueRange = valueRange;
    }

    @Override
    public boolean test(RegistryOps.RegistryInfoLookup registryLookup) {
        MinecraftServer server = QikaLib.getCurrentServer();
        if (server == null)
            return false;

        GameRule<?> rule = findRule(ruleName);
        if (rule == null)
            return false;

        Object value = getValue(server.getGameRules(), rule);
        if (valueRange.isEmpty()) {
            return value instanceof Boolean bool && bool;
        }
        if (value instanceof Integer integer) {
            return valueRange.get().contains(integer);
        }
        return false;
    }

    private static GameRule<?> findRule(String name) {
        for (GameRule<?> rule : BuiltInRegistries.GAME_RULE) {
            Identifier id = rule.getIdentifierWithFallback();
            if (rule.id().equals(name) || (id != null && id.toString().equals(name)))
                return rule;
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object getValue(GameRules rules, GameRule<?> rule) {
        return rules.get((GameRule) rule);
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }
}
