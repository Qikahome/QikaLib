package qikahome.qikalib.compat.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI 页面用到的贴图区域。贴图取自 Create 的 JEI widgets（MIT License），
 * 见 <a href="https://github.com/Creators-of-Create/Create/blob/mc1.20.1/dev/LICENSE">Create MIT License</a>。
 */
public enum GuiTextures {
    JEI_DOWN_ARROW(0, 21, 18, 14),
    JEI_SHADOW(0, 56, 52, 11);

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("qikalib",
            "textures/gui/jei/widgets.png");

    private final int startX;
    private final int startY;
    private final int width;
    private final int height;

    GuiTextures(int startX, int startY, int width, int height) {
        this.startX = startX;
        this.startY = startY;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(TEXTURE, x, y, startX, startY, width, height);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
