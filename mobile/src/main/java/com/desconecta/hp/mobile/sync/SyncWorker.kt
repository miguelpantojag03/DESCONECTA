package com.desconecta.hp.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.desconecta.hp.mobile.cloud.CloudBridge
import com.desconecta.hp.mobile.cloud.RemoteGameApi
import com.desconecta.hp.mobile.data.db.HpDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException

/**
 * Sube a Supabase todo lo pendiente, en orden:
 *  1. Uso agregado por hora (hourly_usage "sucio").
 *  2. Cola de operaciones (FIFO): día, objetos, monstruos, cierre de día, jefe, perfil.
 *  3. Descarga lo que decide el servidor (XP, racha, inventario, logros) y lo fusiona.
 *
 * Errores:
 *  · De red o del servidor (5xx, sin conexión) → Result.retry() con backoff exponencial;
 *    la operación queda en la cola y se reintenta en orden.
 *  · De validación (4xx: RLS, CHECK, día ya cerrado) → no se arreglan reintentando: se
 *    registra el motivo y se descarta para no bloquear la cola.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun db(): HpDatabase
        fun api(): RemoteGameApi
        fun queue(): SyncQueue
        fun bridge(): CloudBridge
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val bridge = deps.bridge()
        val queue = deps.queue()
        if (!bridge.isCloudActive()) return Result.success()

        queue.report { it.copy(running = true) }
        return try {
            uploadUsage(deps)
            processQueue(deps)
            bridge.pullAndMerge()
            queue.report { it.copy(running = false, lastSyncAt = System.currentTimeMillis(), error = null) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            queue.report { it.copy(running = false, error = humanError(e)) }
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private suspend fun uploadUsage(deps: Deps) {
        val usage = deps.db().usage()
        val dirty = usage.dirty()
        if (dirty.isEmpty()) return
        // Por días: si el servidor rechaza uno (p. ej. ya cerrado), se descarta ese día y no
        // se bloquea toda la sincronización reintentando para siempre.
        dirty.groupBy { it.dayKey }.forEach { (_, rows) ->
            try {
                deps.api().upsertUsage(rows)
            } catch (e: RestException) {
                if (!isPermanent(e)) throw e
            }
            rows.forEach { usage.markClean(it.dayKey, it.hour, it.packageName, it.minutes) }
        }
    }

    private suspend fun processQueue(deps: Deps) {
        val dao = deps.db().sync()
        val api = deps.api()
        while (true) {
            val batch = dao.next()
            if (batch.isEmpty()) return
            for (entity in batch) {
                val op = deps.queue().decode(entity)
                if (op == null) {
                    dao.delete(entity.id)
                    continue
                }
                try {
                    execute(op, api)
                    dao.delete(entity.id)
                } catch (e: RestException) {
                    if (isPermanent(e)) {
                        // Permanente: el servidor rechazó el dato (trampa, día cerrado…).
                        dao.markFailed(entity.id, e.message.orEmpty().take(200))
                        dao.delete(entity.id)
                    } else {
                        throw e // sesión caducada, límite de peticiones o 5xx: reintentar
                    }
                }
            }
        }
    }

    private suspend fun execute(op: SyncOp, api: RemoteGameApi) {
        when (op) {
            is SyncOp.Session -> api.upsertSession(
                op.dayKey,
                op.hpStart,
                com.desconecta.hp.mobile.cloud.SessionUpdate(
                    hpEnd = op.hpEnd.coerceAtMost(op.hpStart),
                    regen = op.regen.coerceIn(0, 20),
                    potions = op.potions.coerceIn(0, 10),
                    gameOver = op.gameOver,
                    gameOverAt = op.gameOverAt?.takeIf { op.gameOver }?.let { java.time.Instant.ofEpochMilli(it).toString() },
                ),
            )
            is SyncOp.UseItem -> api.useItem(
                type = op.type,
                origin = op.origin,
                dayKey = op.dayKey,
                clientId = op.clientId,
                monsterId = op.packageName?.let { api.monsterId(it) },
            )
            is SyncOp.Monsters -> api.syncMonsters(op.monsters, op.allies.map { it.packageName to it.appLabel })
            is SyncOp.CloseDay -> api.closeDay(op.dayKey)
            is SyncOp.StartWeek -> api.startWeek(op.dayKey)
            is SyncOp.Profile -> api.updateProfile(op.fields)
            is SyncOp.InsightInsert -> api.insertInsight(op.row)
            is SyncOp.InsightResponse -> api.updateInsight(op.id, op.response, op.feedback)
            is SyncOp.AiSettingsOp -> {
                api.updateProfile(kotlinx.serialization.json.buildJsonObject { put("modo_familiar", kotlinx.serialization.json.JsonPrimitive(op.familyMode)) })
                api.updateAiSettings(op.fields)
            }
        }
    }

    /** 4xx de validación (RLS, CHECK, día cerrado): reintentar no lo arregla. 401/408/429 sí. */
    private fun isPermanent(e: RestException) =
        e.statusCode in 400..499 && e.statusCode != 401 && e.statusCode != 408 && e.statusCode != 429

    private fun humanError(e: Exception): String = when (e) {
        is RestException -> if (e.statusCode == 401) "La sesión caducó: vuelve a iniciar sesión" else "El servidor rechazó un cambio"
        else -> "Sin conexión: se reintentará"
    }

    companion object {
        const val NAME_NOW = "hp_sync_now"
        const val NAME_PERIODIC = "hp_sync_periodic"
        private const val MAX_ATTEMPTS = 8
    }
}
