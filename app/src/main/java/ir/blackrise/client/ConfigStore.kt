package ir.blackrise.client

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val name: String, val config: String)

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("blackrise", Context.MODE_PRIVATE)

    fun load(): List<Profile> = runCatching {
        val arr = JSONArray(prefs.getString("profiles", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Profile(o.getString("name"), o.getString("config"))
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("config", it.config)) }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    var selected: Int
        get() = prefs.getInt("selected", 0)
        set(v) { prefs.edit().putInt("selected", v).apply() }

    var language: String
        get() = prefs.getString("lang", "FA") ?: "FA"
        set(v) { prefs.edit().putString("lang", v).apply() }
}
