用户想要我查看 NEI（NotEnoughItems）的源代码，了解它是如何列出所有物品的。NEI 是一个 Minecraft 的模组，通常用于查看物品和配方。

用户提供了路径 `/workspace/tools/NotEnoughItems`，我需要先探索这个目录的结构，然后找到相关的代码来了解物品列表的实现。

让我先查看这个目录的内容。
我来帮你查看 NEI 的源代码，了解它是如何列出所有物品的。首先让我探索一下这个目录的结构。
好，我看到了 NEI 的源代码结构。从文件列表来看，有几个关键的文件与物品列表相关：

1. `ItemList.java` - 这个看起来是核心，18.39 KB
2. `ItemPanel.java` - 物品面板，9.44 KB
3. `ItemPanels.java` - 物品面板集合，193 B
4. `ItemsGrid.java` - 物品网格，16.99 KB
5. `ItemSorter.java` - 物品排序，5.18 KB
6. `api/ItemInfo.java` - 物品信息，19.76 KB

让我读取这些关键文件来了解 NEI 是如何列出所有物品的。
让我读取几个关键的源文件来了解 NEI 如何列出所有物品：
好的，我已经读取了 NEI 的核心文件。现在让我再查看一下 ItemSorter 和相关的配置文件，以便更全面地了解物品列表的生成和排序机制。
现在让我再看看 ItemSorter 和相关的文件：
好的，让我也读取 ItemsPanelGrid.java 文件来完整了解整个系统。
非常好！现在我已经对 NEI 如何列出所有物品有了完整的理解。让我总结一下关键点：

## NEI 物品列表系统的核心机制

### 1. **核心数据结构**
- `ItemList.java` - 管理所有物品的加载和过滤
- `ItemPanel.java` - 物品面板的 UI 组件
- `ItemsGrid.java` - 抽象的物品网格布局系统
- `ItemsPanelGrid.java` - 具体的物品网格实现
- `ItemInfo.java` - 物品信息和元数据管理
- `ItemSorter.java` - 物品排序逻辑

### 2. **物品加载流程**（ItemList.java）

#### **主要加载任务**（`loadItems` RestartableTask）：
1. **遍历所有注册的物品**（第382行）：
   ```java
   StreamSupport.stream(((Iterable<Item>) Item.itemRegistry).spliterator(), true)
   ```

2. **生成物品的所有变体**（`getPermutations` 方法，第284行）：
   - 首先检查 `ItemInfo.itemOverrides`（自定义覆盖）
   - 然后调用 `item.getSubItems()` 获取子物品
   - 如果没有子物品，进行 damage 搜索（0-15）
   - 最后添加 `ItemInfo.itemVariants` 中的变体

3. **过滤物品**（第297-301行）：
   - 过滤掉 `null` 物品
   - 过滤掉没有注册名的物品
   - 过滤掉隐藏的物品（`ItemInfo.isHidden()`）

4. **初始化 NBT 数据**（`forceTagCompoundInitialization` 方法，第323行）：
   - 预先初始化物品的 NBT 数据，避免运行时计算
   - 处理流体容器、耐久、附魔等属性

5. **排序**（`updateOrdering` 方法，第251行）：
   - 使用 `ItemSorter.sort()` 对物品进行排序
   - 处理可折叠物品组（CollapsibleItems）

### 3. **物品排序机制**（ItemSorter.java）

NEI 提供了多种排序选项：
- **minecraft** - Minecraft 原版物品优先
- **mod** - 按模组名称排序
- **id** - 按物品 ID 排序
- **default** - 默认排序（注册顺序）
- **damage** - 按物品 damage 值排序
- **name** - 按显示名称排序

用户可以自定义排序顺序，配置保存在 `inventory.itemsort` 设置中。

### 4. **物品过滤机制**（ItemList.java）

NEI 支持多种过滤方式：
- **模式匹配**（PatternItemFilter）- 按名称搜索
- **所有条件匹配**（AllMultiItemFilter）- 必须匹配所有过滤器
- **任意条件匹配**（AnyMultiItemFilter）- 匹配任意过滤器
- **取反**（NegatedItemFilter）- 反向匹配

### 5. **可折叠物品组**（CollapsibleItems）

这是 NEI 的一个重要特性，可以将相似的物品分组显示：
- 通过 `CollapsibleItems.getGroupIndex()` 获取物品组索引
- 组可以折叠/展开，减少界面混乱
- 在 `ItemsPanelGrid.java` 中处理组的渲染和边框

