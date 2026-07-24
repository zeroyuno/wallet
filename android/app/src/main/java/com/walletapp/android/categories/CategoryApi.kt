package com.walletapp.android.categories

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
enum class CategoryType { INCOME, EXPENSE }

fun CategoryType.displayLabel(): String = when (this) {
    CategoryType.INCOME -> "Ingreso"
    CategoryType.EXPENSE -> "Gasto"
}

// Las categorías son definidas libremente por el usuario (sin campo de ícono en el backend) — se
// infiere un emoji por palabra clave en el nombre, con el mismo criterio de fallback a
// texto/Unicode ya usado para AccountType.emoji() (research.md #4 de la feature 008).
private val CATEGORY_EMOJI_KEYWORDS = listOf(
    listOf("compra", "shopping") to "🛒",
    listOf("comida", "restaurant", "super", "mercado", "almuerzo", "cena") to "🍴",
    listOf("transporte", "auto", "combustible", "nafta", "uber", "taxi", "colectivo") to "🚗",
    listOf("vivienda", "casa", "alquiler", "renta", "hogar") to "🏠",
    listOf("salud", "farmacia", "medic", "doctor") to "💊",
    listOf("entretenimiento", "ocio", "cine", "streaming", "juego") to "🎮",
    listOf("servicio", "luz", "agua", "gas", "internet", "telefono", "celular") to "🧾",
    listOf("ropa", "vestimenta", "indumentaria") to "👕",
    listOf("educacion", "colegio", "universidad", "curso", "estudio") to "📚",
    listOf("mascota", "veterinario", "perro", "gato") to "🐾",
    listOf("regalo") to "🎁",
    listOf("viaje", "vacacion") to "✈️",
    listOf("sueldo", "salario", "nomina", "trabajo") to "💰",
    listOf("ahorro", "inversion") to "🏦",
    listOf("freelance", "extra", "venta") to "💼"
)

fun CategoryResponse.emoji(): String {
    val normalized = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
    CATEGORY_EMOJI_KEYWORDS.forEach { (keywords, emoji) ->
        if (keywords.any { normalized.contains(it) }) return emoji
    }
    return if (type == CategoryType.INCOME) "💵" else "🏷️"
}

@Serializable
data class CategoryRequest(val name: String, val type: CategoryType, val parentCategoryId: String? = null)

@Serializable
data class CategoryResponse(val id: String, val name: String, val type: CategoryType, val parentCategoryId: String? = null)

interface CategoryApi {

    @GET("api/categories")
    suspend fun list(): List<CategoryResponse>

    @POST("api/categories")
    suspend fun create(@Body request: CategoryRequest): CategoryResponse

    @PUT("api/categories/{id}")
    suspend fun update(@Path("id") id: String, @Body request: CategoryRequest): CategoryResponse

    @DELETE("api/categories/{id}")
    suspend fun delete(@Path("id") id: String)
}
