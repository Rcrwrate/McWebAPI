package love.shirokasoke.webapi.webserver.Auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;

/**
 * 认证管理器
 * <p>
 * 按优先级从高到低（priority 数值从小到大）依次调用各 provider：
 * - 任一 provider 返回 PASS → 认证通过
 * - 任一 provider 返回 DENY → 认证失败
 * - provider 返回 SKIP → 跳过，继续下一个
 * - 所有 provider 都 SKIP → 默认通过（无需认证）
 */
public class Auth {

    private static final List<AuthProvider> providers = new ArrayList<>();

    /**
     * 注册所有认证插件
     */
    public static void init() {
        providers.clear();
        if (Config.authUrlPrefixes.length > 0 && !Config.authToken.isEmpty()) {
            registerProvider(new TokenAuthProvider().setup(Config.authUrlPrefixes));
        }
        if (Config.bannedPrefixes.length > 0) {
            registerProvider(new BannedPreProvider().setup(Config.bannedPrefixes));
        }
    }

    public static void registerProvider(AuthProvider provider) {
        providers.add(provider);
        Collections.sort(providers, (a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        MyMod.LOG.info("Registered AuthProvider: {} (priority={})", provider.getName(), provider.getPriority());
    }

    public static void removeProvider(AuthProvider provider) {
        providers.remove(provider);
        MyMod.LOG.info("Removed AuthProvider: {}", provider.getName());
    }

    /**
     * 获取所有已注册的认证提供者（只读）
     */
    public static List<AuthProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    public static boolean auth(String uri, String method, List<String> Authorization) {
        for (AuthProvider provider : providers) {
            AuthResult result = provider.authenticate(uri, method, Authorization);

            switch (result.getStatus()) {
                case PASS:
                    return true;
                case DENY:
                    MyMod.LOG.warn(
                        "Auth denied by provider [{}]: {} - {} {}",
                        provider.getName(),
                        method,
                        uri,
                        result.getMessage());
                    return false;
                case SKIP:
                    continue;
            }
        }

        // 所有 provider 都 SKIP，默认通过
        return true;
    }
}
