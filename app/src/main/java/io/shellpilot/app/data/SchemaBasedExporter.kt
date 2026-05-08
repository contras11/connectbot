/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.shellpilot.app.data

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import androidx.room.RoomDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Schema-driven database exporter/importer.
 *
 * Uses the Room schema JSON to generically export and import database tables
 * without any hardcoded entity knowledge. All table names, field names, types,
 * relationships, and excluded fields are read from the schema.
 *
 * Fields marked as "excluded" in the schema are:
 * - Omitted from JSON export
 * - Given default values during import (for NOT NULL fields)
 *
 * @param database The Room database instance
 * @param schema The parsed database schema (filtered export schema with excluded field markers)
 */
class SchemaBasedExporter(
    private val database: RoomDatabase,
    private val schema: DatabaseSchema,
    private val importReferences: List<ImportReference> = emptyList(),
    private val droppedReferences: List<DroppedReference> = emptyList(),
    private val skipRowsWhenParentSkipped: List<ImportReference> = emptyList()
) {

    /**
     * Export specified tables to JSON.
     *
     * @param tableNames List of table names to export
     * @param pretty Whether to format JSON with indentation
     * @return JSON string containing all table data
     */
    fun exportToJson(tableNames: List<String>, pretty: Boolean = true): String {
        val json = JSONObject()
        json.put("version", schema.version)

        val db = database.openHelper.readableDatabase

        for (tableName in tableNames) {
            val entitySchema = schema.getEntity(tableName) ?: continue
            val rows = JSONArray()

            // Build column list excluding fields marked as excluded in schema
            val columns = entitySchema.fields
                .filter { !it.excluded }
                .map { it.columnName }

            val cursor = db.query(
                "SELECT ${columns.joinToString(", ")} FROM $tableName"
            )

            cursor.use {
                while (it.moveToNext()) {
                    val row = cursorToJson(it, entitySchema)
                    rows.put(row)
                }
            }

            json.put(tableName, rows)
        }

        return if (pretty) json.toString(2) else json.toString()
    }

    /**
     * Import data from JSON into database tables.
     *
     * @param jsonString JSON string containing table data
     * @param tableNames List of table names to import (in order - parent tables first)
     * @return Map of table name to Pair of (inserted count, skipped count)
     */
    fun importFromJson(jsonString: String, tableNames: List<String>): Map<String, Pair<Int, Int>> {
        val json = JSONObject(jsonString)
        val version = json.optInt("version", 1)

        if (version > schema.version) {
            throw IllegalArgumentException(
                "Unsupported schema version: $version (max supported: ${schema.version})"
            )
        }

        val db = database.openHelper.writableDatabase
        val results = mutableMapOf<String, Pair<Int, Int>>()

        // Track ID mappings for foreign key remapping: tableName -> (oldId -> newId)
        val idMappings = mutableMapOf<String, MutableMap<Long, Long>>()
        val insertedIdMappings = mutableMapOf<String, MutableMap<Long, Long>>()

        db.beginTransaction()
        try {
            // Process tables in order (parent tables first for foreign key resolution)
            for (tableName in tableNames) {
                val entitySchema = schema.getEntity(tableName) ?: continue
                val rows = json.optJSONArray(tableName) ?: continue
                val idMapping = mutableMapOf<Long, Long>()
                idMappings[tableName] = idMapping

                var insertedCount = 0
                var skippedCount = 0

                // Find unique constraint for conflict detection
                val uniqueFields = entitySchema.uniqueIndices
                    .firstOrNull()
                    ?.columnNames

                // Find foreign keys that need remapping
                val foreignKeys = entitySchema.foreignKeys
                val insertedIdMapping = mutableMapOf<Long, Long>()

                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val oldId = row.optLong("id", 0)

                    if (shouldSkipBecauseParentWasSkipped(tableName, row, insertedIdMappings)) {
                        skippedCount++
                        continue
                    }

                    // Remap foreign key values using previously imported ID mappings
                    val remappedRow = remapForeignKeys(row, foreignKeys, idMappings, entitySchema)
                        .let { applyExplicitReferences(tableName, it, idMappings, postponeSelfRefs = true) }
                        .let { applyDroppedReferences(tableName, it) }

                    // Check for existing row by unique constraint
                    val existingId = uniqueFields?.let {
                        findExistingId(db, tableName, remappedRow, it, entitySchema)
                    }

                    val newId = if (existingId != null) {
                        // Skip existing row - do not update
                        skippedCount++
                        existingId
                    } else {
                        // Insert new row
                        val id = insertRow(db, tableName, remappedRow, entitySchema)
                        insertedCount++
                        insertedIdMapping[oldId] = id
                        id
                    }

                    idMapping[oldId] = newId
                }

                // Second pass: update explicitly configured self-referencing keys.
                updateSelfReferences(db, tableName, entitySchema, idMapping, insertedIdMapping, rows)
                insertedIdMappings[tableName] = insertedIdMapping

                results[tableName] = Pair(insertedCount, skippedCount)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // Notify Room's InvalidationTracker that tables have changed
        // This triggers Flow updates for any observers
        database.invalidationTracker.refreshVersionsAsync()

        return results
    }

    /**
     * Convert a cursor row to JSON using schema field definitions.
     */
    private fun cursorToJson(cursor: Cursor, entitySchema: EntitySchema): JSONObject {
        val json = JSONObject()

        for (field in entitySchema.fields) {
            if (field.excluded) continue

            val columnIndex = cursor.getColumnIndex(field.columnName)
            if (columnIndex < 0 || cursor.isNull(columnIndex)) continue

            val value: Any = when (field.affinity) {
                "INTEGER" -> cursor.getLong(columnIndex)
                "TEXT" -> cursor.getString(columnIndex)
                "REAL" -> cursor.getDouble(columnIndex)
                "BLOB" -> Base64.encodeToString(cursor.getBlob(columnIndex), Base64.NO_WRAP)
                else -> cursor.getString(columnIndex)
            }

            // Use fieldPath (Kotlin property name) as JSON key for consistency
            json.put(field.fieldPath, value)
        }

        return json
    }

    /**
     * Remap foreign key values using ID mappings from previously imported tables.
     */
    private fun remapForeignKeys(
        row: JSONObject,
        foreignKeys: List<ForeignKeySchema>,
        idMappings: Map<String, Map<Long, Long>>,
        entitySchema: EntitySchema
    ): JSONObject {
        val remapped = JSONObject(row.toString())

        for (fk in foreignKeys) {
            val referencedMapping = idMappings[fk.table] ?: continue

            for ((localCol, _) in fk.columns.zip(fk.referencedColumns)) {
                val field = entitySchema.fields.find { it.columnName == localCol } ?: continue
                val fieldPath = field.fieldPath

                if (remapped.has(fieldPath)) {
                    val oldValue = remapped.optLong(fieldPath, 0)
                    val newValue = referencedMapping[oldValue]
                    if (newValue != null) {
                        remapped.put(fieldPath, newValue)
                    } else {
                        // Foreign key references non-imported row, set to null
                        remapped.put(fieldPath, JSONObject.NULL)
                    }
                }
            }
        }

        return remapped
    }

    /**
     * Find existing row ID by unique constraint.
     */
    private fun findExistingId(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        row: JSONObject,
        uniqueFields: List<String>,
        entitySchema: EntitySchema
    ): Long? {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (columnName in uniqueFields) {
            val field = entitySchema.fields.find { it.columnName == columnName } ?: continue
            val value = row.opt(field.fieldPath) ?: continue

            conditions.add("$columnName = ?")
            args.add(value.toString())
        }

        if (conditions.isEmpty()) return null

        val cursor = db.query(
            "SELECT id FROM $tableName WHERE ${conditions.joinToString(" AND ")}",
            args.toTypedArray()
        )

        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    /**
     * Insert a new row into the database.
     */
    private fun insertRow(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        row: JSONObject,
        entitySchema: EntitySchema
    ): Long {
        val values = jsonToContentValues(row, entitySchema, excludeId = true)
        return db.insert(tableName, 0, values)
    }

    /**
     * Update self-referencing foreign keys after all rows are imported.
     */
    private fun updateSelfReferences(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        entitySchema: EntitySchema,
        idMapping: Map<Long, Long>,
        insertedIdMapping: Map<Long, Long>,
        originalRows: JSONArray
    ) {
        val explicitSelfRefs = importReferences
            .filter { it.tableName == tableName && it.referencedTableName == tableName }
            .mapNotNull { reference ->
                entitySchema.getField(reference.fieldPath)
            }
            .distinctBy { it.fieldPath }

        if (explicitSelfRefs.isEmpty()) return

        for (i in 0 until originalRows.length()) {
            val row = originalRows.getJSONObject(i)
            val oldId = row.optLong("id", 0)
            val newId = insertedIdMapping[oldId] ?: continue

            for (field in explicitSelfRefs) {
                if (!row.has(field.fieldPath)) continue
                val oldRefId = row.optLong(field.fieldPath, 0)
                if (oldRefId == 0L) continue

                val newRefId = idMapping[oldRefId] ?: continue

                db.execSQL(
                    "UPDATE $tableName SET ${field.columnName} = ? WHERE id = ?",
                    arrayOf(newRefId, newId)
                )
            }
        }
    }

    /**
     * Room schemaだけでは表現されていない参照を明示的に変換する。
     *
     * 変更理由: hosts.pubkeyId はsentinel値を持ち、profileId / jumpHostId は
     * import時のID再採番を明示制御しないと誤変換されるため。
     */
    private fun applyExplicitReferences(
        tableName: String,
        row: JSONObject,
        idMappings: Map<String, Map<Long, Long>>,
        postponeSelfRefs: Boolean
    ): JSONObject {
        val remapped = JSONObject(row.toString())
        importReferences
            .filter { it.tableName == tableName }
            .forEach { reference ->
                if (!remapped.has(reference.fieldPath)) {
                    return@forEach
                }

                if (remapped.isNull(reference.fieldPath)) {
                    // 変更理由: Room FKの汎用remapでNULL化されたprofileIdは、Defaultへ戻す必要がある。
                    if (reference.missingValue != JSONObject.NULL) {
                        remapped.put(reference.fieldPath, reference.missingValue)
                    }
                    return@forEach
                }

                if (postponeSelfRefs && reference.tableName == reference.referencedTableName) {
                    remapped.put(reference.fieldPath, JSONObject.NULL)
                    return@forEach
                }

                val oldValue = remapped.optLong(reference.fieldPath, 0L)
                if (oldValue <= 0L) return@forEach

                val newValue = idMappings[reference.referencedTableName]?.get(oldValue)
                if (newValue != null) {
                    remapped.put(reference.fieldPath, newValue)
                } else {
                    remapped.put(reference.fieldPath, reference.missingValue ?: JSONObject.NULL)
                }
            }
        return remapped
    }

    /**
     * 親行が既存データとしてskipされた子行は、重複importを避けるため取り込まない。
     */
    private fun shouldSkipBecauseParentWasSkipped(
        tableName: String,
        row: JSONObject,
        insertedIdMappings: Map<String, Map<Long, Long>>
    ): Boolean {
        return skipRowsWhenParentSkipped
            .filter { it.tableName == tableName }
            .any { reference ->
                if (!row.has(reference.fieldPath) || row.isNull(reference.fieldPath)) {
                    return@any false
                }
                val oldParentId = row.optLong(reference.fieldPath, 0L)
                oldParentId > 0L && insertedIdMappings[reference.referencedTableName]?.containsKey(oldParentId) == false
            }
    }

    /**
     * import対象外テーブルへの参照を安全な値に落とす。
     *
     * 変更理由: ホスト設定JSONは秘密鍵を含めないため、旧 pubkey_id をそのまま残すと
     * 別端末の既存鍵IDへ偶然紐づく危険がある。
     */
    private fun applyDroppedReferences(tableName: String, row: JSONObject): JSONObject {
        val remapped = JSONObject(row.toString())
        droppedReferences
            .filter { it.tableName == tableName }
            .forEach { reference ->
                if (remapped.has(reference.fieldPath) && !remapped.isNull(reference.fieldPath)) {
                    val oldValue = remapped.optLong(reference.fieldPath, 0L)
                    if (oldValue > 0L) {
                        remapped.put(reference.fieldPath, reference.replacementValue)
                    }
                }
            }
        return remapped
    }

    /**
     * Convert JSON object to ContentValues for database insertion.
     */
    private fun jsonToContentValues(
        row: JSONObject,
        entitySchema: EntitySchema,
        excludeId: Boolean
    ): ContentValues {
        val values = ContentValues()

        for (field in entitySchema.fields) {
            if (excludeId && field.columnName == "id") continue

            // For excluded fields, provide default values if NOT NULL
            if (field.excluded) {
                if (field.notNull) {
                    putSchemaDefault(values, field)
                }
                continue
            }

            if (!row.has(field.fieldPath)) continue

            if (row.isNull(field.fieldPath)) {
                values.putNull(field.columnName)
                continue
            }

            when (field.affinity) {
                "INTEGER" -> values.put(field.columnName, row.getLong(field.fieldPath))
                "TEXT" -> values.put(field.columnName, row.getString(field.fieldPath))
                "REAL" -> values.put(field.columnName, row.getDouble(field.fieldPath))
                "BLOB" -> {
                    val base64 = row.getString(field.fieldPath)
                    values.put(field.columnName, Base64.decode(base64, Base64.NO_WRAP))
                }
            }
        }

        return values
    }

    private fun putSchemaDefault(values: ContentValues, field: FieldSchema) {
        val defaultValue = field.defaultValue
        if (defaultValue != null && defaultValue.equals("NULL", ignoreCase = true)) {
            values.putNull(field.columnName)
            return
        }

        // 変更理由: export対象外のNOT NULL列はRoom schemaのdefaultValueを優先し、
        // last_connect等にだけ互換fallbackを使う。
        when (field.affinity) {
            "INTEGER" -> values.put(field.columnName, defaultValue?.toLongOrNull() ?: 0L)
            "TEXT" -> values.put(field.columnName, defaultValue?.toSqlTextLiteral() ?: "")
            "REAL" -> values.put(field.columnName, defaultValue?.toDoubleOrNull() ?: 0.0)
            "BLOB" -> values.put(field.columnName, ByteArray(0))
        }
    }

    private fun String.toSqlTextLiteral(): String {
        val trimmed = trim()
        return if (trimmed.length >= 2 && trimmed.first() == '\'' && trimmed.last() == '\'') {
            trimmed.substring(1, trimmed.length - 1).replace("''", "'")
        } else {
            trimmed
        }
    }

    data class ImportReference(
        val tableName: String,
        val fieldPath: String,
        val referencedTableName: String,
        val missingValue: Any? = JSONObject.NULL
    )

    data class DroppedReference(
        val tableName: String,
        val fieldPath: String,
        val replacementValue: Any
    )
}
