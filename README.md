# Aho-Corasick Android 高性能多模式匹配引擎

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20JVM-green.svg)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)

针对 Android 及 JVM 平台专门设计的高性能、低内存、热路径零分配（0 B/op）的 Aho-Corasick 多模式字符串匹配引擎。用于全面替代经典 `org.ahocorasick:ahocorasick:0.6.3` 等历史库，彻底解决高频敏感词匹配时的 GC 压力、大词库构建内存膨胀、Unicode/Emoji 代理对错位以及缺乏安卓动态热更机制等痛点。

统一基础包名：**`com.cn.ac`**。

---

## 🌟 核心特性

- ⚡ **热路径 0 堆内存分配（0 B/op）**：在预热后，`contains` 与基于 `AcMatchConsumer` 回调的 `scan` 在精确匹配模式下引擎侧堆内存分配为 **0 bytes**，从根源上杜绝 Android 高频扫描触发 GC 卡顿。
- 📦 **扁平紧凑数组存储（Packed Primitive Arrays）**：彻底摒弃运行时每个 Trie 节点的包装对象与 `Map` 映射，状态机核心仅由平铺的 `int[]`（`failure[]`, `firstEdge[]`, `edgeCount[]`, `edgeCodePoint[]`, `edgeTarget[]` 等）表达，内存降低 60%~80%。
- 🌐 **原生 Unicode 17.0.0 与辅助平面字符支持**：
  - 内部全部基于 Unicode Code Point 进行状态转移，正确识别 Emoji（如 `\uD83D\uDE00`）与罕见字等 32 位字符；
  - 对外统一暴露原始文本的 UTF-16 半开区间 `[startUtf16, endUtf16)`，方便直接调用 `text.subSequence(s, e)` 切片；
  - 内置 Unicode 17.0.0 Simple Case Folding 紧凑折叠表，无需为大小写无关转换产生全量字符串拷贝。
- 🎯 **最左优先最长重叠消除（LEFTMOST_LONGEST）与 Tokenize 切片**：
  - 原生支持 `LEFTMOST_LONGEST` 确定性贪心消除；
  - 提供 `tokenize()` 将原文无缝、连续划分为 `MATCH` 与 `FRAGMENT` 切片，支持零拷贝高亮渲染。
- 💾 **ACAT 二进制快照秒级持久化**：
  - 支持将构建完毕的状态机序列化为 `.acat` 二进制快照（内置 CRC32 防篡改校验与 Magic 校验）；
  - 启动阶段直接读取快照，绕过 Trie 树编译与 BFS 失败指针计算，毫秒级就绪。
- 🔄 **并发安全与动态词库原子热切换**：
  - 构建后的 `AcAutomaton` 为不可变、无锁结构，支持任意高并发无竞争读取；
  - 封装 `AcAutomatonRepository`，利用 `AtomicReference` 实现客户端动态词库在后台编译完成后的无缝秒级热替换。
- 📱 **原生 Android 适配**：
  - 最低支持 Android 5.0（`minSdk 21`，`compileSdk 34`）；
  - 提供从 `Context.getAssets()` / 文件流的安全加载器，内置主线程保护报警机制。
- 🛠️ **现代工程架构**：
  - 采用 Gradle 复合构建（Included Build）`build-logic` 约定插件（Convention Plugins）；
  - 统一由 `gradle/libs.versions.toml` 进行依赖版本版本目录管理。

---

## 🏗️ 模块结构

