package com.lzh.smartbill.ui.screens.home

import android.R.attr.text
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lzh.smartbill.components.AnalyzingIndicator
import com.lzh.smartbill.components.DecompressButton
import com.lzh.smartbill.util.AnalysisUtil
import com.lzh.smartbill.util.SAFUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun Home(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var treeUri by remember { mutableStateOf(SAFUtil.getValidUri(context)) }
    var hasZip by remember { mutableStateOf(false) } // 是否有新增压缩包
    val alipayList = remember { mutableStateListOf<AlipayBill>() }
    var refreshCount by remember { mutableStateOf(0) }
    var analysisFinished by remember { mutableStateOf(AnalysisUtil.getFinished(context)) }
    var analyzing by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    val primaryRootUri = "content://com.android.externalstorage.documents/tree/primary".toUri()


    fun onFinish() {
        analyzing = false
        analysisFinished = true
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree() // 打开手机目录树
    ) {
        uri -> uri?.let {
            SAFUtil.savePermission(context, uri)
            treeUri = uri // 触发重组
        } ?: scope.launch {
            delay(1000.milliseconds) // 延迟1s
            openDocumentSettings(context)
        }
    }

    val launcher_permission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        if (!it) {
            (context as Activity).finish()
        }
    }

    // APP切回前台时执行
    LifecycleResumeEffect(Unit) {
        if (analyzing) {
            AnalysisUtil.checkAnalysisStatus(context) { onFinish() }
        }

        onPauseOrDispose {  }
    }

    LaunchedEffect(treeUri, refreshCount) {
        treeUri?.let {
            hasZip = false
            alipayList.clear()
            val list = collectAlipay(context, it)
            list.forEach {
                if (it.name?.endsWith(".zip") == true) {
                    hasZip = true
                    if (analysisFinished) {
                        AnalysisUtil.setFinished(context, false)
                        analysisFinished = false
                    }
                    return@forEach
                }
            }
            if (list.isNotEmpty()) {
                alipayList.addAll(list)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        , horizontalAlignment = Alignment.CenterHorizontally
        , verticalArrangement = Arrangement.Center
    ) {
        if (treeUri == null) {
            Button(onClick = { launcher.launch(primaryRootUri) }) {
                Text("选择账单目录")
            }
        } else {
            if (hasZip) {
                Text("支付宝")
                BillItem(alipayList, onDepressionFinished = { refreshCount++ })
            } else {
                if (!analysisFinished) {
                    if (analyzing) {
                        AnalyzingIndicator()  // 动态旋转效果
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在分析...", fontWeight = FontWeight.Bold)
                    } else {
                        Box() {
                            Button(onClick = {
                                if (!AnalysisUtil.checkPermission(context, launcher_permission)) {
                                    return@Button
                                }
                                analyzing = true
                                AnalysisUtil.doAnalysis(
                                    context
                                    , lifecycleOwner
                                    , alipayList
                                ) {
                                    onFinish()
                                }
                            }) {
                                Text("分析账单")
                            }
                            if (showDialog) {
                                ShowDialog(onClose = {showDialog = false})
                            }
                        }
                    }
                } else {
                    // TODO 显示分析结果
                    Text("分析结果")
                }
            }
        }
    }
}

@Composable
private fun BillItem(
    billList: List<Any>
    , onDepressionFinished: () -> Unit
) {
    LazyColumn {
        items(billList) {
            when (it) {
                is AlipayBill -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        it.name?.let { text -> Text(text) }
                        Spacer(Modifier.width(2.dp))
                        if (it.name?.endsWith(".zip") == true) {
                            DecompressButton(it.uri, it.parent, onDepressionFinished = onDepressionFinished)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShowDialog(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose
        , title = { Text("获得更好体验") }
        , text = { Text("前往设置，开启应用悬浮通知") }
        , confirmButton = {
            TextButton(
                onClick = {
                    onClose()
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        // 降级：跳转到应用详情
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    }
                },
            ) {
                Text("前往")
            }
        }
        , dismissButton = {
            TextButton(
                onClick = onClose
            ) {
                Text("已开启")
            }
        }
    )
}

/**
 * 前往文档设置页
 */
private fun openDocumentSettings(context: Context) {
    val packageNames = listOf(
        "com.google.android.documentsui",  // Google 版本
        "com.android.documentsui"          // AOSP 版本
    )

    for (pkg in packageNames) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = pkg.toUri()
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 都失败，跳转到应用管理列表
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))
    } catch (e: Exception) {
        // 最后的兜底：跳转到设置首页
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

/**
 * 检测已保存的支付宝账单
 */
private fun collectAlipay(context: Context, treeUri: Uri): List<AlipayBill> {
    val folder = DocumentFile.fromTreeUri(context, treeUri)
    val list = arrayListOf<AlipayBill>()
    folder?.listFiles()?.forEach {
        if (it.isDirectory && it.name.equals("alipay")) {
            it.listFiles().forEach { f ->
                if (f.isFile) {
                    list.add(AlipayBill(f.name, f.uri, it.uri))
                }
            }
        }
    }
    return list
}