# AkiEstoy - Detector de Beacons ESP32

Aplicación Android en Kotlin para detectar y rastrear beacons iBeacon basados en ESP32.

## Características

- Detección en tiempo real de beacons iBeacon
- Cálculo automático de distancia basado en RSSI
- Interfaz moderna con Jetpack Compose y Material 3
- Soporte para escaneo en segundo plano con servicio foreground
- Mapeo de ubicaciones físicas (Baño/Sala)
- Indicadores visuales de proximidad y calidad de señal

## Beacons Configurados

La aplicación está configurada para detectar los siguientes beacons ESP32:

### UUID Común
```
e2c56db5-dffb-48d2-b060-d0f5a71096e0
```

### BeaconA - Baño
- **Major:** 100
- **Minor:** 1
- **MAC:** 38:18:2b:b3:80:34
- **TX Power:** -59 dBm

### BeaconB - Sala
- **Major:** 101
- **Minor:** 1
- **MAC:** 94:54:c5:2e:94:ec
- **TX Power:** -59 dBm

## Arquitectura

### Tecnologías Utilizadas

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose con Material 3
- **Arquitectura:** MVVM (Model-View-ViewModel)
- **Librería de Beacons:** AltBeacon Android Beacon Library
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)

### Estructura del Proyecto

```
app/src/main/java/com/akiestoy/beacons/
├── model/
│   ├── BeaconDetection.kt       # Modelo de datos de detección
│   ├── BeaconLocation.kt        # Enum de ubicaciones (Baño/Sala)
│   └── ProximityZone.kt         # Enum de zonas de proximidad
├── scanner/
│   └── BeaconScanner.kt         # Lógica de escaneo de beacons
├── service/
│   └── BeaconForegroundService.kt # Servicio para escaneo en segundo plano
├── ui/
│   ├── BeaconScreen.kt          # Pantalla principal con Compose
│   ├── BeaconViewModel.kt       # ViewModel para manejo de estado
│   └── theme/                   # Tema Material 3
└── MainActivity.kt              # Activity principal con permisos
```

## Funcionalidades Principales

### 1. Detección de Beacons

La aplicación detecta beacons iBeacon y proporciona:
- **UUID:** Identificador único del grupo de beacons
- **Major/Minor:** Identificadores de ubicación específica
- **RSSI:** Intensidad de señal recibida
- **Distancia:** Calculada automáticamente en metros/centímetros
- **Proximidad:** Clasificada en zonas (Muy cerca, Cerca, Lejos, Fuera de rango)

### 2. Zonas de Proximidad

| Zona | Distancia | Emoji | Descripción |
|------|-----------|-------|-------------|
| **Immediate** | < 0.5m | 🔴 | Muy cerca |
| **Near** | 0.5m - 3m | 🟡 | Cerca |
| **Far** | 3m - 10m | 🟢 | Lejos |
| **Unknown** | > 10m | ⚪ | Fuera de rango |

### 3. Cálculo de Distancia

La distancia se calcula usando la fórmula:
```kotlin
distance = 10 ^ ((measuredPower - RSSI) / (10 * pathLossExponent))
```

Donde:
- `measuredPower` = -59 dBm (TX Power del beacon)
- `RSSI` = Intensidad de señal recibida
- `pathLossExponent` = 2 (factor de pérdida de señal)

### 4. Calidad de Señal

Se calcula un porcentaje de calidad basado en el RSSI:
```kotlin
signalQuality = ((100 + rssi) * 100 / 60).coerceIn(0, 100)
```

## Permisos Requeridos

