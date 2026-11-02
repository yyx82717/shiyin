# 拾音 ShiYin

一款原生 Android 音乐播放器：本地库、自建服务器、网盘、开放曲库四个来源统一成一个曲库，支持搜索、后台播放、锁屏控制、收藏与播放历史。

**关于「抓取全平台音乐」**：本应用不抓取 QQ 音乐 / 网易云 / 酷狗等平台的音频文件。那属于绕过技术措施 + 侵犯信息网络传播权，非商业用途也不构成豁免。作为替代，它提供了四条合法且真正能覆盖「全平台」的通路 —— 详见第三节。

---

## 一、你拿到的东西

| 文件 | 说明 |
| --- | --- |
| `dist/拾音-ShiYin-1.6.0-release.apk` | **推荐安装这个**（不可调试，体积更小） |
| `dist/拾音-ShiYin-1.6.0.apk` | debug 包（含调试信息，出问题时用这个抓日志） |
| `app/src/main/java/com/shiyin/player/` | 全部 Kotlin 源码 |
| `tools/build_apk.sh` | 一行命令重新打包 |
| `tools/setup_toolchain.py` | 自动下载 JDK17 + Android SDK + Gradle（已执行过） |
| `.toolchain/` | 自建工具链，约 1.5 GB，**可以删**，删了重新跑 setup 脚本即可 |

> 构建状态：`testDebugUnitTest` / `assembleDebug` / `assembleRelease` / `lintDebug` 全部通过，**219 个单元测试 0 失败，lint 0 error**。

### 装到手机

1. 把 APK 传到手机（微信/QQ/数据线都行），点开安装，允许「未知来源」。
2. 或者用数据线：`adb install -r dist/拾音-ShiYin-1.6.0-release.apk`
3. 首次打开会申请通知权限 —— 同意才能看到通知栏播放控制（拒绝也能正常播放）。
4. 想听手机里的歌，到「设置 - 音源 - 本地音乐 - 授权并扫描」。

> 系统要求：Android 7.0（API 24）及以上。唯一例外是拼音搜索，它依赖系统的 ICU 音译器（API 29 起才有），Android 9 及以下用不了，搜索框里会直接说明。

---

## 二、功能

- **发现**：顶部是 **10 张内置华语风格专辑**（国风、民谣、摇滚、粤语经典、影视原声、都市情歌、KTV、独立新声、说唱、童年校园，共 118 首），不联网也能看；下面是开放曲库的曲风推荐（电子/嘻哈/低保真/氛围/浩室/爵士/摇滚/古典/流行）。
- **搜索**：输入即搜（420 ms 防抖），**先查内置华语精选，再查本地库 + 自建服务器 + 网盘 + 开放曲库**，结果分区显示并标注来源；搜不到时可一键跳到官方平台。
- **拼音搜索**：不用切输入法。打 `zjl` 出周杰伦、`dfp` 出东风破、`jielun` 也能中（首字母前缀 > 首字母包含 > 全拼包含，三档排序）。转换用系统自带的 ICU 音译器（需要 Android 10 及以上，更老的系统只是少这一项，别的照旧），不额外打包几百 KB 的拼音表；结果单列一段「拼音匹配」，不会和网盘、开放曲库混在一起。
- **搜索历史**：写进搜索框却没搜到的半截词不算；空搜索时列出最近 12 条，点一下复搜，也能一键清空。
- **我的**：歌单 / 收藏 / 最近播放 / **本地曲库** / **已下载** / **网盘** / **百度网盘**，七个 tab；本地库支持「只看华语」和删除原文件，网盘页展示 WebDAV 扫到的全部歌曲，百度网盘页是直连的结果。
- **文件夹浏览**：本地 / 网盘 / 百度三处都能切「按文件夹」——按真实目录逐层进，带面包屑、返回键回上一级；点目录右边 ▶ 播放整个子树，点单曲则从这个目录接着放下一首。网盘里几百首平铺成一列时，这是除搜索之外唯一的抓手。
- **默认列表**：**播放过的歌自动收进「默认列表」**，不用先建歌单；最多留 300 首，可一键清空。
- **一键加入播放列表**：搜索结果、本地曲库、精选专辑、播放页都有 ➕，弹窗里可直接选默认列表或任意自建歌单，也能当场新建。
- **歌单**：新建、重命名、删除、加歌、移除、整单播放。
- **歌单导入导出**：导出成标准 `.m3u8`（带 `#EXTINF` 的时长与「艺人 - 歌名」），用系统文件选择器选存放位置，不申请存储权限；导入时按「地址 → 文件名 → 艺人+歌名 → 歌名」四级匹配回曲库，**歌名这一级只在库里唯一时才认**，最后告诉你「对上 12 首，3 首不在曲库里」。换机、刷机、卸载重装前先导一份。
- **歌词**：播放页封面下滑切到歌词，逐行高亮并自动滚动，点某行直接跳到该句。支持 **逐字歌词**（增强 LRC `<00:12.30>` 与 TTML）、**双语对照**（原文 + 译文，或音译）。同目录放同名 `.lrc` / `.ttml` 会自动读取，WebDAV 上的同名歌词也会去取；匹配错了就点「搜歌词」在线挑一条，也可以直接粘贴 TTML。
- **离线下载**：播放页右上角下载按钮，带环形进度、可取消、失败可重试；下完的曲子播放时自动走本地文件，不再消耗流量。
- **播放页**：封面高斯模糊背景、进度拖动、随机播放、单曲循环/列表循环、收藏。
- **待播队列**：播放页右下角队列图标（那行「队列 12 首」的小字也能点）打开完整队列 —— 当前这首高亮、后面还有几首一目了然；点任意一行直接跳过去播，每行右侧菜单可「上移 / 下移 / 从队列移除 / 清空它后面的全部」，整条队列还能一键清空待播。搜索结果里的「加入歌单」弹窗顶部也多了「插到下一首播放」，不用建歌单就能插队。
- **接着上次听**：队列、第几首、听到哪儿都会落盘（每 10 秒最多写一次，只写变化），重开应用原样回来 —— 但**不会自己出声**，要按一下播放键。超过 30 天的现场自动丢弃。
- **音质标注**：曲目行的艺人下面挂一枚小徽章 —— `FLAC · 无损` 用主题色描边，`MP3 · 高品` / `标准` 用灰阶。本地库读 MediaStore 的真实码率，网盘按容器判断（拿不到码率就不瞎标）。
- **音频体检**（播放设置面板底部）：点「检查文件头」，App 读前 16KB 直接告诉你这个 `.wav` 到底是不是它自称的格式。网盘上不少「车载 CD」WAV 其实是 DTS / AC-3 码流塞在 `.wav` 里，手机没有对应解码器，放出来就是沙沙声 —— 以前只能靠猜，现在点一下就有结论。
- **播放内核**（播放页右下角「调节」图标）：**10 段均衡器**（31Hz–16kHz，±12dB，8 个预设、可手调，自研 PCM 处理器，不依赖设备自带的 5 段）、**倍速** 0.75–2.0×（变速不变调）、**睡眠定时**（15/30/45/60/90 分钟或「播完这首」，到点先淡出再暂停）、**淡入淡出**（起播与切歌从静音推上来，暂停前先降下来）。
- **后台播放**：Media3 `MediaSessionService`，退后台、锁屏、切 App 都不中断；通知栏与锁屏完整控制；来电或拔耳机自动暂停。
- **放不出来就自动跳过**：网盘直链过期、文件损坏、手机没有对应解码器（就是那些 DTS 伪装成 `.wav` 的文件）时，自动跳下一首并说明原因；连着三首都放不出来才停下 —— 那多半是断网，再跳也没意义。
- **放不出来就自动跳过**：网盘直链过期、文件损坏、手机没有对应解码器（就是那些 DTS 伪装成 `.wav` 的文件）时，自动跳下一首并说明原因；连着三首都放不出来才停下 —— 那多半是断网，再跳也没意义。

