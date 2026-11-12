package ai.workis.jowi.ui.screens.partner

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

/**
 * The label's QR through Google's code scanner — it draws its own camera UI, so the app asks
 * no camera permission. A cancelled scan says nothing; a scanner that cannot run calls
 * [onUnavailable].
 */
fun scanLabel(context: Context, onUnavailable: () -> Unit, onRead: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { b -> b.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let(onRead) }
        .addOnFailureListener { onUnavailable() }
}

/**
 * The ONE pickup sheet (ledger 89, 2026-11-11): a pickup is recorded only with the parcel's
 * label code — read by the camera or typed by the courier. [number] and [company] only say
 * which parcel this is; the code is NEVER filled from the shipment's own number.
 *
 * iOS shows the camera inline; here the scanner is Google's own screen (no camera permission):
 * it opens by itself when the sheet arrives without a code, and "Etiketi okutun" opens it again.
 * One code, one way: a read code stands in the scanner's place ("Etiket okundu · CODE" +
 * "Yeniden okutun"), typing drops it. [submit] posts `{code, via}` (`camera` | `typed`) and
 * answers null when the pickup is recorded, else the server's sentence — printed here as
 * sent, the sheet stays open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupSheet(
    number: String,
    company: String?,
    readCode: String? = null,
    onClose: () -> Unit,
    submit: suspend (code: String, via: String) -> String?,
) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var read by remember { mutableStateOf(readCode?.trim()?.takeIf { it.isNotEmpty() }) }
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraOff by remember { mutableStateOf(false) }
    val scan = {
        cameraOff = false
        scanLabel(context, onUnavailable = { cameraOff = true }) { code ->
            read = code; typed = ""; error = null
        }
    }
    LaunchedEffect(Unit) { if (read == null) scan() }
    val code = read ?: typed.trim()

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.cargo_pickup_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = colors.ink)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (number.isNotEmpty()) Text(number, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
                company?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = colors.muted) }
            }

            val shape = RoundedCornerShape(16.dp)
            val got = read
            if (got != null) {
                Column(
                    Modifier.fillMaxWidth().background(colors.canvas, shape).border(1.dp, colors.border, shape).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(WorkisIcons.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cargo_pickup_read), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
                    }
                    Text(got, fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.ink)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !busy) { read = null; error = null; scan() }.padding(vertical = 4.dp),
                    ) {
                        Icon(WorkisIcons.QrScan, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.cargo_pickup_rescan), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.accentText)
                    }
                }
            } else {
                // bordered: "Teslim aldım" stays the sheet's one fill
                OutlinedButton(onClick = { scan() }, enabled = !busy, shape = shape, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(WorkisIcons.QrScan, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cargo_act_scan), fontWeight = FontWeight.Medium, fontSize = 15.sp, color = colors.ink)
                }
                if (cameraOff) Text(stringResource(R.string.cargo_camera_unavailable), fontSize = 13.sp, color = colors.muted)
            }

            Text(stringResource(R.string.cargo_pickup_type_code), fontSize = 13.sp, color = colors.muted)
            OutlinedTextField(
                value = typed,
                onValueChange = { v ->
                    typed = v.uppercase().take(40)
                    // one code, one way: typing drops the read one
                    if (v.isNotEmpty()) read = null
                    error = null
                },
                singleLine = true,
                placeholder = { Text("WSH-…", color = colors.faint, fontFamily = WorkisMono, fontSize = 18.sp) },
                textStyle = TextStyle(fontFamily = WorkisMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.ink).readingStep(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, cursorColor = colors.blue),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, fontSize = 13.sp, color = colors.danger) }
            Button(
                onClick = {
                    val via = if (read != null) "camera" else "typed"
                    busy = true; error = null
                    scope.launch {
                        val refusal = submit(code, via)
                        busy = false
                        if (refusal == null) onClose() else error = refusal
                    }
                },
                enabled = code.isNotEmpty() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill, disabledContainerColor = colors.canvas, disabledContentColor = colors.faint),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                else Text(stringResource(R.string.cargo_pickup_confirm), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
