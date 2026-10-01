# SoundAlert — Wear OS (fase 1: escucha continua + YAMNet)

Primera versión del pipeline real de audio e IA en el reloj:

```
Micrófono → AudioRecord (16 kHz, mono, PCM16, bloques de 500 ms)
→ buffer circular (1 s) → puerta de energía → PCM16 → float [-1, 1]
→ YAMNet (LiteRT, CPU) → 521 clases → SoundCategory
→ estabilizador (2 de 3, vía rápida de peligro, histéresis) → evento → logcat
```

Todo corre dentro de un **foreground service de tipo `microphone`** (`ListeningService`): sigue escuchando con la pantalla apagada o la app cerrada. `MainActivity` es una **pantalla de diagnóstico temporal**, no la interfaz de los mockups.

Fuera de esta fase: contexto, reglas, alertas, vibración, Room, WorkManager, sincronización y backend.

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
adb logcat -v time 'SA/Service:V' 'SA/Audio:V' 'SA/YAMNet:V' 'SA/Event:V' '*:S'   # añade 'SA/Gate:V' para ver el nivel de cada bloque
```

| Tag | Qué muestra |
|-----|-------------|
| `SA/Service` | Inicio y parada de la escucha y configuración aplicada |
| `SA/Audio` | Fuente real (`VOICE_RECOGNITION`/`MIC`), formato, micrófono silenciado o liberado |
| `SA/Gate` | Nivel (dBFS), ruido de fondo y si se infiere (`LOUD`, `HANGOVER`, `PERIODIC`) o no (`QUIET`) |
| `SA/YAMNet` | Latencia de cada inferencia, las 3 clases principales y la mejor categoría de SoundAlert |
| `SA/Event` | `START SIREN confidence=0.82 label="Siren"` y `END SIREN peak=0.91 duration=4.5s` |

## Parámetros

Todos están en `config/PipelineConfig.kt` y son **valores iniciales sin calibrar**:

| Parámetro | Valor |
|-----------|-------|
| Ventana | 15 600 muestras (0,975 s) |
| Salto entre ventanas | 500 ms |
| Puerta de energía | > máx(−55 dBFS, fondo + 6 dB); margen de 3 s; inferencia forzada cada 5 s |
| Inicio de evento | ≥ 0,35 en 2 de 3 ventanas; peligro (sirena y alarmas): una ventana ≥ 0,60 |
| Fin de evento | < 0,15 durante 2 s (5 s en peligro) |

Las puntuaciones de YAMNet salen en pasos de 1/256 porque la salida del modelo está cuantizada.

## Modelo

`app/src/main/assets/yamnet.tflite` es el modelo oficial `google/yamnet` (variante classification, TFLite v1, 4,1 MB, Apache 2.0). En `NOTICE-yamnet.txt` están su origen, el checksum y la licencia. `yamnet_class_map.csv` coincide índice a índice con la lista de etiquetas que lleva el modelo.

Categorías de la v1: las del backend **sin** `NAME_CALLED`, `SCHOOL_BELL` ni `KETTLE_WHISTLE`, que YAMNet no distingue de forma fiable.

## Limitaciones conocidas

- `android.uniquePackageNames=false` en `gradle.properties` es una solución temporal para [LiteRT#6965](https://github.com/google-ai-edge/LiteRT/issues/6965).
- LiteRT 2.2.0 añade al APK `FOREGROUND_SERVICE_DATA_SYNC` y WorkManager, que no usamos, además de librerías nativas pesadas: el APK de debug pesa ~50 MB.
- El servicio no se reanuda solo tras reiniciar el reloj ni si Android mata el proceso: Android 14+ no da acceso al micrófono a un servicio iniciado en segundo plano.
- La latencia medida en el emulador (~1 ms) usa la CPU del Mac: no es representativa de un reloj. La batería solo se puede medir en un reloj físico.
