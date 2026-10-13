# Módulo de IA: arquitectura (paso 1)

La IA es un **compañero de aventura, no un vigilante**. Tres reglas de arquitectura derivan de eso:

1. **Nunca es un punto único de falla.** Cada función tiene una implementación por reglas o plantillas que
   siempre responde. El conteo de HP y el bloqueo de apps no dependen de la IA ni de internet.
2. **Local por defecto.** La nube es opcional, está desactivada al inicio y pide un consentimiento separado.
3. **Nunca decide sola.** Todo lo que cambia límites o gasta objetos pasa por una confirmación del jugador y
   por una RPC del servidor (anti-trampas).

## Diagrama

```
┌──────────────────────────────── RELOJ (Wear OS) ─────────────────────────────────┐
│  NO ejecuta IA · NO habla con Supabase · solo muestra y captura                  │
│                                                                                  │
│  HUD: barra + marca de predicción (+1 h) + burbuja del coach (1 línea)           │
│  Oráculo · Asistente de voz · Pausa "berserker" (respiración) · Sugerencia de    │
│  límite · Diario (pergamino) · Jefe semanal · Tile de IA (frase + micrófono)     │
│                                                                                  │
│  Voz: SpeechRecognizer / RecognizerIntent de Wear → TEXTO (el audio no sale)     │
└──────────────▲───────────────────────────────────────────────┬───────────────────┘
               │ DataClient: GameState + AiSnapshot            │ MessageClient (comandos):
               │   (predicción, frase del coach, aprendizaje)  │  VoiceText(texto)
               │ MessageClient (eventos):                      │  InsightResponse(id, aceptar/ajustar/ahora no)
               │   CoachLine · Prediction · CompulsiveAlert ·  │  InsightFeedback(id, +/−)
               │   LimitSuggestion · DailyJournal · NewBoss ·  │  ConfirmAction(intent JSON)
               │   VoiceResult(intent, pide confirmación)      │  BreathingDone(+2 HP)
┌──────────────┴───────────────────────────────────────────────▼───────────────────┐
│                               TELÉFONO (Android)                                 │
│                                                                                  │
│  ┌──────────────────────────── AiEngine (dominio) ───────────────────────────┐   │
│  │ PredictionService      CompulsiveUseDetector   CoachService               │   │
│  │ VoiceCommandService    InsightService          ChallengeService           │   │
│  │ (+ ContextService para la detección de horarios, sección 3.8)             │   │
│  └───────────────┬────────────────────────────────────────────────────────────┘   │
│                  │ cada interfaz tiene 3 implementaciones                         │
│  ┌───────────────▼──────────────────── AiRouter ─────────────────────────────┐   │
│  │ Elige por función, según:  ai_settings (¿activa?) · modo familiar ·       │   │
│  │ modo solo-dispositivo · ¿hay modelo local? · ¿hay red? · ¿nube_permitida? │   │
│  │ · periodo de aprendizaje (< 7 días → estado "aprendiendo")                │   │
│  │                                                                           │   │
│  │  1) Reglas / plantillas   2) En dispositivo            3) Nube            │   │
│  │     (siempre disponible)     · Estadística → LiteRT       (Edge Function  │   │
│  │                                (predicción, berserker)     vía Functions) │   │
│  │                              · Gemini Nano (ML Kit GenAI)                 │   │
│  │                                (coach, intención de voz)                  │   │
│  └───────┬────────────────────────────────────────────────────────┬──────────┘   │
│          │ toda salida de texto pasa por                          │              │
│  ┌───────▼────────────┐  ┌────────────────────┐  ┌────────────────▼───────────┐  │
│  │ OutputValidator    │  │ RateLimiter + caché│  │ ConfirmationGate           │  │
│  │ ≤80 car. reloj,    │  │ coach ≥ 45 min,    │  │ gastar objeto / cambiar    │  │
│  │ lista prohibida,   │  │ nada en descanso,  │  │ límite → confirmación en   │  │
│  │ sin culpa/salud/   │  │ respuestas en      │  │ pantalla → RPC (use_item,  │  │
│  │ cuerpo/comparación │  │ caché por día      │  │ set_limit) → GameEngine    │  │
│  │ → si falla:        │  └────────────────────┘  └────────────────────────────┘  │
│  │   plantilla        │                                                          │
│  └────────────────────┘                                                          │
│                                                                                  │
│  Entradas (solo agregados, nunca contenido): app_usage por hora · HP · racha ·   │
│  horario · calendario (con permiso) · eventos de GameEngine                      │
│                                                                                  │
│  Room (caché): ai_insights · ai_settings · voice_commands · cola "pendiente"     │
│  SyncWorker (existente) ─────────────────────────────────────────────┐           │
│  Realtime (ai_insights, weekly_challenges) ──▶ UI + reenvío al reloj │           │
└──────────────────────────────────────────────────────────────────────┼───────────┘
                         HTTPS (JWT) + RLS                             │
┌──────────────────────────────────────────────────────────────────────▼───────────┐
│                                   SUPABASE                                       │
│  ai_settings (nube_permitida = false por defecto) · ai_insights · voice_commands │
│  (retención 30 días con pg_cron) · weekly_challenges · app_usage · profiles      │
│  RLS: auth.uid() = user_id en todas · CHECK en enums y frecuencia ≥ 45 min       │
│                                                                                  │
│  Edge Functions (Deno/TS):                                                       │
│   generate-daily-summary · generate-weekly-boss · suggest-limits · coach-message │
│   ① verifica JWT  ② nube_permitida y no modo familiar, si no 403 claro           │
│   ③ lee SOLO agregados  ④ prompt de sistema con reglas de tono                   │
│   ⑤ JSON con esquema → validación  ⑥ cuota diaria por usuario                    │
│   ⑦ timeout 8 s → respuesta de respaldo  ⑧ guarda en ai_insights (origen=nube)   │
│                     │                                                            │
└─────────────────────┼────────────────────────────────────────────────────────────┘
                      ▼  clave de API como SECRETO de la función (nunca en la app)
              Modelo de lenguaje en la nube
```

