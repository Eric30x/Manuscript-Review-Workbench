# 审稿工作台 · 原生安卓 MVP（构建与演示）

> 这是"AI 新闻稿审稿工作台"的可运行 MVP：**原生安卓（Kotlin + Jetpack Compose）**，纯本地运行，不连服务器、不申请存储权限、不联网。
> 口径以 `../README.md` 为准。

---

## 零、交接：把哪些文件给队友

**给整个 `android/` 工程目录（源码），不是 APK，也不是 build 产物。**

- **要带（缺一不可）**：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle/`（含 `wrapper/gradle-wrapper.jar`、`libs.versions.toml`）、`app/`（`src/`、`build.gradle.kts`、`proguard-rules.pro`）、`docs/`（演示截图）、`.gitignore`、本文件。
- **别带**（`.gitignore` 已排除；手动打包时请删掉）：`build/`、`app/build/`、`.gradle/`、`.kotlin/`、`.idea/`、`local.properties`（各人 SDK 路径不同，Android Studio 首次打开会自动生成）、`*.iml`。

两种给法，任选：

1. **打包 zip**：只留上面“要带”的内容，右键压缩成一个 zip 发群里。
2. **走 Git（推荐）**：把 `android/` 里的内容提交到团队仓库的 `android/` 目录；队友 `git clone` 后用 Android Studio 打开 `android/` 即可。

> **校赛交的就是这个**：`00-赛事资料/提交清单与硬约束.md` 第 7 项要"作品应用程序源代码"，命名 `团队名称-作品应用程序源代码.zip` —— 只能用 zip（不能 rar / 7z），且必须可运行。里面装的就是 `android/` 源码，**不是 APK**。

**直接把这段话发群里（队友照着做）：**

> 拿 `android/` 这个目录（源码，不是 APK）。① Android Studio 用 **Open** 打开 `android/` 这一层 → ② 等右下角 Gradle Sync 跑完（第一次要联网，20–60 分钟；**同步没完成前 Run ▶ 一定是灰的**）→ ③ 手机开 USB 调试插上（或 `Tools → Device Manager` 建个 Pixel API 35 模拟器）→ ④ 顶部设备下拉选设备，点绿色 ▶ 或按 `Shift+F10` → 设备上出现"审稿工作台"就成功了。卡住了看同目录 `README-构建与演示.md` 的"报错了怎么办"。

---

## 一、第一次运行

> **现状（2026-11-26）**：`android/` 已在 Android Studio 上**编译通过、整条流程跑通**；模拟器与真机（小米 `25053RT47C`，Android 16 / API 36）都实测「`Shift+F10` → 安装 → 启动」成功，截图见 `docs/demo-shot-0*.png`。

### 最快复现路径（已装好 Android Studio 的机器）

1. 用 Android Studio **Open** 打开 `android/` 这一层（含 `settings.gradle.kts`）。
2. **先等 Gradle Sync 跑完**：底部状态栏会走 `Importing 'android' Gradle Project` 进度条，本机约 2–3 分钟（新机器要下依赖，20–60 分钟）。**同步没成功之前 Run ▶ 一定是灰的**，这是最常见的一次性障碍。
   - 手动触发同步：右上角大象图标，或菜单 `File → Sync Project with Gradle Files`（快捷键 `Ctrl+Shift+O`）。
   - 同步成功的标志：项目视图能展开出 `app` 模块，工具栏出现 `app` 配置 + 一台设备，▶ 变绿。
3. 顶部设备下拉选中**模拟器**或 **USB 调试的真机**；没有模拟器就先 `Tools → Device Manager` 建一个（Pixel 类机型 + API 35 x86_64）。
4. 点绿色 **Run ▶**（`Shift+F10`）。首次编译 1–3 分钟，设备上出现 **审稿工作台** 图标即成功。

### 懒人版：双击 `一键装到手机.bat`（推荐给不熟 Studio 的人）

不想在 Studio 界面里折腾就用这个：**双击工程根目录的 `一键装到手机.bat`**，它会自己走完「Gradle 编译 → adb 安装 → 手机上自动打开」。

- 前提：手机已开 USB 调试并用数据线连着（或先开好模拟器）。
- 它做的事和 Studio 那个绿色 ▶ 完全一样，只是把点击换成了命令。失败时会打印中文提示（设备没连、编译报错等）。
- 有真机和模拟器同时在，它会优先装到**真机**。
- 想指定某一台：先 `set ANDROID_SERIAL=设备序列号` 再运行。

实测可用环境（照这个装最省事）：

| 项 | 实测值 |
|---|---|
| Android Studio | 2026.x（AI-262.10968.63，比 Ladybug 更新，兼容） |
| Android SDK | `platforms/android-35` + `build-tools/34.0.0` + `platform-tools` + `emulator` |
| 模拟器 AVD | Pixel 类机型（如 `Medium_Phone_API_37.0`），x86_64 + google_apis |
| 编译产物 | `app/build/outputs/apk/debug/app-debug.apk`（约 9.9 MB） |
| 真机实测 | 小米 `25053RT47C`（Android 16 / API 36），USB 调试直连，`Shift+F10` 安装 + 启动成功 |
| 本机路径（仅参考，别照抄） | Android Studio 在 `D:\dev\apps\android studio`；SDK 在 `C:\Users\<用户名>\AppData\Local\Android\Sdk`；工程在 `...\02-新闻稿审稿工作台\android` |

### 从零开始（队友还没装 Android Studio）

预计占 **6–8 GB**：Android Studio 约 1.3 GB + SDK 约 2–4 GB + 首次同步依赖 1–2 GB。C: 紧张就把 Android Studio 与 SDK 都装到 D:（Gradle 依赖缓存仍占 C: 约 3–4 GB）。

下面这张表是工程本身的技术栈，写文档 / 答辩引用时用得上。

| 项 | 值 |
|---|---|
| 语言 / UI | Kotlin 2.0.21 + Jetpack Compose（Material 3） |
| 构建 | AGP 8.7.3 + Gradle 8.9（首次同步自动下载） |
| SDK | compileSdk 35 / targetSdk 35 / **minSdk 26（Android 8.0 及以上都能装）** |
| Java | 不用单独装：Android Studio 自带 JBR，用 `jbr-21` 或 17 都行 |
| 需要的 Android Studio | Ladybug（2024.2）或更新 |
| 应用 id | `com.newsreview.workbench` |

### 第 1 步 · 装 Android Studio（下载约 1.3 GB，15–30 分钟）

> 下面给的 `D:\...` 只是**建议路径**（为了给 C: 省空间），跟本机实测路径不同；按自己磁盘的实际情况改就行。

1. 打开官方下载页 <https://developer.android.com/studio>，点 **Download Android Studio**（官网太慢就换个网络或开热点，这是唯一必须下的大件）。
2. 运行安装包时选 **Custom（自定义安装）**，把安装路径改成 `D:\Program Files\Android\Android Studio`。
3. 装完第一次启动会走 Setup Wizard：
   - 选 **Standard（标准安装）**；
   - 走到 **SDK Components Setup** 这一步时，**把 Android SDK Location 改成 `D:\Android\Sdk`**（默认在 C: 的 `AppData\Local\Android\Sdk`，C: 空间不够）；
   - 勾选许可协议，等 SDK 下完（约 2–4 GB）。
4. 确认一下：`Settings → Languages & Frameworks → Android SDK`，路径应为 `D:\Android\Sdk`。

### 第 2 步 · 打开本项目，等第一次同步



欢迎页点 **Open** → 选中你放源码的那一层目录（里面含 `settings.gradle.kts` 的 `android` 文件夹）→ 信任项目。

第一次同步会下载 AGP、Compose 等依赖，**大约 1–2 GB / 20–60 分钟**。同步没变绿之前别点 Run。

### 第 3 步 · 万一报 Gradle wrapper 相关错误

`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar` 都已在工程里，正常同步不会缺。若仍报 `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` 或 `Gradle wrapper jar not found`，按下面任一方法补上：

1. **让 Android Studio 生成一份，拷过来（最稳）**
   1. 欢迎页 **New Project → Empty Activity** → 名字随便（例如 `wraptemp`）→ Finish；
   2. 等这个空工程自己同步完（它会顺手把 wrapper 生成好）；
   3. 进 `<你的用户目录>\AndroidStudioProjects\wraptemp\gradle\wrapper\`，把 **`gradle-wrapper.jar`** 复制到本项目 `android\gradle\wrapper\` 下；
      顺手把同工程的 `gradlew`、`gradlew.bat` 拷到 `android\` 根目录，以后就能用命令行 `gradlew.bat assembleDebug`；
   4. 回到本项目，点 **Sync Now**（右上角大象图标，或 File → Sync Project with Gradle Files）。
2. **改用它自带的 Gradle**：`Settings → Build, Execution, Deployment → Build Tools → Gradle`，把 "Use Gradle from" 从 `gradle-wrapper.properties file` 改成 **`Specified location`**，指向 Android Studio 已下好的 Gradle 目录（形如 `<你的用户目录>\.gradle\wrapper\dists\gradle-8.9-bin\...`），再 Sync。

> 更省事：Android Studio 装好后把安装路径发我，我直接帮你把 wrapper 这几个文件补齐，并逐条核对同步报错。

### 第 4 步 · 同步慢得不动，就开镜像

`settings.gradle.kts` 里已经写好一段**国内镜像**（默认注释掉）。把 `pluginManagement`、`dependencyResolutionManagement` 里那几行 `// maven { url = uri("https://maven.aliyun.com/...") }` 前面的 `//` 去掉，再重新 Sync，通常能从"几十分钟"变成"几分钟"。Gradle 本体下载慢的话，`gradle/wrapper/gradle-wrapper.properties` 顶部注释里给了两个国内镜像地址，换上去即可。

