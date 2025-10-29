package com.akiestoy.beacons.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Modelo para representar un log de escaneo BLE
 */
data class BLEScanLog(
    val id: String = UUID.randomUUID().toString(), // ID único generado al crear el log
    val timestamp: Long = System.currentTimeMillis(),
    val deviceName: String,
    val macAddress: String,
    val rssi: Int,
    val txPower: Int?,
    val manufacturerData: Map<Int, String>,
    val serviceUuids: List<String>,
    val iBeaconData: IBeaconData? = null,
    val rawData: String? = null,
    val rawBytes: ByteArray? = null,
    val advertisingFlags: Int? = null,
    val isConnectable: Boolean? = null
) {
    fun getRawBytesHex(): String? {
        return rawBytes?.joinToString(" ") { "%02X".format(it) }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BLEScanLog

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
    fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getFormattedLog(): String {
        val sb = StringBuilder()
        sb.append("═══════════════════════════════\n")
        sb.append("⏰ ${getFormattedTimestamp()}\n")
        sb.append("📱 Nombre: $deviceName\n")
        sb.append("📍 MAC: $macAddress\n")
        sb.append("📶 RSSI: $rssi dBm\n")
        
        txPower?.let {
            sb.append("⚡ TX Power: $it dBm\n")
        }

        if (manufacturerData.isNotEmpty()) {
            sb.append("\n📦 Manufacturer Data:\n")
            manufacturerData.forEach { (id, data) ->
                sb.append("  ID: 0x${id.toString(16).uppercase()}\n")
                sb.append("  Data: $data\n")
            }
        }

        if (serviceUuids.isNotEmpty()) {
            sb.append("\n🔗 Service UUIDs:\n")
            serviceUuids.forEach { uuid ->
                sb.append("  $uuid\n")
            }
        }

        iBeaconData?.let {
            sb.append("\n✅ iBeacon Detectado!\n")
            sb.append("  UUID: ${it.uuid}\n")
            sb.append("  Major: ${it.major}\n")
            sb.append("  Minor: ${it.minor}\n")
            sb.append("  TX Power: ${it.txPower} dBm\n")
        }

        sb.append("═══════════════════════════════")
        return sb.toString()
    }
}

/**
 * Datos específicos de un iBeacon
 */
data class IBeaconData(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val txPower: Int
)

