#!/usr/bin/env bash
# McWebAPI 简易 Bash 工具
#
# 配置（环境变量）：
#   MCWEBAPI_URL     服务地址       默认 http://127.0.0.1:40002
#   MCWEBAPI_TOKEN   authToken      为空可略
#   MCWEBAPI_DIM     默认维度       默认 0
#   MCWEBAPI_TIMEOUT curl 超时秒    默认 30
#   MCWEBAPI_JQ      全局 jq 过滤器（对 api 输出再过滤一次），可选
#
# 常用示例：
#   wapi.sh status                        # version + tps
#   wapi.sh block 100 64 100              # 查方块（dim 用 MCWEBAPI_DIM）
#   wapi.sh setblock 100 64 100 30111 0   # 放方块（id [meta] [dim]）
#   wapi.sh search 铱                     # 物品模糊搜索
#   wapi.sh item 铱锭                     # 精确名 → id + 子类型
#   wapi.sh recipe-gt 30111 0 5           # GT 配方
#   wapi.sh ae 100 64 100                 # AE 网络摘要
#   wapi.sh ae-craft 100 64 100 30111 64  # 下合成单
#   wapi.sh gt5-scan 6 -3                 # 扫区块 GT5 机器（提交+轮询）
#   wapi.sh raw GET /WorldInfo            # 任意端点逃生舱
#   wapi.sh sse "/ae/cpu/sse?x=0&y=64&z=0&interval=5"   # SSE 事件流
set -euo pipefail

BASE="${MCWEBAPI_URL:-http://127.0.0.1:40002}"
TOKEN="${MCWEBAPI_TOKEN:-}"
DIM="${MCWEBAPI_DIM:-0}"
TIMEOUT="${MCWEBAPI_TIMEOUT:-30}"
GJQ="${MCWEBAPI_JQ:-}"

command -v jq >/dev/null || { echo "需要 jq：apt install jq / brew install jq" >&2; exit 1; }

die() { echo "$*" >&2; exit 1; }
need() { [ $# -ge "$1" ] || die "缺少参数，用法见：$0 help"; }
enc() { jq -rn --arg s "$1" '$s|@uri'; }

# ── 核心：统一请求层 ──────────────────────────────────────────────
# req <METHOD> <path> [curl 附加参数...]   原始输出，不解析
req() {
  local method="$1" path="$2"; shift 2
  local args=(-sS --max-time "$TIMEOUT" -X "$method" "$BASE$path" -H "Accept: application/json")
  if [ -n "$TOKEN" ]; then args+=(-H "Authorization: $TOKEN"); fi
  curl "${args[@]}" "$@"
}

# api <path>                GET，解包 ApiResponse（success=false 时报错），输出 .data
api() {
  local resp
  if ! resp="$(req GET "$1")"; then echo "请求失败：$1" >&2; return 1; fi
  if ! echo "$resp" | jq -e '.success == true' >/dev/null 2>&1; then
    echo "$resp" | jq -r '.message // .' >&2
    return 1
  fi
  local data; data="$(echo "$resp" | jq '.data')"
  if [ -n "$GJQ" ]; then echo "$data" | jq "$GJQ"; else echo "$data"; fi
}

# api_json <METHOD> <path> <json-body>  带 JSON body 的请求，解包方式同 api
api_json() {
  local resp
  if ! resp="$(req "$1" "$2" -H "Content-Type: application/json" -d "$3")"; then
    echo "请求失败：$2" >&2; return 1
  fi
  if ! echo "$resp" | jq -e '.success == true' >/dev/null 2>&1; then
    echo "$resp" | jq -r '.message // .' >&2
    return 1
  fi
  local data; data="$(echo "$resp" | jq '.data')"
  if [ -n "$GJQ" ]; then echo "$data" | jq "$GJQ"; else echo "$data"; fi
}

# api_file <METHOD> <path> <file>       上传文件（二进制 body）
api_file() {
  local resp
  if ! resp="$(req "$1" "$2" --data-binary "@$3")"; then
    echo "请求失败：$2" >&2; return 1
  fi
  if ! echo "$resp" | jq -e '.success == true' >/dev/null 2>&1; then
    echo "$resp" | jq -r '.message // .' >&2
    return 1
  fi
  local data; data="$(echo "$resp" | jq '.data')"
  if [ -n "$GJQ" ]; then echo "$data" | jq "$GJQ"; else echo "$data"; fi
}

# wait_done <path> [间隔秒] [超时秒]     轮询慢任务（.status == completed）
wait_done() {
  local path="$1" interval="${2:-0.5}" timeout="${3:-120}"
  local deadline=$((SECONDS + timeout))
  while :; do
    local out st
    if ! out="$(api "$path")"; then return 1; fi
    st="$(jq -r '.status // empty' <<<"$out")"
    case "$st" in
      completed) printf '%s\n' "$out"; return 0 ;;
      failed|error)
        jq -r '.message // "任务失败"' <<<"$out" >&2; return 1 ;;
    esac
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "轮询超时（${timeout}s），任务可能仍在执行：$path" >&2; return 1
    fi
    sleep "$interval"
  done
}

