package com.example.templei.feature.screen4

import android.content.Context
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Workbook-backed metadata catalog for Screen 4 column definitions and validation rules.
 */
class Screen4WorkbookCatalog private constructor(
    val columnDefinitions: List<Screen4WorkbookColumnDefinition>,
    private val regexPatternsByName: Map<String, Screen4WorkbookRegexPattern>,
    private val normalizationRulesByProcedure: Map<String, List<Screen4WorkbookNormalizationRule>>,
) {
    private val columnsByName = columnDefinitions.associateBy { it.columnName }

    fun findColumnDefinition(columnName: String): Screen4WorkbookColumnDefinition? =
        columnsByName[columnName.trim().lowercase(Locale.US)]

    fun findDefinitionForTemplate(fakerKey: String, constraintType: String): Screen4WorkbookColumnDefinition? {
        val normalizedKey = Screen4NumericFormatPolicy.normalizedTemplateFakerKey(fakerKey)
        val workbookKey = normalizedKey.removePrefix(WORKBOOK_TEMPLATE_PREFIX)
        return when {
            normalizedKey.startsWith(WORKBOOK_TEMPLATE_PREFIX) -> findColumnDefinition(workbookKey)
            else -> findColumnDefinition(constraintType)
        }
    }

    fun findRegexPattern(regexPatternName: String?): Screen4WorkbookRegexPattern? {
        if (regexPatternName.isNullOrBlank()) return null
        return regexPatternsByName[regexPatternName.trim().uppercase(Locale.US)]
    }

    fun normalizationRulesFor(procedureNames: List<String>): List<Screen4WorkbookNormalizationRule> {
        return procedureNames.flatMap { procedure ->
            normalizationRulesByProcedure[procedure.trim().uppercase(Locale.US)].orEmpty()
        }
    }

    fun selectableGroups(): List<Screen4WorkbookColumnGroup> {
        return columnDefinitions
            .groupBy { it.group.uppercase(Locale.US) }
            .map { (key, definitions) ->
                val sample = definitions.first()
                Screen4WorkbookColumnGroup(
                    key = key,
                    label = sample.groupDisplayLabel(),
                    itemCount = definitions.size,
                )
            }
            .sortedBy { it.label }
    }

    fun subgroupsForGroup(groupKey: String): List<Screen4WorkbookColumnSubgroup> {
        return columnDefinitions
            .filter { it.group.equals(groupKey, ignoreCase = true) }
            .groupBy { it.groupClassification.uppercase(Locale.US) }
            .map { (key, definitions) ->
                val sample = definitions.first()
                Screen4WorkbookColumnSubgroup(
                    key = key,
                    label = sample.groupClassificationDisplayLabel(),
                    itemCount = definitions.size,
                )
            }
            .sortedBy { it.label }
    }

    fun columnsForGroup(groupKey: String, subgroupKey: String): List<Screen4WorkbookColumnDefinition> {
        return columnDefinitions
            .filter {
                it.group.equals(groupKey, ignoreCase = true) &&
                    it.groupClassification.equals(subgroupKey, ignoreCase = true)
            }
            .sortedBy { it.displayName }
    }

    companion object {
        private const val WORKBOOK_ASSET_PATH = "screen4/Paulsdataset.xlsx"
        const val WORKBOOK_TEMPLATE_PREFIX = "screen4.workbook."

        @Volatile
        private var instance: Screen4WorkbookCatalog? = null

        fun getInstance(context: Context): Screen4WorkbookCatalog {
            return instance ?: synchronized(this) {
                instance ?: fromWorkbook(context.assets.open(WORKBOOK_ASSET_PATH)).also { instance = it }
            }
        }

        private fun fromWorkbook(inputStream: InputStream): Screen4WorkbookCatalog {
            val entries = readZipEntries(inputStream)
            val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
            val workbook = parseXml(entries.getValue("xl/workbook.xml"))
            val workbookRelationships = parseXml(entries.getValue("xl/_rels/workbook.xml.rels"))
            val sheetTargetsByName = workbookSheetTargets(workbook, workbookRelationships)

            val columnRows = parseSheetRows(
                entries.getValue("xl/${sheetTargetsByName.getValue("ColumnMetaData")}"),
                sharedStrings,
            )
            val regexRows = parseSheetRows(
                entries.getValue("xl/${sheetTargetsByName.getValue("RegexPatterns")}"),
                sharedStrings,
            )
            val normalizationRows = parseSheetRows(
                entries.getValue("xl/${sheetTargetsByName.getValue("NormalizationRules")}"),
                sharedStrings,
            )

            val columnDefinitions = parseColumnDefinitions(columnRows)
            val regexPatterns = parseRegexPatterns(regexRows)
            val normalizationRules = parseNormalizationRules(normalizationRows)

            return Screen4WorkbookCatalog(
                columnDefinitions = columnDefinitions,
                regexPatternsByName = regexPatterns.associateBy { it.name },
                normalizationRulesByProcedure = normalizationRules.groupBy { it.procedureName },
            )
        }

        private fun readZipEntries(inputStream: InputStream): Map<String, ByteArray> {
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val normalizedName = entry.name.replace('\\', '/')
                        entries[normalizedName] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return entries
        }

        private fun parseXml(bytes: ByteArray): Document {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        }

        private fun parseSharedStrings(bytes: ByteArray?): List<String> {
            if (bytes == null) return emptyList()
            val document = parseXml(bytes)
            return document.getElementsByTagNameNS(SPREADSHEET_NS, "si").asList().map { si ->
                si.childElementsRecursive("t").joinToString(separator = "") { it.textContent.orEmpty() }
            }
        }

        private fun workbookSheetTargets(workbook: Document, relationships: Document): Map<String, String> {
            val targetByRelationId = relationships
                .getElementsByTagName("Relationship")
                .asList()
                .associate { relationship ->
                    relationship.attributeValue("Id") to relationship.attributeValue("Target")
                }

            return workbook
                .getElementsByTagNameNS(SPREADSHEET_NS, "sheet")
                .asList()
                .associate { sheet ->
                    val relationId = sheet.attributeValueNs(DOCUMENT_REL_NS, "id")
                    sheet.attributeValue("name") to targetByRelationId.getValue(relationId)
                }
        }

        private fun parseSheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
            val document = parseXml(bytes)
            return document
                .getElementsByTagNameNS(SPREADSHEET_NS, "row")
                .asList()
                .map { row ->
                    val valuesByIndex = mutableMapOf<Int, String>()
                    row.childElements("c").forEach { cell ->
                        val cellReference = cell.attributeValue("r")
                        val index = columnReferenceToIndex(cellReference)
                        valuesByIndex[index] = readCellValue(cell, sharedStrings)
                    }

                    val maxIndex = valuesByIndex.keys.maxOrNull() ?: -1
                    List(maxIndex + 1) { index -> valuesByIndex[index].orEmpty() }
                }
        }

        private fun parseColumnDefinitions(rows: List<List<String>>): List<Screen4WorkbookColumnDefinition> {
            if (rows.isEmpty()) return emptyList()
            val header = rows.first()
            return rows.drop(1).mapNotNull { row ->
                val map = header.associateWithIndex(row)
                val columnName = map["column_name"].normalizedWorkbookCell().lowercase(Locale.US)
                if (columnName.isEmpty()) return@mapNotNull null

                Screen4WorkbookColumnDefinition(
                    columnId = map["Column_ID"].orEmpty(),
                    group = map["group"].orEmpty(),
                    groupClassification = map["group_classification"].orEmpty(),
                    columnName = columnName,
                    displayName = columnName.displayLabel(),
                    dataType = map["data_type"].orEmpty(),
                    regexPatternName = map["regex_pattern_name"].normalizedWorkbookCell().ifBlank { null },
                    uiExampleValue = map["ui_example_value"].normalizedWorkbookCell(),
                    minLength = map["min"].parseWorkbookInt(),
                    maxLength = map["max"].parseWorkbookInt(),
                    allowedValues = map["allowed_values"]
                        .normalizedWorkbookCell()
                        .split('|')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct(),
                    required = map["required"].parseWorkbookBoolean(),
                    normalizationProcedures = map["normalize"]
                        .normalizedWorkbookCell()
                        .split('|')
                        .map { it.trim().uppercase(Locale.US) }
                        .filter { it.isNotEmpty() },
                    uiInputType = map["ui_input_type"].normalizedWorkbookCell().ifBlank { null },
                    notes = map["notes"].normalizedWorkbookCell(),
                    participatesInValidation = map["participates_in_validation"].parseWorkbookBoolean(defaultValue = true),
                )
            }.sortedBy { it.displayName }
        }

        private fun parseRegexPatterns(rows: List<List<String>>): List<Screen4WorkbookRegexPattern> {
            if (rows.isEmpty()) return emptyList()
            val header = rows.first()
            return rows.drop(1).mapNotNull { row ->
                val map = header.associateWithIndex(row)
                val name = map["regex_pattern_name"].normalizedWorkbookCell().uppercase(Locale.US)
                val pattern = map["regex"].normalizedWorkbookCell()
                if (name.isEmpty() || pattern.isEmpty()) return@mapNotNull null

                Screen4WorkbookRegexPattern(
                    name = name,
                    pattern = pattern,
                    description = map["description"].normalizedWorkbookCell(),
                )
            }
        }

        private fun parseNormalizationRules(rows: List<List<String>>): List<Screen4WorkbookNormalizationRule> {
            if (rows.isEmpty()) return emptyList()
            val header = rows.first()
            return rows.drop(1).mapNotNull { row ->
                val map = header.associateWithIndex(row)
                val procedure = map["NormalizationProcedure"].normalizedWorkbookCell().uppercase(Locale.US)
                val operation = map["Operation"].normalizedWorkbookCell().uppercase(Locale.US)
                if (procedure.isEmpty() || operation.isEmpty()) return@mapNotNull null

                Screen4WorkbookNormalizationRule(
                    procedureName = procedure,
                    operation = operation,
                    pattern = map["Pattern"].normalizedWorkbookCell().ifBlank { null },
                    replacement = map["Replacement"].normalizedWorkbookCell().ifBlank { null },
                    description = map["Description"].normalizedWorkbookCell(),
                )
            }
        }

        private fun List<String>.associateWithIndex(row: List<String>): Map<String, String> {
            return mapIndexed { index, key -> key to row.getOrNull(index).orEmpty() }.toMap()
        }

        private fun String?.normalizedWorkbookCell(): String {
            val trimmed = this.orEmpty().trim()
            return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
        }

        private fun String?.parseWorkbookInt(): Int? {
            val normalized = normalizedWorkbookCell()
            if (normalized.isEmpty()) return null
            return normalized.toDoubleOrNull()?.toInt()
        }

        private fun String?.parseWorkbookBoolean(defaultValue: Boolean = false): Boolean {
            return when (normalizedWorkbookCell().uppercase(Locale.US)) {
                "1", "TRUE", "YES", "Y" -> true
                "0", "FALSE", "NO", "N" -> false
                else -> defaultValue
            }
        }

        private fun columnReferenceToIndex(cellReference: String): Int {
            var result = 0
            cellReference.takeWhile { it.isLetter() }.uppercase(Locale.US).forEach { character ->
                result = (result * 26) + (character.code - 'A'.code + 1)
            }
            return result - 1
        }

        private fun readCellValue(cell: Element, sharedStrings: List<String>): String {
            val type = cell.attributeValue("t")
            val valueNode = cell.childElements("v").firstOrNull()
            if (valueNode != null) {
                val rawValue = valueNode.textContent.orEmpty()
                return if (type == "s") {
                    sharedStrings.getOrNull(rawValue.toIntOrNull() ?: -1).orEmpty()
                } else {
                    rawValue
                }
            }

            val inlineStrings = cell.childElementsRecursive("t").joinToString(separator = "") { it.textContent.orEmpty() }
            return inlineStrings
        }

        private fun String.displayLabel(): String {
            return split('_').joinToString(" ") { part ->
                part.replaceFirstChar { character ->
                    if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
                }
            }
        }

        private fun org.w3c.dom.Node.attributeValue(name: String): String =
            attributes?.getNamedItem(name)?.nodeValue.orEmpty()

        private fun org.w3c.dom.Node.attributeValueNs(namespace: String, localName: String): String =
            attributes?.getNamedItemNS(namespace, localName)?.nodeValue.orEmpty()

        private fun org.w3c.dom.Node.childElements(localName: String): List<Element> =
            childNodes.asList().filterIsInstance<Element>().filter { it.localName == localName }

        private fun org.w3c.dom.Node.childElementsRecursive(localName: String): List<Element> {
            val collected = mutableListOf<Element>()
            childNodes.asList().forEach { child ->
                if (child is Element) {
                    if (child.localName == localName) {
                        collected += child
                    }
                    collected += child.childElementsRecursive(localName)
                }
            }
            return collected
        }

        private fun org.w3c.dom.NodeList.asList(): List<org.w3c.dom.Node> =
            (0 until length).map(::item)

        private const val SPREADSHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val DOCUMENT_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    }
}