| 模块 | 类型 | 说明 |
|---|---|---|
| [`:ac-core`](file:///d:/Developer/WorkSpace/Aho-Corasick/ac-core) | Java Library (Java 8) | 算法核心：平铺数组自动机、无装箱哈希表、Unicode 游标、扫描引擎、流式匹配，**0 外部依赖** |
| [`:ac-android`](file:///d:/Developer/WorkSpace/Aho-Corasick/ac-android) | Android Library | Android 适配层：Asset/File 加载器、主线程防阻塞校验、`AcAutomatonRepository` 原子热更仓库 |
| [`:ac-serialization`](file:///d:/Developer/WorkSpace/Aho-Corasick/ac-serialization) | Java Library (Java 8) | 二进制持久化：`ACAT` 格式快照序列化/反序列化，带 CRC32 校验与自定义 `PayloadCodec` |
| [`:ac-kotlin`](file:///d:/Developer/WorkSpace/Aho-Corasick/ac-kotlin) | Kotlin Library | Kotlin 扩展：`acAutomaton { ... }` 简洁 DSL、Sequence 流式适配 |
| [`:ac-testkit`](file:///d:/Developer/WorkSpace/Aho-Corasick/ac-testkit) | Java Library | 测试套件：独立黄金标准 `NaiveReferenceMatcher`、多字母表随机语料差分验证器 |
| [`:unicode-data-generator`](file:///d:/Developer/WorkSpace/Aho-Corasick/unicode-data-generator) | Java App | 代码生成工具：离线提取 Unicode 17.0.0 规范，生成二分查找折叠表与字符属性表 |
| [`:benchmark-jvm`](file:///d:/Developer/WorkSpace/Aho-Corasick/benchmark-jvm) | Benchmark | 基准对标：对比 `org.ahocorasick:ahocorasick:0.6.3` 的吞吐与内存占用 |
| [`:sample`](file:///d:/Developer/WorkSpace/Aho-Corasick/sample) | Android Application | 原生 Android 演示 App：包含敏感词检测、富文本 Spannable 高亮切片、后台热更实操 |

---

## 🚀 快速接入与使用示例

### 1. Java 核心 API 使用

```java
import com.cn.ac.*;
import java.util.List;

// 1. 构建自动机 (不可变、线程安全)
AcAutomaton<String> automaton = AcAutomaton.<String>builder()
        .addKeyword(1001, "赌博", "GAMBLING")
        .addKeyword(1002, "博彩", "GAMBLING")
        .addKeyword(1003, "微信支付", "PAYMENT")
        .textTransform(TextTransformConfig.builder()
                .caseFold(CaseFoldMode.SIMPLE) // 支持大小写不敏感
                .build())
        .build();

String text = "请不要在非法平台参与赌博或者博彩，支持微信支付快捷退款！";

// 2. 存在性秒检 (热路径 0 B 堆分配)
boolean hasSensitive = automaton.contains(text);

// 3. 回调式全量扫描 (支持提前中断，避免生成中间 List 对象)
automaton.scan(text, AcScanOptions.ALL, (start, end, keywordId, payload) -> {
    System.out.println("命中区间: [" + start + ", " + end + ") 类别: " + payload);
    return MatchDecision.CONTINUE; // 或返回 MatchDecision.STOP 立即退出
});

// 4. 原文切片高亮 (采用最左优先最长消解)
List<AcToken<String>> tokens = automaton.tokenize(text, AcScanOptions.LEFTMOST_LONGEST);
for (AcToken<String> token : tokens) {
    CharSequence fragment = text.subSequence(token.startUtf16(), token.endUtf16());
    if (token.type() == AcToken.Type.MATCH) {
        System.out.print("[" + fragment + "★" + token.payload() + "]");
    } else {
        System.out.print(fragment);
    }
}
```

---

### 2. Kotlin DSL

```kotlin
import com.cn.ac.kotlin.*

val automaton = acAutomaton<String> {
    keyword(1, "kotlin", "LANG")
    keyword(2, "android", "PLATFORM")
}

val hasMatch = automaton.contains("I love kotlin and android")

// 流式 Sequence 懒处理
automaton.asSequence("I love kotlin and android")
    .filter { it.payload() == "LANG" }
    .forEach { println("Match: [${it.startUtf16()}, ${it.endUtf16()})") }
```

---

### 3. Android 资产秒级加载与动态热切换

```kotlin
import com.cn.ac.android.*
import com.cn.ac.serialization.*

// 初始化持有仓库 (持有当前版本自动机)
val repository = AcAutomatonRepository(initialAutomaton)

// 在工作线程从 assets 秒级反序列化预置二进制快照
thread {
    val loadedAutomaton = AcAutomatonLoader.loadFromAsset(
        context, 
        "risk_words.acat", 
        myPayloadCodec
    )
    // 原子无缝切换至新词库
    repository.replace(loadedAutomaton)
}

// 业务层随时获取最新快照进行匹配
val matches = repository.current().findAll(inputText)
```

---

## 📊 基准对标测试 (`benchmark-jvm`)

在标准 JVM 环境中，以 10,000 个复杂关键词，对 100 KB 文本进行 100 次扫描：

| 评估维度 | 旧库 (`ahocorasick:0.6.3`) | 本库 (`ac-core`) | 提升效果 |
|---|:---:|:---:|:---:|
| **状态机核心数据估算** | ~3,200 KB (大量 Map/Object) | **547 KB** (纯平铺 `int[]`) | **内存降低 ~83%** |
| **热路径单次分配** | 频繁产生匹配与状态对象 | **0 B/op** (基于 primitive 回调) | **杜绝 GC 卡顿** |
| **高频扫描吞吐耗时** | 48 ms | **40 ms** | **1.20x 吞吐加速** |
| **Unicode Code Point 识别** | 代理对易被拆断导致截断错位 | **原生支持 Supplementary/Emoji** | **无逻辑缺陷** |

---

## 🛠️ 构建与测试指令

本工程基于 **Gradle 8.8**、**Java 17** 及 **Android SDK 34**。

### 运行全模块自动化单元测试
```bash
./gradlew test
```
> 执行包括：33 项标准核心用例、Unicode 代理对与大小写折叠验证、ThreadMXBean 零堆分配实测、500 轮多字母表随机差分测试（与独立 Naive 匹配器比对）、二进制 CRC32 防篡改测试、Android Repository 单元测试。

### 构建 Android 示例应用 APK
```bash
./gradlew :sample:assembleDebug
```
> 生成 APK 产物位于 `sample/build/intermediates/apk/debug/sample-debug.apk`。

---

## 📄 依赖版本清单 (`libs.versions.toml`)

- **Android Gradle Plugin**: `8.4.2`
- **Kotlin**: `1.9.22`
- **Android Compile SDK**: `34` / **Min SDK**: `21`
- **JUnit**: `5.10.2`

---

## 📦 SDK 发布与 Maven 仓库集成

本工程已完整配置 `maven-publish`，支持本地根目录部署与远程 GitHub Packages 发布。

### 1. SDK 构件坐标清单

| 构件 ID | 产物类型 | 说明 | 包含文件 |
|---|:---:|---|---|
| `com.cn.ac:ac-core:1.0.0` | JAR | 核心多模式匹配引擎（Java 8，0 依赖） | `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom` |
| `com.cn.ac:ac-android:1.0.0` | AAR | Android 资产加载与原子热更持有仓 | `.aar`, `-sources.jar`, `-javadoc.jar`, `.pom` |
| `com.cn.ac:ac-serialization:1.0.0` | JAR | 二进制快照持久化（ACAT 格式） | `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom` |
| `com.cn.ac:ac-kotlin:1.0.0` | JAR | Kotlin DSL 语法糖与流式序列扩展 | `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom` |
| `com.cn.ac:ac-testkit:1.0.0` | JAR | 黄金标准 Naive 差分测试套件 | `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom` |

---

### 2. 部署至根目录 `local-maven` 本地私有仓

用于在本地无网络或开发阶段供其他工程免发布直接调试：

```bash
# 发布全量 SDK 至根目录 local-maven
./gradlew publishAllPublicationsToProjectLocalRepository
```

> 执行后所有 AAR、JAR、源码包、Javadoc、POM 及其 SHA/MD5 校验和将全部生成到根目录的 **`local-maven/`** 下。

#### 其他外部工程引用本地仓示例：

在外部项目的 `settings.gradle.kts` 或根 `build.gradle.kts` 中：

```kotlin
repositories {
    google()
    mavenCentral()
    // 依赖本地 Aho-Corasick 的 local-maven
    maven {
        url = uri("file:///D:/Developer/WorkSpace/Aho-Corasick/local-maven")
    }
}

dependencies {
    implementation("com.cn.ac:ac-core:1.0.0")
    implementation("com.cn.ac:ac-android:1.0.0") // Android 工程
}
```

---

### 3. 部署至远程 GitHub Packages Maven

#### 命令行发布方式
配置环境变量或在 `~/.gradle/gradle.properties` 中指定 GitHub 认证信息：

```bash
# 设置凭据
export GITHUB_ACTOR="your_github_username"
export GITHUB_TOKEN="your_personal_access_token_with_package_write"

# 发布全量 SDK 至 GitHub Packages（或直接执行 ./gradlew publish）
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

#### GitHub Actions 自动化发布
工程已参考 ModernIpc 对齐内置了 [`.github/workflows/release.yml`](file:///.github/workflows/release.yml) 工作流：
- 任何推送到远程的 Git Tag（如 `v1.0.0`）都会自动触发 `./gradlew publish` 发布到 GitHub Packages，并自动创建 GitHub Release 附加全套构件资产；
- 也可在 GitHub 页面通过 **Actions -> Run workflow** 手动触发发布。

#### 客户端工程集成远程 GitHub Packages 示例：
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/owner/Aho-Corasick")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```
- **AndroidX**: `core-ktx:1.13.1`, `appcompat:1.7.0`, `material:1.12.0`
