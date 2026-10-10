# SoundAlert — Wear OS (escucha continua + YAMNet + alertas)

Primera versión del pipeline real de audio e IA en el reloj:

```
Micrófono → AudioRecord (16 kHz, mono, PCM16, bloques de 500 ms)
→ buffer circular (1 s) → puerta de energía → PCM16 → float [-1, 1]
→ YAMNet (LiteRT, CPU) → 521 clases → SoundCategory
→ estabilizador (2 de 3, vía rápida de peligro, histéresis) → evento → logcat
```

Todo corre dentro de un **foreground service de tipo `microphone`** (`ListeningService`): sigue escuchando con la pantalla apagada o la app cerrada. `MainActivity` muestra la **interfaz de los mockups** (ver *Interfaz del reloj*).

Fase 2 (local, sin backend): cada evento estable sigue con

```
DetectionEvent → contexto actual → regla → prioridad → Alert → vibración real
```

Fase 3: interfaz de los mockups y sincronización con el backend (Railway → Supabase), secundaria a la detección local. Fuera de estas fases: Room, WorkManager, reglas personalizadas en el reloj, contexto automático (ubicación/movimiento).

## Contexto, reglas, alertas y vibración

- **Contexto activo** (`context/`): `CASA`, `CALLE`, `OTRO` (`SoundAlertContext.ACTIVE`; `OTRO` por defecto). Es independiente del sonido: YAMNet dice qué suena; el contexto dice dónde está la persona. La fuente es la interfaz `ActiveContextProvider`; hoy la implementa `ContextManager` (contexto explícito) y en el futuro podrá implementarla un proveedor por ubicación/movimiento sin tocar el resto. `TRABAJO` y `TRANSPORTE` se conservan con sus reglas, pero no pueden ser el contexto activo. Cada contexto lleva su código de la API (`CASA→HOME`, `CALLE→STREET`, `OTRO→OTHER`). El contexto elegido se guarda (`SharedPreferences`, `ContextStore`) y se restaura al arrancar el proceso (`SoundAlertApp`), así que sobrevive a que Android mate la app, a una reinstalación o a un reinicio; `OTRO` solo se usa si nunca se eligió nada.
- **Clasificación contextual** (`ContextualClassifier`): lee el contexto activo **una sola vez** por detección confirmada y lo congela en una `ContextualDetection` (categoría + contexto + regla). Se guarda en `DetectionHistory` (todas las detecciones, alerten o no, agrupables por contexto) y llega al `AlertManager`; la `Alert` conserva ese contexto y su `detectionId`.
- **Reglas** (`rules/RuleEngine.kt`): configuración fija `contexto + categoría → prioridad`. Las detecciones solo las leen. Un sonido sin regla en el contexto actual (p. ej. bocina en `CASA`) no alerta. `UNKNOWN` nunca tiene regla. Sirena, alarma de incendio y detector de humo son `DANGER` en todos los contextos.
- **Alertas** (`alert/AlertManager.kt`):

| Prioridad | Vibración | Cierre |
|-----------|-----------|--------|
| `DANGER` | 3 largas (600 ms); se repiten cada 15 s mientras siga sin confirmar | Solo con confirmación explícita → `ACKNOWLEDGED`, que detiene la repetición. Nunca expira |
| `ATTENTION` | 2 medias (350 ms) | Toque → `ACKNOWLEDGED`, o a los 10 s → `EXPIRED` |
| `INFORMATION` | 1 corta (150 ms) | Toque → `ACKNOWLEDGED`, o a los 5 s → `EXPIRED` |

  Repeticiones: un sonido sostenido llega como un único evento (estabilizador); además no se crea otra alerta de una categoría con una alerta `ACTIVE`, ni antes de 10 s desde la anterior de esa categoría (cooldown). Confirmar no detiene el micrófono ni YAMNet. Historial en memoria de las últimas 50 alertas.
- **Vibración** (`vibration/`): `AndroidAlertVibrator` usa `VibratorManager` + `VibrationEffect.createWaveform`, con uso `ALARM` para `DANGER`/`ATTENTION` y `NOTIFICATION` para `INFORMATION`. En el emulador no se siente; se comprueba con `adb shell dumpsys vibrator_manager`.
- **Confirmar un `DANGER`**: botón "Entendido" de la pantalla de alerta, o acción "Confirmar" de la notificación de escucha mientras haya un peligro activo.

