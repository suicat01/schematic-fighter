![Uploading f2704ef2183e8fc303208779e5c5fd8b.jpg…]()
# 联网战士蓝图酱 · Schematic Fighter

在机械动力的**蓝图桌**界面里内嵌一个浏览器，直接浏览在线蓝图站、点击下载，蓝图就会存进你的 `schematics` 文件夹——不用切出游戏，不用手动找目录。

> 名字致敬《FX战士久留美》。`Fighter` 对上「战士」，`Schematic` 既指机械动力的蓝图（`.nbt`），也指「原理图」。

## 功能

- **蓝图桌内直接上网**：蓝图桌界面多一个蓝图图标按钮，点开就是内嵌浏览器
- **点下载即入库**：网页里的下载按钮被接管，蓝图自动落盘到 `schematics`，并给聊天栏和顶部状态栏反馈
- **多站点支持**：内置 CMS 蓝图站、MCS 蓝图站，也可填任意网址
- **记住选择**：选过的站点会写进配置，下次直接打开
- **界面风格对齐机械动力**：窗口边框、淡蓝面板、图标均取自 Create 原生 UI 素材

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| 机械动力 Create | 6.x |
| **MCEF** | **2.1.6-1.20.1（必须单独安装）** |

### 必须安装 MCEF

本模组依赖 [MCEF (Minecraft Chromium Embedded Framework)](https://www.curseforge.com/minecraft/mc-mods/mcef) 提供内嵌浏览器。MCEF 是独立模组，**不会随本模组一起分发**，需要你自行下载：

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/mcef
- Modrinth: https://modrinth.com/mod/mcef

选 `1.20.1 / Forge` 版本（`mcef-forge-2.1.6-1.20.1.jar`），丢进 `mods` 文件夹即可。

> 首次启动时 MCEF 会联网下载约 100MB 的 Chromium 运行时，请保持网络畅通并稍等片刻。

## 使用方法

1. 放置一个机械动力的**蓝图桌**，右键打开
2. 点界面右上角工具栏里的**蓝图图标**按钮
3. 在网页里挑蓝图，点下载
4. 聊天栏提示「下载完成」后，点蓝图桌的**刷新**按钮，新蓝图就出现在列表里

### 切换站点

点浏览器工具栏的**齿轮**图标：

- 直接点预设站点（CMS / MCS）
- 或在输入框填任意网址后点「打开自定义网址」（支持省略 `https://`）

选择会被记住。配置文件位于 `config/schematicgrab-client.toml`：

```toml
[browser]
    defaultPreset = "cms"          # cms / mcstool / custom
    customUrl = "https://example.com"
    showPicker = false             # true 则每次打开先弹站点选择
```

### 支持的站点

| 站点 | 说明 |
|---|---|
| [CMS 蓝图站](https://www.creativemechanicserver.com/) | 机械动力中文蓝图站，下载拦截已适配 |
| [MCS 蓝图站](https://mcstool.com/blueprints?mod=create) | 多模组分区，下载拦截已适配 |

其他站点可以正常浏览，但「点下载自动入库」的拦截逻辑是按上面两站的页面结构写的，未必通用。

## 从源码构建

### 1. 准备 MCEF 依赖

由于 Modrinth 分发的 MCEF 缺少 `srgs` 信息、无法自动重映射，且上游 cinemamod 仓库会错误拉取 Fabric 依赖并返回 403，本项目使用本地 jar 方式引用：

1. 从 [Modrinth](https://modrinth.com/mod/mcef/versions?g=1.20.1&l=forge) 下载 `mcef-forge-2.1.6-1.20.1.jar`
2. 在项目根目录新建 `libs` 文件夹，把 jar 放进去

```
libs/mcef-forge-2.1.6-1.20.1.jar
```

### 2. 构建

```bash
# Windows
gradlew.bat build

# macOS / Linux
./gradlew build
```

产物在 `build/libs/schematicgrab-1.0.0.jar`。

### 3. 开发环境运行

```bash
gradlew.bat runClient
```

## 项目结构

```
src/main/java/com/schematicgrab/
├── SchematicGrab.java              # 主类，注册配置、检测 MCEF
├── Config.java                     # 客户端配置：站点预设 / 自定义网址
├── client/
│   ├── SchematicBrowserScreen.java # 浏览器界面，含网页下载拦截脚本
│   ├── SitePickerScreen.java       # 站点选择界面
│   ├── BrowserBridge.java          # 网页 ↔ Java 消息桥（cefQuery）
│   ├── BlueprintDownloader.java    # 下载、gzip 处理、落盘
│   ├── IconButton.java             # 自绘三态图标按钮
│   └── IconGlyph.java              # 图标集采样
└── mixin/
    └── SchematicTableScreenMixin.java  # 注入蓝图桌界面加按钮
```

### 几个实现要点

- **蓝图文件必须是 gzip**：Create 用 `GZIPInputStream` 读取蓝图，落盘时不能解压成裸 NBT，否则会提示「文件已损坏或编码错误」
- **文件名即蓝图名**：Create 直接拿文件名当显示名，所以下载时用站点标题作文件名
- **CEF 按物理像素渲染**：创建浏览器和鼠标坐标都要乘 GUI 缩放

## 鸣谢

- [Create](https://github.com/Creators-of-Create/Create) — 蓝图桌界面与 UI 素材（`icons.png`、配色）
- [MCEF](https://github.com/CinemaMod/mcef) — 内嵌 Chromium

## 许可

[MIT](LICENSE)

本模组不包含 MCEF 的源码或二进制。MCEF 使用 LGPL-2.1 许可，其 Chromium 运行时由 MCEF 在客户端自行下载。
