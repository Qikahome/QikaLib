package qikahome.qikalib.compat.jei;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
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
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import qikahome.qikalib.lib.recipe.ItemApplicationRecipe;

public class ItemApplicationRecipeCategory implements IRecipeCategory<ItemApplicationRecipe> {
    public static final IRecipeType<ItemApplicationRecipe> TYPE = IRecipeType.create("qikalib",
            "item_application", ItemApplicationRecipe.class);

    private final IDrawable icon;
    private final IDrawable slot;

    public ItemApplicationRecipeCategory(IGuiHelper guiHelper) {
        this.slot = guiHelper.getSlotDrawable();
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Items.PAPER));
    }

    @Override
    public IRecipeType<ItemApplicationRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("recipe.qikalib.item_application");
    }

    @Override
    public int getWidth() {
        return 177;
    }

    @Override
    public int getHeight() {
        return 60;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ItemApplicationRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 38)
                .setBackground(slot, -1, -1)
                .add(recipe.getBlock());

        builder.addSlot(RecipeIngredientRole.INPUT, 51, 5)
                .setBackground(slot, -1, -1)
                .add(recipe.getTool());

        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 38)
                .setBackground(slot, -1, -1)
                .add(recipe.getRecipeResult());
    }

    @Override
    public void draw(ItemApplicationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor graphics,
            double mouseX, double mouseY) {
        GuiTextures.JEI_SHADOW.render(graphics, 62, 47);
        GuiTextures.JEI_DOWN_ARROW.render(graphics, 74, 10);

        ItemStack[] blockItems = recipe.getBlock().items()
                .map(Holder::value)
                .map(ItemStack::new)
                .toArray(ItemStack[]::new);
        if (blockItems.length > 0) {
            ItemStack blockStack = blockItems[0];
            if (blockStack.getItem() instanceof BlockItem) {
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(74, 28);
                pose.scale(1.7f, 1.7f);
                graphics.item(blockStack, 0, 0);
                pose.popMatrix();
            }
        }

        Boolean sneaking = recipe.getSneaking();
        if (sneaking != null) {
            MutableComponent text = Component
                    .translatable("recipe.qikalib.item_application.sneaking." + (sneaking ? "true" : "false"));
            graphics.text(Minecraft.getInstance().font, text.withStyle(ChatFormatting.BOLD), 100, 8, 0xFFFFFF, false);
        }
    }
}
