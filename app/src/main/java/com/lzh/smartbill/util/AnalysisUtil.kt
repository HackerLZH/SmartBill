package com.lzh.smartbill.util

import android.Manifest
import android.R.attr.tag
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecyclePauseOrDisposeEffectResult
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lzh.smartbill.MainActivity
import com.lzh.smartbill.R
import com.lzh.smartbill.ui.screens.home.AlipayBill
import kotlinx.serialization.json.Json

object AnalysisUtil {
    private const val PREF = "ANALYSIS"
    private const val KEY_FINISH = "analysis_finished"
    private const val CHANNEL = KEY_FINISH

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
        // 记录开始时间
        val startTime = System.currentTimeMillis()
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
        // 最后追加汇总任务，传入开始时间
        continuation.then(AnalysisFinishedWorker.buildRequest(startTime)).enqueue()
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

    /**
     * 分析完后发通知
     *
     * elapsed: 分析耗时
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun sendNotification(context: Context, elapsed: Long) {
        val channel = NotificationChannel(CHANNEL, CHANNEL
            , NotificationManager.IMPORTANCE_HIGH) // 必须最高优先级，才能弹出悬浮通知
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        // 点击通知跳转
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("分析完成，用时：${format(elapsed)}")
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(1, notification)
    }

    private fun format(elapsed: Long): String {
        var time = 0L
        if (elapsed < 60 * 1000) {
            return "${elapsed / 1000}s ${elapsed % 1000}ms"
        }
        if (elapsed < 60 * 60 * 1000) {
            time = elapsed / 1000
            return "${time / 60}min ${elapsed % 60}s"
        }
        return ">1h"
    }

    /**
     * 权限检查
     */
    fun checkPermission(context: Context, launcher: ActivityResultLauncher<String>): Boolean {
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return false
        }
        return true
    }
}