package com.schematicgrab.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.schematicgrab.Config;
import com.schematicgrab.SchematicGrab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * 内嵌的蓝图站浏览器界面。
 * 从蓝图桌点击「联网战士蓝图酱」按钮进入，网页由 MCEF / Chromium 渲染。
 */
public class SchematicBrowserScreen extends Screen
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 默认首页（CMS 蓝图站），供旧调用方引用 */
    public static final String HOME_URL = "https://www.creativemechanicserver.com/";

    /** 当前浏览的站点首页，用于「首页」按钮与下载 Referer */
    private final String homeUrl;

    /**
     * 注入到蓝图站的脚本：拦截「下载」点击，改由 Java 侧下载到 schematics 目录。
     * <p>
     * 支持的站点结构：
     * <ul>
     *   <li>CMS 蓝图站：真实文件 /upload/blueprint/{uuid}.nbt，中转页 /download/{id}/</li>
     *   <li>MCS 蓝图站（mcstool / mcschematic）：/api/v1/blueprints/{uuid}/versions/{n}/download</li>
     * </ul>
     * 请求格式：grab:&lt;文件地址&gt;\u0001&lt;蓝图标题&gt;（标题取自详情页 og:title）
     */
    private static final String SITE_HOOK_JS = """
            (function () {
              if (window.__schematicGrabHooked) return;
              window.__schematicGrabHooked = true;

              function pageTitle() {
                var m = document.querySelector('meta[property="og:title"]');
                var t = m ? m.getAttribute('content') : '';
                if (!t) {
                  var h1 = document.querySelector('h1');
                  if (h1) t = h1.textContent;
                }
                return (t || document.title || '').trim();
              }

              function send(url) {
                window.cefQuery({
                  request: 'grab:' + url + '\\u0001' + pageTitle(),
                  onSuccess: function () {},
                  onFailure: function () {}
                });
              }

              // CMS：真实文件直链
              function isCmsFile(href) {
                return href.indexOf('/upload/blueprint/') >= 0;
              }

              // CMS：下载中转页
              function isCmsRelay(href) {
                return /\\/download\\/\\d+\\/?$/.test(href);
              }

              // MCS：真实下载接口
              function isMcsApi(href) {
                return /\\/api\\/v1\\/blueprints\\/[0-9a-f-]+\\/versions\\/\\d+\\/download/.test(href);
              }

              // MCS：详情页（形如 /blueprints/{uuid}/bp-123，需要再解析真实下载地址）
              function isMcsDetail(href) {
                return /\\/blueprints\\/[0-9a-f-]{20,}/.test(href) && href.indexOf('/api/') < 0;
              }

              function resolveCms(href) {
                if (isCmsFile(href)) {
                  send(href);
                  return;
                }
                fetch(href, { credentials: 'include' })
                  .then(function (r) { return r.text(); })
                  .then(function (html) {
                    var m = html.match(/\\/upload\\/blueprint\\/[^"'\\s<>]+\\.nbt/);
                    if (m) {
                      send(location.origin + m[0]);
                    } else {
                      alert('未能识别蓝图文件地址');
                    }
                  })
                  .catch(function () { alert('解析下载地址失败'); });
              }

              function resolveMcs(href) {
                if (isMcsApi(href)) {
                  send(href);
                  return;
                }
                // 详情页：抓取页面里的下载接口地址
                fetch(href, { credentials: 'include' })
                  .then(function (r) { return r.text(); })
                  .then(function (html) {
                    var m = html.match(/\\/api\\/v1\\/blueprints\\/[0-9a-f-]+\\/versions\\/\\d+\\/download/);
                    if (m) {
                      var path = m[0].replace(/\\\\u002F/g, '/');
                      send(location.origin + path);
                    } else {
                      alert('未能识别蓝图下载地址');
                    }
                  })
                  .catch(function () { alert('解析下载地址失败'); });
              }

              document.addEventListener('click', function (e) {
                var a = e.target && e.target.closest ? e.target.closest('a') : null;
                if (!a || !a.href) return;
                var href = a.href;

                if (isCmsFile(href) || isCmsRelay(href)) {
                  e.preventDefault();
                  e.stopPropagation();
                  resolveCms(href);
                  return;
                }

                if (isMcsApi(href)) {
                  e.preventDefault();
                  e.stopPropagation();
                  resolveMcs(href);
                  return;
                }
                // 详情页只在「下载」按钮上拦截，避免影响正常浏览
                var label = (a.textContent || '') + ' ' + (a.className || '') + ' ' + (a.id || '');
                if (isMcsDetail(href) && /download|下载/i.test(label)) {
                  e.preventDefault();
                  e.stopPropagation();
                  resolveMcs(href);
                }
              }, true);
            })();
            """;

    /** 窗口外框宽度 */
    private static final int FRAME = 6;
    /** 工具栏高度 */
    private static final int TOOLBAR_H = 24;
    /** 窗口内边距 */
    private static final int PAD = 5;

    /** 窗口九宫格贴图（取自 Create 蓝图桌的边框风格） */
    private static final ResourceLocation TEX_WINDOW =
            new ResourceLocation("schematicgrab", "textures/gui/browser_window.png");
    /** 窗口贴图尺寸与四角 */
    private static final int WIN_TEX = 12;
    private static final int WIN_CORNER = 4;

    /** Create 蓝图桌的淡蓝色面板色 */
    private static final int COLOR_TOOLBAR = 0xFF828FBC;
    private static final int COLOR_TOOLBAR_DK = 0xFF6475AD;

    private final Screen parent;
    private MCEFBrowser browser;
    private int browserX;
    private int browserY;
    private int browserWidth;
    private int browserHeight;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private int lastResizeW = -1;
    private int lastResizeH = -1;
    private boolean browserCreating;
    /** 窗口与网页视口（均以 GUI 坐标计算） */
    private int frameX, frameY, frameW, frameH;
    private int viewW, viewH;
    /** 顶部状态栏文本（下载中/完成/失败） */
    private String statusText;
    private int statusColor = 0xFFFFFF;

    /** 供 BrowserBridge 更新状态栏 */
    public void setStatus(String text, BrowserBridge.Status status)
    {
        this.statusText = text;
        this.statusColor = status.color;
    }

    public SchematicBrowserScreen(Screen parent)
    {
        this(parent, Config.resolveStartUrl());
    }

    public SchematicBrowserScreen(Screen parent, String startUrl)
    {
        super(Component.translatable("gui." + SchematicGrab.MODID + ".browser.title"));
        this.parent = parent;
        this.homeUrl = startUrl == null || startUrl.isBlank() ? HOME_URL : startUrl;
    }

    @Override
    protected void init()
    {
        // 留出窗口边框
        this.frameX = FRAME;
        this.frameY = FRAME;
        this.frameW = this.width - FRAME * 2;
        this.frameH = this.height - FRAME * 2;

        int pad = PAD;
        this.browserX = frameX + pad;
        this.browserY = frameY + TOOLBAR_H + pad;
        this.viewW = frameW - pad * 2;
        this.viewH = frameH - TOOLBAR_H - pad * 2;

        // CEF 使用物理像素渲染，这里把 GUI 尺寸换算成真实像素
        double scale = guiScale();
        this.browserWidth = Math.max(64, (int) (viewW * scale));
        this.browserHeight = Math.max(64, (int) (viewH * scale));

        // 延后到下一帧再创建浏览器：在 init() 内同步创建会让首次导航被 Screen 初始化流程打断（ERR_ABORTED）
        Minecraft.getInstance().execute(this::ensureBrowser);

        int btnY = frameY + pad;
        int btnSize = TOOLBAR_H - pad * 2;
        int step = btnSize + 4;

        addIconButton(new IconGlyph.Back(), frameX + pad, btnY, btnSize,
                () -> exec("history.back()"), "gui." + SchematicGrab.MODID + ".browser.back");

        addIconButton(new IconGlyph.Home(), frameX + pad + step, btnY, btnSize,
                () -> navigate(homeUrl), "gui." + SchematicGrab.MODID + ".browser.home");

        addIconButton(new IconGlyph.Reload(), frameX + pad + step * 2, btnY, btnSize,
                () -> navigate(currentUrl()), "gui." + SchematicGrab.MODID + ".browser.reload");

        // 设置：切换到站点选择界面
        addIconButton(new IconGlyph.Settings(), frameX + pad + step * 3, btnY, btnSize,
                () -> {
                    if (minecraft != null)
                        minecraft.setScreen(new SitePickerScreen(parent));
                }, "gui." + SchematicGrab.MODID + ".browser.settings");

        addIconButton(new IconGlyph.Close(), frameX + frameW - pad - btnSize, btnY, btnSize,
                () -> onClose(), "gui." + SchematicGrab.MODID + ".browser.done");
    }

    private void addIconButton(IconGlyph glyph, int x, int y, int size, Runnable onPress, String tooltipKey)
    {
        IconButton button = new IconButton(x, y, size, glyph);
        button.setPressAction(() -> {
            playClickSound(0.85F);
            onPress.run();
        });
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(tooltipKey)));
        addRenderableWidget(button);
    }

    /** 创建浏览器（只创建一次，切换界面尺寸时仅 resize） */
    private void ensureBrowser()
    {
        // Screen 重建时 init() 可能被多次调用，用标志位防止重复创建
        if (browserCreating)
            return;

        if (browser != null)
        {
            // 尺寸没变就不要 resize，resize 会打断正在进行的加载
            if (browserWidth != lastResizeW || browserHeight != lastResizeH)
            {
                browser.resize(browserWidth, browserHeight);
                lastResizeW = browserWidth;
                lastResizeH = browserHeight;
            }
            return;
        }

        if (!MCEF.isInitialized())
        {
            LOGGER.warn("[{}] MCEF 未初始化，无法创建浏览器", SchematicGrab.MOD_NAME);
            return;
        }

        try
        {
            browserCreating = true;
            LOGGER.info("[{}] 开始创建浏览器: {}, 尺寸 {}x{}", SchematicGrab.MOD_NAME, homeUrl, browserWidth, browserHeight);
            // 一次性带上尺寸创建，避免创建后再 resize 打断首次导航
            browser = MCEF.createBrowser(homeUrl, true, browserWidth, browserHeight);
            if (browser != null)
            {
                lastResizeW = browserWidth;
                lastResizeH = browserHeight;
                browser.setFocus(true);
                // CEF 会把光标设为 Minecraft 不认识的原生句柄（GL ERROR 65539 / 禁止图标），
                // 这里忽略其光标请求，始终显示默认箭头。
                browser.setCursorChangeListener(cefCursorType -> {});
                BrowserBridge.setActiveScreen(this);
                LOGGER.info("[{}] 浏览器已创建, 渲染器纹理={}", SchematicGrab.MOD_NAME,
                        browser.getRenderer() == null ? -1 : browser.getRenderer().getTextureID());
                installSiteHook();
            }
            else
            {
                LOGGER.error("[{}] MCEF 返回了 null 浏览器", SchematicGrab.MOD_NAME);
            }
        }
        catch (Throwable t)
        {
            LOGGER.error("[{}] 创建浏览器失败", SchematicGrab.MOD_NAME, t);
            browser = null;
        }
        finally
        {
            browserCreating = false;
        }
    }

    /**
     * 注册网页桥，并在每次页面加载完成后注入拦截脚本。
     * 监听 onLoadEnd 是因为 executeJavaScript 必须等文档就绪。
     */
    private void installSiteHook()
    {
        BrowserBridge.register();

        MCEF.getClient().addLoadHandler(new CefLoadHandlerAdapter()
        {
            @Override
            public void onLoadStart(CefBrowser cefBrowser, CefFrame frame, CefRequest.TransitionType type)
            {
                if (frame != null && frame.isMain())
                    LOGGER.info("[{}] 开始加载页面: {}", SchematicGrab.MOD_NAME, frame.getURL());
            }

            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode)
            {
                // 只处理主框架，忽略子 frame 与失败页面
                if (frame == null || !frame.isMain())
                    return;
                LOGGER.info("[{}] 页面加载完成: {} (HTTP {})", SchematicGrab.MOD_NAME, frame.getURL(), httpStatusCode);
                if (httpStatusCode >= 400)
                    return;
                if (browser != null)
                    browser.executeJavaScript(SITE_HOOK_JS, "", 0);
            }

            @Override
            public void onLoadError(CefBrowser cefBrowser, CefFrame frame, CefLoadHandler.ErrorCode errorCode,
                                    String errorText, String failedUrl)
            {
                if (frame != null && frame.isMain())
                    LOGGER.error("[{}] 页面加载失败: {} - {} ({})", SchematicGrab.MOD_NAME, failedUrl, errorText, errorCode);
            }
        });
    }

    private String currentUrl()
    {
        if (browser == null)
            return homeUrl;
        String url = browser.getURL();
        return url == null || url.isEmpty() ? homeUrl : url;
    }

    private void navigate(String url)
    {
        if (browser != null)
            browser.loadURL(url);
    }

    private void exec(String js)
    {
        if (browser != null)
            browser.executeJavaScript(js, currentUrl(), 0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        // 半透明遮罩，让下层世界变暗
        graphics.fill(0, 0, width, height, 0xC0101010);

        // 窗口主体（九宫格拉伸，风格对齐机械动力蓝图桌）
        drawWindow(graphics);

        // 工具栏淡蓝面板
        drawToolbar(graphics);

        // 网页视口背景：加载完成前铺淡蓝底，避免白屏突兀
        graphics.fill(browserX, browserY, browserX + viewW, browserY + viewH, 0xFF6E7BA8);

        if (browser != null)
        {
            int textureId = browser.getRenderer() == null ? 0 : browser.getRenderer().getTextureID();
            if (textureId != 0)
                drawBrowserTexture(graphics, textureId);
            else
                graphics.drawCenteredString(font, Component.literal("网页渲染中…"),
                        browserX + viewW / 2, browserY + viewH / 2, 0xFFD8DCF0);
        }
        else
        {
            graphics.drawCenteredString(font,
                    Component.translatable("gui." + SchematicGrab.MODID + ".browser.unavailable"),
                    browserX + viewW / 2, browserY + viewH / 2, 0xFFFF7777);
        }

        // 视口内嵌阴影，让它看起来是凹进窗口的
        drawInnerShadow(graphics);

        // 再渲染控件（工具栏按钮），确保按钮在网页之上
        super.render(graphics, mouseX, mouseY, partialTick);

        // 工具栏中间：优先显示下载状态，没有状态时显示使用提示
        // 左侧有 4 个图标按钮（后退/首页/刷新/设置），文字从它们右侧开始，避免重叠
        int btnSize = TOOLBAR_H - PAD * 2;
        int textX = frameX + PAD + (btnSize + 4) * 4 + 4;
        int maxTextWidth = (frameX + frameW - PAD - btnSize - 10) - textX;
        int textY = frameY + PAD + (btnSize - 8) / 2;
        String text = statusText != null
                ? statusText
                : Component.translatable("gui." + SchematicGrab.MODID + ".browser.hint").getString();
        int color = statusText != null ? statusColor : 0xFFE4E8F8;
        graphics.drawString(font, ellipsize(text, maxTextWidth), textX, textY, color, true);
    }

    /** 窗口外框：九宫格贴图 */
    private void drawWindow(GuiGraphics graphics)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        blitNineSliced(graphics, TEX_WINDOW, frameX, frameY, frameW, frameH, WIN_CORNER, WIN_TEX);
        RenderSystem.disableBlend();
    }

    /** 工具栏：淡蓝面板 + 底部深蓝分隔线 */
    private void drawToolbar(GuiGraphics graphics)
    {
        int x0 = frameX + PAD;
        int y0 = frameY + PAD;
        int x1 = frameX + frameW - PAD;
        int y1 = y0 + TOOLBAR_H - PAD * 2;

        graphics.fill(x0, y0, x1, y1, COLOR_TOOLBAR);
        // 顶部高光
        graphics.fill(x0, y0, x1, y0 + 1, 0xFFA6B0D4);
        // 底部阴影
        graphics.fill(x0, y1 - 2, x1, y1 - 1, COLOR_TOOLBAR_DK);
        graphics.fill(x0, y1 - 1, x1, y1, 0xFF4A5580);
        // 左右描边
        graphics.fill(x0, y0, x0 + 1, y1, COLOR_TOOLBAR_DK);
        graphics.fill(x1 - 1, y0, x1, y1, COLOR_TOOLBAR_DK);
    }

    /** 视口内阴影：上/左深、右下亮，营造凹陷感 */
    private void drawInnerShadow(GuiGraphics graphics)
    {
        int x0 = browserX;
        int y0 = browserY;
        int x1 = browserX + viewW;
        int y1 = browserY + viewH;

        graphics.fill(x0, y0, x1, y0 + 1, 0xFF4A5580);
        graphics.fill(x0, y0, x0 + 1, y1, 0xFF4A5580);
        graphics.fill(x0, y1 - 1, x1, y1, 0xFFB8C0DC);
        graphics.fill(x1 - 1, y0, x1, y1, 0xFFB8C0DC);
    }

    /** 通用九宫格绘制 */
    private void blitNineSliced(GuiGraphics graphics, ResourceLocation tex,
                                int x, int y, int w, int h, int c, int texSize)
    {
        int mw = w - c * 2;
        int mh = h - c * 2;
        int tw = texSize - c * 2;

        if (mw <= 0 || mh <= 0)
        {
            graphics.blit(tex, x, y, w, h, 0, 0, texSize, texSize, texSize, texSize);
            return;
        }

        // 顶行
        graphics.blit(tex, x, y, c, c, 0, 0, c, c, texSize, texSize);
        graphics.blit(tex, x + c, y, mw, c, c, 0, tw, c, texSize, texSize);
        graphics.blit(tex, x + w - c, y, c, c, texSize - c, 0, c, c, texSize, texSize);
        // 中行
        graphics.blit(tex, x, y + c, c, mh, 0, c, c, tw, texSize, texSize);
        graphics.blit(tex, x + c, y + c, mw, mh, c, c, tw, tw, texSize, texSize);
        graphics.blit(tex, x + w - c, y + c, c, mh, texSize - c, c, c, tw, texSize, texSize);
        // 底行
        graphics.blit(tex, x, y + h - c, c, c, 0, texSize - c, c, c, texSize, texSize);
        graphics.blit(tex, x + c, y + h - c, mw, c, c, texSize - c, tw, c, texSize, texSize);
        graphics.blit(tex, x + w - c, y + h - c, c, c, texSize - c, texSize - c, c, c, texSize, texSize);
    }

    /** 超出可用宽度时截断并加省略号 */
    private String ellipsize(String text, int maxWidth)
    {
        if (maxWidth <= 0 || font.width(text) <= maxWidth)
            return text;

        String ellipsis = "...";
        int limit = maxWidth - font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray())
        {
            if (font.width(sb.toString() + c) > limit)
                break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    /**
     * 用 BufferBuilder 手动绘制浏览器纹理。
     * CEF 输出的是自上而下的位图，因此 V 坐标需要翻转。
     */
    private void drawBrowserTexture(GuiGraphics graphics, int textureId)
    {
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = graphics.pose().last().pose();
        float x0 = browserX;
        float y0 = browserY;
        // 屏幕上的绘制范围用 GUI 坐标，browserWidth/Height 是 CEF 物理像素
        float x1 = browserX + viewW;
        float y1 = browserY + viewH;

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, x0, y1, 0).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x1, y1, 0).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x1, y0, 0).uv(1.0F, 0.0F).endVertex();
        buffer.vertex(matrix, x0, y0, 0).uv(0.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    @Override
    public void onClose()
    {
        if (minecraft != null)
            minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (browser != null && isInBrowser(mouseX, mouseY))
        {
            browser.sendMousePress(bx(mouseX), by(mouseY), button);
            browser.setFocus(true);

            // 网页内的左键点击给出音效反馈
            if (button == 0)
                playClickSound(1.0F);

            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 播放界面点击音效 */
    private void playClickSound(float pitch)
    {
        if (minecraft == null)
            return;
        minecraft.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35F));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (browser != null)
        {
            if (isInBrowser(mouseX, mouseY))
            {
                browser.sendMouseRelease(bx(mouseX), by(mouseY), button);
                return true;
            }
            // 拖到框外也补一次 release，避免网页卡在按下状态
            browser.sendMouseRelease(bx(mouseX), by(mouseY), button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY)
    {
        if (browser != null && isInBrowser(mouseX, mouseY))
        {
            int x = bx(mouseX);
            int y = by(mouseY);
            if (x != lastMouseX || y != lastMouseY)
            {
                browser.sendMouseMove(x, y);
                lastMouseX = x;
                lastMouseY = y;
            }
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (browser != null && isInBrowser(mouseX, mouseY))
        {
            browser.sendMouseWheel(bx(mouseX), by(mouseY), delta * 24.0, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (browser != null)
        {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
            browser.setFocus(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        if (browser != null)
        {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers)
    {
        if (browser != null)
        {
            browser.sendKeyTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean isInBrowser(double mouseX, double mouseY)
    {
        return mouseX >= browserX && mouseX < browserX + viewW
                && mouseY >= browserY && mouseY < browserY + viewH;
    }

    /** GUI 坐标转 CEF 像素坐标（浏览器按物理像素渲染，需要乘上 GUI 缩放） */
    private int bx(double mouseX)
    {
        return (int) ((mouseX - browserX) * guiScale());
    }

    private int by(double mouseY)
    {
        return (int) ((mouseY - browserY) * guiScale());
    }

    private double guiScale()
    {
        return minecraft == null ? 1.0 : minecraft.getWindow().getGuiScale();
    }

    @Override
    public void removed()
    {
        BrowserBridge.setActiveScreen(null);
        if (browser != null)
        {
            try
            {
                browser.close();
            }
            catch (Throwable t)
            {
                LOGGER.warn("[{}] 关闭浏览器时出错", SchematicGrab.MOD_NAME, t);
            }
            browser = null;
        }
        super.removed();
    }
}