### Android 12+ (API 31+)
- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`

### Android 10-11 (API 29-30)
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `FOREGROUND_SERVICE`

## Instalación y Configuración

### Requisitos Previos

1. **Android Studio** (latest version)
2. **JDK 17**
3. **Dispositivo Android** con Bluetooth LE (API 26+)

### Pasos de Instalación

1. Clona el repositorio:
```bash
git clone <repository-url>
cd akiestoy-mobile
```

2. Abre el proyecto en Android Studio

3. Sincroniza las dependencias de Gradle

4. Conecta tu dispositivo Android o inicia un emulador

5. Compila y ejecuta:
```bash
./gradlew assembleDebug
./gradlew installDebug
```

### Configuración de Beacons

Si deseas usar diferentes beacons, modifica los valores en:

**`BeaconDetection.kt`:**
```kotlin
companion object {
    const val AKIESTOY_UUID = "tu-uuid-aqui"
}
```

**`BeaconLocation.kt`:**
```kotlin
enum class BeaconLocation(val major: Int, val displayName: String) {
    TU_UBICACION_1(100, "Nombre Ubicación 1"),
    TU_UBICACION_2(101, "Nombre Ubicación 2"),
    // ...
}
```

## Uso de la Aplicación

### Pantalla Principal

1. **Al abrir la app:** Se solicitan los permisos necesarios
2. **Botón "Iniciar Escaneo":** Comienza la detección de beacons
3. **Lista de Detecciones:** Muestra todos los beacons en rango con:
   - Nombre de ubicación (Baño/Sala)
   - Distancia estimada
   - Indicador de proximidad
   - Calidad de señal (%)
   - Datos técnicos (Major, Minor, RSSI, TX Power, MAC)

### Interpretación de Resultados

**Indicador de Proximidad:**
- 🔴 **Rojo (Immediate):** Estás muy cerca del beacon (< 50cm)
- 🟡 **Amarillo (Near):** Estás cerca del beacon (0.5m - 3m)
- 🟢 **Verde (Far):** Estás lejos del beacon (3m - 10m)
- ⚪ **Blanco (Unknown):** Fuera de rango o señal débil

**Calidad de Señal:**
- **90-100%:** Excelente (muy cerca)
- **70-89%:** Buena
- **50-69%:** Regular
- **< 50%:** Débil (lejos o con obstáculos)

## Desarrollo

### Dependencias Principales

```kotlin
// AltBeacon para detección de iBeacons
implementation("org.altbeacon:android-beacon-library:2.20.6")

// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.11.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
```

### Agregar Nuevas Ubicaciones

1. Actualiza `BeaconLocation.kt`:
```kotlin
enum class BeaconLocation(val major: Int, val displayName: String) {
    BANO(100, "Baño"),
    SALA(101, "Sala"),
    COCINA(102, "Cocina"),  // Nueva ubicación
    UNKNOWN(-1, "Desconocido")
}
```

2. Configura tu beacon ESP32 con el Major correspondiente (102)

### Personalizar Intervalos de Escaneo

Edita `BeaconScanner.kt`:
```kotlin
// Escaneo en primer plano (ms)
beaconManager.foregroundScanPeriod = 1100L
beaconManager.foregroundBetweenScanPeriod = 0L

// Escaneo en segundo plano (ms)
beaconManager.backgroundScanPeriod = 1100L
beaconManager.backgroundBetweenScanPeriod = 1100L
```

## Debugging

### Ver Logs de Beacons

Usa Logcat con el filtro:
```
tag:BeaconScanner
```

Verás mensajes como:
```
BeaconScanner: Detected 2 beacons
BeaconScanner: Beacon service connected
BeaconScanner: Started ranging beacons in region: akiestoy-beacons
```

### Verificar Detección con nRF Connect

1. Instala [nRF Connect for Mobile](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp)
2. Escanea dispositivos BLE
3. Busca tus beacons ESP32
4. Verifica que transmitan el UUID correcto en "Manufacturer Data"

## Solución de Problemas

### No Detecta Beacons

1. **Verifica permisos:** Asegúrate de otorgar todos los permisos
2. **Bluetooth activado:** Verifica que Bluetooth esté encendido
3. **Ubicación activada:** Android requiere ubicación para BLE
4. **Beacons encendidos:** Verifica que los ESP32 estén alimentados
5. **UUID correcto:** Confirma que el UUID coincida

### Permisos Denegados

- Android 12+: Ve a Configuración > Apps > AkiEstoy > Permisos
- Otorga permisos de Ubicación y Bluetooth

### Distancia Inexacta

- La distancia es aproximada y depende de:
  - Obstáculos físicos (paredes, muebles)
  - Interferencias (WiFi, otros dispositivos)
  - Orientación del dispositivo
  - Calibración del TX Power

## Roadmap

- [ ] Persistencia local de detecciones (Room Database)
- [ ] Integración con backend REST API
- [ ] Notificaciones push basadas en proximidad
- [ ] Modo de bajo consumo optimizado
- [ ] Dashboard de analytics
- [ ] Soporte para múltiples grupos de beacons
- [ ] Exportación de datos a CSV

## Contribuir

1. Fork el repositorio
2. Crea una rama para tu feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit tus cambios (`git commit -am 'Agrega nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

## Licencia

Este proyecto está bajo la licencia MIT.

## Recursos Adicionales

- [Documentación iBeacon de Apple](https://developer.apple.com/ibeacon/)
- [AltBeacon Android Library](https://altbeacon.github.io/android-beacon-library/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)

## Soporte

Para problemas o preguntas, abre un issue en el repositorio.

---

**Desarrollado con ❤️ usando Kotlin y Jetpack Compose**
