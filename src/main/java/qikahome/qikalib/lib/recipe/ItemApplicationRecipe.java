package qikahome.qikalib.lib.recipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import qikahome.qikalib.QikaLib;
import qikahome.qikalib.lib.handling.HandlingContextStack;
import qikahome.qikalib.lib.ingredient.SelfHandlingIngredient;

@EventBusSubscriber(modid = QikaLib.MODID)
public class ItemApplicationRecipe implements Recipe<RecipeInput>, IHandlingRecipe {
    public static final ResourceLocation TYPE_ID = ResourceLocation.parse("qikalib:item_application");
    public static final RecipeType<ItemApplicationRecipe> TYPE = RecipeType.simple(TYPE_ID);
    public static final TagKey<Item> TOOL_TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.parse("qikalib:item_application_tool"));

    private final Ingredient block;
    private final Ingredient tool;
    private final ItemStack result;
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
     * 本次配方是否有原料需要匹配上下文，由构造方计算后传入（解析后固定）。
     * 为 false 时不建栈、不绑定，省掉整套 context 机械。
     */
    private final boolean requiresContext;

    public ItemApplicationRecipe(Ingredient block, Ingredient tool, ItemStack result,
            boolean dropContainer, boolean keepBlockState, boolean updateBlock, @Nullable Boolean sneaking,
            @Nullable ForceBlockState forceInput, @Nullable ForceBlockState forceOutput, boolean requiresContext) {
        this.block = block;
        this.tool = tool;
        this.result = result;
        this.dropContainer = dropContainer;
        this.keepBlockState = keepBlockState;
        this.updateBlock = updateBlock;
        this.sneaking = sneaking;
        this.forceInput = forceInput;
        this.forceOutput = forceOutput;
        this.requiresContext = requiresContext;
    }

    /**
     * 有原料需要上下文时才建栈并绑定，否则返回 null —— 省掉整套 context 机械。
     * 返回值原样交给 {@link #endContext(HandlingContextStack)}。
     */
    @Nullable
    private HandlingContextStack beginContext() {
        if (!requiresContext)
            return null;
        HandlingContextStack context = new HandlingContextStack().push();
        SelfHandlingIngredient.setContext(block, context);
        SelfHandlingIngredient.setContext(tool, context);
        return context;
    }

    private void endContext(@Nullable HandlingContextStack context) {
        if (context == null)
            return;
        SelfHandlingIngredient.invalidContext(block);
        SelfHandlingIngredient.invalidContext(tool);
    }

    /**
     * 输入匹配（方块状态 + 手持工具）。
     *
     * <p>两条判定共用一个上下文，因此同一个 same_item 分组可以跨 block / tool 约束
     * "必须是同一种物品"。上下文在这里自己绑定、解绑，调用方不用管。
     *
     * <p>匹配规则：
     * - 被右键方块有对应物品时，须通过 block 原料的物品匹配；
     * - 无对应物品（如技术方块）时跳过物品匹配，只能靠 force_input 命中；
     * - force_input 存在时，其声明的方块/属性必须与当前状态一致（未声明的属性不要求）。
     *
     * @param state    被右键的方块状态
     * @param heldTool 手持的工具
     * @return 是否命中
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
        return result;
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
    public ItemStack assemble(RecipeInput inv, HolderLookup.Provider registries) {
        HandlingContextStack context = beginContext();
        try {
            return result.copy();
        } finally {
            endContext(context);
        }
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        ItemStack heldItem = event.getItemStack();
        BlockPos pos = event.getPos();
        BlockState blockState = level.getBlockState(pos);

        if (heldItem.isEmpty())
            return;
        if (blockState.isAir())
            return;
        if (event.isCanceled())
            return;

        if (!heldItem.is(TOOL_TAG))
            return;

        Player player = event.getEntity();

        for (var holder : level.getRecipeManager().getAllRecipesFor(TYPE)) {
            var recipe = holder.value();
            Boolean sneaking = recipe.getSneaking();
            if (sneaking != null && player.isShiftKeyDown() != sneaking)
                continue;

            if (!recipe.test(blockState, heldItem))
                continue;

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);

            if (level.isClientSide())
                return;

            level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.PLAYERS, 1, 1.45f);

            recipe.apply((ServerLevel) level, pos, player, event.getHand());
            return;
        }
    }

    public void apply(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
        HandlingContextStack context = beginContext();
        try {
            ItemStack primaryResult = result.copy();
            ItemStack heldItem = player.getItemInHand(hand);
            BlockState oldState = level.getBlockState(pos);
            var registries = level.registryAccess();

            BlockEntity oldBlockEntity = level.getBlockEntity(pos);
            CompoundTag oldData = oldBlockEntity != null ? oldBlockEntity.saveWithFullMetadata(registries) : null;

            // 旧方块作为 nbt_copy 的 NBT 载体：无对应物品时借用 block 原料的代表物品占位（nbt_copy 只读组件，不校验物品）
            ItemStack blockCarrier = new ItemStack(oldState.getBlock().asItem());
            if (blockCarrier.isEmpty()) {
                ItemStack[] blockItems = block.getItems();
                blockCarrier = blockItems.length > 0 ? new ItemStack(blockItems[0].getItem())
                        : new ItemStack(Blocks.STONE);
            }
            if (oldBlockEntity != null) {
                blockCarrier.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(oldData));
            }

            SelfHandlingIngredient.outputModify(tool, heldItem, primaryResult);
            SelfHandlingIngredient.outputModify(block, blockCarrier, primaryResult);

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
                    if (!level.destroyBlock(pos, false)) {
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
            if (updateBlock && !level.destroyBlock(pos, false)) {
                return;
            }
            if (!level.setBlock(pos, newState, updateBlock ? 3 : 2)) {
                return;
            }
        } else {
            level.removeBlockEntity(pos);
            if (updateBlock && !level.destroyBlock(pos, false)) {
                restoreBlockEntity(level, pos, oldData);
                return;
            }
            if (!level.setBlock(pos, newState, updateBlock ? 3 : 2)) {
                restoreBlockEntity(level, pos, oldData);
                return;
            }
        }

        // 模拟原版物品放置：装载 BE 数据 + setPlacedBy
        loadBlockEntityData(level, player, pos, placementStack);
        BlockState placedState = level.getBlockState(pos);
        placedState.getBlock().setPlacedBy(level, pos, placedState, player, placementStack);
    }

    /** 读取放置栈的 BLOCK_ENTITY_DATA 组件，按原版"带 NBT 放置"语义合并装载进新 BE。 */
    private static void loadBlockEntityData(Level level, @Nullable Player player, BlockPos pos, ItemStack stack) {
        CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData == null)
            return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null)
            return;
        var registries = level.registryAccess();
        CompoundTag existing = blockEntity.saveWithoutMetadata(registries);
        existing.merge(customData.copyTag());
        blockEntity.loadWithComponents(existing, registries);
        blockEntity.setChanged();
    }

    /**
     * 回滚：方块移除/替换被拒绝时，在旧方块仍未被替换的前提下恢复其 BE。
     * 使用当前方块自身的 EntityBlock#newBlockEntity 创建 BE，避免 loadStatic 依据
     * 旧数据里的 BE 类型创建出与当前方块不匹配的实体。
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
            restored.loadWithComponents(oldData, level.registryAccess());
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
        DirectionProperty srcDir = findDirectionProperty(sourceDef);
        DirectionProperty tgtDir = findDirectionProperty(target.getBlock().getStateDefinition());
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
    private static DirectionProperty findDirectionProperty(StateDefinition<Block, BlockState> def) {
        for (Property<?> property : def.getProperties()) {
            if (property instanceof DirectionProperty dirProp) {
                return dirProp;
            }
        }
        return null;
    }

    /**
     * 方块状态规格：`{ "block": "...", "properties": { "facing": "north" } }`。
     * block 与 properties 都可省略；属性匹配/应用时只影响已列出的项，未列出的保留原值。
     * properties 中的值统一按字符串解析。
     */
    public static class ForceBlockState {
        @Nullable
        private final ResourceLocation blockId;
        private final LinkedHashMap<String, String> properties;

        public ForceBlockState(@Nullable ResourceLocation blockId, LinkedHashMap<String, String> properties) {
            this.blockId = blockId;
            this.properties = properties;
        }

        public static final Codec<ForceBlockState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("block").forGetter(s -> Optional.ofNullable(s.blockId)),
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

        public void write(FriendlyByteBuf buffer) {
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

        public static ForceBlockState read(FriendlyByteBuf buffer) {
            ResourceLocation blockId = null;
            if (buffer.readBoolean()) {
                blockId = ResourceLocation.parse(buffer.readUtf());
            }
            LinkedHashMap<String, String> properties = new LinkedHashMap<>();
            int size = buffer.readVarInt();
            for (int i = 0; i < size; i++) {
                properties.put(buffer.readUtf(), buffer.readUtf());
            }
            return new ForceBlockState(blockId, properties);
        }
    }

    public static class Serializer implements RecipeSerializer<ItemApplicationRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        private static final MapCodec<ItemApplicationRecipe> CODEC = RecordCodecBuilder.mapCodec(
                inst -> inst.group(
                        Ingredient.CODEC.fieldOf("block").forGetter(r -> r.block),
                        Ingredient.CODEC.fieldOf("tool").forGetter(r -> r.tool),
                        ItemStack.CODEC.fieldOf("result").forGetter(r -> r.result),
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
                                                forceOutput.orElse(null),
                                                SelfHandlingIngredient.requiresContext(block)
                                                        || SelfHandlingIngredient.requiresContext(tool))));

        private static final StreamCodec<RegistryFriendlyByteBuf, ItemApplicationRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork,
                Serializer::fromNetwork);

        @Override
        public MapCodec<ItemApplicationRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ItemApplicationRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, ItemApplicationRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.block);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.tool);
            ItemStack.STREAM_CODEC.encode(buf, recipe.result);
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
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
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
                    forceInput, forceOutput,
                    SelfHandlingIngredient.requiresContext(block) || SelfHandlingIngredient.requiresContext(tool));
        }
    }
}