### 6. **性能优化**

- **并行处理**：使用 ForkJoinPool 并行加载物品（第444行）
- **缓存机制**：物品列表、排序结果、过滤结果都有缓存
- **屏幕渲染缓存**：使用 Framebuffer 缓存渲染结果（ItemsGrid.java 第27行）
- **超时控制**：使用 ThreadOperationTimer 防止单个物品加载超时

### 7. **物品信息增强**（ItemInfo.java）

- **物品覆盖**：允许其他模组添加自定义物品变体
- **物品隐藏**：可以隐藏特定物品
- **物品子集**：按模组、创意标签、工具类型等分类
- **搜索提供器**：支持按名称、模组、矿物词典、提示等搜索

### 8. **UI 渲染流程**

1. `ItemPanel` 计算面板大小和位置
2. `ItemsPanelGrid` 管理物品的网格布局
3. 处理分页（每页显示固定数量的物品）
4. 处理鼠标悬停、点击等交互
5. 渲染物品图标、组边框、背景色等

这就是 NEI 列出所有物品的完整机制！从遍历注册表到生成变体，从过滤排序到最终渲染，形成了一个完整的管道。
好的！我已经分析了 NEI 的源代码。以下是 NEI 如何列出所有物品的核心机制：

## NEI 物品列表系统核心机制

### 1. **核心加载流程**（`ItemList.java`）

NEI 使用 `RestartableTask` 异步加载所有物品，主要步骤：

**遍历物品注册表**（第382行）：
```java
StreamSupport.stream(((Iterable<Item>) Item.itemRegistry).spliterator(), true)
```

**生成物品变体**（`getPermutations` 方法，第284-302行）：
1. 首先检查 `ItemInfo.itemOverrides` 中的自定义覆盖
2. 调用 `item.getSubItems()` 获取该物品的所有子类型（如不同颜色的羊毛）
3. 如果没有子物品，进行 damage 值搜索（0-15）寻找不同变体
4. 添加 `ItemInfo.itemVariants` 中的额外变体
5. 过滤掉 null、无注册名和隐藏的物品

**预初始化 NBT 数据**（`forceTagCompoundInitialization` 方法）：
- 预先初始化物品的 NBT 标签，避免运行时计算
- 处理流体容器、耐久度、附魔、工具属性等

### 2. **排序系统**（`ItemSorter.java`）

NEI 提供 6 种排序方式，可自定义组合：
- **minecraft**：原版物品优先
- **mod**：按模组名称排序
- **id**：按物品 ID 排序
- **default**：注册表顺序
- **damage**：按 damage 值排序
- **name**：按显示名称排序

### 3. **过滤机制**

支持多种过滤器组合：
- **PatternItemFilter**：按名称正则表达式搜索
- **AllMultiItemFilter**：必须匹配所有条件
- **AnyMultiItemFilter**：匹配任意条件即可
- **NegatedItemFilter**：反向匹配

### 4. **可折叠物品组**（`CollapsibleItems` + `ItemsPanelGrid.java`）

重要特性：自动将相似物品分组显示
- 通过 `CollapsibleItems.getGroupIndex()` 获取组索引
- 组可折叠/展开，减少界面混乱
- 自动为组添加边框和背景色
- 如果只有一组物品，自动展开显示

### 5. **性能优化**

- **并行加载**：使用 ForkJoinPool 并行处理物品
- **多重缓存**：物品列表、排序结果、过滤结果都有缓存
- **渲染缓存**：使用 Framebuffer 缓存网格渲染结果
- **超时控制**：ThreadOperationTimer 防止单个物品卡死

### 6. **物品信息增强**（`ItemInfo.java`）

- **物品覆盖**：允许模组添加自定义物品变体
- **物品隐藏**：可隐藏特定物品或按规则隐藏
- **物品子集**：按模组、创意标签、工具类型等分类
- **搜索提供器**：支持按名称、模组、矿物词典、提示文本搜索

### 7. **UI 渲染流程**

1. `ItemPanel` 计算面板位置和大小
2. `ItemsPanelGrid` 管理网格布局，处理分页
3. 每页显示固定数量的物品（根据面板大小计算）
4. 渲染物品图标、组边框、背景色
5. 处理鼠标悬停、点击、拖拽等交互

整个系统形成了一个完整的管道：**遍历注册表 → 生成变体 → 过滤 → 排序 → 分页 → 渲染**，确保了高效且灵活的物品列表展示。