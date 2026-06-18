# GT5 多方块机器配方执行检查机制

> 源代码路径：`tools/GT5-Unofficial-5.09.51.482`

## 核心调用链

```
MTEMultiBlockBase.checkProcessing()
  → setupProcessingLogic(processingLogic)
    → setProcessingLogicPower(logic)          ← 设置电压/电流
  → doCheckRecipe()
    → processingLogic.process()
      → validateAndCalculateRecipe(recipe)
        → createParallelHelper(recipe)        ← 功率检查
        → createOverclockCalculator(recipe)   ← 电压等级检查
```

### 关键文件

| 文件 | 说明 |
|------|------|
| [`MTEMultiBlockBase.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/implementations/MTEMultiBlockBase.java) | 多方块机器基类，包含 `setProcessingLogicPower()`、能源仓数据获取方法 |
| [`ProcessingLogic.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java) | 配方处理逻辑核心，执行配方查找、验证、并行计算 |
| [`ParallelHelper.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java) | 并行计算器，执行功率检查和电压等级检查 |
| [`OverclockCalculator.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/OverclockCalculator.java) | 超频计算器，计算超频后的功耗和耗时 |
| [`ExoticEnergyInputHelper.java`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ExoticEnergyInputHelper.java) | 能源仓辅助类，汇总电压/电流 |

---

## 1. 电压和电流的设置

### `setProcessingLogicPower`

多方块机器在每次检查配方前，通过 `setProcessingLogicPower` 将能源仓的电压和电流信息注入 `ProcessingLogic`。

来源：[`MTEMultiBlockBase.setProcessingLogicPower()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/implementations/MTEMultiBlockBase.java#L1020)

```java
protected void setProcessingLogicPower(ProcessingLogic logic) {
    boolean useSingleAmp = mEnergyHatches.size() == 1 && mExoticEnergyHatches.isEmpty();
    logic.setAvailableVoltage(getAverageInputVoltage());
    logic.setAvailableAmperage(useSingleAmp ? 1 : getMaxInputAmps());
    logic.setAmperageOC(true);
}
```

**关键设计**：

- `availableVoltage` = 能源仓的**平均输入电压**（单仓时即该仓电压）
- `availableAmperage` = 单能源仓时为 `1`，多能源仓时为总安培数
- `amperageOC = true` 表示允许使用电流（安培数）参与超频计算
- 最终 `availableEUt = availableVoltage × availableAmperage` 代表机器的**总可用功率**

### `ProcessingLogic` 中的设置入口

`ProcessingLogic` 将电压和电流分别存储，用于后续的并行计算和超频计算：

来源：[`ProcessingLogic.availableVoltage/availableAmperage`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L50)

```java
protected long availableVoltage;
protected long availableAmperage;
```

来源：[`ProcessingLogic.setAvailableVoltage()` / `setAvailableAmperage()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L228)

```java
public ProcessingLogic setAvailableVoltage(long voltage) {
    this.availableVoltage = voltage;
    return this;
}

public ProcessingLogic setAvailableAmperage(long amperage) {
    this.availableAmperage = amperage;
    return this;
}
```

---

## 2. 功率传递到检查逻辑

### `createParallelHelper` — 总功率传入

`ProcessingLogic` 创建 `ParallelHelper` 时，将电压 × 电流作为**总可用 EU/t** 传入：

来源：[`ProcessingLogic.createParallelHelper()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L536)

```java
protected ParallelHelper createParallelHelper(@Nonnull GTRecipe recipe) {
    return new ParallelHelper().setRecipe(recipe)
        .setItemInputs(inputItems)
        .setFluidInputs(inputFluids)
        .setAvailableEUt(availableVoltage * availableAmperage)  // ← 总功率
        .setMachine(machine, protectItems, protectFluids)
        .setRecipeLocked(recipeLockableMachine, isRecipeLocked)
        .setMaxParallel(maxParallel)
        .setEUtModifier(euModifier)
        .enableBatchMode(batchSize)
        .setConsumption(true)
        .setOutputCalculation(true);
}
```

### `createOverclockCalculator` — 电压和电流分别传入

`OverclockCalculator` 接收的是**电压**和**安培数**两个独立值，用于超频层数计算：

来源：[`ProcessingLogic.createOverclockCalculator()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L554)

