#!/usr/bin/env python3
"""把本项目所需的 Maven 依赖从 GTNH Nexus 同步到 CNB Maven 制品库。

流程:
  1. 读取依赖清单 (group<TAB>artifact<TAB>version, 由 export-deps.gradle 生成)
  2. 在源仓库 (nexus) 上列出该版本目录下的所有文件
  3. 下载文件 (优先复用本地 Gradle 缓存中同名文件, 可用 --no-cache 关闭)
  4. PUT 上传到目标仓库 (CNB Maven)

用法:
  python3 tools/sync-maven/sync.py                     # 同步
  python3 tools/sync-maven/sync.py --dry-run           # 只统计, 不上传
  python3 tools/sync-maven/sync.py --deps deps.tsv --jobs 8 --force
"""
import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from html import unescape
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

DEFAULT_SRC = "https://nexus.gtnewhorizons.com/repository/public"
DEFAULT_DST = "https://maven.cnb.cool/shirokasoke/love/-/packages/"
GRADLE_CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"

# 不需要同步的文件: 校验和由制品库自行生成, 签名与元数据无意义
SKIP_SUFFIX = (
    ".sha1", ".md5", ".sha256", ".sha512", ".sha256", ".asc",
    ".lastUpdated", ".repositories",
)
HREF_RE = re.compile(r'href="([^"]+)"')


class Http:
    def __init__(self, user, password, timeout=120, retries=3):
        self.timeout = timeout
        self.retries = retries
        self.auth = None
        if user:
            import base64
            self.auth = base64.b64encode(f"{user}:{password}".encode()).decode()

    def _headers(self, extra=None):
        h = {"User-Agent": "gtnh-maven-sync/1.0"}
        if self.auth:
            h["Authorization"] = f"Basic {self.auth}"
        if extra:
            h.update(extra)
        return h

    def request(self, url, method="GET", data=None, headers=None):
        last = None
        for attempt in range(self.retries):
            try:
                req = Request(url, data=data, method=method, headers=self._headers(headers))
                with urlopen(req, timeout=self.timeout) as resp:
                    return resp.status, resp.read()
            except HTTPError as e:
                # 4xx 是确定性结果, 不重试
                if 400 <= e.code < 500 and e.code not in (408, 429):
                    return e.code, b""
                last = e
            except Exception as e:  # URLError / timeout / socket error
                last = e
            time.sleep(1.5 * (attempt + 1))
        raise RuntimeError(f"{method} {url} 失败: {last}")

    def get(self, url):
        return self.request(url, "GET")

    def head(self, url):
        return self.request(url, "HEAD")[0]

    def head_size(self, url):
        """返回目标文件的大小(字节)。

        不存在返回 None; 存在但服务端未给出长度时返回 -1。
        """
        try:
            req = Request(url, method="HEAD", headers=self._headers())
            with urlopen(req, timeout=self.timeout) as resp:
                if resp.status != 200:
                    return None
                length = resp.headers.get("Content-Length")
                if length:
                    return int(length)
        except HTTPError:
            return None
        except Exception:
            return None

        # 部分制品库不返回 Content-Length, 用 Range 请求取总长度
        try:
            req = Request(url, headers=self._headers({"Range": "bytes=0-0"}))
            with urlopen(req, timeout=self.timeout) as resp:
                cr = resp.headers.get("Content-Range") or ""
                if "/" in cr:
                    total = cr.rsplit("/", 1)[-1]
                    if total.isdigit():
                        return int(total)
                length = resp.headers.get("Content-Length")
                if length:
                    return int(length)
        except Exception:
            pass
        return -1

    def put_file(self, url, path):
        data = path.read_bytes()
        return self.request(url, "PUT", data=data,
                            headers={"Content-Type": "application/octet-stream"})[0]

    def download(self, url, path):
        """流式下载到文件, 返回 (状态码, 实际字节数)。"""
        last = None
        for attempt in range(self.retries):
            try:
                req = Request(url, headers=self._headers())
                with urlopen(req, timeout=self.timeout) as resp:
                    total = 0
                    with open(path, "wb") as f:
                        while True:
                            chunk = resp.read(1 << 20)
                            if not chunk:
                                break
                            f.write(chunk)
                            total += len(chunk)
                    return resp.status, total
            except HTTPError as e:
                if 400 <= e.code < 500 and e.code not in (408, 429):
                    return e.code, 0
                last = e
            except Exception as e:
                last = e
            time.sleep(1.5 * (attempt + 1))
        raise RuntimeError(f"GET {url} 失败: {last}")