usage() { sed -n '/^# 常用示例：/,/^set -euo/p' "$0" | sed '1d;$d;s/^# \{0,1\}//'; }

# ── 子命令：状态 / 性能 ──────────────────────────────────────────
cmd_status()    { echo "--- version"; api /version; echo "--- tps"; api /tps; }
cmd_profiler()  { api /profiler; }
cmd_lag()       { api "/lag-analyzer"; }
cmd_sse_check() {
  local msg
  if msg="$(timeout 5 curl -sN --max-time 5 ${TOKEN:+-H "Authorization: $TOKEN"} "$BASE/test/sse" 2>/dev/null)"; then
    echo "SSE 可用：$msg"; return 0
  fi
  echo "SSE 不可用（需服务端启用虚拟线程）" >&2; return 1
}

# ── 子命令：世界 / 方块 ──────────────────────────────────────────
cmd_world()     { api "/WorldInfo"; }
cmd_blocks()    { api "/blocks"; }
cmd_block()     { api "/block?x=$1&y=$2&z=$3&dim=${4:-$DIM}"; }
cmd_fmp()       { api "/block/fmp?posX=$1&posY=$2&posZ=$3&dimension=${4:-$DIM}"; }
cmd_tile() {
  local key="$1" meta="${2:-0}" out="${3:-}"
  local arg; if [[ "$key" =~ ^[0-9]+$ ]]; then arg="id=$key"; else arg="regName=$(enc "$key")"; fi
  if [ -n "$out" ]; then req GET "/block/tile?$arg&meta=$meta" > "$out"; echo "已写入 $out"; \
  else req GET "/block/tile?$arg&meta=$meta"; fi
}
cmd_setblock() {
  local body="{\"id\":$4,\"metadataIn\":${5:-0}"
  [ -n "${WAPI_FLAG:-}" ] && body="$body,\"flag\":$WAPI_FLAG"
  [ -n "${WAPI_NBT:-}" ] && body="$body,\"nbt\":\"$WAPI_NBT\""
  api_json POST "/setblock?x=$1&y=$2&z=$3&dim=${6:-$DIM}" "$body}"
}
cmd_batchsetblock() { api_json POST "/batchsetblock" "$(cat)"; }
cmd_batchsetblock_wait() {
  local id; id="$(api_json POST "/batchsetblock" "$(cat)" | jq -r '.id')"
  echo "job=$id" >&2; wait_done "/batchsetblock?id=$id" 0.2 600
}
cmd_job() { api "/$1?id=$2"; }
cmd_wait() { wait_done "$1" "${2:-0.5}" "${3:-120}"; }

