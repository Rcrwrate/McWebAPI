# GT5 向 NEI 注册合成表查询功能的机制

## 结论

GT5 通过实现 NEI 的插件入口接口 `IConfigureNEI` 并遵循 `NEI*Config` 类名约定，让 NEI 在客户端启动时自动发现并调用其 `loadConfig()`。在该方法中，GT5 遍历全部 `RecipeCategory`（每个类别关联一个 `RecipeMap`），为标记了 `registerNEI` 的类别创建 `GTNEIDefaultHandler`（`TemplateRecipeHandler` 子类），然后**直接**把处理器加入 NEI 的 `GuiCraftingRecipe.craftinghandlers` 和 `GuiUsageRecipe.usagehandlers` 列表，并通过 FML IMC 消息通知 NEIPlugins 附属模组。最后注册机器催化剂（`API.addRecipeCatalyst`）和 `HandlerInfo` 显示元数据，完成整个注册。

## 核心调用链

```text
NEI 客户端启动
  └─ ClientHandler.loadPluginsList()                      # 扫描 NEI*Config 类
      └─ NEIClientConfig 插件加载线程
          └─ new NEIGTConfig().loadConfig()               # GT5 插件入口
              ├─ registerHandlers()
              │   ├─ 遍历 RecipeCategory.ALL_RECIPE_CATEGORIES
              │   ├─ 过滤 NEIRecipeProperties.registerNEI
              │   ├─ new GTNEIDefaultHandler(recipeCategory)
              │   └─ addHandler()
              │       ├─ FMLInterModComms.sendRuntimeMessage("NEIPlugins", ...)
              │       ├─ GuiCraftingRecipe.craftinghandlers.add(handler)   # R 键查询
              │       └─ GuiUsageRecipe.usagehandlers.add(handler)         # U 键查询
              ├─ registerCatalysts()
              │   └─ API.addRecipeCatalyst(机器, 类别名, 优先级)
              ├─ registerItemEntries()
              └─ registerDumpers()

玩家按 R / U 键
  └─ GuiCraftingRecipe / GuiUsageRecipe 遍历已注册 handler
      └─ GTNEIDefaultHandler.loadCraftingRecipes(stack)    # 按产物匹配
          / loadUsageRecipes(stack)                        # 按原料匹配
          / getUsageAndCatalystHandler(机器)               # 点机器查全部配方
```

### 关键文件

| 文件 | 说明 |
|------|------|
| [`IConfigureNEI.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/api/IConfigureNEI.java) | NEI 插件入口接口，定义 `loadConfig()` |
| [`ClientHandler.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ClientHandler.java) | NEI 插件类扫描（`loadPluginsList()`） |
| [`NEIClientConfig.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/NEIClientConfig.java) | 实例化插件并调用 `loadConfig()` |
| [`NEIGTConfig.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java) | GT5 的 NEI 插件主类，注册全部处理器 |
| [`GTNEIDefaultHandler.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java) | GT5 通用配方处理器，每个 `RecipeCategory` 一个 |
| [`GTNEIImprintHandler.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIImprintHandler.java) | 电路装配线（CAL）印记配方专用处理器 |
| [`RecipeCategory.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/recipe/RecipeCategory.java) | 配方类别，构造时自动加入 `ALL_RECIPE_CATEGORIES` |
| [`NEIRecipeProperties.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/recipe/NEIRecipeProperties.java) | 配方映射的 NEI 属性，含 `registerNEI` 开关 |

---

## 1. NEI 的插件发现机制

NEI 定义插件入口接口 `IConfigureNEI`，并约定实现类应以 `NEI` 开头、以 `Config` 结尾命名：

