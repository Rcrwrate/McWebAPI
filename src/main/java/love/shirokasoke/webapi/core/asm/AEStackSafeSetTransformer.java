package love.shirokasoke.webapi.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * 把 {@code appeng.util.item} 包下所有 {@code new ObjectOpenHashSet<>()} 直接改写为
 * {@link love.shirokasoke.webapi.utils.SafeObjectOpenHashSet}
 */
public class AEStackSafeSetTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("WebAPI-ASM");

    /** 只改写该包（含子包）下的类；结尾带点，避免误伤同前缀的其它包 */
    private static final String TARGET_PACKAGE = "appeng.util.item.";

    /** 被替换的 fastutil 集合（AE2 源码里的原始类型） */
    private static final String FASTUTIL_SET = "it/unimi/dsi/fastutil/objects/ObjectOpenHashSet";

    /** 替换目标 {@link love.shirokasoke.webapi.utils.SafeObjectOpenHashSet}；只以字符串出现 */
    private static final String SAFE_SET = "love/shirokasoke/webapi/utils/SafeObjectOpenHashSet";

    private static final String INIT = "<init>";
    private static final String EMPTY_DESC = "()V";

    @Override
    public byte[] transform(final String name, final String transformedName, final byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        final String className = transformedName != null ? transformedName : name;
        if (className == null || !className.startsWith(TARGET_PACKAGE)) {
            return basicClass;
        }

        final ClassNode cn = new ClassNode();
        new ClassReader(basicClass).accept(cn, 0);

        final int replaced = rewriteFreshSets(cn);
        if (replaced == 0) {
            return basicClass;
        }

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);

        LOG.info("replaced {} x new ObjectOpenHashSet<>() -> SafeObjectOpenHashSet in {}", replaced, className);

        return cw.toByteArray();
    }

    /**
     * @return 命中的 {@code new ObjectOpenHashSet<>()} 个数；0 表示类未被改动
     */
    private static int rewriteFreshSets(final ClassNode cn) {
        int replaced = 0;

        for (final MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.NEW || !(insn instanceof TypeInsnNode newInsn)) {
                    continue;
                }
                if (!FASTUTIL_SET.equals(newInsn.desc)) {
                    continue;
                }

                // 只改写 "new X; dup; invokespecial X.<init>()V" 这一种形态，三个条件缺一不可：
                // 少了 DUP/构造器校验，就可能把一个与我们无关的合法序列改坏。
                final AbstractInsnNode dupInsn = newInsn.getNext();
                if (dupInsn == null || dupInsn.getOpcode() != Opcodes.DUP) {
                    continue;
                }

                final AbstractInsnNode initInsn = dupInsn.getNext();
                if (initInsn == null || initInsn.getOpcode() != Opcodes.INVOKESPECIAL
                    || !(initInsn instanceof MethodInsnNode initMethod)) {
                    continue;
                }
                if (!INIT.equals(initMethod.name) || !EMPTY_DESC.equals(initMethod.desc)
                    || !FASTUTIL_SET.equals(initMethod.owner)) {
                    continue;
                }

                newInsn.desc = SAFE_SET;
                initMethod.owner = SAFE_SET;
                replaced++;
            }
        }

        return replaced;
    }
}
