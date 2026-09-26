package dev.dangbei2api.store

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dangbei_settings", Context.MODE_PRIVATE)

    var port: Int
        get() = prefs.getInt("port", 9980)
        set(v) = prefs.edit().putInt("port", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "sk-dangbei-local") ?: "sk-dangbei-local"
        set(v) = prefs.edit().putString("api_key", v).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", true)
        set(v) = prefs.edit().putBoolean("auto_start", v).apply()

    var showFloat: Boolean
        get() = prefs.getBoolean("show_float", false)
        set(v) = prefs.edit().putBoolean("show_float", v).apply()

    /** 全局默认联网开关 */
    var useSearch: Boolean
        get() = prefs.getBoolean("use_search", false)
        set(v) = prefs.edit().putBoolean("use_search", v).apply()

    /** 默认模型 id（对外） */
    var defaultModel: String
        get() = prefs.getString("default_model", "glm-5") ?: "glm-5"
        set(v) = prefs.edit().putString("default_model", v).apply()
}