- **自动更新（小圈子分发友好）**：设置里填一个「更新源地址」（局域网 / 公网都行），把 `update.json` 和新版 apk 放进去，应用**启动自动检查，发现更高版本才弹窗**——绝不静默装。下载走系统 `DownloadManager`（进度通知、断点续传，落应用私有目录，装完不留垃圾），安装走系统安装器并经由 `FileProvider` 分发；Android 8+ 会自动引导你开「安装未知应用」权限。清单带 `sha256` 时严格校验，下错或被篡改直接拒装；「跳过此版本」记下 `versionCode` 后自动检查不再弹它。把地址删空即关闭更新。
- **本地曲库不再每次重扫**：扫描结果落盘成快照，重开应用立刻能看到上次的歌，后台再静默补扫一次。
- **设置**：外观主题 + 四个音源的独立开关与连接测试。

---

## 三、五条音源通路（重点）

| 音源 | 覆盖什么 | 怎么开 |
| --- | --- | --- |
| **本地音乐** | 手机里所有 mp3/flac/m4a/wav/ogg/ape… | 「设置 - 本地音乐 - 授权并扫描」，一次授权即可 |
| **百度网盘（直连）** | 你自己网盘里的音乐目录，**不需要 AList、不需要任何设备常开** | 「一键授权登录」点一下就行，见下方「百度网盘直连」 |
| **自建曲库（Subsonic）** | 你自己的完整音乐收藏，放在 NAS / 树莓派 / 电脑上 | 填服务器地址 + 账号密码，见下方示例 |
| **网盘音乐（WebDAV）** | 坚果云、Nextcloud、群晖，或用 AList 把阿里云盘 / 夸克 / OneDrive 挂进来 | 填 WebDAV 目录地址 + 账号 |
| **Audius / Jamendo** | 开放授权曲库，无需任何配置 | 内置 / 填 client_id |

> 百度网盘没有 WebDAV，所以它**不在 WebDAV 那一栏**。填了也连不上，去看「百度网盘 · 直连」。

### Navidrome / Airsonic 示例

```
服务器地址: http://192.168.1.10:4533
用户名:    你的 Navidrome 账号
密码:      对应密码
```

认证走 Subsonic 官方的 token 方案（`t = md5(password + salt)`），**密码不会随请求发出**。点「保存并测试」会返回服务端版本号，确认真的连上了。

### 百度网盘直连

**为什么不是 WebDAV**：百度网盘没有 WebDAV 接口，而 WebDAV 那一栏必须有中间层（AList）把网盘翻译成标准协议。中间层跑在一台设备上，那台设备一关机，手机就哑了。

这个源换了思路 —— **直接说百度 App 自己的语言**。手机就是客户端，读的是你自己的网盘目录，整条链路没有任何需要常开的服务器。

```
设置 → 音源 → 百度网盘 · 直连 → 点「一键授权登录」
登录百度账号并同意授权 → 自动返回，App 自己抓授权码、换 token、读目录
```

**手动填也可以，而且两种凭据都收**：百度页面上的 **32 位授权码**，或者 `python tools/baidu_token.py`
生成的一长串 **refresh_token**（`122.` 开头），粘进同一个框，点「保存并读取」。App 先看形状
（32 位字母数字 = 授权码，60 字符以上 = refresh_token），是授权码就当场换 token。

