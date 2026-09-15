# 笔记本 App（安卓 APK）

把网页笔记本打包成安卓本地 App。**数据纯本地存储**（WebView localStorage，读写零延迟、不联网），
**换手机 / 发给别人**：用侧边栏的「导入 / 导出」生成 JSON 备份，微信、蓝牙、邮件随便传。

---

## ✨ 本次改动（相对网页版）

| 项目 | 说明 |
|------|------|
| **导入 / 导出** | 「目录」右侧两个同字号文字链接（**目录&nbsp;&nbsp;导入 · 导出**），点导出即分享备份、点导入即选 json |
| **语音输入** | 调用**安卓原生 SpeechRecognizer**（比浏览器灵敏，首次请求录音权限）；无引擎时自动回退 |
| **数据本地化** | 不再依赖 GitHub Gist，纯 localStorage，丝滑无延迟 |
| **原有功能** | 书写/阅读模式、横线格子、侧边栏目录、翻页手势、跳过空日期 —— **全部保留不变** |

---

## 🚀 三种方式出 APK（推荐第一种，零本地配置）

### 方式一：GitHub Actions 自动构建 ⭐ 强烈推荐
**不需要安装 Android Studio / SDK**，推到 GitHub 后自动编译，下载即可。

1. 在 GitHub 新建一个仓库（例如 `notebook-android`），选 **Public**
2. 把本工程全部文件上传到仓库根目录（可直接拖拽，或用 `git push`）
3. 进仓库 → **Settings → Actions → General → 勾选 "Read and write permissions"**（让 Actions 能上传产物）
4. 进 **Actions** 标签页 → 点左边 `Build APK` → **Run workflow**
5. 等约 3~5 分钟变绿 → 点进入 → 底部 **Artifacts** 区下载 `notebook-app-debug.zip`
6. 解压得到 `app-debug.apk`

> workflow 文件：`.github/workflows/build-apk.yml`，推送 `main` 分支会自动触发。

### 方式二：Android Studio（本地，最稳）
1. 电脑装 [Android Studio](https://developer.android.com/studio)，**Open** 选本目录
2. 等 Gradle 同步完成（首次需联网下载依赖）
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. 产物：`app/build/outputs/apk/debug/app-debug.apk`

### 方式三：命令行 Gradle
```bash
export ANDROID_HOME=/path/to/android-sdk   # 装有 SDK 33 + build-tools 33.0.2
./gradlew assembleDebug                    # 或：gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 安装到手机
1. 安卓手机 → 设置 → 安全 → 允许「未知来源应用」安装
2. 把 `app-debug.apk` 传到手机（微信文件 / USB / 网盘），点击安装
3. 首次打开会请求「录音」权限 → **允许**（语音输入需要）
4. 桌面上出现「笔记本」图标，点开即用，无地址栏、全屏

---

## 🔄 换手机 / 发给别人（核心需求）

### 导出（旧手机 / 发送方）
1. 打开 App → 点左上角 `≡` 打开侧边栏
2. 顶部「目录」右边 → 点 **导出**
3. 系统弹出分享框 → 选 **微信** → 发到「文件传输助手」或某个聊天
   - 文件形如 `notebook-backup-2026-09-16.json`
4. （也可选蓝牙、邮件、"保存到手机"等）

### 导入（新手机 / 接收方）
1. 新手机先装好同一个 App
2. 点侧边栏「目录」右边 → **导入**
3. 系统打开文件选择器 → 选中那个 json
4. 弹窗提示「确定=合并 / 取消=先备份当前再导入」→ 一般选 **确定（合并）**
5. 提示"导入成功，共 N 天的笔记" → 侧边栏目录已恢复全部日期

> **微信打不开 json 的解决办法**：先在微信里把文件「保存到手机/用其他应用打开」，
> 再到 App 里「导入」从存储里选它即可。备份文件就是普通文本，用任何编辑器都能打开查看。

---

## 🎤 语音输入说明
- 安卓原生 SpeechRecognizer：点麦克风说话 → 实时灰色预览 → 停顿时自动填入光标处
- 需要：手机已安装语音输入服务（一般出厂自带 Google 语音 / 厂商语音）、已授权录音权限
- 若语音不可用：仍可用键盘正常输入，不影响其他功能

---

## 📂 工程结构
```
.github/workflows/build-apk.yml   ← 自动构建（推荐）
app/src/main/
├── assets/index.html              ← 笔记本网页（已加导入导出 + 语音桥接）
├── java/com/notebook/
│   ├── MainActivity.java          ← WebView + JS桥接 + 导入导出(SAF) + 语音权限
│   └── VoiceRecognizer.java       ← 原生语音识别封装
├── res/                            ← 布局 / 图标 / 主题
└── AndroidManifest.xml
build.gradle / app/build.gradle     ← Gradle 构建脚本
```

## ⚙️ 技术要点
- WebView 启用 `JavaScript + DOM storage` → localStorage 可用
- JS ↔ Java 桥接对象名：**AndroidBridge**（导出/语音）与 **Notebook**（导入回调），双名兼容
- 导出用 SAF（`ActivityResultContracts.CreateDocument`）→ 无 FileUriExposed 问题，可分享到微信
- 导入用 SAF（`GetContent`）→ 支持微信/网盘/本地任意来源
- 支持从微信直接"打开为"本 App（`intent-filter` 已声明）

---
祝你用得顺手 🎉 有问题提 issue 或自行改 `index.html` 后重新构建即可。
