package love.shirokasoke.webapi.webserver.mcp;

import static love.shirokasoke.webapi.Constant.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.config.McpConfig;
import love.shirokasoke.webapi.config.SecurityConfig;
import love.shirokasoke.webapi.webserver.mcp.McpTool.ParamSpec;

/**
 * MCP 工具注册表。
 */
public final class McpRegistry {

    private static final Map<String, McpTool> TOOLS = new LinkedHashMap<>();

    static {
        build();
    }

    private McpRegistry() {}

    // region 写入开关

    /**
     * 写工具总开关。默认关闭，需在 {@link McpConfig#allowWrite} 里显式打开。
     */
    public static boolean writeEnabled() {
        return McpConfig.allowWrite;
    }

    private static boolean isRouteDisabled(String route) {
        String[] disabled = SecurityConfig.disabledRoutes;
        if (disabled == null) return false;
        for (String d : disabled) {
            if (d == null) continue;
            if (d.equals(route) || (d.endsWith("/") && route.startsWith(d)) || route.startsWith(d + "/")) {
                return true;
            }
        }
        return false;
    }

    /** 工具是否可用：写工具需要写开关，且路由未被禁用 */
    public static boolean isToolEnabled(McpTool tool) {
        if (isRouteDisabled(tool.route)) return false;
        if (tool.write && !writeEnabled()) return false;
        return true;
    }

    public static McpTool get(String name) {
        return TOOLS.get(name);
    }

    public static List<McpTool> tools() {
        List<McpTool> enabled = new ArrayList<>();
        for (McpTool t : TOOLS.values()) {
            if (isToolEnabled(t)) enabled.add(t);
        }
        return enabled;
    }

    public static Map<String, McpTool> all() {
        return Collections.unmodifiableMap(TOOLS);
    }

    // region 参数映射

    /** 从 MCP arguments 提取 query 参数：只取在 queryParams 白名单里的字段 */
    public static Map<String, String> buildQuery(McpTool tool, JsonNode args) {
        Map<String, String> query = new LinkedHashMap<>();
        if (args == null || !args.isObject()) return query;
        for (String p : tool.queryParams) {
            JsonNode v = args.get(p);
            if (v == null || v.isNull()) continue;
            query.put(p, v.asText());
        }
        return query;
    }

    /** 从 MCP arguments 生成请求体：优先取 body 对象，其次把 bodyParams 拼成对象 */
    public static byte[] buildBody(McpTool tool, JsonNode args) throws Exception {
        if (args == null || !args.isObject()) return new byte[0];
        JsonNode body = args.get("body");
        if (body != null && !body.isNull()) {
            return mapper.writeValueAsBytes(body);
        }
        ObjectNode out = mapper.createObjectNode();
        boolean any = false;
        for (String p : tool.bodyParams) {
            JsonNode v = args.get(p);
            if (v == null || v.isNull()) continue;
            out.set(p, v);
            any = true;
        }
        return any ? mapper.writeValueAsBytes(out) : new byte[0];
    }

