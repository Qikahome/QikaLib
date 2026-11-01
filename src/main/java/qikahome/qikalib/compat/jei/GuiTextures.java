package qikahome.qikalib.compat.jei;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * GUI texture references based on Create's JEI widgets texture sheet.
 *
 * Original source: Create (https://github.com/Creators-of-Create/Create)
 * Licensed under MIT License.
 *
 * @see <a href="https://github.com/Creators-of-Create/Create/blob/mc1.20.1/dev/LICENSE">Create MIT License</a>
 */
public enum GuiTextures {
    JEI_SLOT(0, 0, 18, 18),
    JEI_CHANCE_SLOT(20, 156, 18, 18),
    JEI_CATALYST_SLOT(0, 156, 18, 18),
    JEI_ARROW(19, 10, 42, 10),
    JEI_DOWN_ARROW(0, 21, 18, 14),
    JEI_LIGHT(0, 42, 52, 11),
    JEI_QUESTION_MARK(0, 178, 12, 16),
    JEI_SHADOW(0, 56, 52, 11),
    BLOCKZAPPER_UPGRADE_RECIPE(0, 75, 144, 66),
    JEI_HEAT_BAR(0, 201, 169, 19),
    JEI_NO_HEAT_BAR(0, 221, 169, 19);

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("qikalib",
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

    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, startX, startY, width, height, 256, 256);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
