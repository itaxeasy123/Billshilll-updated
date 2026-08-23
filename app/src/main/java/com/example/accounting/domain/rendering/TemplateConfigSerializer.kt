package com.example.accounting.domain.rendering

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * (De)serializes [TemplateVisualConfig] to/from `DocumentTemplateEntity.configJson` - the whole
 * layout/typography/colors config is read/written as one JSON blob (never queried column-by-column
 * at the SQL level), the same "structured value object as a JSON string column" convention this
 * project already uses for `OutboxSyncEntity.payloadJson`. Uses the same `Moshi.Builder() +
 * KotlinJsonAdapterFactory` construction as `SyncEventSerializer`.
 */
object TemplateConfigSerializer {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(TemplateVisualConfig::class.java)

    fun toJson(config: TemplateVisualConfig): String = adapter.toJson(config)

    fun fromJson(json: String): TemplateVisualConfig =
        try {
            adapter.fromJson(json) ?: TemplateVisualConfig()
        } catch (e: Exception) {
            TemplateVisualConfig()
        }
}
