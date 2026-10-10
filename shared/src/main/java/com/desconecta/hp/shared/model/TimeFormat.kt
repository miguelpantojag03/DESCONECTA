package com.desconecta.hp.shared.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Milisegundos hasta la próxima medianoche local (el "respawn"). */
fun millisUntilRespawn(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val midnight = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).plusDays(1).atStartOfDay(zone)
    return midnight.toInstant().toEpochMilli() - now
}

/** "5:20:12" — cuenta regresiva con segundos para que se perciba movimiento. */
fun formatCountdown(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

/** "5 h 20 min" — versión legible (para TalkBack y textos). */
fun formatDuration(millis: Long): String = formatMinutes((millis / 60_000).toInt().coerceAtLeast(0))

/** "hace 5 min" para la última sincronización. */
fun formatAgo(now: Long, then: Long): String {
    val min = ((now - then) / 60_000).coerceAtLeast(0)
    return when {
        then == 0L -> "nunca"
        min < 1 -> "hace un momento"
        min < 60 -> "hace $min min"
        else -> "hace ${min / 60} h"
    }
}
