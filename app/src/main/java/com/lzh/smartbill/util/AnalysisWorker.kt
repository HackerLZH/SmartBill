package com.lzh.smartbill.util

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

class AnalysisWorker(
    context: Context
    , params: WorkerParameters
) : CoroutineWorker(context, params){
    override suspend fun doWork(): Result {
        val json = inputData.getString(Constants.KEY_WORK_DATA) ?: return Result.failure()
        val uri = Json.decodeFromString<String>(json).toUri()

        return try {
            withContext(Dispatchers.IO) {
                delay(2000.milliseconds)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

class AnalysisFinishedWorker(
    context: Context
    , params: WorkerParameters
): CoroutineWorker(context, params) {
    companion object {
        fun buildRequest(start: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<AnalysisFinishedWorker>()
                .addTag(Constants.TAG_ANALYSIS_FINISHED_WORK)
                .setInputData(workDataOf(Constants.KEY_START_TIME to start))
                .build()
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        try {
            val start = inputData.getLong(Constants.KEY_START_TIME, 0L)
            val end = System.currentTimeMillis()
            // 清理已完成任务
            WorkManager.getInstance(applicationContext).pruneWork()
            // 标记已分析
//        AnalysisUtil.setFinished(applicationContext, true)

            AnalysisUtil.sendNotification(applicationContext, end - start)
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }
}