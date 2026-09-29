package love.shirokasoke.webapi.webserver.mcp;

import static love.shirokasoke.webapi.Constant.mapper;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class McpTool {

    public final String name;
    public final String description;
    public final String route;
    public final String method;
    /** 是否写操作（写工具可按配置整体关闭） */
    public final boolean write;
    public final List<String> queryParams;
    public final List<String> bodyParams;
    /** 每个参数的类型描述：name → {type, required, description} */
    public final Map<String, ParamSpec> params;

    public McpTool(String name, String description, String route, String method, boolean write,
        List<String> queryParams, List<String> bodyParams, Map<String, ParamSpec> params) {
        this.name = name;
        this.description = description;
        this.route = route;
        this.method = method;
        this.write = write;
        this.queryParams = queryParams;
        this.bodyParams = bodyParams;
        this.params = params;
    }

    /** 参数规格 */
    public static final class ParamSpec {

        // string / integer / boolean / number / object / array
        public final String type;
        public final boolean required;
        public final String description;

        public ParamSpec(String type, boolean required, String description) {
            this.type = type;
            this.required = required;
            this.description = description;
        }
    }

    /** 生成 MCP 标准的 tool 描述对象 */
    public ObjectNode toMcpDescriptor() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ArrayNode required = mapper.createArrayNode();
        params.forEach((pname, spec) -> {
            if (bodyParams != null && bodyParams.contains(pname)) return; // body 参数单独表达
            ObjectNode p = props.putObject(pname);
            p.put("type", spec.type == null ? "string" : spec.type);
            if (spec.description != null) p.put("description", spec.description);
            if (spec.required) required.add(pname);
        });
        if (!required.isEmpty()) schema.set("required", required);
        // 请求体：bodyParams 含 "body" 时透传原始 JSON（对象或数组）
        if (bodyParams != null && !bodyParams.isEmpty()) {
            boolean passthroughBody = bodyParams.contains("body");
            ObjectNode body = props.putObject("body");
            body.put("type", passthroughBody ? "object" : "object");
            body.put(
                "description",
                passthroughBody ? "Raw JSON request body passed through to the route handler (object or array)"
                    : "Request body passed through to the route handler");
        }
        return tool;
    }
}
