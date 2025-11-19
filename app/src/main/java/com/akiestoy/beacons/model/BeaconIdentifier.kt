package com.akiestoy.beacons.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Identificador único de un beacon usando UUID, major y minor
 * Se usa para almacenar beacons favoritos con toda su información
 */
data class BeaconIdentifier(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val macAddress: String? = null  // Opcional, para compatibilidad
) {
    /**
     * Compara si este identificador coincide con otro beacon
     * La comparación se hace SOLO por UUID, major y minor (ignora MAC)
     */
    fun matches(other: BeaconIdentifier): Boolean {
        return uuid.lowercase() == other.uuid.lowercase() &&
                major == other.major &&
                minor == other.minor
    }

    /**
     * Compara si este identificador coincide con los datos de un iBeacon
     */
    fun matches(uuid: String, major: Int, minor: Int): Boolean {
        return this.uuid.lowercase() == uuid.lowercase() &&
                this.major == major &&
                this.minor == minor
    }

    /**
     * Genera una clave única para este beacon
     * Formato: uuid:major:minor
     */
    fun toKey(): String {
        return "${uuid.lowercase()}:$major:$minor"
    }

    companion object {
        /**
         * Crea un BeaconIdentifier desde una clave
         * Formato esperado: uuid:major:minor
         */
        fun fromKey(key: String): BeaconIdentifier? {
            return try {
                val parts = key.split(":")
                if (parts.size >= 3) {
                    BeaconIdentifier(
                        uuid = parts[0],
                        major = parts[1].toInt(),
                        minor = parts[2].toInt()
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Crea un BeaconIdentifier desde un BLEScanLog
         */
        fun fromScanLog(scanLog: BLEScanLog): BeaconIdentifier? {
            val iBeacon = scanLog.iBeaconData ?: return null
            return BeaconIdentifier(
                uuid = iBeacon.uuid,
                major = iBeacon.major,
                minor = iBeacon.minor,
                macAddress = scanLog.macAddress
            )
        }

        /**
         * Crea un BeaconIdentifier desde un RegisteredBeacon
         */
        fun fromRegisteredBeacon(beacon: RegisteredBeacon): BeaconIdentifier {
            return BeaconIdentifier(
                uuid = beacon.advUuid,
                major = beacon.major,
                minor = beacon.minor
            )
        }

        /**
         * Serializa una lista de BeaconIdentifier a JSON
         */
        fun toJson(identifiers: Set<BeaconIdentifier>): String {
            return Gson().toJson(identifiers)
        }

        /**
         * Deserializa una lista de BeaconIdentifier desde JSON
         */
        fun fromJson(json: String): Set<BeaconIdentifier> {
            return try {
                val type = object : TypeToken<Set<BeaconIdentifier>>() {}.type
                Gson().fromJson(json, type) ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
        }
    }
}
