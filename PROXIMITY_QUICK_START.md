# 🚀 Quick Start - Módulo de Proximidad

## ¿Qué se implementó?

Un sistema completo de detección de proximidad BLE que:
- ✅ Escanea beacons en modo **passive** (sin conexión) con `LOW_LATENCY`
- ✅ Detecta eventos **ENTER**, **EXIT** y **HEARTBEAT** con histeresis
- ✅ Aplica suavizado de RSSI (media móvil de 5 muestras)
- ✅ Envía eventos al backend **solo cuando cambia el estado**
- ✅ Funciona en segundo plano como servicio foreground

## 📱 Uso Inmediato

### 1. Configurar URL del Backend

**Opción A - Desde código:**
```kotlin
// En: app/src/main/java/com/akiestoy/beacons/api/ApiClient.kt
private const val BASE_URL = "http://TU_SERVIDOR.com/"
```

**Opción B - Desde la app:**
1. Abre la app
2. Ve a la pestaña "Proximidad" (icono 📍)
3. Presiona "Configurar URL"
4. Ingresa tu URL
5. Presiona "Guardar"

### 2. Iniciar el Servicio

Desde la app:
1. Ve a la pestaña "Proximidad"
2. Presiona "Iniciar Servicio"
3. ✅ El servicio comenzará a escanear y enviar eventos

El servicio continuará funcionando **incluso si cierras la app**.

### 3. Verificar que Funciona

Ejecuta en terminal:
```bash
adb logcat | grep -E "ProximityBeaconScanner|BeaconProximityManager|ProximityEventSender"
```

Deberías ver logs como:
```
ProximityBeaconScanner: Beacon detected: MAC=94:54:c5:2e:94:ec, RSSI=-58
BeaconProximityManager: ENTER event for beacon 94:54:c5:2e:94:ec
ProximityEventSender: Event sent successfully
```

## 📡 Formato de Datos al Backend

Cada evento se envía como POST a `http://TU_SERVIDOR/api/proximity/events`:

```json
{
  "zona": "94:54:c5:2e:94:ec",           // MAC del beacon
  "nombreDispositivo": "Samsung S21",     // Nombre del celular
  "distancia": 2.45,                      // Metros
  "status": "enter",                      // enter | exit | heartbeat
  "timestamp": "2025-10-30T16:45:10Z",    // ISO 8601 UTC
  "rssiAvg": -62.5,                       // RSSI promedio
  "samples": 5                            // Número de muestras
}
```

## 🎯 Umbrales Predeterminados

- **ENTER**: RSSI > -65 dBm (≈ 1-2 metros)
- **EXIT**: RSSI < -70 dBm (≈ 3-4 metros)
- **Timeout EXIT**: 10 segundos sin detectar
- **Heartbeat**: Cada 5 segundos
- **Suavizado**: 5 muestras (requiere mínimo 3 para ENTER)

## ⚙️ Personalizar Umbrales

En `app/src/main/java/com/akiestoy/beacons/model/proximity/ProximityConfig.kt`:

```kotlin
const val DEFAULT_ENTER_THRESHOLD = -65      // Más negativo = más lejos
const val DEFAULT_EXIT_THRESHOLD = -70       // Debe ser < ENTER
const val DEFAULT_EXIT_TIMEOUT_MS = 10_000L  // Milisegundos
const val DEFAULT_HEARTBEAT_INTERVAL_MS = 5_000L
const val DEFAULT_MOVING_AVERAGE_WINDOW = 5  // Muestras
```

### Guía de Umbrales:
- **-50 dBm**: Muy cerca (< 0.5m)
- **-60 dBm**: Cerca (~1m) ← ENTER típico
- **-70 dBm**: Media distancia (~2-3m) ← EXIT típico
- **-80 dBm**: Lejos (~5-10m)
- **-90+ dBm**: Muy lejos

## 🔧 Backend - Endpoint Requerido

Tu backend debe tener este endpoint:

```http
POST /api/proximity/events
Content-Type: application/json

{
  "zona": "MAC_ADDRESS",
  "nombreDispositivo": "DEVICE_NAME",
  "distancia": 2.5,
  "status": "enter",
  "timestamp": "2025-10-30T16:45:10Z",
  "rssiAvg": -62.0,
  "samples": 5
}
```

Debe responder:
```json
{
  "success": true,
  "message": "Event received"
}
```

## 🐛 Troubleshooting Rápido

### No detecta beacons
```bash
# Verificar Bluetooth
adb shell dumpsys bluetooth_manager | grep "enabled"

# Verificar permisos
adb shell pm list permissions -g | grep location
```

### No envía al backend
1. ✅ Verifica conectividad: `adb shell ping -c 3 tu-servidor.com`
2. ✅ Revisa URL en `ApiClient.kt`
3. ✅ Verifica logs: `adb logcat | grep ProximityEventSender`

### Eventos duplicados
- El sistema **NO envía duplicados** por diseño
- Si ves duplicados, puede ser que el backend los esté procesando múltiples veces

## 📊 Arquitectura Simplificada

```
ProximityBeaconScanner (BLE LOW_LATENCY)
           ↓
     [RSSI samples]
           ↓
BeaconProximityManager (Histeresis + Suavizado)
           ↓
    [Solo cambios de estado]
           ↓
ProximityEventSender (HTTP POST)
           ↓
      Backend API
```

## 🔋 Consumo de Batería

`LOW_LATENCY` es **agresivo** en batería. Para reducir consumo:

1. Cambiar modo de escaneo en `ProximityBeaconScanner.kt`:
```kotlin
.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
```

2. Aumentar intervalo heartbeat:
```kotlin
const val DEFAULT_HEARTBEAT_INTERVAL_MS = 15_000L  // 15s en vez de 5s
```

## 📁 Archivos Principales

```
app/src/main/java/com/akiestoy/beacons/
├── api/
│   ├── ApiClient.kt              ← Configurar URL aquí
│   ├── BeaconProximityApi.kt
│   └── ProximityEventSender.kt
├── proximity/
│   ├── BeaconProximityManager.kt ← Lógica de eventos
│   └── ProximityBeaconScanner.kt ← Escaneo BLE
├── model/proximity/
│   ├── ProximityConfig.kt        ← Configurar umbrales aquí
│   └── ...
├── service/
│   └── ProximityForegroundService.kt ← Servicio Android
└── ui/screens/
    └── ProximityScreen.kt        ← UI de control
```

## 🎓 Ejemplos de Uso

### Iniciar programáticamente
```kotlin
ProximityForegroundService.startService(context)
```

### Detener programáticamente
```kotlin
ProximityForegroundService.stopService(context)
```

### Configurar beacon específico
```kotlin
val config = ProximityConfig(
    beaconId = "94:54:c5:2e:94:ec",
    enterThreshold = -60,  // Más sensible
    exitThreshold = -75    // Sale más lejos
)
proximityManager.configureBeacon("94:54:c5:2e:94:ec", config)
```

## 📚 Documentación Completa

Ver `PROXIMITY_MODULE.md` para documentación detallada.

---

**¿Necesitas ayuda?** Revisa los logs con:
```bash
adb logcat | grep -i proximity
```
