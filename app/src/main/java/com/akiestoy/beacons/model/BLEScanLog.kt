package com.akiestoy.beacons.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelo para representar un log de escaneo BLE
 */
data class BLEScanLog(
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

        if (timestamp != other.timestamp) return false
        if (deviceName != other.deviceName) return false
        if (macAddress != other.macAddress) return false
        if (rssi != other.rssi) return false
        if (txPower != other.txPower) return false
        if (manufacturerData != other.manufacturerData) return false
        if (serviceUuids != other.serviceUuids) return false
        if (iBeaconData != other.iBeaconData) return false
        if (rawData != other.rawData) return false
        if (rawBytes != null) {
            if (other.rawBytes == null) return false
            if (!rawBytes.contentEquals(other.rawBytes)) return false
        } else if (other.rawBytes != null) return false
        if (advertisingFlags != other.advertisingFlags) return false
        if (isConnectable != other.isConnectable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + deviceName.hashCode()
        result = 31 * result + macAddress.hashCode()
        result = 31 * result + rssi
        result = 31 * result + (txPower ?: 0)
        result = 31 * result + manufacturerData.hashCode()
        result = 31 * result + serviceUuids.hashCode()
        result = 31 * result + (iBeaconData?.hashCode() ?: 0)
        result = 31 * result + (rawData?.hashCode() ?: 0)
        result = 31 * result + (rawBytes?.contentHashCode() ?: 0)
        result = 31 * result + (advertisingFlags ?: 0)
        result = 31 * result + (isConnectable?.hashCode() ?: 0)
        return result
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