```java
protected OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
    return new OverclockCalculator().setRecipeEUt(recipe.mEUt)
        .setAmperage(availableAmperage)
        .setEUt(availableVoltage)
        .setMaxTierSkips(maxTierSkips)
        .setDuration(recipe.mDuration)
        .setDurationModifier(speedBoost)
        .setEUtDiscount(euModifier)
        .setAmperageOC(amperageOC)
        .setDurationDecreasePerOC(overClockTimeReduction)
        .setEUtIncreasePerOC(overClockPowerIncrease);
}
```

---

## 3. 三重检查 — `ParallelHelper.determineParallel()`

这是配方能否执行的**核心判定方法**。在 `build()` 调用后触发，按顺序进行三步检查。

来源：[`ParallelHelper.build()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L301)

```java
public ParallelHelper build() {
    if (built) {
        throw new IllegalStateException("Tried to build twice");
    }
    if (recipe == null) {
        throw new IllegalStateException("Recipe is not set");
    }
    built = true;
    determineParallel();
    return this;
}
```

### 检查 ①：功率检查（总 EU/t 是否足够）

来源：[`ParallelHelper.determineParallel()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L375)

```java
protected void determineParallel() {
    if (maxParallel <= 0) {
        return;
    }
    // ... input 初始化 ...

    if (calculator == null) {
        calculator = new OverclockCalculator().setEUt(availableEUt)
            .setRecipeEUt(recipe.mEUt)
            .setDuration(recipe.mDuration)
            .setEUtDiscount(eutModifier);
    }

    double heatDiscountMultiplier = calculator.calculateHeatDiscountMultiplier();

    final int tRecipeEUt = (int) Math.ceil(recipe.mEUt * eutModifier * heatDiscountMultiplier);
    if (availableEUt < tRecipeEUt) {
        result = CheckRecipeResultRegistry.insufficientPower(tRecipeEUt);  // ← 功率不足
        return;
    }
    if (!calculator.getAllowedTierSkip()) {
        result = CheckRecipeResultRegistry.insufficientVoltage(tRecipeEUt);  // ← 电压等级不足
        return;
    }
    // ... 继续计算并行数 ...
}
```

- `tRecipeEUt` = 配方基础 EU/t × EU 修正系数 × 热折扣修正
- `availableEUt` = `availableVoltage × availableAmperage`（总功率）
- **如果总功率 < 单次配方消耗 → 返回 `insufficientPower`（功率不足）**

### 检查 ②：电压等级检查（tier skip 检查）

`getAllowedTierSkip()` 在 `OverclockCalculator` 中实现：

来源：[`OverclockCalculator.getAllowedTierSkip()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/OverclockCalculator.java#L303)

```java
public boolean getAllowedTierSkip() {
    if (this.maxTierSkip == Integer.MAX_VALUE) return true;
    return recipeEUt <= machineVoltage * GTUtility.powInt(4, maxTierSkip);
}
```

- 判断 `recipeEUt <= machineVoltage × 4^maxTierSkips`
- 默认 `maxTierSkips = 1`（见 [`ProcessingLogic.maxTierSkips`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L52)），即配方 EU/t 不得超过机器电压的 4 倍
- **如果不满足 → 返回 `insufficientVoltage`（电压等级不足）**
- 这防止了"跳级"运行配方（例如用 LV 机器跑 IV 配方）

`maxTierSkips` 的默认值和设置方法：

来源：[`ProcessingLogic.setMaxTierSkips()` / `setUnlimitedTierSkips()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L246)

```java
protected int maxTierSkips = 1;

public ProcessingLogic setMaxTierSkips(int tierSkips) {
    this.maxTierSkips = tierSkips;
    return this;
}

public ProcessingLogic setUnlimitedTierSkips() {
    this.maxTierSkips = Integer.MAX_VALUE;
    return this;
}
```

### 检查 ③：并行数量受功率限制

通过前两步检查后，实际可执行的并行数受总功率限制：

来源：[`ParallelHelper.determineParallel()` — 并行计算](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L410)

```java
    // ... 超频下 1 tick 的并行倍率计算 ...
    int originalMaxParallel = maxParallel;
    calculator.setParallel(originalMaxParallel);

    // 计算超频后低于 1 tick 的并行倍率
    if (calculator.hasDurationUnderOneTickSupplier()) {
        if (calculator.getDurationUnderOneTickSupplier() < 1) {
            maxParallel = GTUtility.safeInt((long) (maxParallel / calculator.getDurationUnderOneTickSupplier()), 0);
        }
    } else {
        maxParallel = GTUtility.safeInt((long) (maxParallel * calculator.calculateMultiplierUnderOneTick()), 0);
    }
    int maxParallelBeforeBatchMode = maxParallel;
    if (batchMode) {
        maxParallel = GTUtility.safeInt((long) maxParallel * batchModifier, 0);
    }

    // ... 输出空间检查（void protection）...

    // 实际最大并行数 = min(配置最大并行, 总功率 / 单配方EUt)
    int actualMaxParallel = tRecipeEUt > 0 ? (int) Math.min(maxParallelBeforeBatchMode, availableEUt / tRecipeEUt)
        : maxParallelBeforeBatchMode;
```

