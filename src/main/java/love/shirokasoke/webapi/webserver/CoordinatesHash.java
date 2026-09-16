package love.shirokasoke.webapi.webserver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link RouteHandler.coordinates} 的 hashCode 候选实现集合
 * <p>
 * 生产路径仍使用 {@link RouteHandler.coordinates#hashCode()} 中已生效的 {@link #rotateAdd}；
 * 本类只把调研过的各类方案原样保留，便于后续按碰撞质量 / 性能取舍替换。
 * <p>
 * 测量口径：JDK 25；域取 x/z ∈ ±100w、y ∈ [-64,320]、dimension ∈ [-1, MAX]；
 * 「密集网格」22,869 点、「边界全扫」147,840 点（x/z 取 ±100w 附近边界）、
 * 「dim 扫描」12,286 点（dimension -1~4094 × 3 基点）、20 万随机样本的
 * 32 位哈希生日碰撞期望约 4.7 次；「结构族」由两字段平移穷举扫描（1,417 万组）判定。
 *
 * <pre>
 * 方案           密集网格   边界全扫   dim扫描  结构族                        雪崩   耗时
 * packShift         7854     69920    11520  Δdim=512          100%      3.45   0.20ns
 * packLongFold         0     33928        0  Δz=1024&amp;Δdim=4096   52%      0.70   0.22ns
 * packLongMul          0         5        0  无                            9.94   1.36ns
 * rotateAdd            0         0        0  无（当前生效）                16.05   0.31ns
 * rotateAddLean        0         0        0  无                           13.62   0.25ns
 * rotateAddRaw         0         0        0  无                            9.92   0.24ns
 * rotateOnly       19488      8256        0  Δx=2048&amp;Δz=-512   100%     14.50   0.23ns
 * packYz3              0     24640        0  无（dim 打包别名见注释）      14.46   0.27ns
 * packYzLite        6897     24640        0  Δz=-2&amp;Δdim=512     100%     12.96   0.22ns
 * xorMul            7530     39456        0  有（符号对称，见注释）        16.04   1.20ns
 * chain31              0     84960        0  有（31³ 权重，见注释）        16.38   0.23ns
 * </pre>
 *
 * 结论：{@link #rotateAdd} 是唯一同时满足「域内零碰撞 + 无结构族 + 雪崩 16.0」且耗时与
 * 纯位打包同量级的方案；{@link #packLongMul} 结构同样干净但依赖 long 运算，慢约 4 倍。
 */
public final class CoordinatesHash {

    private CoordinatesHash() {}

    /** 候选哈希的统一签名，便于批量对比。 */
    public interface Hash {

        int hash(int posX, int posY, int posZ, int dimension);
    }

    /** 全部候选（保持插入顺序），生产代码不引用，仅用于后续选择 / 对比测试。 */
    public static final Map<String, Hash> CANDIDATES;

    static {
        Map<String, Hash> m = new LinkedHashMap<>();
        m.put("packShift", CoordinatesHash::packShift);
        m.put("packLongFold", CoordinatesHash::packLongFold);
        m.put("packLongMul", CoordinatesHash::packLongMul);
        m.put("rotateAdd", CoordinatesHash::rotateAdd);
        m.put("rotateAddLean", CoordinatesHash::rotateAddLean);
        m.put("rotateAddRaw", CoordinatesHash::rotateAddRaw);
        m.put("rotateOnly", CoordinatesHash::rotateOnly);
        m.put("packYz3", CoordinatesHash::packYz3);
        m.put("packYzLite", CoordinatesHash::packYzLite);
        m.put("xorMul", CoordinatesHash::xorMul);
        m.put("chain31", CoordinatesHash::chain31);
        CANDIDATES = Collections.unmodifiableMap(m);
    }

    // region 位通道打包族

    /**
     * 旧实现：{@code <<} 位通道打包 + 两轮右移折叠。
     * <p>
     * 通道全重叠：y 溢出到 x、z 高 16 位溢出到 dimension、dimension 只保留低 8 位，
     * 存在大量可直接构造的碰撞（仅作对照）。
     */
    public static int packShift(int posX, int posY, int posZ, int dimension) {
        int h = posX + (posY << 8) + (posZ << 16) + (dimension << 24);
        h ^= h >>> 15;
        h ^= h >>> 7;
        return h;
    }

    /**
     * 64 位通道打包 + 高低位折叠（无乘法）：x/z 各 21 位（±1048576）、y 9 位（-64~447）、
     * dimension 13 位（-4096~4095）。
     * <p>
     * 单字段无碰，但折叠是线性运算：Δz=1024 配 Δdim=4096 时约半数基点同哈希，
     * 边界全域 33,928 次碰撞，且雪崩仅 0.70（翻 1 bit 输出平均只变 0.7 bit）。
     */
    public static int packLongFold(int posX, int posY, int posZ, int dimension) {
        long h = (posX & 0x1FFFFFL) | ((posZ & 0x1FFFFFL) << 21)
            | ((long) (posY + 64) << 42)
            | ((dimension & 0x1FFFL) << 51);
        return (int) (h ^ (h >>> 32));
    }

    /**
     * 64 位通道打包 + 一次乘法折叠：x/z 各 21 位（±1048576）、y 9 位（-64~447），
     * dimension 保留全 32 位。
     * <p>
     * 结构同样干净（穷举两字段平移 0 结构族），但 1.36ns/次，是 {@link #rotateAdd} 的 4 倍多，
     * 且雪崩只有 9.94；域外（|x| 超 ±100w）会因掩码折叠而退化。
     */
    public static int packLongMul(int posX, int posY, int posZ, int dimension) {
        long h = (posX & 0x1FFFFFL) | ((posZ & 0x1FFFFFL) << 21) | ((long) (posY + 64) << 42);
        h = (h + (long) dimension * 0xBF58476D1CE4E5B9L) * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    // endregion

    // region 旋转加法族

    /**
     * 当前生产实现：4 段「乘法 + 加法 + 循环移位」+ fmix 收尾，每步可逆。
     * <p>
     * 域内零碰撞、无结构族、雪崩 16.05，0.31ns/次。
     */
    public static int rotateAdd(int posX, int posY, int posZ, int dimension) {
        int h = 0x9E3779B1;
        h = Integer.rotateLeft(h + posX * 0x85EBCA6B, 13);
        h = Integer.rotateLeft(h + posY * 0xC2B2AE35, 17);
        h = Integer.rotateLeft(h + posZ * 0x27D4EB2F, 19);
        h = Integer.rotateLeft(h + dimension * 0x9E3779B1, 23);
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }

    /**
     * 同上，收尾只保留一次乘法（省 2 条指令）。
     * <p>
     * 域内同样零碰撞、无结构族，雪崩 13.62（略弱），0.25ns/次。
     */
    public static int rotateAddLean(int posX, int posY, int posZ, int dimension) {
        int h = 0x9E3779B1;
        h = Integer.rotateLeft(h + posX * 0x85EBCA6B, 13);
        h = Integer.rotateLeft(h + posY * 0xC2B2AE35, 17);
        h = Integer.rotateLeft(h + posZ * 0x27D4EB2F, 19);
        h = Integer.rotateLeft(h + dimension * 0x9E3779B1, 23);
        return h * 0x85EBCA6B;
    }

    /**
     * 同上，完全去掉收尾。
     * <p>
     * 域内同样零碰撞、无结构族，但低位扩散最差（雪崩 9.92），只有依赖
     * {@code HashMap}/{@code ConcurrentHashMap} 自带的 {@code h ^ (h >>> 16)} spread 才可用；0.24ns/次。
     */
    public static int rotateAddRaw(int posX, int posY, int posZ, int dimension) {
        int h = 0x9E3779B1;
        h = Integer.rotateLeft(h + posX * 0x85EBCA6B, 13);
        h = Integer.rotateLeft(h + posY * 0xC2B2AE35, 17);
        h = Integer.rotateLeft(h + posZ * 0x27D4EB2F, 19);
        return Integer.rotateLeft(h + dimension * 0x9E3779B1, 23);
    }

    /**
     * 每字段只做加法 + 循环移位（省掉字段乘法），末尾一次乘法混淆。
     * <p>
     * 字段保持 2 的幂结构，旋转之间的关系可精确抵消，存在 100% 命中的结构性碰撞族，
     * 例如 Δx=2048 且 Δz=-512（仅作对照）。
     */
    public static int rotateOnly(int posX, int posY, int posZ, int dimension) {
        int h = 0x9E3779B1;
        h = Integer.rotateLeft(h + posX, 13);
        h = Integer.rotateLeft(h + posY, 17);
        h = Integer.rotateLeft(h + posZ, 19);
        h = Integer.rotateLeft(h + dimension, 23);
        h *= 0x85EBCA6B;
        h ^= h >>> 16;
        return h;
    }

    /**
     * y 与 dimension 先按位打包成单字段（y ∈ [-64,447]、dimension 全 32 位），共 3 段混合。
     * <p>
     * {@code dimension << 9} 在 int 内不自洽：-1 与 Integer.MAX_VALUE 移位后同为 0xFFFFFE00，
     * 于是 (dim=-1, y) 与 (dim=MAX, y) 恒等——边界全域的 24,640 次碰撞全部来自这一别名。
     */
    public static int packYz3(int posX, int posY, int posZ, int dimension) {
        int h = 0x9E3779B1;
        h = Integer.rotateLeft(h + posX * 0x85EBCA6B, 13);
        h = Integer.rotateLeft(h + posZ * 0xC2B2AE35, 17);
        h = Integer.rotateLeft(h + ((dimension << 9) | (posY + 64)) * 0x27D4EB2F, 19);
        h *= 0x85EBCA6B;
        h ^= h >>> 16;
        return h;
    }

    /**
     * {@link #packYz3} 的省乘法版本（x/z 直接相加），结构性碰撞族更明显，
     * 例如 Δz=-2 且 Δdimension=512（仅作对照）。
     */
    public static int packYzLite(int posX, int posY, int posZ, int dimension) {
        int h = 0x9E3779B1;
        h = Integer.rotateLeft(h + posX, 13);
        h = Integer.rotateLeft(h + posZ, 17);
        h = Integer.rotateLeft(h + ((dimension << 9) | (posY + 64)), 19);
        h *= 0x85EBCA6B;
        h ^= h >>> 16;
        return h;
    }

    // endregion

    // region 异或 / 乘法组合族

    /**
     * 字段乘法结果用 XOR 串联。
     * <p>
     * 奇数倍数的异或存在符号对称恒等式（-C ^ b == C ^ ~(b+1)），
     * 域内即可构造大量碰撞，例如 (-1,126) 与 (1,-128)（仅作对照）。
     */
    public static int xorMul(int posX, int posY, int posZ, int dimension) {
        int h = posX * 0x9E3779B1;
        h = (h ^ posY) * 0x85EBCA6B;
        h = (h ^ posZ) * 0xC2B2AE35;
        h ^= dimension * 0x27D4EB2F;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return h;
    }

    /**
     * Objects.hash 风格 31 链 + fmix 收尾。
     * <p>
     * {@code 31^3 == 31 * 31^2}，故 (x+1, y-31) 与 (x, y) 恒等碰撞（仅作对照）。
     */
    public static int chain31(int posX, int posY, int posZ, int dimension) {
        int h = posX;
        h = 31 * h + posY;
        h = 31 * h + posZ;
        h = 31 * h + dimension;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }

    // endregion
}
