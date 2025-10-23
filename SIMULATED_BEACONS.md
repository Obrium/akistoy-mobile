# 🎯 BEACONS SIMULADOS PARA PRUEBAS

## 📝 RESUMEN

He implementado un sistema de **beacons simulados** que permite probar toda la funcionalidad de detección entrada/salida **sin necesidad de hardware BLE físico**.

---

## 🚀 CÓMO FUNCIONA

### 1. **SimulatedBeaconScanner**

Archivo: `app/src/main/java/com/akistoy/app/data/beacon/SimulatedBeaconScanner.kt`

Simula 3 beacons con comportamiento realista:

| Beacon | UUID | Comportamiento |
|--------|------|----------------|
| **BEACON-ENTRADA-001** | `e2c56db5-dffb-48d2-b060-d0f5a71096e0` | Aparece a los 2s, desaparece a los 15s |
| **BEACON-OFICINA-002** | `a1b2c3d4-e5f6-7890-1234-567890abcdef` | Aparece a los 5s, desaparece a los 20s |
| **BEACON-SALIDA-003** | `11223344-5566-7788-99aa-bbccddeeff00` | Aparece a los 8s, desaparece a los 12s |

### 2. **Timeline de Eventos Simulados**

```
T=0s:   App inicia, escaneo automático activo
        └─> ⏳ Esperando beacons...

T=2s:   BEACON-ENTRADA-001 aparece
        └─> 🟢 ENTRY event (enviado al backend)
        └─> 📱 UI muestra "ENTRADA" en verde

T=3s:   Señal continua de BEACON-ENTRADA-001
        └─> 🔵 DETECTION event (solo UI)

T=5s:   BEACON-OFICINA-002 aparece
        └─> 🟢 ENTRY event (enviado al backend)
        └─> 📱 UI muestra "ENTRADA" en verde
        └─> Ahora hay 2 beacons activos simultáneamente

T=8s:   BEACON-SALIDA-003 aparece
        └─> 🟢 ENTRY event (enviado al backend)
        └─> 3 beacons activos simultáneamente

T=12s:  BEACON-SALIDA-003 deja de emitir señal
        └─> ⏱️ Timeout de 5s inicia

T=15s:  BEACON-ENTRADA-001 deja de emitir señal
        └─> ⏱️ Timeout de 5s inicia

T=17s:  Timeout de BEACON-SALIDA-003 expira
        └─> 🔴 EXIT event (enviado al backend)
        └─> 📱 UI muestra "SALIDA" en rojo

T=20s:  BEACON-OFICINA-002 deja de emitir señal
        └─> ⏱️ Timeout de 5s inicia
        Timeout de BEACON-ENTRADA-001 expira
        └─> 🔴 EXIT event (enviado al backend)

T=25s:  Timeout de BEACON-OFICINA-002 expira
        └─> 🔴 EXIT event (enviado al backend)
        └─> Todos los beacons han salido
```

---

## ⚙️ CÓMO ACTIVAR/DESACTIVAR LA SIMULACIÓN

