package love.shirokasoke.webapi.server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import love.shirokasoke.webapi.Config;

public class Auth {

    private static Map<String, String[]> cache = new HashMap<>();

    public static void setup(String[] set) {
        cache.clear();
        for (String item : set) {
            String[] parts = item.split("\\|");
            if (parts.length >= 2) {
                String url = parts[0];
                String[] methods = Arrays.copyOfRange(parts, 1, parts.length);
                cache.put(url, methods);
            }
        }
    }

    /**
     * 验证请求是否通过认证
     * 
     * @param uri           请求的URI路径
     * @param method        HTTP请求方法（如GET、POST等）
     * @param Authorization Authorization请求头列表
     * @return 如果不需要认证或认证通过返回true，否则返回false
     */
    public static boolean auth(String uri, String method, List<String> Authorization) {
        if (Config.authToken != null && !Config.authToken.isEmpty() && !cache.isEmpty() && needAuth(uri, method)) {
            if (Authorization == null || Authorization.isEmpty()) {
                return false;
            }
            // 检查 Authorization header 中是否有匹配的 token
            // 支持 "Bearer <token>" 或直接 "<token>" 格式
            for (String auth : Authorization) {
                if (auth != null) {
                    String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
                    if (Config.authToken.equals(token)) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            return true;
        }
    }

    private static boolean needAuth(String uri, String method) {
        String matchedPrefix = null;
        int maxLen = 0;

        for (String prefix : cache.keySet()) {
            if (uri.startsWith(prefix) && prefix.length() > maxLen) {
                matchedPrefix = prefix;
                maxLen = prefix.length();
            }
        }

        if (matchedPrefix != null) {
            String[] methods = cache.get(matchedPrefix);
            for (String m : methods) {
                if (m.equalsIgnoreCase(method)) {
                    return true; // 该 URL 和方法需要认证
                }
            }
        }
        return false; // 不需要认证
    }
}
