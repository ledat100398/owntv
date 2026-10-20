<div align="center">

# OwnTV

### 你的电视，你的应用，别无其他。

为 Android TV 和 Google TV 打造的快速、无广告主屏幕，拥有 Apple TV 般的安静与精致。
没有赞助推荐栏，没有自动播放，没有后台常驻应用。

[English](README.md) · [Tiếng Việt](README.vi.md) · 简体中文

### [下载 OwnTV.apk](https://github.com/ledat100398/owntv/releases/latest/download/OwnTV.apk)

界面支持简体中文、English、Tiếng Việt · Android TV 与 Google TV 8.0 及以上


<img src="docs/home.jpg" alt="OwnTV 主屏幕" width="860">

<sub>截图来自索尼 BRAVIA XR-65X95J。</sub>

</div>

---

## 目录

- [为什么做这个项目](#为什么做这个项目)
- [功能](#功能)
- [准备工作](#准备工作)
- [安装](#安装)
- [手动安装（任意系统）](#手动安装任意系统)
- [使用方法](#使用方法)
- [关闭后台应用](#关闭后台应用)
- [自定义](#自定义)
- [恢复原状](#恢复原状)
- [已测试设备](#已测试设备)
- [隐私](#隐私)
- [参与贡献](#参与贡献)
- [致谢](#致谢)
- [许可证](#许可证)

## 为什么做这个项目

Google TV 自带的主屏幕更像一个橱窗：赞助内容、自动播放的预告片，还有十几个厂商服务随电视启动、
一直运行。OwnTV 用一个只做好一件事的主屏幕取而代之：安静、迅速地把你带到想打开的应用。

## 截图

<table>
<tr><td><img src="docs/large-clock.jpg" width="420"><br><sub>大时钟样式</sub></td><td><img src="docs/top-bar.jpg" width="420"><br><sub>顶部栏，选中设置</sub></td></tr>
<tr><td><img src="docs/inputs.jpg" width="420"><br><sub>信号源，显示 HDMI-CEC 设备名称</sub></td><td><img src="docs/settings.jpg" width="420"><br><sub>设置</sub></td></tr>
<tr><td><img src="docs/wifi.jpg" width="420"><br><sub>网络状态</sub></td><td><img src="docs/app-menu.jpg" width="420"><br><sub>在应用上长按 OK</sub></td></tr>
</table>

## 功能

**设计**
- 顶部磨砂玻璃栏包含信号源、Wi-Fi 和设置。一颗"水滴"随轻柔的弹簧在按钮之间滑动；按钮本身从不
  改变大小，界面不会跳动。
- 打开应用时从所点的图块放大展开，返回时收回原处。动画逐帧绘制、圆角抗锯齿，没有白屏闪烁，
  也没有放大失真。
- 壁纸随焦点轻微移动，营造景深。每次移动有轻柔的"嗒"声，到达边缘时轻轻回弹，并遵循电视的系统
  音效设置。
- 全程使用 Inter 字体：接近 Apple 的 SF Pro，且完整支持越南语。

**日常使用**
- 信号源面板列出各 HDMI 接口及已连接设备的名称（通过 HDMI-CEC），以及电视直播。
- Wi-Fi 面板显示当前连接的网络以及能否访问互联网。
- 设置面板只放常用项：画面、声音、定时关机、主屏幕样式、全部设置。电视无法打开的项目会自动隐藏。
- 可选左侧大号日期与时钟（设置 › 主屏幕样式）。
- 在应用上长按 OK 可移动、隐藏或查看应用信息。左右键像阅读一行文字：到行尾自动进入下一行；
  在最后一个应用上按右键回到第一个。
- 遥控器麦克风语音搜索：说出应用名称即可打开，说其他内容则在 YouTube 上搜索。在 Google TV 上，它代替原主屏幕的
  麦克风功能（原主屏幕移除后该功能随之消失）。
- 缓慢漂移的时钟屏保，保护面板。
- 界面支持越南语、英语和简体中文，跟随电视语言自动选择。

**性能**
- 不做实时模糊：玻璃效果使用一张预先模糊的小尺寸壁纸。
- 图块阴影只绘制一次并重复使用；动画只改变位移和透明度。
- 内置测量（第 5 项），可对照目标值报告冷启动/热启动时间、掉帧和内存占用。

## 准备工作

- 运行 **Android TV 或 Google TV 8.0 及以上**的电视或盒子（索尼、TCL、小米、飞利浦、海信、
  NVIDIA Shield、Chromecast with Google TV 等）。三星（Tizen）和 LG（webOS）电视不运行 Android，
  不受支持。
- 打开**开发者选项**和网络调试（见下文）。Android 11 及以上的电视如果只有*无线调试*，第 1 项会引导你用配对码完成配对。
- 一台与电视处于同一网络的电脑（仅 Google TV 或需要关闭后台应用时才需要）。一键脚本适用于 **Windows 10/11**；macOS 或 Linux 请按手动步骤操作。

## 安装

**无需电脑**（Android TV）：在电视上从 Play 商店安装 *Downloader*，输入
`https://github.com/ledat100398/owntv/releases/latest/download/OwnTV.apk`，安装 APK，然后按主页键并选择 OwnTV。Google TV 的原主屏幕会夺回主页键，
请按下方 Windows 步骤操作一次。

**使用 Windows 电脑**（Google TV 需操作一次）：

1. **在电视上打开开发者选项。** 设置 › 系统 › 关于 › 连续选择*版本号* 7 次。然后在 设置 › 系统 ›
   开发者选项 中打开 *USB 调试*（如有*网络调试*也一并打开）。在 设置 › 网络 中查看电视的 IP 地址。
2. **下载本仓库**（绿色 *Code* 按钮 › *Download ZIP*）并解压。
3. **打开 `OwnTV.cmd`，选择 1。** 脚本会从本页下载最新的 `OwnTV.apk`（几 MB）并安装；只有下载失败时才从源码构建。电视提示*允许 USB 调试吗？*时，勾选*始终允许*并确认。完成后，开机或按主页键都会进入
   OwnTV。脚本会自动按主页键检查，如果没有进入 OwnTV，会自动恢复原状。

只需打开 `OwnTV.cmd` 这一个文件，所有操作都在它的菜单里：

| 选项 | 作用 |
|---|---|
| 1 | 安装 OwnTV 并设为主屏幕 |
| 2 | 恢复原主屏幕 |
| 3 | 按组关闭后台、广告和跟踪类应用（索尼列表），可选 |
| 4 | 恢复第 3 项关闭的全部应用 |
| 5 | 测量启动时间、掉帧和内存 |

> 提示文字为越南语，菜单附有英文说明。每一项在更改前都会询问、保留备份，并且可以恢复。
> 如不熟悉越南语，可按下方手动步骤完成同样的操作。

## 手动安装（任意系统）

需要 [ADB](https://developer.android.com/tools/releases/platform-tools) 和 JDK 17。

```sh
# 构建
cd launcher
./gradlew assembleDebug            # Windows：gradlew.bat assembleDebug

# 安装
adb connect <电视IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

设为主屏幕：

```sh
adb shell cmd package set-home-activity --user 0 io.github.ledat100398.tvhome/.MainActivity
```

在 **Google TV** 上，原主屏幕会夺回主页键；而原主屏幕缺失时，开机向导又会占用主页键进行"修复"。
请仅对当前用户移除二者（不会从电视中删除）：

```sh
adb shell pm uninstall -k --user 0 com.google.android.apps.tv.launcherx
adb shell pm uninstall -k --user 0 com.google.android.tungsten.setupwraith
adb shell cmd package set-home-activity --user 0 io.github.ledat100398.tvhome/.MainActivity
adb shell input keyevent KEYCODE_HOME
```

其他品牌的电视和盒子（TCL、小米、FPT Play Box 等）可能还自带主屏幕，重启后会夺回主页键。用
`adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME` 列出所有主屏幕，再按同样方法移除多余的。`OwnTV.cmd` 的第 1 项会自动完成这一步。如果电视坚持不放弃原主屏幕，第 1 项会改用一个小型无障碍服务
（设置 › 无障碍 › OwnTV 主页键）让主页键始终回到 OwnTV，与按键映射类应用的做法相同。

移除期间无法在电视上添加新的 Google 账号；如有需要，请先恢复（见[恢复原状](#恢复原状)）。

时钟屏保（可选）：

```sh
adb shell settings put secure screensaver_components io.github.ledat100398.tvhome/.ClockDream
```

## 使用方法

| 遥控器 | 效果 |
|---|---|
| 方向键 | 在应用间移动。到行尾后自动进入下一行 |
| 在最后一个应用上按右 | 回到第一个应用 |
| 在第一行按上 | 进入顶部栏 |
| OK | 打开应用或面板 |
| 长按 OK 或菜单键 | 移动、隐藏、应用信息 |
| 移动应用时 | 方向键移动，OK 放下，返回键取消 |
| 返回键 | 关闭面板、取消移动、离开顶部栏，或回到第一行 |

已隐藏的应用可在 设置 › 显示已隐藏的应用 中恢复。

## 关闭后台应用

`OwnTV.cmd` 的第 3 项使用 [`examples/sony-bravia-vn/goi-tat.tsv`](examples/sony-bravia-vn/goi-tat.tsv)，
按组（广告与跟踪、门店演示模式、错误报告、少用应用、语音、投屏）逐组询问。应用通过
`pm uninstall -k --user 0` **仅对当前用户**移除：原版仍保留在电视中，可用 `cmd package install-existing`
恢复。之所以不用"停用"，是因为索尼电视每次重启都会重新启用被停用的应用。

其他品牌请参考 [`examples/`](examples/)，其中有 TCL、小米、海信、一加、明基、三洋、NVIDIA 等已测试
的列表。仔细检查列表后，逐行执行 `adb shell pm uninstall -k --user 0 <包名>`。

切勿移除：电视直播应用、HDMI/CEC 服务、遥控器与键盘服务、Google Play 服务、Play 商店、蓝牙。

## 自定义

全部位于 [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml)：

- `dock_apps`：排在最前面的应用及其顺序。
- `hidden_apps`：永不显示为图块的包名。
- `live_tv_apps`、`sleep_timer_apps`：电视自带的直播应用和定时关机应用。

电视上不存在的包会被跳过，因此默认配置适用于任何电视。如需更换壁纸，替换
`launcher/app/src/main/res/drawable-nodpi/wallpaper.jpg`（2008 × 1130）后重新构建。

## 恢复原状

- 原主屏幕：打开 `OwnTV.cmd` 选择 2，或

  ```sh
  adb shell cmd package install-existing com.google.android.tungsten.setupwraith
  adb shell cmd package install-existing com.google.android.apps.tv.launcherx
  adb shell input keyevent KEYCODE_HOME
  ```

- 后台应用：在 `OwnTV.cmd` 中选择 4，或 `adb shell cmd package install-existing <包名>`。
- 卸载 OwnTV：设置 › 应用 › OwnTV › 卸载。

使用稳定后，请关闭电视上的 USB 调试。

## 已测试设备

| 设备 | 系统 | 状态 |
|---|---|---|
| Sony BRAVIA XR-65X95J | Google TV，Android 12 | 日常使用 |

OwnTV 只使用公开的 Android TV API，应可在任何 Android TV 8.0 及以上设备上运行。如果你在其他型号上
试用，欢迎[报告你的电视](https://github.com/ledat100398/owntv/issues/new?template=tv-report.yml)，说明型号和效果，帮助完善此表。

## 隐私

OwnTV 不发起任何网络请求，也不包含任何统计分析。它申请位置权限只是因为 Android 要求有此权限才能
读取当前连接的 Wi-Fi 名称。

## 参与贡献

欢迎提交问题报告、其他电视的应用列表和 Pull Request，详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 致谢

OwnTV 源自 Gathin 的 [tv-vibehack](https://github.com/Gathin23/tv-vibehack)。通过 ADB 替换原主屏幕的
思路、`tools/` 中的 ADB 工具以及 `examples/` 中由社区整理的应用列表均来自该项目。启动器本身、界面设计
和 Windows 脚本均为重新编写。

字体：Rasmus Andersson 的 [Inter](https://rsms.me/inter/)（SIL Open Font License）。
齿轮图标参考 [Lucide](https://lucide.dev)（ISC）。

## 许可证

[MIT](LICENSE) © 2026 Lê Trí Thành Đạt，并保留 tv-vibehack 原版权声明 © 2026 Gathin。

<sub>截图中的应用名称和标志归各自所有者所有。OwnTV 是独立项目，与 Google、索尼、Netflix、亚马逊或截图中出现的任何应用均无关联，也未获其认可。</sub>