Vibración: una vibración en curso de mayor prioridad no se interrumpe por otra de menor prioridad (Android sustituye la vibración actual por la nueva); la alerta se crea igual y solo se omite su vibración. Confirmar una alerta solo corta la vibración si es la suya. El historial en memoria recorta solo alertas cerradas: una `ACTIVE` nunca se pierde.

La repetición de `DANGER` vibra de nuevo la MISMA alerta (no crea alertas ni eventos), sigue aunque el sonido haya terminado y se detiene al confirmar. Una sirena nueva después de confirmar crea otra alerta independiente.

Valores en `config/AlertConfig.kt` (timeouts, cooldown, `dangerRepeatIntervalMs`, duración de pulsos).

## Interfaz del reloj

Jetpack Compose para Wear OS (Material 3, `ui/`). Las pantallas solo **leen** el estado que ya existe (`PipelineStatus`, `AlertManager`, `ContextManager`, `RuleEngine`, `DetectionHistory`); no deciden nada del sonido.

| Pantalla | Qué muestra | De dónde salen los datos |
|----------|-------------|--------------------------|
| A · Reposo | Hora, chip del contexto, oreja (tocar = activar/pausar), "Escuchando" y 3 sonidos vigilados | `PipelineStatus.phase`, `ContextManager`, `RuleEngine.rulesFor` |
| B · Sonido detectado | "IA · ANALIZANDO" / "IA · CONFIRMADO", onda, "Posible sirena 94 %", y si no alerta en ese contexto o es solo registro | Clases principales de YAMNet (≥ umbral de fin 0,15) y último `Started` del estabilizador (`ui/state/DetectionTracker`). UNKNOWN nunca se muestra |
| C · Alerta | PELIGRO: pantalla roja + "Entendido". ATENCIÓN: anillo ámbar, un toque la cierra. AVISO: icono azul + barra de 5 s | La alerta ACTIVE más urgente del `AlertManager`; estados, tiempos y vibración son los suyos |
| D · Contexto | CASA, CALLE, OTRO (manual; se guarda en el reloj) | `ContextManager.set` |
| E · Sonidos en *contexto* | Reglas reales del contexto: punto = prioridad, interruptor = alerta aquí. **Solo lectura** | `RuleEngine` |
| F · Historial | Detecciones y alertas: contexto, estado (confirmada, sin confirmar, se cerró sola, sin alerta, solo registro) y hora | Backend si hay conexión (+ lo pendiente de enviar); si no, memoria del reloj |

Navegación: A → E → F deslizando en horizontal (indicador de página abajo); D tocando el chip del contexto y se vuelve deslizando a la derecha; C aparece encima de cualquier pantalla. Las listas se desplazan con la corona y las filas se estrechan hacia el borde (`TransformingLazyColumn`). En builds de depuración, una pulsación larga en la oreja abre la pantalla de diagnóstico anterior.

- Tipografía: Archivo variable (`res/font/archivo.ttf`, OFL, ver `assets/NOTICE-archivo.txt`). Iconos: Material Icons (Apache 2.0) como vectores locales en `res/drawable/`. Colores medidos sobre los mockups (`ui/theme/Theme.kt`).
- E es de solo lectura porque el `RuleEngine` no aplica reglas personalizadas: un interruptor editable no tendría efecto.

## Conexión con el backend (Railway)

```
Reloj (YAMNet → reglas → alerta/vibración → UI)   ← funciona sin internet
   └─ BackendSync (cola en disco) → API Railway → Supabase
```

- URL: `soundalert.apiUrl` en `gradle.properties` → `BuildConfig.API_BASE_URL` (se cambia con `-Psoundalert.apiUrl=…`). El reloj no lleva claves.
- **Dispositivo**: la primera vez el reloj se registra (`POST /devices`, `WEAR_OS`, contexto actual, `minConfidence` = umbral más bajo del reloj para que el backend no descarte lo ya confirmado) y guarda el id (`SharedPreferencesDeviceStore`). En cada arranque lo comprueba con `GET /devices/:id`; si ya no existe, se registra de nuevo.
- **Qué se envía** (`sync/BackendSync.kt`): cambio de contexto (`PUT /devices/:id/context`), cada detección confirmada con su contexto congelado (`POST …/detections/classified`, con el código de la categoría, p. ej. `SIREN`) y cada confirmación del usuario (`POST …/alerts/:alertId/ack`).
- **Sin internet**: todo se encola en `files/sync-queue.json` y se envía al volver la red (reintentos 2–60 s y aviso de conectividad). Errores 4xx descartan solo esa operación; 5xx y red se reintentan.
- Logs: `adb logcat -s SA/Sync`.