def group_path(group):
    return group.replace(".", "/")


def derive_browse(src):
    """从 .../repository/<repo> 推导 Nexus browse 索引地址。"""
    marker = "/repository/"
    if marker in src:
        host, repo = src.split(marker, 1)
        return f"{host}/service/rest/repository/browse/{repo.strip('/')}"
    return src


def list_files(http, browse_base, g, a, v):
    """通过 Nexus 的 browse 索引列出某个版本下的所有文件名; 目录不存在时返回 None。"""
    vdir = f"{group_path(g)}/{a}/{v}"
    url = f"{browse_base}/{vdir}/"
    status, body = http.request(url, "GET")
    if status != 200:
        return None
    text = body.decode("utf-8", "replace")
    items = []
    for row in re.split(r"<tr[^>]*>", text):
        m = HREF_RE.search(row)
        if not m:
            continue
        href = unescape(m.group(1)).strip()
        if f"{vdir}/" not in href:
            continue
        name = href.split(f"{vdir}/")[-1].split("?")[0]
        if not name or name.endswith("/"):
            continue
        if name.lower().endswith(SKIP_SUFFIX):
            continue
        size_m = re.search(r'align="right">\s*([0-9]+)', row)
        size = int(size_m.group(1)) if size_m else 0
        if name not in [i[0] for i in items]:
            items.append((name, size))
    return items


def cached_file(g, a, v, name):
    """在本地 Gradle 缓存中查找同名文件, 避免重复下载。"""
    d = GRADLE_CACHE / g / a / v
    if not d.is_dir():
        return None
    for p in d.rglob(name):
        if p.is_file() and p.stat().st_size > 0:
            return p
    return None


def verify_download_module(src_http, dst_http, g, a, v, src, browse, dst,
                           tmpdir, exclude_re, repair, state):
    """下载校验: 从目标仓库实际下载每个文件并比对大小, 可自动修复。"""
    gav = f"{g}:{a}:{v}"
    items = list_files(src_http, browse, g, a, v)
    if items is None:
        return gav, "NOT_ON_SOURCE", "源仓库中不存在该版本", 0
    vdir = f"{group_path(g)}/{a}/{v}"
    bad, ok, nbytes = [], 0, 0
    for name, size in items:
        if exclude_re and exclude_re.search(name):
            continue
        dst_url = f"{dst}/{vdir}/{name}"
        key = f"{vdir}/{name}"
        tmp = Path(tmpdir) / f"{g}_{a}_{v}_{name}"
        try:
            status, got = dst_http.download(dst_url, tmp)
        except Exception as e:
            status, got = -1, 0
            bad.append(f"{name}: 下载异常 {e}")
            continue
        finally:
            tmp.unlink(missing_ok=True)
        if status == 200 and (not size or got == size):
            ok += 1
            nbytes += got
            state[key] = size
            continue
        if not repair:
            bad.append(f"{name}(HTTP {status}, {got}/{size} 字节)")
            continue

        local = cached_file(g, a, v, name)
        if local is None:
            st, body = src_http.request(f"{src}/{vdir}/{name}", "GET")
            if st != 200:
                bad.append(f"{name}: 源下载失败 HTTP {st}")
                continue
            local = Path(tmpdir) / f"src_{g}_{a}_{v}_{name}"
            local.write_bytes(body)
        try:
            code = dst_http.put_file(dst_url, local)
        except Exception as e:
            bad.append(f"{name}: 重传异常 {e}")
            continue
        if code not in (200, 201, 204):
            bad.append(f"{name}: 重传失败 HTTP {code}")
            continue
        st2, got2 = dst_http.download(dst_url, tmp)
        tmp.unlink(missing_ok=True)
        if st2 == 200 and (not size or got2 == size):
            ok += 1
            nbytes += got2
            state[key] = size
        else:
            bad.append(f"{name}: 重传后仍异常 HTTP {st2}({got2}/{size} 字节)")

    if bad:
        return gav, "BAD", "; ".join(bad), nbytes
    return gav, "OK", f"{ok} 个文件校验通过", nbytes


