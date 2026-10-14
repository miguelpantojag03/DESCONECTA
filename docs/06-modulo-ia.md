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

## Paso 4: tokens visuales de la IA

Código: `shared/.../designsystem/component/ai/` (`AiComponents.kt`, `AiStateView.kt`, `AiShowcase.kt`).
Capturas: `docs/screenshots/phone/18_ia_tokens.png` y `docs/screenshots/wear/24–26_ia_tokens*.png`.

| Token | Valor | Por qué |
|---|---|---|
| Color `ai` | #7CF5D9 (contenedor #17332E) | Contraste AAA: 15,9:1 sobre negro, 11,9:1 sobre superficie elevada, 12,5:1 para el texto sobre su contenedor. |
| Distancia a otros acentos | ΔE ≈ 37 frente al cian de regeneración y al verde de vida | Se distingue, pero es de la misma familia de tonos. Para alguien con daltonismo verde-rojo podría parecerse al verde, así que **el color nunca va solo**: el cristal es obligatorio. La IA nunca rellena barras de HP, porque opina y no es un dato del juego. |
| Ícono | Bola de cristal pixelada 16×16 (`PixelIcons.Crystal`) | Marca **todo** contenido generado (People + AI: identificar la IA). Misma familia pixel que el resto de íconos de juego. |
| Burbuja | Esquinas pixeladas (2 escalones), borde de 2 dp en `ai`, fondo `aiContainer`, cola hacia el avatar | Es el compañero quien habla; la cola une el mensaje con quien lo dice (Gestalt: conexión). La cola va arriba en el reloj (avatar encima) y al lado en el teléfono (avatar a la izquierda). |
| Burbuja en el reloj | 1 línea, toca para expandir (hasta 4) | Sección 6.1. No compite con la barra de vida (jerarquía) y respeta los 80 caracteres. |
| "Pensando" | Cristal + 3 puntos que se encienden por pasos (300 ms) | Nunca un spinner genérico. Con movimiento reducido, los puntos quedan fijos y el texto "Pensando" basta. |
| Confianza | Palabras + 3 barritas: "Quizá · poca certeza" (en ámbar), "Probablemente", "Casi seguro" | Nunca porcentajes crudos (People + AI). Con certeza baja se **dice** claramente. |
| ¿Por qué? | Botón de 48 dp que muestra el motivo y el origen (reglas, teléfono o nube) | Explicabilidad (People + AI); área táctil mínima (ley de Fitts). |
| 👍 / 👎 | 48 dp, el elegido resaltado | Retroalimentación que se guarda en `ai_insights.feedback`. |
| Aprendizaje | "La IA está conociéndote: día 3 de 7" + 7 segmentos | Fija expectativas en vez de mostrar predicciones poco fiables (People + AI: *set expectations*). |
| Estados sin resultado | Tarjeta con ícono, título, explicación y acción opcional | Los 9 estados de la sección 9: ninguno queda en blanco y todos dicen qué hacer (Nielsen nº 1 y nº 9). |
| Accesibilidad | Todo se anuncia como "Sugerencia de la IA: …" | Quien usa TalkBack también distingue la IA de los datos reales. |
| Ambient | El acento de la IA pasa a blanco y su contenedor a negro | Se mantienen las reglas de bajo consumo. |

## Plan unificado (los dos prompts)

| # | Entregable | Origen |
|---|---|---|
| 1 | Arquitectura general + arquitectura de IA | Supabase 1 + IA 1 ✅ |
| 2 | Estructura de carpetas e interfaces de `AiEngine` | IA 2 ✅ |
| 3 | Migraciones SQL completas (juego + IA), RLS, triggers, RPC, retención y datos seed | Supabase 2–3 + IA 3 ✅ |
| 4 | Tokens visuales de IA | IA 4 ✅ |
| 5 | Capa de datos Kotlin: cliente Supabase con Hilt, Room, repositorios offline-first, SyncWorker, Realtime; se quita el vínculo Wi‑Fi | Supabase 4 |
| 6 | Pantallas de autenticación + consentimiento + exportar y borrar cuenta | Supabase 5 + 10 |
| 7 | Funciones de IA por fases: Oráculo → coach → voz → berserker → diario → jefes → límites → contexto (con pantallas, previews de los 9 estados y justificación) | IA 5, 7, 8 |
| 8 | Edge Functions con prompts de sistema, validación JSON y respaldo | Supabase 6 + IA 6 |
| 9 | Pruebas: RLS, validador del coach, respaldo de la IA, modo sin conexión | Supabase 7 + IA 9 |

## Paso 7 · Fase 1: ajustes de la IA (hecho)

Pantalla **Compañero IA** (Configuración → Inteligencia artificial → Ajustes de la IA). Capturas `26`–`28`.

| Elemento | Decisión | Justificación |
|---|---|---|
| Dónde piensa la IA | "Solo en tu teléfono" marcado por defecto; "También en la nube" abre un consentimiento propio (qué se envía, qué nunca, qué funciones lo usan, revocable). | Local por defecto; People + AI (control y confianza); consentimiento separado del de la cuenta. |
| Opciones no disponibles | Se muestran deshabilitadas con el motivo ("Necesitas una cuenta", "Desactivada en modo familiar") y, si hay, la acción para habilitarlas. | Nielsen nº 1 y nº 10: no se esconde qué existe ni por qué no se puede usar. |
| Funciones | 8 interruptores con una línea de qué hace cada una; las que nunca usan la nube llevan "Siempre en tu teléfono". | Privacidad visible; reconocer mejor que recordar (nº 6). |
| Coach | 4 tonos con frase de ejemplo en la burbuja real; frecuencia 45 min / 1 h / 1 h 30 / 2 h (nunca menos de 45). | Vista previa del resultado (nº 1); la IA no se convierte en otra distracción. |
| Aprendizaje | "La IA está conociéndote: día N de 7" mientras no haya 7 días con datos. | People + AI: fijar expectativas. |
| Modo familiar | Apaga la nube en el mismo cambio; quitarlo pide el PIN. El servidor también lo impone (trigger). | Protección de menores; defensa en profundidad. |
| Borrar mis datos de IA | RPC `delete_my_ai_data` + reinicio del aprendizaje; confirma qué se borra y qué no. | Control del usuario; prevención de errores (nº 5). |

