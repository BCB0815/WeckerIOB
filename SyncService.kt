package de.christian.weckersync

import org.json.JSONObject

class HueApi(private val c: Config) {
    private val root = "https://${c.hueHost}/clip/v2/resource/behavior_instance"
    private fun request(path: String, method: String = "GET", body: String? = null): JSONObject {
        val json = JSONObject(Network.success(Network.call(root + path, method, body,
            mapOf("hue-application-key" to c.hueToken, "Content-Type" to "application/json"), c.huePin)))
        val errors = json.optJSONArray("errors") ?: throw ApiException("Hue-Antwort ohne errors-Feld.")
        if (errors.length() != 0) throw ApiException("Hue-API lehnt Anfrage ab. Token und Routine-ID prüfen.")
        return json
    }
    fun read(): Boolean {
        val data = request("/${c.hueId}").getJSONArray("data")
        if (data.length() != 1) throw ApiException("Hue-Routine nicht eindeutig gefunden.")
        val item = data.getJSONObject(0)
        if (item.optString("id") != c.hueId || item.optString("type") != "behavior_instance") {
            throw ApiException("Hue hat eine unerwartete Routine geliefert.")
        }
        return item.opt("enabled") as? Boolean ?: throw ApiException("Hue-Routine hat keinen enabled-Status.")
    }
    fun reconcile(target: Boolean) {
        if (read() != target) {
            request("/${c.hueId}", "PUT", JSONObject().put("enabled", target).toString())
            if (read() != target) throw ApiException("Hue hat den gewünschten Zustand noch nicht bestätigt; erneuter Versuch folgt.")
        }
    }
    fun list(): List<Pair<String, String>> {
        val a = request("").getJSONArray("data")
        return (0 until a.length()).map { index ->
            val item = a.getJSONObject(index)
            item.getString("id") to (item.optJSONObject("metadata")?.optString("name")?.takeIf { it.isNotBlank() } ?: "Unbenannte Routine")
        }
    }
}