    /** 工具调用时注入的请求头（鉴权 token 由 Auth 配置决定，这里透传） */
    public static Map<String, String> requestHeaders(McpTool tool) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        // 内部调用：若配置了 token 鉴权，注入同一 token，避免自我认证失败
        if (SecurityConfig.authToken != null && !SecurityConfig.authToken.isEmpty()) {
            headers.put("Authorization", "Bearer " + SecurityConfig.authToken);
        }
        return headers;
    }

    // region 工具定义

    private static void add(McpTool tool) {
        TOOLS.put(tool.name, tool);
    }

    private static ParamSpec p(String type, boolean required, String desc) {
        return new ParamSpec(type, required, desc);
    }

    private static List<String> list(String... items) {
        return Collections.unmodifiableList(java.util.Arrays.asList(items));
    }

    private static void build() {
        // region 读：服务状态
        add(
            new McpTool(
                "get_version",
                "Get MCWebAPI mod version and build time.",
                "/version",
                "GET",
                false,
                list(),
                list(),
                Map.of()));
        add(
            new McpTool(
                "get_tps",
                "Get server TPS and tick time for every loaded dimension.",
                "/tps",
                "GET",
                false,
                list(),
                list(),
                Map.of()));
        add(
            new McpTool(
                "get_world_info",
                "Get world info (per-dimension WorldInfo, time, weather, mobs).",
                "/WorldInfo",
                "GET",
                false,
                list(),
                list(),
                Map.of()));

        // region 读：坐标类
        Map<String, ParamSpec> coord = Map.of(
            "x",
            p("integer", true, "Block X"),
            "y",
            p("integer", true, "Block Y"),
            "z",
            p("integer", true, "Block Z"),
            "dim",
            p("integer", false, "Dimension id, default 0"));

        add(
            new McpTool(
                "get_block",
                "Get block and metadata at coordinates.",
                "/block",
                "GET",
                false,
                list("x", "y", "z", "dim"),
                list(),
                coord));
        add(
            new McpTool(
                "get_block_tile",
                "Get a block's top-layer texture as PNG image content. "
                    + "Provide id (numeric) or regName; meta optional, default 0. Coordinates are not used.",
                "/block/tile",
                "GET",
                false,
                list("id", "regName", "meta"),
                list(),
                Map.of(
                    "id",
                    p("integer", false, "Numeric block id (either id or regName is required)"),
                    "regName",
                    p("string", false, "Block registry name, e.g. minecraft:stone"),
                    "meta",
                    p("integer", false, "Block metadata, default 0"))));
        add(
            new McpTool(
                "get_gt5_machine",
                "Detect a GT5 machine at coordinates and return its working state.",
                "/gt5",
                "GET",
                false,
                list("x", "y", "z", "dim"),
                list(),
                coord));
        add(
            new McpTool(
                "get_ae_items",
                "Query AE2 network item list at coordinates.",
                "/ae/item",
                "GET",
                false,
                list("x", "y", "z", "dim"),
                list(),
                coord));
        add(
            new McpTool(
                "get_ae_nodes",
                "List AE2 grid nodes at coordinates.",
                "/ae/nodes",
                "GET",
                false,
                list("x", "y", "z", "dim"),
                list(),
                coord));
        add(
            new McpTool(
                "get_ae_cpu",
                "Get AE2 crafting CPU status at coordinates.",
                "/ae/cpu",
                "GET",
                false,
                list("x", "y", "z", "dim"),
                list(),
                coord));

        // region 读：注册表类
        add(new McpTool("list_items", "List every registered item.", "/items", "GET", false, list(), list(), Map.of()));
        add(
            new McpTool(
                "list_blocks",
                "List every registered block.",
                "/blocks",
                "GET",
                false,
                list(),
                list(),
                Map.of()));
        add(
            new McpTool(
                "list_fluids",
                "List every registered fluid.",
                "/fluids",
                "GET",
                false,
                list(),
                list(),
                Map.of()));
        add(
            new McpTool(
                "list_entities",
                "List all loaded entities for every dimension.",
                "/entities",
                "GET",
                false,
                list(),
                list(),
                Map.of()));
        add(
            new McpTool(
                "list_chunks",
                "List force-loaded chunks and their remaining time.",
                "/chunk/force",
                "GET",
                false,
                list(),
                list(),
                Map.of()));
        add(
            new McpTool(
                "get_item",
                "Get item info by numeric item id.",
                "/item",
                "GET",
                false,
                list("id"),
                list(),
                Map.of("id", p("integer", true, "Numeric item id"))));
        add(
            new McpTool(
                "get_item_icon",
                "Render an item icon; the PNG is returned as MCP image content.",
                "/item/icon",
                "GET",
                false,
                list("id", "damage", "tag"),
                list(),
                Map.of(
                    "id",
                    p("integer", true, "Numeric item id"),
                    "damage",
                    p("integer", false, "Item damage/metadata"),
                    "tag",
                    p("string", false, "Base64 NBT tag"))));
        add(
            new McpTool(
                "get_chunk",
                "Query a single chunk. Provide chunkX+chunkZ or world x+z.",
                "/chunk",
                "GET",
                false,
                list("chunkX", "chunkZ", "x", "z", "dim"),
                list(),
                Map.of(
                    "chunkX",
                    p("integer", false, "Chunk X (or use x)"),
                    "chunkZ",
                    p("integer", false, "Chunk Z (or use z)"),
                    "x",
                    p("integer", false, "World X"),
                    "z",
                    p("integer", false, "World Z"),
                    "dim",
                    p("integer", false, "Dimension id, default 0"))));

        // region 读：配方
        Map<String, ParamSpec> recipeQuery = Map.of(
            "type",
            p("string", false, "Query direction: output (default) or input"),
            "id",
            p("integer", false, "Numeric item id to match, e.g. 7437 = gregtech:gt.metaitem.01"),
            "damage",
            p("integer", false, "Item damage/metadata to match; 32767 = wildcard (any damage)"),
            "tag",
            p("string", false, "Base64 NBT tag of the query item"),
            "limit",
            p("integer", false, "Max recipes per page, 1-5000 (default 200)"),
            "offset",
            p("integer", false, "Pagination offset"));
        add(
            new McpTool(
                "get_furnace_recipes",
                "Get furnace smelting recipes; filter by item with id/damage/tag, paginate with limit/offset.",
                "/recipes/furnace",
                "GET",
                false,
                list("type", "id", "damage", "tag", "limit", "offset"),
                list(),
                recipeQuery));
        add(
            new McpTool(
                "get_crafting_recipes",
                "Get crafting table recipes; filter by item with id/damage/tag, paginate with limit/offset.",
                "/recipes/crafting",
                "GET",
                false,
                list("type", "id", "damage", "tag", "limit", "offset"),
                list(),
                recipeQuery));
        Map<String, ParamSpec> gtRecipeQuery = new LinkedHashMap<>(recipeQuery);
        gtRecipeQuery.put(
            "map",
            p("string", false, "Restrict to one recipe map (unlocalizedName, e.g. gt.recipe.blastfurnace)"));
        gtRecipeQuery.put("fluid", p("string", false, "Fluid name or numeric id to match"));
        add(
            new McpTool(
                "get_gt_recipe_map",
                "Query GT5 machine recipes; filter by item (id/damage/tag), fluid, or a single recipe map. "
                    + "Provide at least one of item/fluid/map; list map names with get_gt_recipe_maps.",
                "/recipes/gt",
                "GET",
                false,
                list("type", "id", "damage", "tag", "fluid", "map", "limit", "offset"),
                list(),
                gtRecipeQuery));
        add(
            new McpTool(
                "get_gt_recipe_maps",
                "List all GT5 recipe maps (unlocalizedName + localized name); use as the 'map' filter of get_gt_recipe_map.",
                "/recipes/gt/maps",
                "GET",
                false,
                list(),
                list(),
                Map.of()));

        // region 3D 打印
        // /3d/size 与 /3d/player 仅接受 PUT 原始图片字节，MCP 的 JSON body 透传无法携带二进制，不注册为工具

        // region 写
        add(
            new McpTool(
                "set_block",
                "Set a single block. Body: {id, metadataIn?, flag?, nbt?}. WRITE OPERATION.",
                "/setblock",
                "POST",
                true,
                list("x", "y", "z", "dim"),
                list("body"),
                coord));
        add(
            new McpTool(
                "batch_set_block",
                "Submit a batch setblock task array. Body is a JSON array of {x,y,z,dim,id,metadata?,flag?}. "
                    + "Returns a job id; poll with the same tool using jobId. WRITE OPERATION.",
                "/batchsetblock",
                "POST",
                true,
                list("jobId"),
                list("body"),
                Map.of("jobId", p("string", false, "Job id to poll (GET); omit to submit (POST)"))));
        add(
            new McpTool(
                "force_chunk",
                "Force-load or unload a chunk. Query: action=load|unload, chunkX/chunkZ or x/z, dim, duration. WRITE OPERATION.",
                "/chunk/force",
                "POST",
                true,
                list("action", "chunkX", "chunkZ", "x", "z", "dim", "duration"),
                list(),
                Map.of(
                    "action",
                    p("string", true, "load | unload"),
                    "chunkX",
                    p("integer", false, "Chunk X (or use x)"),
                    "chunkZ",
                    p("integer", false, "Chunk Z (or use z)"),
                    "x",
                    p("integer", false, "World X"),
                    "z",
                    p("integer", false, "World Z"),
                    "dim",
                    p("integer", false, "Dimension id, default 0"),
                    "duration",
                    p("integer", false, "Hold duration in seconds, default 60"))));
        add(
            new McpTool(
                "gt5_machine_control",
                "Start or stop a GT5 machine, same as a soft mallet. WRITE OPERATION.",
                "/gt5/status",
                "POST",
                true,
                list("x", "y", "z", "dim", "action"),
                list(),
                Map.of(
                    "x",
                    p("integer", true, "Block X"),
                    "y",
                    p("integer", true, "Block Y"),
                    "z",
                    p("integer", true, "Block Z"),
                    "dim",
                    p("integer", false, "Dimension id, default 0"),
                    "action",
                    p("string", true, "start | stop"))));
        add(
            new McpTool(
                "ae_cpu_submit_task",
                "Submit an AE2 auto-crafting task to a crafting CPU at coordinates. WRITE OPERATION.",
                "/ae/cpu/task",
                "POST",
                true,
                list("x", "y", "z", "dim"),
                list("id", "Damage", "Count", "Type", "tag", "cpu"),
                Map.of(
                    "id",
                    p("integer", true, "Numeric item id to craft (fluid id when Type=fluid)"),
                    "Damage",
                    p("integer", false, "Item damage/metadata, default 0"),
                    "Count",
                    p("integer", true, "Craft amount"),
                    "Type",
                    p("string", false, "item (default) or fluid"),
                    "tag",
                    p("string", false, "Base64 NBT tag of the item"),
                    "cpu",
                    p("string", false, "Target crafting CPU name; omit to auto-assign"))));
        add(
            new McpTool(
                "ae_cpu_cancel",
                "Cancel a running AE2 crafting CPU job at coordinates. WRITE OPERATION.",
                "/ae/cpu/cancel",
                "POST",
                true,
                list("x", "y", "z", "dim"),
                list("body"),
                coord));

        MyMod.LOG.info("[MCP] Registered {} tools ({} enabled)", TOOLS.size(), tools().size());
    }
}