def sync_module(src_http, dst_http, g, a, v, src, browse, dst, use_cache, force,
                tmpdir, dry_run, exclude_re, state):
    """同步单个模块, 返回 (gav, 状态, 详情, 已处理字节数)。"""
    gav = f"{g}:{a}:{v}"
    items = list_files(src_http, browse, g, a, v)
    if items is None:
        return gav, "NOT_ON_SOURCE", "源仓库中不存在该版本", 0
    if not items:
        return gav, "EMPTY", "源仓库版本目录下没有文件", 0

    vdir = f"{group_path(g)}/{a}/{v}"
    done, skipped, failed = [], [], []
    nbytes = 0
    for name, size in items:
        if exclude_re and exclude_re.search(name):
            skipped.append(name + " (已排除)")
            continue
        src_url = f"{src}/{vdir}/{name}"
        dst_url = f"{dst}/{vdir}/{name}"
        if not force and not dry_run:
            # 本地状态记录优先; 否则要求目标文件存在且大小一致才跳过
            key = f"{vdir}/{name}"
            prev = state.get(key)
            if prev is not None and (not size or prev == size):
                skipped.append(name)
                continue
            got = dst_http.head_size(dst_url)
            if got is not None and got != -1 and (not size or got == size):
                state[key] = size
                skipped.append(name)
                continue

        local = cached_file(g, a, v, name) if use_cache else None
        if local is None:
            if dry_run:
                done.append(name)
                nbytes += size
                continue
            status, body = src_http.request(src_url, "GET")
            if status != 200:
                failed.append(f"{name}: 下载失败 HTTP {status}")
                continue
            target = Path(tmpdir) / f"{g}_{a}_{v}_{name}"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(body)
            local = target
        if dry_run:
            done.append(name)
            nbytes += size
            continue

        try:
            code = dst_http.put_file(dst_url, local)
        except Exception as e:
            failed.append(f"{name}: 上传异常 {e}")
            continue
        if code in (200, 201, 204):
            done.append(name)
            nbytes += local.stat().st_size
            state[f"{vdir}/{name}"] = size
        elif code == 409:
            skipped.append(name + " (已存在)")
        else:
            failed.append(f"{name}: 上传返回 HTTP {code}")

    if failed:
        return gav, "FAILED", "; ".join(failed), nbytes
    if done:
        return gav, "SYNCED", f"{len(done)} 个文件: {', '.join(done)}", nbytes
    return gav, "SKIPPED", f"目标已存在 {len(skipped)} 个文件", nbytes


def verify_module(src_http, dst_http, g, a, v, browse, dst, exclude_re):
    """校验目标仓库中该模块的文件是否齐全且大小一致。"""
    gav = f"{g}:{a}:{v}"
    items = list_files(src_http, browse, g, a, v)
    if items is None:
        return gav, "NOT_ON_SOURCE", "源仓库中不存在该版本", 0
    vdir = f"{group_path(g)}/{a}/{v}"
    missing, mismatch, ok, nbytes = [], [], 0, 0
    for name, size in items:
        if exclude_re and exclude_re.search(name):
            continue
        got = dst_http.head_size(f"{dst}/{vdir}/{name}")
        if got is None:
            missing.append(name)
        elif got == -1 or not size:
            ok += 1
            nbytes += size
        elif got != size:
            mismatch.append(f"{name}(目标 {got}B != 源 {size}B)")
        else:
            ok += 1
            nbytes += size
    if missing:
        return gav, "MISSING", "缺失: " + ", ".join(missing), nbytes
    if mismatch:
        return gav, "MISMATCH", "; ".join(mismatch), nbytes
    return gav, "OK", f"{ok} 个文件完整", nbytes


def read_gavs(path):
    out = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            parts = line.split(":")
        if len(parts) >= 3:
            out.append((parts[0].strip(), parts[1].strip(), parts[2].strip()))
    return out


