# MC WebAPI

本项目旨在为[GTNH](https://www.gtnewhorizons.com/)/[GTNH CN WIKI](https://gtnh.huijiwiki.com/wiki/%E9%A6%96%E9%A1%B5)添加一个简单的HTTP API接口

这样你就能在不启动游戏的前提进行快乐的下单了和一些别的操作

> [!TIP]
> 仅推荐服务端使用本mod，你不应该在客户端安装它！

## 使用说明

1. 在服务端下安装mod本体（如有需要，请同时安装Lib）
2. 如果希望使用图片相关的功能的话，预下载本项目准备的[图片素材](#图片素材)或者请查阅[自行导出图片](#自行导出图片)
3. 部署Web服务或者访问[在线服务](https://gtnh.elysia.rip)

> [!IMPORTANT]
> web服务会随着mod构建一同构建，纯静态，如果没有相关经验请直接选择使用在线服务
> 
> 在线服务会随着版本自动更新，如果遇到问题，请确保mod为最新版
>
> 更新只会随GTNH最新版进行兼容和修复，不会支持落后一个大版本以上的情况(比如2.9.0与2.8.4，版本差异过大)

## 下载

[![最新构建(测试)](https://github.com/Rcrwrate/McWebAPI/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/Rcrwrate/McWebAPI/actions/workflows/build-and-test.yml)

[![最新发布](https://img.shields.io/github/v/release/Rcrwrate/MCWebAPI)](https://github.com/Rcrwrate/McWebAPI/releases/latest)

目前状态：

| GTNH版本      | 兼容性  | 最后版本          |
|-------------|------|---------------|
| 2.8.4       | 兼容   | 2.8.4-0.4-pre |
| 2.9.0-beta2 | 部分兼容 | -             |


## Lib下载(可选)

这是一些压缩算法的jar包，下载后放置在**mods**文件夹下，用于对http响应体进行压缩**提升少量延迟以大幅节省带宽**

默认会启用内置的**GZIP**，如果你不需要压缩，请在配置文件中禁用

### zstd

> [!TIP]
> 推荐安装，速度和性能比Gzip优秀

[zstd-jni-1.5.7-11.jar](https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-11/zstd-jni-1.5.7-11.jar)

### brotli4j

> [!IMPORTANT]
> brotli4j 自 v1.10+ 拆分为多个模块，使用 Brotli 压缩需要同时下载以下三个 jar，否则会因 `com.aayushatharva.brotli4j.service.BrotliNativeProvider` 缺失而抛出 `NoClassDefFoundError`

> [!CAUTION]
> br压缩率较高，但是延迟**极大**，不推荐使用

- [brotli4j-1.23.0.jar](https://repo1.maven.org/maven2/com/aayushatharva/brotli4j/brotli4j/1.23.0/brotli4j-1.23.0.jar) — 主入口，包含 `Brotli4jLoader`
- [service-1.23.0.jar](https://repo1.maven.org/maven2/com/aayushatharva/brotli4j/service/1.23.0/service-1.23.0.jar) — ServiceLoader 接口 `BrotliNativeProvider`
- 平台原生库（按服务器架构选择）：
    - Linux x86_64: [native-linux-x86_64-1.23.0.jar](https://repo1.maven.org/maven2/com/aayushatharva/brotli4j/native-linux-x86_64/1.23.0/native-linux-x86_64-1.23.0.jar)
    - Windows x86_64: [native-windows-x86_64-1.23.0.jar](https://repo1.maven.org/maven2/com/aayushatharva/brotli4j/native-windows-x86_64/1.23.0/native-windows-x86_64-1.23.0.jar)
    - 其他架构见 [brotli4j 仓库索引](https://repo1.maven.org/maven2/com/aayushatharva/brotli4j/)


## 图片素材

在下方下载预导出的文件并解压到服务端的**dumps**目录

> [!TIP]
> 默认目录为dumps，可在配置文件中修改

[2.8.4.7z](https://cnb.cool/Cool_Sapphire/file/-/releases/download/2.8.4/2.8.4.dumps.6.18.7z)

README更新不一定即时，可以在下方两个仓库中寻找预导出的压缩包

> https://cnb.cool/Cool_Sapphire/file/-/releases
> 
> https://cnb.cool/shirokasoke/McWebAPI/-/releases

## 自行导出图片

1. 客户端安装本mod
2. 启动游戏，修改语言，创建新世界或者进入你的存档
3. 按下E，**等待NEI物品列表加载完成**
4. 在聊天区输入 `/export` 命令导出游戏内资源，所有内容输出到 `.minecraft/dumps/` 目录。

### 命令格式

```
/export <items|nei|missing|blocks|fluids|lang>
```

| 子命令 | 说明 |
|--------|------|
| `items` | 默认模式。遍历游戏中所有注册物品及子物品，导出图标并生成 `items.json` |
| `nei` | NEI 模式。使用 NEI 物品列表进行导出，生成 `items.json` + 图标 |
| `missing` | 缺失模式。读取 `dumps/missing-icons.json` 中的物品列表，仅导出缺失的图标，不生成 `items.json` |
| `blocks` | 导出方块纹理贴图 |
| `fluids` | 导出流体图标 |
| `lang` | 导出语言文件 |

### missing 模式说明

1. 启动游戏**服务端**，运行 WebAPI
2. 使用 TS SDK 中的检测脚本扫描缺失图标的物品：
    - `tool.allitems.ts` — 扫描全物品，不检查是否存在图标
    - `tool.checkicon.ts` — 扫描全物品（含子物品）
    - `tool.checkaeicon.ts` — 扫描你 AE 网络存储中的物品
3. 两个脚本的结果会统一写入 `missing-icons.json`，自动去重并排除 `ae2fc:fluid_drop`
4. 将脚本生成的 `missing-icons.json` 放入**客户端** `.minecraft/dumps/` 目录
5. 打开**客户端**，执行 `/export missing`，自动读取 JSON 并补导出缺失图标

> 已存在的图标会自动跳过，不会重复导出。

## BUG

[BUG](./BUG.md)

## 修改兼容性

[gradle.properties](./gradle.properties#L47-L60)

## vscode插件异常修复

插件ID:   `redhat.java`

异常现象：一些导入的包无法识别

按住`Crtl + Shift + P`，选择`Reload java project`重新加载项目之后再设置`JDK runtime`

异常现象：打开日志看见下述内容(出现兼容性异常)或在问题界面看见`The compiler compliance specified is 25 but a JRE 1.8 is used`

```java
!ENTRY org.eclipse.jdt.core.manipulation 4 0 2026-03-12 15:08:53.741
!MESSAGE Error in JDT Core during AST creation
!STACK 0
java.lang.IllegalStateException: Missing system library
	at org.eclipse.jdt.core.dom.ASTParser.checkForSystemLibrary(ASTParser.java:311)
	at org.eclipse.jdt.core.dom.ASTParser.getClasspath(ASTParser.java:269)
	at org.eclipse.jdt.core.dom.ASTParser.internalCreateASTCached(ASTParser.java:1412)
	at org.eclipse.jdt.core.dom.ASTParser.lambda$1(ASTParser.java:1291)
	at org.eclipse.jdt.internal.core.JavaModelManager.cacheZipFiles(JavaModelManager.java:5709)
	at org.eclipse.jdt.core.dom.ASTParser.internalCreateAST(ASTParser.java:1291)
	at org.eclipse.jdt.core.dom.ASTParser.createAST(ASTParser.java:933)
	at org.eclipse.jdt.core.manipulation.CoreASTProvider$1.run(CoreASTProvider.java:294)
	at org.eclipse.core.runtime.SafeRunner.run(SafeRunner.java:47)
	at org.eclipse.jdt.core.manipulation.CoreASTProvider.createAST(CoreASTProvider.java:286)
	at org.eclipse.jdt.core.manipulation.CoreASTProvider.getAST(CoreASTProvider.java:199)
	at org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler.getASTRoot(CodeActionHandler.java:464)
	at org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler.getCodeActionCommands(CodeActionHandler.java:170)
	at org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer.lambda$15(JDTLanguageServer.java:777)
	at org.eclipse.jdt.ls.core.internal.BaseJDTLanguageServer.lambda$0(BaseJDTLanguageServer.java:87)
	at java.base/java.util.concurrent.CompletableFuture$UniApply.tryFire(Unknown Source)
	at java.base/java.util.concurrent.CompletableFuture$Completion.exec(Unknown Source)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(Unknown Source)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(Unknown Source)
	at java.base/java.util.concurrent.ForkJoinPool.scan(Unknown Source)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(Unknown Source)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(Unknown Source)
```

异常原因：JDK RunTime设置错误，按住`Crtl + Shift + P`，输入`Configure Java runtime`，修改`JDK Runtime`