Datos: Room `ai_settings` (migración real 2→3, sin perder historial ni cola) ↔ Supabase `ai_settings`.
Conflictos: gana el `updated_at` más reciente; el modo familiar siempre viene del perfil del servidor.
La operación de sincronización aplica primero el modo familiar y luego los ajustes, así el servidor nunca
recibe "nube permitida" con el modo familiar aún activo. Probado contra el proyecto real.

## Paso 7 · Fase 2: coach y límites inteligentes (hecho)

Capturas `29_panel_coach.png` y `30_ia_sugerencia_limite.png`.

**Coach** (`shared/.../ai/rules/CoachRules.kt`)
- 5 situaciones (atacado, vida baja, regenerando, buen día, mañana) × 4 tonos × 15 frases = **300 plantillas**.
  Una prueba pasa las 300 por el validador con el nombre de monstruo más largo posible.
- Solo habla si hay algo útil que decir; como mucho una vez cada N min (≥ 45) y **nunca** en Game Over,
  en modo estudio ni de 23:00 a 07:00.
- No repite las últimas 12 frases y **no vuelve a usar una frase que recibió 👎** (el feedback cambia el
  comportamiento, no es decorativo; People + AI).
- Nombres largos: nunca se cortan a medias; si la frase no cabe en 80 caracteres se usa el nombre de la app.
- En el panel aparece justo debajo de la vida, con el avatar "hablando" (cola de la burbuja hacia él),
  "¿Por qué?" con el dato concreto, certeza en palabras y 👍/👎.

**Límites inteligentes** (`LimitRules.kt`)
- Tras 7 días de datos, una vez al día como mucho, hasta 2 propuestas.
- Uso muy por debajo del límite → media × 0,9 redondeada a 5 min. Límite superado 5 de 7 días → uno
  **alcanzable** (entre el límite y la media): un límite imposible solo genera Game Over y abandono.
- Sin uso en toda la semana no se propone nada; tampoco para un monstruo rechazado hace menos de 7 días.
- Certeza según lo regular que es el uso (coeficiente de variación).
- Tarjeta: el cambio se lee de un vistazo ("TikTok: 45 → 35 min al día"), el botón dice qué pasará
  ("Aplicar 35 min"), "Ajustar" abre pasos de 5 min y "Ahora no" pesa lo mismo que ajustar. Nada se aplica
  sin aceptar (principio 3: la IA nunca decide sola).

**Datos**: Room `ai_insights` (migración real 3→4) → Supabase `ai_insights` (insert idempotente por
`client_id`; solo se actualizan respuesta y feedback, como permite el servidor).

## Paso 7 · Fase 3: Oráculo y modo berserker (hecho, versión simple)

A partir de aquí la IA se implementa en su **versión más simple** que demuestre la idea de diseño (es un
proyecto de curso). Captura `31_panel_oraculo_berserker.png`.

- **Oráculo** (`OracleRules.kt`): extrapola el ritmo de daño de hoy (minutos de monstruos desde las 7:00)
  hasta medianoche → "A este ritmo te quedarías sin vida a las 21:40" o "llegas a medianoche con unos 86 HP".
  Va dentro de la tarjeta de vida (proximidad con el dato que predice), en ámbar si prevé Game Over, con
  certeza baja por la mañana y su "¿Por qué?". No aparece con menos de 10 min de uso.
- **Berserker**: 25 min seguidos en el mismo monstruo → notificación ("Avisos de la IA") y tarjeta en el
  panel con dos opciones del mismo tamaño: "Hago una pausa" / "Sigo un rato". Es una invitación, no un
  castigo; no avisa en modo estudio y solo una vez por sesión.

## Paso 7 · Fase 4: diario de batalla (hecho, versión simple)

Captura `32_historial_diario.png`. Al terminar el día (respawn) se escribe con `JournalRules` un resumen
narrativo con los datos reales: resultado, rival más duro, monstruos que se mantuvieron a raya, hora más
intensa y un cierre. Un Game Over se cuenta como "los monstruos ganaron esta vez… hoy es tu revancha",
nunca como un fracaso (sin culpa). Aparece arriba del Historial con 👍/👎 y la marca de origen ("Reglas").
Los **jefes semanales** siguen siendo los del catálogo (decisión de alcance: proyecto de curso).

## Paso 7 · Fase 5: la IA en el reloj (hecho, versión simple)

Capturas `docs/screenshots/wear/27_hud_ia_coach.png` y `28_hud_ia_coach_herido.png`.

- El teléfono calcula y el reloj solo muestra: `GameState.aiLine` (ya sincronizado por la Data Layer) lleva
  la frase del coach durante 30 min tras decirla.
- En el HUD ocupa el lugar de la fila del avatar (una línea con el cristal; tocar la expande). No se coloca
  encima de la vida: probado y tapaba el número de HP (jerarquía: el dato principal no se cubre).
- El **Oráculo se queda en el teléfono**: en la pantalla redonda no cabe sin tapar la vida.
- Sin comandos de voz (decisión de alcance del proyecto de curso).
