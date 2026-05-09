用户问的是：有没有直接通过 ME 接口的坐标获取仅它内部样板的方法，而不是遍历整个 AE 网络。

当前 AEMEHandler 是通过 `grid.getMachines(clazz)` 遍历整个网络的所有机器。用户想要的是：给定一个坐标（比如某个 ME 接口的位置），只获取那个接口里的样板。

我需要看看：
1. ME 接口对应的 TileEntity 是什么类，它是否实现了 IInterfaceViewable
2. 有没有直接通过坐标获取 TileEntity，然后读取样板的方法
3. AE2 中 ME 接口的类名

先看看 AE2 中 ME 接口相关的类。
用户想知道有没有直接通过 ME 接口坐标获取它内部样板的方法。我需要看看 TileInterface 和 PartInterface 的代码，了解它们是否实现了 IInterfaceViewable，以及样板是如何存储的。

让我先读取这两个文件。
-
用户问：有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

现在我已经看了 TileInterface 和 PartInterface 的代码。它们都委托给了 `DualityInterface`。我需要看看 `DualityInterface` 里有没有获取样板的方法，以及 `IInterfaceViewable` 接口里 `getPatterns()` 的来源。

`TileInterface` 没有直接实现 `IInterfaceViewable`，但 `PartInterface` 也没有。让我看看 `IInterfaceViewable` 接口，以及哪些类实现了它。实际上，从前面的搜索看，`InterfaceTerminalRegistry` 管理的是 `IInterfaceViewable` 的实现类。

让我搜索 `IInterfaceViewable` 的实现类，以及 `DualityInterface` 里关于 patterns 的方法。
-
用户想知道有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

从代码来看：
1. `TileInterface` 和 `PartInterface` 都没有直接实现 `IInterfaceViewable`
2. 它们都通过 `DualityInterface` 来工作
3. `IInterfaceViewable` 是接口终端使用的接口

我需要看看：
- `IInterfaceViewable` 接口定义
- `DualityInterface` 中是否有获取样板的方法
- 谁实现了 `IInterfaceViewable`

`InterfaceTerminalRegistry` 注册了某些类。让我看看哪些类被注册了。
-
用户想知道有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

从代码来看：
1. `IInterfaceViewable` 定义了 `getPatterns()` 方法返回 `IInventory`
2. `TileInterface` 和 `PartInterface` 都没有直接实现 `IInterfaceViewable`
3. 但 `DualityInterface` 可能有相关方法

我需要看看谁在 `InterfaceTerminalRegistry` 里注册了，以及 `DualityInterface` 里是否有 `getPatterns()` 方法。
-
用户想知道有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

从代码来看：
1. `DualityInterface` 有 `getPatterns()` 方法，返回 `AppEngInternalInventory`
2. `TileInterface` 和 `PartInterface` 都有 `getInterfaceDuality()` 返回 `DualityInterface`
3. `TileInterface` 实现了 `IInterfaceHost`，`PartInterface` 也实现了 `IInterfaceHost`

但问题是：`TileInterface` 和 `PartInterface` 都没有直接实现 `IInterfaceViewable`。那谁在 `InterfaceTerminalRegistry` 里注册了？

让我搜索一下 `InterfaceTerminalRegistry` 的调用者，看看谁调用了 `register`。
-
现在我明白了。`InterfaceTerminalRegistry` 在构造时就把 `TileInterface.class`、`PartInterface.class`、`PartP2PInterface.class` 注册了。但问题是，`TileInterface` 和 `PartInterface` 并没有直接实现 `IInterfaceViewable` 接口。

不过，Java 的类型擦除和反射机制下，`InterfaceTerminalRegistry` 把它们当作 `Class<? extends IInterfaceViewable>` 来处理。在运行时 `grid.getMachines(clazz)` 时，AE2 内部可能会做类型转换。

等等，让我再看看 `grid.getMachines` 的返回类型。`AEMEHandler` 里写的是：
```java
for (IGridNode node : grid.getMachines(clazz)) {
    IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
```

这说明 `node.getMachine()` 返回的对象可以被强制转换为 `IInterfaceViewable`。但 `TileInterface` 和 `PartInterface` 的类声明里并没有 `implements IInterfaceViewable`。

这说明 `IInterfaceViewable` 可能是通过 Mixin 或其他方式动态添加的？不，在 1.7.10 的 Forge 环境下不太可能有 Mixin。

