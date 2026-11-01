package qikahome.qikalib.lib.condition;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.server.ServerLifecycleHooks;

import qikahome.qikalib.util.IntRange;

/**
 * 按游戏规则值判断的配方/原料条件：{@code {"type": "qikalib:gamerule", "rule": "...", "value": ...}}。
 *
 * <p>{@code value} 可以是精确整数，也可以是区间字符串（{@link IntRange} 的语法）。
 */
public class GameRuleCondition implements ICondition {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:gamerule");
    /** JsonMore 时代的旧 id。 */
    public static final ResourceLocation LEGACY_ID = new ResourceLocation("jsonmore:gamerule");

    private final String ruleName;
    private final String valueRange;

    public GameRuleCondition(String ruleName, String valueRange) {
        this.ruleName = ruleName;
        this.valueRange = valueRange;
    }

    @Override
    public ResourceLocation getID() {
        return ID;
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

        if (valueRange == null) {
            if (value instanceof GameRules.BooleanValue bv)
                return bv.get();
            return false;
        }

        if (value instanceof GameRules.IntegerValue iv) {
            try {
                int exact = Integer.parseInt(valueRange);
                return iv.get() == exact;
            } catch (NumberFormatException e) {
                IntRange range = IntRange.parse(valueRange);
                return range.contains(iv.get());
            }
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

    public static class Serializer implements IConditionSerializer<GameRuleCondition> {
        public static final Serializer INSTANCE = new Serializer(ID);
        /** 旧 id 的别名：读写逻辑相同，只是 {@link #getID()} 不同（序列化器以 id 为注册键）。 */
        public static final Serializer LEGACY_INSTANCE = new Serializer(LEGACY_ID);

        private final ResourceLocation id;

        private Serializer(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public void write(JsonObject json, GameRuleCondition condition) {
            json.addProperty("rule", condition.ruleName);
            if (condition.valueRange != null) {
                try {
                    int exact = Integer.parseInt(condition.valueRange);
                    json.addProperty("value", exact);
                } catch (NumberFormatException e) {
                    json.addProperty("value", condition.valueRange);
                }
            }
        }

        @Override
        public GameRuleCondition read(JsonObject json) {
            String rule = GsonHelper.getAsString(json, "rule");
            String valueRange = null;
            if (json.has("value")) {
                var valueEl = json.get("value");
                if (valueEl.isJsonPrimitive() && valueEl.getAsJsonPrimitive().isNumber()) {
                    int exact = valueEl.getAsInt();
                    valueRange = "[" + exact + "," + exact + "]";
                } else {
                    valueRange = GsonHelper.getAsString(json, "value");
                }
            }
            return new GameRuleCondition(rule, valueRange);
        }

        @Override
        public ResourceLocation getID() {
            return id;
        }
    }
}
