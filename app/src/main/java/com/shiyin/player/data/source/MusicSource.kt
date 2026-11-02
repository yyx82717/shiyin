package com.shiyin.player.data.source

import com.shiyin.player.data.model.Track

/**
 * 音源适配层。
 *
 * 想接入新的曲库，只要实现这个接口并注册到 MusicRepository，
 * 界面层完全不用改。这也是「自动抓取网络音乐」的可扩展点。
 */
interface MusicSource {
    /** 唯一标识，会写进 Track.source，用于界面上标注来源。 */
    val id: String

    /** 展示名。 */
    val label: String

    /** 一句话说明（授权方式/是否需要密钥），显示在设置页。 */
    val note: String

    /** 首页推荐流。genre 为 null 表示不限曲风。 */
    suspend fun feed(genre: String?, limit: Int = 30): List<Track>

    /** 关键词搜索。 */
    suspend fun search(query: String, limit: Int = 30): List<Track>
}