让我再检查一下 `TileInterface` 的继承链。`TileInterface extends AENetworkInvTile implements IGridTickable, ITileStorageMonitorable, IStorageMonitorable, IInventoryDestination, IInterfaceHost, IPriorityHost, IPowerChannelState`

`AENetworkInvTile` 呢？让我看看它是否实现了 `IInterfaceViewable`。

不对，我之前的搜索结果是 `implements IInterfaceViewable` 没有任何匹配。但 `grid.getMachines` 返回的节点可以用 `(IInterfaceViewable) node.getMachine()` 转换...

等一下，让我看看 `IGridNode.getMachine()` 的返回类型，以及 `IGrid.getMachines` 的实现。

实际上，可能的情况是：AE2 的 `IGridNode` 在注册时会把 `IGridHost` 的实例关联起来。`grid.getMachines(clazz)` 会返回所有 `IGridHost` 实例中属于 `clazz` 类型的节点。然后通过 `node.getMachine()` 获取原始对象。如果 `TileInterface` 真的实现了 `IInterfaceViewable`（可能只是我没搜到，或者是在父类里实现的），那就可以转换。

让我搜索 `AENetworkInvTile` 或 `AENetworkTile` 是否实现了 `IInterfaceViewable`。
-
用户想知道有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

我已经看到：
1. `InterfaceTerminalRegistry` 注册了 `TileInterface.class`、`PartInterface.class`、`PartP2PInterface.class`
2. 但这些类都没有在类声明中写 `implements IInterfaceViewable`
3. 但在 `AEMEHandler` 中确实做了 `(IInterfaceViewable) node.getMachine()` 的强制转换

这说明要么：
a) 这些类通过 Mixin/ASM 动态实现了接口（在 1.7.10 不太可能）
b) `IInterfaceViewable` 是某个父接口，这些类间接实现了它
c) 代码里其实有 `instanceof` 检查或者异常处理

让我看看 `AENetworkTile` 的代码，以及 `IGridNode.getMachine()` 的定义。

不过，用户的核心问题是：有没有直接通过坐标获取 ME 接口内部样板的方法？

答案是：**有**，你可以：
1. 通过 `world.getTileEntity(x, y, z)` 获取 `TileInterface`（如果是方块形式的接口）
2. 如果是线缆上的接口部件（PartInterface），需要通过 FMP 的 `TileMultipart` 获取

对于 `TileInterface`：
```java
TileInterface tile = (TileInterface) world.getTileEntity(x, y, z);
IInventory patterns = tile.getInterfaceDuality().getPatterns();
```

对于 `PartInterface`（线缆上的接口部件）：
需要通过 `CableBusPart` → `CableBusContainer` → `getPart(ForgeDirection)` 获取 `IPart`，然后转换为 `PartInterface`。

不过更简单的是：既然 `TileInterface` 没有直接实现 `IInterfaceViewable`，那 `AEMEHandler` 里的强制转换 `(IInterfaceViewable) node.getMachine()` 可能会抛 `ClassCastException`。

让我看看 `IGridNode.getMachine()` 的返回类型是什么。
-
用户想知道有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

从代码来看：
1. `TileInterface` 没有直接实现 `IInterfaceViewable`
2. `AENetworkTile` 也没有实现 `IInterfaceViewable`
3. 但 `InterfaceTerminalRegistry` 注册了 `TileInterface.class`

这说明 `IInterfaceViewable` 可能是后来通过某些方式动态添加的，或者 `grid.getMachines` 实际上返回的是所有实现了 `IGridHost` 的节点，然后代码里做了类型转换。

不过，用户的核心问题是：如何通过坐标直接获取某个 ME 接口的样板？

对于方块形式的 ME 接口（`TileInterface`），可以直接：
```java
TileEntity te = world.getTileEntity(x, y, z);
if (te instanceof TileInterface) {
    TileInterface iface = (TileInterface) te;
    IInventory patterns = iface.getInterfaceDuality().getPatterns();
}
```

对于部件形式的 ME 接口（`PartInterface`，贴在线缆面上的），需要通过 FMP 获取 `CableBusPart`，然后获取 `CableBusContainer`，再获取对应面的 `IPart`，转换为 `PartInterface`。

但更简单的是：`PartInterface` 实现了 `ISidedInventory`（因为它实现了 `IInventory` 的方法），但获取它需要通过 FMP。

