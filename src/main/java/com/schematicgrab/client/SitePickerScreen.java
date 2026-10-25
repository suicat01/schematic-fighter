package com.schematicgrab.client;

import com.schematicgrab.Config;
import com.schematicgrab.SchematicGrab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 站点选择界面：可选内置预设，也可自己输入任意网址。
 * 选择结果会写回配置，下次打开时沿用。
 */
public class SitePickerScreen extends Screen
{
    private final Screen parent;
    private EditBox urlBox;
    private String error;

    public SitePickerScreen(Screen parent)
    {
        super(Component.translatable("gui." + SchematicGrab.MODID + ".picker.title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        int centerX = this.width / 2;
        int top = this.height / 2 - 90;
        int btnW = 240;
        int btnH = 20;
        int gap = 6;

        // 预设站点按钮
        int y = top;
        for (Config.Preset preset : Config.PRESETS)
        {
            final Config.Preset p = preset;
            addRenderableWidget(Button.builder(Component.literal(p.label()), b -> selectPreset(p))
                    .bounds(centerX - btnW / 2, y, btnW, btnH)
                    .build());
            y += btnH + gap;
        }

        // 自定义网址输入
        y += 8;
        this.urlBox = new EditBox(this.font, centerX - btnW / 2, y, btnW, btnH,
                Component.translatable("gui." + SchematicGrab.MODID + ".picker.custom"));
        this.urlBox.setMaxLength(512);
        this.urlBox.setHint(Component.translatable("gui." + SchematicGrab.MODID + ".picker.custom_hint"));
        String configured = Config.CUSTOM_URL.get();
        this.urlBox.setValue(configured == null ? "" : configured);
        addRenderableWidget(this.urlBox);

        y += btnH + gap;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + SchematicGrab.MODID + ".picker.open_custom"),
                        b -> openCustom())
                .bounds(centerX - btnW / 2, y, btnW, btnH)
                .build());

        // 底部：取消
        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + SchematicGrab.MODID + ".picker.cancel"),
                        b -> onClose())
                .bounds(centerX - 50, this.height - 40, 100, btnH)
                .build());
    }

    /** 选中预设站点：记住选择，然后打开 */
    private void selectPreset(Config.Preset preset)
    {
        Config.DEFAULT_PRESET.set(preset.id());
        Config.SPEC.save();
        open(preset.url());
    }

    private void openCustom()
    {
        String raw = urlBox == null ? "" : urlBox.getValue();
        String url = Config.sanitizeUrl(raw);

        if (url == null)
        {
            this.error = Component.translatable("gui." + SchematicGrab.MODID + ".picker.invalid").getString();
            return;
        }

        // 记住自定义地址，下次直接用
        Config.DEFAULT_PRESET.set("custom");
        Config.CUSTOM_URL.set(url);
        Config.SPEC.save();
        open(url);
    }

    private void open(String url)
    {
        if (this.minecraft != null)
            this.minecraft.setScreen(new SchematicBrowserScreen(this, url));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 120, 0xFFFFFF);

        // 预设区标题
        graphics.drawString(this.font,
                Component.translatable("gui." + SchematicGrab.MODID + ".picker.presets"),
                this.width / 2 - 120, this.height / 2 - 106, 0xA0A0A0, false);

        // 自定义区标题
        graphics.drawString(this.font,
                Component.translatable("gui." + SchematicGrab.MODID + ".picker.custom"),
                this.width / 2 - 120, this.height / 2 + 6, 0xA0A0A0, false);

        // 当前默认站点：让玩家确认保存结果
        String current = Config.DEFAULT_PRESET.get();
        String currentLabel = "custom".equalsIgnoreCase(current)
                ? Config.CUSTOM_URL.get()
                : Config.PRESETS.stream()
                        .filter(p -> p.id().equalsIgnoreCase(current))
                        .map(Config.Preset::label)
                        .findFirst()
                        .orElse(current);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui." + SchematicGrab.MODID + ".picker.current", currentLabel),
                this.width / 2, this.height / 2 + 56, 0x8FE08F);

        if (this.error != null)
        {
            graphics.drawCenteredString(this.font, Component.literal(this.error),
                    this.width / 2, this.height - 56, 0xFF5555);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose()
    {
        if (this.minecraft != null)
            this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
