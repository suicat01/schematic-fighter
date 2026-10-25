package com.schematicgrab.client;

import com.cinemamod.mcef.MCEF;
import com.mojang.logging.LogUtils;
import com.schematicgrab.SchematicGrab;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网页与 Java 之间的桥。
 * 页面中的 window.cefQuery({ request: 'grab:<url>\u0001<title>' }) 会被这里收到，
 * 然后异步下载蓝图到 schematics 目录。
 * <p>
 * 下载状态会同时回调到浏览器界面（顶部状态栏）和聊天栏。
 */
public final class BrowserBridge
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "grab:";
    /** 分隔 URL 与标题的控制字符 */
    private static final String SEP = "\u0001";

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    /** 正在下载的 URL，用于同一次点击的重复请求去重 */
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /** 当前打开的浏览器界面，用于把状态显示在 UI 内 */
    private static volatile SchematicBrowserScreen activeScreen;

    private BrowserBridge()
    {
    }

    /** 由浏览器界面在创建/关闭时调用 */
    public static void setActiveScreen(SchematicBrowserScreen screen)
    {
        activeScreen = screen;
    }

    /** 向 MCEF 的客户端注册消息路由，全局只需注册一次 */
    public static void register()
    {
        if (!REGISTERED.compareAndSet(false, true))
            return;

        if (!MCEF.isInitialized())
        {
            LOGGER.warn("[{}] MCEF 未初始化，跳过消息路由注册", SchematicGrab.MOD_NAME);
            REGISTERED.set(false);
            return;
        }

        CefMessageRouter router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter()
        {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                                   String request, boolean persistent,
                                   CefQueryCallback callback)
            {
                if (request == null || !request.startsWith(PREFIX))
                    return false;

                String payload = request.substring(PREFIX.length()).trim();
                String url = payload;
                String title = null;

                int sep = payload.indexOf(SEP);
                if (sep >= 0)
                {
                    url = payload.substring(0, sep).trim();
                    title = payload.substring(sep + SEP.length()).trim();
                    if (title.isEmpty())
                        title = null;
                }

                LOGGER.info("[{}] 收到下载请求: {} (标题: {})", SchematicGrab.MOD_NAME, url, title);
                handleGrab(url, title);

                if (callback != null)
                    callback.success("queued");
                return true;
            }
        }, true);

        MCEF.getClient().getHandle().addMessageRouter(router);
        LOGGER.info("[{}] 网页消息桥已注册", SchematicGrab.MOD_NAME);
    }

    private static void handleGrab(String url, String title)
    {
        if (!url.startsWith("http://") && !url.startsWith("https://"))
        {
            report("§c[联网战士蓝图酱] 无效的下载地址", Status.ERROR);
            return;
        }

        // 同一次点击可能被页面多次触发，短时间去重
        if (!IN_FLIGHT.add(url))
        {
            LOGGER.debug("[{}] 跳过重复的下载请求: {}", SchematicGrab.MOD_NAME, url);
            return;
        }

        // 立即给出反馈，避免玩家以为没反应而重复点击
        String label = title != null ? title : url.substring(url.lastIndexOf('/') + 1);
        report("§e正在下载: §f" + label, Status.DOWNLOADING);

        BlueprintDownloader.download(url, title)
                .thenAccept(path -> {
                    IN_FLIGHT.remove(url);
                    report("§a下载完成: §f" + path.getFileName()
                            + "\n§7点蓝图桌的刷新按钮即可在列表中选择", Status.DONE);
                })
                .exceptionally(t -> {
                    IN_FLIGHT.remove(url);
                    report("§c下载失败: §f" + t.getMessage(), Status.ERROR);
                    return null;
                });
    }

    /** 下载状态，供界面顶部状态栏显示 */
    public enum Status
    {
        DOWNLOADING(0xFFAA00),
        DONE(0x55FF55),
        ERROR(0xFF5555);

        public final int color;

        Status(int color)
        {
            this.color = color;
        }
    }

    /** 同时更新界面状态栏与聊天栏，并播放提示音 */
    private static void report(String message, Status status)
    {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            SchematicBrowserScreen screen = activeScreen;
            if (screen != null)
                screen.setStatus(message.replaceAll("§.", "").replace('\n', ' '), status);

            if (mc.player != null)
            {
                mc.player.displayClientMessage(Component.literal(message), false);
                mc.player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER,
                        1.0F, status == Status.ERROR ? 0.6F : 1.6F);
            }
            else
            {
                // 不在存档里时也保证有声音反馈
                mc.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                SoundEvents.NOTE_BLOCK_PLING.value(),
                                status == Status.ERROR ? 0.6F : 1.6F, 1.0F));
            }
        });
    }
}
