package com.lzh.smartbill.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.lzh.smartbill.ui.theme.STATUS
import com.lzh.smartbill.util.SAFUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * uri: 当前文件
 * parent：父目录
 */
@Composable
fun DecompressButton(
    uri: Uri
    , parent: Uri
    , onDepressionFinished: () -> Unit
) {
    val LOG_TAG = "DecompressButton"
    val scope = rememberCoroutineScope()
    var isDecompressing by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (error) {
        StatusButton(
            onClick = { showPasswordDialog = true }
            , "解压失败"
            , STATUS.ERROR
        )
    } else {
        if (isDecompressing) {
            StatusButton(
                onClick = { showPasswordDialog = true }
                , "压缩中..."
                , STATUS.ING
            )
        }
        else if (success){
            StatusButton(
                onClick = { showPasswordDialog = true }
                , "已解压"
                , STATUS.SUCCESS
            )
        }
        else {
            StatusButton(
                onClick = { showPasswordDialog = true }
                , "解压"
                , STATUS.NORMAL
            )
        }
    }


    if (showPasswordDialog) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("输入解压密码") },
            text = {
                OutlinedTextField(
                    value = password, // 显示密码
                    onValueChange = { password = it }, // 更新密码
                    label = { Text("密码") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    scope.launch {
                        isDecompressing = true
                        try {
                            val targetFolder = DocumentFile.fromTreeUri(context, parent)
                                ?: return@launch
                            SAFUtil.decompressZip(context, uri, targetFolder, password)
//                            isDecompressing = false
//                            success = true
                        } catch (e: Exception) {
//                            isDecompressing = false
//                            error = true
                            Log.e(LOG_TAG, e.message!!)
                        } finally {
//                            delay(2000.milliseconds)
//                            success = false
//                            error = false
                            isDecompressing = false
                            onDepressionFinished()
                        }
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 不同状态按钮显示
 */
@Composable
private fun StatusButton(onClick: () -> Unit, text: String, status: STATUS) {
    val buttonColors = when (status) {
        STATUS.SUCCESS -> buttonColors(
            containerColor = STATUS.SUCCESS.color1
        )
        STATUS.ERROR -> buttonColors(
            containerColor = STATUS.ERROR.color1
        )
        STATUS.ING -> buttonColors(
            containerColor = STATUS.ING.color1
            , contentColor = STATUS.ING.color2
        )
        STATUS.NORMAL -> buttonColors(
            containerColor = STATUS.NORMAL.color1
        )
    }
    Button(
        onClick = onClick
        , modifier = Modifier
            .defaultMinSize(5.dp, 5.dp)
        , contentPadding = PaddingValues(1.dp)
        , shape = MaterialTheme.shapes.extraSmall
        , colors = buttonColors
    ) {

        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}