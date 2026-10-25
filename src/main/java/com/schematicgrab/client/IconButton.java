package com.schematicgrab.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;

/**
 * 自绘图标按钮。
 * 底色为机械动力蓝图桌的淡蓝色面板色，三态（普通/悬停/禁用）用同一张九宫格贴图着色，
 * 图标用矢量像素绘制，保证与 Create 的界面风格一致。
 */
public class IconButton extends AbstractWidget
{
    private static final ResourceLocation TEX_NORMAL =
            new ResourceLocation("schematicgrab", "textures/gui/btn_normal.png");
    private static final ResourceLocation TEX_HOVER =
            new ResourceLocation("schematicgrab", "textures/gui/btn_hover.png");
    private static final ResourceLocation TEX_DISABLED =
            new ResourceLocation("schematicgrab", "textures/gui/btn_disabled.png");

    private final IconGlyph glyph;
    private Runnable pressAction;

    public IconButton(int x, int y, int size, IconGlyph glyph)
    {
        super(x, y, size, size, CommonComponents.EMPTY);
        this.glyph = glyph;
    }

    public void setPressAction(Runnable pressAction)
    {
        this.pressAction = pressAction;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        boolean hovered = this.isHoveredOrFocused() && this.active;

        ResourceLocation tex;
        int tint;
        if (!this.active)
        {
            tex = TEX_DISABLED;
            tint = 0xFFFFFFFF;
        }
        else if (hovered)
        {
            tex = TEX_HOVER;
            tint = 0xFFFFFFFF;
        }
        else
        {
            tex = TEX_NORMAL;
            tint = 0xFFFFFFFF;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 九宫格：四角 4px，中段拉伸
        blitNineSlice(graphics, tex, getX(), getY(), width, height, 4);

        // 图标
        glyph.draw(graphics, getX(), getY(), width, this.active ? 255 : 120);

        RenderSystem.disableBlend();
    }

    /** 简易九宫格绘制：纹理边长 20，四角 4px */
    private void blitNineSlice(GuiGraphics graphics, ResourceLocation tex,
                               int x, int y, int w, int h, int corner)
    {
        final int t = 20;          // 纹理尺寸
        final int c = corner;      // 四角尺寸
        int mw = w - c * 2;        // 中间宽度
        int mh = h - c * 2;        // 中间高度
        int tw = t - c * 2;        // 纹理中间宽度

        if (mw <= 0 || mh <= 0)
        {
            // 按钮太小时整体缩放绘制
            graphics.blit(tex, x, y, w, h, 0, 0, t, t, t, t);
            return;
        }

        // 顶行
        graphics.blit(tex, x, y, c, c, 0, 0, c, c, t, t);
        graphics.blit(tex, x + c, y, mw, c, c, 0, tw, c, t, t);
        graphics.blit(tex, x + w - c, y, c, c, t - c, 0, c, c, t, t);
        // 中行
        graphics.blit(tex, x, y + c, c, mh, 0, c, c, tw, t, t);
        graphics.blit(tex, x + c, y + c, mw, mh, c, c, tw, tw, t, t);
        graphics.blit(tex, x + w - c, y + c, c, mh, t - c, c, c, tw, t, t);
        // 底行
        graphics.blit(tex, x, y + h - c, c, c, 0, t - c, c, c, t, t);
        graphics.blit(tex, x + c, y + h - c, mw, c, c, t - c, tw, c, t, t);
        graphics.blit(tex, x + w - c, y + h - c, c, c, t - c, t - c, c, c, t, t);
    }

    @Override
    public void onClick(double mouseX, double mouseY)
    {
        if (pressAction != null)
            pressAction.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output)
    {
        defaultButtonNarrationText(output);
    }
}
