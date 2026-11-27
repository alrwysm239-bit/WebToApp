package com.example.data.repository

import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.ProjectHistoryEntity
import com.example.data.model.AppExportConfig
import com.example.data.model.AppOrientation
import com.example.data.model.BackButtonBehavior
import com.example.data.model.HostingProvider
import com.example.data.model.ProjectAsset
import com.example.data.model.ProjectSnapshot
import com.example.data.model.ProjectSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class LoadedProject(
    val id: Long,
    val name: String,
    val htmlContent: String,
    val assets: List<ProjectAsset>,
    val appConfig: AppExportConfig,
    val publishedUrl: String?,
    val lastProvider: HostingProvider?,
    val isFavorite: Boolean,
    val updatedAt: Long,
)

class ProjectRepository(private val dao: ProjectDao) {

    val allProjects: Flow<List<ProjectSummary>> = dao.observeAllProjects().map { entities ->
        entities.map { entity ->
            ProjectSummary(
                id = entity.id,
                name = entity.name,
                updatedAt = entity.updatedAt,
                assetCount = countAssets(entity.assetsJson),
                htmlLength = entity.htmlContent.length,
                publishedUrl = entity.publishedUrl,
                lastProvider = HostingProvider.fromKey(entity.lastProviderKey),
                isFavorite = entity.isFavorite,
            )
        }
    }

    fun observeHistory(projectId: Long): Flow<List<ProjectSnapshot>> =
        dao.observeHistoryForProject(projectId).map { rows ->
            rows.map { row ->
                ProjectSnapshot(
                    id = row.id,
                    projectId = row.projectId,
                    label = row.label,
                    createdAt = row.createdAt,
                    htmlSizeBytes = row.htmlContent.toByteArray().size,
                )
            }
        }

    suspend fun loadProject(id: Long): LoadedProject? {
        val entity = dao.getProjectById(id) ?: return null
        return entity.toLoadedProject()
    }

    suspend fun saveProject(
        id: Long,
        name: String,
        htmlContent: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
        publishedUrl: String?,
        lastProvider: HostingProvider?,
        isFavorite: Boolean,
    ): Long {
        val entity = ProjectEntity(
            id = id,
            name = name.ifBlank { "WebForge Project" },
            htmlContent = htmlContent,
            assetsJson = encodeAssets(assets),
            appConfigJson = encodeConfig(config),
            publishedUrl = publishedUrl,
            lastProviderKey = lastProvider?.key,
            isFavorite = isFavorite,
            updatedAt = System.currentTimeMillis(),
        )
        return dao.upsertProject(entity)
    }

