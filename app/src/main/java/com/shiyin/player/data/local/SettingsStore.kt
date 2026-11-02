package com.shiyin.player.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shiyin.player.data.audio.EqSettings
import com.shiyin.player.data.model.AppJson
import com.shiyin.player.data.model.DEFAULT_PLAYLIST_ID
import com.shiyin.player.data.model.DownloadEntry
import com.shiyin.player.data.model.PlaySession
import com.shiyin.player.data.model.Playlist
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.model.appendIfAbsent
import com.shiyin.player.data.model.appended
import com.shiyin.player.data.model.defaultPlaylist
import com.shiyin.player.data.model.sortedForDisplay
import com.shiyin.player.data.model.upsert
import com.shiyin.player.data.source.BaiduConfig
import com.shiyin.player.data.source.SubsonicConfig
import com.shiyin.player.data.source.WebDavConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shiyin_prefs")

/** 收藏、播放历史、歌单、下载记录、主题、音源配置的本地存储。 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val FAVORITES = stringPreferencesKey("favorites")
        val HISTORY = stringPreferencesKey("history")
        val PLAYLISTS = stringPreferencesKey("playlists")
        val DOWNLOADS = stringPreferencesKey("downloads")
        val THEME = intPreferencesKey("theme_mode")
        val JAMENDO = stringPreferencesKey("jamendo_client_id")
        val LOCAL_ON = booleanPreferencesKey("local_enabled")
        val SUB_URL = stringPreferencesKey("subsonic_url")
        val SUB_USER = stringPreferencesKey("subsonic_user")
        val SUB_PASS = stringPreferencesKey("subsonic_pass")
        val DAV_URL = stringPreferencesKey("webdav_url")
        val DAV_USER = stringPreferencesKey("webdav_user")
        val DAV_PASS = stringPreferencesKey("webdav_pass")
        val BAIDU_REFRESH = stringPreferencesKey("baidu_refresh_token")
        val BAIDU_ROOT = stringPreferencesKey("baidu_root_dir")
        val BAIDU_ACCESS = stringPreferencesKey("baidu_access_token")
        val BAIDU_ACCESS_AT = longPreferencesKey("baidu_access_at")
        val EQ = stringPreferencesKey("eq_settings")
        val SPEED_PERCENT = intPreferencesKey("playback_speed_percent")
        val FADE_MS = intPreferencesKey("playback_fade_ms")
        val SESSION = stringPreferencesKey("play_session")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        // ---- 自动更新 ----
        val UPDATE_URL = stringPreferencesKey("update_url")
        val AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        val SKIP_VERSION_CODE = intPreferencesKey("skip_update_version_code")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_at")
    }

    private val trackListSerializer = ListSerializer(Track.serializer())
    private val playlistListSerializer = ListSerializer(Playlist.serializer())
    private val downloadListSerializer = ListSerializer(DownloadEntry.serializer())

    // ---------------- 收藏 / 历史 ----------------

    val favorites: Flow<List<Track>> = context.dataStore.data.map { decode(it[Keys.FAVORITES]) }

    val history: Flow<List<Track>> = context.dataStore.data.map { decode(it[Keys.HISTORY]) }

    suspend fun toggleFavorite(track: Track) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[Keys.FAVORITES]).toMutableList()
            val index = current.indexOfFirst { it.id == track.id }
            if (index >= 0) current.removeAt(index) else current.add(0, track)
            prefs[Keys.FAVORITES] = encode(current)
        }
    }

    suspend fun pushHistory(track: Track) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[Keys.HISTORY]).toMutableList()
            current.removeAll { it.id == track.id }
            current.add(0, track)
            prefs[Keys.HISTORY] = encode(current.take(MAX_HISTORY))
        }
    }

    // ---------------- 歌单 ----------------

    val playlists: Flow<List<Playlist>> = context.dataStore.data.map {
        decodePlaylists(it[Keys.PLAYLISTS]).sortedForDisplay()
    }

    suspend fun playlistsNow(): List<Playlist> = playlists.first()

    /**
     * 播放即归集：正在听的歌自动进「默认列表」。
     *
     * 用户不用先建歌单再收藏，听完一段时间回头看，最近听过的都在这里，
     * 想整理时再挑出喜欢的挪到自己的歌单里。
     */
    suspend fun addToDefault(track: Track) {
        context.dataStore.edit { prefs ->
            val current = decodePlaylists(prefs[Keys.PLAYLISTS])
            val target = current.firstOrNull { it.id == DEFAULT_PLAYLIST_ID } ?: defaultPlaylist()
            val updated = target.appended(track)
            prefs[Keys.PLAYLISTS] = encodePlaylists(
                current.filterNot { it.id == DEFAULT_PLAYLIST_ID } + updated,
            )
        }
    }

    suspend fun clearDefault() {
        context.dataStore.edit { prefs ->
            val current = decodePlaylists(prefs[Keys.PLAYLISTS])
            val target = current.firstOrNull { it.id == DEFAULT_PLAYLIST_ID } ?: defaultPlaylist()
            prefs[Keys.PLAYLISTS] = encodePlaylists(
                current.filterNot { it.id == DEFAULT_PLAYLIST_ID } + target.copy(tracks = emptyList()),
            )
        }
    }

    suspend fun createPlaylist(name: String): String {
        val playlist = Playlist(
            id = UUID.randomUUID().toString().take(8),
            name = name.trim().ifBlank { "未命名歌单" }.take(40),
            tracks = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYLISTS] = encodePlaylists(decodePlaylists(prefs[Keys.PLAYLISTS]) + playlist)
        }
        return playlist.id
    }

    /** 一次写入整单：导入 m3u 时几十首歌逐条 edit 会把 DataStore 拖垮。 */
    suspend fun createPlaylistWithTracks(name: String, tracks: List<Track>): String {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "导入的歌单" }.take(40),
            tracks = tracks.distinctBy { it.id },
            createdAt = System.currentTimeMillis(),
        )
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYLISTS] = encodePlaylists(decodePlaylists(prefs[Keys.PLAYLISTS]) + playlist)
        }
        return playlist.id
    }

    suspend fun renamePlaylist(playlistId: String, name: String) {        val cleaned = name.trim().ifBlank { return }.take(40)
        context.dataStore.edit { prefs ->
            val updated = decodePlaylists(prefs[Keys.PLAYLISTS]).map {
                if (it.id == playlistId) it.copy(name = cleaned) else it
            }
            prefs[Keys.PLAYLISTS] = encodePlaylists(updated)
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        if (playlistId == DEFAULT_PLAYLIST_ID) return
        context.dataStore.edit { prefs ->
            val updated = decodePlaylists(prefs[Keys.PLAYLISTS]).filterNot { it.id == playlistId }
            prefs[Keys.PLAYLISTS] = encodePlaylists(updated)
        }
    }

    /** 同一首歌不会重复加进同一个歌单。 */
    suspend fun addToPlaylist(playlistId: String, track: Track) {
        context.dataStore.edit { prefs ->
            val current = decodePlaylists(prefs[Keys.PLAYLISTS])
            // 默认列表可能还没被创建（用户一首都没播过就先点了「加入默认列表」）
            val existing = current.firstOrNull { it.id == playlistId }
                ?: if (playlistId == DEFAULT_PLAYLIST_ID) defaultPlaylist() else return@edit
            val updated = current.upsert(existing.appendIfAbsent(track))
            prefs[Keys.PLAYLISTS] = encodePlaylists(updated)
        }
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        context.dataStore.edit { prefs ->
            val updated = decodePlaylists(prefs[Keys.PLAYLISTS]).map { playlist ->
                if (playlist.id != playlistId) playlist
                else playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
            }
            prefs[Keys.PLAYLISTS] = encodePlaylists(updated)
        }
    }

    // ---------------- 下载记录 ----------------

    val downloads: Flow<List<DownloadEntry>> = context.dataStore.data.map {
        decodeDownloads(it[Keys.DOWNLOADS]).sortedByDescending { entry -> entry.at }
    }

    suspend fun downloadsNow(): List<DownloadEntry> = downloads.first()

    suspend fun addDownload(entry: DownloadEntry) {
        context.dataStore.edit { prefs ->
            val current = decodeDownloads(prefs[Keys.DOWNLOADS]).filterNot { it.track.id == entry.track.id }
            prefs[Keys.DOWNLOADS] = encodeDownloads(current + entry)
        }
    }

    suspend fun removeDownload(trackId: String) {
        context.dataStore.edit { prefs ->
            val current = decodeDownloads(prefs[Keys.DOWNLOADS]).filterNot { it.track.id == trackId }
            prefs[Keys.DOWNLOADS] = encodeDownloads(current)
        }
    }

    // ---------------- 主题 / 音源配置 ----------------

    val themeMode: Flow<Int> = context.dataStore.data.map { it[Keys.THEME] ?: THEME_FOLLOW_SYSTEM }

    val jamendoIdFlow: Flow<String> = context.dataStore.data.map { it[Keys.JAMENDO].orEmpty() }

    suspend fun jamendoId(): String = context.dataStore.data.first()[Keys.JAMENDO].orEmpty()

    val localEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOCAL_ON] ?: true }

    val subsonicConfigFlow: Flow<SubsonicConfig> = context.dataStore.data.map { prefs ->
        SubsonicConfig(
            baseUrl = prefs[Keys.SUB_URL].orEmpty(),
            user = prefs[Keys.SUB_USER].orEmpty(),
            password = prefs[Keys.SUB_PASS].orEmpty(),
        )
    }

    val webdavConfigFlow: Flow<WebDavConfig> = context.dataStore.data.map { prefs ->
        WebDavConfig(
            baseUrl = prefs[Keys.DAV_URL].orEmpty(),
            user = prefs[Keys.DAV_USER].orEmpty(),
            password = prefs[Keys.DAV_PASS].orEmpty(),
        )
    }

    val baiduConfigFlow: Flow<BaiduConfig> = context.dataStore.data.map { prefs ->
        BaiduConfig(
            refreshToken = prefs[Keys.BAIDU_REFRESH].orEmpty(),
            rootDir = prefs[Keys.BAIDU_ROOT].orEmpty().ifBlank { "/我的音乐" },
            accessToken = prefs[Keys.BAIDU_ACCESS].orEmpty(),
            accessTokenAt = prefs[Keys.BAIDU_ACCESS_AT] ?: 0L,
        )
    }

    suspend fun subsonicConfig(): SubsonicConfig? = subsonicConfigFlow.first().takeIf { it.usable }

    suspend fun webdavConfig(): WebDavConfig? = webdavConfigFlow.first().takeIf { it.usable }

    suspend fun baiduConfig(): BaiduConfig? = baiduConfigFlow.first().takeIf { it.usable }

    /** 保存 refresh_token（用户填的或刚授权拿到的）与根目录。 */
    suspend fun saveBaidu(refreshToken: String, rootDir: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BAIDU_REFRESH] = refreshToken.trim()
            prefs[Keys.BAIDU_ROOT] = rootDir.trim().ifBlank { "/我的音乐" }
            // 换了授权就必须重换 token，否则会用上一个账号的 access_token 去列新账号的目录
            prefs[Keys.BAIDU_ACCESS] = ""
            prefs[Keys.BAIDU_ACCESS_AT] = 0L
        }
    }

    /** 只改音乐目录，不动已换到的 access_token（重新授权时顺手更新目录用）。 */
    suspend fun saveBaiduRoot(rootDir: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BAIDU_ROOT] = rootDir.trim().ifBlank { "/我的音乐" }
        }
    }

    /** 刷新 token 后立刻写回：百度每刷新一次就作废旧 token，不存就等于把授权丢了。 */
    suspend fun saveBaiduToken(access: String, refresh: String, at: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BAIDU_ACCESS] = access
            prefs[Keys.BAIDU_REFRESH] = refresh
            prefs[Keys.BAIDU_ACCESS_AT] = at
        }
    }

    suspend fun isLocalEnabled(): Boolean = localEnabled.first()

    suspend fun setLocalEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.LOCAL_ON] = value }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME] = mode }
    }

    suspend fun setJamendoId(value: String) {
        context.dataStore.edit { it[Keys.JAMENDO] = value.trim() }
    }

    suspend fun saveSubsonic(config: SubsonicConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SUB_URL] = config.baseUrl.trim()
            prefs[Keys.SUB_USER] = config.user.trim()
            prefs[Keys.SUB_PASS] = config.password
        }
    }

    suspend fun saveWebdav(config: WebDavConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAV_URL] = config.baseUrl.trim()
            prefs[Keys.DAV_USER] = config.user.trim()
            prefs[Keys.DAV_PASS] = config.password
        }
    }

    // ---------------- 播放内核（均衡器 / 倍速 / 淡入淡出） ----------------

    val eqSettings: Flow<EqSettings> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.EQ]
        if (raw.isNullOrBlank()) {
            EqSettings()
        } else {
            runCatching { AppJson.decodeFromString(EqSettings.serializer(), raw) }
                .getOrDefault(EqSettings())
                .normalized()
        }
    }

    suspend fun saveEq(settings: EqSettings) {
        val payload = AppJson.encodeToString(EqSettings.serializer(), settings.normalized())
        context.dataStore.edit { it[Keys.EQ] = payload }
    }

    /** 倍速存百分比整数：preferences 里没有 Float，转一道也能顺手做范围裁剪。 */
    val playbackSpeed: Flow<Float> = context.dataStore.data.map {
        (it[Keys.SPEED_PERCENT] ?: 100).coerceIn(MIN_SPEED_PERCENT, MAX_SPEED_PERCENT) / 100f
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        val percent = (speed * 100).toInt().coerceIn(MIN_SPEED_PERCENT, MAX_SPEED_PERCENT)
        context.dataStore.edit { it[Keys.SPEED_PERCENT] = percent }
    }

    /** 淡入淡出时长（毫秒），0 = 关闭。 */
    val fadeMs: Flow<Int> = context.dataStore.data.map { (it[Keys.FADE_MS] ?: 0).coerceIn(0, MAX_FADE_MS) }

    suspend fun saveFadeMs(ms: Int) {
        context.dataStore.edit { it[Keys.FADE_MS] = ms.coerceIn(0, MAX_FADE_MS) }
    }

    // ---------------- 播放现场 ----------------

    val session: Flow<PlaySession?> = context.dataStore.data.map { decodeSession(it[Keys.SESSION]) }

    suspend fun sessionNow(): PlaySession? = session.first()

    /** 队列太长时只留当前曲目前后一段，别把几十 KB 塞进偏好设置。 */
    suspend fun saveSession(session: PlaySession) {
        val trimmed = if (session.tracks.size > PlaySession.MAX_TRACKS) {
            val half = PlaySession.MAX_TRACKS / 2
            val start = (session.index - half).coerceIn(0, session.tracks.size - PlaySession.MAX_TRACKS)
            session.copy(
                tracks = session.tracks.subList(start, start + PlaySession.MAX_TRACKS).toList(),
                index = session.index - start,
            )
        } else {
            session
        }
        val payload = AppJson.encodeToString(PlaySession.serializer(), trimmed)
        context.dataStore.edit { it[Keys.SESSION] = payload }
    }

    /** 退出、清空队列时用：把现场抹掉，下次打开就是干净的。 */
    suspend fun clearSession() {
        context.dataStore.edit { it.remove(Keys.SESSION) }
    }

    // ---------------- 搜索历史 ----------------

    val searchHistory: Flow<List<String>> = context.dataStore.data.map {
        runCatching {
            AppJson.decodeFromString(ListSerializer(String.serializer()), it[Keys.SEARCH_HISTORY].orEmpty())
        }.getOrDefault(emptyList())
    }

    /** 新的排最前，重复的提到最前，最多留 [MAX_SEARCH_HISTORY] 条。 */
    suspend fun pushSearchHistory(keyword: String) {
        val cleaned = keyword.trim().take(40)
        if (cleaned.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = runCatching {
                AppJson.decodeFromString(ListSerializer(String.serializer()), prefs[Keys.SEARCH_HISTORY].orEmpty())
            }.getOrDefault(emptyList())
            val updated = (listOf(cleaned) + current.filterNot { it.equals(cleaned, ignoreCase = true) })
                .take(MAX_SEARCH_HISTORY)
            prefs[Keys.SEARCH_HISTORY] = AppJson.encodeToString(ListSerializer(String.serializer()), updated)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(Keys.SEARCH_HISTORY) }
    }

    // ---------------- 自动更新 ----------------

    val updateUrl: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_URL].orEmpty() }

    val autoCheckUpdate: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.AUTO_CHECK_UPDATE] ?: true
    }

    /** 上次「检查更新」的时刻（毫秒）。限频用：同一天不重复打网络。 */
    suspend fun lastUpdateCheckAt(): Long = context.dataStore.data.first()[Keys.LAST_UPDATE_CHECK] ?: 0L

    suspend fun markUpdateChecked() {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK] = System.currentTimeMillis() }
    }

    suspend fun setUpdateUrl(url: String) {
        context.dataStore.edit { it[Keys.UPDATE_URL] = url.trim() }
    }

    suspend fun setAutoCheckUpdate(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATE] = value }
    }

    /** 记下「这个版本我不想升」，启动自动检查就别再弹它了。下次出更高版本照常弹。 */
    suspend fun skipVersion(versionCode: Int) {
        context.dataStore.edit { it[Keys.SKIP_VERSION_CODE] = versionCode }
    }

    suspend fun skippedVersionCode(): Int = context.dataStore.data.first()[Keys.SKIP_VERSION_CODE] ?: 0

    // ---------------- 序列化 ----------------

    private fun decode(raw: String?): List<Track> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { AppJson.decodeFromString(trackListSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun encode(list: List<Track>): String = AppJson.encodeToString(trackListSerializer, list)

    private fun decodePlaylists(raw: String?): List<Playlist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { AppJson.decodeFromString(playlistListSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun encodePlaylists(list: List<Playlist>): String =
        AppJson.encodeToString(playlistListSerializer, list)

    private fun decodeDownloads(raw: String?): List<DownloadEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { AppJson.decodeFromString(downloadListSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun decodeSession(raw: String?): PlaySession? {
        if (raw.isNullOrBlank()) return null
        return runCatching { AppJson.decodeFromString(PlaySession.serializer(), raw) }.getOrNull()
    }

    private fun encodeDownloads(list: List<DownloadEntry>): String =
        AppJson.encodeToString(downloadListSerializer, list)

    companion object {
        const val THEME_FOLLOW_SYSTEM = 0
        const val THEME_DARK = 1
        const val THEME_LIGHT = 2
        private const val MAX_HISTORY = 80
        private const val MAX_SEARCH_HISTORY = 12

        /** 0.5×–2.0×，超出这个范围音乐就没法听了（语音播客另说）。 */
        const val MIN_SPEED_PERCENT = 50
        const val MAX_SPEED_PERCENT = 200
        const val MAX_FADE_MS = 5_000
    }
}
