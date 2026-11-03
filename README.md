# DSH 悬浮窗

[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](https://developer.android.com)
[![Size](https://img.shields.io/badge/APK-33%20KB-lightgrey)](#)
[![Dependencies](https://img.shields.io/badge/dependencies-none-success)](#)

> 原仓库：https://github.com/xiangshu2026/dsh-float

把 **DeepSeek Harness** 的手机端页面塞进一个可拖动、可缩放的悬浮窗里，
让你在别的 App 上方也能看到 AI 的回复。

**适用场景**：一边看视频 / 打游戏 / 刷网页，一边等 AI 出结果。

---

## 快速上手（先看这个）

> 四步就能跑起来。下面「安装」「怎么用」是细节，卡住了再回去看。

### 第 1 步：电脑上建隧道

手机要能连到电脑的 DSH。最稳的方式是 USB 反向隧道 —— **手机需要开「USB 调试」**：

```bash
adb reverse tcp:19387 tcp:19387
```

- 开 USB 调试的路径：设置 → 关于手机 → 连点「版本号」7 次 → 回设置 → 开发者选项 → USB 调试
- 如果 DSH 实际端口不是 19387，把命令右边改成你的端口（DSH 启动时会打印 `dsh web: http://127.0.0.1:<端口>/?token=...`）
- 隧道**不持久**，手机重连 USB 或重启 adb 后要重跑

### 第 2 步：装 App 并开悬浮窗权限

装 `DSHFloat.apk`，打开 App，点「① 授予悬浮窗权限」，在系统设置里允许。

> 悬浮窗权限不在「开发者选项」里，是普通应用权限，正常操作即可。

### 第 3 步：填地址，点启动

地址填 `http://127.0.0.1:19387/`，点「② 启动悬浮窗」，按 Home 键就能看到浮在桌面上的窗口。

### 第 4 步（大概率需要）：解决认证

App 里**必须**有一个认证凭据，否则页面只会显示：

```
dsh web authentication required, reopen the URL printed by dsh web.
```

这不是 bug —— DSH 的 Web 服务默认要求认证。两条路选一条：

#### 路线 A：填签名密钥（推荐，一次搞定长期有效）

1. 用记事本打开 `%USERPROFILE%\.dsh\.credentials.yaml`
2. 找这一段（缩进要对齐着看）：

```yaml
records:
  client-connection/browser-session:
    payload:
      secret: 这串就是密钥
```

3. 把 `secret:` 后面那串复制出来，粘进 App 的「签名密钥」输入框
4. 点「② 启动悬浮窗」

这样 30 天内都不用再管认证，DSH 重启也不影响。

#### 路线 B：地址里带 token（不用改文件）

DSH 启动时控制台会打印一行：

```
dsh web: http://127.0.0.1:<端口>/?token=<一串字符>
```

把整串（**含 `?token=` 部分**）粘进 App 的「页面地址」，注意端口要换成你隧道用的那个（见第 1 步）。

缺点是 DSH 每次重启 token 就变，要重新复制。

> ⚠️ **每个 DSH 安装的密钥都不一样，抄别人的无效。**
> 本项目源码里 `Auth.DEFAULT_SECRET` 是空字符串，就是为了不把作者自己的密钥打进包里 ——
> 那对别人没用，还泄露作者凭据。

> 💡 **密钥的作用原理**：DSH 用 HMAC-SHA256 自签 cookie。App 拿你的 secret 算出
> `dsh-auth-<authority哈希>` 这个 cookie，DSH 验签通过就放行。
> authority 必须和地址栏里的 `host:port` 一致，App 已经按你填的地址自动算了。

---

## 它是什么

一个 Android 悬浮窗 App，内嵌 WebView 加载 DSH 的手机页面。

- 悬浮球 + 展开面板两种形态
- 可拖动、可缩放
- 面板可折叠成小球，不挡视线
- 深色主题，跟 DSH 风格一致

---

## 安装

### 方式一：直接装 APK（推荐）

下载 `DSHFloat.apk`（约 38 KB），传到手机安装。

**需要手动开的权限只有一个**：

1. **悬浮窗权限** —— 设置 → 应用 → DSH悬浮窗 → 显示在其他应用上层 → 允许
   （App 里点「① 授予悬浮窗权限」会直接跳到这个页面）

网络访问权限在安装时自动授予，不用管。

> **不需要**：root、开发者选项、Xposed、任何系统级修改。
> 只有走 USB 隧道时需要开「USB 调试」，那是**手机**的开发者选项，跟 App 本身无关。

### 方式二：自己编译（命令行，不用 Android Studio）

本仓库不含 gradle 工程，但有一套完整的命令行构建流程，**已实测可用**。
需要 JDK 17 + Android build-tools（aapt2 / d8 / zipalign / apksigner）+ `android.jar`。

```bash
WORK=/path/to/work            # 英文路径，别用中文
BT=$ANDROID_HOME/build-tools  # 含 aapt2 / d8 / zipalign / apksigner
JDK=$JAVA_HOME
AJAR=$WORK/android.jar        # platform-35 的 android.jar

# 1. 编译资源与 manifest
aapt2 compile --dir app/res -o build/res.zip
aapt2 link -o build/base.apk -I $AJAR \
  --manifest app/AndroidManifest.xml \
  --java build/gen build/res.zip \
  --min-sdk-version 26 --target-sdk-version 35 --auto-add-overlay

# 2. 编译 Java（必须用 --release 8 -classpath，不能用 -bootclasspath）
javac -encoding UTF-8 --release 8 -classpath $AJAR -d build/classes \
  $(find app/src build/gen -name '*.java')

# 3. 转 dex
d8 --release --min-api 26 --lib $AJAR --output build/dex build/classes/**/*.class

# 4. 组装 + 对齐 + 签名
cp build/base.apk build/unsigned.apk
(cd build/dex && jar uf ../unsigned.apk classes.dex)
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks my.keystore --out build/DSHFloat.apk build/aligned.apk
```

**三个必须知道的坑**：

| 坑 | 说明 |
|---|---|
| **中文路径会失败** | aapt2 在含中文的路径下报 `failed to open directory`，文件明明存在。把整个工程放到纯英文路径再编译 |
| **`-bootclasspath` 会报错** | javac 用 `-bootclasspath android.jar` 会报「找不到 LambdaMetafactory」，必须改成 `--release 8 -classpath` |
| **改 manifest 要重跑 aapt2** | 只改 Java 代码时 d8 那步就够；改了 `AndroidManifest.xml` 或 `res/` 必须从 aapt2 link 重来 |

**为什么没有 gradle 工程**：这个项目是在手机上直接写 + 用命令行工具编译的，
没有走 Android Studio 的标准工程结构。上面这套命令就是当时的实际构建流程，
2026-10-31 重新验证过一遍，产物 33,820 B，`apksigner verify` 退出码 0。

---

## 怎么用

### 1. 先让电脑端 DSH 能被手机访问

手机要能连到电脑上的 DSH。两种方式：

**方式 A：USB + adb 反向隧道**（推荐，最稳）

```bash
# 手机插 USB，开 USB 调试
adb devices                          # 确认能看到设备
adb reverse tcp:19387 tcp:19387      # 把手机的 19387 转到电脑
```

然后在 App 里填 `http://127.0.0.1:19387/`。

**方式 B：局域网直连（基本走不通，除非改 DSH 配置）**

电脑和手机在同一个 WiFi 下，App 里填 `http://<电脑IP>:19387/`。

> ⚠️ 实测这条路默认被 DSH 的两道围栏挡住：
> 1. **Host 围栏**：DSH 只认 loopback / 绑定地址 / `trustedHosts` 里的 authority，其它 Host 一律 403。实测 `Host: 192.168.1.x` → 403，`Host: 127.0.0.1` → 401（过围栏、只缺认证）。
> 2. **`sec-fetch-site` 围栏**：WebView 首帧会带 `cross-site`，命中就 403。
>
> 想走局域网要同时解决这两道（`dsh --trusted-host <IP:端口>` 或 profile 里 `client-connection.trustedHosts`），但第二道在 WebView 里很难绕。
> **结论：用 USB 隧道，别折腾局域网。**

### 2. 填地址

打开 App，在输入框填上一步的地址，点保存。

### 3. 开悬浮窗

点「启动悬浮窗」，会弹出一个悬浮球。点球展开面板。

---

## 界面操作

| 操作 | 效果 |
|---|---|
| 拖动球 | 移动位置 |
| 点球 | 展开 / 收起面板 |
| 拖右下角 | 缩放面板大小 |
| ☰ 按钮 | 打开抽屉（设置 / 重载 / 关闭） |
| 长按球 | 隐藏到通知栏 |

---

## 已知限制

1. **没有 gradle 工程** —— 用命令行构建（命令见上），或自己搭 Android Studio 工程
2. **只测过 Android 12** —— 一台 Android 12 真机 + MuMu 模拟器（Android 12 / x86_64），其他版本未验证
3. **DSH 登录 token 每次都变** —— App 里用签名密钥自动处理，但 DSH 重启后可能需要重连
4. **局域网模式可能 403** —— 建议用 adb 反向隧道
5. **不支持 iOS**
6. **默认没有内置密钥** —— 源码里 `Auth.DEFAULT_SECRET` 是空字符串，需要自己填（见「三步上手」第 3 步）。留空也能用，靠地址里的 `?token=`

---

## 技术说明

```
app/src/com/alpha/dshfloat/
├── MainActivity.java    主界面：填地址、启动悬浮窗
├── FloatService.java    悬浮窗服务：窗口管理、拖动、缩放、WebView
├── Auth.java            认证：从 DSH 凭据推导签名
└── TidyCss.java         注入的 CSS：把 DSH 页面改造成适合窄屏的样子
```

- `tidy.css` 是注入到 DSH 页面的样式表，把宽屏布局压成手机竖屏可用的样子
- `gen-tidycss.ps1` 是生成 `TidyCss.java` 的脚本（把 CSS 转成 Java 字符串常量）

---

## 许可

MIT

如果打算用于**商业产品**（付费应用、嵌入商业系统等），欢迎先联系我：
**whnbsnm@foxmail.com**

不是因为不允许——MIT 已经授权了——而是想知道它被用在了哪里。
如果合适，我可以提供**适配、扩展或定制开发**。
