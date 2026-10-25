package com.schematicgrab;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 客户端配置：站点选择与自定义网址。
 * 配置文件位于 config/schematicgrab-client.toml
 */
public final class Config
{
    /** 预设站点：显示名 + 首页地址 */
    public record Preset(String id, String label, String url)
    {
    }

    /**
     * 内置预设站点。
     * 注意：CMS 蓝图站的下载拦截依赖站点结构（见 SchematicBrowserScreen 的注入脚本），
     * 其他站点按「浏览器直接打开」处理。
     */
    public static final List<Preset> PRESETS = List.of(
            new Preset("cms", "CMS 蓝图站", "https://www.creativemechanicserver.com/"),
            new Preset("mcstool", "MCS 蓝图站", "https://mcstool.com/blueprints?mod=create")
    );

    public static final ForgeConfigSpec SPEC;

    /** 启动时打开的站点 id，对应 PRESETS 里的 id；填 custom 则使用 customUrl */
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_PRESET;
    /** 自定义网址（DEFAULT_PRESET 为 custom 时生效） */
    public static final ForgeConfigSpec.ConfigValue<String> CUSTOM_URL;
    /** 是否在打开时显示站点选择界面 */
    public static final ForgeConfigSpec.BooleanValue SHOW_PICKER;

    static
    {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("联网战士蓝图酱 / Schematic Fighter 客户端配置").push("browser");

        DEFAULT_PRESET = builder
                .comment("启动时打开的站点。可选: cms, mcstool, custom",
                        "设为 custom 时使用 customUrl 的值")
                .define("defaultPreset", "cms");

        CUSTOM_URL = builder
                .comment("自定义网址，defaultPreset 设为 custom 时生效",
                        "必须是以 http:// 或 https:// 开头的完整地址")
                .define("customUrl", "https://mcstool.com/blueprints?mod=create");

        SHOW_PICKER = builder
                .comment("打开时是否先显示站点选择界面。",
                        "默认 false：直接用 defaultPreset 指定的站点打开，可在浏览器工具栏的齿轮里随时更换")
                .define("showPicker", false);

        builder.pop();

        SPEC = builder.build();
    }

    private Config()
    {
    }

    /** 取配置里指定的站点地址 */
    public static String resolveStartUrl()
    {
        String preset = DEFAULT_PRESET.get();

        if ("custom".equalsIgnoreCase(preset))
            return sanitizeUrl(CUSTOM_URL.get());

        for (Preset p : PRESETS)
        {
            if (p.id().equalsIgnoreCase(preset))
                return p.url();
        }

        // 配置写错时回退到第一个预设
        return PRESETS.get(0).url();
    }

    /** 校验并规范化用户输入的网址，无效时返回 null */
    public static String sanitizeUrl(String raw)
    {
        if (raw == null)
            return null;

        String url = raw.trim();
        if (url.isEmpty())
            return null;

        // 允许省略协议
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            url = "https://" + url;

        try
        {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https")))
                return null;
            if (uri.getHost() == null || uri.getHost().isBlank())
                return null;
            return url;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
