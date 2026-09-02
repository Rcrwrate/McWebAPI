#!/usr/bin/env bash
# 一键同步: 导出本项目解析到的依赖 -> 从 GTNH Nexus 同步到 CNB Maven 制品库
#
#   bash tools/sync-maven/run.sh                 # 增量同步
#   bash tools/sync-maven/run.sh --dry-run       # 只看统计
#   bash tools/sync-maven/run.sh --verify-download --repair   # 下载校验并修复
set -euo pipefail

cd "$(dirname "$0")/../.."

echo "==> 1/2 导出依赖清单"
DEPS_OUT="$PWD/tools/sync-maven/deps.tsv" \
  ./gradlew --init-script tools/sync-maven/export-deps.gradle help \
  --no-configuration-cache --console=plain -q | grep -E "EXPORTED|SKIPPED" || true

echo "==> 2/2 同步到目标仓库"
exec python3 tools/sync-maven/sync.py --extra tools/sync-maven/extras.tsv "$@"
