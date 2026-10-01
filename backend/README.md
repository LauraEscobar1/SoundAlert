# SoundAlert — Backend

API en NestJS que convierte sonidos importantes del entorno en alertas visuales y vibraciones para personas con dificultades auditivas. La persistencia es **Supabase (PostgreSQL)**.

```
DISPOSITIVO → POST detección → CLASIFICACIÓN (SoundClassifier: hoy mock)
  → REGLAS del contexto → PRIORIDAD → ALERTA (icono + mensaje + vibración)
  → guardar detección/alerta en Supabase → respuesta JSON para el reloj
```

| Prioridad | Ejemplos | Vibraciones |
|-----------|----------|-------------|
| `DANGER` | sirena, alarma de incendio, detector de humo | 3 |
| `ATTENTION` | bocina, vehículo acercándose, bebé llorando | 2 |
| `INFORMATION` | timbre, golpe en la puerta, teléfono | 1 |

Contextos: `HOME` (casa), `STREET` (calle), `UNIVERSITY`, `WORK`. Cada contexto activa solo los sonidos relevantes (la bocina solo alerta en la calle). Los sonidos de peligro están activos siempre y no se pueden desactivar.

## 1. Configurar `.env`

```bash
cp .env.example .env
```

Rellena con los datos de *Supabase Dashboard → Project Settings → API*:

- `SUPABASE_URL`: la *Project URL* (`https://<ref>.supabase.co`).
- `SUPABASE_SERVICE_ROLE_KEY`: la clave `service_role` (o la *secret key* `sb_secret_...`).

La service role key **solo** se usa en este backend. Nunca va en el reloj ni en ninguna app. `.env` está en `.gitignore`.

## 2. Ejecutar la migración

Supabase Dashboard → **SQL Editor** → *New query* → pega el contenido de `supabase/migrations/20260930000000_init.sql` → **Run**.

(Alternativa con CLI: `supabase link --project-ref <ref>` y después `supabase db push`.)

Si ya habías ejecutado una versión anterior de la migración, ejecuta primero `supabase/reset_dev.sql`. **Borra todas las tablas de SoundAlert y sus datos.**

### Tablas

| Tabla | Contenido |
|-------|-----------|
| `users` | propietario de los relojes (preparada para Supabase Auth) |
| `contexts` | catálogo HOME / STREET / UNIVERSITY / WORK |
| `devices` | relojes, propietario (`owner_id`) y contexto actual |
| `device_settings` | configuración 1:1 (`min_confidence`, `alerts_enabled`). La crea un trigger al insertar el dispositivo |
| `sound_rules` | personalizaciones por dispositivo + contexto + sonido. Si no hay fila, se aplica la regla por defecto del backend |
| `detections` | cada sonido procesado: predicciones, clasificador, `outcome`, `alerted` |
| `alerts` | alerta generada (icono, mensaje, prioridad, `vibration_count`, `status` ACTIVE/ACKNOWLEDGED) |

RLS está activado sin políticas: las claves públicas no pueden leer ni escribir nada.

## 3. Arrancar

```bash
npm install
npm run start:dev
```

- API: `http://localhost:3000/api/v1`
- Swagger: `http://localhost:3000/docs`
- Salud y conexión a la base de datos: `http://localhost:3000/health`

Sin `SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY` el backend **no arranca** (no hay fallback en memoria).

## Endpoints (`/api/v1`)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/catalog/sounds` · `/catalog/priorities` · `/catalog/contexts` | Catálogos (iconos, colores, vibraciones) |
| POST · GET | `/users` · `/users/:userId` | Crear / ver usuario |
| GET | `/users/:userId/devices` | Relojes de un usuario |
| POST | `/devices` | Registrar reloj |
| GET · PATCH · DELETE | `/devices/:deviceId` | Ver / modificar datos y configuración / eliminar |
| GET · PUT | `/devices/:deviceId/context` | Ver / cambiar el contexto actual |
| GET · PUT · DELETE | `/devices/:deviceId/contexts/:context/rules` | Ver / personalizar / restablecer reglas |
| POST | `/devices/:deviceId/detections/audio` | **Flujo principal**: el servidor clasifica, prioriza y alerta |
| POST | `/devices/:deviceId/detections/classified` | El reloj ya clasificó localmente; el servidor prioriza y alerta |
| GET | `/devices/:deviceId/detections` · `/detections/:detectionId` | Historial / detalle con su alerta |
| GET | `/devices/:deviceId/alerts` | Historial de alertas (`limit`, `priority`, `status`, `since`) |
| POST | `/devices/:deviceId/alerts/:alertId/ack` | Marcar alerta como vista |