让我看看 `IGridNode.getMachine()` 的返回值。
-
现在我已经搞清楚了。`IGridNode.getMachine()` 返回的是 `IGridHost`，而 `IInterfaceViewable extends IGridHost`。

但 `TileInterface` 和 `PartInterface` 都没有在类声明中写 `implements IInterfaceViewable`。那 `AEMEHandler` 里的强制转换 `(IInterfaceViewable) node.getMachine()` 是怎么工作的？

让我看看 `grid.getMachines` 的实现。它可能内部做了 `instanceof` 过滤。

不过，用户的核心问题是：有没有直接通过 ME 接口坐标获取仅它内部的样板的方法？

答案是肯定的：

**对于方块形式的 ME 接口（TileInterface）**：
```java
TileEntity te = world.getTileEntity(x, y, z);
if (te instanceof TileInterface) {
    TileInterface iface = (TileInterface) te;
    IInventory patterns = iface.getInterfaceDuality().getPatterns();
    // patterns 就是样板物品栏
}
```

**对于部件形式的 ME 接口（PartInterface，贴在线缆上）**：
需要通过 FMP 获取：
```java
TileMultipart mp = TileMultipart.getOrConvertTile(world, coord);
for (TMultiPart part : mp.partList()) {
    if (part instanceof PartInterface) {
        PartInterface iface = (PartInterface) part;
        IInventory patterns = iface.getInterfaceDuality().getPatterns();
    }
}
```

不过，用户可能更想知道：如果我知道坐标，能不能不遍历整个网络，只读这一个接口的样板？

可以的。但需要注意的是，ME 接口有两种形态：
1. **方块形态**（独立的 `TileInterface`）
2. **部件形态**（`PartInterface` 贴在 `CableBusPart` 上）

要同时处理这两种情况，你需要：
1. 先尝试 `world.getTileEntity(x, y, z)` 看是不是 `TileInterface`
2. 如果不是，尝试通过 FMP 获取 `TileMultipart`，然后遍历部件看有没有 `PartInterface`

但是！`IInterfaceViewable` 这个接口的定义里包含了 `getPatterns()`。`TileInterface` 虽然没有在类声明中实现 `IInterfaceViewable`，但可能通过某种方式实现了。让我看看 `grid.getMachines` 的实现，以及 `IInterfaceViewable` 到底是在哪里被实现的。

实际上，我在 `InterfaceTerminalRegistry` 的构造函数里看到它注册了 `TileInterface.class`，但 `TileInterface` 没有 `implements IInterfaceViewable`。这在 Java 编译时应该是通不过的...除非...

等等！让我重新看一下 `InterfaceTerminalRegistry` 的代码：
```java
supportedClasses.add(TileInterface.class);
```

而 `supportedClasses` 是 `Set<Class<? extends IInterfaceViewable>>`。如果 `TileInterface` 没有实现 `IInterfaceViewable`，这行代码在编译时就会报错！

所以 `TileInterface` 一定以某种方式实现了 `IInterfaceViewable`。让我再仔细搜索一下。

可能我之前搜 `implements IInterfaceViewable` 时漏掉了，因为可能有 `extends` 或者换行的情况。让我搜索 `IInterfaceViewable` 在 `TileInterface.java` 和 `PartInterface.java` 中的出现。
-
用户想知道有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

让我重新审视这个问题。我已经看到：
1. `InterfaceTerminalRegistry` 在构造时注册了 `TileInterface.class`
2. 但 `TileInterface` 的源代码里并没有 `implements IInterfaceViewable`
3. 而且 `InterfaceTerminalRegistry` 的 `supportedClasses` 类型是 `Set<Class<? extends IInterfaceViewable>>`

这在 Java 中如果没有实现接口是不可能编译通过的。所以有两种可能：
1. 这些类通过 Mixin/ASM 在运行时动态实现了接口（在 1.7.10 不太可能）
2. 我搜索的是 GTNH 分支的 AE2，可能 `IInterfaceViewable` 是在基类或混入中实现的

等一下，让我看看 `AENetworkInvTile` 或 `AEBaseTile` 的继承链。也许 `AENetworkInvTile` 实现了 `IInterfaceViewable`？

不，`AENetworkInvTile` 是 `AENetworkTile` 的子类...让我看看 `AENetworkInvTile`。