> 1.4.1 的坑：百度那串授权码是放在**只读输入框**里的，而当时的抓取脚本只读页面正文
> （`innerText` 不含 input 的 value），所以「一键授权」看着点了没反应。现在的脚本先扫
> `input/textarea` 的 value，并用「页面上出现『授权码』三个字」当开关，避免把登录页的
> 隐藏域当成授权码。万一还是没抓到，点页面上的「复制授权码」，再点底部「从剪贴板读取」。

> 一键授权走 WebView，加载的是 `openapi.baidu.com` 的官方授权页，密码不经过 App。

**两条通道的差别**（这一步踩过坑，记下来）：

| 接口 | 参数 | 实测速度 |
| --- | --- | --- |
| 开放平台 `/rest/2.0/xpan/multimedia?method=filemetas` | 标准参数 | 80 KB/s，部分文件直接 403 |
| 网盘 App 播放通道 `/api/mediainfo` | `media=1&clienttype=1&channel=android_15_…&origin=dlna` | **1.3–3.4 MB/s** |

开放平台签发的直链带 `origin_appid`（第三方应用标识），百度据此限速；App 通道不带，走的是「边下边播」队列。AList 的 `crack_video` 同样是这个路子。

**三个必须知道的限制**：

- `refresh_token` 是**一次性**的，每次换 access_token 都会轮换，旧的立刻作废。App 会马上存新的；但**不要同时让两处用同一个 token**（比如 App 和 AList 的 `/baidu`），否则互相踢下线 —— 被踢之后百度回的是 `{"error":"Trigger security policy"}`，HTTP 400，看起来像「服务器出错」，实际是授权已失效。**本机已把 AList 的 `/baidu` 停用**，百度只归 App 使用。
- 直链 8 小时过期，所以列表里存的是 `baidu://<fs_id>/<路径>`，播放那一刻才换成真实地址。歌单、收藏、快照里都不会有过期链接。
- 授权失败会进入 5 分钟冷却（`BaiduSource.AUTH_COOLDOWN_MS`），期间不再发请求，避免反复重试把百度的限流拖长。改 token 或重新授权会立刻清掉冷却。

### AList / 网盘示例

1. 部署 AList，添加存储（百度网盘、阿里云盘、夸克、OneDrive 都行），确认音乐目录在网页端能打开。
2. AList 的 WebDAV 地址固定是 `http://<主机>:5244/dav`。
3. 在 App 里填：

```
目录地址: http://192.168.1.10:5244/dav/音乐/
用户名:   AList 账号
密码:     AList 密码
```

点「保存并扫描根目录」会返回根目录下的文件夹数和音频文件数。正式扫描时逐层递归（最多 150 个目录 / 3000 首 / 6 层深，防止把大网盘拖死）。遍历结果**落盘成快照**，之后打开「网盘」页直接读快照，不再重扫；快照超过 30 分钟才在后台静默补一次。

> **这台电脑已经部署好了**：AList v3.66.0 装在 `C:\AList`，后台 http://127.0.0.1:5244 ，已挂载两个库：`F:\KwDownload\song`（酷我下载目录，543 首，其中 56 首带 `.lrc`）和百度网盘的 `/我的音乐`（299 首）。App 里的地址**统一填 `http://192.168.8.192:5244/dav`**，一次收两个库共 842 首，账号 `admin`。局域网 IP 变了就同步改一下。完整说明见 `AList-部署说明.md`。

> **手机端的「本地」和电脑上的 F 盘是两回事**。`F:\KwDownload\song` 是电脑硬盘上的目录，手机 App 读不到，只能通过 AList 的 WebDAV 进来 —— 也就是「我的 → 网盘」那个 tab。App 里的「本地」只扫手机自己的存储（系统媒体库里的音频），两边互不相通。想少走网络就把文件用数据线拷到手机 `Music/` 目录，再点「本地 - 重新扫描」。

### 搜不到的歌怎么办

搜索页底部有「把关键词交给官方平台」：QQ音乐 / 网易云 / 酷狗，点一下跳到官方 App 或网页去播。**只跳转，不抓取任何音频**。

---

## 四、歌词、离线下载、歌单

### 歌词

优先级：**本地同名 `.lrc` → 磁盘缓存 → LRCLIB 在线 → lyrics.ovh 兜底**。

- LRCLIB（`lrclib.net`）是开放歌词库，免密钥，直接返回带时间轴的 LRC。先用 `/api/get` 做 `艺术家+曲名+时长` 精确匹配，失败再走 `/api/search` 模糊搜索，候选里挑时长最接近的那条（避免拿到同名不同版本）。
- 本地曲目会在音频同目录找 `歌名.lrc`。国内很多 lrc 是 GBK 编码，用 UTF-8 读会出现替换字符，代码里检测到 `\uFFFD` 就自动回退 GBK。
- 拿到结果后写进 `filesDir/lyrics/<md5>.json` 缓存，**找不到也缓存**，避免每次打开播放页重复请求。
- 歌词请求用单独的短超时客户端（8 秒），网络差的时候不会让界面白等半分钟。

### 手动搜歌词（1.3.0）

自动匹配是按「歌名 + 艺人 + 时长」猜的，同名的现场版、翻唱、伴奏都会撞车。播放页歌词面板右上角有「搜歌词」：

- 默认用「歌名 艺人」去 LRCLIB 搜，结果列表标出**歌手、时长、有没有时间轴**，挑一条即刻生效；
- 想精确一点就写「歌手 - 歌名」，会拆开后先查 LRCLIB，再兜 lyrics.ovh；
- 网络搜不到还能切到「粘贴 LRC」，把手里的 `.lrc` 内容贴进去，带 `[00:12.34]` 的会跟着播放滚动；
- 选中的歌词写进同一份磁盘缓存，下次打开这首歌直接用它。

