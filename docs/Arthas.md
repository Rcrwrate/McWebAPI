# Arthas 伟大，无需多言

在watch或line命令中使用下述快速排除MC主线程的影响

```bash
--condition '#t=@java.lang.Thread@currentThread().getName(), #t != "Server thread" && #t != "Netty Server IO #1"'
```

## 调查World#getTileEntity线程不安全的情况

[McAccessor.java](../src/main/java/love/shirokasoke/webapi/utils/McAccessor.java#L102)

### [jad](https://arthas.aliyun.com/doc/jad.html)

定位line位置

-d: 导出.class到MC服务器根目录下指定文件夹

> jad net.minecraft.world.World func_147438_o -d [path]

```java
ClassLoader:
+-net.minecraft.launchwrapper.LaunchClassLoader@65e98b1c
  +-jdk.internal.loader.ClassLoaders$PlatformClassLoader@2d928643

Location:
file:/workspace/GTNH-server/minecraft_server.1.7.10.jar!/ahb.class

         public TileEntity func_147438_o(int p_147438_1_, int p_147438_2_, int p_147438_3_) {
/*2514*/     if (p_147438_2_ >= 0 && p_147438_2_ < 256) {
                 Chunk chunk;
                 TileEntity tileentity1;
                 int l;
/*2516*/         TileEntity tileentity = null;
/*2520*/         if (this.field_147481_N) {
/*2522*/             for (l = 0; l < this.field_147484_a.size(); ++l) {
                         tileentity1 = (TileEntity)this.field_147484_a.get(l);
/*2526*/                 if (tileentity1.func_145837_r() || tileentity1.field_145851_c != p_147438_1_ || tileentity1.field_145848_d != p_147438_2_ || tileentity1.field_145849_e != p_147438_3_) continue;
/*2528*/                 tileentity = tileentity1;
/*2529*/                 break;
                     }
                 }
/*2534*/         if (tileentity == null && (chunk = this.func_72964_e(p_147438_1_ >> 4, p_147438_3_ >> 4)) != null) {
/*2540*/             tileentity = chunk.func_150806_e(p_147438_1_ & 0xF, p_147438_2_, p_147438_3_ & 0xF);
                 }
/*2544*/         if (tileentity == null) {
/*2546*/             for (l = 0; l < this.field_147484_a.size(); ++l) {
                         tileentity1 = (TileEntity)this.field_147484_a.get(l);
/*2550*/                 if (tileentity1.func_145837_r() || tileentity1.field_145851_c != p_147438_1_ || tileentity1.field_145848_d != p_147438_2_ || tileentity1.field_145849_e != p_147438_3_) continue;
/*2552*/                 tileentity = tileentity1;
/*2553*/                 break;
                     }
                 }
/*2558*/         return tileentity;
             }
/*2562*/     return null;
         }
```

### [line](https://arthas.aliyun.com/doc/line.html)

定位查看tileentity是在哪个位置获取的

> line --class net.minecraft.world.World --method func_147438_o --line 2534,2544 --express '{lineNumber, params, localVarMap}' -n 2 -x 3  --condition '#t=@java.lang.Thread@currentThread().getName(), #t != "Server thread" && #t != "Netty Server IO #1"'

```bash
ts=2026-08-07 17:07:41.520; [thread=pool-9-thread-4(464) cost=0.022857ms] net.minecraft.world.World.func_147438_o(III)Lnet/minecraft/tileentity/TileEntity;:2534
result=@ArrayList[
    @Integer[2534],
    @Object[][
        @Integer[-41],
        @Integer[100],
        @Integer[50],
    ],
    @LinkedHashMap[
        @String[p_147438_1_]:@Integer[-41],
        @String[p_147438_2_]:@Integer[100],
        @String[p_147438_3_]:@Integer[50],
        @String[tileentity]:null,
    ],
]
ts=2026-08-07 17:07:41.520; [thread=pool-9-thread-4(464) cost=0.892312ms] net.minecraft.world.World.func_147438_o(III)Lnet/minecraft/tileentity/TileEntity;:2544
result=@ArrayList[
    @Integer[2544],
    @Object[][
        @Integer[-41],
        @Integer[100],
        @Integer[50],
    ],
    @LinkedHashMap[
        @String[p_147438_1_]:@Integer[-41],
        @String[p_147438_2_]:@Integer[100],
        @String[p_147438_3_]:@Integer[50],
        @String[tileentity]:@TileCraftingMonitorTile[
            dspPlay=null,
            paintedColor=@AEColor[Fluix默认色],
            calc=@CraftingCPUCalculator[appeng.me.cluster.implementations.CraftingCPUCalculator@78743753],
            lightCache=null,
            previousState=null,
            isCoreBlock=@Boolean[false],
            cluster=@CraftingCPUCluster[appeng.me.cluster.implementations.CraftingCPUCluster@9effd91],
            ACCELERATOR_SCALE_FACTOR=@Integer[4],
            gridProxy=@AENetworkProxyMultiblock[appeng.me.helpers.AENetworkProxyMultiblock@76b3c62d],
            DROP_NO_ITEMS=@ThreadLocal[java.lang.ThreadLocal@728c5c23],
            HANDLERS=@HashMap[isEmpty=false;size=23],
            ITEM_STACKS=@HashMap[isEmpty=false;size=49],
            renderFragment=@Integer[0],
            customName=null,
            forward=@ForgeDirection[SOUTH],
            up=@ForgeDirection[UP],
            field_145852_a=@Logger[net.minecraft.tileentity.TileEntity:INFO in net.minecraft.launchwrapper.LaunchClassLoader@65e98b1c],
            field_145855_i=@HashMap[isEmpty=false;size=1538],
            field_145853_j=@HashMap[isEmpty=false;size=1441],
            field_145850_b=@WorldServer[net.minecraft.world.WorldServer@475e9019],
            field_145851_c=@Integer[-41],
            field_145848_d=@Integer[100],
            field_145849_e=@Integer[50],
            field_145846_f=@Boolean[false],
            field_145847_g=@Integer[12],
            field_145854_h=null,
            __OBFID=@String[CL_00000340],
            isVanilla=@Boolean[false],
            INFINITE_EXTENT_AABB=@AxisAlignedBB[box[-Infinity, -Infinity, -Infinity -> Infinity, Infinity, Infinity]],
            LogisticsPipes$informationObject=null,
        ],
    ],
]
Command execution times exceed limit: 2, so command will exit. You can set it with -n option.
[arthas@74662]$
```

### [watch](https://arthas.aliyun.com/doc/watch.html)

用于查看方法调用情况(输入/输出/局部变量)

```bash
[arthas@74662]$ watch net.minecraft.world.World func_72964_e -bfs -x 1 -n 3
Press Q or Ctrl+C to abort.
Affect(class count: 16 , method count: 2) cost in 167 ms, listenerId: 10
method=net.minecraft.world.World.func_72964_e location=AtEnter
ts=2026-08-07 17:11:53.649; [cost=0.051544ms] result=@ArrayList[
    @Object[][isEmpty=false;size=2],
    @WorldServerMulti[net.minecraft.world.WorldServerMulti@7c243170],
    null,
]
method=net.minecraft.world.World.func_72964_e location=AtExit
ts=2026-08-07 17:11:53.649; [cost=1.719419432511458E9ms] result=@ArrayList[
    @Object[][isEmpty=false;size=2],
    @WorldServerMulti[net.minecraft.world.WorldServerMulti@7c243170],
    @Chunk[net.minecraft.world.chunk.Chunk@76dcd8],
]
method=net.minecraft.world.World.func_72964_e location=AtExit
ts=2026-08-07 17:11:53.649; [cost=1.719419432582531E9ms] result=@ArrayList[
    @Object[][isEmpty=false;size=2],
    @WorldServerMulti[net.minecraft.world.WorldServerMulti@7c243170],
    @Chunk[net.minecraft.world.chunk.Chunk@76dcd8],
]
Command execution times exceed limit: 3, so command will exit. You can set it with -n option.
[arthas@74662]$
```

调整`-x`可以访问内部变量，可以使用`| grep`快速筛选

```bash
[arthas@74662]$ watch net.minecraft.world.World func_72964_e -x 2 -n 1
method=net.minecraft.world.World.func_72964_e location=AtExit
ts=2026-08-07 17:13:40.974; [cost=1.719526757318616E9ms] result=@ArrayList[
    @Object[][
        @Integer[-6],
        @Integer[3],
    ],
    @WorldServer[
        field_147491_a=@Logger[net.minecraft.world.WorldServer:INFO in net.minecraft.launchwrapper.LaunchClassLoader@65e98b1c],
        field_73061_a=@DedicatedServer[net.minecraft.server.dedicated.DedicatedServer@4f0ed2a1],
        field_73062_L=@EntityTracker[net.minecraft.entity.EntityTracker@13f3d860],
        field_73063_M=@PlayerManager[net.minecraft.server.management.PlayerManager@37926e87],
        field_73064_N=@HashSet[isEmpty=false;size=887],
        field_73065_O=@TreeSet[isEmpty=false;size=887],
        ...
        captureBlockSnapshots=@Boolean[false],
        capturedBlockSnapshots=@ArrayList[isEmpty=true;size=0],
        s_mapStorage=@MapStorage[net.minecraft.world.storage.MapStorage@93d7efb],
        s_savehandler=@[com.kuba6000.mobsinfo.api.DummyWorld$1@28bb3235],
        cofh_recentTiles=@IdentityLinkedHashList[isEmpty=true;size=0],
        entityOptimizationIgnoreSet=null,
        lightingEngine=@LightingEngine[org.embeddedt.archaicfix.lighting.world.lighting.LightingEngine@6c666868],
        observedX=@Integer[-19],
        observedY=@Integer[13],
        observedZ=@Integer[148],
        reusableCCIP=@ChunkCoordIntPair[[0, 0]],
        hodgepodge$spawnContext=@Integer[0],
        hodgepodge$DUMMY_AABB=@AxisAlignedBB[box[0.0, 0.0, 0.0 -> 0.0, 0.0, 0.0]],
        mm$capturedDrops=null,
    ],
    @Chunk[
        field_150817_t=@Logger[net.minecraft.world.chunk.Chunk:INFO in net.minecraft.launchwrapper.LaunchClassLoader@65e98b1c],
        field_76640_a=@Boolean[true],
        field_76652_q=@ExtendedBlockStorage[][isEmpty=false;size=16],
        ...
        lightingEngine=@LightingEngine[org.embeddedt.archaicfix.lighting.world.lighting.LightingEngine@6c666868],
        SET_BLOCK_STATE_VANILLA=@String[func_150807_a(IIILnet/minecraft/block/Block;I)Z],
        postea_GTNH$posteaID=@Long[350613628],
    ],
]
Command execution times exceed limit: 1, so command will exit. You can set it with -n option.
[arthas@74662]$
```

## 访问实例

访问MC合成表

```bash
vmtool --action getInstances --className net.minecraft.item.crafting.CraftingManager --express 'instances[0]["field_77597_b"]'
```