package love.shirokasoke.webapi.webserver.Auth;

import java.util.List;

public interface AuthProvider {

    /**
     * 获取优先级，数值越小优先级越高
     */
    int getPriority();

    /**
     * 获取提供者名称，用于日志和调试
     */
    String getName();

    /**
     * 执行认证逻辑
     * <p>
     * 如果此 provider 不处理该请求，应返回 {@link AuthResult#skip()}。
     *
     * @param uri           请求的URI路径
     * @param method        HTTP请求方法
     * @param authorization Authorization请求头列表
     * @return 认证结果：PASS / DENY / SKIP
     */
    AuthResult authenticate(String uri, String method, List<String> authorization);
}
