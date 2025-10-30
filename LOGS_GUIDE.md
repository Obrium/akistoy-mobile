# 📋 Guía de Logs - Módulo de Proximidad

## Cómo Ver los Logs

### Ver todos los logs de proximidad:
```bash
adb logcat | grep -E "ProximityBeaconScanner|BeaconProximityManager|ProximityEventSender|ProximityForegroundService"
```

### Ver solo un componente específico:
```bash
# Solo el scanner
adb logcat | grep ProximityBeaconScanner

# Solo el manager de eventos
adb logcat | grep BeaconProximityManager

# Solo el envío al backend
adb logcat | grep ProximityEventSender

# Solo el servicio
adb logcat | grep ProximityForegroundService
```

### Ver con colores (más legible):
```bash
adb logcat -v color | grep -E "Proximity"
```

## 📊 Logs Implementados

### 🚀 **Inicio del Servicio**

```
🚀 ProximityForegroundService created
✅ All components initialized successfully
Device ID: a1b2c3d4e5f6
▶️ Service started
🔍 Proximity scanning started in LOW_LATENCY mode
```

### 🔍 **Scanner BLE**

#### Inicio del escaneo:
```
🔍 Starting BLE scanning in LOW_LATENCY mode...
📍 Looking for iBeacon UUID: e2c56db5-dffb-48d2-b060-d0f5a71096e0
✅ BLE scan started successfully
⏱️ Starting timeout checker (interval: 2000ms)
```

#### Detección de beacons:
```
📡 Beacon detected: MAC=94:54:c5:2e:94:ec, Major=101, Minor=1, RSSI=-58 dBm
📡 Beacon detected: MAC=38:18:2b:b3:80:34, Major=100, Minor=1, RSSI=-62 dBm
```

#### Detención del escaneo:
```
🛑 Stopping BLE scanning...
✅ BLE scan stopped successfully
```

### 📡 **BeaconProximityManager**

#### Inicialización:
```
📡 ProximityManager initialized for device: a1b2c3d4e5f6
```

#### Primer beacon detectado:
```
🆕 New beacon detected: 94:54:c5:2e:94:ec
```

#### Procesamiento de cada muestra (modo VERBOSE):
```
📶 Beacon 94:54:c5:2e:94:ec - RSSI: -58, Avg: -61.2, Samples: 5, InProximity: false
📶 Beacon 94:54:c5:2e:94:ec - RSSI: -57, Avg: -60.8, Samples: 5, InProximity: false
```

#### Evento ENTER:
```
🟢 ENTER event for beacon 94:54:c5:2e:94:ec - Avg RSSI: -61.2 dBm, Samples: 5
✅ ENTER event emitted successfully
```

#### Evento HEARTBEAT:
```
💓 HEARTBEAT event for beacon 94:54:c5:2e:94:ec - Avg RSSI: -62.5 dBm
```

#### Evento EXIT:
```
🔴 EXIT event for beacon 94:54:c5:2e:94:ec - Reason: RSSI below threshold, Avg RSSI: -71.3 dBm
✅ EXIT event emitted successfully
```

O por timeout:
```
🔴 EXIT event for beacon 94:54:c5:2e:94:ec - Reason: Timeout - not detected, Avg RSSI: -68.0 dBm
✅ EXIT event emitted successfully
```

### 📤 **ProximityEventSender**

#### Envío al backend:
```
📨 Proximity event received: ENTER for beacon 94:54:c5:2e:94:ec
📤 Sending ENTER event to backend:
   └─ Beacon: 94:54:c5:2e:94:ec
   └─ Device: Samsung Galaxy S21 (a1b2c3d4)
   └─ Distance: 2.45m
   └─ RSSI Avg: -61.2 dBm
   └─ Samples: 5
✅ Event sent successfully: OK
```

#### Error de red:
```
❌ Error sending event. HTTP 404: Not Found
```

#### Error de conexión:
```
❌ Exception sending event to backend: Failed to connect to tu-servidor.com
```

### 🛑 **Detención del Servicio**

```
🛑 Service destroyed
🛑 Stopping BLE scanning...
✅ BLE scan stopped successfully
Final stats: Total beacons: 2, In proximity: 1
```

## 🎯 Casos de Uso

### 1️⃣ **Verificar que el servicio inició correctamente**

```bash
adb logcat | grep "ProximityForegroundService"
```

Deberías ver:
- ✅ `🚀 ProximityForegroundService created`
- ✅ `✅ All components initialized successfully`
- ✅ `▶️ Service started`
- ✅ `🔍 Proximity scanning started`

---

### 2️⃣ **Verificar que detecta beacons**

```bash
adb logcat | grep "Beacon detected"
```

Deberías ver:
- ✅ `📡 Beacon detected: MAC=...`

Si NO ves beacons:
- Verifica que el UUID del beacon sea: `e2c56db5-dffb-48d2-b060-d0f5a71096e0`
- Verifica que Bluetooth esté activado
- Verifica permisos de ubicación

---

### 3️⃣ **Ver el flujo completo de un evento ENTER**

```bash
adb logcat | grep -E "ENTER|Beacon.*RSSI"
```

