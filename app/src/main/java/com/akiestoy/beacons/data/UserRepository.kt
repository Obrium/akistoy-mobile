package com.akiestoy.beacons.data

import android.util.Log
import com.akiestoy.beacons.model.api.LoginRequest
import com.akiestoy.beacons.model.user.User
import com.akiestoy.beacons.network.AuthApiService
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar datos de usuario y autenticación
 */
class UserRepository(
    private val userDao: UserDao,
    private val zoneDao: ZoneDao,
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
                val user = User(
                    id = loginData.employee.id,
                    rut = loginData.employee.rut,
                    name = loginData.employee.name,
                    email = loginData.employee.email,
                    phone = "", // No viene en el API, se puede actualizar después
                    companyId = loginData.employee.companyId,
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
                    val zonesResponse = zonesApiService.getZones(
                        tenantId = user.tenantId,
                        companyId = user.companyId
                    )

                    Log.i(TAG, "➡️ HTTP Status zonas: ${zonesResponse.code()}")

                    if (zonesResponse.isSuccessful && zonesResponse.body() != null) {
                        val zonesResponse = zonesResponse.body()!!
                        Log.i(TAG, "✅ Se obtuvieron ${zonesResponse.size} zonas")

                        // Convertir ZoneResponse a entidades Zone
                        val zones = zonesResponse.map { zoneResponse ->
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

                        // Agregar los IDs de las zonas como favoritos (vinculados)
                        val zoneIds = zones.map { it.id }
                        favoritesRepository.addFavorites(zoneIds)
                        Log.i(TAG, "✅ Se agregaron ${zoneIds.size} zonas como beacons vinculados")
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

    suspend fun clearUser() {
        userDao.deleteAll()
    }

    /**
     * Verifica si el token de acceso está vigente
     */
    suspend fun isTokenValid(): Boolean {
        val user = getCurrentUserOnce()
        return user != null && user.tokenExpiresAt > System.currentTimeMillis()
    }
}
