# NEI 物品图标渲染路径

## 架构概览

```text
物品面板
ItemsGrid.draw()
  -> ItemsGrid.drawItems()
  -> ItemsGridSlot.drawItem()
  -> GuiContainerManager.drawItem()

NEI 图标导出器
GuiItemIconDumper.drawScreen()
  -> drawItems()
  -> GuiContainerManager.drawItem()
  -> exportItems()
  -> screenshot()

公共渲染入口
GuiContainerManager.drawItem()
  -> safeItemRenderContext()
  -> RenderItem.renderItemAndEffectIntoGUI()
  -> RenderItem.renderItemOverlayIntoGUI()
```

## 关键源文件

| 文件 | 职责 |
|------|------|
| [`ItemsGrid.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ItemsGrid.java) | 物品网格布局、逐槽绘制和渲染缓存入口 |
| [`ItemsPanelGrid.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ItemsPanelGrid.java) | NEI 物品面板的具体网格槽实现 |
| [`GuiContainerManager.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/guihook/GuiContainerManager.java) | 统一物品 GUI 渲染入口、数量覆盖层和异常恢复 |
| [`GuiItemIconDumper.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/config/GuiItemIconDumper.java) | NEI 自带的批量图标导出界面 |
| [`ItemZoom.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ItemZoom.java) | 通过缩放矩阵复用同一渲染入口的示例 |

## 1. 物品面板渲染路径

[`ItemsGrid.drawItems()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ItemsGrid.java#L518) 遍历当前网格中的槽，并调用每个槽的 `drawItem()`：

```java
for (T item : getMask()) {
    item.drawItem(getSlotRect(item.slotIndex));
}
```

默认槽实现在 [`ItemsGridSlot.drawItem()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ItemsGrid.java#L179)。标准 18 像素槽会在四周保留 1 像素边距，并把物品绘制到内部 16x16 区域：

```java
GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, stack, true, "");
```

当槽尺寸不是默认值时，NEI 先平移到槽的内容原点，再缩放 modelview 矩阵，仍然以 `(0, 0)` 和逻辑尺寸 16x16 绘制。物品放大功能也采用相同方式，见 [`ItemZoom.draw()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ItemZoom.java#L41)。

这里传入空字符串 `""`，会禁止绘制堆叠数量；物品本身和耐久度条仍由统一入口处理。

## 2. 公共 drawItem 入口

[`GuiContainerManager.drawItem()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/guihook/GuiContainerManager.java#L300) 的重载最终都进入包含 `FontRenderer`、数量缩放选项和数量文本的完整版本。

核心调用位于 [`GuiContainerManager.java#L320`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/guihook/GuiContainerManager.java#L320)：

```java
safeItemRenderContext(itemstack, offsetX, offsetY, fontRenderer, () -> {
    drawItems.renderItemAndEffectIntoGUI(
        fontRenderer,
        renderEngine,
        itemstack,
        offsetX,
        offsetY);

    drawItems.renderItemOverlayIntoGUI(
        fontRenderer,
        renderEngine,
        itemstack,
        offsetX,
        offsetY,
        stackSize);
});
```

`drawItems` 是 NEI 持有的一个 `RenderItem` 实例。第一步绘制物品模型及效果，内部也会进入 Forge 的 inventory item renderer；第二步绘制数量和耐久度等 GUI 覆盖层。因此，使用 `GuiContainerManager.drawItem()` 比直接读取 `IIcon` 更接近玩家在 NEI 中实际看到的结果。

### 数量文本

[`drawItem()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/guihook/GuiContainerManager.java#L327) 根据参数决定数量文本：

- `quantity == null`：根据 `ItemStack.stackSize` 自动绘制数量。
- `quantity == ""`：不绘制数量。
- `smallAmount == true`：允许 NEI 根据位数缩小数量文本。

图标导出时，如果只需要物品图形而不需要数量，应该使用 `drawItem(x, y, stack, true, "")`；NEI 自带导出器调用的是三参数重载，所以会保留堆叠数量。

## 3. 渲染上下文与异常恢复

[`safeItemRenderContext()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/guihook/GuiContainerManager.java#L375) 为每个物品建立受控的 GUI 渲染环境：

1. 将 `RenderItem.zLevel` 临时提高 100，避免图标被其他 GUI 元素遮挡。
2. 保存启用状态、颜色缓冲和光照属性，并记录 modelview 矩阵栈深度。
3. 启用光照和深度测试后执行实际渲染。
4. 检查模组渲染器是否遗留了未弹出的矩阵，或让 `Tessellator` 停留在绘制状态。
5. 渲染异常时恢复矩阵与 `Tessellator`，改为绘制火焰方块作为错误占位图。
6. 恢复 GL 属性和原始 `zLevel`。

相关的矩阵和属性保存实现在 [`enableMatrixStackLogging()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/guihook/GuiContainerManager.java#L413)，2D/3D 状态切换位于 [`enable3DRender()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/guihook/GuiContainerManager.java#L442)。

这层保护主要约束单个物品渲染器，不负责保存调用者设置的 framebuffer、viewport、投影矩阵或 clear color；这些状态必须由外层界面或离屏导出器管理。

## 4. NEI 自带图标导出器

[`GuiItemIconDumper.drawScreen()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/config/GuiItemIconDumper.java#L80) 每帧依次执行批量绘制和像素导出。它不是为每个物品创建独立 framebuffer，而是把多个物品排布到当前屏幕，再从屏幕截图中逐个裁剪。

### 投影与绘制状态

[`GuiItemIconDumper.drawItems()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/config/GuiItemIconDumper.java#L90) 完成以下设置：

```java
GL11.glOrtho(
    0.0D,
    displayWidth * 16D / iconSize,
    displayHeight * 16D / iconSize,
    0.0D,
    1000.0D,
    3000.0D);

GL11.glClearColor(0, 0, 0, 0);
GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
RenderHelper.enableGUIStandardItemLighting();
GL11.glEnable(GL12.GL_RESCALE_NORMAL);
GL11.glColor4f(1, 1, 1, 1);
```

投影宽高按 `16 / iconSize` 换算，使逻辑上的 16x16 物品最终占用 `iconSize x iconSize` 个物理像素。GUI 渲染流程已经给 modelview 设置了约 `z=-2000` 的平移，因此这里使用 `1000..3000` 的标准 GUI 深度范围。

每个图标占用 18x18 个逻辑像素：16 像素图标加四周各 1 像素边界。批量绘制循环见 [`GuiItemIconDumper.java#L100`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/config/GuiItemIconDumper.java#L100)。

### 读取与裁剪

[`exportItems()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/config/GuiItemIconDumper.java#L119) 先获取整屏截图，再按网格位置裁出每个 `iconSize x iconSize` 子图。

[`screenshot()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/config/GuiItemIconDumper.java#L162) 同时处理两种环境：

- Framebuffer 开启：使用 `glGetTexImage()` 读取 Minecraft 主 framebuffer 的颜色纹理。
- Framebuffer 关闭：使用 `glReadPixels()` 直接读取当前屏幕。

读取格式是 `BGRA + GL_UNSIGNED_INT_8_8_8_8_REV`。随后通过 `TextureUtil.func_147953_a()` 翻转 OpenGL 与 `BufferedImage` 之间相反的 Y 轴。

## 5. 当前实现

[`ItemIconDumperThread`](../../src/main/java/love/shirokasoke/webapi/client/thread/ItemIconDumperThread.java#L49) 沿用了 NEI 的公共物品渲染入口，但导出结构不同：

```text
后台导出线程
  -> Minecraft.func_152344_a() 投递到客户端主线程
  -> 绑定独立的 iconSize x iconSize Framebuffer
  -> 设置透明背景、正交投影和 GUI 标准光照
  -> GuiContainerManager.drawItem(0, 0, stack)
  -> glReadPixels()
  -> 后台线程写入 PNG
```

实际绘制调用位于 [`ItemIconDumperThread.renderItem()`](../../src/main/java/love/shirokasoke/webapi/client/thread/ItemIconDumperThread.java#L287)。其投影把 16x16 逻辑坐标缩放到整个 framebuffer，因此输出尺寸与 NEI 导出器一致，并继续支持 3D 方块、自定义 inventory renderer 和附魔效果。

### 实现约束

独立 framebuffer 方案可以避免批量占用游戏屏幕，但外层必须额外负责 NEI 公共入口没有管理的状态：

1. OpenGL 调用必须在 Minecraft 客户端主线程执行。
2. 使用独立 framebuffer 前应检查 `OpenGlHelper.isFramebufferEnabled()`；FBO 被关闭时，`Framebuffer.bindFramebuffer()` 不会创建或绑定离屏目标。
3. 使用 `try/finally` 恢复 framebuffer、viewport、投影矩阵、modelview 和 clear color。
4. 绘制前显式设置 `GL11.glColor4f(1, 1, 1, 1)`，避免自定义渲染器继承旧颜色。
5. 导出结束后必须在客户端主线程调用 `Framebuffer.deleteFramebuffer()`，释放颜色纹理和深度缓冲。

后续更新重点应是 framebuffer 生命周期和 GL 状态隔离，而不是改为直接导出物品 atlas 中的 `IIcon`。
