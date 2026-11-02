"""多线程分段下载 AList（GitHub 直连不通，走 gh-proxy 镜像）。"""
import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

URL = os.environ.get(
    "ALIST_URL",
    "https://gh-proxy.com/https://github.com/AlistGo/alist/releases/download/v3.66.0/alist-windows-amd64.zip",
)
OUT = sys.argv[1] if len(sys.argv) > 1 else "alist.zip"
WORKERS = 8
CHUNK = 2 * 1024 * 1024

PROXIES = None
if os.environ.get("https_proxy") or os.environ.get("HTTPS_PROXY"):
    PROXIES = {
        "https": os.environ.get("https_proxy") or os.environ.get("HTTPS_PROXY"),
        "http": os.environ.get("http_proxy") or os.environ.get("HTTP_PROXY"),
    }


def build_opener():
    handlers = []
    if PROXIES:
        handlers.append(urllib.request.ProxyHandler(PROXIES))
    return urllib.request.build_opener(*handlers)


def probe():
    req = urllib.request.Request(URL, headers={"Range": "bytes=0-0"})
    with build_opener().open(req, timeout=30) as r:
        cr = r.headers.get("Content-Range", "")
        total = int(cr.split("/")[-1]) if "/" in cr else 0
        return total


def fetch(start, end, path):
    req = urllib.request.Request(URL, headers={"Range": f"bytes={start}-{end}"})
    with build_opener().open(req, timeout=120) as r:
        data = r.read()
    with open(path, "wb") as f:
        f.write(data)
    return len(data)


def main():
    total = probe()
    if not total:
        print("ERROR: 服务器未返回 Content-Range，无法分段")
        return 1
    print(f"总大小 {total / 1048576:.1f} MB，{WORKERS} 线程分段下载")

    tmpdir = OUT + ".parts"
    os.makedirs(tmpdir, exist_ok=True)
    ranges = [(s, min(s + CHUNK - 1, total - 1)) for s in range(0, total, CHUNK)]
    print(f"共 {len(ranges)} 段")

    done = 0
    started = time.time()
    failed = []
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = {
            pool.submit(fetch, s, e, os.path.join(tmpdir, f"{i:04d}")): (i, s, e)
            for i, (s, e) in enumerate(ranges)
        }
        for fut in as_completed(futures):
            i, s, e = futures[fut]
            try:
                done += fut.result()
                speed = done / max(time.time() - started, 0.1) / 1024
                print(f"  {done / 1048576:5.1f}/{total / 1048576:.1f} MB  {speed:.0f} KB/s", flush=True)
            except Exception as exc:  # noqa: BLE001
                failed.append((i, s, e, exc))
                print(f"  段 {i} 失败: {exc}", flush=True)

    for i, s, e, _ in failed:
        fetch(s, e, os.path.join(tmpdir, f"{i:04d}"))
        print(f"  段 {i} 重试成功")

    with open(OUT, "wb") as out:
        for i in range(len(ranges)):
            with open(os.path.join(tmpdir, f"{i:04d}"), "rb") as part:
                out.write(part.read())
            os.remove(os.path.join(tmpdir, f"{i:04d}"))
    os.rmdir(tmpdir)

    size = os.path.getsize(OUT)
    print(f"完成: {OUT} {size / 1048576:.1f} MB，用时 {time.time() - started:.0f}s")
    return 0 if size == total else 1


if __name__ == "__main__":
    sys.exit(main())