## Dónde se ejecuta cada función

| Función | Reglas (respaldo) | En dispositivo | Nube (opcional) |
|---|---|---|---|
| 3.1 Oráculo | Ritmo de las últimas 2 h, extrapolado | Promedio ponderado por hora y día de la semana (`app_usage`) → luego modelo LiteRT | — |
| 3.2 Berserker | Umbrales fijos (25 min seguidos, 4 aperturas en 10 min, uso nocturno) | Umbrales adaptados a su historial | — (100 % local) |
| 3.3 Límites inteligentes | Uso medio × 0,9, redondeado a 5 min | Mismo cálculo + contexto (fin de semana, estudio) | `suggest-limits` |
| 3.4 Coach | Plantillas (≥ 15 por estado y tono) | Gemini Nano si está disponible | `coach-message` |
| 3.5 Voz | Gramática por palabras clave + números | Gemini Nano → JSON de intención | — (el audio y el texto no salen del teléfono) |
| 3.6 Diario | Plantilla narrativa con los datos del día | Gemini Nano | `generate-daily-summary` |
| 3.7 Jefes | Catálogo predefinido en la base de datos (dificultad adaptativa) | — | `generate-weekly-boss` |
| 3.8 Contexto | Horarios repetidos por día de la semana | Agrupación de patrones horarios (+ calendario con permiso) | — |

## Ajustes respecto a lo implementado

| Tema | Decisión |
|---|---|
| Vinculación del reloj por Wi‑Fi (Supabase) | **Se elimina** (sección 2.1): el reloj solo habla con el teléfono. |
| Protocolo reloj ↔ teléfono | Se amplía `WireEvent` y `WireCommand` con los mensajes de IA del diagrama; el reloj sigue sin reglas propias. |
| `ai_insights` / `ai_settings` | Se usa la definición de este prompt (más completa que la del prompt de Supabase): `origen`, `confianza` baja/media/alta, `feedback` positivo/negativo. |
| Color de la IA `#7CF5D9` | Es parecido al cian de pociones y regeneración (`#4DD8FF`). Para que no se confundan (Gestalt: semejanza), la IA **siempre** va acompañada del ícono de cristal mágico y nunca rellena barras de HP. En el paso 4 mediré su contraste. |
| Modelo en la nube | Configurable como secreto de la función. Propuesta: Claude Haiku para el coach (rápido, cabe en los 8 s) y Claude Sonnet para el diario, el jefe y los límites. |
| Gemini Nano | Solo existe en algunos teléfonos (no en el emulador). El `AiRouter` lo detecta y, si no está, usa las plantillas: la app se ve igual de completa. |

## Paso 2: estructura de carpetas e interfaces

