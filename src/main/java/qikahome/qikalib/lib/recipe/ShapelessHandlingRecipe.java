package qikahome.qikalib.lib.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import qikahome.qikalib.lib.handling.HandlingContextStack;
import qikahome.qikalib.lib.ingredient.SelfHandlingIngredient;

public class ShapelessHandlingRecipe extends ShapelessRecipe implements IHandlingRecipe {

    // 缓存上一次的匹配结果，transient 避免序列化
    private volatile transient Map<Integer, Ingredient> lastMatch;

    /** 是否有原料需要匹配上下文（解析后固定）；不需要时整套 context 机械都跳过。 */
    private final boolean requiresContext;

    public ShapelessHandlingRecipe(ResourceLocation id, String group, CraftingBookCategory category,
            ItemStack result, NonNullList<Ingredient> ingredients) {
        super(id, group, category, result, ingredients);
        this.requiresContext = getIngredients().stream().anyMatch(SelfHandlingIngredient::requiresContext);
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
    public boolean matches(CraftingContainer container, Level level) {
        HandlingContextStack context = beginContext();
        try {
            return super.matches(container, level);
        } finally {
            endContext(context);
        }
    }

    // ==================== 共用匹配逻辑（带缓存） ====================
    private Map<Integer, Ingredient> matchIngredients(CraftingContainer container) {
        // 优先验证缓存
        if (lastMatch != null && isCacheValid(container, lastMatch)) {
            return lastMatch;
        }

        // 缓存无效，执行完整回溯匹配
        List<Ingredient> ingredients = new ArrayList<>(getIngredients());
        List<Integer> nonEmptySlots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                nonEmptySlots.add(i);
            }
        }

        if (nonEmptySlots.size() != ingredients.size()) {
            throw new IllegalArgumentException("Item count mismatch: expected " + ingredients.size() +
                    ", got " + nonEmptySlots.size());
        }

        Map<Integer, Ingredient> slotToIngredient = new HashMap<>();
        boolean[] slotUsed = new boolean[container.getContainerSize()];
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
    private boolean isCacheValid(CraftingContainer container, Map<Integer, Ingredient> cached) {
        // 检查缓存中的每个槽位
        for (Map.Entry<Integer, Ingredient> entry : cached.entrySet()) {
            int slot = entry.getKey();
            if (slot >= container.getContainerSize())
                return false;
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty())
                return false; // 原本有物品，现在空了
            if (!entry.getValue().test(stack))
                return false; // 物品类型不匹配
        }

        // 检查容器中是否有未被缓存覆盖的非空槽位（即多余物品）
        for (int i = 0; i < container.getContainerSize(); i++) {
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
            CraftingContainer container) {
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
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        HandlingContextStack context = beginContext();
        try {
            NonNullList<ItemStack> remainingItems = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
            Map<Integer, Ingredient> slotToIngredient = matchIngredients(container);

            for (var entry : slotToIngredient.entrySet()) {
                int slot = entry.getKey();
                ItemStack stack = container.getItem(slot);
                Ingredient ingredient = entry.getValue();
                ItemStack remaining = SelfHandlingIngredient.consume(ingredient, stack);
                remainingItems.set(slot, remaining);
            }
            return remainingItems;
        } finally {
            endContext(context);
        }
    }

    // ==================== 合成输出处理 ====================
    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        HandlingContextStack context = beginContext();
        try {
            ItemStack result = super.assemble(container, registryAccess);
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

    // ==================== 序列化（不变） ====================
    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements RecipeSerializer<ShapelessHandlingRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public ShapelessHandlingRecipe fromJson(ResourceLocation id, JsonObject json) {
            ShapelessRecipe recipe = net.minecraft.world.item.crafting.RecipeSerializer.SHAPELESS_RECIPE.fromJson(id,
                    json);
            return new ShapelessHandlingRecipe(recipe.getId(), recipe.getGroup(), recipe.category(),
                    recipe.getResultItem(RegistryAccess.EMPTY), recipe.getIngredients());
        }

        @Override
        public ShapelessHandlingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            ShapelessRecipe recipe = net.minecraft.world.item.crafting.RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id,
                    buffer);
            return new ShapelessHandlingRecipe(recipe.getId(), recipe.getGroup(), recipe.category(),
                    recipe.getResultItem(RegistryAccess.EMPTY), recipe.getIngredients());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ShapelessHandlingRecipe recipe) {
            net.minecraft.world.item.crafting.RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
