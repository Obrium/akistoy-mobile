# Módulo de Proximidad BLE

## Descripción General

Este módulo implementa un sistema completo de detección de proximidad para beacons BLE con eventos **enter**, **exit** y **heartbeat**. El sistema utiliza escaneo pasivo (sin conexión) en modo `LOW_LATENCY` y envía eventos al backend solo cuando cambia el estado de proximidad.

## Características Implementadas

### ✅ Escaneo BLE Pasivo
- Modo `SCAN_MODE_LOW_LATENCY` para máxima frecuencia de escaneo
- Sin conexión al beacon (passive scanning)
- Parsing nativo de paquetes iBeacon advertising

### ✅ Detección de Eventos con Histeresis
- **ENTER**: Cuando el RSSI promedio supera el umbral de entrada (-65 dBm por defecto)
- **EXIT**: Cuando el RSSI promedio baja del umbral de salida (-70 dBm) o no se detecta por 10s
- **HEARTBEAT**: Cada 5s si el beacon sigue en proximidad

### ✅ Suavizado de RSSI
- Media móvil de 5 muestras (configurable)
- Requiere mínimo 3 lecturas para generar evento ENTER
- Evita falsos positivos por variaciones instantáneas

### ✅ Envío al Backend (REST API)
- Solo se envían cambios de estado (no cada paquete)
- Formato JSON adaptado a los requerimientos:
  ```json
  {
    "zona": "MAC_ADDRESS",
    "nombreDispositivo": "Samsung Galaxy S21 (android_id)",
    "distancia": 2.5,
    "status": "enter|exit|heartbeat",
    "timestamp": "2025-10-30T16:45:10Z",
    "rssiAvg": -62.0,
    "samples": 5
  }
  ```

### ✅ Configuración por Beacon
- Umbrales calibrables individualmente
- Configuración de timeouts e intervalos
- Ventana de promedio móvil ajustable

## Arquitectura del Módulo

```
┌─────────────────────────────────────────────────┐
│         ProximityForegroundService              │
│  (Servicio en primer plano - Android Service)   │
└───────────────┬─────────────────────────────────┘
                │
        ┌───────┴───────┐
        │               │
        ▼               ▼
┌──────────────┐  ┌──────────────────┐
│   Proximity  │  │  Proximity       │
│   Scanner    │─▶│  Manager         │
│              │  │                  │
│ LOW_LATENCY  │  │ • Histeresis     │
│ Passive BLE  │  │ • Suavizado      │
│              │  │ • Estados        │
└──────────────┘  └──────┬───────────┘
                         │
                         │ proximityEvents Flow
                         ▼
                  ┌──────────────┐
                  │   Event      │
                  │   Sender     │──▶ Backend REST API
                  │              │
                  │ Retrofit     │
                  └──────────────┘
```

## Archivos Creados

### 📂 Modelos de Datos
- `model/proximity/ProximityEventType.kt` - Enum de tipos de eventos
- `model/proximity/ProximityMetrics.kt` - Métricas de RSSI
- `model/proximity/ProximityEvent.kt` - Evento interno
- `model/proximity/BeaconProximityRequest.kt` - Request al backend
- `model/proximity/ProximityConfig.kt` - Configuración de umbrales
- `model/proximity/BeaconState.kt` - Estado interno por beacon

### 🔧 Lógica de Proximidad
- `proximity/BeaconProximityManager.kt` - Gestión de estados y eventos
- `proximity/ProximityBeaconScanner.kt` - Scanner BLE LOW_LATENCY

### 🌐 API REST
- `api/BeaconProximityApi.kt` - Interface Retrofit
- `api/ApiClient.kt` - Cliente HTTP configurado
- `api/ProximityEventSender.kt` - Servicio de envío de eventos

### 🔄 Servicio Android
- `service/ProximityForegroundService.kt` - Servicio en primer plano

### 🖥️ Interfaz de Usuario
- `ui/screens/ProximityScreen.kt` - Pantalla de control
- `ui/navigation/NavDestinations.kt` - Ruta de navegación actualizada

## Configuración Inicial

### 1. Configurar URL del Backend

En `api/ApiClient.kt`, actualiza la URL base:

```kotlin
private const val BASE_URL = "http://TU_SERVIDOR.com/"
```

O configúrala dinámicamente desde la UI en la pantalla "Proximidad".

### 2. Ajustar Umbrales (Opcional)

En `model/proximity/ProximityConfig.kt`:

```kotlin
const val DEFAULT_ENTER_THRESHOLD = -65  // dBm
const val DEFAULT_EXIT_THRESHOLD = -70   // dBm
const val DEFAULT_EXIT_TIMEOUT_MS = 10_000L  // 10 segundos
const val DEFAULT_HEARTBEAT_INTERVAL_MS = 5_000L  // 5 segundos
const val DEFAULT_MOVING_AVERAGE_WINDOW = 5  // muestras
```

### 3. Permisos