### 第 5 步 · 准备一台"安卓设备"

- **有安卓手机（最快，推荐）**：手机 **设置 → 关于手机 → 连续点 7 次"版本号"** 打开开发者模式 → 回到 设置 → 系统 → 开发者选项 → 打开 **USB 调试**；数据线插到电脑，手机弹窗点"允许 USB 调试"。Android Studio 顶部设备下拉里就会出现手机型号。
- **没手机，用模拟器**：**Tools → Device Manager → Create Device** → 选 **Pixel 7**，系统镜像选 **API 35（Android 15）x86_64** → Finish（再下约 1.5 GB）。
  - 模拟器起不来、报 hypervisor 相关错误：`Settings → Android SDK → SDK Tools` 勾上 **Android Emulator hypervisor driver** 安装；仍然不行就是 BIOS 里没开虚拟化（Intel VT-x / AMD-V）。

### 第 6 步 · 跑起来

顶部选中设备，点绿色三角 **Run ▶**（或 `Shift+F10`）。第一次编译约 1–3 分钟，设备上会出现一个名为 **审稿工作台** 的图标，点开就是角色选择页；接着按第二节的演示脚本走一遍。

### 报错了怎么办

| 现象 | 处理 |
|---|---|
| 同步报 wrapper 相关错误 | 见第 3 步 |
| 长时间卡在 Downloading | 见第 4 步（开镜像） |
| 报 `Unsupported class file major version` | `Settings → Build Tools → Gradle → Gradle JDK` 选自带的 `jbr-21`（或 17） |
| 报 `SDK location not found` | 项目根目录新建 `local.properties`，写一行 `sdk.dir=D:/Android/Sdk` |
| **Run 按钮一直灰、点不动** | 基本只有一个原因：**Gradle Sync 没成功**。按 `Ctrl+Shift+O` 手动同步一次，等底部进度条走完 —— 同步成功后工具栏会出现 `app` 配置，▶ 立刻变绿。同步过还灰就 `File → Invalidate Caches / Restart` 重启 IDE。 |
| 同步报 `Your project path contains non-ASCII characters` | 工程路径带中文（如"02-新闻稿审稿工作台"）会触发 AGP 的路径保护，Sync 直接失败、Run 就灰了。`gradle.properties` 里已写好 `android.overridePathCheck=true`，确认这一行在、再 Sync 即可（纯 Kotlin / Java 工程不受中文路径影响）。 |
| Run 按钮灰着 / 设备列表是空的 | 第 5 步没做完；真机没弹"允许调试"就拔插重来 |
| 中文显示成方块 | 一般不会（字体由系统提供）；真遇到就把设备语言切成中文 |
| `processDebugResources FAILED` 并报“另一个程序正在使用此文件” | 有旧的 Gradle 进程占着构建产物：在工程根目录执行 `.\gradlew.bat --stop`，再重新 Run |

