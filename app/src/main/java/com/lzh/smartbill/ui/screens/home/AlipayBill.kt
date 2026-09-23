package com.lzh.smartbill.ui.screens.home

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable // 用于WorkManager序列化传输
data class AlipayBill(var name: String?, val uri: Uri, val parent: Uri)
