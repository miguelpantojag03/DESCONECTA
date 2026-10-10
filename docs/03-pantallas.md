# Fase 3: pantallas y flujos

Capturas reales generadas con Roborazzi (`./gradlew :wear:recordRoborazziDebug :mobile:recordRoborazziDebug`):
`docs/screenshots/wear/` (23) y `docs/screenshots/phone/` (17).

## Arquitectura en una frase

El **teléfono es la fuente de verdad**: mide el uso, aplica las reglas (`GameEngine`, en `shared`, con 17 pruebas
unitarias) y bloquea apps. El **reloj es el mando**: muestra el estado y envía comandos ("usar poción").

```
Teléfono                                               Reloj
MonitorService ──uso──▶ GameRepository ──DataClient (estado)──▶ WatchListenerService ─▶ WatchGameRepository ─▶ UI
BlockerService ◀─estado─┘     ▲   └────MessageClient (eventos)──▶   (haptics + notificación si la app está cerrada)
RespawnWorker ──medianoche────┘   ◀───MessageClient (comandos)──── WatchViewModel (poción, escudo, límite, revivir)
```

Si el reloj se desconecta, el teléfono sigue aplicando las reglas. El DataItem del estado queda guardado y el reloj
se pone al día al reconectar; mientras tanto muestra "Sin conexión" y la antigüedad de los datos.

---

## Reloj

| # | Pantalla | Decisiones de diseño y principio |
|---|---|---|
| 1 | **HUD** (`01`–`07`) | Arco de 240° en el bisel y número en el centro: la vida es lo primero que se ve (jerarquía). El monstruo se dibuja en la punta de la barra (Gestalt: proximidad causa-efecto). Avatar, nivel y racha van en el hueco inferior. Sin hora del sistema: el arco ocupa ese espacio y aquí "el tiempo" relevante es el que queda. El indicador de página a la derecha avisa que hay más pantallas (affordance). Avisos de texto "Sin conexión" o "Regenerando": nunca solo animación. |
| 2 | **Monstruos** (`09`, `11`) | `ScalingLazyColumn`; orden: el que ataca, luego el de menos margen y los derrotados al final (ley de Hick: lo urgente arriba). Borde rojo y texto "¡Atacando!" (no solo color). |
| 3 | **Detalle** (`10`) | Límite con la corona en pasos de 5 min (precisión sin tapar la pantalla) **y** botones −/+ (alternativa accesible). Debounce de 700 ms: se envía un solo comando al terminar de girar. Gráfica de 7 días con la línea del límite: los excesos se ven en rojo. |
| 4 | **Inventario** (`12`) | Ranuras pixeladas (lenguaje de "juego") y filas con el efecto explicado. Si no hay objetos, se dice cómo ganarlos. |
| 5 | **Confirmación** (`13`, `14`) | Deslizar para confirmar (prevención de errores, Nielsen 5) con acción "Confirmar" para TalkBack. Vista previa del efecto ("84 → 94 HP"). Estados: enviando, éxito y error con reintento en lenguaje humano (Nielsen 9). |
| — | **PIN** (`15`) | Teclado 3×4 de teclas de 48 dp: patrón conocido (ley de Jakob). Los dígitos se ocultan. |
| 6 | **Advertencia** (`16`) | Fondo tintado del estado, corazón que late, monstruo culpable y un único botón. No se cierra sola (WCAG 2.2.1). |
| 7 | **Monstruo derrotado** (`17`) | Sprite en gris con "Zz" flotando: descansa, no castiga. Dice cuándo vuelve. |
| 8 | **Game Over** (`08`) | Avatar K.O., "Es una pausa, no un castigo", cuenta regresiva al respawn, resumen y Revivir en el EdgeButton (zona más alcanzable del reloj redondo, ley de Fitts). Si no está disponible, se explica cuándo lo estará. |
| 9 | **Level up / logro** (`18`, `19`) | Confeti pixelado y escala con rebote suave (refuerzo positivo). Sin confeti con movimiento reducido. |
| 10 | **Perfil** (`20`) | Avatar, título, XP (morada y sin segmentos, para no confundirse con la vida), racha y últimas medallas. |
| 11 | **Jefe semanal** (`21`) | Misma barra de vida en rojo "enemigo": el usuario ya sabe leerla (consistencia). Mensaje de ritmo: "¡Vas bien!" o "Vas por encima". |
| 12 | **Conexión** (`04`, `23`) | Chip "Sin conexión" en el HUD + antigüedad de los datos; pantalla de primer uso sin partida. |
| 13 | **Tile** | Mismo arco de 240° con el hueco abajo. "Poción" abre la confirmación de la app: los objetos siempre se confirman. |
| 14 | **Complicaciones** | HP (valor en rango + texto + ícono) y racha (número + llama), con descripción para TalkBack. |
| — | **Ambient** (`06`) | Solo contornos grises, sin animaciones ni avatar (consumo y quemado de pantalla). |
| — | **Estados** (`22`, `11`, `14`) | Skeleton "8 bits" al cargar, vacío con fantasmita amable, error con reintento. |

