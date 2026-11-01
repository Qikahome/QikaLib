package qikahome.qikalib.compat.jei;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import qikahome.qikalib.lib.recipe.ItemApplicationRecipe;

/**
 * item_application 的 JEI 展示页：方块 + 工具 → 结果，并标出潜行要求。
 * 纯展示，不影响配方逻辑。
 */
public class ItemApplicationRecipeCategory implements IRecipeCategory<ItemApplicationRecipe> {
    public static final RecipeType<ItemApplicationRecipe> TYPE = RecipeType.create("qikalib", "item_application",
            ItemApplicationRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;

    public ItemApplicationRecipeCategory(IGuiHelper guiHelper) {
        this.slot = guiHelper.getSlotDrawable();
        this.background = guiHelper.createBlankDrawable(177, 60);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Items.PAPER));
    }

    @Override
    public RecipeType<ItemApplicationRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("recipe.qikalib.item_application");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ItemApplicationRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 38)
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.getBlock());

        builder.addSlot(RecipeIngredientRole.INPUT, 51, 5)
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.getTool());

        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 38)
                .setBackground(slot, -1, -1)
                .addItemStack(recipe.getRecipeResult());
    }

    @Override
    public void draw(ItemApplicationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX,
            double mouseY) {
        GuiTextures.JEI_SHADOW.render(graphics, 62, 47);
        GuiTextures.JEI_DOWN_ARROW.render(graphics, 74, 10);

        ItemStack[] blockItems = recipe.getBlock().getItems();
        if (blockItems.length > 0) {
            ItemStack blockStack = blockItems[0];
            if (blockStack.getItem() instanceof BlockItem) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(74, 28, 0);
                pose.scale(1.7f, 1.7f, 1.7f);
                graphics.renderItem(blockStack, 0, 0);
                pose.popPose();
            }
        }

        Boolean sneaking = recipe.getSneaking();
        if (sneaking != null) {
            MutableComponent text = Component
                    .translatable("recipe.qikalib.item_application.sneaking." + (sneaking ? "true" : "false"));
            graphics.drawString(Minecraft.getInstance().font, text.withStyle(ChatFormatting.BOLD), 100, 8, 0xFFFFFF,
                    false);
        }
    }
}