### 离线下载

- 文件存在应用私有目录 `filesDir/downloads`，**不需要存储权限**，卸载 App 一起清掉。
- 下载协程挂在 Application 级 scope 上，切页面、退后台都不中断；进程被系统杀掉才会停。
- 下完立即刷新索引，播放时 `streamUrl` 自动替换成本地文件路径，不再走网络。
- 下载时沿用了播放器那张「主机 → Authorization」表，**所以 WebDAV 网盘上的歌也能离线**（这点很容易漏，第一版就漏了，已修）。

### 歌单

新建 / 重命名 / 删除 / 加歌 / 移除 / 整单播放。曲目按 id 去重，加歌时已经在里面的会跳过。歌单和收藏、历史一起存在 DataStore 里。

---

## 五、工程结构

```
app/src/main/java/com/shiyin/player/
├── ShiYinApp.kt                  Application，持有 settings / repository，同步网盘鉴权表
├── MainActivity.kt               入口，edge-to-edge + 主题分发
├── data/
│   ├── model/
│   │   ├── Track.kt              曲目模型 + 与 MediaItem 的双向转换
│   │   ├── Playlist.kt           歌单
│   │   └── DownloadEntry.kt      已下载条目
│   ├── remote/
│   │   ├── Http.kt               OkHttp 客户端（含 PROPFIND）
│   │   ├── JsonExt.kt            容错 JSON 取值
│   │   ├── RemoteAuth.kt         播放器用的「主机 → Authorization」表
│   │   └── Crypto.kt             MD5
│   ├── source/
│   │   ├── MusicSource.kt        音源接口（扩展点）
│   │   ├── LocalSource.kt        MediaStore 扫描 + 专辑封面提取 + 删除原文件
│   │   ├── SubsonicSource.kt     Subsonic / Navidrome 协议（网络层）
│   │   ├── SubsonicJson.kt       响应解析（纯函数，可测）
│   │   ├── WebDavSource.kt       PROPFIND 递归遍历（网络层）
│   │   ├── DavXml.kt             multistatus 解析 + 文件名拆分（纯函数，可测）
│   │   ├── AudiusSource.kt       开放曲库（网络层）
│   │   ├── AudiusJson.kt         响应解析（纯函数，可测）
│   │   └── JamendoSource.kt      CC 授权曲库
│   ├── curated/
│   │   ├── CuratedCatalog.kt     内置 10 张华语风格专辑（只有元数据，不含音频/歌词）
│   │   └── SongMatch.kt          歌名归一化与本地曲库匹配（纯函数，可测）
│   ├── repository/MusicRepository.kt  多源聚合，单源失败不影响其它源
│   ├── lyrics/
│   │   ├── Lyrics.kt             LRC 解析（多时间轴标签、毫秒补齐）+ 纯文本切行
│   │   ├── Sidecar.kt            网盘同目录 .lrc 地址推算（纯函数，可测）
│   │   ├── LyricsSearch.kt       歌词候选模型 + LRCLIB 搜索解析 + 手动粘贴解析（纯函数，可测）
│   │   └── LyricsRepository.kt   本地 lrc / LRCLIB / lyrics.ovh / 磁盘缓存 / 手动指定
│   ├── download/
│   │   ├── Downloader.kt         流式下载、进度、取消、重试、网盘鉴权
│   │   └── Downloads.kt          已下载索引（供播放时替换本地路径）
│   └── local/
│       ├── SettingsStore.kt      收藏/历史/歌单/下载/主题/各源配置（DataStore 持久化）
│       └── LocalLibraryStore.kt  本地曲库快照（JSON 落盘，冷启动秒开）
├── playback/PlaybackService.kt   Media3 播放服务 + 按主机注入认证头
└── ui/
    ├── AppRoot.kt                底部导航 + 迷你播放器 + 全屏播放页
    ├── PlatformLinks.kt          官方平台跳转
    ├── home/  search/  settings/         发现 / 搜索 / 设置
    ├── curated/
    │   ├── CuratedAlbumScreen.kt          华语专辑详情（本地可播 / 跳官方平台）
    │   └── CuratedSongRow.kt              精选曲目行 + 平台选择弹窗（专辑页与搜索页共用）
    ├── library/
    │   ├── LibraryScreen.kt               歌单、收藏、历史、本地、已下载
    │   └── PlaylistDetailScreen.kt        歌单详情
    ├── player/
    │   ├── PlayerViewModel.kt             播放控制 + 歌词 + 下载状态
    │   ├── PlayerScreen.kt                全屏播放页
    │   ├── LyricsPanel.kt                 滚动歌词
    │   └── LyricsSearchDialog.kt          在线搜歌词 / 粘贴 LRC
    ├── components/               封面、胶囊标签、列表行、迷你播放器、对话框
    └── theme/                    深/浅色主题

app/src/test/java/com/shiyin/player/    单元测试（JVM，不需要设备）
├── data/curated/CuratedCatalogTest.kt
├── data/curated/SongMatchTest.kt
├── data/lyrics/LrcParserTest.kt
├── data/lyrics/SidecarTest.kt
├── data/lyrics/LyricsSearchTest.kt
├── data/local/LocalSnapshotTest.kt
├── data/model/PlaylistOpsTest.kt
├── data/source/LocalQueryTest.kt
├── data/remote/UrlCodecTest.kt
├── data/source/DavXmlTest.kt
├── data/source/SubsonicJsonTest.kt
├── data/source/AudiusJsonTest.kt
├── data/source/JamendoTagsTest.kt
└── data/download/DownloadNamingTest.kt
```