Si no hay alerta, `outcome` explica por qué: `NO_PREDICTIONS`, `UNKNOWN_SOUND`, `LOW_CONFIDENCE`, `DISABLED_IN_CONTEXT`, `COOLDOWN` (mismo sonido hace menos de `ALERT_COOLDOWN_SECONDS`) o `ALERTS_DISABLED`.

Errores: `400` validación (UUID, enums, `confidence` fuera de 0-1, campos desconocidos), `404` no encontrado, `401` API key (si `API_KEY` está definida), `413` audio demasiado grande, `503` clasificador remoto caído, `500` error de base de datos (el detalle solo aparece en el log del servidor).

## Probar desde Swagger

1. `POST /devices` → `{ "name": "Mi reloj", "currentContext": "STREET" }` → copia el `id`.
2. `POST /devices/{deviceId}/detections/audio` con:
   ```json
   { "audioBase64": "AAAA", "format": "PCM_16LE", "sampleRate": 16000, "channels": 1, "simulatedLabel": "Siren" }
   ```
3. Prueba `"Vehicle horn"` y `"Doorbell"`, y cambia el contexto con `"context": "HOME"` en el body o con `PUT /devices/{id}/context`.
4. Consulta `GET /devices/{id}/alerts` y `GET /devices/{id}/detections`, o mira las tablas en el *Table Editor* de Supabase.

## Tests

```bash
npm test               # unitarios: clasificador mock, reglas, prioridad/vibración, motor, flujo, repositorios (memoria)
npm run test:e2e       # API HTTP completa con base de datos en memoria
npm run test:supabase  # contra tu Supabase REAL (lee backend/.env): esquema, INSERT/SELECT, flujo completo
```

`test:supabase` crea datos de prueba y los borra al terminar. Con `KEEP_TEST_DATA=1 npm run test:supabase` se conservan para verlos en el Table Editor.

## Integrar la IA (pendiente)

Todo lo relacionado con el modelo está en `src/modules/classification/`. El resto del backend solo conoce el contrato `SoundClassifier`:

```ts
interface SoundClassifier {
  readonly info: { name: string; version: string };
  isReady(): boolean;
  classify(input: AudioInput): Promise<ClassificationResult[]>; // [{ category, confidence 0-1, rawLabel }]
}
```

- `mock-sound-classifier.ts` — implementación actual: devuelve `simulatedLabel`.
- `label-mapper.ts` — traduce etiquetas del modelo (AudioSet: "Siren", "Vehicle horn, car horn, honking"…) a `SoundCategory`.
- `remote-sound-classifier.ts` — delega en un servicio HTTP (`CLASSIFIER_PROVIDER=remote`).
- `classification.module.ts` — **único** punto donde se elige la implementación.

Para añadir un modelo (`YamnetSoundClassifier`, `OnnxSoundClassifier`…): crea una clase que implemente `SoundClassifier`, añade un `case` en `classification.module.ts` y su valor en la validación de `CLASSIFIER_PROVIDER` (`config/configuration.ts`). El test *“depende solo del contrato SoundClassifier”* de `detections.service.spec.ts` comprueba que el flujo funciona con cualquier implementación.

## Autenticación (pendiente)

`src/auth/auth.guard.ts` es un guard global. Hoy, si `API_KEY` está definida, exige la cabecera `x-api-key`; si no, deja pasar. Para la autenticación real (JWT de Supabase Auth) solo hay que cambiar ese guard, enlazar `users` con `auth.users` y comprobar que el `deviceId` pertenece al usuario. `@Public()` marca las rutas abiertas.

## Estructura

```
src/
  auth/              guard global (API key opcional, preparado para JWT)
  config/            variables de entorno y validación
  domain/            enums, catálogo de sonidos, prioridad/vibración, reglas por contexto
  database/          contratos de repositorios + Supabase (+ memoria solo para tests)
  modules/
    classification/  frontera con la IA (SoundClassifier)
    detections/      orquesta el flujo completo
    alerts/          motor de decisión (alert-engine.ts) e historial
    rules/  devices/  users/  catalog/  health/
supabase/migrations/ esquema SQL
test/                e2e (memoria) e integración (Supabase real)
```
