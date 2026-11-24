package vn.tung.taichinh.data

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipInputStream

/** Minimal offline XLSX reader for the six source sheets used by this app. */
object WorkbookXlsxImporter {
    fun read(resolver: ContentResolver, uri: Uri): JSONObject {
        val entries = linkedMapOf<String, ByteArray>()
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) entries[e.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        } ?: error("Không mở được tệp Excel")
        require(entries.containsKey("xl/workbook.xml")) { "Tệp không phải workbook XLSX hợp lệ" }
        val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val rels = parseRelationships(entries["xl/_rels/workbook.xml.rels"])
        val sheetPaths = parseWorkbook(entries["xl/workbook.xml"]).mapValues { (_, relId) ->
            val target = rels[relId] ?: error("Không tìm thấy sheet trong workbook")
            if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("xl/")
        }
        fun rows(sheet: String): List<Map<Int, String>> {
            val path = sheetPaths[sheet] ?: error("Không tìm thấy sheet '$sheet'")
            val bytes = entries[path] ?: error("Không đọc được dữ liệu sheet '$sheet'")
            return parseSheet(bytes, shared)
        }
        val root = JSONObject()
        root.put("accounts", sourceRows(rows("HỆ THỐNG TK"), 0, 1, stringCols = setOf(0, 1)))
        root.put("categories", sourceRows(rows("MỤC THU CHI"), 0, 1, stringCols = setOf(0, 1)))
        root.put("savings", sourceRows(rows("SỔ TK"), 0, 9, setOf(5, 6)))
        root.put("gold", sourceRows(rows("VÀNG"), 0, 9, setOf(0)))
        root.put("cash", sourceRows(rows("tủ"), 0, 2))
        val tx = JSONArray()
        val ledgerRows = rows("SỔ CÁI")
        ledgerRows.forEachIndexed { index, r ->
            // Source workbook has two heading rows; data starts at Excel row 3.
            val excelRow = r[-1]?.toIntOrNull() ?: (index + 1)
            if (excelRow < 3) return@forEachIndexed
            val date = excelDate(r[0].orEmpty())
            val amount = r[1]?.toDoubleOrNull() ?: 0.0
            // Preserve source rows with a date/account/category/note even when amount is blank.
            if (r[0].isNullOrBlank() && r[4].isNullOrBlank() && r[5].isNullOrBlank() && r[6].isNullOrBlank() && r[1].isNullOrBlank()) return@forEachIndexed
            val sign = r[2]?.toDoubleOrNull() ?: 1.0
            val cv = JSONObject()
                .put("sourceRow", excelRow)
                .put("date", date)
                .put("amount", amount)
                .put("sign", sign)
                .put("includeInReport", r[3]?.toDoubleOrNull() ?: 0.0)
                .put("account", r[4] ?: JSONObject.NULL)
                .put("category", r[5] ?: JSONObject.NULL)
                .put("note", r[6] ?: "")
                .put("signedAmount", r[7]?.toDoubleOrNull() ?: amount * sign)
            tx.put(cv)
        }
        root.put("transactions", tx)
        root.put("source", "xlsx")
        return root
    }

    private fun sourceRows(rows: List<Map<Int, String>>, first: Int, last: Int, dateCols: Set<Int> = emptySet(), stringCols: Set<Int> = emptySet()): JSONArray {
        val out = JSONArray()
        rows.forEachIndexed { i, row ->
            val sourceRow = row[-1]?.toIntOrNull() ?: (i + 1)
            val values = JSONArray()
            for (c in first..last) {
                val raw = row[c]
                val value: Any = when {
                    raw == null -> JSONObject.NULL
                    c in dateCols -> excelDate(raw)
                    c in stringCols -> raw
                    else -> raw.toDoubleOrNull() ?: raw
                }
                values.put(value)
            }
            if ((0..last).any { row[it]?.isNotBlank() == true }) {
                out.put(JSONObject().put("sourceRow", sourceRow).put("values", values))
            }
        }
        return out
    }

    private fun excelDate(raw: String): String {
        raw.toDoubleOrNull()?.let { serial ->
            if (serial > 0) return LocalDate.of(1899, 12, 30).plusDays(serial.toLong()).toString()
        }
        return raw.take(10)
    }

    private fun parser(bytes: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val p = parser(bytes); val result = mutableListOf<String>(); var current = StringBuilder(); var inSi = false
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name == "si") { current = StringBuilder(); inSi = true }
            else if (event == XmlPullParser.START_TAG && p.name == "t" && inSi) current.append(readText(p))
            else if (event == XmlPullParser.END_TAG && p.name == "si") { result.add(current.toString()); inSi = false }
            event = p.next()
        }
        return result
    }
    private fun readText(p: XmlPullParser): String = p.nextText()

    private fun parseRelationships(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val p = parser(bytes); val out = mutableMapOf<String, String>(); var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "Relationship") {
                val id = p.getAttributeValue(null, "Id"); val target = p.getAttributeValue(null, "Target")
                if (id != null && target != null) out[id] = target
            }
            e = p.next()
        }
        return out
    }
    private fun parseWorkbook(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val p = parser(bytes); val out = linkedMapOf<String, String>(); var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "sheet") {
                val name = p.getAttributeValue(null, "name")
                var id: String? = null
                for (i in 0 until p.attributeCount) if (p.getAttributeName(i) == "id") id = p.getAttributeValue(i)
                if (name != null && id != null) out[name] = id
            }
            e = p.next()
        }
        return out
    }
    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Map<Int, String>> {
        val p = parser(bytes); val out = mutableListOf<Map<Int, String>>(); var row = linkedMapOf<Int, String>()
        var cellRef = ""; var cellType = ""; var cellValue = ""; var inValue = false; var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            when (e) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> { row = linkedMapOf(); row[-1] = p.getAttributeValue(null, "r") ?: "" }
                    "c" -> { cellRef = p.getAttributeValue(null, "r") ?: "A1"; cellType = p.getAttributeValue(null, "t") ?: ""; cellValue = "" }
                    "v", "t" -> if (cellRef.isNotEmpty()) inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) cellValue += p.text
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val col = columnIndex(cellRef)
                        val value = if (cellType == "s") shared.getOrNull(cellValue.toIntOrNull() ?: -1).orEmpty() else cellValue
                        row[col] = value
                        cellRef = ""
                    }
                    "row" -> out.add(row)
                }
            }
            e = p.next()
        }
        return out
    }
    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        var n = 0
        letters.forEach { n = n * 26 + (it - 'A' + 1) }
        return n - 1
    }
}