Los permisos ya están configurados en `AndroidManifest.xml`:
- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`
- `FOREGROUND_SERVICE`
- `INTERNET`

## Uso de la Aplicación

### Desde la UI

1. Abre la app y navega a la pestaña **"Proximidad"** (icono de ubicación)
2. Presiona **"Iniciar Servicio"** para comenzar el escaneo
3. El servicio continuará funcionando en segundo plano
4. Los eventos se enviarán automáticamente al backend
5. Presiona **"Detener Servicio"** para finalizar

### Programáticamente

```kotlin
// Iniciar servicio
ProximityForegroundService.startService(context)

// Detener servicio
ProximityForegroundService.stopService(context)
```

## Formato de Datos Enviados al Backend

Cada evento se envía como un POST a `http://TU_SERVIDOR/api/proximity/events`:

```json
{
  "zona": "94:54:c5:2e:94:ec",
  "nombreDispositivo": "Samsung Galaxy S21 (a1b2c3d4)",
  "distancia": 2.45,
  "status": "enter",
  "timestamp": "2025-10-30T16:45:10.123Z",
  "rssiAvg": -62.5,
  "samples": 5
}
```

### Campos:
- **zona**: MAC address del beacon
- **nombreDispositivo**: Marca + Modelo + Android ID del celular
- **distancia**: Distancia aproximada en metros (calculada con path loss)
- **status**: `"enter"`, `"exit"` o `"heartbeat"`
- **timestamp**: ISO 8601 UTC
- **rssiAvg**: RSSI promedio de las últimas N muestras
- **samples**: Número de muestras en el promedio

## Backend - Gestión de Estado

El backend debe:

1. **Mantener estado por (deviceId, beaconId)**
   - Guardar último timestamp de evento recibido
   - Guardar último status

2. **Expirar estados sin heartbeat**
   - Si no recibe heartbeat en 2× el intervalo (10 segundos)
   - Marcar como "perdido" o generar EXIT automático

3. **Validar transiciones**
   - Solo aceptar ENTER si no estaba en proximidad
   - Solo aceptar HEARTBEAT si estaba en proximidad
   - EXIT siempre es válido

## Pruebas y Validación

### Verificar Escaneo BLE

Usa `adb logcat` para ver los logs:

```bash
adb logcat | grep -E "ProximityBeaconScanner|BeaconProximityManager|ProximityEventSender"
```

### Logs Esperados

```
ProximityBeaconScanner: Beacon detected: MAC=94:54:c5:2e:94:ec, RSSI=-58
BeaconProximityManager: Beacon 94:54:c5:2e:94:ec - RSSI: -58, Avg: -61.2
BeaconProximityManager: ENTER event for beacon 94:54:c5:2e:94:ec
ProximityEventSender: Sending event to backend: BeaconProximityRequest(...)
ProximityEventSender: Event sent successfully
```

### Probar Eventos

1. **ENTER**: Acerca el celular al beacon (< 1 metro)
2. **HEARTBEAT**: Mantén el celular cerca por 5+ segundos
3. **EXIT**: Aleja el celular o cubre el beacon

## Optimizaciones y Consideración de Batería

### LOW_LATENCY vs Batería

El modo `SCAN_MODE_LOW_LATENCY` consume **más batería** pero proporciona:
- Detecciones más rápidas (< 1 segundo)
- Mayor precisión en RSSI
- Mejor experiencia de usuario

Para **reducir consumo**:
1. Cambiar a `SCAN_MODE_BALANCED` en `ProximityBeaconScanner.kt`:
   ```kotlin
   .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
   ```
2. Aumentar intervalo de heartbeat a 10-15 segundos
3. Reducir ventana de promedio móvil a 3 muestras

## Troubleshooting

### No se detectan beacons

1. Verificar que Bluetooth esté activado
2. Verificar permisos de ubicación (necesarios para BLE)
3. Verificar que el beacon esté transmitiendo el UUID correcto: `e2c56db5-dffb-48d2-b060-d0f5a71096e0`
4. Revisar logs con `adb logcat`

### No se envían eventos al backend

1. Verificar URL del backend en `ApiClient.kt`
2. Verificar conectividad de red
3. Revisar logs de `ProximityEventSender`
4. Verificar que el backend esté escuchando en la ruta `/api/proximity/events`

### Eventos duplicados

El sistema está diseñado para NO enviar duplicados:
- Solo se envía ENTER cuando hay transición de fuera → dentro
- Solo se envía EXIT cuando hay transición de dentro → fuera
- HEARTBEAT se envía en intervalos fijos mientras esté en proximidad

## Mejoras Futuras

- [ ] Persistencia de configuración en SharedPreferences
- [ ] Cola de reintentos para eventos fallidos
- [ ] Estadísticas en tiempo real en la UI
- [ ] Exportación de logs de eventos
- [ ] Notificaciones push para eventos específicos
- [ ] Calibración automática de umbrales por entorno
- [ ] Soporte para múltiples UUIDs de beacons

## Dependencias Agregadas

```kotlin
// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("com.google.code.gson:gson:2.10.1")
```

## Licencia

Este módulo es parte del proyecto AkiEstoy.

---

**Implementado por**: Claude Code
**Fecha**: Octubre 2025
**Versión**: 1.0.0
