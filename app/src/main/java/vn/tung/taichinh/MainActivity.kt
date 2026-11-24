package vn.tung.taichinh

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import vn.tung.taichinh.data.*

class MainActivity : ComponentActivity() {
    private lateinit var db: FinanceDb
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FinanceDb(this)
        val first = db.importSeed(this)
        setContent { FinanceApp(db, first) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(db: FinanceDb, firstImport: Boolean) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tx?>(null) }
    var showInfo by remember { mutableStateOf(firstImport) }
    var error by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(db.rawBackup().toString(2).toByteArray()) }
        }.onFailure { error = "Không thể sao lưu: ${it.message}" }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Không đọc được tệp")
            db.restoreBackup(JSONObject(raw)); refresh++
        }.onFailure { error = "Không thể khôi phục: ${it.message}" }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val parsed = WorkbookXlsxImporter.read(context.contentResolver, uri)
            db.replaceWorkbookData(parsed)
            refresh++
            showInfo = false
        }.onFailure { error = "Không thể nhập Excel: ${it.message}" }
    }
    val money = remember { NumberFormat.getNumberInstance(Locale("vi", "VN")) }
    val totals = remember(refresh) { db.totals() }
    val transactions = remember(refresh) { db.transactions() }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Tài chính cá nhân", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = { backupLauncher.launch("sao-luu-tai-chinh.json") }) { Icon(Icons.Default.Backup, "Sao lưu") } })
        },
        floatingActionButton = { if (tab == 0 || tab == 1) FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Thêm giao dịch") } },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Tổng quan") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Sổ cái") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Analytics, null) }, label = { Text("Báo cáo") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text("Tài sản") })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Cài đặt") })
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(Modifier.padding(padding), totals, transactions.size, money)
            1 -> Ledger(Modifier.padding(padding), transactions, money, onEdit = { editing = it }, onDelete = { db.deleteTransaction(it); refresh++ })
            2 -> Reports(Modifier.padding(padding), db, refresh, money)
            3 -> Assets(Modifier.padding(padding), db, totals, refresh, money, onChanged = { refresh++ })
            else -> SettingsPage(Modifier.padding(padding), db, transactions.size, onChanged = { refresh++ },
                backup = { backupLauncher.launch("sao-luu-tai-chinh.json") },
                restore = { restoreLauncher.launch(arrayOf("application/json", "text/*")) },
                importExcel = { xlsxLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) })
        }
    }
    if (showAdd) TransactionDialog(db, null, onDismiss = { showAdd = false }, onSaved = { refresh++; showAdd = false }, onError = { error = it })
    if (editing != null) TransactionDialog(db, editing, onDismiss = { editing = null }, onSaved = { refresh++; editing = null }, onError = { error = it })
    if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text("Đã nhập dữ liệu Excel") },
        text = { Text("Dữ liệu khởi tạo từ BCTC (1).xlsx đã được nạp vào bộ nhớ trong. Hãy đối chiếu báo cáo và số dư với Excel trước khi dùng làm số liệu chính thức.") },
        confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Đã hiểu") } })
    if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Thông báo") }, text = { Text(error!!) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Đóng") } })
}

