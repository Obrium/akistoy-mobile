# Estado Global - Zona Actual

## Cómo funciona

Se implementó un sistema de estado global similar a Redux usando `StateFlow` de Kotlin para mantener información sobre la zona actual basada en el beacon más cercano.

## Arquitectura

### AppState (Estado Global)
- **Ubicación**: `app/src/main/java/com/akiestoy/beacons/state/AppState.kt`
- **Tipo**: Singleton object
- **Función**: Almacena y distribuye el estado de la zona actual a toda la app

### ZoneInfo (Información de Zona)
Contiene:
- `beaconName`: Nombre del beacon más cercano
- `beaconMac`: Dirección MAC del beacon
- `rssi`: Señal RSSI (más alto = más cerca)
- `timestamp`: Momento de detección

### Lógica de Selección
El beacon "conectado" es el **más cercano** determinado por:
1. Filtrar beacons favoritos vistos en los últimos 10 segundos
2. Seleccionar el que tenga **mejor RSSI** (más cercano a 0, menos negativo)
3. Ejemplo: RSSI -45 es más cercano que RSSI -80

## Uso en cualquier pantalla

```kotlin
import com.akiestoy.beacons.state.AppState
import androidx.compose.runtime.collectAsState

@Composable
fun MiPantalla() {
    // Observar la zona actual desde cualquier pantalla
    val zonaActual by AppState.currentZone.collectAsState()

    zonaActual?.let { zona ->
        Text("Estás en: ${zona.beaconName}")
        Text("RSSI: ${zona.rssi} dBm")
        Text("Proximidad: ${zona.proximityLevel()}")

        // Distancia estimada
        val distancia = zona.estimatedDistance()
        Text("Distancia aprox: ${String.format("%.2f", distancia)}m")
    }
}
```

## Ejemplo de uso en ViewModel

```kotlin
class MiViewModel : ViewModel() {
    private val zonaActual = AppState.currentZone

    fun observarZona() {
        viewModelScope.launch {
            zonaActual.collect { zona ->
                zona?.let {
                    println("Zona cambió: ${it.beaconName}")
                    // Realizar acciones cuando cambia la zona
                    enviarEventoAlServidor(it)
                }
            }
        }
    }
}
```

## Niveles de Proximidad

```kotlin
enum class ProximityLevel {
    IMMEDIATE,  // Muy cerca (RSSI >= -50) - < 0.5m
    NEAR,       // Cerca (RSSI >= -70) - 0.5m a 3m
    FAR,        // Lejos (RSSI >= -90) - 3m+
    UNKNOWN     // Señal muy débil (RSSI < -90)
}
```

## Características Importantes

1. **Actualización automática**: La zona se actualiza cada vez que hay nuevos beacons detectados
2. **Pull-to-refresh**: El usuario puede actualizar manualmente deslizando hacia abajo
3. **Estado reactivo**: Cualquier pantalla puede suscribirse a los cambios
4. **Thread-safe**: Usa StateFlow que es thread-safe por defecto

## Acceso directo al estado

```kotlin
// Obtener valor actual sin observar
val zonaActual = AppState.currentZone.value

// Actualizar manualmente (normalmente no es necesario)
AppState.updateCurrentZone(
    ZoneInfo(
        beaconName = "Sala Principal",
        beaconMac = "00:11:22:33:44:55",
        rssi = -55
    )
)

// Limpiar la zona actual
AppState.clearCurrentZone()
```

## Flujo de Datos

```
Escaneo BLE → BeaconViewModel → favoriteBeacons
                                       ↓
                              HomeScreen (calculateConnectionState)
                                       ↓
                              Selecciona beacon más cercano (mejor RSSI)
                                       ↓
                              AppState.updateCurrentZone()
                                       ↓
                              Cualquier pantalla observa AppState.currentZone
```