def main():
    ap = argparse.ArgumentParser(description="同步 Maven 依赖到 CNB Maven 制品库")
    ap.add_argument("--deps", default=str(Path(__file__).parent / "deps.tsv"), help="依赖清单文件")
    ap.add_argument("--extra", action="append", default=[], help="额外的依赖清单文件, 可重复")
    ap.add_argument("--src", default=DEFAULT_SRC, help="源 Maven 仓库地址")
    ap.add_argument("--browse", default=None,
                    help="源仓库的 Nexus browse 索引地址(用于列目录), 默认由 --src 推导")
    ap.add_argument("--dst", default=DEFAULT_DST, help="目标 Maven 仓库地址")
    ap.add_argument("--user", default="cnb", help="目标仓库用户名")
    ap.add_argument("--token-env", default="CNB_TOKEN,maven_TOKEN", help="读取令牌的环境变量名(逗号分隔)")
    ap.add_argument("--jobs", type=int, default=6, help="并发数")
    ap.add_argument("--dry-run", action="store_true", help="仅统计, 不下载不上传")
    ap.add_argument("--force", action="store_true", help="忽略目标仓库已存在的文件, 强制覆盖")
    ap.add_argument("--no-cache", action="store_true", help="不使用本地 Gradle 缓存, 全部从源仓库下载")
    ap.add_argument("--exclude", default=None,
                    help="按正则排除文件名, 例如 '(javadoc|sources)\\.jar$'")
    ap.add_argument("--verify", action="store_true",
                    help="只校验目标仓库的文件是否齐全(HEAD), 不上传")
    ap.add_argument("--verify-download", action="store_true",
                    help="从目标仓库逐个下载并比对大小做精确校验")
    ap.add_argument("--repair", action="store_true",
                    help="配合 --verify-download, 校验不通过时自动重新上传")
    ap.add_argument("--state", default=str(Path(__file__).parent / "state.json"),
                    help="已上传文件的本地状态记录, 用于避免重复上传")
    args = ap.parse_args()
    exclude_re = re.compile(args.exclude) if args.exclude else None

    token = None
    for name in args.token_env.split(","):
        token = os.environ.get(name.strip())
        if token:
            break
    if not token and not args.dry_run:
        sys.exit(f"未找到令牌, 请设置环境变量之一: {args.token_env}")

    gavs = read_gavs(args.deps)
    for extra in args.extra:
        gavs.extend(read_gavs(extra))
    # 去重保持顺序
    seen, uniq = set(), []
    for g in gavs:
        if g not in seen:
            seen.add(g)
            uniq.append(g)

    src = args.src.rstrip("/")
    browse = args.browse.rstrip("/") if args.browse else derive_browse(src)
    src_http = Http(None, None)  # 源仓库匿名访问, 不能带目标仓库的认证头
    dst_http = Http(args.user, token)
    print(f"源仓库: {src}  (索引: {browse})")
    print(f"目标仓库: {args.dst}")
    print(f"待处理模块: {len(uniq)}  并发: {args.jobs}  dry-run: {args.dry_run}")
    print("-" * 80)

    state_path = Path(args.state)
    state = {}
    if state_path.exists():
        try:
            state = json.loads(state_path.read_text(encoding="utf-8"))
        except Exception:
            state = {}

    tmpdir = tempfile.mkdtemp(prefix="maven-sync-")
    results = []
    dst = args.dst.rstrip("/")
    try:
        with ThreadPoolExecutor(max_workers=args.jobs) as pool:
            if args.verify:
                futures = {
                    pool.submit(verify_module, src_http, dst_http, g, a, v, browse,
                                dst, exclude_re): (g, a, v)
                    for g, a, v in uniq
                }
            elif args.verify_download:
                futures = {
                    pool.submit(verify_download_module, src_http, dst_http, g, a, v,
                                src, browse, dst, tmpdir, exclude_re, args.repair,
                                state): (g, a, v)
                    for g, a, v in uniq
                }
            else:
                futures = {
                    pool.submit(sync_module, src_http, dst_http, g, a, v, src, browse,
                                dst, not args.no_cache, args.force,
                                tmpdir, args.dry_run, exclude_re, state): (g, a, v)
                    for g, a, v in uniq
                }
            finished = 0
            for fut in as_completed(futures):
                finished += 1
                gav, status, detail, nbytes = fut.result()
                results.append((gav, status, detail, nbytes))
                print(f"[{finished}/{len(uniq)}] {status:14s} {gav}  {detail}")
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)
        if state:
            tmp_state = state_path.with_suffix(".json.tmp")
            tmp_state.write_text(json.dumps(state, sort_keys=True, indent=0),
                                 encoding="utf-8")
            tmp_state.replace(state_path)

    summary = {}
    total = 0
    for _, status, _, nbytes in results:
        summary[status] = summary.get(status, 0) + 1
        total += nbytes
    print("-" * 80)
    print("汇总:", "  ".join(f"{k}={v}" for k, v in sorted(summary.items())))
    print(f"处理数据量: {total / 1024 / 1024:.1f} MB")

    if args.verify_download:
        report_name = "verify-download-report.txt"
    elif args.verify:
        report_name = "verify-report.txt"
    else:
        report_name = "report.txt"
    report = Path(__file__).parent / report_name
    with report.open("w", encoding="utf-8") as f:
        for gav, status, detail, nbytes in sorted(results):
            f.write(f"{status}\t{gav}\t{detail}\n")
    print(f"详细报告: {report}")


if __name__ == "__main__":
    main()
