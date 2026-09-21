package de.christian.weckersync

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import java.util.concurrent.Executors

class SettingsActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val inputs = linkedMapOf<String, EditText>()
    private lateinit var https: Switch
    private lateinit var status: TextView
    private lateinit var pinLabel: TextView
    private var approvedPin = ""
    private var approvedHost = ""
    private var nextId = 100
    private var working = false
    private val buttons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (SyncService.running) { finish(); return }
        val c = try { ConfigStore(this).load() } catch (_: Exception) {
            AlertDialog.Builder(this).setMessage("Gespeicherte Konfiguration konnte nicht entschlüsselt werden. Bitte neu eingeben.").setPositiveButton("OK", null).show()
            Config()
        }
        approvedPin = savedInstanceState?.getString("approvedPin") ?: c.huePin
        approvedHost = savedInstanceState?.getString("approvedHost") ?: c.hueHost
        val root = Ui.page(this, "Einstellungen")
        Ui.text(this, root, "ioBroker ist die zentrale Vorgabe. Beim Start werden Hue und Sonos an diesen Zustand angepasst.", 14)
        fun field(key: String, label: String, value: String, secret: Boolean = false) {
            inputs[key] = Ui.field(this, root, label, value, nextId++, secret)
        }
        fun button(label: String, action: () -> Unit) {
            buttons += Ui.button(this, root, label, action)
        }
        Ui.text(this, root, "ioBroker · Simple-API", 22)
        field("iobHost", "Server-IP / Hostname", c.iobHost)
        field("iobPort", "Simple-API-Port", c.iobPort)
        field("stateId", "Boolean-Datenpunkt-ID (Groß-/Kleinschreibung beachten)", c.stateId)
        https = Switch(this).apply { id = nextId++; text = "HTTPS für ioBroker"; isChecked = c.iobHttps; root.addView(this) }
        field("iobUser", "Benutzer (optional)", c.iobUser)
        field("iobPassword", "Passwort (optional)", c.iobPassword, true)
        Ui.text(this, root, "HTTP ist für das vertrauenswürdige Heimnetz vorgesehen. Bei HTTP werden auch optionale Zugangsdaten unverschlüsselt übertragen. HTTPS setzt ein vom Handy anerkanntes Serverzertifikat voraus.", 14)
        button("ioBroker-Verbindung lesen") {
            val config = form()
            async({
                Config.validateHost(config.iobHost)
                require(config.iobPort.toIntOrNull()?.let { it in 1..65535 } == true) { "Port ungültig." }
                require(config.stateId.isNotBlank()) { "Datenpunkt fehlt." }
                IoBrokerApi(config).read()
            }) { value -> status.text = "ioBroker erreichbar: ${if (value) "AN" else "AUS"}. Keine Änderung vorgenommen." }
        }
        Ui.text(this, root, "Philips Hue", 22)
        field("hueHost", "Bridge-IP / Hostname", c.hueHost)
        field("hueToken", "API-Key / User Token", c.hueToken, true)
        field("hueId", "Routine-ID (behavior_instance UUID)", c.hueId)
        pinLabel = Ui.text(this, root, if (approvedPin.isBlank()) "Noch kein Bridge-Zertifikat bestätigt" else "Zertifikat für $approvedHost bestätigt", 14)
        button("Hue-Zertifikat koppeln") {
            val host = text("hueHost")
            async({ Network.inspectHue(host) }) { pin ->
                AlertDialog.Builder(this).setTitle("Bridge-Zertifikat bestätigen")
                    .setMessage("Adresse: $host\n\nSHA-256:\n$pin\n\nDie erste Kopplung vertraut dem aktuell erreichbaren Gerät. Nur im eigenen vertrauenswürdigen Netz bestätigen. Für eine unabhängige Prüfung den Fingerabdruck mit dem Bridge-Zertifikat vergleichen.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Bestätigen") { _, _ ->
                        approvedHost = host; approvedPin = pin
                        pinLabel.text = "Zertifikat für $host bestätigt. Einstellungen noch speichern."
                    }.show()
            }
        }
        button("Hue-Routine auswählen") {
            val config = form()
            async({
                Config.validateHost(config.hueHost)
                require(config.huePin.isNotBlank() && config.hueToken.isNotBlank()) { "Zuerst API-Key eingeben und Bridge koppeln." }
                HueApi(config).list()
            }) { routines ->
                if (routines.isEmpty()) status.text = "Keine Hue behavior_instance gefunden. Routine zuerst in der Hue-App anlegen."
                else AlertDialog.Builder(this).setTitle("Aufwachroutine auswählen")
                    .setItems(routines.map { "${it.second}\n${it.first}" }.toTypedArray()) { _, which -> inputs.getValue("hueId").setText(routines[which].first) }
                    .setNegativeButton("Abbrechen", null).show()
            }
        }
        Ui.text(this, root, "Sonos", 22)
        field("sonosHost", "Lautsprecher-IP / Hostname", c.sonosHost)
        field("alarmId", "Alarm-ID", c.alarmId)
        button("Sonos-Wecker auswählen") {
            val config = form()
            async({ Config.validateHost(config.sonosHost); SonosApi(config).list() }) { alarms ->
                if (alarms.isEmpty()) status.text = "Keine Sonos-Wecker gefunden. Bitte zuerst in der Sonos-App anlegen."
                else AlertDialog.Builder(this).setTitle("Vorhandenen Sonos-Wecker auswählen")
                    .setItems(alarms.map { "ID ${it["ID"]} · ${it["StartTime"]} · ${it["Recurrence"]}\nRaum ${it["RoomUUID"]} · Lautstärke ${it["Volume"]}" }.toTypedArray()) { _, which ->
                        inputs.getValue("alarmId").setText(alarms[which].getValue("ID"))
                    }.setNegativeButton("Abbrechen", null).show()
            }
        }
        status = Ui.text(this, root, "Verbindungstests lesen nur Daten. Die Geräte werden erst bei gestarteter Überwachung geschaltet.", 14)
        button("Speichern") {
            try {
                check(!SyncService.running) { "Überwachung zuerst stoppen." }
                ConfigStore(this).save(form())
                Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) { status.text = Network.userError(e) }
        }
        button("Zurück") { finish() }
    }
    private fun text(key: String): String = inputs.getValue(key).text.toString().trim()
    private fun form(): Config = Config(text("iobHost"), text("iobPort"), text("stateId"), https.isChecked,
        text("iobUser"), inputs.getValue("iobPassword").text.toString(), text("hueHost"), text("hueToken"), text("hueId"),
        if (text("hueHost") == approvedHost) approvedPin else "", text("sonosHost"), text("alarmId"))
    private fun <T> async(task: () -> T, done: (T) -> Unit) {
        if (working) return
        working = true
        buttons.forEach { it.isEnabled = false }
        status.text = "Verbindung wird geprüft …"
        worker.execute {
            val result = runCatching(task)
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    working = false
                    buttons.forEach { it.isEnabled = true }
                    result.fold({ status.text = "Abfrage abgeschlossen"; done(it) }, {
                        status.text = Network.userError(it as? Exception ?: Exception())
                    })
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("approvedPin", approvedPin); outState.putString("approvedHost", approvedHost)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
