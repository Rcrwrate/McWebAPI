package love.shirokasoke.webapi.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

import love.shirokasoke.webapi.MyMod;

public class log {

    public static void e(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        MyMod.LOG.error(sw);
    }
}