    suspend fun toggleFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    suspend fun saveSnapshot(
        projectId: Long,
        label: String,
        htmlContent: String,
        assets: List<ProjectAsset>,
    ) {
        if (projectId <= 0L) return
        dao.insertHistory(
            ProjectHistoryEntity(
                projectId = projectId,
                label = label,
                htmlContent = htmlContent,
                assetsJson = encodeAssets(assets),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun restoreSnapshot(snapshotId: Long): Pair<String, List<ProjectAsset>>? {
        val row = dao.getSnapshotById(snapshotId) ?: return null
        return row.htmlContent to decodeAssets(row.assetsJson)
    }

    suspend fun duplicateProject(projectId: Long): Long? {
        val original = dao.getProjectById(projectId) ?: return null
        val copy = original.copy(
            id = 0L,
            name = "${original.name} (Copy)",
            publishedUrl = null,
            isFavorite = false,
            updatedAt = System.currentTimeMillis(),
        )
        return dao.upsertProject(copy)
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deleteHistoryForProject(projectId)
        dao.deleteProjectById(projectId)
    }

    suspend fun clearAllProjects() {
        dao.deleteAllHistory()
        dao.deleteAllProjects()
    }

    suspend fun exportBackupJson(): String {
        val all = dao.getAllProjectsOnce()
        val arr = JSONArray()
        all.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("htmlContent", p.htmlContent)
                    put("assetsJson", p.assetsJson)
                    put("appConfigJson", p.appConfigJson)
                    put("publishedUrl", p.publishedUrl ?: "")
                    put("lastProviderKey", p.lastProviderKey ?: "")
                    put("isFavorite", p.isFavorite)
                    put("updatedAt", p.updatedAt)
                },
            )
        }
        return JSONObject().apply {
            put("app", "WebForge")
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())
            put("projects", arr)
        }.toString(2)
    }

    suspend fun importBackupJson(jsonText: String): Int {
        val root = JSONObject(jsonText)
        val arr = root.getJSONArray("projects")
        var imported = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val entity = ProjectEntity(
                id = 0L,
                name = obj.getString("name"),
                htmlContent = obj.getString("htmlContent"),
                assetsJson = obj.optString("assetsJson", "[]"),
                appConfigJson = obj.optString("appConfigJson", "{}"),
                publishedUrl = obj.optString("publishedUrl").takeIf { it.isNotEmpty() },
                lastProviderKey = obj.optString("lastProviderKey").takeIf { it.isNotEmpty() },
                isFavorite = obj.optBoolean("isFavorite", false),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            )
            dao.upsertProject(entity)
            imported++
        }
        return imported
    }

    private fun ProjectEntity.toLoadedProject(): LoadedProject = LoadedProject(
        id = id,
        name = name,
        htmlContent = htmlContent,
        assets = decodeAssets(assetsJson),
        appConfig = decodeConfig(appConfigJson, name),
        publishedUrl = publishedUrl,
        lastProvider = HostingProvider.fromKey(lastProviderKey),
        isFavorite = isFavorite,
        updatedAt = updatedAt,
    )

    private fun encodeConfig(config: AppExportConfig): String = JSONObject().apply {
        put("appName", config.appName)
        put("packageName", config.packageName)
        put("versionName", config.versionName)
        put("versionCode", config.versionCode)
        put("themeColorHex", config.themeColorHex)
        put("statusBarColorHex", config.statusBarColorHex)
        put("orientation", config.orientation.key)
        put("fullscreen", config.fullscreen)
        put("pullToRefresh", config.pullToRefresh)
        put("keepScreenOn", config.keepScreenOn)
        put("backButtonBehavior", config.backButtonBehavior.key)
        put("permissions", JSONArray(config.permissions.toList()))
        if (config.customIconBase64 != null) put("customIconBase64", config.customIconBase64)
        put("seoDescription", config.seoDescription)
        put("customDomain", config.customDomain)
        put("twaHost", config.twaHost)
        put("twaSha256", config.twaSha256)
        put("admobAppId", config.admobAppId)
    }.toString()

    private fun decodeConfig(json: String, fallbackName: String): AppExportConfig {
        if (json.isBlank() || json == "{}") return AppExportConfig(appName = fallbackName)
        return runCatching {
            val obj = JSONObject(json)
            val permsArr = obj.optJSONArray("permissions")
            val perms = if (permsArr != null) {
                buildSet {
                    for (i in 0 until permsArr.length()) add(permsArr.getString(i))
                }
            } else {
                setOf("INTERNET", "ACCESS_NETWORK_STATE")
            }
            AppExportConfig(
                appName = obj.optString("appName", fallbackName),
                packageName = obj.optString("packageName", "com.webforge.app"),
                versionName = obj.optString("versionName", "1.0.0"),
                versionCode = obj.optInt("versionCode", 1),
                themeColorHex = obj.optString("themeColorHex", "#5B3DF5"),
                statusBarColorHex = obj.optString("statusBarColorHex", "#0C0A20"),
                orientation = AppOrientation.fromKey(obj.optString("orientation")),
                fullscreen = obj.optBoolean("fullscreen", false),
                pullToRefresh = obj.optBoolean("pullToRefresh", true),
                keepScreenOn = obj.optBoolean("keepScreenOn", false),
                backButtonBehavior = BackButtonBehavior.fromKey(obj.optString("backButtonBehavior")),
                permissions = perms,
                customIconBase64 = if (obj.has("customIconBase64")) obj.getString("customIconBase64") else null,
                seoDescription = obj.optString("seoDescription", "Built with WebForge Studio"),
                customDomain = obj.optString("customDomain", ""),
                twaHost = obj.optString("twaHost", ""),
                twaSha256 = obj.optString("twaSha256", ""),
                admobAppId = obj.optString("admobAppId", ""),
            )
        }.getOrDefault(AppExportConfig(appName = fallbackName))
    }

    private fun encodeAssets(assets: List<ProjectAsset>): String {
        val array = JSONArray()
        assets.forEach { asset ->
            val obj = JSONObject().apply {
                put("path", asset.path)
                put("mimeType", asset.mimeType)
                if (asset.textContent != null) put("text", asset.textContent)
                if (asset.base64Content != null) put("base64", asset.base64Content)
                put("sizeBytes", asset.sizeBytes)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeAssets(json: String): List<ProjectAsset> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ProjectAsset(
                            path = obj.getString("path"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            textContent = if (obj.has("text")) obj.getString("text") else null,
                            base64Content = if (obj.has("base64")) obj.getString("base64") else null,
                            sizeBytes = obj.optInt("sizeBytes", 0),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun countAssets(json: String): Int {
        if (json.isBlank()) return 0
        return runCatching { JSONArray(json).length() }.getOrDefault(0)
    }
}
