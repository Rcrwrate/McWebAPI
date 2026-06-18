# GT5 多方块机器多仓升压机制

> 场景：机器拥有 2 个 EV 能源仓（每个 2048V、2A），共 4A EV，升压后可执行 1A IV 配方（8192 EU/t）。

## 数值

```
EV = 2048 EU/t
IV = 8192 EU/t

2个 EV 能源仓 × 2A = 4A EV
总功率 = 2048 × 4 = 8192 EU/t

IV 配方 = 8192 EU/t (1A IV)
```

---

## 第一步：`setProcessingLogicPower` 设置参数

来源：[`MTEMultiBlockBase.setProcessingLogicPower()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/implementations/MTEMultiBlockBase.java#L1020)

```java
protected void setProcessingLogicPower(ProcessingLogic logic) {
    boolean useSingleAmp = mEnergyHatches.size() == 1 && mExoticEnergyHatches.isEmpty();
    logic.setAvailableVoltage(getAverageInputVoltage());  // = 2048 (EV)
    logic.setAvailableAmperage(useSingleAmp ? 1 : getMaxInputAmps());  // = 4 (2A×2仓)
    logic.setAmperageOC(true);  // ← 关键：允许电流参与超频
}
```

此时 `ProcessingLogic` 中：

- `availableVoltage = 2048` (EV 电压)
- `availableAmperage = 4` (总安培数)
- `amperageOC = true`

---

## 第二步：`createParallelHelper` 传入总功率

来源：[`ProcessingLogic.createParallelHelper()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/logic/ProcessingLogic.java#L536)

```java
protected ParallelHelper createParallelHelper(@Nonnull GTRecipe recipe) {
    return new ParallelHelper().setRecipe(recipe)
        .setItemInputs(inputItems)
        .setFluidInputs(inputFluids)
        .setAvailableEUt(availableVoltage * availableAmperage)  // = 2048 × 4 = 8192
        // ...
}
```

`availableEUt = 2048 × 4 = 8192`，恰好等于 IV 配方的 8192 EU/t。

---

## 第三步：三重检查

### 检查 ①：功率检查 — 通过

来源：[`ParallelHelper.determineParallel()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/ParallelHelper.java#L399)

```java
final int tRecipeEUt = (int) Math.ceil(recipe.mEUt * eutModifier * heatDiscountMultiplier);
if (availableEUt < tRecipeEUt) {
    result = CheckRecipeResultRegistry.insufficientPower(tRecipeEUt);
    return;
}
```

- `tRecipeEUt = 8192` (IV 配方)
- `availableEUt = 8192` (2048 × 4)
- `8192 < 8192` → **false** → 功率检查通过

### 检查 ②：电压等级检查（tier skip）— 通过

来源：[`OverclockCalculator.getAllowedTierSkip()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/OverclockCalculator.java#L303)

```java
public boolean getAllowedTierSkip() {
    if (this.maxTierSkip == Integer.MAX_VALUE) return true;
    return recipeEUt <= machineVoltage * GTUtility.powInt(4, maxTierSkip);
}
```

- `recipeEUt = 8192`
- `machineVoltage = 2048` (EV)
- `maxTierSkip = 1` (默认值)
- 判断：`8192 <= 2048 × 4^1 = 8192` → **true** → 电压等级检查通过

**这就是"升压"的关键**：`maxTierSkips = 1` 允许配方 EU/t 最多是机器电压的 **4 倍**（即高 1 个电压等级）。EV(2048) × 4 = IV(8192)，恰好卡在边界上。

### 检查 ③：超频计算 — 0 次超频

来源：[`OverclockCalculator.calculateOverclock()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/OverclockCalculator.java#L332)

```java
protected void calculateOverclock() {
    // ...
    double recipePower = recipeEUt * parallel * eutModifier * calculateHeatDiscountMultiplier();
    // = 8192 × 1 × 1 × 1 = 8192
    double machinePower = machineVoltage * (amperageOC ? machineAmperage : Math.min(machineAmperage, parallel));
    // = 2048 × 4 = 8192  (amperageOC=true，用满安培数)
    int tiersAbove = (int) GTUtility.log4((long) machinePower / Math.max((long) recipePower, 32));
    // = log4(8192 / 8192) = log4(1) = 0
```

- `machinePower = 2048 × 4 = 8192`（`amperageOC=true`，用满安培）
- `recipePower = 8192`
- `tiersAbove = log4(8192 / 8192) = log4(1) = 0`
- `overclocks = min(maxOverclocks, 0) = 0`

**0 次超频**意味着：配方以其**原始 EU/t (8192) 和原始耗时**运行，不加速。

来源：[`OverclockCalculator.calculateOverclock()` — 最终计算](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/util/OverclockCalculator.java#L406)

```java
calculatedConsumption = (long) Math.ceil(recipePower * GTUtility.powInt(eutIncreasePerOC, overclocks));
// = 8192 × 4^0 = 8192
duration /= GTUtility.powInt(durationDecreasePerOC, regularOverclocks);
// duration 不变
calculatedDuration = (int) Math.max(duration, 1);
```

最终：机器消耗 **8192 EU/t（即 4A EV）**，配方以原始速度运行。

---

## 总结：升压的本质

```
                EV 能源仓 (2048V) × 4A
                        │
                        ▼
        ┌───────────────────────────────────────┐
        │  availableEUt = 2048 × 4 = 8192       │
        │  machineVoltage = 2048 (EV)           │
        │  machineAmperage = 4                   │
        │  amperageOC = true                     │
        └───────────────┬───────────────────────┘
                        │
                        ▼
        ┌───────────────────────────────────────┐
        │  ① 功率检查: 8192 ≥ 8192 ✓            │
        │     (总功率满足 IV 配方需求)            │
        │                                        │
        │  ② 跨级检查: 8192 ≤ 2048 × 4^1 ✓      │
        │     (maxTierSkips=1 允许高 1 级)       │
        │                                        │
        │  ③ 超频: log4(8192/8192) = 0           │
        │     (功率刚好够，无超频)                │
        └───────────────────────────────────────┘
                        │
                        ▼
        消耗 8192 EU/t (4A EV)，原始速度运行 IV 配方
```

**"升压"不是真正的电压升高，而是：**

1. **`maxTierSkips = 1`** — 允许配方 EU/t 达到机器电压的 4 倍（高 1 个电压等级），这是"跨级运行"的许可
2. **`amperageOC = true`** — 在超频计算中，机器功率 = 电压 × **总安培数**，多个能源仓的安培数累加
3. **功率检查**用的是 `availableEUt = 电压 × 总安培`，即总功率

| 机制 | 作用 | 在本例中 |
|------|------|---------|
| `maxTierSkips = 1` | 允许配方 EU/t ≤ 机器电压 × 4 | 8192 ≤ 2048 × 4 = 8192 ✓ |
| `amperageOC = true` | 机器功率 = 电压 × 总安培 | 2048 × 4 = 8192 |
| `availableEUt` | 总功率 = 电压 × 安培 | 2048 × 4 = 8192 ≥ 8192 ✓ |

所以本质是：**用 4A 的 EV 电流凑出 IV 级别的总功率，配合跨级检查机制允许运行高 1 级的配方**。如果只有 `maxTierSkips = 0`（不允许跨级），即使功率够也无法运行 IV 配方。
