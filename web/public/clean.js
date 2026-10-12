/**
 * localStorage 清理脚本（每日最多执行一次）。
 * 只保留 KEEP_PREFIXES 中任一前缀开头的 key，其余一律删除：
 * - 清除 PersistentDataGrid 旧版 "grid:" 前缀残留（新版为 "grid-state:"）
 * - 顺带清除其它任何来源写入的未知/废弃 key
 * 执行完成后写入当日日期标记，同一天内再次加载直接短路。
 */
;(function () {
    var CLEAN_FLAG = "local-clean-date"

    /**
     * 保留这些前缀开头的 key，未匹配的 key 全部删除。
     * 新增本地存储功能时请在此登记前缀，否则数据会被本脚本清除。
     */
    var KEEP_PREFIXES = [
        "grid-state:",         // PersistentDataGrid 表格状态
        "gt5_machines",        // GT5 已保存机器
        "ae_coords",           // AE 坐标
        "dashboard-config-v1", // 仪表盘布局
        "dark", "darkm", "lightm", // 主题设置
        "url", "auth",         // API 设置
        "noSw",                // Service Worker 开关
    ]

    function today() {
        var d = new Date()
        return d.getFullYear() + "-" + (d.getMonth() + 1) + "-" + d.getDate()
    }

    try {
        var store = window.localStorage
        if (store.getItem(CLEAN_FLAG) === today()) return

        // 先收集再删除，避免删除时索引错位
        var stale = []
        for (var i = 0; i < store.length; i++) {
            var key = store.key(i)
            if (!key) continue
            var keep = false
            for (var j = 0; j < KEEP_PREFIXES.length; j++) {
                if (key.indexOf(KEEP_PREFIXES[j]) === 0) {
                    keep = true
                    break
                }
            }
            if (!keep) stale.push(key)
        }
        for (var k = 0; k < stale.length; k++) {
            store.removeItem(stale[k])
        }
        store.setItem(CLEAN_FLAG, today())
    } catch (e) {
        // localStorage 不可用（隐私模式等）时静默跳过
    }
})()
