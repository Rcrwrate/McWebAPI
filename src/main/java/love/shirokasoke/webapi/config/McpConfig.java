package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.mcp", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class McpConfig {

    @Config.Comment("Enable the built-in MCP endpoint at POST /mcp (Streamable HTTP, JSON-RPC 2.0)")
    public static boolean enable = false;

    @Config.Comment({ "Allow MCP tools that perform write operations",
        "Read-only tools are always available when MCP is enabled.",
        "Keep disabled unless you explicitly want an AI agent to modify the world." })
    public static boolean allowWrite = false;

    @Config.Comment("MCP protocol version advertised by the server; unsupported client versions negotiate down to this")
    public static String protocolVersion = "2025-03-26";
}
