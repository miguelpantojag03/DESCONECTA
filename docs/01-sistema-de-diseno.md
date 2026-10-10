# HP: Health Points — Fase 1: Sistema de diseño

## 1. Estructura de módulos

```
Desconecta/
├── settings.gradle.kts            :shared, :wear, :mobile
├── gradle/libs.versions.toml      versiones centralizadas
├── docs/                          justificación de diseño (este archivo)
│
├── shared/                        biblioteca Android: lo que comparten reloj y teléfono
│   └── src/main/
│       ├── res/font/              Press Start 2P, Silkscreen (Regular/Bold), Inter variable (OFL)
│       ├── res/values/strings.xml estados de vida y textos de TalkBack
│       └── java/com/desconecta/hp/shared/
│           ├── model/             dominio puro: Health, HealthStatus, HpWarning
│           │                      (fases sig.: Monster, Item, Inventory, Player, Achievement, GameState)
│           ├── datalayer/         rutas de la Wearable Data Layer API
│           └── designsystem/
│               ├── color/         HpPalette (hex crudos) → HpColors (roles) → HpColorSchemes
│               ├── type/          HpFonts, HpTypography, HpTypeScale.Watch / .Phone
│               ├── shape/         HpShapes, PixelCornerShape
│               ├── spacing/       HpSpacing (retícula de 4 dp), WatchInsets, HealthBarDimens
│               ├── motion/        HpDurations, HpEasings, HpMotion (movimiento reducido)
│               ├── haptics/       HpHapticEvent, HpHaptics (patrones), HpHapticPlayer
│               ├── icon/          PixelIcons (sprites 16×16 generados en código)
│               ├── theme/         HpTheme, objeto Hp, HealthStatusVisual
│               ├── catalog/       DesignSystemCatalog (hoja de estilo viva con @Preview)
│               └── component/     (fase 2) HealthBar, MonsterCard, ItemSlot, XpBar,
│                                  AchievementBadge, Avatar
│
├── wear/                          app de Wear OS (minSdk 30)
│   └── java/com/desconecta/hp/wear/
│       ├── ui/theme/WearHpTheme   tokens HP → Wear Material 3
│       ├── ui/hud, ui/monsters, ui/inventory, ui/gameover, ui/profile…   (fase 3)
│       ├── tile/  complication/   (fase 3)
│       └── data/                  cliente de Data Layer, repositorio local (fase 3)
│
└── mobile/                        app de teléfono (minSdk 26)
    └── java/com/desconecta/hp/mobile/
        ├── ui/theme/MobileHpTheme tokens HP → Material 3
        ├── ui/onboarding, ui/home, ui/bestiary, ui/block, ui/gameover… (fase 3)
        ├── data/                  Room, DataStore, UsageStats, Data Layer (fase 3)
        ├── domain/                casos de uso: aplicar daño, usar objeto, respawn… (fase 3)
        └── service/               servicio en primer plano, AccessibilityService,
                                   DeviceAdmin, workers de WorkManager (fase 3)
```

**Por qué el sistema de diseño vive en `shared` y sin Material:** reloj y teléfono usan
bibliotecas de Material distintas (Wear M3 y M3). Si los tokens dependieran de una de ellas
no se podrían reutilizar. `HpTheme` solo depende de Compose base; cada app lo "traduce"
a su MaterialTheme (`WearHpTheme`, `MobileHpTheme`). Resultado: **una sola fuente de
verdad** para color, tipo, forma, movimiento y haptics (consistencia: heurística nº 4 de Nielsen).

**Por qué el teléfono es la fuente de verdad del estado:** es quien mide el uso y bloquea
apps. El reloj solo *envía comandos* ("usar poción") y *muestra* el estado. Así, si el
reloj se desconecta, las reglas se siguen aplicando.

## 2. Color

Tres capas: **paleta** (hex crudos, solo en `HpPalette`) → **roles semánticos**
(`hpCritical`, `textSecondary`…) → **esquemas** (reloj, teléfono, alto contraste, ambient).
Las pantallas solo conocen roles, así que el modo alto contraste o ambient se aplica sin
tocar ninguna pantalla.

### Contraste WCAG 2.1 (medido)

| Color | sobre #000000 | sobre #15151C | sobre #22222D |
|---|---|---|---|
| Texto principal #F5F5F7 | 19.3 | 16.7 | 14.5 |
| Texto secundario #9A9AA8 | 7.6 | 6.5 | 5.7 |
| Saludable #3DDC84 | 11.8 | 10.2 | 8.8 |
| Herido #FFC93C | 13.7 | 11.8 | 10.2 |
| Crítico #FF4D5E | 6.5 | 5.6 | 4.9 |
| Regeneración #4DD8FF | 12.6 | 10.9 | 9.4 |
| XP #B57BFF | 7.2 | 6.3 | 5.4 |
| Oro #FFD166 | 14.6 | 12.6 | 10.9 |

Todas las combinaciones superan **AA (4.5:1)**. Texto negro sobre cualquier acento
(botones) también pasa: el más bajo es negro sobre rojo, 6.5:1.

