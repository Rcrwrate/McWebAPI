# MC WebAPI

本项目旨在为[GTNH](https://www.gtnewhorizons.com/)/[GTNH CN](https://gtnh.huijiwiki.com/wiki/%E9%A6%96%E9%A1%B5)添加一个简单的HTTP API接口

这样你就能在服务端运行时不启动游戏客户端的情况下进行快乐的操作：包括但不限于AE下单、快捷3D打印，具体请查阅[功能](#功能)

> [!TIP]
> 仅推荐服务端使用本mod，你正常情况下不应该在客户端安装它！

## 使用说明

1. 在服务端下安装mod本体（如有需要，请同时安装Lib）
2. 如果希望使用图片相关的功能的话，预下载本项目准备的[图片素材](#图片素材)或者请查阅[自行导出图片](#自行导出图片)
3. 部署Web服务或者访问在线服务

> [!IMPORTANT]
> web服务会随着mod构建一同构建，纯静态，如果没有相关经验请直接选择使用在线服务
>
> 在线服务会随着版本自动更新，如果遇到问题，请确保mod为最新版
>
> 更新只会随GTNH最新版进行兼容和修复，不会支持落后一个大版本以上的情况(比如2.9.0与2.8.4，版本差异过大)

## 下载

[![最新构建(测试)](https://img.shields.io/github/actions/workflow/status/Rcrwrate/McWebAPI/build-and-test.yml?logo=github&label=Build%20and%20test&link=https%3A%2F%2Fgithub.com%2FRcrwrate%2FMcWebAPI%2Factions%2Fworkflows%2Fbuild-and-test.yml)](https://github.com/Rcrwrate/McWebAPI/actions/workflows/build-and-test.yml)

[![最新发布](https://img.shields.io/github/v/release/Rcrwrate/MCWebAPI)](https://github.com/Rcrwrate/McWebAPI/releases/latest)

目前状态：

| GTNH版本    | 状态     | 最后版本            |
| ----------- | -------- | ------------------- |
| 2.8.4       | 停止维护 | 2.8.4-0.5-pre       |
| 2.9.0-beta2 | 维护中   | 2.9.0-beta2-0.8-pre |

## Lib下载(可选)

这是一些压缩算法的jar包，下载后放置在**mods**文件夹下，用于对http响应体进行压缩**提升少量延迟以大幅节省带宽**

默认会启用内置的**GZIP**，如果你不需要压缩，请在配置文件中禁用

### zstd

> [!TIP]
> 推荐安装，速度和性能比Gzip优秀

[zstd-jni-1.5.7-11.jar](https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-11/zstd-jni-1.5.7-11.jar)

### brotli4j

> [!IMPORTANT]
> brotli4j 自 v1.10+ 拆分为多个模块，使用 Brotli 压缩需要同时下载以下三个 jar，否则会出错

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

[2.9.0-beta2.7z](https://cnb.cool/shirokasoke/McWebAPI/-/releases/download/2.8.4-0.4-pre/2.9.0-beta2.dumps.7z)

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

| 子命令    | 说明                                                                                          |
| --------- | --------------------------------------------------------------------------------------------- |
| `items`   | 默认模式。遍历游戏中所有注册物品及子物品，导出图标并生成 `items.json`                         |
| `nei`     | NEI 模式。使用 NEI 物品列表进行导出，生成 `items.json` + 图标                                 |
| `missing` | 缺失模式。读取 `dumps/missing-icons.json` 中的物品列表，仅导出缺失的图标，不生成 `items.json` |
| `blocks`  | 导出方块纹理贴图                                                                              |
| `fluids`  | 导出流体图标                                                                                  |
| `lang`    | 导出语言文件                                                                                  |

### missing 模式说明

1. 启动游戏**服务端**，运行 WebAPI
2. 使用 TS SDK 中的检测脚本(或者你自行编写脚本)扫描缺失图标的物品：
   - `tool.allitems.ts` — 扫描全物品，不检查是否存在图标
   - `tool.checkicon.ts` — 扫描全物品（含子物品）
   - `tool.checkaeicon.ts` — 扫描你 AE 网络存储中的物品
3. 两个脚本的结果会统一写入 `missing-icons.json`，自动去重并排除 `ae2fc:fluid_drop`
4. 将脚本生成的 `missing-icons.json` 放入**客户端** `.minecraft/dumps/` 目录
5. 打开**客户端**，执行 `/export missing`，自动读取 JSON 并补导出缺失图标

> 已存在的图标会自动跳过，不会重复导出。

## 功能

引入版本不一定准确

### 世界与性能

| 功能         | 说明                           | 引入版本      |
| ------------ | ------------------------------ | ------------- |
| `/tps`       | TPS 与耗时统计                 | 2.8.4-0.1-pre |
| `/WorldInfo` | 世界信息（时间、天气、维度等） | 2.8.4-0.1-pre |
| `/version`   | Mod 版本信息                   | 2.8.4-0.2-pre |
| TPS 录制     | 记录 TPS 历史用于回看          | 2.8.4-0.3-pre |

### 方块 / 区块 / 实体

| 功能                          | 说明                          | 引入版本      |
| ----------------------------- | ----------------------------- | ------------- |
| `/block`、`/blocks`           | 查询单个/批量方块             | 2.8.4-0.1-pre |
| `/block/tile`                 | 方块Icon                      | 2.8.4-0.1-pre |
| `/block/fmp`                  | ForgeMultiPart 多方块结构数据 | 2.8.4-0.1-pre |
| `/setblock`、`/batchsetblock` | 修改方块                      | 2.8.4-0.1-pre |
| `/chunk`、`/chunks`           | 查询区块信息                  | 2.8.4-0.1-pre |
| `/chunk/map`                  | 区块地图                      | 2.8.4-0.1-pre |
| `/chunk/force`                | 强制加载/卸载区块             | 2.8.4-0.1-pre |
| `/entity`、`/entities`        | 实体信息查询                  | 2.8.4-0.1-pre |

### 物品与流体

| 功能                                         | 说明                         | 引入版本      |
| -------------------------------------------- | ---------------------------- | ------------- |
| `/item`、`/items`                            | 查询物品元数据               | 2.8.4-0.1-pre |
| `/item/icon`                                 | 物品图标                     | 2.8.4-0.1-pre |
| `/items/ae`                                  | AE 网络全物品                | 2.8.4-0.1-pre |
| `/fluids`、`/fluid/icon`、`/fluidContainers` | 流体列表、流体图标、流体容器 | 2.8.4-0.1-pre |

### AE2

| 功能                                  | 说明                      | 引入版本      |
| ------------------------------------- | ------------------------- | ------------- |
| `/ae`                                 | AE 网络概览               | 2.8.4-0.1-pre |
| `/ae/item`                            | AE 网络物品查询(含流体堆) | 2.8.4-0.1-pre |
| `/ae/me`、`/ae/mes`、`/ae/me/support` | ME 接口信息               | 2.8.4-0.1-pre |
| `/ae/cpu`                             | 合成 CPU 列表             | 2.8.4-0.1-pre |
| `/ae/cpu/task`                        | 提交合成任务              | 2.8.4-0.1-pre |
| `/ae/cpu/cancel`                      | 取消合成任务              | 2.8.4-0.1-pre |
| `/ae/nodes`                           | AE 网络节点信息           | 2.8.4-0.1-pre |

### GT5

| 功能         | 说明           | 引入版本      |
| ------------ | -------------- | ------------- |
| `/gt5`       | 机器信息查询   | 2.8.4-0.2-pre |
| `/gt5/scan`  | 按区块扫描机器 | 2.8.4-0.2-pre |
| `/gt5/batch` | 批量查询机器   | 2.8.4-0.2-pre |

### 配方

| 功能                | 说明                         | 引入版本            |
| ------------------- | ---------------------------- | ------------------- |
| `/recipes/gt`       | GT5 机器配方查询             | 2.9.0-beta2-0.9-pre |
| `/recipes/gt/maps`  | 配方映射表                   | 2.9.0-beta2-0.9-pre |
| `/recipes/crafting` | 合成台配方查询(可选索引加速) | 2.9.0-beta2-0.8-pre |
| `/recipes/furnace`  | 熔炉配方查询                 | 2.9.0-beta2-0.8-pre |

### 3D 打印(游戏外)

| 功能         | 说明                     | 引入版本            |
| ------------ | ------------------------ | ------------------- |
| `/3d/player` | 打印完投递到玩家背包     | 2.9.0-beta2-0.9-pre |
| `/3d/world`  | 直接在世界中进行 3D 打印 | 2.9.0-beta2-0.9-pre |

## BUG

[BUG](./BUG.md)

## 修改兼容性

[gradle.properties](./gradle.properties#L47-L60)

## vscode插件异常修复

插件ID: `redhat.java`

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
