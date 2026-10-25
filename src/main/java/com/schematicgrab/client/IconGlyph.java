package com.schematicgrab.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 工具栏图标，直接取自机械动力的图标集（icons.png）。
 * 这样能保证图标是 Create 原生像素画，风格与蓝图桌完全一致。
 * <p>
 * 图标集为 16x16 网格，每个图标 16px。
 */
public abstract class IconGlyph
{
    /** Create 原版图标集，复制到本模组资源中以避免跨模组路径依赖 */
    private static final ResourceLocation ICON_ATLAS =
            new ResourceLocation("schematicgrab", "textures/gui/create_icons.png");
    private static final int ICON_SIZE = 16;
    private static final int ATLAS_SIZE = 256;

    /** 图标在图集中的列/行（像素坐标除以 16） */
    protected final int col;
    protected final int row;

    protected IconGlyph(int col, int row)
    {
        this.col = col;
        this.row = row;
    }

    /**
     * 在按钮区域内居中绘制图标。
     *
     * @param areaSize 按钮边长（GUI 像素）
     * @param alpha    透明度 0-255
     */
    public void draw(GuiGraphics graphics, int x, int y, int areaSize, int alpha)
    {
        int iconSize = Math.max(8, areaSize - 6);
        int ox = x + (areaSize - iconSize) / 2;
        int oy = y + (areaSize - iconSize) / 2;

        float a = Math.min(1.0F, alpha / 255.0F);

        graphics.setColor(1.0F, 1.0F, 1.0F, a);
        graphics.blit(ICON_ATLAS,
                ox, oy, iconSize, iconSize,
                col * ICON_SIZE, row * ICON_SIZE,
                ICON_SIZE, ICON_SIZE,
                ATLAS_SIZE, ATLAS_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 后退：图集 r9c0 的「‹」 */
    public static class Back extends IconGlyph
    {
        public Back()
        {
            super(0, 9);
        }
    }

    /** 首页：图集 r1c2 的「列表/文档」图标，表示回到蓝图库列表 */
    public static class Home extends IconGlyph
    {
        public Home()
        {
            super(2, 1);
        }
    }

    /** 刷新：图集 r1c3 的环形箭头 */
    public static class Reload extends IconGlyph
    {
        public Reload()
        {
            super(3, 1);
        }
    }

    /** 关闭：图集 r9c1 的「×」 */
    public static class Close extends IconGlyph
    {
        public Close()
        {
            super(1, 9);
        }
    }

    /** 设置：图集 r2c1 的齿轮 */
    public static class Settings extends IconGlyph
    {
        public Settings()
        {
            super(1, 2);
        }
    }
}
