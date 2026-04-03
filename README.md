# MC WebAPI

## 下载

[![CodeQL Advanced](https://github.com/Rcrwrate/McWebAPI/actions/workflows/codeql.yml/badge.svg)](https://github.com/Rcrwrate/McWebAPI/actions/workflows/codeql.yml)

[在CodeQL Advanced的Artifacts中下载(想着不高兴再写一个就合并在一块了)](https://github.com/Rcrwrate/McWebAPI/actions/workflows/codeql.yml)

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