Secuencia esperada:
1. `📶 Beacon XX - RSSI: -58, Avg: -61.2, Samples: 3...`
2. `📶 Beacon XX - RSSI: -59, Avg: -61.5, Samples: 4...`
3. `📶 Beacon XX - RSSI: -60, Avg: -62.0, Samples: 5...`
4. `🟢 ENTER event for beacon XX - Avg RSSI: -62.0 dBm`
5. `📨 Proximity event received: ENTER`
6. `📤 Sending ENTER event to backend`
7. `✅ Event sent successfully`

---

### 4️⃣ **Verificar que envía al backend**

```bash
adb logcat | grep "ProximityEventSender"
```

Deberías ver:
- ✅ `📤 Sending ENTER event to backend:`
- ✅ `✅ Event sent successfully`

Si ves errores:
- `❌ Error sending event. HTTP 404` → Verifica URL en `ApiClient.kt`
- `❌ Exception: Failed to connect` → Verifica conectividad de red
- `❌ Exception: timeout` → Backend no responde o URL incorrecta

---

### 5️⃣ **Monitorear HEARTBEATS**

```bash
adb logcat | grep "HEARTBEAT"
```

Cada 5 segundos deberías ver:
```
💓 HEARTBEAT event for beacon 94:54:c5:2e:94:ec - Avg RSSI: -62.5 dBm
📨 Proximity event received: HEARTBEAT
📤 Sending HEARTBEAT event to backend
✅ Event sent successfully
```

---

### 6️⃣ **Ver eventos EXIT**

```bash
adb logcat | grep "EXIT"
```

Deberías ver:
```
🔴 EXIT event for beacon 94:54:c5:2e:94:ec - Reason: RSSI below threshold
```

O por timeout:
```
🔴 EXIT event for beacon 94:54:c5:2e:94:ec - Reason: Timeout - not detected
```

---

## 🔧 Troubleshooting por Log

### ❌ "Bluetooth not available"
```
❌ Bluetooth not available on this device
```
**Solución**: El dispositivo no tiene BLE. Usa un dispositivo diferente.

---

### ❌ "Bluetooth is disabled"
```
❌ Bluetooth is disabled
```
**Solución**: Activa Bluetooth en Settings.

---

### ⚠️ "Scanning already in progress"
```
⚠️ Scanning already in progress
```
**Info**: El escaneo ya está activo. Esto es normal si intentas iniciarlo dos veces.

---

### ❌ "Permission denied for BLE scanning"
```
❌ Permission denied for BLE scanning
```
**Solución**:
1. Ve a Settings > Apps > AkiEstoy > Permissions
2. Otorga permisos de Ubicación y Bluetooth

---

### 📡 No se detectan beacons
Si ves esto pero NO ves `📡 Beacon detected`:

**Posibles causas**:
1. Beacon apagado o sin batería
2. UUID del beacon diferente
3. Demasiado lejos (RSSI < -90 dBm)
4. Interferencia BLE

**Debugging**:
```bash
# Ver TODOS los beacons (no solo los tuyos)
adb logcat | grep "ScanResult"
```

---

### ❌ No envía al backend

Si ves eventos generados pero NO se envían:

```bash
adb logcat | grep -E "ENTER|EXIT|HEARTBEAT|ProximityEventSender"
```

Deberías ver AMBOS:
- `🟢 ENTER event for beacon...` ✅
- `📤 Sending ENTER event to backend` ❌ (falta)

**Solución**:
1. Verifica URL en `ApiClient.kt`
2. Verifica conectividad: `adb shell ping -c 3 tu-servidor.com`

---

## 📈 Niveles de Log

### VERBOSE (Log.v) - Debugging detallado
- Cada detección de beacon con RSSI
- Beacons con UUID diferente (ignorados)

Para verlos:
```bash
adb logcat *:V | grep Proximity
```

### DEBUG (Log.d) - Información de desarrollo
- Detalles de eventos
- Configuraciones
- Stats

```bash
adb logcat *:D | grep Proximity
```

### INFO (Log.i) - Eventos importantes
- Inicio/fin de servicios
- Eventos ENTER/EXIT/HEARTBEAT
- Éxitos

```bash
adb logcat *:I | grep Proximity
```

### ERROR (Log.e) - Problemas
- Errores de red
- Permisos denegados
- Bluetooth desactivado

```bash
adb logcat *:E | grep Proximity
```

---

## 🎨 Emojis en Logs

Para mejor legibilidad, todos los logs usan emojis:

- 🚀 Inicialización
- 🔍 Escaneo
- 📡 Beacon detectado
- 📶 Nivel de señal
- 🟢 ENTER event
- 🔴 EXIT event
- 💓 HEARTBEAT event
- 📨 Evento recibido
- 📤 Enviando al backend
- ✅ Éxito
- ❌ Error
- ⚠️ Advertencia
- 🛑 Detención

---

## 💡 Filtros Útiles

### Ver solo eventos (sin detalles de RSSI):
```bash
adb logcat | grep -E "🟢|🔴|💓"
```

### Ver solo errores:
```bash
adb logcat | grep "❌"
```

### Ver inicio hasta primer evento:
```bash
adb logcat | grep -E "🚀|🔍|🟢" | head -20
```

### Guardar logs en archivo:
```bash
adb logcat | grep Proximity > proximity_logs.txt
```

### Ver logs en tiempo real con timestamp:
```bash
adb logcat -v time | grep Proximity
```

---

**Tip**: Para mejor experiencia, usa una terminal con soporte de emojis y colores.
