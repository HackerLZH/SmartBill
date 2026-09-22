package com.lzh.smartbill.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import java.io.BufferedOutputStream
import java.io.File
import java.util.UUID

/**
 * SAF工具类
 */
object SAFUtil {
    private const val PREF = "SAF"
    private const val KEY_TREE_URI = "tree_uri"

    /**
     * 获取tree uri，无效则返回null
     */
    fun getValidUri(context: Context): Uri? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val uri = sp.getString(KEY_TREE_URI, null)?.toUri() ?: return null
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return if (hasPermission) uri else {
            // 清空uri记录
            sp.edit { remove(KEY_TREE_URI) }
            null
        }
    }

    /**
     * 保存读写权限，持久化uri
     */
    fun savePermission(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri
            , Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_TREE_URI, uri.toString())
                apply()
            }
    }

    /**
     * 解压zip（支付宝带密码）
     */
    suspend fun decompressZip(
        context: Context,
        zipUri: Uri,
        targetFolder: DocumentFile,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        // 1. 将 SAF URI 复制到临时文件（Zip4j 需要 File 对象）
        val tempZip = File.createTempFile(UUID.randomUUID().toString(), ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                tempZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 2. 用 Zip4j 打开
            val zipFile = ZipFile(tempZip)
            if (zipFile.isEncrypted) {
                if (password.isNullOrEmpty()) {
                    throw IllegalArgumentException("此 ZIP 需要密码")
                }
                zipFile.setPassword(password.toCharArray())
            }

            // 3. 遍历条目并解压
            val fileHeaders: List<FileHeader> = zipFile.fileHeaders
            for (header in fileHeaders) {
                val entryName = header.fileName
                if (header.isDirectory) {
                    createDirectoryRecursive(targetFolder, entryName)
                } else {
                    // 逐级创建父目录
                    val parentFolder = createDirectoryRecursive(
                        targetFolder,
                        entryName.substringBeforeLast('/', "")
                    )
                    val fileName = entryName.substringAfterLast('/')
                    val newFile = parentFolder.createFile(
                        getMimeType(fileName), fileName
                    ) ?: continue

                    // 4. 读取解密后的流，写入 SAF
                    try {
                        zipFile.getInputStream(header).use { inputStream ->
                            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                                BufferedOutputStream(outputStream).use { bufOut ->
                                    inputStream.copyTo(bufOut)
                                }
                            }
                        }
                    } catch (e: ZipException) {
                        if (e.message?.contains("Wrong Password", ignoreCase = true) == true) {
                            // 提示用户重新输入
                            showError(context, "密码错误，请重新输入")
                        } else {
                            // 其他 ZipException（如文件损坏、CRC 校验失败）
                            showError(context, "解压失败: ${e.message}")
                        }
                        newFile.delete()
                        throw e
                    } catch (e: Exception) {
                        showError(context, "未知错误: ${e.message}")
                        newFile.delete()
                        throw e
                    }

                    // 删除原zip
                    DocumentFile.fromTreeUri(context, zipUri)?.delete()
                }
            }
        } finally {
            // 5. 清理临时文件
            tempZip.delete()
        }
    }

    private suspend fun showError(context: Context, string: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, string, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 递归创建目录
     */
    private fun createDirectoryRecursive(
        root: DocumentFile,
        path: String
    ): DocumentFile {
        if (path.isBlank()) return root
        var current = root
        path.split("/").filter { it.isNotBlank() }.forEach { dirName ->
            current = current.findFile(dirName) // 当前目录查找子目录
                ?: current.createDirectory(dirName) // 找不到则创建
                        ?: current // 创建失败保持当前目录不变
        }
        return current
    }

    /**
     * MIME 类型推断
     */
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }
}