### Archivo: `app/src/main/java/com/akistoy/app/core/di/AppModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object BeaconModule {
    @Provides
    @Singleton
    fun provideBeaconScanner(
        @ApplicationContext context: Context,
        realScanner: RealBeaconScanner,
        simulatedScanner: SimulatedBeaconScanner
    ): BeaconScanner {
        // Cambiar este valor para alternar entre simulación y hardware real
        val useSimulation = BuildConfig.DEBUG && true  // ← CAMBIAR AQUÍ
        return if (useSimulation) simulatedScanner else realScanner
    }
}
```

### Para usar hardware BLE real:

```kotlin
val useSimulation = BuildConfig.DEBUG && false  // ← false = BLE real
```

### Para usar simulación:

```kotlin
val useSimulation = BuildConfig.DEBUG && true   // ← true = simulado
```

---

## 📊 COMPORTAMIENTO REALISTA

### 1. **RSSI Variable**

Los beacons simulados generan señales RSSI variables para simular movimiento:

```kotlin
BEACON-ENTRADA-001: -50 a -70 dBm (cercano)
BEACON-OFICINA-002: -60 a -80 dBm (medio)
BEACON-SALIDA-003:  -70 a -90 dBm (lejano)
```

### 2. **Proximidad Calculada**

```kotlin
RSSI >= -60  →  Immediate (muy cerca)
-80 < RSSI < -61  →  Near (cerca)
RSSI <= -80  →  Far (lejos)
```

### 3. **Frecuencia de Emisión**

Cada beacon emite señal cada **1-2 segundos** (aleatorio), simulando comportamiento real de beacons BLE.

### 4. **Multi-beacon Simultáneo**

Los 3 beacons se ejecutan en **coroutines independientes**, permitiendo:
- Detección simultánea
- Entradas y salidas independientes
- Tracking individual de cada beacon

---

## 🧪 EVENTOS QUE VERÁS EN LA APP

### En HomeScreen:

1. **Indicador de estado grande**:
   - DESCONECTADO (gris) → Cuando no hay beacons
   - Muestra ID del último beacon detectado

2. **Card "✓ Escaneo activo"**:
   - Siempre visible (escaneo automático)

3. **Lista de detecciones** (scroll):
   ```
   🟢 ENTRADA  BEACON-ENTRADA-001
      RSSI: -65 dBm | Near
      2025-01-23 10:30:02

   🟢 ENTRADA  BEACON-OFICINA-002
      RSSI: -72 dBm | Far
      2025-01-23 10:30:05

   🟢 ENTRADA  BEACON-SALIDA-003
      RSSI: -81 dBm | Far
      2025-01-23 10:30:08

   🔴 SALIDA   BEACON-SALIDA-003
      RSSI: -81 dBm | Far
      2025-01-23 10:30:17

   🔴 SALIDA   BEACON-ENTRADA-001
      RSSI: -65 dBm | Near
      2025-01-23 10:30:20

   🔴 SALIDA   BEACON-OFICINA-002
      RSSI: -72 dBm | Far
      2025-01-23 10:30:25
   ```

4. **Estadísticas**:
   - Total detecciones: 6 (3 ENTRY + 3 EXIT)
   - Última: hace 0m 5s

---

## 📡 EVENTOS ENVIADOS AL BACKEND

Solo se envían eventos **ENTRY** y **EXIT** al backend (los DETECTION son solo para UI).

### Request simulado para BEACON-ENTRADA-001:

#### ENTRY (T=2s):
```json
{
  "device_id": "samsung_galaxy_abc123",
  "user_id": "user_456",
  "beacon_id": "BEACON-ENTRADA-001",
  "rssi": -65,
  "ts_client": "2025-01-23T10:30:02.123Z",
  "event_type": "entry"
}
```

#### EXIT (T=20s):
```json
{
  "device_id": "samsung_galaxy_abc123",
  "user_id": "user_456",
  "beacon_id": "BEACON-ENTRADA-001",
  "rssi": -65,
  "ts_client": "2025-01-23T10:30:20.456Z",
  "event_type": "exit"
}
```

---

## 🔍 DEBUGGING

### Ver logs de beacons simulados:

```bash
adb logcat | grep -E "SimulatedBeacon|BeaconRepository|SendMarkUseCase"
```

### Logs esperados:

```
BeaconRepository: Handling detection for BEACON-ENTRADA-001
BeaconRepository: First detection - creating ENTRY event
SendMarkUseCase: Sending ENTRY event to backend
MarkRepository: Event buffered: BEACON-ENTRADA-001 (entry)
MarkRepository: Sending to API: POST /marks

... (3 segundos después)

BeaconRepository: Beacon still active, emitting DETECTION
SendMarkUseCase: Skipping DETECTION event (not sent to backend)

... (5 segundos sin señal)

BeaconRepository: Timeout expired for BEACON-ENTRADA-001
BeaconRepository: Creating EXIT event
SendMarkUseCase: Sending EXIT event to backend
```

---

## ⚡ VENTAJAS DE LA SIMULACIÓN

✅ **No necesitas beacons físicos** para desarrollar/probar
✅ **Comportamiento predecible** y reproducible
✅ **Pruebas automatizadas** posibles
✅ **Múltiples beacons** sin hardware adicional
✅ **Timing controlado** para verificar lógica de entrada/salida
✅ **Funciona en emulador y dispositivo físico**

---

## 🎮 CÓMO PROBAR

### 1. Instalar app con simulación:

```bash
./gradlew installDebug
```

### 2. Abrir app y hacer login

### 3. Ver la pantalla principal:

- Espera 2 segundos → verás primer beacon entrando
- Espera 5 segundos → segundo beacon entra
- Espera 8 segundos → tercer beacon entra
- Espera 12-25 segundos → verás las 3 salidas

### 4. Verificar backend:

Revisa que tu API reciba las peticiones POST /marks con:
- 3 eventos `event_type: "entry"`
- 3 eventos `event_type: "exit"`

---

## 🔄 CAMBIAR A PRODUCCIÓN

Antes de lanzar a producción con beacons reales:

```kotlin
// En AppModule.kt línea 101:
val useSimulation = BuildConfig.DEBUG && false  // ← Cambiar a false
```

Luego rebuild:
```bash
./gradlew assembleRelease
```

---

## 📝 PERSONALIZAR BEACONS SIMULADOS

En `SimulatedBeaconScanner.kt` líneas 33-53, puedes modificar:

```kotlin
private val simulatedBeacons = listOf(
    SimulatedBeacon(
        id = "TU-BEACON-ID",
        uuid = "tu-uuid-aqui",
        minRssi = -50,              // Señal más fuerte
        maxRssi = -70,              // Señal más débil
        appearanceDelayMs = 3000L,  // Cuándo aparece (ms)
        disappearanceDelayMs = 20000L // Cuándo desaparece (ms)
    )
    // Agregar más beacons...
)
```

---

## ✅ BUILD SUCCESSFUL

```
BUILD SUCCESSFUL in 10s
44 actionable tasks: 11 executed
```

La app con beacons simulados está lista para probar!
