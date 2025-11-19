# Configuración de Timers

Todos los intervalos de tiempo de la aplicación están configurados mediante variables de entorno en el archivo `secrets.properties`.

## Variables Disponibles

### BEACON_READING_INTERVAL_SECONDS
**Descripción**: Intervalo para enviar lecturas de beacons al servidor  
**Por defecto**: 15 segundos  
**Ubicación**: `BeaconReadingService.kt`  
**API**: `/v1/mobile/beacon-reading`

### SCAN_INTERVAL_SECONDS  
**Descripción**: Intervalo de escaneo de beacons  
**Por defecto**: 2 segundos  
**Ubicación**: Múltiples servicios de escaneo

### EVENT_BATCH_INTERVAL_SECONDS
**Descripción**: Intervalo para enviar eventos en batch  
**Por defecto**: 15 segundos  
**Ubicación**: `EventBatcher.kt`

### SIGNAL_CHECK_INTERVAL_SECONDS
**Descripción**: Intervalo de verificación de señal de beacons  
**Por defecto**: 2 segundos  
**Ubicación**: `BeaconTrackingService.kt`

### HEARTBEAT_INTERVAL_SECONDS
**Descripción**: Intervalo de heartbeat (keepalive)  
**Por defecto**: 60 segundos  
**Ubicación**: `BeaconTrackingService.kt`

### EXIT_DELAY_SECONDS
**Descripción**: Delay antes de marcar salida definitiva cuando se pierde señal  
**Por defecto**: 120 segundos (2 minutos)  
**Ubicación**: `BeaconTrackingService.kt`

### SIGNAL_LOST_THRESHOLD_SECONDS
**Descripción**: Timeout para considerar que la señal se perdió  
**Por defecto**: 5 segundos  
**Ubicación**: `BeaconTrackingService.kt`, `AppConfig.kt`

### FILTER_UPDATE_INTERVAL_SECONDS
**Descripción**: Intervalo de actualización de filtros en la UI  
**Por defecto**: 1 segundo  
**Ubicación**: `BeaconViewModel.kt`

## Cómo Modificar

1. Edita el archivo `secrets.properties` en la raíz del proyecto
2. Cambia el valor del timer que desees (en segundos)
3. Recompila la aplicación con `./gradlew assembleDebug`
4. La nueva configuración se aplicará automáticamente

## Ejemplo

```properties
# Cambiar el intervalo de lecturas de beacons a 20 segundos
BEACON_READING_INTERVAL_SECONDS=20

# Cambiar el heartbeat a 30 segundos
HEARTBEAT_INTERVAL_SECONDS=30
```

## Arquitectura

Los timers se configuran en tres niveles:

1. **secrets.properties**: Variables de entorno (valores en segundos)
2. **app/build.gradle.kts**: Lee y genera `BuildConfig` fields
3. **AppConfig.kt**: Convierte segundos a milisegundos y proporciona valores por defecto

Cada servicio importa `AppConfig` y usa las constantes configurables.
