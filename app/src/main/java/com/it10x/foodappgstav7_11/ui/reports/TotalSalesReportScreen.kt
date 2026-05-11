package com.it10x.foodappgstav7_11.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.it10x.foodappgstav7_11.ui.pos.SummaryRow
import com.it10x.foodappgstav7_11.viewmodel.OnlineReportsViewModel
import androidx.compose.runtime.getValue
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.it10x.foodappgstav7_11.printer.PrintJob
import com.it10x.foodappgstav7_11.printer.PrinterManager
import java.text.SimpleDateFormat
import java.util.*
@Composable
fun TotalSalesReportScreen(
    viewModel: OnlineReportsViewModel,
    onBack: () -> Unit,
    onHistoryCategoryReport: () -> Unit,
    onHistoryProductReport: () -> Unit,
    onHistoryCategoryProductReport: () -> Unit,
) {

    val context = LocalContext.current

    val totalSales by viewModel.totalSales.collectAsState()
    val totalDiscount by viewModel.totalDiscount.collectAsState()
    val totalBefore by viewModel.totalBeforeDiscount.collectAsState()
    val loading by viewModel.loading.collectAsState()

    // ✅ DEFAULT TODAY RANGE
    val startCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val endCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }

    var startDate by remember { mutableStateOf(startCalendar.timeInMillis) }
    var endDate by remember { mutableStateOf(endCalendar.timeInMillis) }

    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val totalTax by viewModel.totalTax.collectAsState()
    Column(modifier = Modifier.padding(16.dp)) {



        Spacer(Modifier.height(12.dp))

        // 🔥 DATE ROW (same style as category screen)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }


            // START DATE
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance()

                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val selected = Calendar.getInstance()
                            selected.set(y, m, d, 0, 0, 0)
                            selected.set(Calendar.MILLISECOND, 0)
                            startDate = selected.timeInMillis
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            ) {
                Text(dateFormatter.format(Date(startDate)))
            }

            // END DATE
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance()

                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val selected = Calendar.getInstance()
                            selected.set(y, m, d, 23, 59, 59)
                            selected.set(Calendar.MILLISECOND, 999)
                            endDate = selected.timeInMillis
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            ) {
                Text(dateFormatter.format(Date(endDate)))
            }

            // LOAD BUTTON
            Button(
                onClick = {
                    viewModel.loadTotalSalesReport(startDate, endDate)
                }
            ) {
                Text("Load")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    onHistoryCategoryReport()
                }
            ) {
                Text("Categorys Report")
            }
            Button(
                onClick = {
                    onHistoryProductReport()
                }
            ) {
                Text("Products Report")
            }


            Button(
                onClick = {
                    onHistoryCategoryProductReport()
                }
            ) {
                Text("Category Products Report")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Total Sales Report", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        // 🔥 RESULT
        when {
            loading -> Text("Loading...")

            else -> {
                SummaryRow("Before Discount", totalBefore)
                SummaryRow("Discount", totalDiscount)
                SummaryRow("After Discount", totalSales)
                SummaryRow("Tax", totalTax)
            }
        }


        val printer = remember { PrinterManager.getInstance(context) }

        if (!loading) {
            Button(
                onClick = {
                    printer.print(
                        PrintJob.TotalSalesReport(
                            beforeDiscount = totalBefore,
                            discount = totalDiscount,
                            afterDiscount = totalSales,
                            tax = totalTax,
                            fromMillis = startDate,
                            toMillis = endDate
                        )
                    )
                }
            ) {
                Text("Print")
            }
        }



    }
}