# ── 子命令：物品 / 流体 ──────────────────────────────────────────
cmd_items()      { api "/items"; }
cmd_search()     { api "/items" | jq -c --arg kw "$1" '[.[] | select(.localizedName | test($kw))] | .[0:20]'; }
cmd_item() {
  local id
  id="$(api "/items" | jq -r --arg kw "$1" '[.[] | select(.localizedName == $kw)] | .[0].id // empty')"
  [ -n "$id" ] || die "未找到精确匹配：$1（试试 search 子命令）"
  echo "id=$id"
  api "/item?id=$id" | jq '.subs // .'
}
cmd_item_by_id() { api "/item?id=$1" | jq '.subs // .'; }
cmd_item_icon()  { local out="${3:-}"; if [ -n "$out" ]; then req GET "/item/icon?id=$1&damage=${2:-0}" > "$out"; echo "已写入 $out"; else req GET "/item/icon?id=$1&damage=${2:-0}"; fi; }
cmd_ae_items_def() { api "/items/ae"; }
cmd_fluids()          { api "/fluids"; }
cmd_fluid_containers(){ api "/fluidContainers"; }
cmd_fluid_icon()      { local out="${2:-}"; if [ -n "$out" ]; then req GET "/fluid/icon?name=$(enc "$1")" > "$out"; echo "已写入 $out"; else req GET "/fluid/icon?name=$(enc "$1")"; fi; }

# ── 子命令：实体 / 区块 ──────────────────────────────────────────
cmd_entities() { api "/entities"; }
cmd_entities_filtered() { api "/entities" | jq '[to_entries[] | {dim: .key, entities: [.value.entities[]?]}]'; }
cmd_entity()   { api "/entity?id=$1"; }
cmd_chunks()   { api "/chunks"; }
cmd_chunk()    { api "/chunk?chunkX=$1&chunkZ=$2&dim=${3:-$DIM}"; }
cmd_chunk_map() {
  if [ "${1#-}" != "$1" ] || [ "$1" = "--raw" ]; then  # wapi.sh chunk-map --raw cx cz [dim]
    shift; req GET "/chunk/map?chunkX=$1&chunkZ=$2&dim=${3:-$DIM}&raw=true"
  else
    api "/chunk/map?chunkX=$1&chunkZ=$2&dim=${3:-$DIM}"
  fi
}
cmd_chunk_force()   { api "/chunk/force"; }
cmd_chunk_load()    { api_json POST "/chunk/force?action=load&x=$1&z=$2&dim=${3:-$DIM}${4:+&duration=$4}" ""; }
cmd_chunk_unload()  { api_json POST "/chunk/force?action=unload&x=$1&z=$2&dim=${3:-$DIM}" ""; }

# ── 子命令：GT5 ──────────────────────────────────────────────────
cmd_gt5()       { api "/gt5?x=$1&y=$2&z=$3&dim=${4:-$DIM}"; }
cmd_gt5_start() { api_json POST "/gt5/status?x=$1&y=$2&z=$3&dim=${4:-$DIM}&action=start" ""; }
cmd_gt5_stop()  { api_json POST "/gt5/status?x=$1&y=$2&z=$3&dim=${4:-$DIM}&action=stop" ""; }
cmd_gt5_batch() { api_json POST "/gt5/batch" "$(cat)"; }
cmd_gt5_batch_wait() {
  local id; id="$(api_json POST "/gt5/batch" "$(cat)" | jq -r '.id')"
  echo "job=$id" >&2; wait_done "/gt5/batch?id=$id" 0.2 600
}
cmd_gt5_scan() {
  local id; id="$(api_json POST "/gt5/scan?chunkX=$1&chunkZ=$2&dim=${3:-$DIM}" "" | jq -r '.id')"
  echo "job=$id" >&2; wait_done "/gt5/scan?id=$id" 1 120
}

