package com.lzh.smartbill.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.lzh.smartbill.components.DecompressButton
import com.lzh.smartbill.util.SAFUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun Home(innerPadding: PaddingValues) {
    val context = LocalContext.current
    var treeUri by remember { mutableStateOf(SAFUtil.getValidUri(context)) }
    var hasZip by remember { mutableStateOf(false) } // 是否有新增压缩包
    val alipayList = remember { mutableStateListOf<AlipayBill>() }
    var refreshCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val primaryRootUri = "content://com.android.externalstorage.documents/tree/primary".toUri()

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

    LaunchedEffect(treeUri, refreshCount) {
        treeUri?.let {
            hasZip = false
            alipayList.clear()
            val list = collectAlipay(context, it)
            list.forEach {
                if (it.name?.endsWith(".zip") == true) {
                    hasZip = true
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
                Button(onClick = {}) {
                    Text("分析账单")
                }
            }
        }
    }
}

@Composable
private fun BillItem(billList: List<Any>, onDepressionFinished: () -> Unit) {
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
