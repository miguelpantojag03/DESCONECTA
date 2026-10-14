package com.desconecta.hp.shared.ai.rules

import com.desconecta.hp.shared.ai.engine.AiResult
import com.desconecta.hp.shared.ai.model.AiOrigin
import com.desconecta.hp.shared.ai.model.AiSettings
import com.desconecta.hp.shared.ai.model.CoachLine
import com.desconecta.hp.shared.ai.model.CoachTone
import com.desconecta.hp.shared.ai.model.Confidence
import com.desconecta.hp.shared.ai.safety.CoachThrottle
import com.desconecta.hp.shared.ai.safety.OutputValidator
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState

/** En qué momento está el jugador (decide qué tipo de frase toca). */
enum class CoachSituation { ATTACKED, LOW_HP, REGENERATING, DOING_WELL, MORNING }

/** Frase elegida + clave de plantilla (para no repetir y para aprender de los 👎). */
data class CoachPick(val line: CoachLine, val templateKey: String, val reason: String)

/**
 * Coach por reglas: siempre disponible, sin red y sin modelo. Es el respaldo de Gemini Nano
 * y de la nube, y por ahora la implementación principal.
 *
 *  · Solo habla si hay algo útil que decir (situación clara) y respeta [CoachThrottle].
 *  · Calla en el modo descanso: modo estudio activo u horas de sueño (23:00–07:00).
 *  · No repite las últimas frases y nunca vuelve a usar una que recibió 👎.
 *  · Toda frase pasa por [OutputValidator] (≤ 80 caracteres, sin culpa, sin salud ni cuerpo).
 */
object CoachRules {

    const val QUIET_FROM_HOUR = 23
    const val QUIET_TO_HOUR = 7
    fun isRestTime(state: GameState, now: Long, hour: Int): Boolean =
        state.studyModeActive(now) || hour >= QUIET_FROM_HOUR || hour < QUIET_TO_HOUR

    /** La situación actual, o null si no hay nada que valga la pena decir. */
    fun situation(state: GameState, hour: Int): CoachSituation? {
        if (state.phase == GamePhase.GAME_OVER) return null
        val pct = state.currentHp * 100 / state.maxHp.coerceAtLeast(1)
        return when {
            pct <= 30 -> CoachSituation.LOW_HP
            state.attacker != null -> CoachSituation.ATTACKED
            state.regenerating -> CoachSituation.REGENERATING
            hour < 12 && pct >= 85 -> CoachSituation.MORNING
            hour >= 15 && pct >= 60 -> CoachSituation.DOING_WELL
            else -> null
        }
    }

    /**
     * Elige una frase o devuelve por qué no habla (null). [seed] cambia la elección entre
     * llamadas (p. ej. minutos desde la medianoche) sin depender de un Random global.
     */
    fun pick(
        state: GameState,
        settings: AiSettings,
        now: Long,
        hour: Int,
        lastSpokeAt: Long,
        recentKeys: Set<String>,
        dislikedKeys: Set<String>,
        seed: Int,
    ): CoachPick? {
        if (!CoachThrottle.canSpeak(now, lastSpokeAt, settings, isRestTime(state, now, hour), state.phase == GamePhase.GAME_OVER)) return null
        val situation = situation(state, hour) ?: return null
        val tone = settings.tone
        val all = CoachTemplates.of(situation, tone).mapIndexed { i, t -> "${situation.name}.${tone.name}.$i" to t }
        val fresh = all.filter { (k, _) -> k !in dislikedKeys && k !in recentKeys }
        val pool = fresh.ifEmpty { all.filter { (k, _) -> k !in dislikedKeys } }.ifEmpty { return null }
        val start = Math.floorMod(seed, pool.size)
        // La primera que pase el validador tras sustituir los datos (casi siempre la primera).
        for (i in pool.indices) {
            val (key, template) = pool[(start + i) % pool.size]
            val text = fill(template, state)
            if (OutputValidator.isValid(text)) {
                return CoachPick(CoachLine(text, tone), key, reason(situation, state))
            }
        }
        return null
    }

    /** Como [AiResult] (para las pantallas que pintan los 9 estados). */
    fun asResult(pick: CoachPick): AiResult.Ready<CoachLine> =
        AiResult.Ready(pick.line, AiOrigin.RULES, Confidence.HIGH, pick.reason)

