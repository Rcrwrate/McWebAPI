package love.shirokasoke.webapi.webserver.Auth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import love.shirokasoke.webapi.Config;

/**
 * 基于 Token 的认证提供者，兼容原有 authToken + authUrlPrefixes 配置
 */
public class TokenAuthProvider implements AuthProvider {

    private int priority = 100;
    private Map<String, String[]> urlCache = new HashMap<>();

    public TokenAuthProvider() {}

    public TokenAuthProvider(int priority) {
        this.priority = priority;
    }

    /**
     * 初始化 URL 前缀缓存
     */
    public TokenAuthProvider setup(String[] urlPrefixes) {
        urlCache.clear();
        for (String item : urlPrefixes) {
            String[] parts = item.split("\\|");
            if (parts.length >= 2) {
                String url = parts[0];
                String[] methods = Arrays.copyOfRange(parts, 1, parts.length);
                urlCache.put(url, methods);
            }
        }
        return this;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public String getName() {
        return "TokenAuth";
    }

    @Override
    public AuthResult authenticate(String uri, String method, List<String> authorization) {
        if (Config.authToken == null || Config.authToken.isEmpty() || urlCache.isEmpty()) {
            return AuthResult.skip();
        }
        if (!needAuth(uri, method)) {
            return AuthResult.skip();
        }
        if (authorization == null || authorization.size() != 1) {
            return AuthResult.deny("missing or invalid Authorization header");
        }
        for (String auth : authorization) {
            if (auth != null) {
                String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
                if (Config.authToken.equals(token)) {
                    return AuthResult.pass();
                }
            }
        }
        return AuthResult.deny("invalid token");
    }

    protected boolean needAuth(String uri, String method) {
        String matchedPrefix = null;
        int maxLen = 0;

        for (String prefix : urlCache.keySet()) {
            if (uri.startsWith(prefix) && prefix.length() > maxLen) {
                matchedPrefix = prefix;
                maxLen = prefix.length();
            }
        }

        if (matchedPrefix != null) {
            String[] methods = urlCache.get(matchedPrefix);
            for (String m : methods) {
                if (m.equalsIgnoreCase(method)) {
                    return true;
                }
            }
        }
        return false;
    }
}
