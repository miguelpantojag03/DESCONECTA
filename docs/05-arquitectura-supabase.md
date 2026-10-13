# Arquitectura con Supabase como base de datos principal

## Qué cambia respecto a lo ya construido

| Área | Hoy (implementado) | Con esta decisión |
|---|---|---|
| Fuente de verdad | `GameState` en DataStore (teléfono); Supabase solo como copia en JSON (`players.state`) | **Supabase** con tablas normalizadas (`profiles`, `monsters`, `daily_sessions`…). El teléfono guarda en **Room** como caché offline. |
| Room | Solo historial (`day_history`) y logros | Caché de **todas** las tablas + cola de cambios **pendientes** (`sync_queue`). |
| DataStore | Partida completa + PIN + preferencias | Solo preferencias del dispositivo (tema, haptics, sonido, modo solo-dispositivo). El PIN pasa a ser una verificación del lado del servidor (hash en `profiles`). |
| Reloj ↔ nube | El reloj se vincula con un código de 6 dígitos y se conecta **directo** a Supabase por Wi‑Fi | **Se elimina.** El reloj vuelve a hablar **solo** con el teléfono (Data Layer). Ahorra batería y no maneja credenciales. Se borran `link_codes`, `redeem_link_code`, `get_state_by_token` y la pantalla "Vincular con código". |
| Autenticación | Sesión **anónima** automática | **Correo + contraseña y Google.** Nuevas pantallas: inicio de sesión, registro, recuperar contraseña, verificar correo, sesión expirada. Un trigger crea `profiles` al registrarse. Opción **"Solo en este dispositivo"** sin cuenta. |
| Reglas del juego | `GameEngine` en el teléfono aplica todo localmente | El `GameEngine` **sigue** aplicando el daño y el bloqueo offline (nunca dependen de internet). Lo **sensible** pasa a RPC en el servidor: usar Revivir, sumar XP, desbloquear logros y canjear objetos, con validación anti-trampas. |
| Inventario | Campo dentro del estado | Tabla `inventory` **recalculada a partir de `item_events`** (fuente auditable). |
| Uso de apps | Minutos acumulados por monstruo en el estado | Tabla `app_usage` **agregada por hora**, subida por lotes (no en tiempo real). |
| Sincronización | Subida inmediata en cada cambio | **SyncWorker** (WorkManager) con reintentos y backoff exponencial; gana el `updated_at` más reciente (salvo el inventario). |
| Realtime | Broadcast de estado al reloj | `postgres_changes` sobre `inventory`, `profiles`, `user_achievements` y `weekly_challenges`: un level up o un logro llega al instante al teléfono, que lo **reenvía al reloj**. |
| Catálogos | Logros, monstruos y accesorios en enums de Kotlin | Tablas de catálogo de solo lectura + datos **seed**. Ilustraciones en el bucket público `game-assets` con caché local (los sprites en código quedan como respaldo offline). |
| Jefe semanal | `BossCatalog` local que rota 4 plantillas | `weekly_challenges`, que puede generar la Edge Function `generate-weekly-boss`. |
| Nuevas pantallas | — | Autenticación (5), consentimiento de nube en el onboarding, indicador de sincronización, **exportar datos (JSON)** y **borrar cuenta**. |
| IA | No existe | Tablas `ai_insights` y `ai_settings` + 4 Edge Functions. La IA en la nube queda **desactivada por defecto** y siempre en modo familiar. |

Lo que **no cambia**: el sistema de diseño, la HealthBar, todas las pantallas del reloj, la medición con
UsageStatsManager, el bloqueo por accesibilidad y Device Admin, y el protocolo reloj ↔ teléfono.

## Entregable 1: arquitectura completa