@Composable
fun Dashboard(modifier: Modifier, totals: FinanceTotals, count: Int, money: NumberFormat) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("TỔNG QUAN", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("Tài sản ròng")
                Text("${money.format(totals.netWorth)} đ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Tài sản có ${money.format(totals.assets)} đ · Nợ ${money.format(kotlin.math.abs(totals.liabilities))} đ", style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("Tổng thu lũy kế", totals.income, Color(0xFF16834A), Modifier.weight(1f), money)
            SummaryCard("Tổng chi lũy kế", totals.expense, Color(0xFFB45309), Modifier.weight(1f), money)
        } }
        item { Text("$count dòng trong SỔ CÁI đã nhập", style = MaterialTheme.typography.bodySmall) }
        item { Text("Thu/chi được tính từ giá trị THÀNH TIỀN của SỔ CÁI và mã danh mục: 1xx là thu, 2xx là chi. Không lọc cứng theo cờ TÍNH VÀO BÁO CÁO; các nghiệp vụ cho vay, đi vay, thu nợ, trả nợ được phân loại riêng theo mã danh mục.", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun SummaryCard(title: String, value: Double, color: Color, modifier: Modifier, money: NumberFormat) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) { Text(title, color = color); Text("${money.format(value)} đ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun Ledger(modifier: Modifier, txs: List<Tx>, money: NumberFormat, onEdit: (Tx) -> Unit, onDelete: (Long) -> Unit) {
    var selected by remember { mutableStateOf<Tx?>(null) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("SỔ CÁI · ${txs.size} dòng", Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(txs, key = { it.id }) { tx ->
            Card(Modifier.clickable { selected = tx }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tx.note.ifBlank { tx.category.ifBlank { "Giao dịch" } }, fontWeight = FontWeight.SemiBold)
                        Text("${tx.date.take(10)} · ${tx.account}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (tx.category.isNotBlank()) Text(tx.category, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${if (tx.signed >= 0) "+" else "−"}${money.format(kotlin.math.abs(tx.signed))} đ",
                        color = if (tx.signed >= 0) Color(0xFF16834A) else Color(0xFFB45309), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (selected != null) AlertDialog(onDismissRequest = { selected = null }, title = { Text("Giao dịch") },
        text = { Text("${selected!!.date.take(10)}\n${selected!!.note}\n${selected!!.account}\n${selected!!.category}\n${money.format(selected!!.amount)} đ") },
        confirmButton = { TextButton(onClick = { val v = selected!!; selected = null; onEdit(v) }) { Text("Sửa") } },
        dismissButton = { Row {
            TextButton(onClick = { val id = selected!!.id; selected = null; onDelete(id) }) { Text("Xóa") }
            TextButton(onClick = { selected = null }) { Text("Đóng") }
        } })
}

@Composable
fun Reports(modifier: Modifier, db: FinanceDb, refresh: Int, money: NumberFormat) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var month by remember { mutableIntStateOf(LocalDate.now().monthValue) }
    val totals = remember(refresh, year, month) { db.periodTotals(year, month) }
    val incomes = remember(refresh, year, month) { db.categoryTotals(year, month, true) }
    val expenses = remember(refresh, year, month) { db.categoryTotals(year, month, false) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("BÁO CÁO THU CHI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }) { Icon(Icons.Default.ChevronLeft, null) }
            Text("%02d/%d".format(month, year), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }) { Icon(Icons.Default.ChevronRight, null) }
        } }
        item { SummaryCard("Tổng thu", totals.first, Color(0xFF16834A), Modifier.fillMaxWidth(), money) }
        item { SummaryCard("Tổng chi", totals.second, Color(0xFFB45309), Modifier.fillMaxWidth(), money) }
        item { SummaryCard("Chênh lệch", totals.first - totals.second, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth(), money) }
        item { Text("Chi tiết khoản thu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(incomes) { CategoryLine(it, money, Color(0xFF16834A)) }
        item { Text("Chi tiết khoản chi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(expenses) { CategoryLine(it, money, Color(0xFFB45309)) }
        item { Text("Báo cáo tháng lọc theo năm/tháng và mã danh mục gốc, sử dụng giá trị THÀNH TIỀN đã lưu từ SỔ CÁI; không lọc cứng theo cờ TÍNH VÀO BÁO CÁO.", style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable
fun CategoryLine(item: CategoryTotal, money: NumberFormat, color: Color) {
    ListItem(headlineContent = { Text(item.name) }, trailingContent = { Text("${money.format(item.amount)} đ", color = color, fontWeight = FontWeight.SemiBold) })
}

@Composable
fun Assets(modifier: Modifier, db: FinanceDb, totals: FinanceTotals, refresh: Int, money: NumberFormat, onChanged: () -> Unit) {
    val balances = remember(refresh) { db.accountBalances() }
    val savingsRecords = remember(refresh) { db.rawRecords("savings") }
    val goldRecords = remember(refresh) { db.rawRecords("gold") }
    val cashRecords = remember(refresh) { db.rawRecords("cash") }
    val savings = remember(savingsRecords) { savingsRecords.map { it.value } }
    val gold = remember(goldRecords) { goldRecords.map { it.value } }
    val cash = remember(cashRecords) { cashRecords.map { it.value } }
    val goldSummary = remember(gold) { calculateGoldFifo(gold) }
    var editRecord by remember { mutableStateOf<Pair<String, RawRecord?>?>(null) }
    var assetError by remember { mutableStateOf<String?>(null) }
    val activeSavings = remember(savingsRecords) { savingsRecords.filter { rec -> val v = rec.value.optJSONArray("values") ?: org.json.JSONArray(); val start = v.optString(5, ""); v.optDouble(2, 0.0) > 0.0 && v.optString(0, "").isNotBlank() && v.optString(1, "").isNotBlank() && start.length >= 10 && start.take(10).matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && v.optString(9) !in listOf("ĐÃ TẤT TOÁN", "ĐÃ TÁI TỤC") } }
    val cashTotal = remember(cash) { cash.drop(1).sumOf { row -> val v = row.optJSONArray("values") ?: org.json.JSONArray(); if (v.optDouble(0, 0.0) > 0) v.optDouble(2, v.optDouble(0) * v.optDouble(1) * 100.0) else 0.0 } }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("TÀI SẢN & CÔNG NỢ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { SummaryCard("Tài sản có", totals.assets, Color(0xFF16834A), Modifier.fillMaxWidth(), money) }
        item { SummaryCard("Tài sản nợ (giá trị âm)", totals.liabilities, Color(0xFFB45309), Modifier.fillMaxWidth(), money) }
        item { SummaryCard("Tài sản ròng", totals.netWorth, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth(), money) }
        item { Text("Số dư theo tài khoản", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(balances) { b -> ListItem(headlineContent = { Text(b.name) }, supportingContent = { Text("Mã ${b.code}") }, trailingContent = { Text("${money.format(b.balance)} đ", fontWeight = FontWeight.SemiBold) }) }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SỔ TIẾT KIỆM · ${activeSavings.size} sổ có dữ liệu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { editRecord = "savings" to null }) { Icon(Icons.Default.Add, null); Text("Thêm") }
        } }
        items(activeSavings) { rec ->
            val row = rec.value
            val v = row.optJSONArray("values") ?: org.json.JSONArray()
            val owner = v.optString(0, "")
            val name = v.optString(1, "Sổ tiết kiệm")
            val principal = v.optDouble(2, 0.0)
            val rate = v.optDouble(3, 0.0)
            val months = v.optDouble(4, 0.0)
            val maturity = v.optString(6, "").take(10)
            val type = v.optString(8, "").trim()
            val interest = if (principal > 0 && rate > 0 && months > 0) principal * rate * months / 12.0 else 0.0
            val maturityValue = if (type == "LÃI NHẬP GỐC") principal + interest else principal
            val maturityText = if (maturity.length == 10 && maturity != "00:00:00") "Đến hạn $maturity" else "Chưa có ngày đến hạn"
            ListItem(headlineContent = { Text("$owner · ${name.ifBlank { "Sổ tiết kiệm" }}") }, supportingContent = {
                Text("Gốc ${money.format(principal)} đ · ${(rate * 100).toString()}%/năm · ${months.toInt()} tháng\nDự tính lãi ${money.format(interest)} đ · $maturityText\n${if (type == "LÃI TRẢ TRC") "Lãi trả trước; dự kiến nhận lại gốc" else "Lãi nhập gốc; dự kiến cả gốc và lãi"}")
            }, trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(money.format(maturityValue) + " đ", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { editRecord = "savings" to rec }) { Icon(Icons.Default.Edit, "Sửa sổ") }
                IconButton(onClick = { runCatching { db.settleSavings(rec.id); onChanged() }.onFailure { assetError = it.message } }) { Icon(Icons.Default.CheckCircle, "Tất toán") }
                if (maturity.length == 10 && maturity <= LocalDate.now().toString()) IconButton(onClick = { runCatching { db.renewSavings(rec.id); onChanged() }.onFailure { assetError = it.message } }) { Icon(Icons.Default.Autorenew, "Tái tục") }
                IconButton(onClick = { db.deleteRawRecord("savings", rec.id); onChanged() }) { Icon(Icons.Default.Delete, "Xóa sổ") }
            } })
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VÀNG · ${goldSummary.transactions.size} giao dịch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { editRecord = "gold" to null }) { Icon(Icons.Default.Add, null); Text("Thêm") }
        } }
        item { Text("Tồn ${"%.3f".format(goldSummary.balance)} lượng · Giá vốn tồn ${money.format(goldSummary.remainingCost)} đ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        item { Text("Lãi/lỗ đã thực hiện theo FIFO: ${money.format(goldSummary.realizedProfit)} đ", style = MaterialTheme.typography.bodyMedium, color = if (goldSummary.realizedProfit >= 0) Color(0xFF16834A) else Color(0xFFB45309)) }
        if (goldSummary.unmatchedQuantity > 1e-8) item { Text("Cảnh báo: có ${"%.3f".format(goldSummary.unmatchedQuantity)} lượng bán vượt số vàng mua còn lại; lãi/lỗ FIFO không đáng tin cậy.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        items(goldSummary.transactions) { g ->
            ListItem(headlineContent = { Text("${g.date} · ${if (g.quantity >= 0) "Mua" else "Bán"} ${"%.3f".format(kotlin.math.abs(g.quantity))} lượng") },
                supportingContent = { Text("Đơn giá ${money.format(g.price)} đ/lượng · Giá vốn FIFO ${money.format(g.cost)} đ") },
                trailingContent = { if (g.quantity < 0) Text("${money.format(g.profit)} đ", color = if (g.profit >= 0) Color(0xFF16834A) else Color(0xFFB45309), fontWeight = FontWeight.SemiBold) })
        }
        item { Text("Sửa dữ liệu giao dịch vàng gốc", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
        items(goldRecords) { rec ->
            val v = rec.value.optJSONArray("values") ?: org.json.JSONArray()
            if (v.optDouble(1, 0.0) != 0.0) ListItem(headlineContent = { Text("${v.optString(0).take(10)} · ${v.optDouble(1)} lượng") }, supportingContent = { Text("Giá ${money.format(v.optDouble(2))} đ/lượng") }, trailingContent = {
                Row { IconButton(onClick = { editRecord = "gold" to rec }) { Icon(Icons.Default.Edit, "Sửa") }
                    IconButton(onClick = { db.deleteRawRecord("gold", rec.id); onChanged() }) { Icon(Icons.Default.Delete, "Xóa") } }
            })
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BẢNG KÊ TIỀN MẶT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { editRecord = "cash" to null }) { Icon(Icons.Default.Add, null); Text("Thêm") }
        } }
        items(cashRecords.drop(1)) { rec ->
            val row = rec.value
            val v = row.optJSONArray("values") ?: org.json.JSONArray()
            val denomination = v.optDouble(0, 0.0); val count = v.optDouble(1, 0.0); val total = v.optDouble(2, denomination * count)
            if (denomination > 0) ListItem(headlineContent = { Text("Mệnh giá ${money.format(denomination)} đ") }, supportingContent = { Text("Số lượng ${"%.2f".format(count)}") }, trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text("${money.format(total)} đ"); IconButton(onClick = { editRecord = "cash" to rec }) { Icon(Icons.Default.Edit, "Sửa") }; IconButton(onClick = { db.deleteRawRecord("cash", rec.id); onChanged() }) { Icon(Icons.Default.Delete, "Xóa") } } })
            else if (v.optString(0, "").isNotBlank()) ListItem(headlineContent = { Text(v.optString(0)) }, trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text("${money.format(total)} đ"); IconButton(onClick = { editRecord = "cash" to rec }) { Icon(Icons.Default.Edit, "Sửa") }; IconButton(onClick = { db.deleteRawRecord("cash", rec.id); onChanged() }) { Icon(Icons.Default.Delete, "Xóa") } } })
        }
        val extraCash = cash.drop(1).sumOf { row -> val v = row.optJSONArray("values") ?: org.json.JSONArray(); if (v.optDouble(0, 0.0) > 0) 0.0 else v.optDouble(2, 0.0) }
        item { Text("Tổng theo mệnh giá: ${money.format(cashTotal)} đ", fontWeight = FontWeight.SemiBold) }
        item { Text("Các khoản tiền khác: ${money.format(extraCash)} đ", fontWeight = FontWeight.SemiBold) }
        item { Text("Tổng bảng kê: ${money.format(cashTotal + extraCash)} đ", fontWeight = FontWeight.Bold) }
        item { Text("Lưu ý: lãi tiết kiệm là ước tính theo lãi đơn = gốc × lãi suất năm × kỳ hạn/12; chưa thay thế xác nhận lãi thực tế của ngân hàng. Vàng tính giá vốn FIFO theo thứ tự ngày giao dịch.", style = MaterialTheme.typography.bodySmall) }
    }
    if (editRecord != null) RawRecordDialog(editRecord!!.first, editRecord!!.second, onDismiss = { editRecord = null }, onSave = { table, id, sourceRow, values ->
        db.saveRawRecord(table, id, sourceRow, values)
        editRecord = null
        onChanged()
    })
    if (assetError != null) AlertDialog(onDismissRequest = { assetError = null }, title = { Text("Không thể thực hiện") }, text = { Text(assetError!!) }, confirmButton = { TextButton(onClick = { assetError = null }) { Text("Đóng") } })
}

data class GoldTxn(val date: String, val quantity: Double, val price: Double, val cost: Double, val profit: Double)
data class GoldSummary(val transactions: List<GoldTxn>, val balance: Double, val remainingCost: Double, val realizedProfit: Double, val unmatchedQuantity: Double)
private data class GoldLot(var quantity: Double, val unitCost: Double)

fun calculateGoldFifo(rows: List<org.json.JSONObject>): GoldSummary {
    val lots = mutableListOf<GoldLot>()
    val txns = mutableListOf<GoldTxn>()
    var realized = 0.0
    var unmatched = 0.0
    rows.sortedWith(compareBy<org.json.JSONObject> { it.optJSONArray("values")?.optString(0, "") ?: "" }.thenBy { it.optInt("sourceRow") }).forEach { row ->
        val v = row.optJSONArray("values") ?: org.json.JSONArray()
        val date = v.optString(0, "").take(10)
        val qty = v.optDouble(1, 0.0)
        val price = v.optDouble(2, 0.0)
        if (qty > 0 && price > 0) {
            lots.add(GoldLot(qty, price))
            txns.add(GoldTxn(date, qty, price, 0.0, 0.0))
        } else if (qty < 0 && price > 0) {
            var needed = -qty
            var cost = 0.0
            for (lot in lots) {
                if (needed <= 1e-9) break
                val take = minOf(needed, lot.quantity)
                cost += take * lot.unitCost
                lot.quantity -= take
                needed -= take
            }
            val proceeds = -qty * price
            unmatched += needed
            val profit = if (needed <= 1e-8) proceeds - cost else 0.0
            if (needed <= 1e-8) realized += profit
            txns.add(GoldTxn(date, qty, price, cost, profit))
        }
    }
    val remainingQty = lots.sumOf { it.quantity }
    val remainingCost = lots.sumOf { it.quantity * it.unitCost }
    return GoldSummary(txns, remainingQty, remainingCost, realized, unmatched)
}

@Composable
fun RawRecordDialog(table: String, record: RawRecord?, onDismiss: () -> Unit, onSave: (String, Long?, Int, org.json.JSONArray) -> Unit) {
    val labels = when (table) {
        "savings" -> listOf("Chủ sổ", "Tên sổ", "Tiền gốc", "Lãi suất năm (vd 0.05)", "Kỳ hạn (tháng)", "Ngày gửi YYYY-MM-DD", "Ngày đến hạn YYYY-MM-DD", "Tiến độ", "Loại hình (LÃI NHẬP GỐC/LÃI TRẢ TRC)", "Tình trạng")
        "gold" -> listOf("Ngày YYYY-MM-DD", "Khối lượng (+ mua, - bán)", "Đơn giá / lượng")
        else -> listOf("Mệnh giá / tên khoản tiền", "Số lượng", "Thành tiền")
    }
    val original = record?.value?.optJSONArray("values")
    val fields = remember(table, record?.id) { androidx.compose.runtime.mutableStateListOf<String>().apply {
        labels.indices.forEach { i -> add(original?.optString(i, "")?.takeUnless { it == "null" } ?: "") }
    } }
    var errorText by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (record == null) "Thêm ${when(table) { "savings" -> "sổ tiết kiệm"; "gold" -> "giao dịch vàng"; else -> "khoản tiền mặt" }}" else "Sửa dữ liệu") },
        text = { Column(Modifier.heightIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { i, label -> OutlinedTextField(value = fields[i], onValueChange = { fields[i] = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if (errorText != null) Text(errorText!!, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            runCatching {
                val values = org.json.JSONArray()
                fields.forEachIndexed { i, value ->
                    val numeric = when (table) {
                        "savings" -> i in setOf(2,3,4)
                        "gold" -> i in setOf(1,2)
                        else -> i in setOf(1,2) || (i == 0 && value.replace(",", "").toDoubleOrNull() != null)
                    }
                    if (numeric) values.put(if (value.isBlank()) JSONObject.NULL else value.replace(",", "").toDouble()) else values.put(value.ifBlank { JSONObject.NULL })
                }
                if (table == "cash" && fields[0].replace(",", "").toDoubleOrNull() != null && fields[1].replace(",", "").toDoubleOrNull() != null) {
                    values.put(2, fields[0].replace(",", "").toDouble() * fields[1].replace(",", "").toDouble() * 100.0)
                }
                if (table == "savings") {
                    val start = runCatching { LocalDate.parse(fields[5].take(10)) }.getOrNull()
                    val months = fields[4].toDoubleOrNull() ?: 0.0
                    require(start != null) { "Ngày gửi phải theo định dạng YYYY-MM-DD" }
                    require(months > 0 && (fields[3].toDoubleOrNull() ?: -1.0) >= 0) { "Lãi suất và kỳ hạn không hợp lệ" }
                    if (fields[6].isBlank() && start != null && months > 0) values.put(6, start.plusMonths(months.toLong()).toString())
                    if (fields[8].isBlank()) values.put(8, "LÃI NHẬP GỐC")
                    require(fields[8].isBlank() || fields[8].trim() in listOf("LÃI NHẬP GỐC", "LÃI TRẢ TRC")) { "Loại hình phải là LÃI NHẬP GỐC hoặc LÃI TRẢ TRC" }
                    if (fields[9].isBlank()) values.put(9, "ĐANG GỬI")
                }
                // Keep formula/result columns in the source gold sheet untouched.
                if (table == "gold" && original != null) for (i in labels.size until original.length()) values.put(original.get(i))
                if (table == "savings") require(fields[0].isNotBlank() && fields[1].isNotBlank() && (fields[2].toDoubleOrNull() ?: 0.0) > 0) { "Chủ sổ, tên sổ và tiền gốc là bắt buộc" }
                if (table == "gold") require(fields[0].length >= 10 && (fields[1].toDoubleOrNull() ?: 0.0) != 0.0 && (fields[2].toDoubleOrNull() ?: 0.0) > 0) { "Ngày, khối lượng khác 0 và đơn giá là bắt buộc" }
                val sourceRow = record?.value?.optInt("sourceRow") ?: 9999
                onSave(table, record?.id, sourceRow, values)
            }.onFailure { errorText = it.message ?: "Dữ liệu không hợp lệ" }
        }) { Text("Lưu") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } })
}

@Composable
fun SettingsPage(modifier: Modifier, db: FinanceDb, count: Int, onChanged: () -> Unit, backup: () -> Unit, restore: () -> Unit, importExcel: () -> Unit) {
    var addType by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingCode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("CÀI ĐẶT & DỮ LIỆU", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { ListItem(headlineContent = { Text("Dữ liệu đã nhập") }, supportingContent = { Text("$count dòng SỔ CÁI cùng danh mục, sổ tiết kiệm, vàng và tiền mặt") }, leadingContent = { Icon(Icons.Default.Storage, null) }) }
        item { OutlinedButton(onClick = backup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("Sao lưu dữ liệu JSON") } }
        item { OutlinedButton(onClick = restore, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("Khôi phục dữ liệu JSON") } }
        item { OutlinedButton(onClick = { confirmImport = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Nhập workbook Excel (.xlsx)") } }
        item { Text("Lưu ý: nhập Excel sẽ thay thế toàn bộ dữ liệu hiện tại. Hãy sao lưu JSON trước khi nhập.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        item { Text("TÙY CHỈNH DANH MỤC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Text("Thêm tài khoản hoặc danh mục mới. Mã phân cấp cần theo quy ước workbook: tài sản/thu bắt đầu bằng 1; nợ/chi bắt đầu bằng 2.", style = MaterialTheme.typography.bodySmall) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { name = ""; code = ""; addType = "accounts" }, modifier = Modifier.weight(1f)) { Text("Thêm tài khoản") }
            Button(onClick = { name = ""; code = ""; addType = "categories" }, modifier = Modifier.weight(1f)) { Text("Thêm thu/chi") }
        } }
        item { Text("Tài khoản (${db.names("accounts").size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(db.rawRecords("accounts")) { rec ->
            val vals = rec.value.optJSONArray("values") ?: org.json.JSONArray()
            val n = vals.optString(0); val c = vals.optString(1)
            ListItem(headlineContent = { Text(n) }, supportingContent = { Text("Mã $c") }, trailingContent = {
                Row { IconButton(onClick = { editingEntry = "accounts" to rec.id; editingName = n; editingCode = c }) { Icon(Icons.Default.Edit, "Sửa") }
                    IconButton(onClick = { runCatching { db.deleteCustomEntry("accounts", rec.id); onChanged() }.onFailure { message = it.message } }) { Icon(Icons.Default.Delete, "Xóa") } }
            })
        }
        item { Text("Danh mục thu chi (${db.names("categories").size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(db.rawRecords("categories")) { rec ->
            val vals = rec.value.optJSONArray("values") ?: org.json.JSONArray()
            val n = vals.optString(0); val c = vals.optString(1)
            ListItem(headlineContent = { Text(n) }, supportingContent = { Text("Mã $c") }, trailingContent = {
                Row { IconButton(onClick = { editingEntry = "categories" to rec.id; editingName = n; editingCode = c }) { Icon(Icons.Default.Edit, "Sửa") }
                    IconButton(onClick = { runCatching { db.deleteCustomEntry("categories", rec.id); onChanged() }.onFailure { message = it.message } }) { Icon(Icons.Default.Delete, "Xóa") } }
            })
        }
        item { Text("Phiên bản 0.4.0 · SQLite nội bộ · Không gửi dữ liệu lên máy chủ", style = MaterialTheme.typography.bodySmall) }
    }
    if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false }, title = { Text("Xác nhận nhập Excel") },
        text = { Text("Dữ liệu workbook sẽ thay thế toàn bộ dữ liệu đang có trong ứng dụng. Hãy sao lưu JSON trước khi tiếp tục.") },
        confirmButton = { TextButton(onClick = { confirmImport = false; importExcel() }) { Text("Chọn tệp Excel") } },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Hủy") } })
    if (addType != null) AlertDialog(
        onDismissRequest = { addType = null },
        title = { Text(if (addType == "accounts") "Thêm tài khoản" else "Thêm danh mục thu/chi") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên") }, singleLine = true)
            OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit) }, label = { Text("Mã phân cấp (3–8 chữ số)") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = {
            runCatching { db.addCustomEntry(addType!!, name, code) }
                .onSuccess { addType = null; onChanged() }
                .onFailure { message = it.message ?: "Không thể thêm" }
        }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = { addType = null }) { Text("Hủy") } }
    )
    if (editingEntry != null) AlertDialog(
        onDismissRequest = { editingEntry = null }, title = { Text("Sửa ${if (editingEntry!!.first == "accounts") "tài khoản" else "danh mục"}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = editingName, onValueChange = { editingName = it }, label = { Text("Tên") }, singleLine = true)
            OutlinedTextField(value = editingCode, onValueChange = { editingCode = it.filter(Char::isDigit) }, label = { Text("Mã") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { runCatching { db.updateCustomEntry(editingEntry!!.first, editingEntry!!.second, editingName, editingCode); editingEntry = null; onChanged() }.onFailure { message = it.message } }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = { editingEntry = null }) { Text("Hủy") } }
    )
    if (message != null) AlertDialog(onDismissRequest = { message = null }, title = { Text("Không thể lưu") }, text = { Text(message!!) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Đóng") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDialog(db: FinanceDb, tx: Tx?, onDismiss: () -> Unit, onSaved: () -> Unit, onError: (String) -> Unit) {
    var amount by remember(tx?.id) { mutableStateOf(tx?.amount?.toLong()?.toString() ?: "") }
    var date by remember(tx?.id) { mutableStateOf(tx?.date?.take(10) ?: LocalDate.now().toString()) }
    var sign by remember(tx?.id) { mutableDoubleStateOf(tx?.sign ?: 1.0) }
    val accounts = remember { db.names("accounts").filter { it !in listOf("TÀI SẢN CÓ", "TÀI SẢN NỢ") } }
    val categories = remember { db.names("categories").filter { it !in listOf("THU", "CHI") } }
    var account by remember(tx?.id) { mutableStateOf(tx?.account ?: accounts.firstOrNull().orEmpty()) }
    var category by remember(tx?.id) { mutableStateOf(tx?.category ?: categories.firstOrNull().orEmpty()) }
    var note by remember(tx?.id) { mutableStateOf(tx?.note ?: "") }
    var report by remember(tx?.id) { mutableStateOf(tx?.report != 0.0) }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (tx == null) "Thêm giao dịch" else "Sửa giao dịch") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sign > 0, onClick = { sign = 1.0 }, label = { Text("Tăng tài khoản") })
                FilterChip(selected = sign < 0, onClick = { sign = -1.0 }, label = { Text("Giảm tài khoản") })
            }
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text("Số tiền (VND)") }, singleLine = true)
            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Ngày YYYY-MM-DD") }, singleLine = true)
            ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = it }) {
                OutlinedTextField(value = account, onValueChange = {}, readOnly = true, label = { Text("Tài khoản") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) { accounts.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { account = it; accountExpanded = false }) } }
            }
            ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Danh mục thu/chi") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) { categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; categoryExpanded = false }) } }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Diễn giải") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = report, onCheckedChange = { report = it })
                Text("Tính vào báo cáo thu chi")
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val v = amount.replace(",", "").toDoubleOrNull()
            runCatching {
                require(v != null && v > 0) { "Số tiền không hợp lệ" }
                if (tx == null) db.addTransaction(date, v!!, sign, account, category.takeIf { it.isNotBlank() }, note, if (report) 1.0 else 0.0)
                else db.updateTransaction(tx.id, date, v!!, sign, account, category.takeIf { it.isNotBlank() }, note, if (report) 1.0 else 0.0)
            }.onSuccess { onSaved() }.onFailure { onError(it.message ?: "Không lưu được giao dịch") }
        }) { Text("Lưu") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } })
}
