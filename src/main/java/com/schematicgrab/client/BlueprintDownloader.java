package com.schematicgrab.client;

import com.mojang.logging.LogUtils;
import com.schematicgrab.SchematicGrab;
import com.simibubi.create.foundation.utility.CreatePaths;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 蓝图下载与落盘。
 * <p>
 * CMS 蓝图站的真实文件地址形如 /upload/blueprint/{uuid}.nbt，内容是 gzip 压缩的 NBT，
 * 而 Create 读取的是未压缩的 .nbt，所以下载后需要按魔数判断并解压。
 */
public final class BlueprintDownloader
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 单个蓝图文件大小上限，避免恶意超大文件 */
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    /** 解压后的上限，防 gzip 炸弹 */
    private static final int MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private BlueprintDownloader()
    {
    }

    /**
     * 异步下载蓝图到 Create 的 schematics 目录。
     *
     * @param fileUrl  真实文件地址（.nbt）
     * @param fileName 期望的文件名，可为 null 时从 URL 推断
     * @return 完成后的本地路径
     */
    public static CompletableFuture<Path> download(String fileUrl, String fileName)
    {
        CompletableFuture<Path> future = new CompletableFuture<>();

        HttpRequest request;
        try
        {
            URI uri = URI.create(fileUrl);
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    // HTTP 头必须是 ASCII，不能用中文模组名
                    .header("User-Agent", "SchematicGrab/1.0 (Minecraft Forge Mod)")
                    .GET();

            // Referer 用文件地址自身的站点根，兼容任意蓝图站
            String referer = originOf(uri);
            if (referer != null)
                builder.header("Referer", referer);

            request = builder.build();
        }
        catch (Exception e)
        {
            future.completeExceptionally(e);
            return future;
        }

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    try
                    {
                        if (response.statusCode() != 200)
                            throw new IOException("HTTP " + response.statusCode());

                        byte[] raw = response.body();
                        if (raw.length == 0)
                            throw new IOException("下载内容为空");
                        if (raw.length > MAX_FILE_BYTES)
                            throw new IOException("文件过大: " + raw.length + " 字节");

                        boolean gzipped = isGzip(raw);
                        // Create 用 GZIPInputStream 读蓝图，落盘必须是 gzip；站点已是 gzip 则原样保存
                        byte[] fileBytes = gzipped ? raw : gzip(raw);

                        // 解压一份出来读蓝图名称（Create 蓝图里 Name 字段即蓝图名）
                        String displayName = fileName;
                        if (displayName == null)
                        {
                            try
                            {
                                displayName = readSchematicName(gunzip(raw));
                            }
                            catch (Exception e)
                            {
                                LOGGER.debug("[{}] 读取蓝图名称失败，回退到 URL 文件名", SchematicGrab.MOD_NAME, e);
                            }
                        }

                        Path target = resolveTarget(displayName, fileUrl);

                        Files.createDirectories(target.getParent());
                        Files.write(target, fileBytes);

                        LOGGER.info("[{}] 蓝图已保存: {} ({} 字节)", SchematicGrab.MOD_NAME, target, fileBytes.length);
                        future.complete(target);
                    }
                    catch (Throwable t)
                    {
                        LOGGER.error("[{}] 保存蓝图失败", SchematicGrab.MOD_NAME, t);
                        future.completeExceptionally(t);
                    }
                })
                .exceptionally(t -> {
                    LOGGER.error("[{}] 下载蓝图失败: {}", SchematicGrab.MOD_NAME, fileUrl, t);
                    future.completeExceptionally(t);
                    return null;
                });

        return future;
    }

    /** 取 URI 的 scheme://host[:port] 作为 Referer，取不到时返回 null */
    private static String originOf(URI uri)
    {
        String host = uri.getHost();
        if (host == null || host.isBlank())
            return null;
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
        return uri.getPort() > 0
                ? scheme + "://" + host + ":" + uri.getPort() + "/"
                : scheme + "://" + host + "/";
    }

    /** 判断是否为 gzip（1f 8b） */
    private static boolean isGzip(byte[] raw)
    {
        return raw.length >= 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B;
    }

    /**
     * 解压 gzip 得到 NBT 字节，仅用于读取元数据（名称/尺寸），不会写回文件。
     * Create 读取蓝图时固定使用 GZIPInputStream，因此落盘文件必须保持 gzip 压缩。
     */
    private static byte[] gunzip(byte[] raw) throws IOException
    {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw)))
        {
            byte[] out = in.readNBytes(MAX_UNCOMPRESSED_BYTES);
            if (in.read() != -1)
                throw new IOException("解压后超过上限 " + MAX_UNCOMPRESSED_BYTES + " 字节");
            return out;
        }
    }

    /** 把裸 NBT 压成 gzip，保证 Create 能读 */
    private static byte[] gzip(byte[] data) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out))
        {
            gz.write(data);
        }
        return out.toByteArray();
    }

    /**
     * 读取 Create 蓝图里的名称。
     * 结构为 { Name: "...", Size: [...], Palette: ... }，取不到时返回 null。
     */
    private static String readSchematicName(byte[] nbt)
    {
        try
        {
            CompoundTag tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbt)));
            if (tag == null)
                return null;

            String name = tag.getString("Name");
            if (name == null || name.isBlank())
                name = tag.getString("File");

            return name == null || name.isBlank() ? null : name;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** 落盘路径：Create 的 schematics 目录，文件名做安全化处理 */
    private static Path resolveTarget(String fileName, String fileUrl)
    {
        String name = sanitize(fileName != null ? fileName : lastSegment(fileUrl));

        if (!name.toLowerCase(Locale.ROOT).endsWith(".nbt"))
            name = name + ".nbt";

        Path dir = CreatePaths.SCHEMATICS_DIR;
        Path target = dir.resolve(name);

        // 重名时追加序号，避免覆盖本地已有蓝图
        if (Files.exists(target))
        {
            String base = name.substring(0, name.length() - 4);
            int i = 1;
            while (Files.exists(target) && i < 1000)
            {
                target = dir.resolve(base + "_" + i + ".nbt");
                i++;
            }
        }
        return target;
    }

    private static String lastSegment(String url)
    {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0)
            path = path.substring(0, q);
        int hash = path.indexOf('#');
        if (hash >= 0)
            path = path.substring(0, hash);
        int slash = path.lastIndexOf('/');
        String seg = slash >= 0 ? path.substring(slash + 1) : path;
        return seg.isEmpty() ? "blueprint" : seg;
    }

    /**
     * 生成安全文件名。
     * Create 直接拿文件名当蓝图显示名，因此保留中文标题，只替换路径分隔符与 Windows 非法字符。
     */
    private static String sanitize(String name)
    {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();

        // 冒号等已替换，压缩连续下划线并去掉首尾空白与点
        cleaned = cleaned.replaceAll("_{2,}", "_").replaceAll("^[.\\s]+|[.\\s]+$", "");

        // 文件名过长会超出 Windows 限制，截断到 80 字符
        if (cleaned.length() > 80)
            cleaned = cleaned.substring(0, 80).trim();

        if (cleaned.isEmpty() || cleaned.equals(".."))
            cleaned = "blueprint";

        return cleaned;
    }

    /** 打开 schematics 文件夹，供玩家确认下载结果 */
    public static void openFolder()
    {
        Util.getPlatform().openFile(CreatePaths.SCHEMATICS_DIR.toFile());
    }
}
