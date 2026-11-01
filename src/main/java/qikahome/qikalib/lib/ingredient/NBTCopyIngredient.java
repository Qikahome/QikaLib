package qikahome.qikalib.lib.ingredient;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ResourceLocationException;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import qikahome.qikalib.QikaLib;

public class NBTCopyIngredient extends SelfHandlingIngredient {
    public static final ResourceLocation ID = ResourceLocation.parse("qikalib:nbt_copy");

    public static final MapCodec<NBTCopyIngredient> CODEC = RecordCodecBuilder.mapCodec(
            v -> v.group(
                    getIngredientField(),
                    Codec.STRING.xmap(Mode::valueOf, Mode::name).fieldOf("mode").forGetter(i -> i.mode),
                    TagFilter.CODEC.optionalFieldOf("tags", TagFilter.EMPTY).forGetter(i -> i.tagFilter))
                    .apply(v, NBTCopyIngredient::new));
    public static final DeferredHolder<IngredientType<?>, IngredientType<NBTCopyIngredient>> TYPE = QikaLib.INGREDIENT_TYPES
            .register(ID.getPath(), NBTCopyIngredient::createType);
    public static final DeferredHolder<IngredientType<?>, IngredientType<NBTCopyIngredient>> LEGACY_TYPE = QikaLib.LEGACY_INGREDIENT_TYPES
            .register(ID.getPath(), NBTCopyIngredient::createType);

    public enum Mode {
        REPLACE_ALL,
        REPLACE,
        MERGE_TARGET_FIRST,
        MERGE_SOURCE_FIRST,
    }

    /**
     * 已解析的 tag 过滤配置。解析在 codec 层完成，非法路径会变成
     * {@link DataResult#error}，而不是在构造时抛出异常中断数据包加载。
     */
    private record TagFilter(List<String> raw, boolean hasWildcard, List<ResourceLocation> includes,
            List<ResourceLocation> excludes) {
        static final TagFilter EMPTY = new TagFilter(List.of(), false, List.of(), List.of());

        static final Codec<TagFilter> CODEC = Codec.STRING.listOf().flatXmap(TagFilter::parse,
                filter -> DataResult.success(filter.raw()));

        private static DataResult<TagFilter> parse(List<String> tags) {
            boolean hasWildcard = false;
            List<ResourceLocation> includes = new ArrayList<>();
            List<ResourceLocation> excludes = new ArrayList<>();
            for (String tag : tags) {
                if (tag.equals("*")) {
                    hasWildcard = true;
                    continue;
                }
                boolean excluded = tag.startsWith("!");
                String path = excluded ? tag.substring(1) : tag;
                try {
                    ResourceLocation location = ResourceLocation.parse(path);
                    (excluded ? excludes : includes).add(location);
                } catch (ResourceLocationException e) {
                    return DataResult.error(() -> "Invalid tag path \"" + tag + "\": " + e.getMessage());
                }
            }
            return DataResult.success(new TagFilter(List.copyOf(tags), hasWildcard,
                    List.copyOf(includes), List.copyOf(excludes)));
        }
    }

    private final Mode mode;
    private final TagFilter tagFilter;
    private boolean hasWildcard = false;
    final List<ResourceLocation> includes = new ArrayList<>();
    final List<ResourceLocation> excludes = new ArrayList<>();

    public NBTCopyIngredient(Ingredient ingredient, Mode mode) {
        this(ingredient, mode, TagFilter.EMPTY);
    }

    private NBTCopyIngredient(Ingredient ingredient, Mode mode, TagFilter tagFilter) {
        super(ingredient, false);
        this.mode = mode;
        this.tagFilter = tagFilter;
        this.hasWildcard = tagFilter.hasWildcard();
        this.includes.addAll(tagFilter.includes());
        this.excludes.addAll(tagFilter.excludes());
    }

    public static IngredientType<NBTCopyIngredient> createType() {
        return new IngredientType<>(CODEC);
    }

    @Override
    public void outputModify(ItemStack matched, ItemStack output) {
        if (matched.isEmpty() || output.isEmpty())
            return;
        copyNBT(output, matched);
    }

    public void copyNBT(ItemStack target, ItemStack source) {
        if (target.isEmpty() || source.isEmpty())
            return;

        var sourceNBT = source.getComponentsPatch();

        switch (mode) {
            case REPLACE -> {
                var targetPatch = target.getComponentsPatch();
                targetPatch.forget(any -> true);
                mergeAllFiltered(target, sourceNBT, true);
            }
            case MERGE_TARGET_FIRST -> {
                mergeAllFiltered(target, sourceNBT, false);
            }
            case MERGE_SOURCE_FIRST -> {
                mergeAllFiltered(target, sourceNBT, true);
            }
            case REPLACE_ALL -> {
                var targetPatch = target.getComponentsPatch();
                targetPatch.forget(any -> true);
                for (var key : source.getComponents().keySet()) {
                    target.set((DataComponentType) key, source.get(key));
                }
            }
        }
    }

    private void mergeAllFiltered(ItemStack target, DataComponentPatch source, boolean sourceWins) {
        var targetPatch = target.getComponentsPatch();
        for (var entry : source.entrySet()) {
            DataComponentType key = entry.getKey();
            ResourceLocation keyName = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(key);
            if (this.hasWildcard || this.includes.isEmpty() || this.includes.contains(keyName)) {
                if (!this.excludes.contains(keyName)) {
                    if (sourceWins || targetPatch.entrySet().stream().noneMatch(e -> e.getKey() == key)) {
                        entry.getValue().ifPresent(val -> target.set(key, val));
                    }
                }
            }
        }
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE.get();
    }

    public static void register() {
    }
}
