package qikahome.qikalib.lib.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import qikahome.qikalib.lib.handling.HandlingContextStack;
import qikahome.qikalib.lib.ingredient.SelfHandlingIngredient;

public class ShapelessHandlingRecipe extends ShapelessRecipe implements IHandlingRecipe {

    // 缓存上一次的匹配结果，transient 避免序列化
    private volatile transient Map<Integer, Ingredient> lastMatch;

    /** 是否有原料需要匹配上下文（解析后固定）；不需要时整套 context 机械都跳过。 */
    private final boolean requiresContext;

    public ShapelessHandlingRecipe(String group, CraftingBookCategory category,
            ItemStack result, NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
        this.requiresContext = getIngredients().stream().anyMatch(SelfHandlingIngredient::requiresContext);
    }

    public static ShapelessHandlingRecipe fromVanilla(ShapelessRecipe shapelessRecipe) {
        return new ShapelessHandlingRecipe(shapelessRecipe.getGroup(), shapelessRecipe.category(),
                shapelessRecipe.getResultItem(RegistryAccess.EMPTY), shapelessRecipe.getIngredients());
    }

    /**
     * 有原料需要上下文时才建栈并绑定本配方的全部原料，否则返回 null。
     * 返回值原样交给 {@link #endContext(HandlingContextStack)}。
     */
    @Nullable
    private HandlingContextStack beginContext() {
        if (!requiresContext)
            return null;
        HandlingContextStack context = new HandlingContextStack().push();
        for (Ingredient ingredient : getIngredients())
            SelfHandlingIngredient.setContext(ingredient, context);
        return context;
    }

    private void endContext(@Nullable HandlingContextStack context) {
        if (context == null)
            return;
        for (Ingredient ingredient : getIngredients())
            SelfHandlingIngredient.invalidContext(ingredient);
    }

    /**
     * 原版 {@code ShapelessRecipe.matches} 会直接调 {@code ingredient.test(...)}，
     * 所以必须在 super 之前绑定上下文。
     */
    @Override
    public boolean matches(CraftingInput container, Level level) {
        HandlingContextStack context = beginContext();
        try {
            return super.matches(container, level);
        } finally {
            endContext(context);
        }
    }

    // ==================== 共用匹配逻辑（带缓存） ====================
    private Map<Integer, Ingredient> matchIngredients(CraftingInput container) {
        // 优先验证缓存
        if (lastMatch != null && isCacheValid(container, lastMatch)) {
            return lastMatch;
        }

        // 缓存无效，执行完整回溯匹配
        List<Ingredient> ingredients = new ArrayList<>(getIngredients());
        List<Integer> nonEmptySlots = new ArrayList<>();
        for (int i = 0; i < container.size(); i++) {
            if (!container.getItem(i).isEmpty()) {
                nonEmptySlots.add(i);
            }
        }

        if (nonEmptySlots.size() != ingredients.size()) {
            throw new IllegalArgumentException("Item count mismatch: expected " + ingredients.size() +
                    ", got " + nonEmptySlots.size());
        }

        Map<Integer, Ingredient> slotToIngredient = new HashMap<>();
        boolean[] slotUsed = new boolean[container.size()];
        boolean found = backtrackMatch(0, ingredients, nonEmptySlots, slotUsed, slotToIngredient, container);

        if (!found) {
            throw new IllegalArgumentException("Cannot match recipe ingredients");
        }

        // 更新缓存
        lastMatch = slotToIngredient;
        return slotToIngredient;
    }

    /**
     * 验证缓存的匹配结果是否仍适用于当前容器。
     */
    private boolean isCacheValid(CraftingInput container, Map<Integer, Ingredient> cached) {
        // 检查缓存中的每个槽位
        for (Map.Entry<Integer, Ingredient> entry : cached.entrySet()) {
            int slot = entry.getKey();
            if (slot >= container.size())
                return false;
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty())
                return false; // 原本有物品，现在空了
            if (!entry.getValue().test(stack))
                return false; // 物品类型不匹配
        }