技术栈：Kotlin 2.0.21 · Jetpack Compose (BOM 2024.10.01) · Media3 1.4.1 · OkHttp 4.12 · kotlinx.serialization · Coil · DataStore。minSdk 24 / targetSdk 35。

### 三个实现上值得留意的点

1. **本地封面**：Android 10 起 MediaStore 不再暴露专辑封面路径，只能用 `loadThumbnail` 取缩略图。所以扫描时按专辑逐个提取、压缩成 jpg 缓存到 `cacheDir/local_art/`，避免滚动列表时反复解码。
2. **WebDAV 的认证头**：ExoPlayer 的 `MediaItem` 没法带自定义请求头。解法是 `ResolvingDataSource` —— 播放前按 URL 主机名查 `RemoteAuth` 表，只有命中的主机（也就是你自己的网盘）才加 `Authorization`，不会把密码带给 Audius 这类第三方。下载器自己也查同一张表，否则网盘上的歌能播不能下。
3. **歌词滚动不抖**：`LazyColumn` 里每帧都 `animateScrollToItem` 会让用户手动滑动立刻被拽回去。做法是只在「当前行索引变化」时才滚动，且用户手指按住列表期间暂停自动滚动。

---

## 六、自己重新编译

### 方式 A：命令行（本机已可用，不需要 Android Studio）

```bash
bash tools/build_apk.sh            # 出 debug 包
bash tools/build_apk.sh assembleRelease
```

产物在 `app/build/outputs/apk/debug/`。

> 工程目录名含中文（`音乐软件`），AGP 默认会拒绝构建，已在 `gradle.properties` 里加了 `android.overridePathCheck=true` 放行。
> 如果哪天 aapt2 报路径编码错误，把工程挪到纯英文路径（例如 `C:\dev\shiyin`）再构建即可，代码本身与路径无关。

### 跑单元测试

```bash
bash tools/build_apk.sh test
```

129 个用例，覆盖 LRC 解析、WebDAV XML、Subsonic/Audius JSON、URL 拼装、MD5 token、下载命名、
歌单去重与默认列表、华语专辑数据完整性、歌名归一化匹配、歌词候选解析、本地快照序列化、
网盘旁挂歌词的 URL 推算（含中文百分号编码、查询串剥离、隐藏文件名等边界）、
快照新鲜度判断（阈值边界、时钟回拨）、百度直连的 URL 构造与脏文件名拆分、
OAuth 授权地址与授权码提取、百度错误码到中文提示的映射。
不依赖设备或模拟器，跑完看 `app/build/reports/tests/testDebugUnitTest/index.html`。

> **一个必须知道的坑**：`gradle.properties` 里的 `org.gradle.jvmargs` **不能**加 `-Dfile.encoding=UTF-8`。
> 加了之后 Gradle 用 UTF-8 写测试进程的 classpath 参数文件，而 worker JVM 按系统本地编码（中文 Windows 是 GBK）去读，
> 工程路径里的中文就变成乱码，**所有测试都会 ClassNotFoundException**，看起来像代码坏了，实际一行没错。
> 编译 APK 不受影响，只有跑测试会撞上。

### 方式 B：Android Studio

`File → Open` 选择本目录，等 Gradle Sync 完成后直接 Run。若提示 SDK 版本，选 Android 14/15 SDK（API 35）。

---

## 七、接一个新音源

只要实现 `MusicSource` 并在 `MusicRepository` 里注册，界面层一行都不用改：

```kotlin
class MySource : MusicSource {
    override val id = "myserver"
    override val label = "我的曲库"
    override val note = "自建服务器"

    override suspend fun feed(genre: String?, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val json = Http.get("https://your-server.com/api/feed?limit=$limit")
        // 解析成 List<Track>，streamUrl 填音频直链即可
        parse(json)
    }

    override suspend fun search(query: String, limit: Int): List<Track> { /* ... */ }
}
```

然后在 `MusicRepository.searchSources()` 里 `list += MySource()`。

---

## 八、已知限制

- **播放链路未在真机验证**：APK 的可安装性（签名、清单、启动 Activity、权限、资源）与全部解析逻辑（41 个单元测试）都已验证，但这里没有安卓设备和模拟器，**声音有没有真的出来、通知栏控制是否正常，没有实测**。装上后有异常把现象告我，我直接改。
- **界面未截图验证**：Compose 布局只做了编译期检查，配色、间距、深色模式的实际观感需要你在手机上看一眼。
- **局域网 http**：清单里开了 `usesCleartextTraffic`，所以 `http://192.168.x.x` 的自建服务器能直连；如果服务器是公网地址，建议上 https。
- **密码存储**：Subsonic / WebDAV 密码明文存在应用私有 DataStore 里（仅本机、不联网上传）。要更稳妥可以后续接 Android Keystore 加密。
- **WebDAV 扫描是同步遍历**：目录特别多时首次扫描要几十秒；之后走落盘快照，打开即出，超过 30 分钟在后台静默补扫。
- **下载不支持断点续传**：中断后重下会从头开始（文件本身不会重复占空间，`.part` 会清掉）。
- **歌词匹配靠艺术家 + 曲名**：Audius 上的独立音乐在 LRCLIB 里大多没有收录，这类曲子会显示「暂无歌词」，用「搜歌词」或粘贴 LRC 解决。
- **华语精选只有元数据**：专辑里是「谁唱了什么」，不含音频和歌词。本机有对应文件才能直接播，否则点「去听」跳官方平台 —— 这是版权上唯一干净的做法。
- **LRCLIB 在国内可能连不上**：部分网络对 `lrclib.net` 有 SNI 干扰，搜歌词时失败会自动退到 lyrics.ovh（纯文本）；两条都不通时用「粘贴 LRC」。
- **删除本地文件不可恢复**：Android 10 起会弹系统确认框，确认后媒体库条目和文件一起消失，没有回收站。
- 上架应用商店需换成你自己的签名（release 目前复用 debug 签名，方便直接安装）。