- 实际最大并行数 = `min(配置最大并行, 总功率 / 单配方EUt)`
- **电流（安培数）通过总功率 `availableEUt` 间接影响并行数**
- 如果 `tRecipeEUt = 0`（免费配方），则并行数不受功率限制

---

## 4. 超频计算中的电流作用

### `OverclockCalculator.calculateOverclock()`

来源：[`OverclockCalculator.calculateOverclock()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/OverclockCalculator.java#L332)

```java
protected void calculateOverclock() {
    double duration = durationUnderOneTickSupplier != null ? durationUnderOneTickSupplier.get()
        : this.duration * durationModifier;

    currentParallel = Math.max(currentParallel, parallel);

    // 配方功率 = 配方EUt × 并行 × EU修正 × 热折扣
    double recipePower = recipeEUt * parallel * eutModifier * calculateHeatDiscountMultiplier();
    // 机器功率 = 电压 × (amperageOC ? 安培 : min(安培, 并行))
    double machinePower = machineVoltage * (amperageOC ? machineAmperage : Math.min(machineAmperage, parallel));
    // 可超频层数 = log4(机器功率 / 配方功率)
    int tiersAbove = (int) GTUtility.log4((long) machinePower / Math.max((long) recipePower, 32));

    if (noOverclock) {
        calculatedConsumption = (long) Math.ceil(recipePower);
        calculatedDuration = (int) Math.ceil(duration);
        return;
    }

    // ... 激光超频特殊处理 ...

    overclocks = Math.min(maxOverclocks, tiersAbove);

    // 如果不使用电流超频，则超频层数受电压等级差限制
    if (!amperageOC) {
        int voltageTierMachine = (int) Math.max(GTUtility.log4ceil(machineVoltage / 8), 1);
        int voltageTierRecipe = (int) Math.max(GTUtility.log4ceil(recipeEUt / 8), 1);
        overclocks = Math.min(overclocks, voltageTierMachine - voltageTierRecipe);
    }

    overclocks = Math.max(overclocks, 0);

    // 拆分热超频和普通超频
    int heatOverclocks = Math.min(heatOC ? (machineHeat - recipeHeat) / HEAT_OVERCLOCK_THRESHOLD : 0, overclocks);
    int regularOverclocks = overclocks - heatOverclocks;

    // 计算最终功耗和耗时
    calculatedConsumption = (long) Math.ceil(recipePower * GTUtility.powInt(eutIncreasePerOC, overclocks));
    duration /= GTUtility.powInt(durationDecreasePerHeatOC, heatOverclocks);
    duration /= GTUtility.powInt(durationDecreasePerOC, regularOverclocks);
    calculatedDuration = (int) Math.max(duration, 1);
}
```

**超频逻辑要点**：

| 变量 | 公式 | 说明 |
|------|------|------|
| `recipePower` | `recipeEUt × parallel × eutModifier × heatDiscount` | 配方总功率 |
| `machinePower` | `machineVoltage × machineAmperage` (amperageOC=true) | 机器总功率 |
| `tiersAbove` | `log4(machinePower / recipePower)` | 可超频层数 |
| `overclocks` | `min(maxOverclocks, tiersAbove)` | 实际超频层数 |
| `calculatedConsumption` | `recipePower × 4^overclocks` | 超频后功耗 |

- **电流越大 → 机器总功率越大 → 可超频层数越多**
- 当 `amperageOC = false` 时，超频层数额外受电压等级差限制（不能用电流跨级超频）

---

## 5. 能源仓数据获取方法

`MTEMultiBlockBase` 提供以下方法获取能源仓的电压、电流、功率信息：

来源：[`MTEMultiBlockBase.getMaxInputVoltage()` 等](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/implementations/MTEMultiBlockBase.java#L1460)

```java
/**
 * Sums up voltage of energy hatches. Amperage does not matter.
 */
public long getMaxInputVoltage() {
    long rVoltage = 0;
    for (MTEHatchEnergy tHatch : validMTEList(mEnergyHatches)) rVoltage += tHatch.getBaseMetaTileEntity()
        .getInputVoltage();
    return rVoltage;
}

public long getAverageInputVoltage() {
    return ExoticEnergyInputHelper.getAverageInputVoltageMulti(mEnergyHatches);
}

public long getMaxInputAmps() {
    return ExoticEnergyInputHelper.getMaxWorkingInputAmpsMulti(mEnergyHatches);
}

public long getMaxInputEu() {
    return ExoticEnergyInputHelper.getTotalEuMulti(mEnergyHatches);
}

/**
 * Sums up max input EU/t of energy hatches, amperage included.
 */
public long getMaxInputPower() {
    long eut = 0;
    for (MTEHatchEnergy tHatch : validMTEList(mEnergyHatches)) {
        IGregTechTileEntity baseTile = tHatch.getBaseMetaTileEntity();
        eut += baseTile.getInputVoltage() * baseTile.getInputAmperage();
    }
    return eut;
}
```

### 方法对比

| 方法 | 返回值 | 用途 |
|------|--------|------|
| `getMaxInputVoltage()` | 所有能源仓**电压之和** | 不含电流的电压总和 |
| `getAverageInputVoltage()` | 能源仓**平均电压** | 默认 `setProcessingLogicPower` 使用此值 |
| `getMaxInputAmps()` | 最大工作**安培数** | 多仓时作为 `availableAmperage` |
| `getMaxInputEu()` | 通过 `ExoticEnergyInputHelper` 获取总 EU | 含奇异能源仓 |
| `getMaxInputPower()` | 电压 × 安培的**功率之和** | 含电流的总功率 |

> **注意**：默认 `setProcessingLogicPower` 用的是 `getAverageInputVoltage()`（平均电压）而非最大电压，用 `getMaxInputAmps()` 获取总安培。这样 `availableEUt = 平均电压 × 总安培` 就是机器能使用的总功率。各多方块子类可重写 `setProcessingLogicPower` 来调整策略。

---

## 6. 配方验证完整流程

### `ProcessingLogic.validateAndCalculateRecipe`

来源：[`ProcessingLogic.validateAndCalculateRecipe()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L436)

