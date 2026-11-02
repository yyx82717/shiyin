"""下载并解压 Android 构建工具链：JDK17 / cmdline-tools / Gradle。
不依赖 Android Studio，也不需要管理员权限，全部解压到工程内 .toolchain/。
下载源已换成国内可达的镜像（Adoptium 官方源在国内会绕到 GitHub，极慢）。
"""
import os
import sys
import time
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.path.join(ROOT, ".toolchain")
DL = os.path.join(TC, "_dl")

JDK_URL = "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip"
CMDLINE_URL = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
GRADLE_URL = "https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip"


def log(msg):
    print(msg, flush=True)


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 1024 * 1024:
        log(f"[skip] 已存在 {os.path.basename(dest)} ({os.path.getsize(dest)/1048576:.1f} MB)")
        return dest
    tmp = dest + ".part"
    log(f"[get ] {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "shiyin-builder/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r, open(tmp, "wb") as f:
        total = int(r.headers.get("Content-Length") or 0)
        done = 0
        last = 0.0
        while True:
            chunk = r.read(1024 * 512)
            if not chunk:
                break
            f.write(chunk)
            done += len(chunk)
            now = time.time()
            if now - last > 3:
                last = now
                pct = (done * 100.0 / total) if total else 0
                log(f"       {done/1048576:8.1f} / {total/1048576:.1f} MB  ({pct:.0f}%)")
    os.replace(tmp, dest)
    log(f"[ok  ] {os.path.basename(dest)} {os.path.getsize(dest)/1048576:.1f} MB")
    return dest


def extract(zip_path, target_dir):
    if os.path.exists(target_dir):
        log(f"[skip] 目录已存在 {target_dir}")
        return target_dir
    os.makedirs(target_dir, exist_ok=True)
    log(f"[unz ] -> {os.path.basename(target_dir)}")
    with zipfile.ZipFile(zip_path) as z:
        z.extractall(target_dir)
    return target_dir


def main():
    os.makedirs(DL, exist_ok=True)

    # 1) JDK 17
    jdk_zip = download(JDK_URL, os.path.join(DL, "jdk17.zip"))
    jdk_dir = os.path.join(TC, "jdk")
    extract(jdk_zip, jdk_dir)
    inner = [d for d in os.listdir(jdk_dir) if d.lower().startswith("jdk")]
    if inner:
        src = os.path.join(jdk_dir, inner[0])
        for item in os.listdir(src):
            os.replace(os.path.join(src, item), os.path.join(jdk_dir, item))
        os.rmdir(src)
    log(f"[ok  ] JDK -> {jdk_dir}")

    # 2) Android cmdline-tools
    cl_zip = download(CMDLINE_URL, os.path.join(DL, "cmdline-tools.zip"))
    sdk_dir = os.path.join(TC, "android-sdk")
    extract(cl_zip, sdk_dir)
    ct_root = os.path.join(sdk_dir, "cmdline-tools")
    latest = os.path.join(ct_root, "latest")
    if not os.path.exists(latest) and os.path.exists(os.path.join(ct_root, "bin")):
        os.makedirs(latest, exist_ok=True)
        for item in os.listdir(ct_root):
            if item == "latest":
                continue
            os.replace(os.path.join(ct_root, item), os.path.join(latest, item))
    log(f"[ok  ] cmdline-tools -> {latest}")

    # 3) Gradle
    g_zip = download(GRADLE_URL, os.path.join(DL, "gradle-8.9-bin.zip"))
    extract(g_zip, os.path.join(TC, "gradle"))
    log(f"[ok  ] Gradle -> {os.path.join(TC, 'gradle')}")

    log("ALL_DONE")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001
        log(f"FAILED: {type(e).__name__}: {e}")
        sys.exit(1)
