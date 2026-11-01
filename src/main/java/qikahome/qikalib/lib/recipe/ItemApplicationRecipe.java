package qikahome.qikalib.lib.recipe;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.lib.handling.HandlingContextStack;
import qikahome.qikalib.lib.ingredient.SelfHandlingIngredient;

import static qikahome.qikalib.lib.ingredient.SelfHandlingIngredient.invalidContext;
import static qikahome.qikalib.lib.ingredient.SelfHandlingIngredient.outputModify;
import static qikahome.qikalib.lib.ingredient.SelfHandlingIngredient.setContext;

public class ItemApplicationRecipe implements Recipe<RecipeInput>, IHandlingRecipe {
    public static final Identifier TYPE_ID = Identifier.parse("qikalib:item_application");
    public static final TagKey<Item> TOOL_TAG = TagKey.create(Registries.ITEM,
            Identifier.parse("qikalib:item_application_tool"));

    /**
     * 原版 {@link RecipeType} 需要自行注册进 {@code BuiltInRegistries.RECIPE_TYPE}，
     * 且必须早于注册表冻结；由 {@code QikaLib} 入口显式调用 {@link #register()}。
     */
    public static RecipeType<ItemApplicationRecipe> TYPE;

    public static void register() {
        if (TYPE != null)
            return;
        TYPE = Registry.register(BuiltInRegistries.RECIPE_TYPE, TYPE_ID, new RecipeType<ItemApplicationRecipe>() {
            @Override
            public String toString() {
                return TYPE_ID.toString();
            }
        });
    }

    private static final RecipeBookCategory RECIPE_BOOK_CATEGORY = new RecipeBookCategory();

    /**
     * 客户端同步缓存（由 {@code ClientRecipeSynchronizedEvent} 写入）；服务端恒为 null。
     */
    @Nullable
    private static volatile List<RecipeHolder<ItemApplicationRecipe>> clientRecipes;

    public static void setClientRecipes(@Nullable Collection<RecipeHolder<ItemApplicationRecipe>> recipes) {
        clientRecipes = recipes == null ? null : List.copyOf(recipes);
    }

    /**
     * @return 客户端同步缓存；未同步（或服务端）时为 null。
     */
    @Nullable
    public static List<RecipeHolder<ItemApplicationRecipe>> getClientRecipes() {
        return clientRecipes;
    }

    private final Ingredient block;
    private final Ingredient tool;
    private final ItemStackTemplate result;
    private final boolean dropContainer;
    private final boolean keepBlockState;
    private final boolean updateBlock;
    @Nullable
    private final Boolean sneaking;
    /**
     * 输入强制：直接按方块/方块状态匹配被右键的方块，而非将其转为物品后匹配。
     */
    @Nullable
    private final ForceBlockState forceInput;
    /**
     * 输出强制：指定放置的方块/方块状态，绕过"result 必须是可放置物品且放默认状态"的限制。
     */
    @Nullable
    private final ForceBlockState forceOutput;
    /**
     * 本次配方是否有原料需要匹配上下文（解析后固定）。
     */
    private final boolean requiresContext;

    public ItemApplicationRecipe(Ingredient block, Ingredient tool, ItemStackTemplate result,
            boolean dropContainer, boolean keepBlockState, boolean updateBlock, @Nullable Boolean sneaking,
            @Nullable ForceBlockState forceInput, @Nullable ForceBlockState forceOutput) {
        this.block = block;
        this.tool = tool;
        this.result = result;
        this.dropContainer = dropContainer;
        this.keepBlockState = keepBlockState;
        this.updateBlock = updateBlock;
        this.sneaking = sneaking;
        this.forceInput = forceInput;
        this.forceOutput = forceOutput;
        this.requiresContext = SelfHandlingIngredient.requiresContext(block)
                || SelfHandlingIngredient.requiresContext(tool);
    }

    /**
     * 有原料需要上下文时才建栈并绑定，否则返回 null —— 省掉整套 context 机械。
     */
    @Nullable
    private HandlingContextStack beginContext() {
        if (!requiresContext)
            return null;
        HandlingContextStack context = new HandlingContextStack().push();
        setContext(block, context);
        setContext(tool, context);
        return context;
    }

    private void endContext(@Nullable HandlingContextStack context) {
        if (context == null)
            return;
        invalidContext(block);
        invalidContext(tool);
    }

