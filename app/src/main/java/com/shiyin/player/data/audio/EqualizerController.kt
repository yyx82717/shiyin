package com.shiyin.player.data.audio


import androidx.media3.common.util.UnstableApi
import com.shiyin.player.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 均衡器的唯一入口。
 *
 * 处理器需要跨越 UI、服务两层被改（界面拖滑块、服务把它挂到音频链上），
 * 所以由 App 持有单例，两边拿到的是同一个对象。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class EqualizerController(private val store: SettingsStore) {

    val processor = EqualizerProcessor()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // 启动时先把上次保存的设置推进去，避免「界面显示开着，实际没生效」
        scope.launch {
            store.eqSettings.collect { processor.update(it.enabled, it.gains) }
        }
    }

    /** 拖动中的实时预览：立刻生效但不落盘，否则一次拖动会写几十次 DataStore。 */
    fun preview(settings: EqSettings) {
        processor.update(settings.enabled, settings.gains)
    }

    /** 松手 / 切换开关：生效并保存。 */
    fun commit(settings: EqSettings) {
        val fixed = settings.normalized()
        processor.update(fixed.enabled, fixed.gains)
        scope.launch { store.saveEq(fixed) }
    }
}
