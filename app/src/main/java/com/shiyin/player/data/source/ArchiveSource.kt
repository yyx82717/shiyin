package com.shiyin.player.data.source

import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.UrlCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Internet Archive 的开放音频库（公有领域 / CC 授权）。
 *
 * 选它的理由和 Audius 一样：资源本身可以自由流播，不涉及任何商业平台的版权内容，
 * 所以「自动搜 + 播」在授权上是干净的。它曲库极大（现场录音、公有领域古典、有声书…），
 * 作为「搜不到时的补充」很合适，且不违背本应用「曲库由用户自行提供 / 不抓商业平台」的原则。
 *
 * 代价是国内直连可能偏慢，且单首要先拉一次 metadata 才能拿到文件名。
 * 因此这里并行拉取详情；同时它被 [com.shiyin.player.data.repository.MusicRepository.collect]
 * 容错包裹——这一源超时 / 失败只丢它自己，不影响其它源。
 *
 * 接口：
 *   GET https://archive.org/advancedsearch.php?q=...&fl[]=identifier&rows=N&output=json&sort[]=downloads+desc
 *   GET https://archive.org/metadata/{identifier}   → files[] 里挑音频，直链 download/{id}/{name}
 */
class ArchiveSource : MusicSource {

    override val id = "archive"
    override val label = "Internet Archive"
    override val note = "公有领域 / CC 音频库，无需密钥；国内可能偏慢，失败不影响其它源"

    private val searchApi = "https://archive.org/advancedsearch.php"
    private val metaApi = "https://archive.org/metadata/"

    override suspend fun feed(genre: String?, limit: Int): List<Track> = idsThenTracks(feedQuery(genre), limit)

    override suspend fun search(query: String, limit: Int): List<Track> = idsThenTracks(query, limit)

    private fun feedQuery(genre: String?): String =
        if (!genre.isNullOrBlank()) {
            "subject:\"$genre\" OR collection:opensource_audio"
        } else {
            "collection:opensource_audio"
        }

    private suspend fun idsThenTracks(query: String, limit: Int): List<Track> = coroutineScope {
        val url = buildString {
            append(searchApi).append("?q=").append(UrlCodec.encode(query))
            append("&fl[]=identifier&rows=").append(minOf(limit, 12))
            append("&page=1&output=json&sort[]=downloads+desc")
        }
        val ids = parseArchiveIdentifiers(runCatching { Http.get(url) }.getOrNull().orEmpty(), 12)
        // map 的嵌套 lambda 会把隐式 CoroutineScope 接收者遮掉，这里显式抓出来给 async 用。
        val scope = this
        ids.map { id -> scope.async(Dispatchers.IO) { detailToTrack(id) } }
            .mapNotNull { it.await() }
            .take(limit)
    }

    private fun detailToTrack(identifier: String): Track? {
        val body = runCatching { Http.get("$metaApi$identifier") }.getOrNull() ?: return null
        return parseArchiveTrack(body, identifier)
    }
}
