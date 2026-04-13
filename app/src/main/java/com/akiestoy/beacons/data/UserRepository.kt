package com.akiestoy.beacons.data

import android.util.Log
import com.akiestoy.beacons.model.api.LoginRequest
import com.akiestoy.beacons.model.api.UpdateEmployeeRequest
import com.akiestoy.beacons.model.user.User
import com.akiestoy.beacons.network.AuthApiService
import kotlinx.coroutines.flow.Flow

/** Repositorio para gestionar datos de usuario y autenticación */
class UserRepository(
        private val userDao: UserDao,
        private val zoneDao: ZoneDao,
        private val registeredBeaconDao: RegisteredBeaconDao,
        private val pendingEventDao: PendingEventDao,
        private val pendingZoneEventDao: PendingZoneEventDao,
        private val authApiService: AuthApiService,
        private val zonesApiService: com.akiestoy.beacons.network.ZonesApiService,
        private val favoritesRepository: FavoritesRepository
) {

    private val TAG = "UserRepository"

    val currentUser: Flow<User?> = userDao.getCurrentUser()

    suspend fun getCurrentUserOnce(): User? {
        return userDao.getCurrentUserOnce()
    }

    /**
     * Realiza login contra el API y guarda los datos del usuario
     * @return Pair<Boolean, String> (éxito, mensaje de error si aplica)
     */
    suspend fun login(rut: String, rutEmpresa: String, deviceId: String): Pair<Boolean, String> {
        return try {
            Log.i(TAG, "📤 ===== INICIANDO LOGIN =====")
            Log.i(TAG, "📤 RUT enviado: $rut")
            Log.i(TAG, "📤 RUT Empresa enviado: $rutEmpresa")

            val request = LoginRequest(rut = rut, rutEmpresa = rutEmpresa)
            Log.i(TAG, "📤 Request JSON: rut=$rut, rutEmpresa=$rutEmpresa")

            Log.i(TAG, "🌐 Enviando petición POST a API...")
            val response = authApiService.mobileLogin(request)

            Log.i(TAG, "\u27a1\ufe0f HTTP Status mobile-login: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val loginData = response.body()!!
                Log.i(TAG, "📥 Body recibido - Employee ID: ${loginData.employee.id}")
                Log.i(TAG, "📥 Body recibido - Employee Name: ${loginData.employee.name}")
                Log.i(TAG, "📥 Body recibido - Device ID: ${loginData.device.deviceId}")

                // Calcular timestamp de expiración del token
                val tokenExpiresAt = System.currentTimeMillis() + (loginData.expiresIn * 1000)

                // Crear el usuario con todos los datos
                val user =
                        User(
                                id = loginData.employee.id,
                                rut = loginData.employee.rut,
                                name = loginData.employee.name,
                                email = loginData.employee.email,
                                phone = "", // No viene en el API, se puede actualizar después
                                companyId = loginData.employee.companyId,
                                companyRut = rutEmpresa, // Guardar RUT de empresa para re-login
                                tenantId = loginData.employee.tenantId,
                                active = loginData.employee.active,
                                consentTracking = loginData.employee.consentTracking,
                                accessToken = loginData.accessToken,
                                tokenExpiresAt = tokenExpiresAt,
                                deviceId = loginData.device.deviceId,
                                hmacKey = loginData.device.hmacKey,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                        )

                // Guardar en la base de datos
                saveUser(user)

                Log.i(TAG, "✅ Login exitoso para: ${user.name}")

                // Obtener zonas y guardarlas en la base de datos
                try {
                    val zonesResponse =
                            zonesApiService.getZones(
                                    tenantId = user.tenantId,
                                    companyId = user.companyId
                            )

                    Log.i(TAG, "➡️ HTTP Status zonas: ${zonesResponse.code()}")

                    if (zonesResponse.isSuccessful && zonesResponse.body() != null) {
                        val zonesData = zonesResponse.body()!!
                        Log.i(TAG, "✅ Se obtuvieron ${zonesData.size} zonas")

                        // Convertir ZoneResponse a entidades Zone
                        val zones =
                                zonesData.map { zoneResponse ->
                                    com.akiestoy.beacons.model.Zone(
                                            id = zoneResponse.id,
                                            tenantId = zoneResponse.tenantId,
                                            companyId = zoneResponse.companyId,
                                            name = zoneResponse.name,
                                            type = zoneResponse.type,
                                            rssiThresholdNear = zoneResponse.rssiThresholdNear,
                                            rssiThresholdFar = zoneResponse.rssiThresholdFar,
                                            createdAt = System.currentTimeMillis(),
                                            updatedAt = System.currentTimeMillis()
                                    )
                                }

                        // Guardar las zonas en la base de datos
                        zoneDao.insertZones(zones)
                        Log.i(TAG, "✅ Se guardaron ${zones.size} zonas en la base de datos")

                        // Extraer y guardar todos los beacons de las zonas
                        val allBeacons = zonesData.flatMap { zoneResponse ->
                            zoneResponse.beacons.map { beaconResponse ->
                                com.akiestoy.beacons.model.RegisteredBeacon(
                                    id = beaconResponse.id,
                                    tenantId = beaconResponse.tenantId,
                                    companyId = beaconResponse.companyId,
                                    advUuid = beaconResponse.advUuid.lowercase(),
                                    mac = beaconResponse.mac,
                                    beaconName = beaconResponse.beaconName,
                                    major = beaconResponse.major,
                                    minor = beaconResponse.minor,
                                    txPower = beaconResponse.txPower,
                                    model = beaconResponse.model,
                                    beaconType = beaconResponse.beaconType,
                                    status = beaconResponse.status,
                                    zoneName = beaconResponse.zoneName,
                                    zoneId = beaconResponse.zoneId,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                        }

                        registeredBeaconDao.insertBeacons(allBeacons)
                        Log.i(TAG, "✅ Se guardaron ${allBeacons.size} beacons en la base de datos")
                        allBeacons.forEach { beacon ->
                            Log.i(TAG, "   📍 Beacon: ${beacon.beaconName ?: beacon.zoneName} (UUID: ${beacon.advUuid}, MAC: ${beacon.mac ?: "N/A"}, major: ${beacon.major}, minor: ${beacon.minor})")
                        }

                        // Los beacons se marcarán como favoritos automáticamente cuando se detecten
                        Log.i(TAG, "ℹ️ Los beacons se marcarán como favoritos automáticamente al ser detectados")
                    } else {
                        Log.e(TAG, "❌ Error al obtener zonas: ${zonesResponse.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Excepción al obtener zonas", e)
                }

                Pair(true, "")
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                Log.e(TAG, "❌ ===== ERROR EN LOGIN =====")
                Log.e(TAG, "❌ HTTP Code: ${response.code()}")
                Log.e(TAG, "❌ Error Body: $errorMsg")
                Log.e(TAG, "❌ Headers: ${response.headers()}")
                Pair(false, "Error al iniciar sesión: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ===== EXCEPCIÓN EN LOGIN =====")
            Log.e(TAG, "❌ Tipo: ${e.javaClass.simpleName}")
            Log.e(TAG, "❌ Mensaje: ${e.message}")
            Log.e(TAG, "❌ Stack trace:", e)
            Pair(false, "Error de conexión: ${e.message}")
        }
    }

    suspend fun saveUser(user: User) {
        userDao.insertUser(user)
    }

    /**
     * Limpia la sesión del usuario y todos los datos de Room asociados al tenant.
     * Previene data cruzada entre sesiones cuando un dispositivo cambia de empleado
     * o de empresa (ej. logout + login con otro RUT empresa).
     */
    suspend fun clearUser() {
        Log.i(TAG, "🧹 Limpiando sesión y datos locales del tenant")
        userDao.deleteAll()
        zoneDao.deleteAll()
        registeredBeaconDao.deleteAll()
        pendingEventDao.deleteAll()
        pendingZoneEventDao.deleteAll()
        favoritesRepository.clearFavorites()
        Log.i(TAG, "✅ Limpieza completada")
    }

    /** Verifica si el token de acceso está vigente */
    suspend fun isTokenValid(): Boolean {
        val user = getCurrentUserOnce()
        return user != null && user.tokenExpiresAt > System.currentTimeMillis()
    }

    /**
     * Actualiza los datos de un empleado en el servidor
     * @return Pair<Boolean, String> (éxito, mensaje de error si aplica)
     */
    suspend fun updateEmployee(
        employeeId: String,
        name: String,
        email: String,
        active: Boolean,
        consentTracking: Boolean
    ): Pair<Boolean, String> {
        return try {
            Log.i(TAG, "📤 ===== ACTUALIZANDO EMPLEADO =====")
            Log.i(TAG, "📤 Employee ID: $employeeId")
            Log.i(TAG, "📤 Nombre: $name")
            Log.i(TAG, "📤 Email: $email")
            Log.i(TAG, "📤 Active: $active")
            Log.i(TAG, "📤 ConsentTracking: $consentTracking")

            val request = UpdateEmployeeRequest(
                name = name,
                email = email,
                active = active,
                consentTracking = consentTracking
            )

            Log.i(TAG, "🌐 Enviando petición PUT a API...")
            val response = authApiService.updateEmployee(employeeId, request)

            Log.i(TAG, "➡️ HTTP Status update-employee: ${response.code()}")

            if (response.isSuccessful) {
                Log.i(TAG, "✅ Empleado actualizado exitosamente en el servidor")

                // Hacer re-login para obtener datos actualizados
                val currentUser = getCurrentUserOnce()
                if (currentUser != null && currentUser.companyRut.isNotEmpty()) {
                    Log.i(TAG, "🔄 Haciendo re-login para actualizar datos locales...")
                    val loginResult = login(
                        rut = currentUser.rut,
                        rutEmpresa = currentUser.companyRut,
                        deviceId = currentUser.deviceId
                    )
                    
                    if (loginResult.first) {
                        Log.i(TAG, "✅ Re-login exitoso, datos actualizados")
                    } else {
                        Log.w(TAG, "⚠️ Re-login falló, pero actualización fue exitosa: ${loginResult.second}")
                    }
                }

                Pair(true, "")
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                Log.e(TAG, "❌ Error al actualizar empleado: ${response.code()}")
                Log.e(TAG, "❌ Error Body: $errorMsg")
                Pair(false, "Error al actualizar: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al actualizar empleado", e)
            Pair(false, "Error de conexión: ${e.message}")
        }
    }

    /**
     * Obtiene y actualiza las zonas del servidor
     * @return Pair<Boolean, String> (éxito, mensaje de error si aplica)
     */
    suspend fun refreshZones(): Pair<Boolean, String> {
        return try {
            val user = getCurrentUserOnce()
            if (user == null) {
                Log.w(TAG, "⚠️ No hay usuario autenticado para refrescar zonas")
                return Pair(false, "No hay usuario autenticado")
            }

            Log.i(TAG, "🔄 Refrescando zonas...")

            val zonesResponse =
                    zonesApiService.getZones(tenantId = user.tenantId, companyId = user.companyId)

            Log.i(TAG, "➡️ HTTP Status zonas: ${zonesResponse.code()}")

            if (zonesResponse.isSuccessful && zonesResponse.body() != null) {
                val zonesResponseBody = zonesResponse.body()!!
                Log.i(TAG, "✅ Se obtuvieron ${zonesResponseBody.size} zonas del servidor")

                // Convertir ZoneResponse a entidades Zone
                val zones =
                        zonesResponseBody.map { zoneResponse ->
                            com.akiestoy.beacons.model.Zone(
                                    id = zoneResponse.id,
                                    tenantId = zoneResponse.tenantId,
                                    companyId = zoneResponse.companyId,
                                    name = zoneResponse.name,
                                    type = zoneResponse.type,
                                    rssiThresholdNear = zoneResponse.rssiThresholdNear,
                                    rssiThresholdFar = zoneResponse.rssiThresholdFar,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                            )
                        }

                // Limpiar zonas anteriores y guardar las nuevas
                zoneDao.deleteAll()
                zoneDao.insertZones(zones)
                Log.i(TAG, "✅ Se guardaron ${zones.size} zonas en la base de datos")

                // Extraer y guardar todos los beacons de las zonas
                val allBeacons = zonesResponseBody.flatMap { zoneResponse ->
                    zoneResponse.beacons.map { beaconResponse ->
                        com.akiestoy.beacons.model.RegisteredBeacon(
                            id = beaconResponse.id,
                            tenantId = beaconResponse.tenantId,
                            companyId = beaconResponse.companyId,
                            advUuid = beaconResponse.advUuid.lowercase(),
                            mac = beaconResponse.mac,
                            beaconName = beaconResponse.beaconName,
                            major = beaconResponse.major,
                            minor = beaconResponse.minor,
                            txPower = beaconResponse.txPower,
                            model = beaconResponse.model,
                            beaconType = beaconResponse.beaconType,
                            status = beaconResponse.status,
                            zoneName = beaconResponse.zoneName,
                            zoneId = beaconResponse.zoneId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }

                // Limpiar beacons anteriores y guardar los nuevos
                registeredBeaconDao.deleteAll()
                registeredBeaconDao.insertBeacons(allBeacons)
                Log.i(TAG, "✅ Se actualizaron ${allBeacons.size} beacons en la base de datos")
                allBeacons.forEach { beacon ->
                    Log.i(TAG, "   📍 Beacon: ${beacon.beaconName ?: beacon.zoneName} (MAC: ${beacon.mac ?: "N/A"})")
                }

                // Los beacons se marcarán como favoritos automáticamente cuando se detecten
                Log.i(TAG, "ℹ️ Los beacons se marcarán como favoritos automáticamente al ser detectados")

                Pair(true, "")
            } else {
                val errorMsg = zonesResponse.errorBody()?.string() ?: "Error desconocido"
                Log.e(TAG, "❌ Error al obtener zonas: ${zonesResponse.code()}")
                Log.e(TAG, "❌ Error Body: $errorMsg")
                Pair(false, "Error al obtener zonas: ${zonesResponse.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al refrescar zonas", e)
            Pair(false, "Error de conexión: ${e.message}")
        }
    }
}
