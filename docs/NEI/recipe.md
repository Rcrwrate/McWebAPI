# NEI 配方查询机制

## 结论

NEI 的配方查询在**客户端本地执行**。玩家查询一个物品时，NEI 不会向服务端发送“查询配方”请求，而是遍历客户端已经注册的 `ICraftingHandler` 或 `IUsageHandler`，让每个 handler 构造一个仅包含匹配配方的新实例，最后将非空结果交给配方 GUI 显示。

原版工作台和熔炉配方的数据来自 `CraftingManager`、`FurnaceRecipes` 等逻辑端注册表，因此这部分**数据遍历和物品匹配逻辑可以在服务端重新实现**。但是，NEI 当前的查询框架同时依赖客户端 GUI、LWJGL、客户端配置和客户端物品缓存，不能原样加载到专用服务端。

## 核心调用链

```text
玩家按配方键 / 用途键，或点击物品
  ├─ 配方：GuiCraftingRecipe.openRecipeGui("item", stack)
  │   └─ getCraftingHandlers("item", stack)
  │       └─ RecipeHandlerQuery.runWithProfiling()
  │           └─ handler.getRecipeHandler("item", stack)
  │               └─ TemplateRecipeHandler.newInstance()
  │                   └─ loadCraftingRecipes(stack)
  │
  └─ 用途：GuiUsageRecipe.openRecipeGui("item", stack)
      └─ getUsageHandlers("item", stack)
          └─ RecipeHandlerQuery.runWithProfiling()
              └─ handler.getUsageHandler("item", stack)
                  └─ TemplateRecipeHandler.newInstance()
                      └─ loadUsageRecipes(stack)
```

### 关键文件