---

## 九、版本记录

| 版本 | 内容 |
| --- | --- |
| 1.6.0 | **自动更新（小圈子分发友好）**。设置里填一个「更新源地址」（局域网/公网均可），把 `update.json` 和新版 apk 放进去，应用启动静默检查，发现更高 `versionCode` 才弹窗——**绝不静默装**：下载走系统 DownloadManager（进度通知、断点续传，落应用私有目录），安装走系统安装器并经 FileProvider 分发；Android 8+ 自动引导开「安装未知应用」权限。清单带 `sha256` 时严格校验，下错或被篡改直接拒装；「跳过此版本」记 `versionCode` 后自动检查不再弹它；同一天只查一次、同版本不重复弹。新增 `UpdateInfo`/`UpdateChecker`/`UpdateManager` 三件套 + `UpdateDialog`，设置页「更新」区块，Manifest 加 `REQUEST_INSTALL_PACKAGES` 与 FileProvider。229 个单元测试（新增 10）。 |
| 1.5.0 | **向同类播放器补课**。对着椒盐音乐、Poweramp、Retro Music、Auxio、Musicolet、NeriPlayer 逐项比了一遍，挑出拾音真正缺的五件「用了就回不去」的东西：① **待播队列**（此前队列只以「队列 12 首」一行小字存在，知道有几首但看不到是哪几首）—— `QueueSheet` 面板，当前曲目高亮，点行即跳，行内菜单可上移/下移/移除/清空其后，另有「插到下一首播放」挂在「加入歌单」弹窗顶部；没做拖拽排序而是上移/下移，因为长列表里拖拽会和滚动抢手势，而队列通常只有几十首。② **拼音搜索**：`Pinyin` 用 Android 自带 ICU `Transliterator("Han-Latin")` 转拼音再去声调（NFD 拆组合符再滤掉），取每个音节首字母；不打包第三方拼音表。为了能在 JVM 单元测试里跑，把音译抽成可注入参数，测试用假音译器（真 ICU 在 JVM 是桩，直接调会抛 not mocked）。匹配分首字母前缀/首字母包含/全拼包含三档。③ **搜索历史**：DataStore 存最近 12 条，防抖后的半截词不记。④ **接着上次听**：`PlaySession` 存队列+索引+进度+随机/循环，每 10 秒一个指纹（进度按 10 秒分桶）才落盘，恢复时 `playWhenReady=false` 不出声；超 30 天或索引越界的现场丢弃。⑤ **播放失败自动跳过**：`onPlayerError` 自动 `seekToNextMediaItem`，连错 3 首才停；计数只在 `onIsPlayingChanged(true)` 归零，不能放在 `onMediaItemTransition` 里 —— 那样每次跳过都会归零，整条队列全是坏文件时会一路空转到最后一首。另加 **m3u8 歌单导入导出**：`M3u` 只实现 `#EXTM3U`/`#EXTINF` 两条指令（读时忽略其余，写时不生成），`M3uImport` 四级匹配且「只有歌名」这一级要求库内唯一，避免对错人。219 个单元测试（新增 30）。 |
| 1.4.4 | **播放内核 + 文件夹浏览**。① **10 段均衡器**：自写 `AudioProcessor` 处理 PCM16，31Hz–16kHz 每段 ±12dB，8 个预设；**踩坑一**：不能用 `isActive()=false` 把自己摘出音频链 —— ExoPlayer 只在音频格式变化时重新 configure，中途开 EQ 要等下一首才生效，所以改成「永远在链上，关闭时走直通」；**踩坑二**：拖动滑块直接换系数会「咔」一声，改成每 256 帧（5.8ms）最多移动 0.35dB，12dB 约 200ms 走完；**踩坑三**：不能直接用 `DefaultAudioProcessorChain`，会把 Sonic 丢掉，1.5 倍速就变成快进磁带 —— 自建链条「静音跳过 → Sonic → EQ」并显式转发变速参数。② **倍速** 0.75–2.0×，Sonic 时间伸缩保音高。③ **睡眠定时**：15/30/45/60/90 分钟 + 「播完当前这首」，到点淡出后暂停（只暂停不停止，醒了按一下接着听）。④ **淡入淡出**：起播/切歌从静音推上来，暂停与定时到点先降下来；真正的 crossfade 需要两个播放器同时出声，这里如实说明只做「淡入 + 淡出」。⑤ **音质标注**：`Track.format` / `bitrateKbps` 两个新字段，本地库从 MediaStore 的 MIME_TYPE 与 BITRATE 填，网盘按扩展名填；`AudioQualityOf` 是纯函数（无损容器直接定档，有损看码率，拿不到码率时才退回「高品」），曲目行上的徽章由它驱动。⑥ **音频体检**：`AudioProbe` 解析 RIFF/fmt/data 三段，识别 AC-3(0x2000) / DTS(0x0008) / MPEG(0x0050) 格式码，以及在「头写着 PCM 但数据段开头是 DTS 同步字 `7F FE 80 01`」这种伪装 —— 起因是用户报「网盘 WAV 全是沙沙声」，实测该文件是标准 16-bit PCM、Range 也正常，所以问题只可能出在「文件不是它自称的格式」上。⑦ **文件夹浏览**：本地/网盘/百度三处可切「按文件夹」，真实目录逐层进、面包屑、返回键回上级、目录级「播放全部」；`Track.folder` 新增字段，三个源各自填（本地取 MediaStore 的 DATA 列并剥掉 `/storage/emulated/0`，WebDAV 相对配置地址算，百度取 `path` 父目录），老快照没有该字段时从 URL 反推。189 个单元测试（新增 37）。 |
| 1.4.3 | **逐字歌词 + 双语对照**：① 增强 LRC 的行内标签 `<00:12.30>` 以前会被当成正文原样显示（界面上一堆尖括号），现在解析成逐字时间轴，当前行按字点亮，字与字之间用帧时钟补间（播放器 500ms 才推一次位置，直接拿来做逐字会一格一格跳）；② 新增 **TTML 解析器**（Apple Music / AMLL 那套），支持 `span` 逐字、`ttm:role=x-translation` 译文、`x-roman` 音译，时间格式兼容 `HH:MM:SS.mmm` / `MM:SS` / `12.5s` / 纯秒；③ 双语 LRC 自动合并成「原文 + 译文」一行，不再各占一行；④ 同名 `.ttml` 优先于 `.lrc`（本地与 WebDAV 都支持），粘贴框也认 TTML。踩坑：`<?xml?>` 声明前多一个 BOM 或换行，整份 TTML 就解析不出来 —— 下载来的歌词文件带 BOM 是常事，解析前先清掉。152 个单元测试（新增 17）。 |
| 1.4.2 | 修「一键授权点了没反应 + 保存后还是报参数不合法」：① 授权码在百度页面上是放在**只读输入框**里的，`innerText` 读不到 input 的 value，所以老脚本永远抓空 —— 现在先扫 `input/textarea`，并用「页面出现『授权码』」当开关防止误抓登录页隐藏域，底部再加「从剪贴板读取」兜底；② 用户把 32 位授权码填进 refresh_token 栏是必然发生的，以前只会回一句「参数不合法」，现在 `BaiduApi` 先认形状（32 位字母数字 = 授权码、60 字符以上 = refresh_token），是授权码就当场换 token，两条路都能走通；③ 粘贴带进来的空格/换行一律先抹掉（`sanitizeCredential`），这是另一类 400 的来源；④ 「已配置」不再只看非空，填错凭据时状态如实显示未配置；⑤ 授权码路径的 `invalid_grant` 翻译成「码过期或已用过」，不再误导成「token 被别的程序刷掉了」。135 个单元测试（新增 6）。 |
| 1.4.1 | 修「百度直连报 400 服务器错误」：根因是 `Http` 把非 2xx 的**响应体丢了**，百度的真实原因（`invalid_grant` / `Trigger security policy`）写在 body 里，界面只剩一串数字。现在 `HttpStatusException` 带上 body，`BaiduApi.tokenError` 把英文错误翻译成能照着做的中文（含「AList 在抢同一份授权」这种实际原因）。新增**一键授权**：WebView 里登录百度，自动从授权页文字里抠出 32 位授权码并换 token，不用再手输 83 个字符。授权失败进 5 分钟冷却，避免反复重试把限流拖长；起播失败不再静默，写进 logcat。129 个单元测试（新增 10）。 |
| 1.4.0 | **百度网盘直连**：新增独立音源，填一个 refresh_token 就能读网盘里的音乐目录，不需要 AList、不需要电脑开机、不需要任何设备常驻。关键技术点：公开 API 签发的直链带 `origin_appid` 被限速到 80 KB/s，改用网盘 App 的 `mediainfo` 播放通道后实测 **1.99 MB/s**（支持 Range 拖动）。播放地址用 `baidu://<fs_id>/<路径>` 占位、开播瞬间换真实直链（直链 8 小时过期，绝不能存进歌单）；token 轮换后立刻落盘，避免再次授权；目录结果落盘快照，冷启动秒出；下载器同样支持百度直链。119 个单元测试（新增 19）。 |
| 1.3.3 | 网盘曲库也落盘快照（`webdav_library.json`）：冷启动直接读上次的遍历结果，不用每次打开都重扫一遍 AList；快照超 30 分钟才在后台静默补扫，不挡着点歌；网盘页显示「刚刚 / 12 分钟前扫描」；改了 WebDAV 地址会连快照一起清掉，避免列表里全是打不开的旧链接。「本地」空状态补了一句「电脑上的目录要走网盘」，不再让人以为 F 盘会自动出现在本地。新增 10 个单元测试。 |
| 1.3.2 | 修「网盘扫完看不到歌」：我的页新增**网盘 tab**，展示 WebDAV 扫到的全部曲目（原先只进缓存，界面上没有任何入口）；扫描改成按层并发（5 路），52 个目录从 45 秒降到 11 秒；目录上限 60→150、文件 2000→3000，够装下本地 + 百度两个库；设置页扫描后直接显示「已收录 N 首」；Subsonic 地址误填成 WebDAV 时给出明确提示。 |
| 1.3.1 | 网盘音源支持同目录 `.lrc` 旁挂歌词：WebDAV 播放时按 URL 换扩展名取同名歌词文件，带 GBK 回退、HTML 错误页识别，网盘里的中文歌不用联网匹配就有滚动歌词。新增 13 个单元测试。 |
| 1.3.0 | 中文曲库：内置 10 张华语风格专辑（元数据 + 官方平台跳转 + 本地智能匹配）、搜索先查华语精选、本地库「只看华语」；本地曲库落盘快照，重开应用不再重扫；播放过的歌自动进「默认列表」；所有列表一键加入播放列表；播放页可在线搜歌词 / 粘贴 LRC；本地音乐可删除原文件（含 Android 10+ 系统确认流程）。新增 36 个单元测试。 |
| 1.2.1 | 新增 41 个单元测试并修掉测试暴露的问题：Jamendo 曲风标签被错误转义（筛选一直失效）、WebDAV 目录 href 以 `/` 结尾时子目录会被整棵丢弃、服务器返回 HTML/畸形 JSON 时列表页直接崩。解析逻辑拆成纯函数，脱离 Android 可测。 |
| 1.2.0 | 歌词（本地 lrc / LRCLIB / lyrics.ovh，滚动高亮、可点跳转）、离线下载（进度、取消、重试、网盘鉴权）、歌单管理。 |
| 1.1.0 | 放弃抓取商业平台，换成四条合法通路：本地库、Subsonic 自建曲库、WebDAV 网盘、Audius/Jamendo 开放曲库。 |
| 1.0.0 | 首个可安装版本：发现、搜索、播放页、后台播放、通知栏与锁屏控制、收藏、历史。 |

