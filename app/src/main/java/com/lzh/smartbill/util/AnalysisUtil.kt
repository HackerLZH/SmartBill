package com.lzh.smartbill.util

import android.R.attr.tag
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecyclePauseOrDisposeEffectResult
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lzh.smartbill.ui.screens.home.AlipayBill
import kotlinx.serialization.json.Json

object AnalysisUtil {
    private const val PREF = "ANALYSIS"
    private const val KEY_FINISH = "analysis_finished"

    /**
     * 是否已分析
     */
    fun getFinished(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_FINISH, false)
    }

    fun setFinished(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_FINISH, value)
                apply()
            }
    }

    /**
     * 分析账单
     */
    fun doAnalysis(
        context: Context
        , lifecycleOwner: LifecycleOwner
        , alipayList: List<AlipayBill>
        , onFinished: () -> Unit
    ) {
        val requests = alipayList.map {
            OneTimeWorkRequestBuilder<AnalysisWorker>()
                 // 非基本类型，要序列化; uri没有序列化器，转string
                .setInputData(workDataOf(Constants.KEY_WORK_DATA to Json.encodeToString(it.uri.toString())))
                .build()
        }
        // beginWith 只接受第一个，后续用 then 串行追加
        var continuation = WorkManager.getInstance(context).beginWith(requests.first())
        requests.drop(1).forEach { request ->
            continuation = continuation.then(request)
        }
        // 最后追加汇总任务
        continuation.then(AnalysisFinishedWorker.buildRequest()).enqueue()
        WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(Constants.TAG_ANALYSIS_FINISHED_WORK)
            .observe(lifecycleOwner) { workInfos ->
                if (workInfos.isNotEmpty() && workInfos.all { it.state == WorkInfo.State.SUCCEEDED }) {
                    onFinished()
                }
            }
    }

    /**
     * 检查分析状态
     *
     * onFinished: 分析完回调（更新UI状态）
     */
    fun checkAnalysisStatus(
        context: Context,
        onFinished: () -> Unit
    ) {
        val workInfo = WorkManager.getInstance(context)
            .getWorkInfosByTag(Constants.TAG_ANALYSIS_FINISHED_WORK)
            .get()
        if (workInfo.isEmpty()) {
            Log.d("checkAnalysisStatus", "Analysis Finished")
            onFinished()
        }
    }
}