        // 检查容器中是否有未被缓存覆盖的非空槽位（即多余物品）
        for (int i = 0; i < container.size(); i++) {
            if (!container.getItem(i).isEmpty() && !cached.containsKey(i)) {
                return false;
            }
        }

        // 确保缓存的槽位数与原料数量一致
        return cached.size() == getIngredients().size();
    }

    // 原回溯方法保持不变，但建议改为 private static（如需供 Shaped 使用，保持 public static）
    public static boolean backtrackMatch(int idx, List<Ingredient> ingredients, List<Integer> slots,
            boolean[] slotUsed, Map<Integer, Ingredient> slotToIngredient,
            CraftingInput container) {
        if (idx == ingredients.size())
            return true;
        Ingredient ingredient = ingredients.get(idx);
        for (int slot : slots) {
            if (!slotUsed[slot] && ingredient.test(container.getItem(slot))) {
                slotUsed[slot] = true;
                slotToIngredient.put(slot, ingredient);
                if (backtrackMatch(idx + 1, ingredients, slots, slotUsed, slotToIngredient, container)) {
                    return true;
                }
                slotUsed[slot] = false;
                slotToIngredient.remove(slot);
            }
        }
        return false;
    }

    // ==================== 剩余物品处理 ====================
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput container) {
        HandlingContextStack context = beginContext();
        try {
            NonNullList<ItemStack> remainingItems = NonNullList.withSize(container.size(), ItemStack.EMPTY);
            Map<Integer, Ingredient> slotToIngredient = matchIngredients(container);

            Player player = CommonHooks.getCraftingPlayer();
            ServerLevel level = null;
            if (player != null && player.level() instanceof ServerLevel serverLevel)
                level = serverLevel;
            if (level == null) {
                var server = ServerLifecycleHooks.getCurrentServer();
                if (server != null)
                    level = server.overworld();
                else
                    return remainingItems; // 客户端预览，跳过消耗逻辑
            }

            for (var entry : slotToIngredient.entrySet()) {
                int slot = entry.getKey();
                ItemStack stack = container.getItem(slot);
                Ingredient ingredient = entry.getValue();
                ItemStack remaining = SelfHandlingIngredient.consume(ingredient, stack, level, player);
                remainingItems.set(slot, remaining);
            }
            return remainingItems;
        } finally {
            endContext(context);
        }
    }

    // ==================== 合成输出处理 ====================
    @Override
    public ItemStack assemble(CraftingInput container, HolderLookup.Provider lookup) {
        HandlingContextStack context = beginContext();
        try {
            ItemStack result = super.assemble(container, lookup);
            if (result.isEmpty()) {
                return result;
            }

            Map<Integer, Ingredient> slotToIngredient = matchIngredients(container);

            for (var entry : slotToIngredient.entrySet()) {
                int slot = entry.getKey();
                ItemStack inputStack = container.getItem(slot);
                Ingredient ingredient = entry.getValue();
                SelfHandlingIngredient.outputModify(ingredient, inputStack, result);
            }
            return result;
        } finally {
            endContext(context);
        }
    }

    // ==================== 序列化 ====================
    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements RecipeSerializer<ShapelessHandlingRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        private static final MapCodec<ShapelessHandlingRecipe> CODEC = RecipeSerializer.SHAPELESS_RECIPE
                .codec().xmap(ShapelessHandlingRecipe::fromVanilla, a -> a);
        private static final StreamCodec<RegistryFriendlyByteBuf, ShapelessHandlingRecipe> STREAM_CODEC = StreamCodec
                .of(
                        (buf, recipe) -> RecipeSerializer.SHAPELESS_RECIPE
                                .streamCodec().encode(buf, recipe),
                        (buf) -> fromVanilla(RecipeSerializer.SHAPELESS_RECIPE
                                .streamCodec().decode(buf)));

        @Override
        public MapCodec<ShapelessHandlingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapelessHandlingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