    /**
     * Sustituye los marcadores. Usa el nombre completo del monstruo; si la frase no cabe en
     * 80 caracteres, usa el nombre de la app (más corto). Nunca corta un nombre a medias.
     */
    fun fill(template: String, state: GameState): String {
        val m = state.attacker ?: state.monsters.maxByOrNull { it.usedMinutes }
        fun with(name: String) = template
            .replace("{m}", name.ifBlank { "el monstruo" })
            .replace("{hp}", state.currentHp.toString())
            .replace("{racha}", state.player.streakDays.toString())
        val full = with(m?.name.orEmpty())
        return if (OutputValidator.visibleLength(full) <= OutputValidator.WATCH_MAX || m == null) full else with(m.appLabel)
    }

    /** El "¿Por qué?" en una línea, con el dato que llevó a hablar. */
    fun reason(situation: CoachSituation, state: GameState): String {
        val pct = state.currentHp * 100 / state.maxHp.coerceAtLeast(1)
        return when (situation) {
            CoachSituation.ATTACKED -> "${state.attacker?.name ?: "Un monstruo"} te está quitando vida ahora mismo."
            CoachSituation.LOW_HP -> "Te queda el $pct % de la vida del día."
            CoachSituation.REGENERATING -> "Llevas un rato sin usar el teléfono y estás recuperando vida."
            CoachSituation.DOING_WELL -> "Conservas el $pct % de la vida a estas horas."
            CoachSituation.MORNING -> "Empiezas el día con la vida casi llena."
        }
    }
}

/**
 * Plantillas: 15 por situación y tono (300 en total). Marcadores: {m} monstruo, {hp} vida.
 * Escritas para pasar el [OutputValidator]; una prueba las revisa todas con nombres largos.
 */
object CoachTemplates {

    fun of(situation: CoachSituation, tone: CoachTone): List<String> = table.getValue(situation).getValue(tone)

    val all: List<String> get() = table.values.flatMap { it.values.flatten() }