> 提醒：代码已在 Android Studio 编译通过、并在模拟器与真机跑通（见 `docs/` 截图）。第一次在**新机器**上打开仍需联网同步依赖，请预留半小时处理环境问题。

---

## 二、演示脚本（照着点就行，约 4 分钟）

演示数据是**虚构**的：没有任何单位名、真名、学号；角色姓名用"林小舟 / 沈佳 / 顾言 / 郑毅 / 何书"。

1. **写稿人**（林小舟）进入 → 首页看"待我处理"是空的 → 点 **最近稿件** 里状态为「已退回」的《校园开放日报名情况》。
2. 详情页三件事顺着看：**批注与意见**（12 条，含二审的退改意见）→ 右上角 **AI 检查** 看逐条证据链 → **稿件原文** 看红底/橙底高亮 → **流转记录** 看谁在什么时候做了什么。
3. 在 AI 报告里点一条错别字的 **采纳建议**，回原文能看到字已经改了；再点 **加批注** 把一条 L2 疑点转成人工意见。
4. 退回原因（标题口径与实际到场人数不一致）看明白后，点底部 **继续修改并重提** → 编辑页改标题与人数 → **保存并重新送审** → 选发布渠道 → 确认送审。
5. **切换角色 → 一审 · 编辑初审**（沈佳）：待我处理里还有一篇《学院举办志愿服务培训》在等初审，点开 → **通过并送下一审**（演示一审平时的工作台）。
6. **切换角色 → 二审 · 指导老师复审**（顾言）→ 待我处理里有 2 篇，处理《校园开放日报名情况》→ **通过并送下一审**。
   > 值得在这里停一下：**一审没有再出现**。退回只回到被打回的那一级，已经通过的环节不重审 —— 这是答辩时值得点出来的一处设计。
