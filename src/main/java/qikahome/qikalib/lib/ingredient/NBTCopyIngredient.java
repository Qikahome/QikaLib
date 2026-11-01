package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.IdentifierException;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 把匹配到的输入物品的组件（1.21+ 的 DataComponent，旧称 NBT）复制到产物上。
 *
 * <p>{@code tags} 是组件 id 列表：{@code *} 表示全部，{@code !id} 表示排除；
 * {@code mode} 支持 {@code REPLACE} / {@code REPLACE_ALL} / {@code MERGE_TARGET_FIRST} / {@code MERGE_SOURCE_FIRST}。
 */
public class NBTCopyIngredient extends SelfHandlingIngredient {
    public static final Identifier ID = Identifier.parse("qikalib:nbt_copy");

    public static final MapCodec<NBTCopyIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    Codec.STRING.optionalFieldOf("mode", Mode.REPLACE.name())
                            .forGetter(i -> i.mode.name()),
                    Codec.STRING.listOf()
                            .flatXmap(NBTCopyIngredient::validateTags, DataResult::success)
                            .optionalFieldOf("tags", List.of()).forGetter(i -> i.tags))
                    .apply(v, (ingredient, mode, tags) -> new NBTCopyIngredient(ingredient,
                            Mode.valueOf(mode.toUpperCase(java.util.Locale.ROOT)),
                            tags.isEmpty() ? null : tags)));
    public static final CustomIngredientSerializer<NBTCopyIngredient> SERIALIZER = new SimpleIngredientSerializer<>(ID,
            CODEC);

    public enum Mode {
        REPLACE_ALL,
        REPLACE,
        MERGE_TARGET_FIRST,
        MERGE_SOURCE_FIRST,
    }

    private static DataResult<List<String>> validateTags(List<String> tags) {
        for (String tag : tags) {
            if (tag.equals("*"))
                continue;
            String id = tag.startsWith("!") ? tag.substring(1) : tag;
            try {
                Identifier.parse(id);
            } catch (IdentifierException e) {
                return DataResult.error(() -> "Invalid component id '" + id + "' in nbt_copy tags: " + e.getMessage());
            }
        }
        return DataResult.success(tags);
    }

    private final Mode mode;
    @Nullable
    private final List<String> tags;
    private final boolean hasWildcard;
    private final List<Identifier> includes = new ArrayList<>();
    private final List<Identifier> excludes = new ArrayList<>();

    public NBTCopyIngredient(Ingredient ingredient, Mode mode) {
        this(ingredient, mode, null);
    }

    public NBTCopyIngredient(Ingredient ingredient, Mode mode, @Nullable List<String> tags) {
        super(ingredient, false);
        this.mode = mode;
        this.tags = tags;

        boolean wildcard = false;
        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                if (tag.equals("*")) {
                    wildcard = true;
                } else if (tag.startsWith("!")) {
                    excludes.add(Identifier.parse(tag.substring(1)));
                } else {
                    includes.add(Identifier.parse(tag));
                }
            }
        }
        this.hasWildcard = wildcard;
    }

    @Override
    public void outputModify(ItemStack matched, ItemStack output) {
        if (matched.isEmpty() || output.isEmpty())
            return;
        copyComponents(output, matched);
    }

    public void copyComponents(ItemStack target, ItemStack source) {
        if (target.isEmpty() || source.isEmpty())
            return;

        DataComponentPatch sourcePatch = source.getComponentsPatch();

        if (mode == Mode.REPLACE || mode == Mode.REPLACE_ALL) {
            for (DataComponentType<?> type : new ArrayList<>(target.getComponents().keySet())) {
                target.remove(type);
            }
        }

        for (var entry : sourcePatch.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            if (mode != Mode.REPLACE_ALL && !isIncluded(type))
                continue;
            Optional<?> value = entry.getValue();
            if (value.isPresent()) {
                if (mode == Mode.MERGE_TARGET_FIRST && target.get(type) != null)
                    continue;
                setUnchecked(target, type, value.get());
            } else if (mode == Mode.REPLACE_ALL || mode == Mode.MERGE_SOURCE_FIRST) {
                target.remove(type);
            }
        }
    }

    private boolean isIncluded(DataComponentType<?> type) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (!hasWildcard && !includes.isEmpty() && !includes.contains(id))
            return false;
        return id == null || !excludes.contains(id);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void setUnchecked(ItemStack target, DataComponentType type, Object value) {
        target.set(type, value);
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
