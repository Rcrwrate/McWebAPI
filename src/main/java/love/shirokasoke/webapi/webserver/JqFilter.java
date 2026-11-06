package love.shirokasoke.webapi.webserver;

import static love.shirokasoke.webapi.Constant.mapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.webserver.RouteHandler.ApiException;

/**
 * JQ is best!
 * 
 * @apiNote 安全起见，jq 的 {@code import}/{@code include} 不可用，{@code env} 也不可用
 * @apiNote 但 jq 允许递归（如 {@code [range(1e9)]}），存在 CPU 耗尽型 DDoS 风险
 */
public final class JqFilter {

    private static final int MAX_EXPR_LENGTH = 512;
    /** 只读共享，初始化后不再修改（jackson-jq 要求） */
    private static final Scope ROOT_SCOPE;
    /**
     * 编译产物是不可变对象，无需 refresh
     */
    private static final LoadingCache<String, JsonQuery> CACHE = Caffeine.newBuilder()
        .softValues()
        .maximumSize(64)
        .expireAfterAccess(Duration.ofMinutes(30))
        .build(key -> JsonQuery.compile(key, Versions.JQ_1_7));

    static {
        Scope scope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance()
            .loadFunctions(Versions.JQ_1_7, scope);
        ROOT_SCOPE = scope;
    }

    private JqFilter() {}

    public static JsonNode filter(HttpExchange exchange, JsonNode data) throws ApiException {
        String expression = expression(exchange);
        if (expression == null) return data;

        long start = System.nanoTime();
        JsonQuery query;
        try {
            query = CACHE.get(expression);
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new ApiException(400, "Invalid jq expression: " + cause.getMessage());
        }

        List<JsonNode> outputs = new ArrayList<>();
        try {
            query.apply(
                Scope.newChildScope(ROOT_SCOPE),
                data == null ? NullNode.getInstance() : data,
                node -> { outputs.add(node == null ? NullNode.getInstance() : node); });
        } catch (JsonQueryException | RuntimeException e) {
            throw new ApiException(400, "jq evaluation failed: " + e.getMessage());
        }

        JsonNode result = switch (outputs.size()) {
            case 0 -> NullNode.getInstance();
            case 1 -> outputs.get(0);
            default -> {
                ArrayNode array = mapper.createArrayNode();
                array.addAll(outputs);
                yield array;
            }
        };
        MyMod.LOG.debug(
            "jq\t{}\t-> {} result(s)\t{}ms",
            expression,
            outputs.size(),
            String.format("%.3f", (System.nanoTime() - start) / 1_000_000.0));
        return result;
    }

    /**
     * 获取JQ表达式
     */
    private static String expression(HttpExchange exchange) throws ApiException {
        String value = exchange.getRequestHeaders()
            .getFirst("Filter");
        if (value == null) return null;

        value = value.trim();
        if (value.isEmpty() || ".".equals(value)) return null;
        if (value.length() > MAX_EXPR_LENGTH) {
            throw new ApiException(414, "jq expression too long (max " + MAX_EXPR_LENGTH + ')');
        }
        return value;
    }
}