data class Screen4WorkbookColumnGroup(
    val key: String,
    val label: String,
    val itemCount: Int,
)

data class Screen4WorkbookColumnSubgroup(
    val key: String,
    val label: String,
    val itemCount: Int,
)

data class Screen4WorkbookColumnDefinition(
    val columnId: String,
    val group: String,
    val groupClassification: String,
    val columnName: String,
    val displayName: String,
    val dataType: String,
    val regexPatternName: String?,
    val uiExampleValue: String,
    val minLength: Int?,
    val maxLength: Int?,
    val allowedValues: List<String>,
    val required: Boolean,
    val normalizationProcedures: List<String>,
    val uiInputType: String?,
    val notes: String,
    val participatesInValidation: Boolean,
)

data class Screen4WorkbookRegexPattern(
    val name: String,
    val pattern: String,
    val description: String,
)

data class Screen4WorkbookNormalizationRule(
    val procedureName: String,
    val operation: String,
    val pattern: String?,
    val replacement: String?,
    val description: String,
)

private fun Screen4WorkbookColumnDefinition.groupDisplayLabel(): String =
    group.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

private fun Screen4WorkbookColumnDefinition.groupClassificationDisplayLabel(): String =
    groupClassification.lowercase(Locale.US)
        .split('_')
        .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.US) } }