# ── 子命令：AE2 ──────────────────────────────────────────────────
cmd_ae() {
  api "/ae/item?x=$1&y=$2&z=$3&dimension=${4:-$DIM}" \
    | jq '{usedTypes, totalTypes, usedBytes, totalBytes, cellStatus, total: (.items | length)}'
}
cmd_ae_items() {
  api "/ae/item?x=$1&y=$2&z=$3&dimension=${4:-$DIM}" \
    | jq -c --arg kw "${5:-}" '[.items[] | select($kw == "" or (.localizedName | test($kw)))] | .[0:50]'
}
cmd_ae_cpu()       { api "/ae/cpu?x=$1&y=$2&z=$3&dimension=${4:-$DIM}"; }
cmd_ae_nodes()     { api "/ae/nodes?x=$1&y=$2&z=$3&dimension=${4:-$DIM}"; }
cmd_ae_nodes_bad() { api "/ae/nodes?x=$1&y=$2&z=$3&dimension=${4:-$DIM}" | jq -c '[.[] | select(.active == false or .meetsChannel == false)]'; }
cmd_ae_me()        { api "/ae/me?x=$1&y=$2&z=$3&dimension=${4:-$DIM}"; }
cmd_ae_mes()       { api "/ae/mes?x=$1&y=$2&z=$3&dimension=${4:-$DIM}&pattern=true"; }
cmd_ae_me_support(){ api "/ae/me/support"; }
cmd_ae_craft()     { api_json POST "/ae/cpu/task?x=$1&y=$2&z=$3&dimension=${7:-$DIM}" "{\"id\":$4,\"Count\":$5,\"Damage\":${6:-0}}"; }
cmd_ae_craft_fluid(){ api_json POST "/ae/cpu/task?x=$1&y=$2&z=$3&dimension=${5:-$DIM}" "{\"id\":$4,\"Count\":$5,\"Type\":\"fluid\"}"; }
cmd_ae_cancel()    { api_json DELETE "/ae/cpu/cancel?x=$1&y=$2&z=$3&dimension=${4:-$DIM}" "{\"name\":\"$5\"}"; }

# ── 子命令：配方 ─────────────────────────────────────────────────
cmd_recipe_gt()       { api "/recipes/gt?type=${4:-output}&id=$1&damage=${2:-0}&limit=${3:-5}"; }
cmd_recipe_crafting() { api "/recipes/crafting?type=${4:-output}&id=$1&damage=${2:-0}&limit=${3:-5}"; }
cmd_recipe_furnace()  { api "/recipes/furnace?type=${4:-output}&id=$1&damage=${2:-0}&limit=${3:-5}"; }
cmd_recipe_maps()     { api "/recipes/gt/maps"; }
cmd_recipe_gt_map()   { api "/recipes/gt?map=$(enc "$1")&limit=${2:-20}"; }

# ── 子命令：3D 打印 ──────────────────────────────────────────────
cmd_print_size()   { api_file PUT "/3d/size${LABEL:+?label=$(enc "$LABEL")}" "$1"; }
cmd_print_player() {
  local q; if [[ "$1" =~ ^[0-9]+$ ]]; then q="id=$1"; else q="name=$(enc "$1")"; fi
  [ -n "${LABEL:-}" ] && q="$q&label=$(enc "$LABEL")"
  [ -n "${TOOLTIP:-}" ] && q="$q&tooltip=$(enc "$TOOLTIP")"
  api_file PUT "/3d/player?$q" "$2"
}
cmd_print_world() {
  local q="x=$1&y=$2&z=$3&dim=${6:-$DIM}"
  [ -n "${5:-}" ] && q="$q&facing=$5"
  [ -n "${LABEL:-}" ] && q="$q&label=$(enc "$LABEL")"
  api_file PUT "/3d/world?$q" "$4"
}
cmd_print_job() { api "/3d/world?id=$1"; }

# ── 子命令：通用逃生舱 ───────────────────────────────────────────
cmd_get()     { api "$1"; }
cmd_post()    { api_json POST "$1" "${2:-{\}}"; }
cmd_delete()  { api_json DELETE "$1" "${2:-{\}}"; }
cmd_put_file(){ api_file PUT "$1" "$2"; }
cmd_raw()     { req "$@"; echo; }
cmd_sse()     { exec curl -sN --max-time 86400 ${TOKEN:+-H "Authorization: $TOKEN"} "$BASE$1"; }