| 文件 | 说明 |
|------|------|
| [`ShortcutInputHandler.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/api/ShortcutInputHandler.java) | 处理配方键、用途键和鼠标点击，发起查询 |
| [`GuiCraftingRecipe.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/GuiCraftingRecipe.java) | 查询所有能产出目标物品的 handler |
| [`GuiUsageRecipe.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/GuiUsageRecipe.java) | 查询所有使用目标物品的 handler |
| [`RecipeHandlerQuery.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/RecipeHandlerQuery.java) | 调度串行/并行 handler、过滤空结果并排序 |
| [`TemplateRecipeHandler.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/TemplateRecipeHandler.java) | handler 模板，创建查询实例并保存匹配配方 |
| [`ShapedRecipeHandler.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/ShapedRecipeHandler.java) | 原版有序合成及 Forge 有序矿辞配方查询 |
| [`ShapelessRecipeHandler.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/ShapelessRecipeHandler.java) | 原版无序合成及 Forge 无序矿辞配方查询 |
| [`FurnaceRecipeHandler.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/FurnaceRecipeHandler.java) | 熔炉配方查询 |
| [`PositionedStack.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/PositionedStack.java) | 带 GUI 坐标的配方输入/输出，以及候选物品匹配 |
| [`NEIServerUtils.java`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/NEIServerUtils.java) | `ItemStack` 配方匹配等通用工具 |

---

## 1. 查询入口

`ShortcutInputHandler` 根据按键或鼠标操作区分“查看配方”和“查看用途”。

来源：[`ShortcutInputHandler` 的配方/用途快捷键](../../tools/NotEnoughItems/src/main/java/codechicken/nei/api/ShortcutInputHandler.java#L147)

```java
if (KeyManager.isKeyDown("recipe.recipe")) {
    return GuiCraftingRecipe.openRecipeGui("item", stackover);
}

if (KeyManager.isKeyDown("recipe.usage")) {
    return GuiUsageRecipe.openRecipeGui("item", stackover);
}
```

鼠标左键查询配方，右键查询用途：

来源：[`ShortcutInputHandler.handleMouseClick()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/api/ShortcutInputHandler.java#L175)

```java
if (button == 0) {
    return GuiCraftingRecipe.openRecipeGui("item", stackover.copy());
} else if (button == 1) {
    return GuiUsageRecipe.openRecipeGui("item", stackover.copy());
}
```

这里的 `"item"` 是查询类型 ID。NEI 还允许 handler 定义 `"all"`、`"smelting"`、`"fuel"` 等类型，用来查询全部配方或某类机器操作。

---

## 2. 查询参数规范化

打开配方 GUI 前，NEI 会复制目标 `ItemStack`，再依次交给注册的 stack stringify handler 规范化。附加模组可以借此删除不应影响配方查询的 NBT，或把特殊物品转换成稳定的查询形式。

来源：[`GuiCraftingRecipe.createRecipeGui()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/GuiCraftingRecipe.java#L35)

```java
for (int i = 0; i < results.length; i++) {
    if (results[i] instanceof ItemStack stack) {
        results[i] = StackInfo.normalizeRecipeQueryStack(stack.copy());
    }
}
```

来源：[`StackInfo.normalizeRecipeQueryStack()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/StackInfo.java#L79)

```java
public static ItemStack normalizeRecipeQueryStack(ItemStack stack) {
    ItemStack result = null;

    for (int i = stackStringifyHandlers.size() - 1; i >= 0 && result == null; i--) {
        result = stackStringifyHandlers.get(i).normalizeRecipeQueryStack(stack);
    }

    return result == null ? stack : result;
}
```

用途查询在 [`GuiUsageRecipe.openRecipeGui()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/GuiUsageRecipe.java#L23) 中执行相同的规范化。

---

## 3. Handler 注册与分发

### 客户端注册

NEI 的默认 handler 在 `IMCForNEI.IMCSender()` 中注册。每种配方可以分别注册产物查询 handler 和用途查询 handler。

来源：[`IMCForNEI.IMCSender()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/IMCForNEI.java#L27)

```java
API.registerRecipeHandler(new ShapedRecipeHandler());
API.registerUsageHandler(new ShapedRecipeHandler());

API.registerRecipeHandler(new ShapelessRecipeHandler());
API.registerUsageHandler(new ShapelessRecipeHandler());

API.registerRecipeHandler(new FurnaceRecipeHandler());
API.registerUsageHandler(new FurnaceRecipeHandler());
```

`API` 最终把 handler 保存到 `GuiCraftingRecipe.craftinghandlers` 或 `GuiUsageRecipe.usagehandlers`。配置为串行执行的 handler 会保存到单独列表。

来源：[`GuiCraftingRecipe.registerRecipeHandler()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/GuiCraftingRecipe.java#L161)

```java
if (NEIClientConfig.serialHandlers.contains(handlerId)) {
    serialCraftingHandlers.add(handler);
} else {
    craftinghandlers.add(handler);
}
```

模组机器配方通常不在 `CraftingManager` 中。对应模组通过 NEI API 注册自己的 handler，并在 handler 内部遍历该模组的 recipe map。因此，“NEI 查询配方”实际是**遍历一组可扩展 handler**，而不是查询一个统一的 NEI 配方数据库。

### 产物查询分发

来源：[`GuiCraftingRecipe.getCraftingHandlers()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/GuiCraftingRecipe.java#L75)

```java
if ("all".equals(outputId)) {
    recipeHandlerFunction = GuiCraftingRecipe::buildAllRecipesHandler;
} else if ("recipeId".equals(outputId)) {
    // 先限制 handler 类型，再按目标物品查询
    recipeHandlerFunction = h -> h.getRecipeHandler("item", stack);
} else {
    recipeHandlerFunction = h -> h.getRecipeHandler(outputId, results);
}
```

### 用途查询分发

来源：[`GuiUsageRecipe.getUsageHandlers()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/GuiUsageRecipe.java#L54)

```java
new RecipeHandlerQuery<>(
    "all".equals(inputId)
        ? GuiUsageRecipe::buildAllRecipesHandler
        : h -> getUsageOrCatalystHandler(h, inputId, ingredients),
    GuiUsageRecipe.usagehandlers,
    GuiUsageRecipe.serialUsageHandlers,
    // ...
);
```

用途查询还会检查目标物品是不是某个 handler 的配方催化剂。例如查询机器方块的用途时，可以直接展示该机器能够处理的全部配方。按住 Control 可以跳过这项行为。

来源：[`GuiUsageRecipe.getUsageOrCatalystHandler()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/GuiUsageRecipe.java#L112)

---

## 4. 每次查询都会创建 Handler 实例

`TemplateRecipeHandler` 中的 `arecipes` 保存当前实例匹配到的配方。执行一次查询时，NEI 通过无参构造器创建新的同类型 handler，然后调用加载方法填充该列表。

来源：[`TemplateRecipeHandler.newInstance()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/TemplateRecipeHandler.java#L532)

```java
public TemplateRecipeHandler newInstance() {
    try {
        findFuelsOnce();
        return getClass().getConstructor().newInstance();
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
```

来源：[`TemplateRecipeHandler.getRecipeHandler()` / `getUsageHandler()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/TemplateRecipeHandler.java#L563)

```java
public ICraftingHandler getRecipeHandler(String outputId, Object... results) {
    TemplateRecipeHandler handler = newInstance();
    handler.loadCraftingRecipes(outputId, results);
    return handler;
}

public IUsageHandler getUsageHandler(String inputId, Object... ingredients) {
    TemplateRecipeHandler handler = newInstance();
    handler.loadUsageRecipes(inputId, ingredients);
    return handler;
}
```

基础实现将 `"item"` 查询转发到接受 `ItemStack` 的简化方法，具体 handler 负责扫描自己的数据源并向 `arecipes` 添加结果。

来源：[`TemplateRecipeHandler.loadCraftingRecipes()` / `loadUsageRecipes()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/TemplateRecipeHandler.java#L407)

```java
public void loadCraftingRecipes(String outputId, Object... results) {
    if (outputId.equals("item")) loadCraftingRecipes((ItemStack) results[0]);
}

public void loadUsageRecipes(String inputId, Object... ingredients) {
    if (inputId.equals("item")) loadUsageRecipes((ItemStack) ingredients[0]);
}
```

---

## 5. 并行查询和结果过滤

`RecipeHandlerQuery` 先执行配置为串行的 handler，再通过 `ItemList.forkJoinPool` 和 `parallelStream()` 执行其余 handler。

来源：[`RecipeHandlerQuery.getRecipeHandlersParallel()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/RecipeHandlerQuery.java#L53)

```java
ArrayList<T> handlers = getSerialHandlersWithRecipes();
handlers.addAll(getHandlersWithRecipes());
handlers.sort(NEIClientConfig.HANDLER_COMPARATOR);
```

并行部分：

来源：[`RecipeHandlerQuery.getHandlersWithRecipes()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/RecipeHandlerQuery.java#L88)

```java
return ItemList.forkJoinPool.submit(
    () -> recipeHandlers.parallelStream()
        .map(handler -> recipeHandlerFunction.apply(handler))
        .filter(h -> h != null && h.numRecipes() > 0
            && SearchRecipeHandler.findFirst(h, recipeIndex -> true) != -1)
        .collect(Collectors.toCollection(ArrayList::new)))
    .get();
```

最终只保留以下 handler：

- handler 查询没有返回 `null`
- `numRecipes() > 0`
- 至少有一个配方通过当前客户端的配方搜索过滤器

单个 handler 抛出的异常会被记录，并尽量允许其他 handler 继续返回结果。

---

## 6. 原版工作台配方查询

### 查询某个物品的合成配方

`ShapedRecipeHandler` 遍历 `CraftingManager` 的全部 `IRecipe`，先比较配方产物，再把支持的有序配方转换成 `CachedShapedRecipe`。

来源：[`ShapedRecipeHandler.loadCraftingRecipes(ItemStack)`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/ShapedRecipeHandler.java#L117)

```java
for (IRecipe irecipe : (List<IRecipe>) CraftingManager.getInstance().getRecipeList()) {
    if (NEIServerUtils.areStacksSameTypeCrafting(irecipe.getRecipeOutput(), result)) {
        CachedShapedRecipe recipe = null;
        if (irecipe instanceof ShapedRecipes) {
            recipe = new CachedShapedRecipe((ShapedRecipes) irecipe);
        } else if (irecipe instanceof ShapedOreRecipe) {
            recipe = forgeShapedRecipe((ShapedOreRecipe) irecipe);
        }

        if (recipe != null) {
            recipe.computeVisuals();
            arecipes.add(recipe);
        }
    }
}
```

### 查询某个物品的用途

用途查询同样遍历所有配方，构造输入列表后检查目标物品是否出现在任意输入候选项中。

来源：[`ShapedRecipeHandler.loadUsageRecipes(ItemStack)`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/ShapedRecipeHandler.java#L133)

```java
for (IRecipe irecipe : (List<IRecipe>) CraftingManager.getInstance().getRecipeList()) {
    CachedShapedRecipe recipe = null;
    if (irecipe instanceof ShapedRecipes) {
        recipe = new CachedShapedRecipe((ShapedRecipes) irecipe);
    } else if (irecipe instanceof ShapedOreRecipe) {
        recipe = forgeShapedRecipe((ShapedOreRecipe) irecipe);
    }

    if (recipe == null || !recipe.contains(recipe.ingredients, ingredient.getItem())) continue;

    recipe.computeVisuals();
    if (recipe.contains(recipe.ingredients, ingredient)) {
        recipe.setIngredientPermutation(recipe.ingredients, ingredient);
        arecipes.add(recipe);
    }
}
```

第一次按 `Item` 粗筛，避免不必要的候选项展开；`computeVisuals()` 展开通配 metadata 等显示候选项后，再按完整 `ItemStack` 匹配。

`ShapelessRecipeHandler` 使用相同思路处理 `ShapelessRecipes` 和 `ShapelessOreRecipe`，来源：[`ShapelessRecipeHandler.loadCraftingRecipes(ItemStack)`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/ShapelessRecipeHandler.java#L121) 与 [`loadUsageRecipes(ItemStack)`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/ShapelessRecipeHandler.java#L138)。

---

## 7. 配方输入与物品匹配规则

`PositionedStack` 同时保存 GUI 坐标和一个输入槽允许的全部 `ItemStack`。构造时，矿辞列表、数组或单个物品会通过 `extractRecipeItems()` 统一转换成数组。

来源：[`PositionedStack` 构造器](../../tools/NotEnoughItems/src/main/java/codechicken/nei/PositionedStack.java#L37)

```java
items = NEIServerUtils.extractRecipeItems(object);
```

通配 metadata（`Short.MAX_VALUE`）会借助客户端 `ItemList.itemMap` 展开成该物品的已知变体：

来源：[`PositionedStack.generatePermutations()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/PositionedStack.java#L53)

输入槽匹配最终调用 `areStacksSameTypeCrafting()`：

来源：[`PositionedStack.contains(ItemStack)`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/PositionedStack.java#L170)

```java
public boolean contains(ItemStack ingredient) {
    for (ItemStack item : items) {
        if (NEIServerUtils.areStacksSameTypeCrafting(item, ingredient)) return true;
    }
    return false;
}
```

来源：[`NEIServerUtils.areStacksSameTypeCrafting()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/NEIServerUtils.java#L231)

```java
return stack1 != null && stack2 != null
    && stack1.getItem() == stack2.getItem()
    && (stack1.getItemDamage() == stack2.getItemDamage()
        || stack1.getItemDamage() == OreDictionary.WILDCARD_VALUE
        || stack2.getItemDamage() == OreDictionary.WILDCARD_VALUE
        || stack1.getItem().isDamageable());
```

该基础匹配比较物品类型和 metadata，支持矿辞通配值，并将可损耗物品视为同类；它默认不要求 NBT 完全相等。需要 NBT 的 handler 可以改用 `containsWithNBT()` 或其他自定义判断。

---

## 8. 熔炉配方查询

熔炉 handler 直接遍历 `FurnaceRecipes.smelting().getSmeltingList()`。

来源：[`FurnaceRecipeHandler.loadCraftingRecipes(ItemStack)`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/FurnaceRecipeHandler.java#L94)

```java
Map<ItemStack, ItemStack> recipes =
    (Map<ItemStack, ItemStack>) FurnaceRecipes.smelting().getSmeltingList();

for (Entry<ItemStack, ItemStack> recipe : recipes.entrySet()) {
    if (NEIServerUtils.areStacksSameType(recipe.getValue(), result)) {
        arecipes.add(new SmeltingPair(recipe.getKey(), recipe.getValue()));
    }
}
```

查询用途时比较熔炼输入，并将匹配到的输入变体固定为查询目标：

来源：[`FurnaceRecipeHandler.loadUsageRecipes(ItemStack)`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/FurnaceRecipeHandler.java#L110)

---

## 9. 是否向服务端查询

NEI 的服务端包处理器 [`NEISPH.handlePacket()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/NEISPH.java#L31) 处理给予物品、修改槽位、天气、时间、附魔和容器同步等操作，但没有配方查询消息。配方快捷键直接进入客户端 `GuiCraftingRecipe` / `GuiUsageRecipe`，中间不存在客户端到服务端的配方请求。

这意味着联机时看到的配方来自客户端安装的模组及其 NEI 插件。通常客户端和服务端使用相同模组配置，因此双方注册表一致；如果两端的配方注册或配置不同，NEI 展示内容并不自动代表服务端一定接受该配方。

---

## 10. 服务端运行能力分析

### 生命周期明确区分客户端和服务端

默认配方 handler 只在物理客户端注册。

来源：[`NEIModContainer.init()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/NEIModContainer.java#L109)

```java
public void init(FMLInitializationEvent event) {
    if (CommonUtils.isClient()) {
        ClientHandler.load();
        IMCForNEI.IMCSender(); // 注册默认配方 handler
    }
    ServerHandler.load();
}
```

专用服务端会执行 [`ServerHandler.load()`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/ServerHandler.java#L24)，但不会执行 `IMCForNEI.IMCSender()`，因此不会建立 `craftinghandlers` / `usagehandlers` 查询列表。

### 各部分兼容性

| 部分 | 能否原样在专用服务端运行 | 原因 |
|------|--------------------------|------|
| `CraftingManager` / `FurnaceRecipes` 遍历 | 可以 | 属于逻辑端配方注册表 |
| `NEIServerUtils.areStacksSameTypeCrafting()` | 可以 | 只依赖通用 `ItemStack` 和矿辞类型 |
| `GuiCraftingRecipe` / `GuiUsageRecipe` | 不可以 | 直接访问 `Minecraft`、`GuiScreen` 和客户端面板 |
| `RecipeHandlerQuery` | 不可以 | 依赖 `NEIClientConfig`、客户端 `ItemList`、客户端聊天提示和 GUI 搜索过滤器 |
| `IRecipeHandler` | 不可以原样加载 | 接口签名引用客户端 `GuiContainer` 和 LWJGL `Keyboard` |
| `TemplateRecipeHandler` | 不可以 | 混合 OpenGL 绘制、GUI 注册、客户端配置、物品显示缓存和查询逻辑 |
| 现有模组 NEI handler | 通常不可以 | 多数按客户端插件注册，并直接引用机器 GUI 或渲染代码 |

例如，`IRecipeHandler` 本身已经把查询数据接口和客户端展示接口放在一起：

来源：[`IRecipeHandler`](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/IRecipeHandler.java#L18)

```java
void drawBackground(int recipe);
void drawForeground(int recipe);
boolean hasOverlay(GuiContainer gui, Container container, int recipe);
boolean keyTyped(GuiRecipe<?> gui, char keyChar, int keyCode, int recipe);
```

`TemplateRecipeHandler` 的静态初始化还会注册 GUI 输入和 tooltip handler：

来源：[`TemplateRecipeHandler` 静态初始化](../../tools/NotEnoughItems/src/main/java/codechicken/nei/recipe/TemplateRecipeHandler.java#L365)

```java
static {
    GuiContainerManager.addInputHandler(new RecipeTransferRectHandler());
    GuiContainerManager.addTooltipHandler(new RecipeTransferRectHandler());
}
```

因此不能通过“只调用 `getRecipeHandler()`”来规避客户端依赖；JVM 在装载这些类型时就可能解析专用服务端不存在的客户端类。

---

## 11. 服务端实现建议

如果只需要查询原版工作台和熔炉配方，可以在服务端独立实现纯数据查询：

1. 遍历 `CraftingManager.getInstance().getRecipeList()` 或 `FurnaceRecipes.smelting().getSmeltingList()`。
2. 使用服务端安全的 `ItemStack` 比较函数匹配产物或输入。
3. 将结果转换成不带 GUI 坐标、纹理和渲染状态的 DTO。
4. 如需发给客户端，再定义专用网络协议序列化该 DTO。

如果需要覆盖全部模组机器配方，则需要更大范围的拆分：

- 定义公共侧的 `RecipeDataProvider`，只负责查询输入、输出、概率、耗时等数据。
- 客户端 NEI handler 只负责把公共数据转换成 `PositionedStack` 并绘制。
- 对每个模组的 recipe map 提供服务端安全 adapter。
- 在服务端单独注册 provider，不能复用只在 `IConfigureNEI` 中注册的客户端 handler。
- 不依赖 `ItemList.itemMap` 展开通配物品；服务端应从物品注册表、矿辞或 provider 明确提供候选项。

最终边界是：**原版配方数据和基础匹配算法适合服务端运行；NEI 现有的 handler/query/GUI 整体是客户端架构，需要解耦后才能迁移。**