## 十、自动更新：部署你自己的更新源

小圈子分发没有应用商店，更新源就由你自己控制。原理一句话：**应用启动时去你填的那个目录地址下 `update.json`，比对版本号，更高就提示下载同名 apk**。

### update.json 格式

```json
{
  "versionCode": 16,
  "versionName": "1.6.1",
  "apk": "app-release.apk",
  "notes": "本次更新内容，支持多行。",
  "size": 15728640,
  "sha256": "安装包的 SHA-256 十六进制，可选项但强烈建议填"
}
```

字段说明：
- `versionCode`：**唯一的判断依据**，必须大于已装版本才会提示。每发一次版，`app/build.gradle.kts` 里的 `versionCode` 和这里的要对上并 +1。
- `versionName`：给人看的版本号，如 `1.6.0`。
- `apk`：新包文件名，**或**完整 http(s) 地址。填文件名时拼到更新源目录下；填完整地址则直接用。
- `notes` / `size` / `sha256`：弹窗里展示的说明、大小、校验值。`sha256` 不填则只校验能装上；填了就严格比对——这是挡住「源被篡改投毒」的最后一道闸。

### 怎么部署

1. 编译出新版：`dist/拾音-ShiYin-1.6.0-release.apk`（见第六章命令行编译）。
2. 算 sha256（可选但建议）：`sha256sum 拾音-ShiYin-1.6.0-release.apk`。
3. 把 apk 和写好的 `update.json` 放到同一 http 可访问目录——群晖 Web、AList 的 Web 根、对象存储 / 任意静态托管都行。
4. App「设置 → 更新」里填那个目录地址（结尾加不加斜杠都行），打开「启动时自动检查」。
5. 下次打开 App 自动检查到更高 `versionCode` 就弹窗；点「下载并更新」→「安装」即可。

