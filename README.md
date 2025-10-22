# Akistoy

Akistoy es una aplicación Android (Kotlin + Jetpack Compose) que detecta beacons BLE y envía marcajes a una API remota. El proyecto sigue MVVM + Clean Architecture con Hilt, WorkManager y coroutines/Flow.

## Requisitos

- Android Studio Ladybug (o superior) con soporte para Android 15 (API 35).
- JDK 17.

## Configuración inicial

1. Clona el repositorio.
2. Copia `.env.example` en `.env` (opcional) y ajusta:
   ```
   API_BASE_URL=https://api.example.com/
   FAKE_LOGIN=true
   ```
3. Importa el proyecto en Android Studio. Gradle usa Kotlin DSL y Wrapper (Gradle 8.7).

## Variantes de build

- `debug`: apunta a `https://staging.example.com/` y deshabilita minify.
- `release`: minify activado con reglas Proguard básicas.

### API Base URL

Puedes sobrescribir `BuildConfig.API_BASE_URL` modificando los campos `buildConfigField` en `app/build.gradle.kts`.

## Permisos

| Versión | Permisos |
|---------|----------|
| Android ≤ 11 | `ACCESS_FINE_LOCATION` |
| Android 12+ | `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT` |
| Android 13+ | `POST_NOTIFICATIONS` |
| Foreground Service | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_DATA_SYNC` |

Al abrir la app se muestra una pantalla de onboarding solicitando los permisos necesarios.

## Flujo principal

1. **Login:** pantalla simple con captura de email. `FAKE_LOGIN=true` genera credenciales locales.
2. **Home:** indicador visual del estado de detección, contador de eventos, lista en vivo de detecciones y botón para activar/desactivar el servicio en primer plano.
3. **Servicio en primer plano:** mantiene el escaneo BLE activo y muestra una notificación persistente (“Akistoy activo: detectando beacons”). Se reanuda automáticamente tras reinicios mediante `BOOT_COMPLETED` y WorkManager.
4. **Settings:** permite editar la lista de UUIDs/IDs de beacons, consultar estado de permisos/Bluetooth y forzar sincronización remota de configuración.
5. **WorkManager:** tarea periódica (15 min, `NetworkType.CONNECTED`) que envía el buffer de detecciones almacenadas cuando hay conectividad.

## BLE

- `RealBeaconScanner` usa `BluetoothLeScanner` con filtros por UUID configurables desde DataStore.
- `FakeBeaconScanner` emite detecciones simuladas para pruebas unitarias.
- Deducción simple por ventana temporal para evitar inundar la API.

## Red

- Retrofit + OkHttp con interceptor de autorización (token DataStore).
- Kotlinx Serialization para (de)serialización JSON.
- Timeouts y logging configurados en `OkHttpClient`.

## DataStore

- Guarda usuario, token, deviceId, lista de UUIDs y estado del servicio en primer plano.

## WorkManager

- `MarkSyncWorker` se registra vía Hilt. Se puede reprogramar manualmente llamando a `MarkSyncWorker.schedule(context)`.

## Servicio en primer plano

- `BeaconForegroundService` gestiona `StartScanningUseCase`/`StopScanningUseCase` y muestra la notificación.
- `ServiceController` expone métodos para iniciar/detener el servicio y guardar la preferencia del usuario.

## Tests

- Unit tests (`app/src/test`) para `LoginViewModel` y `StartScanningUseCase` usando Turbine/Coroutines Test.
- Instrumented test (`app/src/androidTest`) verifica que la navegación inicia en la pantalla de login.

Ejecuta pruebas:

```bash
./gradlew test        # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests (requiere dispositivo/emulador)
```

## Optimización de batería

Para asegurar funcionamiento en segundo plano, guía a los usuarios a excluir Akistoy de las optimizaciones de batería del sistema (Configuración → Batería → Optimización → Akistoy → No optimizar). No se usan APIs privadas.

## Pruebas sugeridas

| Dispositivo | Android | Resultado esperado |
|-------------|---------|--------------------|
| Teléfono 1  | 10      | Escaneo BLE activo y notificación presente |
| Teléfono 2  | 12      | Solicitud de permisos Bluetooth y detección < 3s |
| Teléfono 3  | 13      | Solicitud `POST_NOTIFICATIONS`, alerta cuando BT está OFF |
| Teléfono 4  | 15      | Servicio en primer plano con reanudación tras reinicio |

## Notas

- Iconografía genérica incluida (`ic_launcher`).
- Splash screen mediante `androidx.core:splashscreen`.
- TODOs específicos pueden añadirse según necesidades futuras (persistencia local, métricas avanzadas, etc.).