    /**
     * 输入匹配（方块状态 + 手持工具）。上下文在这里自己绑定、解绑，调用方不用管。
     */
    public boolean test(BlockState state, ItemStack heldTool) {
        HandlingContextStack context = beginContext();
        try {
            Item blockItem = state.getBlock().asItem();
            boolean hasItem = blockItem != Items.AIR;
            if (hasItem && !block.test(new ItemStack(blockItem))) {
                return false;
            }
            if (!hasItem && forceInput == null) {
                return false;
            }
            if (forceInput != null && !forceInput.matches(state)) {
                return false;
            }
            return tool.test(heldTool);
        } finally {
            endContext(context);
        }
    }

    /**
     * 右键交互的完整匹配：潜行条件 + {@link #test(BlockState, ItemStack)}。
     */
    private boolean matchesInteraction(Player player, BlockState state, ItemStack heldTool) {
        Boolean sneaking = getSneaking();
        if (sneaking != null && player.isShiftKeyDown() != sneaking) {
            return false;
        }
        return test(state, heldTool);
    }

    public boolean shouldDropContainer() {
        return dropContainer;
    }

    public boolean shouldKeepBlockState() {
        return keepBlockState;
    }

    public boolean shouldUpdateBlock() {
        return updateBlock;
    }

    @Nullable
    public Boolean getSneaking() {
        return sneaking;
    }

    public Ingredient getBlock() {
        return block;
    }

    public Ingredient getTool() {
        return tool;
    }

    public ItemStack getRecipeResult() {
        return result.create();
    }

    @Nullable
    public ForceBlockState getForceInput() {
        return forceInput;
    }

    @Nullable
    public ForceBlockState getForceOutput() {
        return forceOutput;
    }

    @Override
    public boolean matches(RecipeInput inv, Level level) {
        HandlingContextStack context = beginContext();
        try {
            return block.test(inv.getItem(0)) && tool.test(inv.getItem(1));
        } finally {
            endContext(context);
        }
    }

    @Override
    public ItemStack assemble(RecipeInput inv) {
        return result.create();
    }

    @Override
    public RecipeSerializer<ItemApplicationRecipe> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public RecipeType<ItemApplicationRecipe> getType() {
        return TYPE;
    }

    @Override
    public boolean showNotification() {
        return true;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RECIPE_BOOK_CATEGORY;
    }

    /**
     * 对应 NeoForge 的 {@code PlayerInteractEvent.RightClickBlock}：Fabric 侧等价钩子是
     * {@link UseBlockCallback}，由 {@code QikaLib#onInitialize} 注册。
     *
     * <p>客户端优先用 {@code ClientRecipeSynchronizedEvent} 缓存的同步配方做真实匹配；
     * 缓存尚未就绪时返回 PASS，交给服务端处理。
     */
    public static InteractionResult onRightClickBlock(Player player, Level level, InteractionHand hand,
            BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        BlockPos pos = hit.getBlockPos();
        BlockState blockState = level.getBlockState(pos);

        if (heldItem.isEmpty())
            return InteractionResult.PASS;
        if (blockState.isAir())
            return InteractionResult.PASS;

        if (!heldItem.is(TOOL_TAG))
            return InteractionResult.PASS;

        if (level.isClientSide()) {
            List<RecipeHolder<ItemApplicationRecipe>> recipes = clientRecipes;
            if (recipes == null || recipes.isEmpty())
                return InteractionResult.PASS;
            for (RecipeHolder<ItemApplicationRecipe> holder : recipes) {
                if (holder.value().matchesInteraction(player, blockState, heldItem)) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }

        MinecraftServer server = level.getServer();
        if (server == null)
            return InteractionResult.PASS;

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ItemApplicationRecipe recipe))
                continue;

            if (!recipe.matchesInteraction(player, blockState, heldItem))
                continue;

