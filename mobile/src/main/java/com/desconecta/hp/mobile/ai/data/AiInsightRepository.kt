package com.desconecta.hp.mobile.ai.data

import com.desconecta.hp.mobile.cloud.AuthRepository
import com.desconecta.hp.mobile.cloud.AuthState
import com.desconecta.hp.mobile.data.db.AiInsightEntity
import com.desconecta.hp.mobile.data.db.HpDatabase
import com.desconecta.hp.mobile.sync.SyncOp
import com.desconecta.hp.mobile.sync.SyncQueue
import com.desconecta.hp.shared.ai.model.AiFeedback
import com.desconecta.hp.shared.ai.model.AiInsight
import com.desconecta.hp.shared.ai.model.AiOrigin
import com.desconecta.hp.shared.ai.model.Confidence
import com.desconecta.hp.shared.ai.model.InsightType
import com.desconecta.hp.shared.ai.model.UserResponse
import com.desconecta.hp.shared.datalayer.WireCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultados de la IA: Room es la fuente para la interfaz (funciona sin red) y, con cuenta,
 * cada resultado y cada respuesta del jugador se suben a ai_insights.
 */
@Singleton
class AiInsightRepository @Inject constructor(
    private val db: HpDatabase,
    private val queue: SyncQueue,
    private val auth: AuthRepository,
) {
    private val dao get() = db.aiInsights()
    private val signedIn get() = auth.state.value is AuthState.SignedIn

    fun latest(type: InsightType): Flow<AiInsight?> = dao.latest(type.name).map { it?.toModel() }

    fun pending(type: InsightType): Flow<List<AiInsight>> = dao.pending(type.name).map { list -> list.map { it.toModel() } }

    suspend fun lastAt(type: InsightType): Long = dao.lastAt(type.name) ?: 0L
    suspend fun since(type: InsightType, since: Long): List<AiInsight> = dao.since(type.name, since).map { it.toModel() }
    suspend fun dislikedTemplates(): Set<String> = dao.dislikedKeys().toSet()
    suspend fun recentTemplates(n: Int = 12): Set<String> = dao.recentKeys(n).toSet()

    suspend fun save(insight: AiInsight, templateKey: String? = null) {
        dao.put(insight.toEntity(templateKey))
        if (signedIn) queue.enqueue(SyncOp.InsightInsert(insight.id, insight.toRow()))
    }

    suspend fun respond(id: String, response: UserResponse) {
        val e = dao.get(id) ?: return
        dao.setResponse(id, response.name)
        upload(e.copy(response = response.name))
    }

    /** Tocar el mismo 👍/👎 otra vez lo quita. */
    suspend fun feedback(id: String, value: AiFeedback) {
        val e = dao.get(id) ?: return
        val next = if (e.feedback == value.name) null else value.name
        dao.setFeedback(id, next)
        upload(e.copy(feedback = next))
    }

    suspend fun clear() = dao.clear()

    private suspend fun upload(e: AiInsightEntity) {
        if (!signedIn) return
        queue.enqueue(
            SyncOp.InsightResponse(
                e.id,
                wire(UserResponse.serializer(), UserResponse.valueOf(e.response)),
                e.feedback?.let { wire(AiFeedback.serializer(), AiFeedback.valueOf(it)) },
            ),
        )
    }

    private fun AiInsight.toEntity(templateKey: String?) = AiInsightEntity(
        id = id, type = type.name, content = content, reason = reason, confidence = confidence.name,
        origin = origin.name, createdAt = createdAt, response = response.name, feedback = feedback?.name,
        payload = payload, templateKey = templateKey,
    )

    private fun AiInsightEntity.toModel() = AiInsight(
        id = id,
        type = InsightType.valueOf(type),
        content = content,
        reason = reason,
        confidence = Confidence.valueOf(confidence),
        origin = AiOrigin.valueOf(origin),
        createdAt = createdAt,
        response = UserResponse.valueOf(response),
        feedback = feedback?.let(AiFeedback::valueOf),
        payload = payload,
    )

    /** Fila de ai_insights con los valores en español de los CHECK del servidor. */
    private fun AiInsight.toRow() = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("client_id", JsonPrimitive(id))
        put("tipo", JsonPrimitive(wire(InsightType.serializer(), type)))
        put("contenido", JsonPrimitive(content.take(1000)))
        put("motivo", JsonPrimitive(reason.take(300)))
        put("confianza", JsonPrimitive(wire(Confidence.serializer(), confidence)))
        put("origen", JsonPrimitive(wire(AiOrigin.serializer(), origin)))
        put("respuesta_usuario", JsonPrimitive(wire(UserResponse.serializer(), response)))
        put("feedback", feedback?.let { JsonPrimitive(wire(AiFeedback.serializer(), it)) } ?: JsonNull)
        put("payload", payload?.let { runCatching { WireCodec.json.parseToJsonElement(it) }.getOrNull() } ?: JsonNull)
        put("created_at", JsonPrimitive(Instant.ofEpochMilli(createdAt).toString()))
    }

    private fun <T> wire(serializer: KSerializer<T>, value: T): String =
        (WireCodec.json.encodeToJsonElement(serializer, value) as JsonElement).jsonPrimitive.content
}