# ── 调度 ─────────────────────────────────────────────────────────
cmd="${1:-}"; shift || true
case "$cmd" in
  # 状态/性能
  status)                cmd_status ;;
  profiler)              cmd_profiler ;;
  lag)                   cmd_lag ;;
  sse-check)             cmd_sse_check ;;
  # 世界/方块
  world)                 cmd_world ;;
  blocks)                cmd_blocks ;;
  block)                 need 3 "$@"; cmd_block "$@" ;;
  fmp)                   need 3 "$@"; cmd_fmp "$@" ;;
  tile)                  need 1 "$@"; cmd_tile "$@" ;;
  setblock)              need 4 "$@"; cmd_setblock "$@" ;;
  batchsetblock)         cmd_batchsetblock ;;
  batchsetblock-wait)    cmd_batchsetblock_wait ;;
  job)                   need 2 "$@"; cmd_job "$@" ;;
  wait)                  need 1 "$@"; cmd_wait "$@" ;;
  # 物品/流体
  items)                 cmd_items ;;
  search)                need 1 "$@"; cmd_search "$@" ;;
  item)                  need 1 "$@"; cmd_item "$@" ;;
  item-by-id)            need 1 "$@"; cmd_item_by_id "$@" ;;
  item-icon)             need 1 "$@"; cmd_item_icon "$@" ;;
  ae-items-def)          cmd_ae_items_def ;;
  fluids)                cmd_fluids ;;
  fluid-containers)      cmd_fluid_containers ;;
  fluid-icon)            need 1 "$@"; cmd_fluid_icon "$@" ;;
  # 实体/区块
  entities)              cmd_entities ;;
  entities-filtered)     cmd_entities_filtered ;;
  entity)                need 1 "$@"; cmd_entity "$@" ;;
  chunks)                cmd_chunks ;;
  chunk)                 need 2 "$@"; cmd_chunk "$@" ;;
  chunk-map)             cmd_chunk_map "$@" ;;
  chunk-force)           cmd_chunk_force ;;
  chunk-load)            need 2 "$@"; cmd_chunk_load "$@" ;;
  chunk-unload)          need 2 "$@"; cmd_chunk_unload "$@" ;;
  # GT5
  gt5)                   need 3 "$@"; cmd_gt5 "$@" ;;
  gt5-start)             need 3 "$@"; cmd_gt5_start "$@" ;;
  gt5-stop)              need 3 "$@"; cmd_gt5_stop "$@" ;;
  gt5-batch)             cmd_gt5_batch ;;
  gt5-batch-wait)        cmd_gt5_batch_wait ;;
  gt5-scan)              need 2 "$@"; cmd_gt5_scan "$@" ;;
  # AE2
  ae)                    need 3 "$@"; cmd_ae "$@" ;;
  ae-items)              need 3 "$@"; cmd_ae_items "$@" ;;
  ae-cpu)                need 3 "$@"; cmd_ae_cpu "$@" ;;
  ae-nodes)              need 3 "$@"; cmd_ae_nodes "$@" ;;
  ae-nodes-bad)          need 3 "$@"; cmd_ae_nodes_bad "$@" ;;
  ae-me)                 need 3 "$@"; cmd_ae_me "$@" ;;
  ae-mes)                need 3 "$@"; cmd_ae_mes "$@" ;;
  ae-me-support)         cmd_ae_me_support ;;
  ae-craft)              need 5 "$@"; cmd_ae_craft "$@" ;;
  ae-craft-fluid)        need 5 "$@"; cmd_ae_craft_fluid "$@" ;;
  ae-cancel)             need 4 "$@"; cmd_ae_cancel "$@" ;;
  # 配方
  recipe|recipe-gt)      need 1 "$@"; cmd_recipe_gt "$@" ;;
  recipe-crafting)       need 1 "$@"; cmd_recipe_crafting "$@" ;;
  recipe-furnace)        need 1 "$@"; cmd_recipe_furnace "$@" ;;
  recipe-maps)           cmd_recipe_maps ;;
  recipe-gt-map)         need 1 "$@"; cmd_recipe_gt_map "$@" ;;
  # 3D 打印
  print-size)            need 1 "$@"; cmd_print_size "$@" ;;
  print-player)          need 2 "$@"; cmd_print_player "$@" ;;
  print-world)           need 4 "$@"; cmd_print_world "$@" ;;
  print-job)             need 1 "$@"; cmd_print_job "$@" ;;
  # 通用
  get)                   need 1 "$@"; cmd_get "$@" ;;
  post)                  need 1 "$@"; cmd_post "$@" ;;
  delete)                need 1 "$@"; cmd_delete "$@" ;;
  put-file)              need 2 "$@"; cmd_put_file "$@" ;;
  raw)                   need 2 "$@"; cmd_raw "$@" ;;
  sse)                   need 1 "$@"; cmd_sse "$@" ;;
  -h|--help|help|"")     usage ;;
  *)                     die "未知子命令：$cmd（$0 help 查看用法）" ;;
esac
