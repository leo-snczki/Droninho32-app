package pt.droninho32.app.data.store

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.droninho32.app.data.dto.TelemetryPoint
import java.io.File
import java.util.UUID

/**
 * Um voo gravado localmente (offline), à espera de ser sincronizado com o backend.
 * Guarda tudo o que é preciso para criar o voo + enviar a telemetria em lote.
 */
@JsonClass(generateAdapter = true)
data class PendingFlight(
    val localId: String,
    val droneId: Int?,
    val startedAt: String?,
    val endedAt: String?,
    val status: String,
    val points: List<TelemetryPoint>,
)

/**
 * Persistência **offline-first** dos voos: cada voo é gravado como um ficheiro JSON em
 * `filesDir/pending_flights/`. Quando há Internet + sessão, são enviados para o backend
 * e apagados localmente. Assim nenhum voo se perde por estar ligado ao WiFi do drone
 * (sem Internet) durante o controlo.
 */
class PendingFlightStore(context: Context) {

    private val dir = File(context.filesDir, "pending_flights").apply { mkdirs() }
    private val adapter = Moshi.Builder().build().adapter(PendingFlight::class.java)

    /** Cria um id local único para um novo voo pendente. */
    fun newLocalId(): String = "flight_" + UUID.randomUUID().toString()

    suspend fun save(pf: PendingFlight) = withContext(Dispatchers.IO) {
        File(dir, "${pf.localId}.json").writeText(adapter.toJson(pf))
    }

    suspend fun list(): List<PendingFlight> = withContext(Dispatchers.IO) {
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
            .sortedBy { it.name }
            .mapNotNull { f -> runCatching { adapter.fromJson(f.readText()) }.getOrNull() }
    }

    suspend fun delete(localId: String) = withContext(Dispatchers.IO) {
        File(dir, "$localId.json").delete()
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
    }
}
