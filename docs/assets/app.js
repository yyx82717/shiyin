/* 拾音 ShiYin · 站点脚本
 * 使用说明：把下面一行的 REPO 改成你的 GitHub 仓库（所有者/仓库名），
 * 例如 const REPO = "shiyin/shiyin-android";
 * 改完后，导航里的 GitHub 链接、下载页的「最新版本」和「下载按钮」会自动指向你的仓库。
 */
const REPO = "yyx82717/shiyin";

const GH = {
  url: () => `https://github.com/${REPO}`,
  releases: () => `https://github.com/${REPO}/releases`,
  releasesApi: () => `https://api.github.com/repos/${REPO}/releases/latest`,
  raw: (p) => `https://raw.githubusercontent.com/${REPO}/main/${p}`,
  issues: () => `https://github.com/${REPO}/issues`,
};

// 设置所有带 data-gh 的链接
function applyRepoLinks() {
  if (REPO.includes("YOUR_USERNAME")) return; // 还没配置
  document.querySelectorAll("[data-gh]").forEach((el) => {
    const kind = el.getAttribute("data-gh");
    const fn = GH[kind] || GH.url;
    el.href = fn();
    el.classList.remove("need-config");
  });
}

// 未配置 REPO 时显示提示条
function showConfigBanner() {
  if (!REPO.includes("YOUR_USERNAME")) return;
  const b = document.createElement("div");
  b.className = "banner";
  b.innerHTML =
    '站点已就绪，但还没填写 GitHub 仓库名 —— 编辑 <code>docs/assets/app.js</code> 第 6 行的 <code>REPO</code> 即可自动接入下载与链接。';
  document.body.prepend(b);
}

/* ===== 下载页：拉取 GitHub Releases 最新版 ===== */
function fmtSize(bytes) {
  if (!bytes) return "";
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + " MB";
  return (bytes / 1024).toFixed(0) + " KB";
}
function fmtDate(iso) {
  if (!iso) return "";
  try { return new Date(iso).toLocaleDateString("zh-CN"); } catch (e) { return ""; }
}

async function loadLatestRelease() {
  const badge = document.getElementById("dlVersion");
  const sizeEl = document.getElementById("dlSize");
  const dateEl = document.getElementById("dlDate");
  const btn = document.getElementById("dlPrimaryBtn");
  const note = document.getElementById("dlLatestNote");

  // 未配置仓库：保留静态占位信息，并提示
  if (REPO.includes("YOUR_USERNAME")) {
    if (note) note.style.display = "block";
    return;
  }

  try {
    const res = await fetch(GH.releasesApi(), {
      headers: { Accept: "application/vnd.github+json" },
    });
    if (!res.ok) throw new Error("http " + res.status);
    const r = await res.json();
    const asset =
      (r.assets || []).find((a) => /\.apk$/i.test(a.name)) || (r.assets || [])[0];
    const version = (r.tag_name || "").replace(/^v/i, "");
    const date = fmtDate(r.published_at);
    const size = fmtSize(asset && asset.size);

    if (badge) {
      badge.innerHTML = '最新版本 <b>v' + (version || r.tag_name || "") + "</b>" +
        (date ? ' · ' + date : "");
    }
    if (sizeEl && size) sizeEl.textContent = size;
    if (dateEl && date) dateEl.textContent = date;
    if (btn) {
      if (asset && asset.browser_download_url) {
        btn.href = asset.browser_download_url;
        btn.textContent = "下载 v" + (version || "") + "（APK）";
      } else {
        btn.href = GH.releases();
        btn.textContent = "前往 GitHub Releases 下载";
      }
    }
    if (note) note.style.display = "none";
  } catch (e) {
    // 拉取失败（限流/网络/未配置）：保留静态占位信息
    if (note) note.style.display = "block";
  }
}

document.addEventListener("DOMContentLoaded", () => {
  showConfigBanner();
  applyRepoLinks();
  if (document.getElementById("dlPrimaryBtn")) loadLatestRelease();

  // 移动端菜单
  const toggle = document.querySelector(".menu-toggle");
  const links = document.querySelector(".nav-links");
  if (toggle && links) {
    toggle.addEventListener("click", () => links.classList.toggle("open"));
  }
});