7. **切换角色 → 终审 · 负责人**（郑毅）→ **确认签发** → 回到"稿件原文"页签，底部出现绿色卡片：**文末三审署名行（签发时自动写入）**，初审 / 复审 / 终审三个姓名齐全。这就是学校通知里点名要求、又最容易漏的那一行。
8. **切换角色 → 发布人员**（何书）→ **导出签发版** → 系统分享面板（可存到文件 / 发到微信）；**标记已发布** 完成归档。

另外两个入口也值得演示：

- 首页（写稿人）**导入 Word 稿件（样例自带修订痕迹）**：走的是真实的 .docx 解析（zip + OOXML），演示数据里故意放了 2 处 Word 修订（一处插入、一处删除），导入后提示"识别到 2 处修订（默认接受并留痕）"，并且立即跑出一轮 AI 预检（错别字 / 规范用语 / 敏感词）。
- 首页 **参考资料**：通知、签到表、议程、录音转写四份材料，以及 3 条带时间戳的录音转写——AI 的每条依据都能指回这里的具体行。

---

## 三、代码结构与分层

```
app/src/main/java/com/newsreview/workbench/
├── MainActivity.kt              唯一的 Activity
├── WorkbenchViewModel.kt        界面与业务之间唯一接口（含导出 .docx、导入 Word）
├── domain/                      纯 Kotlin，不碰任何 Android API → 可跑 JVM 单元测试
│   ├── model/Models.kt          角色、状态、批注、证据链、版本、材料
│   ├── check/ArticleChecker.kt  AI 三层检查（L1 文字 / L2 一致性 / L3 判断）
│   ├── rule/AhoCorasick.kt      多模式匹配（词库几万条也是一次线性扫描）
│   ├── rule/WordLists.kt        错别字 / 敏感词 / 规范用语三张表
│   ├── flow/ReviewFlow.kt       流程状态机 + 文末三审署名行
│   └── docx/DocxReader|Writer   零依赖 .docx 读写（zip + XmlPullParser）
├── data/
│   ├── WorkbenchRepository.kt   状态仓库（StateFlow，进程内共享）
│   └── DemoData.kt              演示稿件与材料（全部虚构）
└── ui/
    ├── AppNavigation.kt         路由图 + 全局 Snackbar
    ├── Common.kt                顶栏、卡片、标签、把握度条、证据链
    ├── Share.kt                 FileProvider 分享导出文件
    ├── components/              分段选择、原文高亮、批注卡、流转轴、弹窗、AI 批注卡
    └── screens/                 登录 / 首页 / 稿件详情 / AI 报告 / 编辑 / 词表 / 材料

app/src/test/java/...            纯 domain 层单元测试（检查器不变量、流程、匹配算法）
app/src/main/assets/demo/        演示用 .docx（真实 OOXML 包，含 2 处修订痕迹）
```