不对，让我换个思路。也许 `IInterfaceViewable` 是后来添加到 API 的，而 `TileInterface` 在 GTNH 版本中实际上有实现，只是我读的文件可能不是最新的？不，我已经读了 `TileInterface.java` 的内容，里面确实没有 `IInterfaceViewable`。

等等，我注意到 `InterfaceTerminalRegistry` 的 import 中有：
```java
import appeng.tile.misc.TileInterface;
import appeng.parts.misc.PartInterface;
import appeng.parts.p2p.PartP2PInterface;
```

而 `supportedClasses` 是 `Set<Class<? extends IInterfaceViewable>>`。如果 `TileInterface` 没有实现 `IInterfaceViewable`，这在 Java 编译时就会报错：`Type mismatch: cannot convert from Class<TileInterface> to Class<? extends IInterfaceViewable>`。

这怎么可能编译通过？除非...

啊！我想起来了。在 Java 中，`Class` 的泛型在运行时是擦除的。`Class<TileInterface>` 可以隐式转换到 `Class<? extends IInterfaceViewable>` 吗？不，Java 的泛型在编译时会检查，如果 `TileInterface` 没有实现 `IInterfaceViewable`，`Class<TileInterface>` 不能赋值给 `Class<? extends IInterfaceViewable>`。

除非... `TileInterface` 的某个父类实现了 `IInterfaceViewable`？让我搜一下 `AENetworkInvTile` 和 `AEBaseTile`。

或者... GTNH 分支修改了 `TileInterface` 但我的搜索结果有问题？

等等，让我看看 `AEMEHandler` 的当前代码。用户已经写了：
```java
for (IGridNode node : grid.getMachines(clazz)) {
    IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
```

如果 `node.getMachine()` 返回的不是 `IInterfaceViewable`，这段代码在运行时会抛 `ClassCastException`。但用户似乎已经确认这段代码在工作？

让我看看 `IGrid.getMachines` 的定义，以及 `grid.getMachines` 的实现。
-
用户想知道有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

让我继续查看 `IGrid.java` 和 `Grid.java` 来了解 `getMachines` 的工作原理。
-
现在我明白了。`IGrid.getMachines` 接收的参数是 `Class<? extends IGridHost>`，而不是 `Class<? extends IInterfaceViewable>`。但在 `AEMEHandler` 中，用户传的是 `InterfaceTerminalRegistry.instance().getSupportedClasses()`，它返回的是 `Set<Class<? extends IInterfaceViewable>>`。

由于 `IInterfaceViewable extends IGridHost`，所以 `Class<? extends IInterfaceViewable>` 可以赋值给 `Class<? extends IGridHost>`。这解释了为什么代码能编译通过。

但 `grid.getMachines(clazz)` 返回的是 `IMachineSet`，遍历它得到 `IGridNode`，然后 `node.getMachine()` 返回的是 `IGridHost`。由于 `TileInterface` 实现了 `IGridHost`，所以这里不会抛异常。但 `(IInterfaceViewable) node.getMachine()` 这个强制转换...

如果 `TileInterface` 没有实现 `IInterfaceViewable`，这个强制转换在运行时会抛 `ClassCastException`。除非... `TileInterface` 真的实现了 `IInterfaceViewable` 但我搜索漏掉了？

让我再仔细看看 `TileInterface.java` 的类声明：
```java
public class TileInterface extends AENetworkInvTile implements IGridTickable, ITileStorageMonitorable,
        IStorageMonitorable, IInventoryDestination, IInterfaceHost, IPriorityHost, IPowerChannelState {
```

没有 `IInterfaceViewable`。但 `InterfaceTerminalRegistry` 里注册了 `TileInterface.class`。这怎么可能？

除非... GTNH 版本的 `TileInterface` 和我的搜索文件不一样？不，我已经确认了文件路径。

等等，让我重新看看 `InterfaceTerminalRegistry` 的代码。`supportedClasses` 是 `Set<Class<? extends IInterfaceViewable>>`，但 `register` 方法的参数是 `Class<? extends IInterfaceViewable>`。

在构造函数中：
```java
supportedClasses.add(TileInterface.class);
```

Java 的泛型在编译时会检查。如果 `TileInterface` 没有实现 `IInterfaceViewable`，这行代码应该编译报错。除非...

