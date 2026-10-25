package com.schematicgrab.mixin;

import com.schematicgrab.Config;
import com.schematicgrab.SchematicGrab;
import com.schematicgrab.client.SchematicBrowserScreen;
import com.schematicgrab.client.SitePickerScreen;
import com.simibubi.create.content.schematics.table.SchematicTableMenu;
import com.simibubi.create.content.schematics.table.SchematicTableScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在机械动力蓝图桌界面注入「联网战士蓝图酱」按钮，点击打开内嵌网页浏览器。
 * 注入点选在 init 末尾，此时 leftPos / topPos 与既有按钮已完成初始化。
 */
@Mixin(SchematicTableScreen.class)
public abstract class SchematicTableScreenMixin extends AbstractSimiContainerScreen<SchematicTableMenu>
{
    protected SchematicTableScreenMixin(SchematicTableMenu container, Inventory inv, Component title)
    {
        super(container, inv, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void schematicgrab$addBrowserButton(CallbackInfo ci)
    {
        if (!SchematicGrab.isBrowserAvailable())
            return;

        int x = this.leftPos;
        int y = this.topPos + 2;

        // 放在既有的「刷新」按钮右侧（刷新按钮位于 x + 206, y + 21）
        IconButton browseButton = new IconButton(x + 228, y + 21, AllIcons.I_SCHEMATIC);
        browseButton.withCallback(() -> {
            // 配置开启站点选择时先让玩家选站，否则直接打开默认站点
            if (Config.SHOW_PICKER.get())
                this.minecraft.setScreen(new SitePickerScreen(this));
            else
                this.minecraft.setScreen(new SchematicBrowserScreen(this));
        });
        browseButton.setToolTip(Component.translatable("gui." + SchematicGrab.MODID + ".open_browser"));
        this.addRenderableWidget(browseButton);
    }
}