## Requisitos

- Android Studio con SDK 37 y el AVD `Wear_OS_Small_Round` (Wear OS 7.0, arm64-v8a).
- JDK 17+ (sirve el de Android Studio).

## Compilar y probar

```bash
cd wear
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest          # lógica pura: buffer, puerta, mapeo de etiquetas, estabilizador
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest  # con el emulador encendido: YAMNet real (tensores, silencio, latencia)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Abre SoundAlert en el reloj y pulsa **Activar**. Android solo concede el micrófono a un foreground service iniciado con la app visible, por eso hay que activarlo desde la app.

## Audio real en el emulador

1. Arranca el emulador **desde Android Studio** (Device Manager), para que macOS atribuya el uso del micrófono a Android Studio.
2. Extended controls (`⋯`) → **Microphone** → activa **Virtual microphone uses host audio input**. Está desactivado por defecto y puede volver a desactivarse al reiniciar el emulador.
3. Si macOS lo pide, permite el acceso al micrófono (*Ajustes del Sistema → Privacidad y seguridad → Micrófono*).
4. Reproduce sonidos cerca del micrófono del Mac.

Si el emulador no puede abrir el micrófono del Mac, su log muestra `coreaudio: Could not initialize record` y el reloj recibe un **tono de prueba**: YAMNet lo clasifica como `Sine wave` (~0,98) a −3 dBFS constantes.

## Logs

```bash
adb logcat -v time 'SA/Service:V' 'SA/Audio:V' 'SA/YAMNet:V' 'SA/Event:V' 'SA/Context:V' 'SA/Rule:V' 'SA/Alert:V' 'SA/Vibration:V' '*:S'
# añade 'SA/Gate:V' para ver el nivel de cada bloque
```

| Tag | Qué muestra |
|-----|-------------|
| `SA/Service` | Inicio y parada de la escucha y configuración aplicada |
| `SA/Audio` | Fuente real (`VOICE_RECOGNITION`/`MIC`), formato, micrófono silenciado o liberado |
| `SA/Gate` | Nivel (dBFS), ruido de fondo y si se infiere (`LOUD`, `HANGOVER`, `PERIODIC`) o no (`QUIET`) |
| `SA/YAMNet` | Latencia de cada inferencia, las 3 clases principales y la mejor categoría de SoundAlert |
| `SA/Event` | `START SIREN confidence=0.82 label="Siren"` y `END SIREN peak=0.91 duration=4.5s` |
| `SA/Context` | `contexto actual=CALLE (antes OTRO)` |
| `SA/Rule` | `SIREN + CALLE -> DANGER` o `CAR_HORN + CASA -> sin regla` |
| `SA/Alert` | `START DANGER SIREN …`, `REPEAT DANGER … vibración #2`, `ACK DANGER …`, `EXPIRED ATTENTION …`, `COOLDOWN …`, `… ya está ACTIVE; no se crea otra` |
| `SA/Vibration` | `DANGER pattern=3_LONG timings=[0, 600, 250, 600, 250, 600]` |

## Parámetros

Todos están en `config/PipelineConfig.kt` y son **valores iniciales sin calibrar**:

| Parámetro | Valor |
|-----------|-------|
| Ventana | 15 600 muestras (0,975 s) |
| Salto entre ventanas | 500 ms |
| Puerta de energía | > máx(−55 dBFS, fondo + 6 dB); margen de 3 s; inferencia forzada cada 5 s |
| Inicio de evento | ≥ umbral de la categoría en 2 de 3 ventanas; peligro (sirena y alarmas): una ventana ≥ 0,60 |
| Umbral por categoría | `DEFAULT_ON_THRESHOLDS`: `CAR_HORN` 0,25 (bocinas reales por altavoz dieron 0,26–0,33); `SIREN`, `FIRE_ALARM`, `SMOKE_ALARM`, `BICYCLE_BELL`, `DOORBELL`, `DOOR_KNOCK` 0,35; el resto usa 0,35 |
| Fin de evento | < 0,15 durante 2 s (5 s en peligro) |

