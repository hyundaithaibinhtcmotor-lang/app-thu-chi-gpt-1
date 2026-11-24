package vn.tung.taichinh.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class FinanceDb(context: Context) : SQLiteOpenHelper(context, "personal_finance.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, source_row INTEGER, date TEXT, amount REAL NOT NULL, sign REAL NOT NULL, report_flag REAL NOT NULL DEFAULT 1, account TEXT, category TEXT, note TEXT, signed_amount REAL NOT NULL)")
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, source_row INTEGER, name TEXT, code TEXT, raw_json TEXT)")
        db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT, source_row INTEGER, name TEXT, code TEXT, raw_json TEXT)")
        db.execSQL("CREATE TABLE savings(id INTEGER PRIMARY KEY AUTOINCREMENT, source_row INTEGER, raw_json TEXT)")
        db.execSQL("CREATE TABLE gold(id INTEGER PRIMARY KEY AUTOINCREMENT, source_row INTEGER, raw_json TEXT)")
        db.execSQL("CREATE TABLE cash(id INTEGER PRIMARY KEY AUTOINCREMENT, source_row INTEGER, raw_json TEXT)")
        db.execSQL("CREATE TABLE app_meta(key TEXT PRIMARY KEY, value TEXT)")
        createIndexes(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createIndexes(db)
        if (oldVersion < 3) db.execSQL("CREATE TABLE IF NOT EXISTS app_meta(key TEXT PRIMARY KEY, value TEXT)")
    }
    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_account ON transactions(account)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_category ON transactions(category)")
    }

    fun importSeed(context: Context): Boolean {
        val db = writableDatabase
        val exists = db.rawQuery("SELECT value FROM app_meta WHERE key='seed_imported'", null).use { it.moveToFirst() }
        if (exists) return false
        val json = JSONObject(context.assets.open("initial_data.json").bufferedReader().use { it.readText() })
        db.beginTransaction()
        try {
            insertRawTable(db, json.getJSONArray("accounts"), "accounts")
            insertRawTable(db, json.getJSONArray("categories"), "categories")
            insertRawTable(db, json.getJSONArray("savings"), "savings")
            insertRawTable(db, json.getJSONArray("gold"), "gold")
            insertRawTable(db, json.getJSONArray("cash"), "cash")
            val rows = json.getJSONArray("transactions")
            for (i in 0 until rows.length()) {
                val o = rows.getJSONObject(i)
                val amount = o.optDouble("amount")
                val sign = o.optDouble("sign", 1.0)
                val cv = ContentValues().apply {
                    put("source_row", o.optInt("sourceRow")); put("date", o.optString("date"))
                    put("amount", amount); put("sign", sign); put("report_flag", o.optDouble("includeInReport"))
                    put("account", o.optString("account").takeUnless { it == "null" })
                    put("category", o.optString("category").takeUnless { it == "null" })
                    put("note", o.optString("note")); put("signed_amount", o.optDouble("signedAmount", amount * sign))
                }
                db.insert("transactions", null, cv)
            }
            db.execSQL("INSERT INTO app_meta(key,value) VALUES('seed_imported','true')")
            db.execSQL("INSERT INTO app_meta(key,value) VALUES('source_file','BCTC (1).xlsx')")
            db.execSQL("INSERT INTO app_meta(key,value) VALUES('seed_version','1')")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return true
    }

    private fun insertRawTable(db: SQLiteDatabase, array: JSONArray, table: String) {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i); val vals = o.optJSONArray("values") ?: JSONArray()
            val name = vals.optString(0).takeUnless { it == "null" || it.isBlank() }
            val code = vals.optString(1).takeUnless { it == "null" || it.isBlank() }
            val cv = ContentValues().apply {
                put("source_row", o.optInt("sourceRow"))
                if (table == "accounts" || table == "categories") { put("name", name); put("code", code) }
                put("raw_json", o.toString())
            }
            db.insert(table, null, cv)
        }
    }

    fun addTransaction(date: String, amount: Double, sign: Double, account: String, category: String?, note: String, reportFlag: Double = 1.0) {
        require(amount > 0 && amount.isFinite()) { "Số tiền phải lớn hơn 0" }
        require(runCatching { LocalDate.parse(date.take(10)) }.isSuccess) { "Ngày không hợp lệ (YYYY-MM-DD)" }
        val cv = ContentValues().apply {
            put("date", date.take(10)); put("amount", amount); put("sign", if (sign < 0) -1.0 else 1.0)
            put("report_flag", reportFlag); put("account", account); put("category", category)
            put("note", note); put("signed_amount", amount * if (sign < 0) -1.0 else 1.0)
        }
        writableDatabase.insertOrThrow("transactions", null, cv)
    }
    fun updateTransaction(id: Long, date: String, amount: Double, sign: Double, account: String, category: String?, note: String, reportFlag: Double) {
        require(amount > 0 && amount.isFinite())
        require(runCatching { LocalDate.parse(date.take(10)) }.isSuccess)
        val cv = ContentValues().apply {
            put("date", date.take(10)); put("amount", amount); put("sign", if (sign < 0) -1.0 else 1.0)
            put("report_flag", reportFlag); put("account", account); put("category", category)
            put("note", note); put("signed_amount", amount * if (sign < 0) -1.0 else 1.0)
        }
        writableDatabase.update("transactions", cv, "id=?", arrayOf(id.toString()))
    }
    fun deleteTransaction(id: Long) { writableDatabase.delete("transactions", "id=?", arrayOf(id.toString())) }

    fun transactions(): List<Tx> {
        val out = mutableListOf<Tx>()
        readableDatabase.rawQuery("SELECT id,date,amount,sign,account,category,note,signed_amount,report_flag,source_row FROM transactions ORDER BY date DESC, id DESC", null).use { c ->
            while (c.moveToNext()) out.add(Tx(c.getLong(0), c.getString(1) ?: "", c.getDouble(2), c.getDouble(3), c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getDouble(7), c.getDouble(8), if(c.isNull(9)) null else c.getInt(9)))
        }
        return out
    }
    fun names(table: String): List<String> {
        require(table in listOf("accounts", "categories"))
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT name FROM $table WHERE name IS NOT NULL AND name<>'' ORDER BY source_row", null).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out.distinct()
    }
    fun accountCode(name: String): String? = readableDatabase.rawQuery("SELECT code FROM accounts WHERE name=? LIMIT 1", arrayOf(name)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun categoryCode(name: String): String? = readableDatabase.rawQuery("SELECT code FROM categories WHERE name=? LIMIT 1", arrayOf(name)).use { if (it.moveToFirst()) it.getString(0) else null }

    // Excel's account hierarchy: code prefix 1 = assets; prefix 2 = liabilities.
    // Liabilities are already negative in the ledger, so net worth = assets + liabilities.
    fun totals(): FinanceTotals {
        val db = readableDatabase
        fun scalar(sql: String): Double = db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
        val assets = scalar("SELECT COALESCE(SUM(t.signed_amount),0) FROM transactions t JOIN accounts a ON a.name=t.account WHERE a.code LIKE '1%'")
        val liabilities = scalar("SELECT COALESCE(SUM(t.signed_amount),0) FROM transactions t JOIN accounts a ON a.name=t.account WHERE a.code LIKE '2%'")
        val income = scalar("SELECT COALESCE(SUM(t.signed_amount),0) FROM transactions t JOIN categories c ON c.name=t.category WHERE c.code LIKE '1%'")
        val expense = scalar("SELECT COALESCE(-SUM(t.signed_amount),0) FROM transactions t JOIN categories c ON c.name=t.category WHERE c.code LIKE '2%'")
        return FinanceTotals(assets, liabilities, assets + liabilities, income, expense)
    }

    fun periodTotals(year: Int, month: Int): Pair<Double, Double> {
        val db = readableDatabase
        fun sum(prefix: String): Double = db.rawQuery(
            "SELECT COALESCE(SUM(t.signed_amount),0) FROM transactions t JOIN categories c ON c.name=t.category WHERE c.code LIKE ? AND substr(t.date,1,4)=? AND substr(t.date,6,2)=?",
            arrayOf("$prefix%", year.toString(), "%02d".format(month))
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
        return sum("1") to -sum("2")
    }

    fun categoryTotals(year: Int, month: Int, income: Boolean): List<CategoryTotal> {
        val prefix = if (income) "1%" else "2%"
        val out = mutableListOf<CategoryTotal>()
        readableDatabase.rawQuery(
            "SELECT t.category, CASE WHEN c.code LIKE '2%' THEN -SUM(t.signed_amount) ELSE SUM(t.signed_amount) END total FROM transactions t JOIN categories c ON c.name=t.category WHERE c.code LIKE ? AND substr(t.date,1,4)=? AND substr(t.date,6,2)=? GROUP BY t.category ORDER BY total DESC",
            arrayOf(prefix, year.toString(), "%02d".format(month))
        ).use { c -> while (c.moveToNext()) out.add(CategoryTotal(c.getString(0) ?: "Chưa phân loại", c.getDouble(1))) }
        return out
    }

    fun accountBalances(): List<AccountBalance> {
        val out = mutableListOf<AccountBalance>()
        readableDatabase.rawQuery(
            "SELECT t.account, a.code, COALESCE(SUM(t.signed_amount),0) balance FROM transactions t JOIN accounts a ON a.name=t.account WHERE t.account IS NOT NULL AND t.account<>'' GROUP BY t.account,a.code ORDER BY a.code,t.account",
            null
        ).use { c -> while (c.moveToNext()) out.add(AccountBalance(c.getString(0), c.getString(1) ?: "", c.getDouble(2))) }
        return out
    }

    fun savingsRows(): List<JSONObject> = rawRows("savings")
    fun goldRows(): List<JSONObject> = rawRows("gold")
    fun cashRows(): List<JSONObject> = rawRows("cash")
    private fun rawRows(table: String): List<JSONObject> {
        require(table in listOf("savings", "gold", "cash"))
        val out = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT raw_json FROM $table ORDER BY source_row", null).use { c ->
            while (c.moveToNext()) out.add(JSONObject(c.getString(0)))
        }
        return out
    }

    /** Replace all workbook-derived tables atomically after a successful XLSX parse. */
    fun replaceWorkbookData(root: JSONObject) {
        val required = listOf("accounts", "categories", "savings", "gold", "cash", "transactions")
        required.forEach { require(root.optJSONArray(it) != null) { "Workbook thiếu dữ liệu $it" } }
        require(root.getJSONArray("transactions").length() > 0) { "Không tìm thấy giao dịch SỔ CÁI; dữ liệu hiện tại được giữ nguyên" }
        require(root.getJSONArray("accounts").length() > 0 && root.getJSONArray("categories").length() > 0) { "Thiếu danh mục tài khoản hoặc thu chi; dữ liệu hiện tại được giữ nguyên" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in listOf("transactions", "accounts", "categories", "savings", "gold", "cash")) db.delete(table, null, null)
            insertRawTable(db, root.getJSONArray("accounts"), "accounts")
            insertRawTable(db, root.getJSONArray("categories"), "categories")
            insertRawTable(db, root.getJSONArray("savings"), "savings")
            insertRawTable(db, root.getJSONArray("gold"), "gold")
            insertRawTable(db, root.getJSONArray("cash"), "cash")
            val tx = root.getJSONArray("transactions")
            for (i in 0 until tx.length()) {
                val o = tx.getJSONObject(i); val amount = o.optDouble("amount"); val sign = o.optDouble("sign", 1.0)
                val cv = ContentValues().apply {
                    put("source_row", o.optInt("sourceRow")); put("date", o.optString("date")); put("amount", amount)
                    put("sign", sign); put("report_flag", o.optDouble("includeInReport"))
                    put("account", o.optString("account").takeUnless { it == "null" })
                    put("category", o.optString("category").takeUnless { it == "null" })
                    put("note", o.optString("note")); put("signed_amount", o.optDouble("signedAmount", amount * sign))
                }
                db.insertOrThrow("transactions", null, cv)
            }
            db.execSQL("INSERT OR REPLACE INTO app_meta(key,value) VALUES('seed_imported','true')")
            db.execSQL("INSERT OR REPLACE INTO app_meta(key,value) VALUES('source_file','Imported XLSX')")
            db.execSQL("INSERT OR REPLACE INTO app_meta(key,value) VALUES('seed_version','3')")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun updateCustomEntry(table: String, id: Long, name: String, code: String) {
        require(table == "accounts" || table == "categories")
        val cleanName = name.trim(); val cleanCode = code.trim()
        require(cleanName.isNotEmpty()) { "Tên không được để trống" }
        require(cleanCode.matches(Regex("[0-9]{3,8}"))) { "Mã phải gồm 3–8 chữ số" }
        require(cleanCode.first() == '1' || cleanCode.first() == '2') { "Mã phải bắt đầu bằng 1 hoặc 2" }
        val duplicate = readableDatabase.rawQuery("SELECT 1 FROM $table WHERE (name=? OR code=?) AND id<>? LIMIT 1", arrayOf(cleanName, cleanCode, id.toString())).use { it.moveToFirst() }
        require(!duplicate) { "Tên hoặc mã đã tồn tại" }
        val existingRaw = readableDatabase.rawQuery("SELECT raw_json FROM $table WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) JSONObject(it.getString(0)) else JSONObject() }
        val values = existingRaw.optJSONArray("values") ?: JSONArray()
        while (values.length() < 2) values.put(JSONObject.NULL)
        values.put(0, cleanName); values.put(1, cleanCode)
        val oldName = readableDatabase.rawQuery("SELECT name FROM $table WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null }
        require(oldName != null) { "Không tìm thấy mục cần sửa" }
        val raw = JSONObject().put("sourceRow", existingRaw.optInt("sourceRow", id.toInt())).put("values", values)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("name", cleanName); put("code", cleanCode); put("raw_json", raw.toString()) }
            require(db.update(table, cv, "id=?", arrayOf(id.toString())) == 1) { "Không tìm thấy mục cần sửa" }
            if (oldName != cleanName) {
                val column = if (table == "accounts") "account" else "category"
                val txValues = ContentValues().apply { put(column, cleanName) }
                db.update("transactions", txValues, "$column=?", arrayOf(oldName))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun deleteCustomEntry(table: String, id: Long) {
        require(table == "accounts" || table == "categories")
        val name = readableDatabase.rawQuery("SELECT name FROM $table WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null }
        require(name != null) { "Không tìm thấy mục" }
        val used = if (table == "accounts") "SELECT 1 FROM transactions WHERE account=? LIMIT 1" else "SELECT 1 FROM transactions WHERE category=? LIMIT 1"
        require(!readableDatabase.rawQuery(used, arrayOf(name)).use { it.moveToFirst() }) { "Mục đang được giao dịch sử dụng; không thể xóa" }
        writableDatabase.delete(table, "id=?", arrayOf(id.toString()))
    }

    fun rawRecords(table: String): List<RawRecord> {
        require(table in listOf("savings", "gold", "cash", "accounts", "categories"))
        val out = mutableListOf<RawRecord>()
        readableDatabase.rawQuery("SELECT id,raw_json FROM $table ORDER BY source_row", null).use { c ->
            while (c.moveToNext()) out.add(RawRecord(c.getLong(0), JSONObject(c.getString(1))))
        }
        return out
    }
    fun saveRawRecord(table: String, id: Long?, sourceRow: Int, values: JSONArray) {
        require(table in listOf("savings", "gold", "cash"))
        val raw = JSONObject().put("sourceRow", sourceRow).put("values", values)
        val cv = ContentValues().apply { put("source_row", sourceRow); put("raw_json", raw.toString()) }
        if (id == null) writableDatabase.insertOrThrow(table, null, cv)
        else require(writableDatabase.update(table, cv, "id=?", arrayOf(id.toString())) == 1) { "Không tìm thấy dữ liệu" }
    }
    fun deleteRawRecord(table: String, id: Long) {
        require(table in listOf("savings", "gold", "cash"))
        writableDatabase.delete(table, "id=?", arrayOf(id.toString()))
    }

    fun settleSavings(id: Long) {
        val db = writableDatabase
        val raw = db.rawQuery("SELECT raw_json FROM savings WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) JSONObject(it.getString(0)) else null }
            ?: error("Không tìm thấy sổ tiết kiệm")
        val values = raw.optJSONArray("values") ?: JSONArray()
        while (values.length() < 10) values.put(JSONObject.NULL)
        values.put(9, "ĐÃ TẤT TOÁN")
        val cv = ContentValues().apply { put("raw_json", raw.put("values", values).toString()) }
        db.update("savings", cv, "id=?", arrayOf(id.toString()))
    }

    fun renewSavings(id: Long): Long {
        val db = writableDatabase
        val raw = db.rawQuery("SELECT raw_json FROM savings WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) JSONObject(it.getString(0)) else null }
            ?: error("Không tìm thấy sổ tiết kiệm")
        val old = raw.optJSONArray("values") ?: JSONArray()
        require(old.optString(9) !in listOf("ĐÃ TẤT TOÁN", "ĐÃ TÁI TỤC")) { "Sổ đã tất toán hoặc tái tục" }
        val maturity = LocalDate.parse(old.optString(6).take(10))
        require(!maturity.isAfter(LocalDate.now())) { "Sổ chưa đến hạn; chưa thể tái tục" }
        val principal = old.optDouble(2); val rate = old.optDouble(3); val months = old.optDouble(4)
        require(principal > 0 && rate >= 0 && months > 0) { "Thông tin gốc/lãi suất/kỳ hạn không hợp lệ" }
        val type = old.optString(8).trim()
        val interest = principal * rate * months / 12.0
        val newPrincipal = if (type == "LÃI NHẬP GỐC") principal + interest else principal
        val newMaturity = maturity.plusMonths(months.toLong())
        val duration = ChronoUnit.DAYS.between(maturity, newMaturity).coerceAtLeast(1)
        val newValues = JSONArray()
            .put(old.optString(0)).put(old.optString(1)).put(newPrincipal).put(rate).put(months)
            .put(maturity.toString()).put(newMaturity.toString()).put("0/$duration còn $duration ngày")
            .put(type).put("ĐANG GỬI")
        val nextRow = db.rawQuery("SELECT COALESCE(MAX(source_row),0)+1 FROM savings", null).use { if (it.moveToFirst()) it.getInt(0) else 1 }
        val newRaw = JSONObject().put("sourceRow", nextRow).put("values", newValues)
        db.beginTransaction()
        try {
            old.put(9, "ĐÃ TÁI TỤC")
            db.update("savings", ContentValues().apply { put("raw_json", raw.put("values", old).toString()) }, "id=?", arrayOf(id.toString()))
            val cv = ContentValues().apply { put("source_row", nextRow); put("raw_json", newRaw.toString()) }
            val newId = db.insertOrThrow("savings", null, cv)
            db.setTransactionSuccessful()
            return newId
        } finally { db.endTransaction() }
    }

    fun addCustomEntry(table: String, name: String, code: String) {
        require(table == "accounts" || table == "categories")
        val cleanName = name.trim()
        val cleanCode = code.trim()
        require(cleanName.isNotEmpty()) { "Tên không được để trống" }
        require(cleanCode.matches(Regex("[0-9]{3,8}"))) { "Mã phải gồm 3–8 chữ số" }
        require(cleanCode.first() == '1' || cleanCode.first() == '2') {
            "Mã phải bắt đầu bằng 1 (tài sản/thu) hoặc 2 (nợ/chi)"
        }
        val exists = readableDatabase.rawQuery("SELECT 1 FROM $table WHERE name=? OR code=? LIMIT 1", arrayOf(cleanName, cleanCode)).use { it.moveToFirst() }
        require(!exists) { "Tên hoặc mã đã tồn tại" }
        val nextRow = readableDatabase.rawQuery("SELECT COALESCE(MAX(source_row),0)+1 FROM $table", null).use { if (it.moveToFirst()) it.getInt(0) else 1 }
        val raw = JSONObject().put("sourceRow", nextRow).put("values", JSONArray().put(cleanName).put(cleanCode))
        val cv = ContentValues().apply { put("source_row", nextRow); put("name", cleanName); put("code", cleanCode); put("raw_json", raw.toString()) }
        writableDatabase.insertOrThrow(table, null, cv)
    }

    fun rawBackup(): JSONObject {
        val root = JSONObject(); val db = readableDatabase
        for (table in listOf("transactions", "accounts", "categories", "savings", "gold", "cash")) {
            val arr = JSONArray()
            db.rawQuery("SELECT * FROM $table", null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) o.put(c.getColumnName(i), if (c.isNull(i)) JSONObject.NULL else c.getString(i))
                    arr.put(o)
                }
            }
            root.put(table, arr)
        }
        root.put("format", "personal-finance-backup-v2"); root.put("createdAt", System.currentTimeMillis())
        root.put("appMeta", JSONObject().put("source", "BCTC (1).xlsx"))
        return root
    }
    fun restoreBackup(root: JSONObject) {
        require(root.optString("format") in listOf("personal-finance-backup-v1", "personal-finance-backup-v2")) { "Tệp sao lưu không đúng định dạng" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in listOf("transactions", "accounts", "categories", "savings", "gold", "cash")) {
                db.delete(table, null, null)
                val arr = root.optJSONArray(table) ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i); val cv = ContentValues()
                    val allowed = mutableSetOf<String>()
                    db.rawQuery("PRAGMA table_info($table)", null).use { c -> while(c.moveToNext()) allowed.add(c.getString(1)) }
                    for (key in item.keys()) if (key in allowed) {
                        val v = item.get(key)
                        when (v) {
                            JSONObject.NULL -> cv.putNull(key)
                            else -> cv.put(key, v.toString())
                        }
                    }
                    db.insertOrThrow(table, null, cv)
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
data class RawRecord(val id: Long, val value: JSONObject)
data class Tx(val id: Long, val date: String, val amount: Double, val sign: Double, val account: String, val category: String, val note: String, val signed: Double, val report: Double, val sourceRow: Int?)
data class FinanceTotals(val assets: Double, val liabilities: Double, val netWorth: Double, val income: Double, val expense: Double)
data class CategoryTotal(val name: String, val amount: Double)
data class AccountBalance(val name: String, val code: String, val balance: Double)