## Teléfono

| # | Pantalla | Decisiones de diseño y principio |
|---|---|---|
| 1 | **Intro** (`01`) | 4 pasos con progreso "1 DE 4" y "Saltar" (control y libertad, Nielsen 3). La barra se vacía sola en el paso 1: el concepto se entiende sin leer. Emparejamiento con los estados "buscando" y "conectado", y la opción de continuar sin reloj. |
| — | **Permisos** | Uno por uno, con el porqué en lenguaje del juego ("Para que los monstruos no te ataquen a escondidas…") y "Necesario" u "Opcional". Pedir con contexto aumenta la aceptación y la confianza. |
| 2 | **Crear personaje** (`02`) | Vista previa del avatar en vivo, estilos como opciones de radio, vida en chips con su equivalencia en horas ("= 2 h al día"). PIN opcional con su propósito explicado. |
| 3 | **Monstruos** (`03`, `04`) | Las apps adictivas instaladas llegan **ya marcadas** con nombre y límite sugeridos (ley de Hick + valores por defecto razonables). Contador en vivo "5 monstruos · 0 aliadas". Estados: cargando (skeleton), vacío de búsqueda y error con reintento. |
| 4 | **Panel principal** (`05`, `06`) | La vida arriba y grande (patrón en F); tarjetas por tema (Gestalt: región común). El estado va en su propia línea para que no se parta. Aviso prioritario si faltan permisos: la app nunca falla en silencio. Gráfica semanal con los Game Over en rojo **y** en la descripción accesible. |
| 5 | **Bestiario** (`10`, `11`) | Estadísticas por monstruo (promedio, semana, excesos). Edición con galería de ilustraciones y confirmación antes de quitar (acción destructiva). |
| 6 | **Bloqueo de app** (`07`, `08`) | Monstruo dormido y mensaje amable que nombra la app real. Acción principal "Volver al inicio" abajo y grande (Fitts); la poción es secundaria y tiene 4 estados (lista, sin pociones, reloj desconectado, "¡revisa tu reloj!"). "Atrás" lleva al inicio, nunca a la app bloqueada. |
| 7 | **Game Over** (`09`) | Versión grande y amable con cuenta regresiva, resumen por monstruo y cómo revivir. **Emergencia y Marcador en una barra fija** fuera del scroll: siempre visibles (seguridad). |
| 8 | **Logros** (`12`) | Rejilla; bloqueados en silueta pero **con título visible** (metas claras motivan). Detalle con fecha. |
| 9 | **Historial** (`13`, `14`) | Calendario-mapa de calor con leyenda, calavera en los Game Over (no solo color) y texto negro o blanco según la luminancia de cada casilla (contraste AA). |
| 10 | **Avatar** (`15`) | Vista previa de las 4 expresiones; accesorios bloqueados con candado y el nivel necesario. |
| 11 | **Configuración** (`16`) | Agrupada por intención (vida, apps, seguridad, experiencia, permisos, partida); lo destructivo al final y confirmado. Reducir animaciones: Sistema/Sí/No. |
| — | **Modo estudio** | Bloquea todos los monstruos 25 o 50 min; pantalla propia en color XP (concentración = progreso). |

## Flujos de principio a fin

| Flujo | Cómo funciona |
|---|---|
| Primer uso | Intro → permisos → personaje → monstruos → `createGame` → arranca `MonitorService` + `RespawnWorker`. |
| Uso normal | `MonitorService` (cada 5 s) → `onUsage` → advertencias 50/20/10 %/5 HP (vibración + alerta en el reloj) → monstruo derrotado (`BlockerService` muestra la pantalla de bloqueo) → Game Over (`lockNow` + pantalla en cualquier app). |
| Poción desde el reloj | Inventario → deslizar → `UsePotion` → el teléfono valida y aplica → el estado nuevo llega a ambos dispositivos. |
| Poción desde el bloqueo | Botón en el teléfono → evento `OpenPotion` → el reloj abre la confirmación (o una notificación) → la app se despierta 10 min. |
| Regeneración | Con la pantalla apagada: `regenerating = true` ("Regenerando" en el reloj); +5 HP cada 30 min, máx. 20 al día. |
| Revivir | Game Over → PIN en el reloj → el teléfono comprueba el hash con sal → 15 HP, 1 vez por semana. |
| Respawn | `RespawnWorker` a las 00:01 (y `ensureToday()` si se perdió): XP = HP sobrantes, racha, pociones cada 3 días, escudos cada 7, logros, level up e historial en Room. |
| Logro | `AchievementUnlocked` → Room + alerta con confeti en el reloj + sonido opcional. |
| Desconexión | El teléfono sigue con las reglas; el reloj muestra los últimos datos y "Sin conexión"; al volver, el `DataItem` lo actualiza. |
