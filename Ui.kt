package de.christian.weckersync

import org.json.JSONObject

class IoBrokerApi(private val c: Config) {
    private fun url(operation: String, query: String = ""): String {
        val auth = if (c.iobUser.isNotBlank()) "&user=${Config.encode(c.iobUser)}&pass=${Config.encode(c.iobPassword)}" else ""
        return "${if (c.iobHttps) "https" else "http"}://${c.iobHost}:${c.iobPort}/$operation/${Config.encode(c.stateId)}?$query$auth"
    }
    fun read(): Boolean = parseState(Network.success(Network.call(url("get"))))
    fun write(value: Boolean) {
        val result = JSONObject(Network.success(Network.call(url("set", "value=$value&type=boolean&ack=false"))))
        if (result.has("error")) throw ApiException("ioBroker hat das Schreiben abgelehnt. Datenpunkt und Rechte prüfen.")
        // Caller always reads back; an HTTP 200 alone does not confirm the new state.
    }
    companion object {
        fun parseState(body: String): Boolean {
            val json = JSONObject(body)
            if (json.has("error")) throw ApiException("ioBroker-Datenpunkt nicht lesbar. ID und Rechte prüfen.")
            if (json.has("q") && json.optInt("q", 0) != 0) throw ApiException("ioBroker meldet ungültige Datenqualität.")
            return json.opt("val") as? Boolean ?: throw ApiException("ioBroker muss einen echten Boolean liefern, keine Zahl oder Zeichenkette.")
        }
    }
}