    private val table: Map<CoachSituation, Map<CoachTone, List<String>>> = mapOf(
        CoachSituation.ATTACKED to mapOf(
            CoachTone.MOTIVATOR to listOf(
                "¡{m} ataca! Tú puedes cerrarla y ganar esta ronda.",
                "Cada minuto fuera de {m} es vida que guardas. ¡Vamos!",
                "Plántale cara a {m}: un respiro ahora vale oro.",
                "¡Tú mandas, no {m}! Sal un momento y recarga.",
                "Esta batalla contra {m} se gana saliendo a tiempo.",
                "¡Ánimo! Dejar a {m} ahora te deja más vida esta noche.",
                "Puedes con {m}. Demuéstralo con una pausa.",
                "{m} quiere tu vida. Guárdala para algo que te guste.",
                "¡Gran momento para vencer a {m}! Cierra y respira.",
                "Sal de {m} y la barra deja de bajar. ¡Tú puedes!",
                "¡Vamos, héroe! {m} no aguanta una buena retirada.",
                "Lo estás haciendo bien. Dale un respiro a tu vida.",
                "¡Cada victoria cuenta! Esta puede ser contra {m}.",
                "Tu vida vale más que otro rato en {m}. ¡A por ello!",
                "¡Hoy {m} no gana! Un descanso y sigues en pie.",
            ),
            CoachTone.FUNNY to listOf(
                "{m} te ha visto. Disimula y cierra la app despacito.",
                "{m} no se cansa nunca. Tú sí. Empate: ciérrala.",
                "{m} come tu vida sin pedir permiso. Qué maleducado.",
                "Si {m} cobrara alquiler, ya te debería minutos.",
                "{m} pide 5 minutos más. Siempre dice lo mismo.",
                "Giro de guion: el héroe cierra {m} y va por un snack.",
                "{m} usa su golpe especial: 'solo uno más'.",
                "Alerta: {m} detectado. Recomiendo huida táctica.",
                "{m} ya tiene suficientes fans. Tómate un descanso.",
                "¿{m} otra vez? Ese monstruo no tiene vida propia.",
                "Tu barra de vida te saluda y pide salir de {m}.",
                "{m} sube de nivel si te quedas. No le ayudes.",
                "Truco secreto contra {m}: el botón de inicio.",
                "{m} dice que no es un monstruo. Sospechoso.",
                "Hora de dejar a {m} hablando solo un rato.",
            ),
            CoachTone.WISE to listOf(
                "El guerrero sabio elige cuándo luchar. Aléjate de {m}.",
                "{m} no se va a ningún sitio. Tu tiempo, sí.",
                "Quien sabe parar a tiempo gana más que quien sigue.",
                "Cada minuto que no das a {m} vuelve a ti.",
                "La calma también es una victoria contra {m}.",
                "No hace falta vencer a {m}; basta con no seguirle.",
                "Mira alrededor un momento. {m} puede esperar.",
                "El tiempo bien guardado se vuelve buenos recuerdos.",
                "Una pausa ahora es un regalo para tu yo de la noche.",
                "{m} quiere prisa. Tú puedes elegir calma.",
                "Las mejores batallas se ganan con una retirada a tiempo.",
                "Respira hondo. Decide tú si sigues con {m}.",
                "Lo que alimentas crece. Hoy, que no sea {m}.",
                "Tu atención es tu tesoro. {m} lo sabe.",
                "Detenerse también es avanzar.",
            ),
            CoachTone.DIRECT to listOf(
                "{m} te está quitando vida. ¿Lo cierras?",
                "Sigues en {m}. Te quedan {hp} HP.",
                "Cada minuto en {m} es 1 HP menos.",
                "¿Necesitas {m} ahora mismo?",
                "Cierra {m} y la barra deja de bajar.",
                "{m} activo. {hp} HP restantes.",
                "Buen momento para salir de {m}.",
                "Si paras ahora, guardas vida para luego.",
                "{m} no se cansa. Decide tú cuándo parar.",
                "¿Lo dejas aquí con {m}?",
                "Quedan {hp} HP. {m} sigue atacando.",
                "Sal de {m} y recupera el control.",
                "Una pausa ahora cuenta. ¿La haces?",
                "{m} lleva un rato contigo. ¿Seguimos?",
                "Te recomiendo cerrar {m} ya.",
            ),
        ),
        CoachSituation.LOW_HP to mapOf(
            CoachTone.MOTIVATOR to listOf(
                "¡Aún quedan {hp} HP! Cuídalos y llegas a medianoche.",
                "Estás en la recta final. ¡Tú puedes aguantar!",
                "Poca vida, mucho corazón. ¡Resiste un poco más!",
                "¡Lo vas a lograr! Guarda esos {hp} HP como un tesoro.",
                "Cada minuto sin pantalla te acerca a la victoria.",
                "¡Último esfuerzo! Mañana empiezas con la vida llena.",
                "Has llegado lejos hoy. ¡No te rindas ahora!",
                "¡Llegar a medianoche con vida es una gran victoria!",
                "Un descanso ahora y llegas al final del día en pie.",
                "¡Héroe en apuros, pero héroe! Cuida tus {hp} HP.",
                "Las mejores remontadas empiezan con una pausa.",
                "¡Vamos! Hoy puedes terminar sin Game Over.",
                "Respira y aguanta: quedan {hp} HP de aventura.",
                "¡Cada HP que guardas ahora es una victoria!",
                "Estás a tiempo de cerrar el día con buen sabor.",
            ),
            CoachTone.FUNNY to listOf(
                "Tu barra va en modo ahorro. Yo también lo haría.",
                "{hp} HP. Tu personaje pide vacaciones urgentes.",
                "Modo supervivencia activado. Los monstruos se frotan las manos.",
                "Quedan {hp} HP. Hasta el Game Over está nervioso.",
                "Tu vida está en las últimas. El sofá te llama.",
                "Si la vida fuera batería, estarías en modo avión.",
                "Los monstruos ya celebran. Arruínales la fiesta.",
                "Aviso del gremio de héroes: toca retirada estratégica.",
                "{hp} HP. Momento perfecto para mirar por la ventana.",
                "Tu avatar sujeta la barra con dos dedos. Ayúdale.",
                "Poca vida. Ideal para descubrir qué hay en la nevera.",
                "El Game Over está cerca. Hazle la cobra.",
                "Hasta los monstruos dicen que ya fue suficiente.",
                "Tu vida pide un descanso y yo la apoyo.",
                "Plan secreto: no tocar el móvil hasta mañana. Épico.",
            ),
            CoachTone.WISE to listOf(
                "Lo poco que queda es lo más valioso. Cuídalo.",
                "El final del día se gana con paciencia.",
                "Cuando queda poco, cada elección cuenta más.",
                "Descansar no es rendirse; es prepararse para mañana.",
                "Hasta la vela más corta puede iluminar la noche.",
                "Guarda tus {hp} HP como quien guarda la última luz.",
                "Mañana el día empieza de nuevo. Hoy, cuida lo que tienes.",
                "La pausa de ahora es la victoria de esta noche.",
                "Quien conoce sus límites, los usa a su favor.",
                "Un momento de calma vale más que mil avisos.",
                "El río no se apura y aun así llega al mar.",
                "Cierra el día con serenidad, no con prisa.",
                "La vida que no gastas hoy también es tuya.",
                "Pequeñas pausas, grandes victorias.",
                "Escucha a tu barra: te pide calma.",
            ),
            CoachTone.DIRECT to listOf(
                "Te quedan {hp} HP. Mejor pausa ya.",
                "Vida baja. Evita a los monstruos hasta mañana.",
                "Con {hp} HP, cualquier rato cuenta.",
                "Riesgo de Game Over. ¿Descansas un rato?",
                "Quedan {hp} HP. Usa solo apps aliadas.",
                "Vida en reserva. Hoy toca parar.",
                "Una poción o una pausa te darían margen.",
                "¿Dejas el móvil hasta mañana?",
                "Poca vida. Desactiva distracciones.",
                "{hp} HP. Aún puedes cerrar el día sin Game Over.",
                "Momento de activar el modo estudio.",
                "Si sigues, el Game Over está cerca.",
                "Guarda los {hp} HP que quedan.",
                "Vida baja: 30 min sin pantalla te dan +5 HP.",
                "Toca proteger la vida que queda.",
            ),
        ),
        CoachSituation.REGENERATING to mapOf(
            CoachTone.MOTIVATOR to listOf(
                "¡Así se hace! Estás recuperando vida.",
                "¡Pausa poderosa! Tu barra vuelve a subir.",
                "¡Bien jugado! Cada media hora fuera suma +5 HP.",
                "Te estás curando. ¡Sigue disfrutando la pausa!",
                "¡Eso es! El descanso también es entrenamiento.",
                "¡Tu vida lo agradece! Sigue así un rato más.",
                "¡Regeneración en marcha! Eres imparable.",
                "¡Gran decisión! La barra se recupera sola.",
                "Disfruta el momento: estás ganando vida.",
                "¡Esto es progreso real! +5 HP en camino.",
                "¡Bravo! Desconectar te hace más fuerte.",
                "Pausa activada, héroe recargando. ¡Genial!",
                "¡Sigue así! Tu yo de esta noche te lo agradecerá.",
                "¡Lo estás bordando! La vida vuelve.",
                "¡Qué buena pausa! Los monstruos no te encuentran.",
            ),
            CoachTone.FUNNY to listOf(
                "Los monstruos te buscan y no te encuentran. Jeje.",
                "Modo ninja: invisible para las apps. Vida subiendo.",
                "Tu avatar se está tomando un té. Se lo ha ganado.",
                "Regenerando... como en los juegos, pero de verdad.",
                "Las notificaciones esperan. Que sufran un poco.",
                "Pausa detectada. El sofá aprueba esta decisión.",
                "Tu barra hace estiramientos y vuelve a crecer.",
                "+5 HP en camino. Ni un hechizo lo haría mejor.",
                "Los monstruos están confundidos: ¿dónde te metiste?",
                "Desconectado y sumando vida. Estrategia de leyenda.",
                "Tu avatar dice: 'esto de descansar me gusta'.",
                "Fuera del móvil también hay gráficos en 4K.",
                "Regeneración activa. Los monstruos rabian.",
                "Ningún monstruo puede contra una buena pausa.",
                "Recargando vida... por favor, no desenchufar.",
            ),
            CoachTone.WISE to listOf(
                "En la quietud, la vida vuelve a llenarse.",
                "Descansar también es avanzar.",
                "El tiempo sin pantalla es tiempo que vuelve a ti.",
                "Como la marea, tu energía regresa si la dejas.",
                "Las raíces crecen en silencio.",
                "Una pausa bien vivida vale más que una hora de scroll.",
                "Lo que dejas descansar, se fortalece.",
                "El presente es el mejor lugar para recuperar fuerzas.",
                "La calma de ahora es la fuerza de luego.",
                "Sin prisa, la vida se recompone.",
                "Hay victorias que se ganan sin hacer nada.",
                "El silencio también nutre.",
                "Cada respiro cuenta.",
                "Quien sabe descansar, sabe ganar.",
                "Deja que el día respire contigo.",
            ),
            CoachTone.DIRECT to listOf(
                "Regenerando: +5 HP cada 30 min sin pantalla.",
                "Buena pausa. La vida está subiendo.",
                "Sigue sin pantalla y sumas más HP.",
                "Vas bien. La regeneración está activa.",
                "Cada 30 min fuera: +5 HP.",
                "Recuperando vida. Sigue así.",
                "Pausa en curso. Vida en aumento.",
                "Tienes {hp} HP y subiendo.",
                "No toques el móvil y ganas vida.",
                "Regeneración activa: hasta 20 HP al día.",
                "Buen momento para seguir desconectado.",
                "La barra sube mientras no uses apps.",
                "Pausa útil: vida recuperándose.",
                "Mantén la pausa y suma otros +5 HP.",
                "Estás ganando vida ahora mismo.",
            ),
        ),
        CoachSituation.DOING_WELL to mapOf(
            CoachTone.MOTIVATOR to listOf(
                "¡Vas genial! Guarda esa vida para esta noche.",
                "¡Día de héroe! Conservas {hp} HP.",
                "¡Qué buen ritmo llevas hoy!",
                "¡Así se juega! Los monstruos no pueden contigo.",
                "¡Vas camino de un día perfecto!",
                "Tu barra luce estupenda. ¡Sigue así!",
                "¡Hoy llevas el equilibrio de maravilla!",
                "¡Gran trabajo! Hoy mandas tú.",
                "¡Victoria a la vista! Mantén el ritmo.",
                "¡Excelente! Tu yo de mañana te lo agradecerá.",
                "¡Brillante! Llevas el día bajo control.",
                "¡Con {hp} HP a estas horas, eres imparable!",
                "¡Qué buena jugada! Sigue así.",
                "¡Hoy toca subir de nivel! Lo estás logrando.",
                "¡Así se cuida la vida! Bien hecho.",
            ),
            CoachTone.FUNNY to listOf(
                "Los monstruos piensan en buscarse otro héroe. Normal.",
                "{hp} HP a estas horas. Los monstruos piden revancha.",
                "Tu avatar está tan bien que quiere hacerse un selfi.",
                "Los monstruos te han puesto en su lista negra.",
                "Hoy eres el jefe final de tus apps.",
                "Tu barra de vida está presumiendo. Déjala.",
                "Los monstruos están de huelga. Tú lo has provocado.",
                "Con esta vida podrías prestar HP. No lo hagas.",
                "Tu móvil se siente ignorado. Perfecto.",
                "Nivel de autocontrol: legendario. Casi da miedo.",
                "Las apps te dicen 'te echamos de menos'. Ignóralas.",
                "Tu avatar baila de alegría. Es raro, pero bonito.",
                "Hoy los monstruos no tienen nada que hacer contigo.",
                "Si esto fuera un examen, sacarías nota.",
                "Vas tan bien que hasta yo me motivo.",
            ),
            CoachTone.WISE to listOf(
                "Un buen día se construye minuto a minuto.",
                "El equilibrio no se encuentra: se practica. Y lo haces.",
                "Lo que hoy cuidas, mañana te sostiene.",
                "La constancia es la magia más poderosa.",
                "Vas en calma y con rumbo. Así se avanza.",
                "Cada decisión de hoy suma a quien quieres ser.",
                "El día te sonríe porque tú lo cuidas.",
                "La vida bien usada no se ve en la barra: se ve en ti.",
                "Sigue así: los buenos hábitos crecen en silencio.",
                "No gana la prisa, gana el rumbo.",
                "Guardar vida es guardar posibilidades.",
                "Hoy eliges bien. Mañana será más fácil.",
                "Quien cuida su tiempo, cuida su historia.",
                "Pequeñas elecciones, gran día.",
                "La mejor batalla es la que no hace falta pelear.",
            ),
            CoachTone.DIRECT to listOf(
                "Llevas {hp} HP. Buen ritmo.",
                "Vas bien. Mantén los límites.",
                "Día bajo control. Sigue así.",
                "Te sobra vida para la noche.",
                "Buen uso hoy. Nada que corregir.",
                "{hp} HP a estas horas: muy bien.",
                "Sin Game Over a la vista.",
                "Mantén este ritmo hasta medianoche.",
                "Hoy vas por delante de los monstruos.",
                "Todo en orden. Continúa.",
                "Buen día. Guarda la vida para luego.",
                "Ritmo correcto. Sin cambios.",
                "Con esta vida, hoy ganarás buena XP.",
                "Si sigues así, cierras el día con mucha vida.",
                "Bien gestionado. Sigue igual.",
            ),
        ),
        CoachSituation.MORNING to mapOf(
            CoachTone.MOTIVATOR to listOf(
                "¡Buenos días! Vida llena y un día por ganar.",
                "¡Nuevo día, nueva aventura! Tienes {hp} HP.",
                "¡Arriba, héroe! Hoy puede ser tu mejor día.",
                "¡Empieza fuerte! Tus monstruos aún duermen.",
                "¡Vida al máximo! A por un día sin Game Over.",
                "¡Hoy vas a brillar! Empieza con calma.",
                "¡Qué buen comienzo! Cuida esa barra llena.",
                "¡A por el día! Tus límites te protegen.",
                "¡Comienza la partida! Tú decides el ritmo.",
                "¡Mañana de campeón! Empieza sin prisas.",
                "¡Hoy es un buen día para subir de nivel!",
                "¡Con la vida llena todo es posible!",
                "¡Despierta, héroe! La aventura empieza ahora.",
                "¡Buen día! Hoy la victoria es tuya.",
                "¡Primer paso del día: empezar sin pantalla!",
            ),
            CoachTone.FUNNY to listOf(
                "Buenos días. Los monstruos aún están en pijama.",
                "Vida llena. Los monstruos se están desperezando.",
                "Empieza el día. Tu móvil ya te pone ojitos.",
                "Consejo matutino: el desayuno no necesita scroll.",
                "Tu avatar ha dormido genial. ¿Y tú?",
                "Barra llena, café en mano. Que tiemblen las apps.",
                "Los monstruos ya calientan. Tú, a lo tuyo.",
                "Nuevo día: los HP están recién horneados.",
                "Mañana tranquila. Las notificaciones pueden esperar.",
                "Hoy toca ser protagonista, no espectador.",
                "Las apps no han desayunado. No las alimentes.",
                "Día nuevo, botón de reinicio pulsado. Bien.",
                "Tu barra está tan llena que brilla.",
                "Modo mañana: menos pantalla, más ventana.",
                "Los monstruos te dan los buenos días. Desconfía.",
            ),
            CoachTone.WISE to listOf(
                "Cada mañana es una página en blanco.",
                "Empieza despacio y llegarás lejos.",
                "El día se decide en sus primeros minutos.",
                "Una mañana tranquila es un gran comienzo.",
                "Hoy tienes toda la vida por delante. Úsala bien.",
                "El sol no mira el móvil antes de salir.",
                "Lo primero del día merece tu atención entera.",
                "Siembra calma por la mañana y la recogerás de noche.",
                "Empieza con intención, no con notificaciones.",
                "Un nuevo día, una nueva oportunidad.",
                "Tu tiempo de hoy es un regalo sin abrir.",
                "Elige bien el primer paso; el resto lo sigue.",
                "La mañana es de quien la vive, no de quien la desliza.",
                "Comienza con calma: el día no se escapa.",
                "Lo que haces al despertar marca el ritmo.",
            ),
            CoachTone.DIRECT to listOf(
                "Buenos días. Tienes {hp} HP.",
                "Día nuevo. Vida al máximo.",
                "Empieza sin redes y gana margen.",
                "Tus límites ya están activos.",
                "Hoy: {hp} HP. Úsalos con cabeza.",
                "Primera hora sin pantalla: buena idea.",
                "Vida completa. Mantén el control desde ya.",
                "Piensa qué apps vas a necesitar hoy.",
                "Arranca el día con un objetivo claro.",
                "Mañana libre de monstruos: recomendable.",
                "Vida llena. Que dure hasta la noche.",
                "Empieza bien: deja el móvil un rato.",
                "Nuevo día, mismos límites. Adelante.",
                "Tienes todo el día. Planifícalo.",
                "Primer reto: 1 hora sin redes.",
            ),
        ),
    )
}
