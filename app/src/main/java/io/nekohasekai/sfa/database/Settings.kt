package io.nekohasekai.sfa.database

import androidx.room.Room
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.bg.ProxyService
import io.nekohasekai.sfa.bg.VPNService
import io.nekohasekai.sfa.constant.Path
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.SettingsKey
import io.nekohasekai.sfa.database.preference.KeyValueDatabase
import io.nekohasekai.sfa.database.preference.RoomPreferenceDataStore
import io.nekohasekai.sfa.ktx.boolean
import io.nekohasekai.sfa.ktx.int
import io.nekohasekai.sfa.ktx.long
import io.nekohasekai.sfa.ktx.string
import io.nekohasekai.sfa.ktx.stringSet
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object Settings {

    private val instance by lazy {
        Application.application.getDatabasePath(Path.SETTINGS_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application,
            KeyValueDatabase::class.java,
            Path.SETTINGS_DATABASE_PATH
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }
    val dataStore = RoomPreferenceDataStore(instance.keyValuePairDao())
    var selectedProfile by dataStore.long(SettingsKey.SELECTED_PROFILE) { -1L }
    var serviceMode by dataStore.string(SettingsKey.SERVICE_MODE) { ServiceMode.NORMAL }
    var startedByUser by dataStore.boolean(SettingsKey.STARTED_BY_USER)

    var checkUpdateEnabled by dataStore.boolean(SettingsKey.CHECK_UPDATE_ENABLED) { true }
    var disableMemoryLimit by dataStore.boolean(SettingsKey.DISABLE_MEMORY_LIMIT)
    var dynamicNotification by dataStore.boolean(SettingsKey.DYNAMIC_NOTIFICATION) { true }


    const val PER_APP_PROXY_DISABLED = 0
    const val PER_APP_PROXY_EXCLUDE = 1
    const val PER_APP_PROXY_INCLUDE = 2

    var perAppProxyEnabled by dataStore.boolean(SettingsKey.PER_APP_PROXY_ENABLED) { false }
    var perAppProxyMode by dataStore.int(SettingsKey.PER_APP_PROXY_MODE) { PER_APP_PROXY_EXCLUDE }
    var perAppProxyList by dataStore.stringSet(SettingsKey.PER_APP_PROXY_LIST) { emptySet() }
    var perAppProxyUpdateOnChange by dataStore.int(SettingsKey.PER_APP_PROXY_UPDATE_ON_CHANGE) { PER_APP_PROXY_DISABLED }

    var systemProxyEnabled by dataStore.boolean(SettingsKey.SYSTEM_PROXY_ENABLED) { true }

    fun serviceClass(): Class<*> {
        return when (serviceMode) {
            ServiceMode.VPN -> VPNService::class.java
            else -> ProxyService::class.java
        }
    }

    suspend fun rebuildServiceMode(): Boolean {
        var newMode = ServiceMode.NORMAL
        try {
            if (needVPNService()) {
                newMode = ServiceMode.VPN
            }
        } catch (_: Exception) {
        }
        if (serviceMode == newMode) {
            return false
        }
        serviceMode = newMode
        return true
    }

    private suspend fun needVPNService(): Boolean {
        val selectedProfileId = selectedProfile
        if (selectedProfileId == -1L) return false
        val profile = ProfileManager.get(selectedProfile) ?: return false

        val content = Yaml.default.parseToYamlNode(File(profile.typed.path).readText())
        return content.yamlMap.get<YamlList>("inbounds")?.items?.any {
            it.yamlMap.get<YamlScalar>("type")?.content == "tun"
        } ?: false
    }

}