package com.tharunbirla.librecuts.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 一条「最近项目」记录。
 *
 * 只存 URI 与轻量元信息，**不缓存视频内容、不生成缩略图**，
 * 因此不会触碰任何解码 / FFmpeg / Media3 逻辑。
 */
data class ProjectEntry(
    val uri: String,
    val name: String,
    val lastModified: Long,
    val sizeBytes: Long = -1L
)

/**
 * 最近项目索引的本地持久化。
 *
 * 实现要点：
 *  - 复用项目已有的 Gson（[ProjectSerializer.gson]），**不引入任何新依赖**；
 *  - 落在应用私有目录 `filesDir/projects_index.json`，无需存储权限；
 *  - 全部方法对异常做兜底，读写失败绝不崩溃，最坏情况只是列表为空。
 */
object ProjectStore {

    private const val TAG = "ProjectStore"
    private const val FILE_NAME = "projects_index.json"
    private const val MAX_ENTRIES = 100

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** 读取全部记录（按最近使用倒序，读失败返回空列表）。 */
    fun load(context: Context): List<ProjectEntry> {
        return try {
            val f = file(context)
            if (!f.exists()) return emptyList()
            val type = object : TypeToken<List<ProjectEntry>>() {}.type
            val list: List<ProjectEntry>? = ProjectSerializer.gson.fromJson(f.readText(), type)
            list ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "load failed: ${e.message}", e)
            emptyList()
        }
    }

    /** 写入/更新一条记录并置顶，返回最新列表。 */
    fun upsert(context: Context, entry: ProjectEntry): List<ProjectEntry> {
        val list = load(context).filter { it.uri != entry.uri }.toMutableList()
        list.add(0, entry)
        val trimmed = list.take(MAX_ENTRIES)
        save(context, trimmed)
        return trimmed
    }

    /** 移除一条记录，返回最新列表。 */
    fun remove(context: Context, uri: String): List<ProjectEntry> {
        val list = load(context).filter { it.uri != uri }
        save(context, list)
        return list
    }

    private fun save(context: Context, list: List<ProjectEntry>) {
        try {
            file(context).writeText(ProjectSerializer.gson.toJson(list))
        } catch (e: Exception) {
            Log.e(TAG, "save failed: ${e.message}", e)
        }
    }

    /**
     * 记录「刚保存好的工程」。
     *
     * 由 VideoEditingActivity 在存盘成功后调用（唯一的逻辑侧接入点）。
     * 同时尝试获取持久化 URI 权限，否则 App 重启后记录会变成不可打开的死链。
     */
    fun recordSavedProject(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Log.d(TAG, "takePersistableUriPermission skipped: ${e.message}")
        }
        try {
            val (name, size) = queryMeta(context, uri)
            upsert(
                context,
                ProjectEntry(
                    uri = uri.toString(),
                    name = name,
                    lastModified = System.currentTimeMillis(),
                    sizeBytes = size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "recordSavedProject failed: ${e.message}", e)
        }
    }

    /** 从 ContentResolver 读取显示名与体积；拿不到就退回 URI 末段。 */
    fun queryMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "queryMeta failed: ${e.message}")
        }
        return (name ?: uri.lastPathSegment ?: "project.lcprj") to size
    }

    /** 记录是否仍可访问（用户可能在文件管理器里删掉了工程文件）。 */
    fun isReachable(context: Context, uriString: String): Boolean {
        return try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
