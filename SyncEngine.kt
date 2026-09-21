package de.christian.weckersync

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

/** An immutable configuration snapshot is used for each synchronization cycle. */
data class Config(
    val iobHost: String = "192.168.178.59",
    val iobPort: String = "8087",
    val stateId: String = "0_userdata.0.Wecker.aktiv",
    val iobHttps: Boolean = false,
    val iobUser: String = "",
    val iobPassword: String = "",
    val hueHost: String = "",
    val hueToken: String = "",
    val hueId: String = "",
    val huePin: String = "",
    val sonosHost: String = "192.168.178.42",
    val alarmId: String = "1"
) {
    fun validate() {
        listOf(iobHost, hueHost, sonosHost).forEach(::validateHost)
        require(iobPort.toIntOrNull()?.let { it in 1..65535 } == true) { "ioBroker-Port: 1 bis 65535 erforderlich." }
        require(stateId.isNotBlank() && !stateId.contains(',') && !stateId.contains(';')) { "Genau eine Datenpunkt-ID eingeben." }
        require(hueToken.isNotBlank()) { "Hue API-Key fehlt." }
        require(hueId.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))) { "Hue Routine-ID muss eine v2 UUID sein." }
        require(huePin.matches(Regex("[a-fA-F0-9]{64}"))) { "Hue-Zertifikat bitte zuerst koppeln." }
        require(alarmId.matches(Regex("[0-9]+"))) { "Sonos Alarm-ID muss numerisch sein." }
        require(iobUser.isNotBlank() || iobPassword.isBlank()) { "ioBroker-Benutzer zum Passwort angeben." }
    }
    companion object {
        fun validateHost(host: String) {
            require(host.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?"))) {
                "IP oder Hostname ohne http://, Port oder Pfad eingeben (IPv4/Hostname)."
            }
        }
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    private val secrets = SecretStore()
    fun load(): Config {
        val raw = prefs.getString("encrypted", null) ?: return Config()
        val j = JSONObject(secrets.decrypt(raw))
        return Config(j.getString("iobHost"), j.getString("iobPort"), j.getString("stateId"),
            j.getBoolean("iobHttps"), j.getString("iobUser"), j.getString("iobPassword"),
            j.getString("hueHost"), j.getString("hueToken"), j.getString("hueId"),
            j.getString("huePin"), j.getString("sonosHost"), j.getString("alarmId"))
    }
    fun save(c: Config) {
        c.validate()
        val j = JSONObject().put("iobHost", c.iobHost).put("iobPort", c.iobPort)
            .put("stateId", c.stateId).put("iobHttps", c.iobHttps).put("iobUser", c.iobUser)
            .put("iobPassword", c.iobPassword).put("hueHost", c.hueHost).put("hueToken", c.hueToken)
            .put("hueId", c.hueId).put("huePin", c.huePin).put("sonosHost", c.sonosHost).put("alarmId", c.alarmId)
        check(prefs.edit().putString("encrypted", secrets.encrypt(j.toString())).commit()) { "Speichern fehlgeschlagen." }
    }
}
