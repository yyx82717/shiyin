package com.shiyin.player.data.curated

import com.shiyin.player.data.model.Track

/** 精选专辑里的一首歌：只有元数据，不含音频，也不含歌词。 */
data class CuratedSong(
    val title: String,
    val artist: String,
    val year: Int = 0,
)

/**
 * 一张华语风格专辑。
 *
 * 这里只放「谁唱了什么」这类事实信息：
 * 不打包音频、不内置歌词，播放要么用你自己手机里的文件，
 * 要么点一下跳到官方平台去听。这样专辑可以放心随应用一起分发。
 */
data class CuratedAlbum(
    val id: String,
    val name: String,
    val tagline: String,
    val blurb: String,
    val accent: Long,
    val songs: List<CuratedSong>,
)

/**
 * 华语精选曲库（内置）。
 *
 * 这是「中文曲库」的骨架：即使一个网络音源都连不上，
 * 打开应用也有东西可看——按风格分成 9 张专辑，每首歌都能：
 * 1. 在你手机里找到同名的文件就直接播（本地曲库匹配）；
 * 2. 找不到就给一个直达官方平台的入口。
 */
object CuratedCatalog {

    val albums: List<CuratedAlbum> = listOf(
        CuratedAlbum(
            id = "guofeng",
            name = "国风古韵",
            tagline = "中国风 · 戏腔 · 古典意象",
            blurb = "把诗词、戏腔和五声音阶放进流行编曲里。适合安静的时候顺着听，也适合当写字的背景。",
            accent = 0xFF7A5CFF,
            songs = listOf(
                CuratedSong("青花瓷", "周杰伦", 2007),
                CuratedSong("东风破", "周杰伦", 2003),
                CuratedSong("菊花台", "周杰伦", 2006),
                CuratedSong("兰亭序", "周杰伦", 2008),
                CuratedSong("千里之外", "周杰伦 & 费玉清", 2006),
                CuratedSong("卷珠帘", "霍尊", 2014),
                CuratedSong("大鱼", "周深", 2016),
                CuratedSong("左手指月", "萨顶顶", 2018),
                CuratedSong("赤伶", "HITA", 2018),
                CuratedSong("芒种", "音阙诗听 & 赵方婧", 2019),
                CuratedSong("半壶纱", "刘珂矣", 2015),
                CuratedSong("牵丝戏", "银临 & Aki阿杰", 2015),
                CuratedSong("万疆", "李玉刚", 2021),
            ),
        ),
        CuratedAlbum(
            id = "folk",
            name = "民谣故事",
            tagline = "吉他 · 城市 · 讲一个故事",
            blurb = "从成都的小酒馆唱到北方的冬天。民谣的好，是每首歌都像一个你认识的人。",
            accent = 0xFFF5A65B,
            songs = listOf(
                CuratedSong("成都", "赵雷", 2016),
                CuratedSong("南方姑娘", "赵雷", 2011),
                CuratedSong("我们的时光", "赵雷", 2014),
                CuratedSong("理想三旬", "陈鸿宇", 2016),
                CuratedSong("南山南", "马頔", 2014),
                CuratedSong("斑马，斑马", "宋冬野", 2013),
                CuratedSong("董小姐", "宋冬野", 2013),
                CuratedSong("安和桥", "宋冬野", 2013),
                CuratedSong("春风十里", "鹿先森乐队", 2016),
                CuratedSong("一生有你", "水木年华", 2001),
                CuratedSong("玫瑰", "贰佰"),
                CuratedSong("米店", "张玮玮和郭龙"),
                CuratedSong("秦皇岛", "万能青年旅店", 2010),
            ),
        ),
        CuratedAlbum(
            id = "rock",
            name = "摇滚现场",
            tagline = "失真吉他 · 大合唱 · 汗",
            blurb = "适合开大音量、跟着吼的那一挂。前奏一响，就知道是它。",
            accent = 0xFFFF6B6B,
            songs = listOf(
                CuratedSong("海阔天空", "Beyond", 1993),
                CuratedSong("光辉岁月", "Beyond", 1990),
                CuratedSong("真的爱你", "Beyond", 1989),
                CuratedSong("蓝莲花", "许巍", 2002),
                CuratedSong("曾经的你", "许巍", 2002),
                CuratedSong("春天里", "汪峰", 2009),
                CuratedSong("一无所有", "崔健", 1986),
                CuratedSong("倔强", "五月天", 2004),
                CuratedSong("突然好想你", "五月天", 2008),
                CuratedSong("温柔", "五月天", 2000),
                CuratedSong("追梦赤子心", "GALA", 2011),
                CuratedSong("火车驶向云外，梦安魂于九霄", "刺猬", 2017),
            ),
        ),
        CuratedAlbum(
            id = "cantopop",
            name = "粤语经典",
            tagline = "港乐 · 黄金年代",
            blurb = "从红馆唱到茶餐厅。这一张里有几代人的粤语启蒙。",
            accent = 0xFF3FBF9F,
            songs = listOf(
                CuratedSong("富士山下", "陈奕迅", 2006),
                CuratedSong("浮夸", "陈奕迅", 2005),
                CuratedSong("遥远的她", "张学友", 1985),
                CuratedSong("每天爱你多一些", "张学友", 1991),
                CuratedSong("千千阙歌", "陈慧娴", 1989),
                CuratedSong("偏偏喜欢你", "陈百强", 1983),
                CuratedSong("男儿当自强", "林子祥", 1991),
                CuratedSong("追", "张国荣", 1994),
                CuratedSong("风继续吹", "张国荣", 1983),
                CuratedSong("一生所爱", "卢冠廷", 1995),
                CuratedSong("喜帖街", "谢安琪", 2008),
                CuratedSong("处处吻", "杨千嬅", 2003),
                CuratedSong("下一站天后", "Twins", 2003),
            ),
        ),
        CuratedAlbum(
            id = "ost",
            name = "影视原声",
            tagline = "剧集 · 电影 · 一响起就有画面",
            blurb = "很多歌是先在剧里听哭，才去搜歌名的。这张专辑专门收这类。",
            accent = 0xFF9B8CFF,
            songs = listOf(
                CuratedSong("平凡之路", "朴树", 2014),
                CuratedSong("凉凉", "杨宗纬 & 张碧晨", 2017),
                CuratedSong("年轮", "张碧晨", 2015),
                CuratedSong("只要平凡", "张杰 & 张碧晨", 2018),
                CuratedSong("默", "那英", 2014),
                CuratedSong("红颜旧", "郁可唯", 2015),
                CuratedSong("不染", "毛不易", 2018),
                CuratedSong("一荤一素", "毛不易", 2018),
                CuratedSong("起风了", "买辣椒也用券", 2017),
                CuratedSong("时间都去哪儿了", "王铮亮", 2010),
                CuratedSong("光年之外", "邓紫棋", 2016),
            ),
        ),
        CuratedAlbum(
            id = "citypop",
            name = "都市情歌",
            tagline = "通勤 · 加班 · 一个人",
            blurb = "耳机里的地铁和夜路。旋律都熟，情绪都很稳。",
            accent = 0xFF56A8FF,
            songs = listOf(
                CuratedSong("晴天", "周杰伦", 2003),
                CuratedSong("七里香", "周杰伦", 2004),
                CuratedSong("稻香", "周杰伦", 2008),
                CuratedSong("说好不哭", "周杰伦 & 阿信", 2019),
                CuratedSong("演员", "薛之谦", 2015),
                CuratedSong("绅士", "薛之谦", 2015),
                CuratedSong("丑八怪", "薛之谦", 2013),
                CuratedSong("小幸运", "田馥甄", 2015),
                CuratedSong("慢慢喜欢你", "莫文蔚", 2018),
                CuratedSong("体面", "于文文", 2017),
                CuratedSong("说散就散", "袁娅维", 2017),
                CuratedSong("红色高跟鞋", "蔡健雅", 2007),
            ),
        ),
        CuratedAlbum(
            id = "ktv",
            name = "KTV 大合唱",
            tagline = "副歌一起唱 · 包厢必备",
            blurb = "点歌率最高的那一批。跟朋友唱到副歌，总有人会自动和声。",
            accent = 0xFFFF8FB1,
            songs = listOf(
                CuratedSong("后来", "刘若英", 1999),
                CuratedSong("很爱很爱你", "刘若英", 1998),
                CuratedSong("勇气", "梁静茹", 2000),
                CuratedSong("宁夏", "梁静茹", 2004),
                CuratedSong("可惜不是你", "梁静茹", 2005),
                CuratedSong("遇见", "孙燕姿", 2003),
                CuratedSong("天黑黑", "孙燕姿", 2000),
                CuratedSong("我怀念的", "孙燕姿", 2007),
                CuratedSong("泡沫", "邓紫棋", 2012),
                CuratedSong("句号", "邓紫棋", 2019),
                CuratedSong("倒带", "蔡依林", 2004),
                CuratedSong("日不落", "蔡依林", 2007),
                CuratedSong("朋友", "周华健", 1997),
            ),
        ),
        CuratedAlbum(
            id = "indie",
            name = "独立新声",
            tagline = "卧室制作 · 新声代",
            blurb = "从音乐节和短视频里长出来的一批人，编曲更轻，词更直白。",
            accent = 0xFFE4C05A,
            songs = listOf(
                CuratedSong("小半", "陈粒", 2015),
                CuratedSong("奇妙能力歌", "陈粒", 2014),
                CuratedSong("云烟成雨", "房东的猫", 2017),
                CuratedSong("消愁", "毛不易", 2017),
                CuratedSong("像我这样的人", "毛不易", 2017),
                CuratedSong("我曾", "隔壁老樊", 2017),
                CuratedSong("晚安", "颜人中", 2018),
                CuratedSong("水星记", "郭顶", 2016),
                CuratedSong("年少有为", "李荣浩", 2018),
                CuratedSong("模特", "李荣浩", 2013),
                CuratedSong("白羊", "徐秉龙 & 沈以诚", 2017),
                CuratedSong("星辰大海", "黄霄雲", 2020),
            ),
        ),
        CuratedAlbum(
            id = "rap",
            name = "说唱态度",
            tagline = "中文说唱 · 押韵和人",
            blurb = "中文说唱从地下走到台前的那几年，这一张里几乎每首都能跟着念两句。",
            accent = 0xFF8ED081,
            songs = listOf(
                CuratedSong("天干物燥", "GAI", 2017),
                CuratedSong("苦行僧", "GAI", 2017),
                CuratedSong("野狼disco", "宝石Gem", 2019),
                CuratedSong("明天不上班", "谢帝", 2014),
                CuratedSong("沙漠骆驼", "展展与罗罗", 2017),
                CuratedSong("双截棍", "周杰伦", 2001),
                CuratedSong("本草纲目", "周杰伦", 2006),
                CuratedSong("龙拳", "周杰伦", 2002),
                CuratedSong("我的新衣", "VaVa毛衍七", 2017),
            ),
        ),
        CuratedAlbum(
            id = "memory",
            name = "童年与校园",
            tagline = "老磁带 · 毕业歌",
            blurb = "爸妈车里放过、毕业典礼唱过的那批歌。前奏一起，时间就回去了。",
            accent = 0xFFB08BD9,
            songs = listOf(
                CuratedSong("童年", "罗大佑", 1982),
                CuratedSong("光阴的故事", "罗大佑", 1982),
                CuratedSong("同桌的你", "老狼", 1994),
                CuratedSong("睡在我上铺的兄弟", "老狼", 1994),
                CuratedSong("白衣飘飘的年代", "叶蓓", 1996),
                CuratedSong("外婆的澎湖湾", "潘安邦", 1979),
                CuratedSong("让我们荡起双桨", "中央人民广播电台少年广播合唱团"),
                CuratedSong("祝福", "张学友", 1993),
                CuratedSong("明天会更好", "群星", 1985),
                CuratedSong("一路生花", "温奕心", 2021),
            ),
        ),
    )

    val totalSongs: Int get() = albums.sumOf { it.songs.size }

    fun byId(id: String): CuratedAlbum? = albums.firstOrNull { it.id == id }

    /** 专辑在本地曲库里的命中数（首页卡片上显示「本地 8 首」）。 */
    fun matchedCount(album: CuratedAlbum, index: Map<String, List<Track>>): Int =
        album.songs.count { index.containsKey(SongMatch.normalizeTitle(it.title)) }
}
