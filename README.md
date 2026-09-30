# KlinRead

一个简洁的安卓本地电子书阅读器。界面走透明液态玻璃风格，解析全部手写、零第三方解析库。

**这个 App 不包含、也不提供任何书籍或音乐内容。** 它只读取你自己导入的本地文件，
不联网、不上传。装好后书架是空的，需要你自己点「导入」选择文件。

---

## 目录

- [功能](#功能)
- [支持格式](#支持格式)
- [阅读界面](#阅读界面)
- [界面](#界面)
- [本地账号（目前未接入界面）](#本地账号目前未接入界面)
- [构建](#构建)
- [工程结构](#工程结构)
- [两个值得说明的设计决定](#两个值得说明的设计决定)
- [已知限制](#已知限制)
- [许可证](#许可证)

---

## 功能

底部导航共 **4 个板块**：

| 板块 | 内容 |
|---|---|
| **书架** | 书籍列表、「今日阅读」时长统计、底部「导入书籍」按钮 |
| **音乐** | 导入本地音轨播放；「读书时自动播放」「单曲循环」开关 |
| **设置** | 阅读亮度、三种阅读主题、全局深色开关、版本与支持格式说明 |
| **作者** | 作者与开发工具署名、捐赠入口、隐私说明 |

阅读相关设置在**阅读页内部**的底部弹窗里（字号 / 行距 / 页边距 / 翻页方式 / 主题），
不在「设置」页。

启动页带一个「每日一言」，从 `app/src/main/assets/text.txt` 读取，每 24 小时换一条。

---

## 支持格式

| 格式 | 说明 |
|---|---|
| **TXT** | 自动识别 UTF-8 / GB18030 / BOM；按章节标题切分 |
| **EPUB** | EPUB 2 / 3，按 spine 顺序，读取 nav / ncx 目录标题 |
| **FB2** | FictionBook 2（XML），提取 body 下的段落与标题 |
| **HTML** | 单文件网页书，去脚本/样式后取正文 |
| **MOBI / AZW / AZW3 / PRC** | PalmDOC 压缩的未加密书；HUFF/CDIC 压缩与 DRM 会明确报错 |
| **UMD** | 中文 UMD，解压 zlib 文本块 |

格式**靠文件内容判断，不靠扩展名**（见 `FormatDetector`）。这一点很关键——
部分安卓文件管理器返回的文件名不带扩展名，只按扩展名判断会误报「未知格式」。

解析全部手写（ZIP + OPF + HTML + PalmDOC + zlib），零第三方解析库，
好处是行为可验证、出问题能直接定位。

不支持：DRM 加密的 EPUB / MOBI、固定版式（fixed-layout）书籍、内嵌音视频。

---

## 阅读界面

- **三种主题**：浅色 / 护眼（默认）/ 深色
- **两种翻页方式**：上下滚动 / 左右平移
- **可调**：字号、行距、页边距，均为拖动时即时预览、松手才保存
- **目录**：独立入口，顶栏和底栏都能打开，显示章节数与当前章
- **进度**：记录到段落级，切换翻页方式或重开 App 都能回到原位

> 「覆盖」和「仿真」两种翻页方式**已被移除**：实际用起来都不对劲，
> 而一个半可用的仿真翻页比不提供更糟。这一点写在 `PageTurnMode` 的注释里。

### 关于"自动换行"

正文按**段落**逐条渲染，而不是整章塞进一个 `Text`。

原因是：一本长篇小说的单章可能有几十万字，一个巨大的文本节点会让排版和滚动都很卡；
更关键的是，如果源文件的 TXT **没有任何换行**（整本一行），整章会变成一段永远滚不完的文字。
`splitParagraphs` 会把超长行按 1200 字切开，所以无论源文件怎么排版，都能正常阅读。

### 关于设置面板的文字

底部弹窗（目录 / 阅读设置）用的是**阅读配色**，不是液态玻璃的 token。
玻璃 token 是半透明的，铺在弹窗表面上会让标题和滑块轨道淡到几乎看不见。

---

## 界面

四个底部导航板块，**透明液态玻璃**风格。液态玻璃不是贴一张毛玻璃图片，
而是三层效果叠加：

1. 半透明渐变填充
2. 顶部 1.4px 的**边缘高光**（rim light）——这是"玻璃感"的关键
3. 0.6dp 的细边框

背景是一层**缓慢漂移的彩色渐变**（24 秒一个来回）。玻璃只有在背后有东西时
才像玻璃，所以这层背景不是装饰，是面板要"透"出来的内容。

真正的实时背景模糊需要 `RenderEffect`（API 31+），为了兼容 minSdk 24 没有用。

---

## 本地账号（目前未接入界面）

`data/AccountStore.kt` 仍在仓库里，但**当前的 UI 没有任何地方引用它**——
底部导航的「我的」板块已经被「作者」页取代。这个文件目前是**死代码**，
保留下来是为了以后可能重新接入。

如果你打算接回来，先读清楚它是什么：**这不是真正的登录系统，是一个本地身份标识。**

- 数据只存在本机 DataStore，**不联网、不上传**
- 密码用 MD5 存，**只是为了不明文落盘**，没有加盐和拉伸，不构成安全防护
- 拿到设备 app 数据的人可以轻松还原
- ID 生成后不可修改（没有服务器来协调唯一性）
- 「退出」= 清除本地数据，账号就没了，无法找回

如果你要的是真实账号系统（服务端、密码找回、多设备同步），那需要另外做后端。

---

## 构建

### 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17 或更高（Gradle 需要；`compileOptions` 为 Java 17） |
| Android SDK | Platform **android-36**，Build-Tools 36.x |
| Android Studio | 任意较新版本，或纯命令行构建 |

`local.properties` 里的 `sdk.dir` 指向本机 SDK，已被 `.gitignore` 忽略，
Android Studio 打开工程时会自己生成。

> `gradle.properties` 里原本有一行 `org.gradle.java.home` 指向作者本机的
> JetBrains Runtime，已注释掉——在别的机器上该路径不存在，会导致 Sync 直接失败。
> 需要固定 JDK 时，取消注释并改成你自己的路径，或在
> Settings > Build, Execution, Deployment > Build Tools > Gradle 里设置。

### 命令行的构建

```powershell
$env:JAVA_HOME = "<你的 JDK 路径>"
$env:ANDROID_HOME = "<你的 Android SDK 路径>"
.\gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

把工程根目录下的 APK 复制出来（等价于 `assembleDebug` + 复制）：

```powershell
.\gradlew.bat exportApk
```

### 装到手机上

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 测试

```powershell
.\gradlew.bat test
```

当前有 **35 个单元测试**，覆盖两个最容易出静默错误的地方：

| 测试类 | 数量 | 覆盖内容 |
|---|---|---|
| `ChapterSplitterTest` | 18 | 章节标题的各种写法、回退行为、切分完整性 |
| `TextDecoderTest` | 17 | UTF-8 / GB18030 / BOM 判定、UTF-8 严格校验 |

其中包含**两个回归测试**，对应下面「章节切分的两个坑」里提到的两处，
把它们固定下来，避免以后改坏。

#### 如果 `gradlew test` 跑不起来

在某些受限环境下 Gradle 无法 fork 测试 worker 进程，会报：

```
ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain
```

这是环境限制，不是代码问题——测试代码本身编译正常。这类测试是纯 JVM 逻辑测试，
不需要 worker 隔离，可以用附带的脚本绕过 Gradle 直接跑：

```powershell
pwsh tools\run-tests.ps1
```

脚本会先编译测试代码，再从 Gradle 缓存里找到 JUnit 与 Kotlin 标准库，
用 `JUnitCore` 直接执行，效果和 `gradlew test` 等价。

**在 Android Studio 里用图形界面跑测试不受这个限制。**

#### CI

`.github/workflows/build.yml` 在每次 push / PR 到 `main` 时自动：

1. 构建 debug APK —— 保证代码能编译通过
2. 运行单元测试 —— 保证测试通过
3. **核对实际执行的用例数是否等于 35** —— 这一步是为了防止「测试类没被收集到、
   一个都没跑，却显示成功」这种假阳性

因此新增或删除测试时，要同步修改 workflow 里的 `EXPECTED`（目前是 35），
否则 CI 会以「用例数不符」失败。

---

## 工程结构

```
app/src/main/java/com/klin/read/
├── MainActivity.kt              导航（书架 / 音乐 / 设置 / 作者）+ 阅读页，edge-to-edge
├── data/
│   ├── BookEntity.kt            书架条目：只存 SAF URI，不存正文
│   ├── BookDao.kt               查询、去重、更新打开时间
│   ├── ReaderDatabase.kt        Room 数据库
│   ├── ReaderPreferences.kt     DataStore：阅读设置 + 每本书位置
│   ├── AccountStore.kt          本地账号（死代码，界面未引用）
│   ├── MusicStore.kt            音乐库
│   └── ReadingStatsStore.kt     阅读统计
├── reader/
│   ├── ParsedBook.kt            解析结果：全文 + 章节边界
│   ├── TextDecoder.kt           编码识别（UTF-8 / GB18030 / BOM）
│   ├── ChapterSplitter.kt       章节切分
│   ├── FormatDetector.kt        按内容判断格式
│   ├── BookParser.kt            入口，EPUB 扩展点
│   ├── EpubParser.kt            EPUB 2 / 3
│   └── ExtraFormats.kt          FB2 / HTML / MOBI / UMD
├── importer/BookImporter.kt     URI → 书架条目，含持久化读取权限
├── music/MusicPlayer.kt         本地音轨播放
└── ui/
    ├── theme/Theme.kt           Material 3 配色
    ├── design/                  液态玻璃组件与设计 token
    ├── shelf/                   ShelfScreen + ShelfViewModel
    ├── reader/                  ReaderScreen + ReaderViewModel + 配色
    ├── music/                   音乐页
    ├── splash/                  启动页与每日一言
    ├── settings/                设置页
    ├── about/                   作者与捐赠
    └── nav/BottomBar.kt         底部导航
```

---

## 两个值得说明的设计决定

**书架只存 URI，不存正文。** 数据库里没有任何书籍内容，App 也没有任何一份文件的副本。
`takePersistableUriPermission` 让重启后仍能打开，代价是用户删掉原文件后书架条目会失效。

**进度存字符偏移，不存页码。** 页码依赖字号、行距、屏幕尺寸，
用户一改字号就全乱了。字符偏移则与这些无关。

### 章节切分的两个坑

切分逻辑（`ChapterSplitter`）踩过两个真实 bug：

1. **不能要求标题前必须有空行。** 实际导出的 TXT 里，标题通常紧跟在正文段落后，
   没有空行——按「前面必须是空行」判断会导致整本书被识别成「全文」一章。
   现在改由标题规则本身过滤。

2. **独立标题的正则结尾不能用 `.*$`。** 否则任何以「引子」「序」开头的正文行都会匹配。
   独立标题必须**独占一行**。

支持的章节标题写法：

```
第一章 标题        第1章 标题
第 12 章：标题      第十二回 标题
楔子 / 序章 / 尾声 / 后记   （需独占一行）
```

识别不到章节时会退化成单章「全文」，仍然可以正常阅读。

---

## 已知限制

- **Compose UI 的交互细节未在设备上逐项验证**：翻页、字号切换、目录跳转、进度恢复
  在真机上的表现还没有逐条点击确认过。
- **`AccountStore.kt` 是死代码**：没有任何界面引用它，见[本地账号](#本地账号目前未接入界面)。
- **无存储权限**：书籍通过 Storage Access Framework 打开，由用户逐个授权，
  `AndroidManifest.xml` 里没有声明任何存储权限。
- 不支持 DRM 内容与固定版式书籍。

---

## 许可证

[MIT](LICENSE)