Decisiones:
- **Reloj en negro puro (#000):** en OLED un píxel negro está apagado → batería. Además
  el bisel desaparece y la barra en arco parece "flotar".
- **Teléfono en #0B0B10, no negro puro:** en pantallas grandes el negro absoluto produce
  *black smearing* al hacer scroll y las tarjetas (#15151C) perderían su sensación de elevación.
- **Verde/amarillo/rojo:** convención universal de semáforo y de los videojuegos
  (modelo mental existente → heurística nº 2, coincidencia con el mundo real).
- **El color nunca va solo** (WCAG 1.4.1, daltonismo rojo-verde ≈ 8 % de los hombres):
  cada estado tiene un **ícono distinto** (corazón lleno / medio / agrietado / calavera),
  **texto** ("Saludable", "Herido"…) y **patrón háptico** propio — ver `HealthStatusVisual`.
- **Alto contraste:** el texto secundario sube a #C9C9D3, contornos blancos y pista de la
  barra más visible.
- **Ambient Mode:** solo grises sobre negro, sin rellenos, para no superar el ~15 % de
  píxeles encendidos y evitar quemado de pantalla.

## 3. Tipografía

| Familia | Rol | Por qué |
|---|---|---|
| **Press Start 2P** | número de HP, "GAME OVER", "LEVEL UP!" | máxima carga "videojuego"; solo en textos de 1–9 caracteres porque es muy ancha |
| **Silkscreen** | etiquetas cortas: "NV 12", "x3", "/ 120 HP" | pixel pero más estrecha y legible a 14 sp |
| **Inter** | todo lo que se lee: frases, botones, tiempos | diseñada para pantalla, x-height alta; cifras tabulares (`tnum`) para que "1 h 24 min" no salte al cambiar |

Separar "fuente de juego" y "fuente de lectura" da la estética retro **sin sacrificar
legibilidad** (estética y diseño minimalista, heurística nº 8).

Escala del reloj: mínimo 14 sp en cualquier texto; el HP es lo más grande (40 sp,
3 dígitos ≈ 120 dp, cabe dentro del arco). Jerarquía: **cuánta vida tengo** → cuánto tiempo
es → en qué estado estoy.

## 4. Forma y espaciado

- **Dos lenguajes de forma con significado:** redondeado = interfaz (tarjetas, botones
  píldora); esquinas pixeladas (`PixelCornerShape`) = mecánica de juego (barras, ranuras de
  inventario, medallas). El usuario distingue de un vistazo qué es "juego" y qué es
  "configuración" (Gestalt: semejanza).
- **Retícula de 4 dp:** ritmo consistente; lo que está a 4–8 dp se lee como un grupo y lo
  que está a 16–24 dp como grupos separados (Gestalt: proximidad).
- **48 dp de área táctil mínima, también en el reloj** (ley de Fitts: objetivos grandes y
  cercanos al pulgar se alcanzan más rápido y con menos errores).
- **Márgenes del reloj en porcentaje** del diámetro, porque los relojes van de ~192 a ~240 dp.

## 5. Movimiento

| Token | Duración | Uso |
|---|---|---|
| MICRO | 150 ms | pulsaciones, toggles |
| TRANSITION | 300 ms | navegación, cambio de color de la barra |
| CELEBRATION | 650 ms | poción, regeneración |
| CELEBRATION_LONG | 800 ms | level up, logro |
| GHOST_DELAY + GHOST_SHRINK | 500 + 300 ms | barra fantasma de daño |
| HEARTBEAT_PERIOD | 1000 ms | latido en crítico (≈ 60 lpm, ritmo de reposo: alerta sin agobio) |

- **Drenaje lineal:** el tiempo pasa a ritmo constante; un easing "mentiría" sobre la
  velocidad real del consumo.
- **Rebote suave (easeOutBack) para recompensas:** comunica alegría sin exagerar.
- **Barra fantasma:** el jugador *ve cuánto acaba de perder* antes de que desaparezca
  (visibilidad del estado del sistema, heurística nº 1). Patrón conocido de juegos de pelea.
- **Reducir animaciones:** se lee `ANIMATOR_DURATION_SCALE` del sistema (y una opción
  propia en Configuración). Se apagan sacudida, destello, partículas, latido y confeti;
  se conservan los cambios de valor porque transmiten información.
- **Ambient:** cero animaciones.

## 6. Haptics (reloj)

| Evento | Patrón | Razón |
|---|---|---|
| Daño normal | ninguno | ocurre cada pocos segundos; vibrar sería ruido |
| Aviso 50 % / 20 % | 2 pulsos cortos | notable pero no alarmante |
| Aviso 10 % / 5 HP | 3 pulsos | escalada: más severidad = más pulsos |
| Crítico | latido doble suave, cada minuto | sincronizado con la animación de latido |
| Monstruo derrotado | golpe firme | "cierre" de un evento |
| Game Over | larga y que se desvanece | un "apagarse", no una alarma: pausa, no castigo |
| Level up / logro | 3 pulsos ascendentes | la subida de intensidad se siente como celebración |
| Curación | 3 ticks suaves ascendentes | llenado |

Cada patrón tiene versión con **primitivas** (más nítidas en relojes con motor LRA) y una
forma de onda de respaldo. Así el jugador sabe qué pasó **sin mirar** el reloj.

## 7. Iconografía

Sprites pixel 16×16 definidos como mapas de caracteres en `PixelIcons.kt`. Se tiñen con
cualquier token (el mismo corazón sirve en verde, amarillo, rojo y gris de ambient) y la
versión en contorno para Ambient se genera automáticamente. Íconos de sistema (ajustes,
atrás) en Material Symbols Rounded, para que lo "de sistema" se reconozca como tal.

## 8. Cómo verlo

- **Teléfono:** `shared/.../catalog/DesignSystemCatalog.kt` → previews "Catálogo · teléfono"
  y "Catálogo · alto contraste". La app del teléfono abre ese catálogo.
- **Reloj:** `wear/.../MainActivity.kt` → previews en reloj redondo pequeño, redondo
  grande, cuadrado, escalas de fuente, crítico y ambient.