Las puntuaciones de YAMNet salen en pasos de 1/256 porque la salida del modelo está cuantizada.

## Modelo

`app/src/main/assets/yamnet.tflite` es el modelo oficial `google/yamnet` (variante classification, TFLite v1, 4,1 MB, Apache 2.0). En `NOTICE-yamnet.txt` están su origen, el checksum y la licencia. `yamnet_class_map.csv` coincide índice a índice con la lista de etiquetas que lleva el modelo.

## Catálogo de sonidos

Hay tres tipos de sonido:

- **Alertables**: categorías con clase YAMNet específica y reglas por contexto.
- **Solo registro** (`BELL`, `WARNING_SIGNAL`): clases genéricas. Se detectan y aparecen en el log (`SA/Event`, `SA/Alert: REGISTRO …`), pero nunca alertan ni vibran; el `RuleEngine` rechaza cualquier regla para ellas.
- **`UNKNOWN`**: todo lo demás, incluidos por decisión explícita voz, conversación, música, aplausos y pasos. Nunca genera eventos ni tiene reglas.

Las clases genéricas se descartan en la ventana si aparece su versión específica (`SoundCategory.SUPPRESSED_BY`): `Alarm` frente a sirena, incendio, humo…; `Bell` frente a timbre de bicicleta o de puerta; `Beep`/`Buzzer` frente a cualquier alarma.

| Categoría | Clases YAMNet | Prioridad por defecto | Umbral · vía rápida | Validación |
|-----------|---------------|-----------------------|---------------------|------------|
| `SIREN` | Siren, Civil defense siren, Police car (siren), Ambulance (siren), Fire engine… (siren), Emergency vehicle | DANGER | 0,35 · 0,60 | Grabaciones 5/5 · micrófono |
| `FIRE_ALARM` | Fire alarm | DANGER | 0,35 · 0,60 | Micrófono (una prueba) |
| `SMOKE_ALARM` | Smoke detector, smoke alarm | DANGER | 0,35 · 0,60 | Micrófono (una prueba) |
| `GENERAL_ALARM` | Alarm (padre; se descarta si hay una alarma específica) | ATTENTION | 0,50 | Sin datos |
| `CAR_HORN` | Vehicle horn…, Air horn, truck horn, Toot (coche, moto o camión: YAMNet no los distingue) | ATTENTION | 0,25 | Grabaciones 4/5 · micrófono |
| `CAR_ALARM` | Car alarm | ATTENTION | 0,50 | Sin datos |
| `TIRE_SKID` | Skidding, Tire squeal | ATTENTION | 0,50 | Sin datos |
| `REVERSING_VEHICLE` | Reversing beeps | ATTENTION | 0,50 | Sin datos |
| `TRAIN_HORN` | Train horn, Train whistle | ATTENTION | 0,50 | Sin datos |
| `BICYCLE_BELL` | Bicycle bell | ATTENTION | 0,35 | Sin datos |
| `GLASS_BREAK` | Shatter | ATTENTION | 0,50 · 0,70 | Grabaciones 3/5 |
| `SCREAM` | Screaming | ATTENTION | 0,50 | Sin datos |
| `BABY_CRYING` | Baby cry, infant cry | INFORMATION (Casa, Otro) | 0,35 | Grabaciones 2/5 |
| `DOG_BARK` | Bark | INFORMATION | 0,35 | Grabaciones 5/5 |
| `BELL` | Bell, Church bell, Jingle bell, Chime | Solo registro | 0,50 | Grabaciones: campanas de iglesia 5/5 |
| `WARNING_SIGNAL` | Beep, bleep, Buzzer | Solo registro | 0,50 | Modelo real: un tono de 1 kHz da Beep 0,74 |
| `DOORBELL` | Doorbell, Ding-dong | INFORMATION | 0,35 | Sin datos |
| `DOOR_KNOCK` | Knock | INFORMATION | 0,35 | Grabaciones 4/5 · micrófono |
| `PHONE_RING` | Telephone bell ringing, Ringtone | INFORMATION | 0,35 | Micrófono (Ringtone 0,92) |
| `ALARM_CLOCK` | Alarm clock | INFORMATION | 0,35 | Grabaciones 3/5 (YAMNet lo confunde con PHONE_RING) |
| `WATER_RUNNING` | Water tap, faucet | INFORMATION | 0,35 | Sin datos |