```java
private CalculationResult validateAndCalculateRecipe(@Nonnull GTRecipe recipe) {
    CheckRecipeResult result = validateRecipe(recipe);          // ① 自定义验证（默认通过）
    if (!result.wasSuccessful()) {
        return CalculationResult.ofFailure(result);
    }

    ParallelHelper helper = createParallelHelper(recipe);       // ② 创建并行计算器（含功率检查）
    OverclockCalculator calculator = createOverclockCalculator(recipe);  // ③ 创建超频计算器（含电压检查）
    helper.setCalculator(calculator);
    helper.build();                                              // ④ 执行检查（determineParallel）

    if (!helper.getResult()
        .wasSuccessful()) {
        return CalculationResult.ofFailure(helper.getResult());
    }

    return CalculationResult.ofSuccess(applyRecipe(recipe, helper, calculator, result));  // ⑤ 应用配方
}
```

### `ProcessingLogic.process` — 配方查找入口

来源：[`ProcessingLogic.process()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L373)

```java
public CheckRecipeResult process() {
    RecipeMap<?> recipeMap = getCurrentRecipeMap();

    if (maxParallelSupplier != null) {
        maxParallel = maxParallelSupplier.get();
    }

    // ... 输入初始化 ...

    // 如果有缓存的 pattern 配方，直接验证
    if (activeDualInv != null) {
        Set<GTRecipe> matchedRecipes = dualInvWithPatternToRecipeCache.get(activeDualInv);
        for (GTRecipe matchedRecipe : matchedRecipes) {
            if (matchedRecipe.maxParallelCalculatedByInputs(1, inputFluids, inputItems) == 1) {
                CalculationResult foundResult = validateAndCalculateRecipe(matchedRecipe);
                return foundResult.checkRecipeResult;
            }
        }
        activeDualInv = null;
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    // 如果锁定了单个配方，直接验证
    if (isRecipeLocked && recipeLockableMachine != null && recipeLockableMachine.getSingleRecipeCheck() != null) {
        SingleRecipeCheck singleRecipeCheck = recipeLockableMachine.getSingleRecipeCheck();
        if (singleRecipeCheck.checkRecipeInputs(false, 1, inputItems, inputFluids) == 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        return validateAndCalculateRecipe(
            recipeLockableMachine.getSingleRecipeCheck()
                .getRecipe()).checkRecipeResult;
    }

    // 遍历所有匹配的配方
    Stream<GTRecipe> matchedRecipes = findRecipeMatches(recipeMap);
    Iterable<GTRecipe> recipeIterable = matchedRecipes::iterator;
    CheckRecipeResult checkRecipeResult = CheckRecipeResultRegistry.NO_RECIPE;
    for (GTRecipe matchedRecipe : recipeIterable) {
        CalculationResult foundResult = validateAndCalculateRecipe(matchedRecipe);
        if (foundResult.successfullyConsumedInputs) {
            return foundResult.checkRecipeResult;
        }
        if (foundResult.checkRecipeResult != CheckRecipeResultRegistry.NO_RECIPE) {
            checkRecipeResult = foundResult.checkRecipeResult;
        }
    }
    return checkRecipeResult;
}
```

---

## 7. 检查结果类型

检查失败时返回的 `CheckRecipeResult` 类型：

| 结果 | 触发条件 | 检查位置 |
|------|---------|---------|
| `insufficientPower` | `availableEUt < tRecipeEUt` | [`ParallelHelper.determineParallel()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L401) |
| `insufficientVoltage` | `!calculator.getAllowedTierSkip()` | [`ParallelHelper.determineParallel()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L405) |
| `NO_RECIPE` | 没有找到匹配配方 | [`ProcessingLogic.process()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L373) |
| `INTERNAL_ERROR` | `currentParallel <= 0` | [`ParallelHelper.determineParallel()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L498) |
| `ITEM_OUTPUT_FULL` | 输出仓已满（void protection） | [`ParallelHelper.determineParallel()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L464) |
| `FLUID_OUTPUT_FULL` | 输出仓已满（void protection） | [`ParallelHelper.determineParallel()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L468) |
| `POWER_OVERFLOW` | 功耗超过 `Long.MAX_VALUE` | [`ProcessingLogic.applyRecipe()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L470) |
| `DURATION_OVERFLOW` | 时长超过 `Integer.MAX_VALUE` | [`ProcessingLogic.applyRecipe()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L473) |

---

## 8. 总结：三重检查逻辑

```
                        ┌─────────────────────────────────────────────┐
                        │          setProcessingLogicPower            │
                        │   voltage = getAverageInputVoltage()        │
                        │   amperage = getMaxInputAmps()              │
                        │   availableEUt = voltage × amperage          │
                        └──────────────────┬──────────────────────────┘
                                           │
                                           ▼
                        ┌─────────────────────────────────────────────┐
                        │          determineParallel()                │
                        │                                             │
            ① 功率检查   │   if (availableEUt < tRecipeEUt)             │
                        │       → insufficientPower                   │
                        │                                             │
            ② 电压检查   │   if (!calculator.getAllowedTierSkip())      │
                        │       → insufficientVoltage                 │
                        │       (recipeEUt <= machineVoltage × 4^N)   │
                        │                                             │
            ③ 并行限制   │   actualParallel = min(maxParallel,          │
                        │       availableEUt / tRecipeEUt)            │
                        └─────────────────────────────────────────────┘
                                           │
                                           ▼
                        ┌─────────────────────────────────────────────┐
                        │          OverclockCalculator.calculate()     │
                        │                                             │
                        │   machinePower = voltage × amperage          │
                        │   tiersAbove = log4(machinePower/recipePower)│
                        │   overclocks = min(maxOverclocks, tiersAbove)│
                        │   consumption = recipePower × 4^overclocks  │
                        │   duration = duration / 2^overclocks        │
                        └─────────────────────────────────────────────┘
```

| 检查项 | 检查内容 | 失败结果 | 源代码位置 |
|--------|---------|---------|-----------|
| **功率检查** | `availableEUt (电压×电流)` ≥ `配方EUt` | `insufficientPower` | [`ParallelHelper.java#L400`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L400) |
| **电压等级检查** | `配方EUt` ≤ `机器电压 × 4^maxTierSkips` | `insufficientVoltage` | [`OverclockCalculator.java#L303`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/OverclockCalculator.java#L303) |
| **并行功率限制** | 实际并行 = `min(最大并行, 总功率/配方EUt)` | 限制并行数 | [`ParallelHelper.java#L476`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L476) |
| **超频层数** | `log4(机器功率/配方功率)` | 限制超频 | [`OverclockCalculator.java#L343`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/OverclockCalculator.java#L343) |
