package com.example.ui.screens

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.HistoryEvent
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SunsetOrange
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    events: List<HistoryEvent>,
    onDeleteEvent: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedActionFilter by remember { mutableStateOf("ALL") } // ALL, PURCHASE, SALE, RETURN, REPAIR_SENT, REPAIR_RETURNED

    var activeDetailEvent by remember { mutableStateOf<HistoryEvent?>(null) }
    var activePrintEvent by remember { mutableStateOf<HistoryEvent?>(null) }

    val filteredEvents = remember(events, searchQuery, selectedActionFilter) {
        events.filter { event ->
            val matchQuery = event.model.contains(searchQuery, ignoreCase = true) ||
                    event.name.contains(searchQuery, ignoreCase = true) ||
                    event.serialNumber.contains(searchQuery, ignoreCase = true) ||
                    (event.phoneNumber?.contains(searchQuery) ?: false)

            val matchAction = selectedActionFilter == "ALL" || event.actionType == selectedActionFilter

            matchQuery && matchAction
        }
    }

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search transactions...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("history_search_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            )
        )

        // Filters row
        val filters = listOf("ALL", "PURCHASE", "SALE", "RETURN", "REPAIR_SENT", "REPAIR_RETURNED")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                FilterChip(
                    selected = selectedActionFilter == filter,
                    onClick = { selectedActionFilter = filter },
                    label = { Text(filter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("history_filter_chip_$filter")
                )
            }
        }

        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "No Events",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No transactions recorded yet.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredEvents, key = { it.id }) { event ->
                    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    val dateFormatted = sdf.format(Date(event.timestamp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeDetailEvent = event }
                            .testTag("history_event_card_${event.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val actionColor = when (event.actionType) {
                                        "PURCHASE" -> EmeraldGreen
                                        "SALE" -> SunsetOrange
                                        "RETURN" -> AccentBlue
                                        "REPAIR_SENT", "REPAIR_RETURNED" -> Color(0xFFF1C40F)
                                        else -> Color.Gray
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(actionColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = event.actionType,
                                            color = actionColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = dateFormatted,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = event.model,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "To/From: ${event.name}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "INR ${String.format("%,.2f", event.amount)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { activePrintEvent = event },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                                            .testTag("button_print_${event.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Print Voucher",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { onDeleteEvent(event.id) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color.Red.copy(alpha = 0.1f), CircleShape)
                                            .testTag("button_delete_${event.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Event",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    activeDetailEvent?.let { event ->
        Dialog(onDismissRequest = { activeDetailEvent = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Transaction Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    DetailRow(label = "Action Type", value = event.actionType)
                    DetailRow(label = "Brand & Model", value = event.model)
                    DetailRow(label = "Customer/Supplier", value = event.name)
                    DetailRow(label = "Contact Phone", value = event.phoneNumber ?: "N/A")
                    DetailRow(label = "IMEI / Serial", value = event.serialNumber)
                    DetailRow(label = "Disbursed Amount", value = "INR ${String.format("%,.2f", event.amount)}")
                    DetailRow(label = "Aadhaar Number", value = event.aadhaarNumber ?: "N/A")
                    DetailRow(label = "Quantity", value = "${event.quantity} unit(s)")

                    val (addressVal, descVal) = extractAddressAndDescription(event.description)
                    DetailRow(label = "Address", value = addressVal.ifBlank { "N/A" })
                    DetailRow(label = "Description", value = descVal.ifBlank { "N/A" })

                    // Images render
                    val photoUrls = remember(event.photoUri) {
                        event.photoUri?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                    }

                    if (photoUrls.isNotEmpty()) {
                        Text(
                            text = "Attached Photos (${photoUrls.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            items(photoUrls) { url ->
                                Image(
                                    painter = rememberAsyncImagePainter(url),
                                    contentDescription = "Detail Photo",
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { activeDetailEvent = null }) {
                            Text("Close")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            activePrintEvent = event
                            activeDetailEvent = null
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Print")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print Voucher")
                        }
                    }
                }
            }
        }
    }

    // Custom Print Dialog
    activePrintEvent?.let { event ->
        CustomPrintDialog(
            event = event,
            onDismiss = { activePrintEvent = null },
            onPrint = { customText ->
                printHistoryEventCustom(context, event, customText)
                activePrintEvent = null
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPrintDialog(
    event: HistoryEvent,
    onDismiss: () -> Unit,
    onPrint: (String) -> Unit
) {
    val defaultTerms = remember(event.actionType, event.timestamp) {
        val sdfDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val formattedDateVal = sdfDate.format(Date(event.timestamp))
        when (event.actionType) {
            "SALE" -> {
                "उपरोक्त सभी तथ्य बिल्कुल सही है।\n" +
                "मैने ये मोबाइल आज पूरा चेक कर के मोबाइल गैलरी से लिया है और मैं इससे संतुष्ट हूँ।\n" +
                "अब से इस मोबाइल की सारी जिम्मेदारी केवल मेरी है।\n\n\n" +
                "Sign                     Date: $formattedDateVal\n\n" +
                "1. WARRANTY ASSISTANCE: No warranty/guarantee for the used phones. In case any phone is eligible, it will be told separately and shall be valid only if it is written on this paper.\n\n" +
                "2. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt at the sole discretion of the store."
            }
            "REPAIR_SENT", "REPAIR_RETURNED" -> {
                "उपरोक्त सभी तथ्य बिल्कुल सही है।\n" +
                "मैने आज ये मोबाइल जिसका मै खुद स्वामी हु, स्वेच्छा से मोबाइल गैलरी को दिया है।\n" +
                "उपरोक्त फोन पर किसी भी प्रकार का ऋण, ब्याज या क्लेम बाकी नहीं है। इसका किसी भी लोन/फाइनेंस कंपनी से कोई संबंध नहीं है। यदि इसपे कोई लोन रिकवरी होती है तो उसकी सारी जिम्मेदारी मेरी होगी और किसी की नहीं होगी।\n" +
                "आज से इस फोन का मालिक मै नहीं हू।\n\n\n" +
                "Sign                     Date: $formattedDateVal\n\n" +
                "1. Seller/Customer is solely responsible for the all the previous repairs, finances and other tasks related to this phone. The buyer-store does not have any responsibility of any finance emi's or and any wrong doings in the past. Any EMIs due on this phone shall be paid by the seller-customer. Buyer can independently format it now.\n\n" +
                "2. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt at the sole discretion of the store.\n\n" +
                "3. OUT-FOR-REPAIR DEVICES: Repair hand-overs are registered entirely at client's risk. Please backup/clone personal user files. Retailer is not liable for data loss or software degradation during repair."
            }
            else -> { // PURCHASE or RETURN or fallback
                "उपरोक्त सभी तथ्य बिल्कुल सही है।\n" +
                "मैने आज ये मोबाइल जिसका मै खुद स्वामी हू, स्वेच्छा से मोबाइल गैलरी को दिया है।\n" +
                "उपरोक्त फोन पर किसी भी प्रकार का ऋण, ब्याज या क्लेम बाकी नहीं है। इसका किसी भी लोन/फाइनेंस कंपनी से कोई संबंध नहीं है। यदि इसपे कोई लोन रिकवरी होती है तो उसकी सारी जिम्मेदारी मेरी होगी और किसी की नहीं होगी ।\n" +
                "आज से इस फोन का मालिक मै नहीं हू।\n\n\n" +
                "Sign                     Date: $formattedDateVal\n\n" +
                "1. Seller/Customer is solely responsible for the all the previous repairs, finances and other tasks related to this phone. The buyer-store does not have any responsibility of any finance emi's or and any wrong doings in the past. Any EMIs due on this phone shall be paid by the seller-customer. Buyer can independently format it now.\n\n" +
                "2. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt at the sole discretion of the store."
            }
        }
    }

    var termsText by remember { mutableStateOf(defaultTerms) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Voucher Terms",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = termsText,
                    onValueChange = { termsText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .testTag("print_terms_textarea"),
                    label = { Text("Terms & Signature Block") },
                    maxLines = 15
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onPrint(termsText) }, modifier = Modifier.testTag("print_action_proceed")) {
                        Text("Print Now")
                    }
                }
            }
        }
    }
}

fun printHistoryEventCustom(
    context: Context,
    event: HistoryEvent,
    customText: String
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "${event.actionType}_Voucher_${event.serialNumber}"

    val webView = WebView(context)
    val (addressVal, _) = extractAddressAndDescription(event.description)

    // Format photo Html
    val loadedPhotos = event.photoUri?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    var photosHtml = ""
    if (loadedPhotos.isNotEmpty()) {
        val imgElements = loadedPhotos.joinToString("") { url ->
            "<img src=\"$url\" style=\"max-width: 250px; max-height: 250px; margin: 10px; border: 1.5px solid #eaeaea; border-radius: 6px; object-fit: cover;\" />"
        }
        photosHtml = """
            <div class="photos-section" style="margin-top: 40px; border-top: 1.5px solid #ddd; padding-top: 15px;">
                <h3 style="font-size: 13px; color: #111; margin-bottom: 12px; font-weight: bold; text-align: left;">ATTACHED PHOTOS</h3>
                <div style="display: flex; flex-wrap: wrap; justify-content: flex-start;">
                    $imgElements
                </div>
            </div>
        """.trimIndent()
    }

    val actionLabel = when (event.actionType) {
        "PURCHASE" -> "Product Purchase Voucher"
        "SALE" -> "Sales Delivery Invoice"
        "RETURN" -> "Returns / Ledger Declaration"
        "REPAIR_SENT" -> "Repair Sent Intake"
        "REPAIR_RETURNED" -> "Repair Returned To Client"
        else -> "${event.actionType.replace("_", " ")} Declaration"
    }

    val htmlDocument = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body {
                    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                    padding: 30px;
                    background: #fff;
                    color: #000;
                    margin: 0;
                    font-size: 12px;
                }
                .header {
                    text-align: center;
                    border-bottom: 2px solid #000;
                    padding-bottom: 12px;
                    margin-bottom: 24px;
                }
                .header h1 {
                    font-size: 26px;
                    margin: 0;
                    font-weight: 800;
                    letter-spacing: 1.5px;
                }
                .header h2 {
                    font-size: 14px;
                    margin: 4px 0 0 0;
                    color: #444;
                    font-weight: bold;
                }
                .voucher-title {
                    text-align: right;
                    color: #444;
                }
                table.meta-table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 16px;
                }
                table.meta-table td {
                    padding: 6px 8px;
                    font-size: 11px;
                    border-bottom: 1px dotted #ccc;
                }
                table.meta-table td.label {
                    font-weight: bold;
                    color: #111;
                    width: 140px;
                }
                .terms-block {
                    font-size: 11px;
                    margin-top: 16px;
                    margin-bottom: 24px;
                    color: #111;
                    white-space: pre-wrap;
                    line-height: 1.5;
                }
                @media print {
                    body { padding: 0; margin: 0; }
                    .header h1 { font-size: 24px; }
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>MOBILE GALLERY</h1>
                <h2>$actionLabel</h2>
            </div>
            
            <table class="meta-table">
                <tr>
                    <td class="label" style="width: 15%;">Action Mode:</td>
                    <td style="font-weight: bold; color: #111; width: 35%;">${event.actionType}</td>
                    <td class="label" style="width: 15%;">Brand & Model:</td>
                    <td style="width: 35%;">${event.model.ifBlank { "________________" }}</td>
                </tr>
                <tr>
                    <td class="label">Customer Name:</td>
                    <td>${event.name.ifBlank { "_____________________________" }}</td>
                    <td class="label">Contact Phone:</td>
                    <td>${event.phoneNumber ?: "_____________________________"}</td>
                </tr>
                <tr>
                    <td class="label">IMEI / Serial key:</td>
                    <td style="font-family: monospace;">${event.serialNumber.ifBlank { "________________" }}</td>
                    <td class="label">Disbursed Amount:</td>
                    <td style="font-weight: bold; color: #111;">INR ${String.format("%,.2f", event.amount)}</td>
                </tr>
                <tr>
                    <td class="label">Aadhaar Number:</td>
                    <td style="font-family: monospace;">${event.aadhaarNumber?.ifBlank { "_____________________________" } ?: "_____________________________"}</td>
                    <td class="label">Quantity / Qty:</td>
                    <td>${event.quantity} unit(s)</td>
                </tr>
                <tr>
                    <td class="label">Address:</td>
                    <td colspan="3">${addressVal.ifBlank { "_____________________________" }}</td>
                </tr>
            </table>

            <div class="terms-block">
                $customText
            </div>

            $photosHtml
        </body>
        </html>
    """.trimIndent()

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL("file:///android_asset/", htmlDocument, "text/html", "UTF-8", null)
}

fun extractAddressAndDescription(desc: String?): Pair<String, String> {
    if (desc == null) return Pair("", "")
    val trimmed = desc.trim()
    if (trimmed.startsWith("Address: ")) {
        val newlineIdx = trimmed.indexOf("\n")
        if (newlineIdx != -1) {
            val addressVal = trimmed.substring(0, newlineIdx).replace("Address: ", "").trim()
            val descVal = trimmed.substring(newlineIdx + 1).trim()
            return Pair(addressVal, descVal)
        } else {
            val addressVal = trimmed.replace("Address: ", "").trim()
            return Pair(addressVal, "")
        }
    }
    return Pair("", trimmed)
}
