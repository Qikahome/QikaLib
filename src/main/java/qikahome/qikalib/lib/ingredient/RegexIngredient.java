package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 按物品注册名匹配的原料。
 *
 * <p>三个匹配字段都是正则、都是整串匹配（{@code matches()}，需要"包含"语义时自行写 {@code .*}），
 * 可任选其一或组合使用，同时给出时需全部满足：
 * <ul>
 *   <li>{@code pattern}：匹配完整的 {@code 命名空间:路径}</li>
 *   <li>{@code namespace}：只匹配命名空间（常用于"某个模组的全部物品"）</li>
 *   <li>{@code path}：只匹配路径部分</li>
 * </ul>
 *
 * <p>{@code expand_items}（默认 {@code false}）控制展示：为 {@code true} 时首次
 * {@link #getItems()} 会遍历物品注册表、缓存全部匹配物品；否则只给一条代表栈。
 * 两种模式下 {@code test()} 都是现场取注册名匹配，只预编译 {@link Pattern}——
 * 不做匹配集缓存，避免物品注册表尚未就绪（thingpack 动态物品）时的缓存失效问题。
 */
public class RegexIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = new ResourceLocation("qikalib:regex");

    @Nullable
    private final String patternRaw;
    @Nullable
    private final String namespaceRaw;
    @Nullable
    private final String pathRaw;
    private final boolean expandItems;

    @Nullable
    private final Pattern pattern;
    @Nullable
    private final Pattern namespacePattern;
    @Nullable
    private final Pattern pathPattern;

    private final String description;

    private RegexIngredient(@Nullable String pattern, @Nullable String namespace, @Nullable String path,
            boolean expandItems) {
        super(Stream.empty());
        if (pattern == null && namespace == null && path == null) {
            throw new JsonParseException("Regex ingredient needs at least one of 'pattern', 'namespace', 'path'");
        }
        this.patternRaw = pattern;
        this.namespaceRaw = namespace;
        this.pathRaw = path;
        this.expandItems = expandItems;
        this.pattern = compile(pattern, "pattern");
        this.namespacePattern = compile(namespace, "namespace");
        this.pathPattern = compile(path, "path");
        this.description = describe(pattern, namespace, path);
    }

    @Nullable
    private static Pattern compile(@Nullable String regex, String field) {
        if (regex == null) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new JsonParseException("Invalid '" + field + "' regex: " + e.getDescription(), e);
        }
    }

    private static String describe(@Nullable String pattern, @Nullable String namespace, @Nullable String path) {
        StringBuilder builder = new StringBuilder();
        if (pattern != null) {
            builder.append(pattern);
        }
        if (namespace != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("namespace=").append(namespace);
        }
        if (path != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("path=").append(path);
        }
        return builder.toString();
    }

    private boolean matches(ResourceLocation id) {
        if (pattern != null && !pattern.matcher(id.toString()).matches()) {
            return false;
        }
        if (namespacePattern != null && !namespacePattern.matcher(id.getNamespace()).matches()) {
            return false;
        }
        if (pathPattern != null && !pathPattern.matcher(id.getPath()).matches()) {
            return false;
        }
        return true;
    }

    @Nullable
    private ItemStack[] representativeStacks = null;
    @Nullable
    private ItemStack[] expandedStacks = null;

    @Override
    public ItemStack[] getItems() {
        if (expandItems) {
            if (expandedStacks == null) {
                List<ItemStack> matched = new ArrayList<>();
                for (Item item : ForgeRegistries.ITEMS) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id != null && matches(id)) {
                        matched.add(new ItemStack(item));
                    }
                }
                // 空结果可能是注册表尚未就绪，留空以便下次调用重试
                if (!matched.isEmpty()) {
                    expandedStacks = matched.toArray(new ItemStack[0]);
                }
            }
            if (expandedStacks != null) {
                return expandedStacks.clone();
            }
        }
        if (representativeStacks == null) {
            // 匹配集可能极大，只展示一条代表栈，并把规则本身写进名称，让玩家能看出实际接受范围。
            ItemStack stack = new ItemStack(Items.NAME_TAG);
            stack.setHoverName(Component.translatable("ingredient.qikalib.regex", description));
            representativeStacks = new ItemStack[] { stack };
        }
        return representativeStacks.clone();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && matches(id);
    }

    @Override
    public boolean isSimple() {
        // 匹配始终走 test()（即使用户开了 expand_items，展开结果也只是展示用），
        // 因此这里必须为 false，避免 Forge 改用 getItems() 展开参与配方记账。
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        if (patternRaw != null) {
            json.addProperty("pattern", patternRaw);
        }
        if (namespaceRaw != null) {
            json.addProperty("namespace", namespaceRaw);
        }
        if (pathRaw != null) {
            json.addProperty("path", pathRaw);
        }
        if (expandItems) {
            json.addProperty("expand_items", true);
        }
        return json;
    }

    public static class Serializer implements IIngredientSerializer<RegexIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public RegexIngredient parse(FriendlyByteBuf buffer) {
            boolean expandItems = buffer.readBoolean();
            String pattern = readNullable(buffer);
            String namespace = readNullable(buffer);
            String path = readNullable(buffer);
            return new RegexIngredient(pattern, namespace, path, expandItems);
        }

        @Override
        public RegexIngredient parse(JsonObject json) {
            String pattern = json.has("pattern") ? json.get("pattern").getAsString() : null;
            String namespace = json.has("namespace") ? json.get("namespace").getAsString() : null;
            String path = json.has("path") ? json.get("path").getAsString() : null;
            boolean expandItems = json.has("expand_items") && json.get("expand_items").getAsBoolean();
            return new RegexIngredient(pattern, namespace, path, expandItems);
        }

        @Override
        public void write(FriendlyByteBuf buffer, RegexIngredient ingredient) {
            buffer.writeBoolean(ingredient.expandItems);
            writeNullable(buffer, ingredient.patternRaw);
            writeNullable(buffer, ingredient.namespaceRaw);
            writeNullable(buffer, ingredient.pathRaw);
        }

        private static void writeNullable(FriendlyByteBuf buffer, @Nullable String value) {
            buffer.writeBoolean(value != null);
            if (value != null) {
                buffer.writeUtf(value);
            }
        }

        @Nullable
        private static String readNullable(FriendlyByteBuf buffer) {
            return buffer.readBoolean() ? buffer.readUtf() : null;
        }
    }
}