```
┌──────────────────────────── RELOJ (Wear OS) ─────────────────────────────┐
│  HUD · Monstruos · Inventario · Alertas · Tile · Complicaciones           │
│  WatchViewModel ── WatchGameRepository (caché del último estado)          │
│  · Sin credenciales, sin internet propio para HP                          │
│  · "Inicia sesión en tu teléfono" si el teléfono no tiene sesión          │
└───────────────▲──────────────────────────────────────┬────────────────────┘
                │ DataClient: GameState (persistente)  │ MessageClient: comandos
                │ MessageClient: eventos (alertas)     │ (poción, escudo, límite, revivir+PIN)
┌───────────────┴──────────────────────────────────────▼────────────────────┐
│                          TELÉFONO (Android)                               │
│                                                                           │
│  UI (Compose) ── ViewModels ── Repositorios (offline-first)               │
│                                     │                                     │
│   ┌─────────────── Dominio ─────────┴────────────┐                        │
│   │ GameEngine (daño, bloqueo, regeneración)     │  ◀── funciona SIEMPRE  │
│   │ → NUNCA depende de internet                  │      sin conexión      │
│   └──────────────────────────────────────────────┘                        │
│        │ escribe primero en               │ lee de                        │
│   ┌────▼──────────────────────────────────▼──────┐   ┌──────────────────┐ │
│   │ Room (caché + sync_queue "pendiente")        │   │ DataStore        │ │
│   │ profiles · monsters · daily_sessions ·       │   │ tema · haptics · │ │
│   │ app_usage(hora) · inventory · item_events ·  │   │ sonido · modo    │ │
│   │ achievements · challenges · ai_insights      │   │ solo-dispositivo │ │
│   └────┬─────────────────────────────────────────┘   └──────────────────┘ │
│        │ SyncWorker (WorkManager): sube pendientes con conexión,          │
│        │ reintentos + backoff exponencial; gana updated_at más reciente   │
│        │ (inventario: se recalcula desde item_events)                     │
│   MonitorService · BlockerService · DeviceAdmin · RespawnWorker           │
│   SupabaseClient (Hilt @Singleton): Auth · Postgrest · Realtime ·         │
│                                     Storage · Functions                   │
└────────┬──────────────────▲─────────────────────────┬─────────────────────┘
         │ HTTPS (JWT del   │ Realtime                │ invoke(JWT)
         │ usuario) + RLS   │ postgres_changes        │
┌────────▼──────────────────┴─────────────────────────▼─────────────────────┐
│                              SUPABASE                                     │
│  Auth: correo+contraseña, Google · trigger → crea profiles                │
│                                                                           │
│  PostgreSQL (RLS en TODAS las tablas: auth.uid() = user_id)               │
│   Usuario: profiles · monsters · daily_sessions · app_usage · inventory · │
│            item_events · user_achievements · user_avatar_items ·          │
│            weekly_challenges · ai_insights · ai_settings · devices        │
│   Catálogo (solo lectura): achievements · avatar_items · monster_catalog  │
│   RPC anti-trampas (security definer): revive · grant_xp ·                │
│            unlock_achievement · use_item · close_day (respawn) ·          │
│            export_my_data · delete_my_account                             │
│                                                                           │
│  Realtime → inventory · profiles · user_achievements · weekly_challenges  │
│  Storage  → avatars (privado, carpeta por usuario) ·                      │
│             game-assets (público, solo lectura)                           │
│                                                                           │
│  Edge Functions (Deno/TS)  ──▶  Modelo de lenguaje (clave como secreto)   │
│   generate-daily-summary · generate-weekly-boss · suggest-limits ·        │
│   coach-message                                                           │
│   Cada una: verifica JWT → ai_settings.nube_permitida = true → solo       │
│   datos agregados → límite de uso por usuario → respuesta de respaldo     │
│   si el modelo falla. service_role SOLO aquí, nunca en la app.            │
└───────────────────────────────────────────────────────────────────────────┘
```

### Flujos clave

| Flujo | Recorrido |
|---|---|
| Daño en tiempo real | UsageStats → `GameEngine` (local) → Room (`app_usage` pendiente) → reloj por Data Layer. La nube recibe el agregado por hora cuando hay red. |
| Usar poción en el reloj | Reloj → teléfono (MessageClient) → `GameEngine` aplica al instante (offline) → `item_events` "usado/reloj" pendiente → SyncWorker → RPC `use_item` valida en el servidor → `inventory` recalculado → Realtime confirma. |
| Revivir | Reloj (PIN) → teléfono → RPC `revive` (verifica el PIN, 1 vez por semana, solo en Game Over). Sin red: se rechaza con un mensaje claro, porque es una acción anti-trampas. |
| Respawn | RespawnWorker → `GameEngine` cierra el día offline → `daily_sessions` pendiente → RPC `close_day` calcula XP, racha y logros en el servidor → Realtime → celebración en el teléfono → reenvío al reloj. |
| Logro / level up | El servidor (RPC) inserta en `user_achievements` o actualiza `profiles` → Realtime → teléfono → `WireEvent` al reloj. |
| Diario de batalla (IA) | Respawn → si `nube_permitida`, invoke `generate-daily-summary` → `ai_insights` → teléfono. Si no hay permiso o el modelo falla, se usa un resumen local de respaldo. |
| Sin cuenta | "Solo en este dispositivo": Room es la única base; el SyncWorker y las funciones de nube quedan desactivados. |

### Decisiones abiertas (las aplico así salvo que digas otra cosa)

1. **El estado del día** (HP actual en tiempo real) vive en Room y se sube agregado. El servidor es la fuente
   de verdad del **resultado** del día (`daily_sessions`), no de cada minuto.
2. **Revivir requiere conexión**, porque es una acción anti-trampas. Pociones y escudos funcionan offline y el
   servidor los valida al sincronizar; si no cuadran, el inventario se corrige desde `item_events`.
3. **Google Sign-In** usa Credential Manager con un ID de cliente de Google Cloud (lo tendrás que crear tú).
4. **El progreso de invitado no pasa a la cuenta** (decidido). Al entrar con una cuenta distinta de la que hay en
   caché (o de invitado a cuenta), el teléfono descarta XP, nivel, racha, logros, objetos e historial y adopta los
   de la cuenta; la cola pendiente se vacía y el uso de días pasados no se sube. Se conservan los monstruos, los
   límites y la vida de hoy (uso real). El perfil local solo se sube si la cuenta es nueva. Antes de entrar se
   muestra un diálogo que dice qué se pierde y qué se conserva (Nielsen nº 5, prevención de errores). Esto también
   evita que el progreso de una cuenta se mezcle con otra en un teléfono compartido.

## Entregable 5: capa de datos Kotlin (hecho)

- `cloud/AuthRepository` (email/contraseña, Google con ID token, estados `Unconfigured/LocalOnly/SignedIn/Expired`).
- `cloud/RemoteGameApi`: PostgREST por columnas permitidas + RPC (`use_item`, `revive`, `close_day`, …).
- `sync/SyncQueue` + `SyncWorker`: cola en Room con claves de fusión y reintentos con backoff exponencial.
- `cloud/CloudBridge`: Realtime en 5 tablas (debounce 800 ms), aviso al reloj si caduca la sesión, adopción de cuenta.
- Uso agregado por hora (`hourly_usage` → `app_usage`, migración 006).
- Prueba de integración contra el proyecto real: `-Dhp.integration=true` (crea y borra un usuario temporal).