啊！也许 `TileInterface` 是通过反射或者 Mixin 在运行时实现的？在 1.7.10 这不太可能。

或者... 也许 `IInterfaceViewable` 是在某个 patch 中才添加给 `TileInterface` 的？让我看看 `IInterfaceViewable` 是在哪里被实现的。

等等，让我看看 `IInterfaceHost` 是否继承了 `IInterfaceViewable`。
-
用户想知道有没有直接通过 ME 接口坐标获取仅它内部的样板的方法。

让我看看 `IInterfaceHost` 是否继承了 `IInterfaceViewable`。
-
现在我明白了！`IInterfaceHost extends IInterfaceViewable`！所以 `TileInterface` 实现了 `IInterfaceHost`，也就间接实现了 `IInterfaceViewable`。

这解释了为什么 `InterfaceTerminalRegistry` 可以注册 `TileInterface.class`，以及为什么 `AEMEHandler` 里的 `(IInterfaceViewable) node.getMachine()` 能正常工作。

现在回到用户的问题：有没有直接通过 ME 接口坐标获取仅它内部的样板的方法？

答案是：**有**，而且很简单。

**对于方块形式的 ME 接口（TileInterface）**：
```java
TileEntity te = world.getTileEntity(x, y, z);
if (te instanceof IInterfaceHost) {
    IInterfaceHost host = (IInterfaceHost) te;
    IInventory patterns = host.getPatterns();
    // patterns 就是样板物品栏
}
```

**对于部件形式的 ME 接口（PartInterface，贴在线缆上）**：
需要通过 FMP 获取：
```java
TileMultipart mp = TileMultipart.getOrConvertTile(world, coord);
for (TMultiPart part : mp.partList()) {
    if (part instanceof IInterfaceHost) {
        IInterfaceHost host = (IInterfaceHost) part;
        IInventory patterns = host.getPatterns();
    }
}
```

或者更通用的方式：既然 `IInterfaceHost` 继承了 `IInterfaceViewable`，而 `IInterfaceViewable` 有 `getPatterns()` 方法，那对于任何实现了 `IInterfaceHost` 的 TileEntity 或 Part，都可以直接调用 `getPatterns()`。

用户想要的可能是：给定一个坐标，如何只获取这个坐标上的 ME 接口的样板，而不是遍历整个网络。

答案就是：
1. 通过坐标获取 `TileEntity`
2. 判断是否是 `IInterfaceHost`
3. 直接调用 `getPatterns()`

如果是部件形式（FMP），需要通过 `TileMultipart` 获取。

我应该给出一个简洁、准确的回答。
找到了。`IInterfaceHost extends IInterfaceViewable`，而 `TileInterface` 和 `PartInterface` 都实现了 `IInterfaceHost`。所以你可以直接通过坐标获取，不需要遍历网络。

**方块形式的 ME 接口**（独立的 `TileInterface`）：
```java
TileEntity te = world.getTileEntity(x, y, z);
if (te instanceof IInterfaceHost) {
    IInterfaceHost host = (IInterfaceHost) te;
    IInventory patterns = host.getPatterns();
    // patterns 里就是该接口的样板
}
```

**部件形式的 ME 接口**（`PartInterface` 贴在线缆上）：
```java
TileMultipart mp = TileMultipart.getOrConvertTile(world, new BlockCoord(x, y, z));
if (mp != null) {
    for (TMultiPart part : mp.partList()) {
        if (part instanceof IInterfaceHost) {
            IInterfaceHost host = (IInterfaceHost) part;
            IInventory patterns = host.getPatterns();
        }
    }
}
```

**统一写法**（同时兼容两种形态）：
```java
TileEntity te = world.getTileEntity(x, y, z);
IInventory patterns = null;

if (te instanceof IInterfaceHost) {
    patterns = ((IInterfaceHost) te).getPatterns();
} else if (te instanceof TileMultipart) {
    for (TMultiPart part : ((TileMultipart) te).partList()) {
        if (part instanceof IInterfaceHost) {
            patterns = ((IInterfaceHost) part).getPatterns();
            break;
        }
    }
}
```

关键点：
- `IInterfaceHost.getPatterns()` 的默认实现就是 `getInterfaceDuality().getPatterns()`
- `DualityInterface.getPatterns()` 返回的是 `AppEngInternalInventory`，里面存的就是样板
- 这和接口终端（Interface Terminal）里看到的数据来源完全一致