分层原则：**业务规则全部在 `domain/`，只依赖 Kotlin 标准库**。所以"错别字能不能抓出来""退回后回到哪一级""署名行怎么生成"这些问题可以用单元测试锁住，不用起模拟器。

---

## 四、几个刻意的技术选择（答辩时会被问到）

| 选择 | 为什么不是更"主流"的那个 |
|---|---|
| **Compose 而不是 XML 布局** | 一条稿件有四种状态 × 六种角色，用状态驱动 UI 比写一堆 layout + 状态同步代码更短、更不容易漏状态。声明式 UI 也正好对得上"审稿是状态机"这件事。 |
| **词库 Aho–Corasick，而不是逐词 `contains`** | 词库几百上千条时，逐词匹配是 O(词数 × 文本长度)；Aho–Corasick 是一次扫描出全部命中，还能直接给区间——正文红线的区间就是它给的，点"采纳"改的也是同一段区间。 |
| **`.docx` 自己解析（zip + XmlPullParser），而不是引 Apache POI** | POI 在安卓上要拖进 xmlbeans 等一大堆依赖、还要开脱糖，容易在真机崩。`.docx` 本质就是"zip + 一个 document.xml"，用系统自带的 `java.util.zip` 与 `XmlPullParser` 解析正文段落足够稳，**零依赖、零体积增长**。代价：不解析样式、表格、页眉页脚（MVP 用不到）。 |
| **本机跑，不连服务器** | 稿件、签到表、录音转写都属于"发布前不宜外流"的材料。放在本机是产品的一部分，也省掉了整套后端。后续如要接大模型，只替换 `ArticleChecker` 的实现，界面与流程不用改。 |
| **AI 不占一个审核环节** | 学校通知明确要求"三审由不同审核人员把关"。让 AI 去充当一级，既不符合制度，也把责任推给了机器。所以 AI 是**每一级都能调用的辅助证据层**：不签字、不改状态，只出证据和把握度。 |
| **把握度 = 证据链增量之和** | 直接给一个"87% 可能是错的"没法被质疑也无法被追溯。把每一条依据的贡献拆开摆出来，审核人能看到机器"凭什么这么说"，也可以不采纳。 |

---

## 五、已知限制（写进文档，别在答辩上被问住）

1. **没有持久化**：状态在内存里，杀掉进程会回到演示数据（下一步换 Room，界面层不用改）。
2. **没有账号体系**：登录页是角色直选，用来演示三级审核由不同人完成。
3. **图片只做占位**：展示了"图片属于审核范围"的位置，没有做画面识别。
4. **发送到服务器 / 公众号的接口没接**：导出的是带三审署名行的 `.docx`，由发布人员人工上传。
5. **评测指标还没跑**：需要人工标 30 条语料才能给出准确率 / 误报率（这是下一件要紧事）。
6. **`.docx` 解析不覆盖样式与表格**：只取正文段落，修订默认接受并保留痕迹清单。

---

## 六、提交前的匿名红线

- 本目录内所有文件**不得出现学校、学院、专业、班级、姓名、学号、指导老师**；正文里需要指代时统一写"本校"。演示数据为虚构姓名，与真实人员无关。
- `app/build.gradle.kts`、`AndroidManifest.xml` 里的名称与 id 均为中性命名（`com.newsreview.workbench`）。
- 打包时记得带团队名（见 `../../00-赛事资料/提交清单与硬约束.md`）。
