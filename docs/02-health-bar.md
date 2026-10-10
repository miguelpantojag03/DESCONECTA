# Fase 2: componente HealthBar

Código: `shared/src/main/java/com/desconecta/hp/shared/designsystem/component/healthbar/`

| Archivo | Responsabilidad |
|---|---|
| `HealthBarAnimation.kt` | Orquesta las animaciones y produce un `HealthBarFrame` (una "foto" del instante) |
| `HealthBarDrawing.kt` | Dibujo puro: arco, barra recta, estelas, partículas, ambient, alto contraste |
| `HealthBar.kt` | API pública: `HealthBar`, `HealthBarLayout` (sin estado), `SegmentedBar`, `HealthReadout` |
| `HealthBarPreviews.kt` | Previews de cada estado en reloj redondo, reloj cuadrado y teléfono + playground interactivo |
| `icon/MonsterSprites.kt` | Sprites a color de los monstruos (Scrollzilla, Medusa, Rey Autoplay, Goblin, Fantasma) |

## Arquitectura: animación separada del dibujo

`rememberHealthBarFrame()` convierte el valor real de vida en un `HealthBarFrame`
(barra, estela, color, destello, sacudida, latido, partículas). El dibujo es una función
pura de ese frame. Ventajas:
- Se puede previsualizar **cualquier instante** de una animación (p. ej. la barra fantasma
  a medio encoger) con un `@Preview` estático.
- El mismo motor alimenta el arco, la barra recta y las mini barras de los monstruos.

## Variantes

| Variante | Dónde | Decisión |
|---|---|---|
| Arco 240° | reloj redondo | Sigue el bisel y deja el centro libre para el número. Hueco abajo: el ojo completa el círculo (Gestalt: cierre) y el hueco queda sobre la zona que tapa la muñeca. |
| Recta | reloj cuadrado, teléfono | La forma natural de un rectángulo; en el teléfono el número va a la izquierda y el estado a la derecha (lectura en F). |
| Mini (6 dp) | cada monstruo | Mismos segmentos y colores: el usuario reconoce que "es lo mismo, en pequeño" (consistencia). |

`HealthBarVariant.AUTO` elige sola según `isScreenRound` y el tipo de dispositivo.

## Decisiones de diseño

- **Segmentos de 10 HP (10 min):** la vida se puede *contar* ("me quedan 8 bloques"),
  lo que se entiende antes que un porcentaje. Los segmentos son proporcionales: si la
  vida máxima no es múltiplo de 10, el último segmento es más corto, para que el largo
  dibujado siempre corresponda a los minutos reales.
- **Extremos rectos, sin redondear** (`StrokeCap.Butt`) y un brillo de 1 "píxel" arriba:
  lenguaje de sprite de 16 bits, coherente con las formas pixeladas del sistema.
- **La vida se vacía de derecha a izquierda**, como un indicador de combustible o
  de batería: un modelo mental que ya existe (Nielsen nº 2).
- **El número es exacto, no se anima al recibir daño.** Al curarse sí "cuenta" hacia
  arriba, porque es una recompensa. El dato siempre es fiel; la celebración solo es positiva.
- **El estado se repite tres veces:** color + ícono (corazón lleno / medio / agrietado /
  calavera) + palabra ("HERIDO"). Nunca depende solo del color (WCAG 1.4.1).

## Animaciones y su porqué

| Efecto | Cuándo | Por qué |
|---|---|---|
| Drenaje lineal 300 ms | cada daño | El tiempo pasa a ritmo constante; un easing "mentiría" sobre la velocidad real. |
| Barra fantasma blanca (espera 500 ms, se encoge en 300 ms) | cada daño | El jugador ve *cuánto acaba de perder* (Nielsen nº 1). Si llegan más golpes antes, la fantasma se queda arriba y acumula el "combo", como en los juegos de pelea. |
| Destello + sacudida | ≥ 3 HP de golpe, o monstruo feroz | Los monstruos más adictivos (TikTok, Instagram, YouTube, juegos) hacen "más daño visual": cada minuto que se les da se siente. Los mensajes (Fantasma) no sacuden. |
| Avance cian + rebote suave | poción, regeneración | Simétrico a la barra fantasma: el daño se queda atrás en blanco, la curación va por delante en cian. El rebote (easeOutBack) comunica alegría. |
| Partículas cian que suben | curación | Refuerzo positivo; se mueven en una rejilla de 1 dp para que avancen "a saltos" como sprites. |
| Transición de color 300 ms | cambio de estado | Evita saltos bruscos de verde a amarillo a rojo. |
| Latido 1.0 → 1.04 ("lub-dub" en 1 s) | crítico | Alerta sin agobio: 60 lpm es un ritmo de reposo. Con `heartbeatHaptics` la vibración arranca en el mismo frame y se repite cada 60 latidos exactos, así quedan sincronizadas. |

**Reducir animaciones:** se desactivan destello, sacudida, partículas y latido; la barra
fantasma desaparece al instante. Se conserva el cambio de valor (más corto, 150 ms).
**Ambient:** nada se anima; todo se dibuja en contorno gris (segmentos llenos con borde
brillante, vacíos con borde tenue) y el monstruo se convierte en una silueta.

## El monstruo en la punta

En el arco, el sprite del monstruo que está atacando se dibuja dentro del anillo, justo
en la punta de la vida: está "mordiendo" la barra. Causa (el monstruo) y efecto (la vida
que baja) quedan juntos (Gestalt: proximidad). En la barra recta va a la izquierda, en
una posición fija, porque así se lee primero (patrón en F).

Los monstruos no usan verde, amarillo, rojo ni cian, para que nunca se confundan con
un estado de vida. Comparten anatomía (cuerpo redondo, ojos grandes) para parecer una
familia traviesa, no amenazante: el juego es divertido, no punitivo.

## Accesibilidad

- `clearAndSetSemantics`: TalkBack lee un único anuncio, "Vida: 84 de 120, estado
  Saludable. Te quedan 1 h 24 min de uso. La Medusa del Feed te está haciendo daño".
- `progressBarRangeInfo`: los servicios de accesibilidad lo reconocen como barra de progreso.
- Alto contraste: borde blanco en cada segmento y pista más visible.
- Previews con texto grande (`fontScale = 1.24`) para verificar que nada se corta.