> 局域网场景：更新源填 `http://192.168.8.192:5244/dav/shiyin-update` 这类地址，手机和电脑同一 WiFi 即可。本项目已允许明文 http（`usesCleartextTraffic=true`），但用 https 更稳妥。

---

## 十一、官网、下载与 GitHub

- **官网**：`docs/index.html`。部署到 GitHub Pages 即为项目主页，含功能介绍、五源说明、常见问题与下载入口。
- **下载页**：`docs/download.html`。页面会自动从 GitHub Releases 拉取最新版本、大小、日期与下载链接（需在 `docs/assets/app.js` 第 6 行把 `REPO` 改成你的仓库名）；并给出安装步骤与「自动更新」配置说明。
- **更新日志**：`CHANGELOG.md`。

### 公开仓库的注意事项（已写入 `.gitignore`）

以下含凭据 / 个人信息的文件**不会**进入 git，请放心推到公开仓库：

| 文件 | 为什么不公开 |
| --- | --- |
| `tools/baidu_refresh_token.txt` | 百度网盘一次性 refresh_token，泄露即失效且可被他人读你网盘 |
| `tools/baidu_token.py`、`tools/mount_baidu.py` | 脚本里硬编码了 AList 后台密码 |
| `AList-部署说明.md` | 含后台密码与局域网 IP |
| `dist/`、`*.apk` | 安装包走 GitHub Releases 分发，不进 git |
| `.workbuddy/` | 项目私人记忆 |

> 注：百度 OAuth 的 `client_id / client_secret` 写在 `app/src/main/java/com/shiyin/player/data/source/BaiduApi.kt`，这是原生百度网盘直连的固有做法——它已编译进 APK，可被反编译得到，与已发布的安装包一致，故源码保留不动。

### 发新版的标准流程（发完即自动更新）

1. 改代码，升 `app/build.gradle.kts` 里的 `versionCode`（+1）与 `versionName`。
2. `bash tools/build_apk.sh assembleRelease` 出包。
3. 在 GitHub 创建 Release（建议 tag `vX.Y.Z`），上传 `拾音-ShiYin-X.Y.Z-release.apk`。
4. 改仓库根 `update.json`：`versionCode` / `versionName` / `apk`（填第 3 步 release 资源的**完整 URL**）/ `notes` / `size` / `sha256`，提交并 push。
5. 已配置「更新源 = `https://raw.githubusercontent.com/<你>/<仓库>/main/update.json`」的用户，下次启动会自动发现更高版本并提示——绝不静默安装。

### 首次推送到 GitHub

```bash
git remote add origin https://github.com/<你>/<仓库>.git
git branch -M main
git push -u origin main
```

随后在仓库 **Settings → Pages** 里，Source 选 `Deploy from a branch`、分支 `main`、目录 `/docs`，即可用 GitHub Pages 托管官网。
Releases 里上传 APK 后，下载页会自动显示最新版。