            level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.PLAYERS, 1, 1.45f);

            recipe.apply((ServerLevel) level, pos, player, hand);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public void apply(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
        HandlingContextStack context = beginContext();
        try {
            ItemStack primaryResult = result.create();
            ItemStack heldItem = player.getItemInHand(hand);
            BlockState oldState = level.getBlockState(pos);

            BlockEntity oldBlockEntity = level.getBlockEntity(pos);
            CompoundTag oldData = oldBlockEntity != null ? oldBlockEntity.saveWithFullMetadata(level.registryAccess())
                    : null;

            // 旧方块作为 nbt_copy 的组件载体：无对应物品时借用 block 原料的代表物品占位
            ItemStack blockCarrier = new ItemStack(oldState.getBlock().asItem());
            if (blockCarrier.isEmpty()) {
                blockCarrier = block.items().map(Holder::value).map(ItemStack::new).findFirst()
                        .orElse(new ItemStack(Blocks.STONE));
            }
            if (oldBlockEntity != null) {
                blockCarrier.set(DataComponents.BLOCK_ENTITY_DATA,
                        TypedEntityData.of(oldBlockEntity.getType(), oldData));
            }

            outputModify(tool, heldItem, primaryResult);
            outputModify(block, blockCarrier, primaryResult);

            // 目标方块：force_output 优先（可指定无物品方块），否则 result 为方块物品时用其方块
            Block targetBlock = null;
            if (forceOutput != null) {
                targetBlock = forceOutput.getBlock();
            }
            if (targetBlock == null && primaryResult.getItem() instanceof BlockItem blockItem) {
                targetBlock = blockItem.getBlock();
            }

            if (targetBlock != null) {
                BlockState newState = targetBlock.defaultBlockState();
                if (keepBlockState) {
                    newState = copyCompatibleProperties(oldState, newState);
                }
                if (forceOutput != null) {
                    newState = forceOutput.applyTo(newState);
                }
                replaceBlock(level, pos, player, newState, primaryResult, oldData);
            } else {
                if (forceOutput != null) {
                    QikaLib.LOGGER.warn(
                            "ItemApplicationRecipe: force_output 指定的方块不存在且 result 不是方块物品，退化为普通物品掉落");
                }
                // 非方块输出：移除旧方块并掉落 result 物品
                if (updateBlock) {
                    if (!level.destroyBlock(pos, false, player, 512)) {
                        return;
                    }
                } else {
                    level.removeBlockEntity(pos);
                    if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)) {
                        restoreBlockEntity(level, pos, oldData);
                        return;
                    }
                }
                Block.popResource(level, pos, primaryResult);
            }

            if (!player.isCreative()) {
                ItemStack consumeResult = SelfHandlingIngredient.consume(tool, heldItem, level, player);
                heldItem.shrink(1);
                if (heldItem.isEmpty()) {
                    player.setItemInHand(hand, consumeResult);
                } else {
                    if (!consumeResult.isEmpty()) {
                        if (ItemStack.isSameItemSameComponents(heldItem, consumeResult)) {
                            int space = heldItem.getMaxStackSize() - heldItem.getCount();
                            if (space > 0) {
                                int toAdd = Math.min(space, consumeResult.getCount());
                                heldItem.grow(toAdd);
                                consumeResult.shrink(toAdd);
                            }
                        }
                        if (!consumeResult.isEmpty()) {
                            if (!player.getInventory().add(consumeResult)) {
                                player.drop(consumeResult, false);
                            }
                        }
                    }
                }
            }
        } finally {
            endContext(context);
        }
    }

    /**
     * 方块型输出统一替换逻辑。
     * <p>
     * drop_container=true：走"破坏"语义——不摘除旧 BE，让旧方块 onRemove（容器会洒出内容物）自然清理；
     * drop_container=false：静默替换——先摘除旧 BE 防止洒出，数据保留完全交给 result 的 nbt_copy。
     * <p>
     * 放置成功后按原版物品放置流程补两步：装载 result 携带的 BE 数据、调用方块 setPlacedBy。
     */
    private void replaceBlock(ServerLevel level, BlockPos pos, Player player, BlockState newState,
            ItemStack placementStack, @Nullable CompoundTag oldData) {
        if (dropContainer) {
            if (updateBlock && !level.destroyBlock(pos, false, player, 512)) {
                return;
            }
            if (!level.setBlock(pos, newState, updateBlock ? 3 : 2)) {
                return;
            }
        } else {
            level.removeBlockEntity(pos);
            if (updateBlock && !level.destroyBlock(pos, false, player, 512)) {
                restoreBlockEntity(level, pos, oldData);
                return;
            }
            if (!level.setBlock(pos, newState, updateBlock ? 3 : 2)) {
                restoreBlockEntity(level, pos, oldData);
                return;
            }
        }

        // 模拟原版物品放置：装载 BE 数据 + setPlacedBy
        BlockItem.updateCustomBlockEntityTag(level, player, pos, placementStack);
        BlockState placedState = level.getBlockState(pos);
        placedState.getBlock().setPlacedBy(level, pos, placedState, player, placementStack);
    }

    /**
     * 回滚：方块移除/替换被拒绝时，在旧方块仍未被替换的前提下恢复其 BE。
     */
    private static void restoreBlockEntity(Level level, BlockPos pos, @Nullable CompoundTag oldData) {
        if (oldData == null)
            return;
        if (level.getBlockEntity(pos) != null)
            return;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !(state.getBlock() instanceof EntityBlock entityBlock))
            return;
        BlockEntity restored = entityBlock.newBlockEntity(pos, state);
        if (restored != null) {
            restored.loadWithComponents(
                    TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), oldData));
            level.setBlockEntity(restored);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static BlockState copyCompatibleProperties(BlockState source, BlockState target) {
        StateDefinition<Block, BlockState> sourceDef = source.getBlock().getStateDefinition();
        for (Property<?> property : sourceDef.getProperties()) {
            if (target.hasProperty(property)) {
                Comparable value = source.getValue((Property) property);
                if (property.getPossibleValues().contains(value)) {
                    target = target.setValue((Property) property, (Comparable) value);
                }
            }
        }
        EnumProperty<Direction> srcDir = findDirectionProperty(sourceDef);
        EnumProperty<Direction> tgtDir = findDirectionProperty(target.getBlock().getStateDefinition());
        if (srcDir != null && tgtDir != null && srcDir != tgtDir) {
            Direction dir = source.getValue(srcDir);
            if (tgtDir.getPossibleValues().contains(dir)) {
                target = target.setValue(tgtDir, dir);
            }
        }
        if (target.hasProperty(BlockStateProperties.OPEN)) {
            target = target.setValue(BlockStateProperties.OPEN, false);
        }
        return target;
    }

    @Nullable
    private static EnumProperty<Direction> findDirectionProperty(StateDefinition<Block, BlockState> def) {
        for (Property<?> property : def.getProperties()) {
            if (property instanceof EnumProperty<?> dirProp) {
                var values = dirProp.getPossibleValues();
                if (!values.isEmpty() && values.iterator().next() instanceof Direction) {
                    @SuppressWarnings("unchecked")
                    EnumProperty<Direction> casted = (EnumProperty<Direction>) dirProp;
                    return casted;
                }
            }
        }
        return null;
    }

    /**
     * 方块状态规格：`{ "block": "...", "properties": { "facing": "north" } }`。
     * block 与 properties 都可省略；属性匹配/应用时只影响已列出的项，未列出的保留原值。
     */
    public static class ForceBlockState {
        @Nullable
        private final Identifier blockId;
        private final LinkedHashMap<String, String> properties;

        public ForceBlockState(@Nullable Identifier blockId, LinkedHashMap<String, String> properties) {
            this.blockId = blockId;
            this.properties = properties;
        }

        public static final Codec<ForceBlockState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("block").forGetter(s -> Optional.ofNullable(s.blockId)),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", Map.of())
                        .forGetter(s -> s.properties))
                .apply(instance, (blockId, properties) -> new ForceBlockState(blockId.orElse(null),
                        new LinkedHashMap<>(properties))));

        /** 解析出的方块；blockId 缺失或注册表无此方块时返回 null。 */
        @Nullable
        public Block getBlock() {
            if (blockId == null)
                return null;
            return BuiltInRegistries.BLOCK.getOptional(ResourceKey.create(Registries.BLOCK, blockId))
                    .filter(block -> block != Blocks.AIR).orElse(null);
        }

        public boolean matches(BlockState state) {
            if (blockId != null) {
                Block block = getBlock();
                if (block == null || state.getBlock() != block) {
                    return false;
                }
            }
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property == null || !valueMatches(state, property, entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        /** 将已列出的属性应用到目标状态；解析失败的属性保持原值。 */
        public BlockState applyTo(BlockState state) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property != null) {
                    state = setPropertyValue(state, property, entry.getValue());
                }
            }
            return state;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static boolean valueMatches(BlockState state, Property property, String value) {
            Optional<?> parsed = property.getValue(value);
            return parsed.isPresent() && state.getValue(property).equals(parsed.get());
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static BlockState setPropertyValue(BlockState state, Property property, String value) {
            Optional<?> parsed = property.getValue(value);
            return parsed.isPresent() ? state.setValue(property, (Comparable) parsed.get()) : state;
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(blockId != null);
            if (blockId != null) {
                buffer.writeUtf(blockId.toString());
            }
            buffer.writeVarInt(properties.size());
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                buffer.writeUtf(entry.getKey());
                buffer.writeUtf(entry.getValue());
            }
        }

        public static ForceBlockState read(RegistryFriendlyByteBuf buffer) {
            Identifier blockId = null;
            if (buffer.readBoolean()) {
                blockId = Identifier.parse(buffer.readUtf());
            }
            LinkedHashMap<String, String> properties = new LinkedHashMap<>();
            int size = buffer.readVarInt();
            for (int i = 0; i < size; i++) {
                properties.put(buffer.readUtf(), buffer.readUtf());
            }
            return new ForceBlockState(blockId, properties);
        }
    }

    public static class Serializer {
        private static final MapCodec<ItemApplicationRecipe> CODEC = RecordCodecBuilder.mapCodec(
                inst -> inst.group(
                        Ingredient.CODEC.fieldOf("block").forGetter(r -> r.block),
                        Ingredient.CODEC.fieldOf("tool").forGetter(r -> r.tool),
                        ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
                        Codec.BOOL.optionalFieldOf("drop_container", true).forGetter(r -> r.dropContainer),
                        Codec.BOOL.optionalFieldOf("keep_block_state", false).forGetter(r -> r.keepBlockState),
                        Codec.BOOL.optionalFieldOf("update_block", true).forGetter(r -> r.updateBlock),
                        Codec.BOOL.optionalFieldOf("sneaking").forGetter(r -> Optional.ofNullable(r.sneaking)),
                        ForceBlockState.CODEC.optionalFieldOf("force_input")
                                .forGetter(r -> Optional.ofNullable(r.forceInput)),
                        ForceBlockState.CODEC.optionalFieldOf("force_output")
                                .forGetter(r -> Optional.ofNullable(r.forceOutput)))
                        .apply(inst,
                                (block, tool, result, drop, keep, update, sneak, forceInput,
                                        forceOutput) -> new ItemApplicationRecipe(block, tool, result, drop, keep,
                                                update, sneak.orElse(null), forceInput.orElse(null),
                                                forceOutput.orElse(null))));

        private static final StreamCodec<RegistryFriendlyByteBuf, ItemApplicationRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork,
                Serializer::fromNetwork);

        public static final RecipeSerializer<ItemApplicationRecipe> INSTANCE = new RecipeSerializer<>(CODEC,
                STREAM_CODEC);
        /** 旧 id {@code jsonmore:item_application} 用的独立实例（Serializer 无状态）。 */
        public static final RecipeSerializer<ItemApplicationRecipe> LEGACY_INSTANCE = new RecipeSerializer<>(CODEC,
                STREAM_CODEC);

        private static void toNetwork(RegistryFriendlyByteBuf buf, ItemApplicationRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.block);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.tool);
            ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result);
            buf.writeBoolean(recipe.dropContainer);
            buf.writeBoolean(recipe.keepBlockState);
            buf.writeBoolean(recipe.updateBlock);
            buf.writeBoolean(recipe.sneaking != null);
            if (recipe.sneaking != null) {
                buf.writeBoolean(recipe.sneaking);
            }
            buf.writeBoolean(recipe.forceInput != null);
            if (recipe.forceInput != null) {
                recipe.forceInput.write(buf);
            }
            buf.writeBoolean(recipe.forceOutput != null);
            if (recipe.forceOutput != null) {
                recipe.forceOutput.write(buf);
            }
        }

        private static ItemApplicationRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            Ingredient block = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient tool = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buf);
            boolean dropContainer = buf.readBoolean();
            boolean keepBlockState = buf.readBoolean();
            boolean updateBlock = buf.readBoolean();
            boolean hasSneaking = buf.readBoolean();
            Boolean sneaking = hasSneaking ? buf.readBoolean() : null;
            boolean hasForceInput = buf.readBoolean();
            ForceBlockState forceInput = hasForceInput ? ForceBlockState.read(buf) : null;
            boolean hasForceOutput = buf.readBoolean();
            ForceBlockState forceOutput = hasForceOutput ? ForceBlockState.read(buf) : null;
            return new ItemApplicationRecipe(block, tool, result, dropContainer, keepBlockState, updateBlock, sneaking,
                    forceInput, forceOutput);
        }
    }
}
