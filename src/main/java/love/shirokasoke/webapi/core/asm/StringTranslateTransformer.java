package love.shirokasoke.webapi.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * 移除 {@link net.minecraft.util.StringTranslate} 全部方法的 {@code ACC_SYNCHRONIZED} 标记
 */
public class StringTranslateTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("WebAPI-ASM");

    private static final String TARGET_CLASS = "net.minecraft.util.StringTranslate";

    @Override
    public byte[] transform(final String name, final String transformedName, final byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        final String className = transformedName != null ? transformedName : name;
        if (!TARGET_CLASS.equals(className)) {
            return basicClass;
        }

        final ClassNode cn = new ClassNode();
        new ClassReader(basicClass).accept(cn, 0);

        final int removed = stripSynchronized(cn);
        if (removed == 0) {
            return basicClass;
        }

        // 没有改动任何指令，栈帧/操作数栈无需重算
        final ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);

        LOG.info("StringTranslate rewritten: ACC_SYNCHRONIZED removed (x{})", removed);

        return cw.toByteArray();
    }

    /**
     * 清除所有方法的 {@code ACC_SYNCHRONIZED} 标记
     *
     * @return 剥离个数
     */
    private static int stripSynchronized(final ClassNode cn) {
        int removed = 0;

        for (final MethodNode mn : cn.methods) {
            if ((mn.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
                mn.access &= ~Opcodes.ACC_SYNCHRONIZED;
                removed++;
            }
        }

        return removed;
    }
}
