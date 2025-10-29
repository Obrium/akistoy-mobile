# Guía de Inicio Rápido - AkiEstoy

## Instalación Rápida

### 1. Compilar el APK

```bash
./gradlew assembleDebug
```

El APK se generará en:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Instalar en Dispositivo Android

Conecta tu dispositivo Android con USB debugging activado:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

O arrastra el APK directamente a tu dispositivo y ábrelo.

### 3. Otorgar Permisos

Al abrir la app por primera vez, se te solicitarán estos permisos:
- **Ubicación** (Necesario para escaneo BLE)
- **Bluetooth** (Para detectar los beacons)

Acepta todos los permisos para que la app funcione correctamente.

## Uso de la Aplicación

### Pantalla Principal

1. **Botón "Iniciar Escaneo"**: Presiona para comenzar a buscar beacons
2. **Lista de Detecciones**: Verás aparecer los beacons detectados con:
   - 📍 Ubicación (Baño/Sala)
   - 📏 Distancia estimada
   - 🎯 Indicador de proximidad (🔴 muy cerca, 🟡 cerca, 🟢 lejos)
   - 📶 Calidad de señal (%)
   - 🔧 Datos técnicos (Major, Minor, RSSI, MAC)

### Interpretación de Resultados

#### Zonas de Proximidad
- 🔴 **Immediate** (< 50cm): Estás muy cerca del beacon
- 🟡 **Near** (0.5m - 3m): Estás a una distancia cercana
- 🟢 **Far** (3m - 10m): Estás lejos del beacon
- ⚪ **Unknown** (> 10m): Fuera de rango o señal débil

#### Calidad de Señal
- **90-100%**: Excelente (muy cerca)
- **70-89%**: Buena
- **50-69%**: Regular
- **< 50%**: Débil (lejos o con obstáculos)

## Configuración de Beacons ESP32

Los beacons deben estar configurados con:

### UUID
```
e2c56db5-dffb-48d2-b060-d0f5a71096e0
```

### Ubicaciones
- **BeaconA (Baño)**: Major = 100, Minor = 1
- **BeaconB (Sala)**: Major = 101, Minor = 1

### TX Power
```
-59 dBm
```

## Desarrollo

### Estructura del Código

```
app/src/main/java/com/akiestoy/beacons/
├── model/                      # Modelos de datos
│   ├── BeaconDetection.kt
│   ├── BeaconLocation.kt
│   └── ProximityZone.kt
├── scanner/                    # Lógica de escaneo
│   └── BeaconScanner.kt
├── ui/                         # Interfaz de usuario
│   ├── BeaconScreen.kt
│   ├── BeaconViewModel.kt
│   └── theme/
└── MainActivity.kt
```

### Agregar Nuevas Ubicaciones

Edita `app/src/main/java/com/akiestoy/beacons/model/BeaconLocation.kt`:

```kotlin
enum class BeaconLocation(val major: Int, val displayName: String) {
    BANO(100, "Baño"),
    SALA(101, "Sala"),
    COCINA(102, "Cocina"),  // Nueva ubicación
    UNKNOWN(-1, "Desconocido")
}
```

Luego configura tu beacon ESP32 con Major = 102.

### Modificar UUID

Edita `app/src/main/java/com/akiestoy/beacons/model/BeaconDetection.kt`:

```kotlin
companion object {
    const val AKIESTOY_UUID = "tu-nuevo-uuid-aqui"
}
```

Y `app/src/main/java/com/akiestoy/beacons/scanner/BeaconScanner.kt`:

```kotlin
private const val AKIESTOY_UUID = "tu-nuevo-uuid-aqui"
```

## Comandos Útiles

### Compilar
```bash
./gradlew assembleDebug
```

### Instalar en dispositivo
```bash
./gradlew installDebug
```

### Limpiar build
```bash
./gradlew clean
```

### Ver logs
```bash
adb logcat | grep BeaconScanner
```

### Listar dispositivos conectados
```bash
adb devices
```

## Solución de Problemas

### No detecta beacons

1. **Verifica permisos**: Settings > Apps > AkiEstoy > Permissions
2. **Activa Bluetooth**: Settings > Bluetooth
3. **Activa Ubicación**: Settings > Location
4. **Beacons encendidos**: Verifica que los ESP32 estén alimentados
5. **UUID correcto**: Confirma que coincida con los beacons

### App no instala

```bash
# Desinstalar versión anterior
adb uninstall com.akiestoy.beacons

# Reinstalar
./gradlew installDebug
```

### Error de compilación

```bash
# Limpiar y recompilar
./gradlew clean
./gradlew assembleDebug
```

## Depuración

### Ver logs de beacons detectados

```bash
adb logcat -s BeaconScanner
```

### Verificar beacons con nRF Connect

1. Instala [nRF Connect](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp)
2. Escanea dispositivos BLE
3. Busca "iBeacon-Baño" o "iBeacon-Sala"
4. Verifica UUID en "Manufacturer Data"

## Recursos

- **Documentación completa**: Ver `README.md`
- **Código fuente**: `app/src/main/java/com/akiestoy/beacons/`
- **AltBeacon Library**: https://altbeacon.github.io/android-beacon-library/

## Soporte

Para problemas o preguntas:
1. Revisa la documentación en `README.md`
2. Verifica los logs con `adb logcat`
3. Comprueba que los beacons estén transmitiendo con nRF Connect

---

¡Listo! Tu aplicación está funcionando y detectando beacons ESP32. 🎉