Umbrales en `config/PipelineConfig.kt` (`DEFAULT_ON_THRESHOLDS`, `DEFAULT_FAST_PATH_THRESHOLDS`); reglas por contexto en `rules/RuleEngine.kt`. Las categorías sin datos tienen valores conservadores, pendientes de calibrar.

**Descartadas** (no hay clase fiable o hay demasiados falsos positivos): explosión, disparos, golpes fuertes, caída, choque, terremoto (ninguna clase de YAMNet lo representa), detector de CO, temporizador (los pitidos genéricos quedan como `WARNING_SIGNAL` de solo registro), bocina de moto (no existe esa clase en YAMNet: se detecta como `CAR_HORN`), moto como categoría (es ruido de fondo), vehículo acercándose ("Car passing by" no indica que se acerque), microondas ("Microwave oven" es el aparato funcionando, no su pitido), campanas genéricas, pasos, tráfico, tren, avión, voz, música, tos, aplausos.

Respecto al backend: faltan en el reloj `NAME_CALLED`, `SCHOOL_BELL`, `KETTLE_WHISTLE`, `VEHICLE_APPROACHING` y `MICROWAVE_BEEP`; son nuevas `GENERAL_ALARM`, `CAR_ALARM`, `TIRE_SKID`, `REVERSING_VEHICLE`, `TRAIN_HORN`, `GLASS_BREAK` y `SCREAM`. Habrá que alinearlo al sincronizar.

### Evaluar con grabaciones reales

```bash
tools/fetch-eval-clips.sh          # ESC-50 (CC BY-NC) → app/src/androidTest/assets/clips/ (en .gitignore)
./gradlew connectedDebugAndroidTest
adb logcat -s SA/Eval              # resultado por clip y RESUMEN por categoría
```

`ClipEvaluationTest` pasa cada clip por el pipeline real (buffer, puerta, YAMNet, mapeo, estabilizador) sin micrófono. Falla si un negativo (respiración, tecleo, tos, risas, pasos, aspiradora, voz…) genera algún evento **alertable**; las campanas de iglesia pueden generar `BELL` (solo registro). Los aciertos de los positivos se informan. Sin clips, el test se omite.

## Limitaciones conocidas

- `android.uniquePackageNames=false` en `gradle.properties` es una solución temporal para [LiteRT#6965](https://github.com/google-ai-edge/LiteRT/issues/6965).
- LiteRT 2.2.0 añade al APK `FOREGROUND_SERVICE_DATA_SYNC` y WorkManager, que no usamos, además de librerías nativas pesadas: el APK de debug pesa ~50 MB.
- El servicio no se reanuda solo tras reiniciar el reloj ni si Android mata el proceso: Android 14+ no da acceso al micrófono a un servicio iniciado en segundo plano.
- La latencia medida en el emulador (~1 ms) usa la CPU del Mac: no es representativa de un reloj. La batería solo se puede medir en un reloj físico.
- Las vibraciones de `INFORMATION` usan `USAGE_NOTIFICATION`, y en Wear OS 7 (API 37) el sistema las ignora fuera de una notificación (`dumpsys vibrator_manager` → `ignored_app_ops`, restricción de audio "Zen" activa aunque No molestar esté apagado). `DANGER` y `ATTENTION` (`USAGE_ALARM`) sí vibran. Pendiente de decidir el cambio de uso.
- El backend fecha cada detección al recibirla: lo que se envía desde la cola sin conexión queda con la hora de envío, y el cooldown del backend puede marcar como `COOLDOWN` detecciones que llegan juntas.
- Cada instalación es un dispositivo nuevo en el backend (el id se guarda en los datos de la app; `connectedDebugAndroidTest` desinstala la app).
- Con la app en segundo plano, una alerta no enciende la pantalla: `DANGER` sigue en la notificación de escucha. Mostrarla a pantalla completa requiere `USE_FULL_SCREEN_INTENT`, restringido desde Android 14.
