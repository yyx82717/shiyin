package com.shiyin.player.data.model

/**
 * 目录树。
 *
 * 各音源的 `folder` 是一串路径，但来源不同、写法也不同（本地是 `/storage/emulated/0/Music/x`，
 * WebDAV 是 `/dav/music/x`，百度是 `/我的音乐/x`）。这里只做一件事：**统一归一化**，
 * 再按「共同前缀」拼成一棵树。路径推导的口径统一在这里，UI 只管画。
 */
data class FolderNode(
    /** 最后一级的名字；根节点用 [FolderTree.ROOT_LABEL]。 */
    val name: String,
    /** 归一化后的完整路径，根为空串。 */
    val path: String,
    /** 直接放在这个目录里的歌（不含子目录）。 */
    val direct: List<Track>,
    val children: List<FolderNode>,
) {
    /** 含所有子目录的歌曲数。 */
    val totalCount: Int by lazy { direct.size + children.sumOf { it.totalCount } }

    val isEmpty: Boolean get() = totalCount == 0

    /** 这个目录及其子目录里的全部歌曲，按「目录名 → 文件名」的稳定顺序。 */
    fun allTracks(): List<Track> = direct + children.flatMap { it.allTracks() }
}

object FolderTree {

    const val ROOT_LABEL = "全部文件夹"

    /**
     * 系统盘前缀：本地曲库的路径带着这些，显示时没必要让用户看见。
     *
     * 这里写死是故意的 —— 这些字符串只用来做前缀匹配，不去拿它访问文件，
     * 所以不能用 Environment.getExternalStorageDirectory()（那个值还可能是别的）。
     */
    @Suppress("SdCardPath")
    private val VOLUME_PREFIXES = listOf(
        "/storage/emulated/0/",
        "/storage/emulated/",
        "/sdcard/",
        "/mnt/sdcard/",
    )

    /**
     * 归一化路径：反斜杠转正斜杠、去掉各段首尾空白与多余分隔符、去掉卷标前缀。
     *
     * 只做「显示口径」的整理，不做大小写折叠 —— Android 上目录确实区分大小写。
     */
    fun normalize(raw: String?): String {
        var path = raw.orEmpty().trim().replace('\\', '/')
        if (path.isEmpty()) return ""
        // 折叠重复斜杠：Windows 风格的 \\nas\music\a 会变成 //nas/music/a，
        // 不折叠的话下面的卷标前缀一条都匹配不上
        path = path.replace(Regex("/{2,}"), "/")
        for (prefix in VOLUME_PREFIXES) {
            if (path.startsWith(prefix)) {
                path = path.removePrefix(prefix)
                break
            }
        }
        return path.split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." }
            .joinToString("/")
    }

    /** 从音频地址推目录，用于老快照（没有 folder 字段）时兜底。 */
    fun folderOf(track: Track): String {
        val declared = normalize(track.folder)
        if (declared.isNotEmpty()) return declared
        return normalize(guessFromUrl(track.streamUrl))
    }

    /**
     * 从 URL 里猜目录。
     *
     * `http://host/dav/music/a/b.mp3` → `dav/music/a`；
     * `baidu://fsid/我的音乐/a/b.mp3` → `我的音乐/a`。
     * 猜不准也没关系，最差就是把歌放到根目录。
     */
    private fun guessFromUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val noQuery = trimmed.substringBefore('?').substringBefore('#')
        // scheme://authority/rest —— 去掉协议与主机，只留路径
        val afterScheme = noQuery.substringAfter("://", noQuery)
        val slash = afterScheme.indexOf('/')
        val path = if (slash >= 0) afterScheme.substring(slash + 1) else ""
        if (path.isEmpty()) return ""
        // baidu://<fs_id>/<路径> 里的 fs_id 是 authority，已经被剥掉了，
        // 剩下的就是纯路径，不用再特殊处理
        return path.substringBeforeLast('/', "")
    }

    /**
     * 建树。[tracks] 顺序会被保留（同一目录内维持调用方给的顺序，通常是「最近添加」倒序）。
     */
    fun build(tracks: List<Track>, rootLabel: String = ROOT_LABEL): FolderNode {
        // path -> 直接歌曲；同时记录每个出现过的目录的父目录，用来搭骨架
        val direct = LinkedHashMap<String, MutableList<Track>>()
        val childrenOf = HashMap<String, MutableSet<String>>()

        fun ensure(path: String) {
            if (direct.containsKey(path)) return
            direct[path] = mutableListOf()
            childrenOf[path] = linkedSetOf()
            if (path.isNotEmpty()) {
                val parent = parentOf(path)
                ensure(parent)
                childrenOf.getOrPut(parent) { linkedSetOf() }.add(path)
            }
        }

        ensure("")
        for (track in tracks) {
            val path = folderOf(track)
            ensure(path)
            direct.getOrPut(path) { mutableListOf() }.add(track)
        }

        fun nodeOf(path: String): FolderNode {
            val kids = childrenOf[path].orEmpty()
                .map { nodeOf(it) }
                .sortedBy { it.name.lowercase() }
            return FolderNode(
                name = if (path.isEmpty()) rootLabel else path.substringAfterLast('/'),
                path = path,
                direct = direct[path].orEmpty().toList(),
                children = kids,
            )
        }

        return nodeOf("")
    }

    fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    /** 按路径取节点；取不到就退回根，避免界面白屏。 */
    fun at(root: FolderNode, path: String): FolderNode {
        val target = normalize(path)
        if (target.isEmpty()) return root
        var node = root
        for (segment in target.split('/')) {
            val next = node.children.firstOrNull { it.name == segment } ?: return root
            node = next
        }
        return node
    }

    /** 面包屑：从根到当前路径的每一级。 */
    fun breadcrumbs(path: String): List<Pair<String, String>> {
        val clean = normalize(path)
        if (clean.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        var acc = ""
        for (segment in clean.split('/')) {
            acc = if (acc.isEmpty()) segment else "$acc/$segment"
            out += acc to segment
        }
        return out
    }
}
