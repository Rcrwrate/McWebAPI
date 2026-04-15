package love.shirokasoke.webapi.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import love.shirokasoke.webapi.MyMod;

public class log {

    public static void e(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        MyMod.LOG.error(sw);
    }

    public static void debugFields(Object obj) {
        if (obj == null) {
            MyMod.LOG.info("Object is null");
            return;
        }
        Field[] fields = obj.getClass()
            .getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                MyMod.LOG.info(
                    "字段名称: " + field.getName()
                        + " 字段修饰符: "
                        + field.getModifiers()
                        + " 字段值: "
                        + field.get(obj)
                        + " 字段类型："
                        + field.getType()
                            .getName());
            } catch (IllegalAccessException e) {
                MyMod.LOG.error("访问字段 " + field.getName() + " 失败: " + e.getMessage());
            }
        }
    }

    public static void debugMethods(Object obj) {
        if (obj == null) {
            MyMod.LOG.info("Object is null");
            return;
        }
        Method[] methods = obj.getClass()
            .getDeclaredMethods();
        for (Method method : methods) {
            method.setAccessible(true);
            Parameter[] params = method.getParameters();
            String[] paramTypes = Arrays.stream(params)
                .map(
                    p -> p.getType()
                        .getSimpleName())
                .toArray(String[]::new);
            MyMod.LOG.info(
                "方法名称: " + method.getName()
                    + " 修饰符: "
                    + method.getModifiers()
                    + " 返回值: "
                    + method.getReturnType()
                        .getSimpleName()
                    + " 参数: "
                    + Arrays.toString(paramTypes));
        }
    }
}