来源：[`IConfigureNEI`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/api/IConfigureNEI.java#L7)

```java
public interface IConfigureNEI {
    public void loadConfig();
    public String getName();
    public String getVersion();
}
```

NEI 在客户端启动时扫描 classpath 中所有符合命名约定且实现该接口的类：

来源：[`ClientHandler.loadPluginsList()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ClientHandler.java#L315)

```java
final ClassDiscoverer classDiscoverer = new ClassDiscoverer(
        test -> test.startsWith("NEI") && test.endsWith("Config.class"),
        IConfigureNEI.class);

NEIClientConfig.pluginsList.addAll(classDiscoverer.findClasses());
```

随后在 "NEI Plugin Loader" 线程中逐个实例化并调用 `loadConfig()`：

来源：[`NEIClientConfig.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/NEIClientConfig.java#L981)

```java
IConfigureNEI config = (IConfigureNEI) clazz.getConstructor().newInstance();
config.loadConfig();
NEIModContainer.plugins.add(config);
```

因此模组**不需要**手动调用 NEI；只要提供一个 `NEI*Config implements IConfigureNEI` 的类即可被自动加载。

---

## 2. GT5 的插件入口 `NEIGTConfig`

GT5 的入口类 `gregtech.nei.NEIGTConfig` 遵循上述约定：

来源：[`NEIGTConfig.loadConfig()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L76)

```java
@Override
public void loadConfig() {
    sIsAdded = false;
    registerHandlers();      // 注册配方处理器
    registerCatalysts();     // 注册催化剂（机器图标）
    registerItemEntries();   // 注册额外物品到 NEI 物品列表
    registerDumpers();       // 注册 NEI 数据导出工具
    sIsAdded = true;
}
```

---

## 3. 配方处理器注册：`registerHandlers()`

核心逻辑是遍历全局配方类别表，过滤出需要 NEI 页面的类别，为每个类别创建一个处理器：

来源：[`NEIGTConfig.registerHandlers()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L86)

```java
private void registerHandlers() {
    RecipeCategory.ALL_RECIPE_CATEGORIES.values()
        .stream()
        .filter(
            recipeCategory -> recipeCategory.recipeMap.getFrontend()
                .getNEIProperties().registerNEI)
        .map(GTNEIDefaultHandler::new)
        .sorted(RECIPE_MAP_HANDLER_COMPARATOR)
        .forEach(NEIGTConfig::addHandler);

    GuiCraftingRecipe.craftinghandlers.add(CAL_IMPRINT_HANDLER);
    GuiUsageRecipe.usagehandlers.add(CAL_IMPRINT_HANDLER);
}
```

其中：

- `RecipeCategory.ALL_RECIPE_CATEGORIES` 是全局静态表，每个 `RecipeCategory` 构造时自动注册（[`RecipeCategory.java` 第 58 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/recipe/RecipeCategory.java#L58)），并持有所属的 `RecipeMap`。
- 过滤条件 `NEIRecipeProperties.registerNEI`（[`NEIRecipeProperties.java` 第 41 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/recipe/NEIRecipeProperties.java#L41)）决定该配方映射是否生成 NEI 页面；纯内部假配方映射可将其关闭。
- `RECIPE_MAP_HANDLER_COMPARATOR` 按 `RECIPE_MAP_ORDERING` 调整标签页顺序（例如装配线可视化、扫描仪假配方排后）。

### 双重注册：`addHandler()`

来源：[`NEIGTConfig.addHandler()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L66)

```java
private static void addHandler(TemplateRecipeHandler handler) {
    FMLInterModComms.sendRuntimeMessage(
        GTMod.GT,
        "NEIPlugins",
        "register-crafting-handler",
        "gregtech@" + handler.getRecipeName() + "@" + handler.getOverlayIdentifier());
    GuiCraftingRecipe.craftinghandlers.add(handler);
    GuiUsageRecipe.usagehandlers.add(handler);
}
```

注意 GT5 **没有**调用 NEI 的 `API.registerRecipeHandler()` / `API.registerUsageHandler()` 封装，而是：

1. 直接把处理器加入 `GuiCraftingRecipe.craftinghandlers` —— 玩家按 **R 键**查合成时 NEI 遍历的列表；
2. 同时加入 `GuiUsageRecipe.usagehandlers` —— 按 **U 键**查用途时遍历的列表。同一个处理器实例同时承担两种查询；
3. 通过 FML 运行时 IMC 消息向 NEIPlugins 附属模组注册，消息格式为 `gregtech@配方名@overlay标识符`，用于其标签页集成。

---

## 4. 处理器如何响应查询：`GTNEIDefaultHandler`

`GTNEIDefaultHandler` 继承 NEI 的 `TemplateRecipeHandler`（[第 73 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java#L73)），构造时保存 `RecipeCategory`、`RecipeMap`、`RecipeMapFrontend` 及 NEI 属性，并用 ModularUI 构建配方页面模板。

### 按产物查询（R 键）

来源：[`GTNEIDefaultHandler.loadCraftingRecipes(ItemStack)`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java#L234)

```java
public void loadCraftingRecipes(ItemStack aResult) {
    ItemData tPrefixMaterial = GTOreDictUnificator.getAssociation(aResult);
    ArrayList<ItemStack> tResults = new ArrayList<>();
    tResults.add(aResult);
    tResults.add(GTOreDictUnificator.get(true, aResult));
    // ... 扩展矿物词典同族前缀、矿石变体、流体容器等候选
    for (CachedDefaultRecipe recipe : getCache()) {
        if (tResults.stream()
            .anyMatch(stack -> recipe.contains(recipe.mOutputs, stack))) arecipes.add(recipe);
    }
}
```

查询时会把目标物品扩展为一组候选（统一化产物、同族矿辞前缀、GT 矿石的 8 种变体、流体及容器形式），再与缓存配方列表的产物匹配。

### 按原料查询（U 键）

来源：[`GTNEIDefaultHandler.loadUsageRecipes(ItemStack)`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java#L299)

逻辑相同，只是匹配 `recipe.mInputs`。

### 点击机器查全部配方（催化剂路径）

来源：[`GTNEIDefaultHandler.getUsageAndCatalystHandler()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java#L317)

```java
if (RecipeCatalysts.containsCatalyst(handler, candidate)) {
    IMetaTileEntity metaTile = ItemMachines.getMetaTileEntity(candidate);
    // 取机器的超频描述器，按其电压等级过滤配方
    handler.loadCraftingRecipes(recipeMap.unlocalizedName, overclockDescriber);
    return handler;
}
```

玩家对机器方块按 U 键时，处理器加载该配方映射下**不超过机器电压等级**的全部配方。

### 配方缓存

每个类别的配方列表经 `SortedRecipeListCache` 缓存（第 79 行 `CACHE`），按 NEI 重载次数失效；主类别还会合并配置为 `MERGE` 的子类别配方（[`getCache()` 第 152 行起](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java#L152)）。

### 页面标识与绘制

- `getOverlayIdentifier()` 返回 `recipeCategory.unlocalizedName`（[第 346 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java#L346)），作为 GUI 覆盖层与 HandlerInfo 的关联键；
- `drawBackground()` 用 ModularUI 窗口绘制槽位布局（[第 351 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java#L351)）；
- `newInstance()` 每次查询创建同类别新实例（[第 211 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIDefaultHandler.java#L211)），符合 NEI 每查询一实例的模型。

---

## 5. 机器催化剂注册：`registerCatalysts()`

催化剂让玩家在 NEI 中看到“哪些机器能处理这类配方”，点击机器即可打开对应配方页。

GT5 先扫描全部 MetaTileEntity，建立 `RecipeCategory → 机器列表` 索引：

来源：[`NEIGTConfig.generateRecipeCatalystIndex()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L173)

```java
for (int i = 1; i < GregTechAPI.METATILEENTITIES.length; i++) {
    IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[i];
    if (!(mte instanceof RecipeMapWorkable recipeMapWorkable)) continue;
    for (RecipeMap<?> recipeMap : recipeMapWorkable.getAvailableRecipeMaps()) {
        for (RecipeCategory recipeCategory : recipeMap.getAssociatedCategories()) {
            builder.put(recipeCategory, recipeMapWorkable);
        }
    }
}
```

再对每个类别调用 NEI 的 `API.addRecipeCatalyst()`：

来源：[`NEIGTConfig.registerCatalysts()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L100)

```java
recipeMapWorkable -> API.addRecipeCatalyst(
    recipeMapWorkable.getStackForm(1),
    entry.getKey().unlocalizedName,
    recipeMapWorkable.getRecipeCatalystPriority())
```

优先级让高等级机器排在前面；IC2 核反应堆被单独注册到假配方映射。

---

## 6. HandlerInfo 元数据注册

`NEIGTConfig` 通过 Forge 事件总线监听 NEI 的 `NEIRegisterHandlerInfosEvent`，为每个类别提供标签页图标、高度、每页配方数等显示元数据：

来源：[`NEIGTConfig.registerHandlerInfo()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L131)

```java
@SubscribeEvent
public void registerHandlerInfo(NEIRegisterHandlerInfosEvent event) {
    RecipeCategory.ALL_RECIPE_CATEGORIES.values()
        .forEach(recipeCategory -> {
            HandlerInfo.Builder builder = createHandlerInfoBuilderTemplate(recipeCategory);
            // 优先用类别自定义的 handlerInfoCreator，
            // 否则从催化剂索引取第一台机器作为显示图标
            event.registerHandlerInfo(handlerInfo);
        });
}
```

模板默认 `setShiftY(6).setHeight(135).setMaxRecipesPerPage(2)`（[第 164-171 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L164)）。

---

## 7. 特殊处理器：`GTNEIImprintHandler`

电路装配线（CAL）的印记配方不走通用处理器，而是继承 NEI 的 `ShapelessRecipeHandler` 单独实现：

来源：[`GTNEIImprintHandler`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/GTNEIImprintHandler.java#L18)

```java
public class GTNEIImprintHandler extends ShapelessRecipeHandler {
    @Override
    public String getOverlayIdentifier() {
        return "gt.recipe.cal-imprinting";
    }
```

它在 `registerHandlers()` 末尾被单独加入两个查询列表（`NEIGTConfig.java` 第 96-97 行），数据来自 BartWorks 的电路装配线配方映射。

---

## 8. 其他注册项

`loadConfig()` 还完成两类辅助注册：

- **物品条目**（[`registerItemEntries()` 第 115 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L115)）：`API.addItemListEntry()` 把容积烧瓶等隐藏物品补进 NEI 物品列表；
- **数据导出器**（[`registerDumpers()` 第 119 行](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/nei/NEIGTConfig.java#L119)）：`API.addOption()` 注册一组 `gregtech.nei.dumper` 下的导出工具，可从 NEI 选项面板 dump 机器、材料、MetaItem 等数据为 CSV。

---

## 9. 小结：注册链全景

| 步骤 | GT5 代码 | NEI 机制 |
|------|----------|----------|
| 插件发现 | `NEIGTConfig implements IConfigureNEI`（`NEI*Config` 命名） | `ClassDiscoverer` 扫描并实例化 |
| 处理器注册 | 遍历 `RecipeCategory`，创建 `GTNEIDefaultHandler` | 加入 `craftinghandlers` / `usagehandlers` 列表 |
| 附属模组兼容 | `FMLInterModComms.sendRuntimeMessage("NEIPlugins", ...)` | NEIPlugins 接收 IMC 注册标签页 |
| 催化剂 | `generateRecipeCatalystIndex()` 扫描机器 | `API.addRecipeCatalyst()` |
| 显示元数据 | `@SubscribeEvent registerHandlerInfo()` | `NEIRegisterHandlerInfosEvent` |
| 查询响应 | `loadCraftingRecipes` / `loadUsageRecipes` / `getUsageAndCatalystHandler` | R/U 键分发到各 handler |