```
shared/src/main/java/com/desconecta/hp/shared/ai/        ← Kotlin puro: lo comparten teléfono y reloj
├── model/AiModels.kt      vocabulario común (mismos valores que los CHECK de Supabase):
│                          AiFeature · InsightType · Confidence ("Quizá / Probablemente / Casi seguro")
│                          AiOrigin · UserResponse · AiFeedback · CoachTone · AiSettings · AiInsight
│                          HpPrediction · CompulsiveSignal · LimitSuggestion · CoachLine
│                          VoiceIntent (5 intenciones + desconocida) · BattleJournal · BossProposal
│                          ContextSuggestion
├── engine/AiEngine.kt     AiResult<T>: los 9 estados (Ready, Learning, Thinking, Failed + respaldo,
│                          Offline, CloudNotAllowed, Disabled, MissingPermission; baja confianza = Ready)
│                          AiInput (solo agregados) · interfaces: PredictionService,
│                          CompulsiveUseDetector, CoachService, VoiceCommandService,
│                          InsightService, ChallengeService, ContextService
├── engine/AiRouter.kt     función pura que elige reglas, dispositivo o nube
└── safety/AiSafety.kt     OutputValidator (80 car., culpa, salud mental, cuerpo, comparaciones,
                           enlaces) · CoachThrottle (≥ 45 min, nada en descanso ni en Game Over)

mobile/src/main/java/com/desconecta/hp/mobile/ai/        ← implementaciones (fases del paso 7)
├── rules/                 respaldo siempre disponible: RulesPrediction, TemplateCoach (≥ 15 frases por
│                          estado y tono), KeywordVoiceParser, RulesInsights, BossCatalogChallenge…
├── device/                StatisticalPredictor → LiteRtPredictor · GeminiNanoCoach/Voice/Journal
│                          (ML Kit GenAI) · HabitContextService
├── cloud/                 EdgeFunctionCoach, EdgeFunctionInsights, EdgeFunctionChallenge (supabase functions)
├── AiEngineImpl.kt        aplica AiRouter + respaldo + validador + caché/limitador
├── data/                  Room (ai_insights, ai_settings, voice_commands) + sync con Supabase
└── di/AiModule.kt         Hilt

wear/src/main/java/com/desconecta/hp/wear/ui/ai/          ← solo interfaz: burbuja del coach, oráculo,
                                                           voz, pausa berserker, sugerencias, diario,
                                                           jefe y Tile de IA
```

### Por qué así

- **Interfaces en `shared` y sin Android:** el `AiRouter` y el validador se prueban con tablas de casos en la JVM
  (20 pruebas ya escritas). El reloj usa los mismos modelos para mostrar resultados sin ejecutar IA.
- **`AiResult` con los 9 estados:** el tipo obliga a que cada pantalla diseñe el caso de "aprendiendo",
  "sin conexión", "nube no autorizada", etc. Si no los cubre, no compila (Nielsen nº 1: visibilidad del estado).
- **Funciones que nunca van a la nube:** voz, berserker, oráculo y contexto. Son las que tocan datos más
  personales o necesitan responder en menos de 2 s.

## Plan unificado (los dos prompts)

| # | Entregable | Origen |
|---|---|---|
| 1 | Arquitectura general + arquitectura de IA | Supabase 1 + IA 1 ✅ |
| 2 | Estructura de carpetas e interfaces de `AiEngine` | IA 2 ✅ |
| 3 | Migraciones SQL completas (juego + IA), RLS, triggers, RPC, retención y datos seed | Supabase 2–3 + IA 3 |
| 4 | Tokens visuales de IA | IA 4 |
| 5 | Capa de datos Kotlin: cliente Supabase con Hilt, Room, repositorios offline-first, SyncWorker, Realtime; se quita el vínculo Wi‑Fi | Supabase 4 |
| 6 | Pantallas de autenticación + consentimiento + exportar y borrar cuenta | Supabase 5 + 10 |
| 7 | Funciones de IA por fases: Oráculo → coach → voz → berserker → diario → jefes → límites → contexto (con pantallas, previews de los 9 estados y justificación) | IA 5, 7, 8 |
| 8 | Edge Functions con prompts de sistema, validación JSON y respaldo | Supabase 6 + IA 6 |
| 9 | Pruebas: RLS, validador del coach, respaldo de la IA, modo sin conexión | Supabase 7 + IA 9 |
