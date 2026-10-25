package com.schematicgrab;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * 联网战士蓝图酱 (Schematic Fighter)
 * 在机械动力蓝图桌界面内嵌网页，直接浏览并下载在线蓝图到 schematics 文件夹。
 */
@Mod(SchematicGrab.MODID)
public class SchematicGrab
{
    public static final String MODID = "schematicgrab";
    public static final String MOD_NAME = "联网战士蓝图酱";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SchematicGrab(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);

        // 客户端配置：站点选择 / 自定义网址
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, Config.SPEC);
    }

    private void clientSetup(final FMLClientSetupEvent event)
    {
        if (FMLEnvironment.dist != Dist.CLIENT)
            return;

        if (!ModList.get().isLoaded("mcef"))
        {
            LOGGER.warn("[{}] 未检测到 MCEF，网页浏览器功能将不可用。请安装 MCEF (1.20.1 Forge)。", MOD_NAME);
            return;
        }
        LOGGER.info("[{}] 已检测到 MCEF，蓝图桌网页浏览器可用。", MOD_NAME);
    }

    /**
     * MCEF 是否可用。为 false 时蓝图桌不显示浏览按钮。
     */
    public static boolean isBrowserAvailable()
    {
        return ModList.get().isLoaded("mcef");
    }

    public static Logger logger()
    {
        return LOGGER;
    }
}
