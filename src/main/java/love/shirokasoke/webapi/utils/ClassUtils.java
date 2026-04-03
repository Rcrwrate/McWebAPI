package love.shirokasoke.webapi.utils;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.Constant;

public class ClassUtils {

    private static final ObjectMapper mapper = Constant.mapper;

    public static ObjectNode getClassInfo(Object obj, ObjectNode dataNode, String keyString) {
        if (Config.classDump) {
            dataNode.set(keyString, getClassInfo(obj));
        }
        return dataNode;
    }

    public static ObjectNode getClassInfo(Object obj, ObjectNode dataNode) {
        return getClassInfo(obj, dataNode, "class");
    }

    /**
     * 获取对象的类信息，包括继承关系和实现的接口
     *
     * @deprecated
     * @param obj 输入对象
     * @return ObjectNode 包含 extends 和 implements 两个数组
     */
    @Deprecated
    public static ObjectNode getClassInfo(Object obj) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode extendsArray = mapper.createArrayNode();
        ArrayNode implementsArray = mapper.createArrayNode();

        if (obj == null) {
            result.set("extends", extendsArray);
            result.set("implements", implementsArray);
            result.put("location", "null");
            return result;
        }

        Class<?> clazz = obj.getClass();
        result.put(
            "package",
            clazz.getPackage() != null ? clazz.getPackage()
                .getName() : "null");
        result.put("location", getClassLocation(clazz));

        // 1. 添加继承链（extends）
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            extendsArray.add(currentClass.getName());
            currentClass = currentClass.getSuperclass();
        }

        // 2. 添加所有实现的接口（implements）
        List<String> interfaceNames = new ArrayList<>();
        extractAllInterfaces(clazz, interfaceNames);
        for (String interfaceName : interfaceNames) {
            implementsArray.add(interfaceName);
        }

        result.set("extends", extendsArray);
        result.set("implements", implementsArray);
        return result;
    }

    /**
     * 获取对象涉及的所有类名（包括自身类、所有父类和所有实现的接口）
     *
     * @param obj 输入对象
     * @return 类名列表，按继承层次排序（从子类到父类，然后是接口）
     * @deprecated 建议使用 getClassInfo() 代替
     */
    @Deprecated
    public static List<String> getAllClassNames(Object obj) {
        List<String> classNames = new ArrayList<>();

        if (obj == null) {
            return classNames;
        }

        Class<?> clazz = obj.getClass();

        // 1. 添加对象本身的类名
        addClassAndInterfaces(clazz, classNames);

        // 2. 递归添加所有父类
        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            addClassAndInterfaces(superClass, classNames);
            superClass = superClass.getSuperclass();
        }

        return classNames;
    }

    /**
     * 添加类名及其所有接口名
     */
    private static void addClassAndInterfaces(Class<?> clazz, List<String> classNames) {
        String className = clazz.getName();
        if (!classNames.contains(className)) {
            classNames.add(className);
        }

        // 添加所有直接实现的接口
        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> iface : interfaces) {
            String interfaceName = iface.getName();
            if (!classNames.contains(interfaceName)) {
                classNames.add(interfaceName);
            }
            // 递归添加接口的父接口
            addInterfaceParents(iface, classNames);
        }
    }

    /**
     * 递归添加接口的所有父接口
     */
    private static void addInterfaceParents(Class<?> iface, List<String> classNames) {
        Class<?>[] parentInterfaces = iface.getInterfaces();
        for (Class<?> parentIface : parentInterfaces) {
            String parentInterfaceName = parentIface.getName();
            if (!classNames.contains(parentInterfaceName)) {
                classNames.add(parentInterfaceName);
                addInterfaceParents(parentIface, classNames);
            }
        }
    }

    /**
     * 获取对象涉及的所有类名（简化版，只包括类本身和直接父类链）
     *
     * @param obj 输入对象
     * @return 类名列表，从子类到父类
     */
    public static List<String> getClassHierarchy(Object obj) {
        List<String> classNames = new ArrayList<>();

        if (obj == null) {
            return classNames;
        }

        Class<?> clazz = obj.getClass();

        while (clazz != null && clazz != Object.class) {
            classNames.add(clazz.getName());
            clazz = clazz.getSuperclass();
        }

        return classNames;
    }

    /**
     * 获取对象实现的所有接口名
     *
     * @param obj 输入对象
     * @return 接口名列表
     */
    public static List<String> getAllInterfaces(Object obj) {
        List<String> interfaceNames = new ArrayList<>();

        if (obj == null) {
            return interfaceNames;
        }

        Class<?> clazz = obj.getClass();
        extractAllInterfaces(clazz, interfaceNames);

        return interfaceNames;
    }

    /**
     * 提取类及其父类的所有接口
     */
    private static void extractAllInterfaces(Class<?> clazz, List<String> interfaceNames) {
        // 提取当前类的接口
        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> iface : interfaces) {
            String interfaceName = iface.getName();
            if (!interfaceNames.contains(interfaceName)) {
                interfaceNames.add(interfaceName);
                // 递归提取接口的父接口
                extractAllInterfaces(iface, interfaceNames);
            }
        }

        // 递归提取父类的接口
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            extractAllInterfaces(superClass, interfaceNames);
        }
    }

    /**
     * 获取类的代码位置
     *
     * @param clazz 类对象
     * @return 类的位置（jar包路径或文件路径）
     */
    public static String getClassLocation(Class<?> clazz) {
        if (clazz == null) {
            return "null";
        }

        // 方法1：通过类加载器获取资源
        String classFileName = clazz.getName()
            .replace('.', '/') + ".class";
        java.net.URL resource = clazz.getClassLoader()
            .getResource(classFileName);

        if (resource != null) {
            String location = resource.toString();
            // 简化路径，只显示jar文件名
            if (location.contains(".jar")) {
                int jarIndex = location.lastIndexOf(".jar");
                int startIndex = location.lastIndexOf('/', jarIndex);
                if (startIndex > 0) {
                    return location.substring(startIndex + 1);
                }
            }
            return location;
        }

        // 方法2：通过ProtectionDomain获取
        java.security.ProtectionDomain pd = clazz.getProtectionDomain();
        if (pd != null && pd.getCodeSource() != null) {
            return pd.getCodeSource()
                .getLocation()
                .toString();
        }

        return "Unknown";
    }

    /**
     * 获取实例的类位置（便捷方法）
     *
     * @param obj 实例对象
     * @return 类的位置
     */
    public static String getClassLocation(Object obj) {
        if (obj == null) {
            return "null";
        }
        return getClassLocation(obj.getClass());
    }
}
