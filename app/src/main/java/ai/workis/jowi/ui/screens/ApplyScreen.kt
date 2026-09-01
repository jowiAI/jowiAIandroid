package ai.workis.jowi.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApplySubmitBody
import ai.workis.jowi.data.applyErrorMessage
import ai.workis.jowi.data.errorCodeFromBody
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream

enum class ApplyStep { Intro, Role, Tax, Email, Sign, Success }

class ApplyViewModel : ViewModel() {
    var step by mutableStateOf(ApplyStep.Intro)
    var role by mutableStateOf<String?>(null) // buyer | maker | both | carrier

    var company by mutableStateOf("")
    var shortName by mutableStateOf("")
    var taxNumber by mutableStateOf("")
    var taxOffice by mutableStateOf("")
    var city by mutableStateOf("")
    var address by mutableStateOf("")
    var entityType by mutableStateOf<String?>(null)
    var confidence by mutableStateOf<Double?>(null)
    var showTaxFields by mutableStateOf(false)

    var email by mutableStateOf("")
    var sector by mutableStateOf("")
    var signerName by mutableStateOf("")
    var agree by mutableStateOf(false)

    var taxFileBytes: ByteArray? = null
    var taxFileName: String? = null
    var taxFileMime: String? = null

    var parsing by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)

    private val lang get() = Graph.language.value

    fun back() {
        error = null
        step = when (step) {
            ApplyStep.Role -> ApplyStep.Intro
            ApplyStep.Tax -> ApplyStep.Role
            ApplyStep.Email -> ApplyStep.Tax
            ApplyStep.Sign -> ApplyStep.Email
            else -> step
        }
    }

    fun parse(bytes: ByteArray, name: String, mime: String) {
        taxFileBytes = bytes; taxFileName = name; taxFileMime = mime
        parsing = true
        error = null
        viewModelScope.launch {
            try {
                val part = MultipartBody.Part.createFormData(
                    "file", name, bytes.toRequestBody(mime.toMediaType()),
                )
                val r = Graph.api.applyParse(part)
                if (r.success && r.data != null) {
                    company = r.data.company_name ?: company
                    shortName = r.data.short_name ?: shortName
                    taxNumber = r.data.tax_number ?: taxNumber
                    taxOffice = r.data.tax_office ?: taxOffice
                    address = r.data.business_address ?: address
                    city = r.data.city ?: city
                    entityType = r.data.entity_type ?: entityType
                    confidence = r.confidence
                } else {
                    // never a dead end — surface the message, keep the manual path
                    error = applyErrorMessage(r.error, lang)
                }
            } catch (e: HttpException) {
                error = applyErrorMessage(errorCodeFromBody(e), lang)
            } catch (e: Exception) {
                error = statusMessage(null, lang)
            }
            showTaxFields = true
            parsing = false
        }
    }

    fun submit() {
        if (submitting) return
        submitting = true
        error = null
        viewModelScope.launch {
            try {
                val body = ApplySubmitBody(
                    role = role ?: "buyer",
                    company = company.trim(),
                    email = email.trim(),
                    agree = true,
                    signerName = signerName.trim(),
                    taxNumber = taxNumber.ifBlank { null },
                    taxOffice = taxOffice.ifBlank { null },
                    entityType = entityType,
                    address = address.ifBlank { null },
                    city = city.ifBlank { null },
                    sector = sector.ifBlank { null },
                    lang = lang,
                    shortName = shortName.ifBlank { null },
                    confidence = confidence,
                )
                val file = taxFileBytes
                val r = if (file != null) {
                    val fields = buildMap<String, RequestBody> {
                        fun putText(k: String, v: String?) {
                            if (!v.isNullOrBlank()) put(k, v.toRequestBody("text/plain".toMediaType()))
                        }
                        putText("role", body.role); putText("company", body.company)
                        putText("email", body.email); putText("agree", "true")
                        putText("signerName", body.signerName)
                        putText("taxNumber", body.taxNumber); putText("taxOffice", body.taxOffice)
                        putText("entityType", body.entityType); putText("address", body.address)
                        putText("city", body.city); putText("sector", body.sector)
                        putText("lang", body.lang); putText("shortName", body.shortName)
                        body.confidence?.let { putText("confidence", it.toString()) }
                    }
                    val part = MultipartBody.Part.createFormData(
                        "taxFile", taxFileName ?: "tax.jpg",
                        file.toRequestBody((taxFileMime ?: "image/jpeg").toMediaType()),
                    )
                    Graph.api.applySubmitWithFile(fields, part)
                } else {
                    Graph.api.applySubmit(body)
                }
                if (r.success) {
                    successMessage = r.message
                    step = ApplyStep.Success
                } else {
                    error = applyErrorMessage(r.error, lang)
                }
            } catch (e: HttpException) {
                error = applyErrorMessage(errorCodeFromBody(e), lang)
            } catch (e: Exception) {
                error = statusMessage(null, lang)
            }
            submitting = false
        }
    }
}

private const val MAX_FILE_BYTES = 5 * 1024 * 1024

/** Camera/gallery picks re-encode (long edge ≤2000 px, JPEG 0.8); PDFs gate at 5 MB. */
private fun prepareFile(bytes: ByteArray, mime: String): Triple<ByteArray, String, String>? {
    if (mime == "application/pdf") {
        return if (bytes.size <= MAX_FILE_BYTES) Triple(bytes, "tax.pdf", mime) else null
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val longEdge = maxOf(bitmap.width, bitmap.height)
    val scaled = if (longEdge > 2000) {
        val f = 2000f / longEdge
        Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * f).toInt(), (bitmap.height * f).toInt(), true,
        )
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
    val jpeg = out.toByteArray()
    return if (jpeg.size <= MAX_FILE_BYTES) Triple(jpeg, "tax.jpg", "image/jpeg") else null
}

@Composable
fun ApplyScreen(onClose: () -> Unit, vm: ApplyViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val prepared = prepareFile(bytes, mime)
        if (prepared == null) {
            vm.error = applyErrorMessage("bad_file", Graph.language.value)
        } else {
            vm.parse(prepared.first, prepared.second, prepared.third)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 24.dp)
            .imePadding(),
    ) {
        Spacer(Modifier.height(16.dp))
        // header pattern: back-circle top-left
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (vm.step == ApplyStep.Intro || vm.step == ApplyStep.Success) onClose() else vm.back() },
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.border, CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.ink,
                )
            }
            Spacer(Modifier.weight(1f))
            stepLabel(vm.step)?.let {
                Text(
                    stringResource(it),
                    fontFamily = WorkisMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = colors.muted,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (vm.step) {
                ApplyStep.Intro -> IntroStep(vm)
                ApplyStep.Role -> RoleStep(vm)
                ApplyStep.Tax -> TaxStep(vm) { picker.launch(arrayOf("application/pdf", "image/*")) }
                ApplyStep.Email -> EmailStep(vm)
                ApplyStep.Sign -> SignStep(vm)
                ApplyStep.Success -> SuccessStep(vm, onClose)
            }

            vm.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = colors.danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun stepLabel(step: ApplyStep): Int? = when (step) {
    ApplyStep.Role -> R.string.tl1t
    ApplyStep.Tax -> R.string.tl2t
    ApplyStep.Email -> R.string.tl3t
    ApplyStep.Sign -> R.string.tl4t
    else -> null
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, loading: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Kiremit400,
            contentColor = OnKiremitFill,
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = OnKiremitFill)
        } else {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun applyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Kiremit500,
    unfocusedBorderColor = WorkisTheme.colors.border,
    focusedTextColor = WorkisTheme.colors.ink,
    unfocusedTextColor = WorkisTheme.colors.ink,
    cursorColor = WorkisTheme.colors.blue,
)

@Composable
private fun ApplyField(value: String, onChange: (String) -> Unit, hintRes: Int, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(stringResource(hintRes), color = WorkisTheme.colors.faint) },
        label = { Text(stringResource(hintRes), color = WorkisTheme.colors.muted, fontSize = 12.sp) },
        singleLine = minLines == 1,
        minLines = minLines,
        shape = RoundedCornerShape(20.dp),
        colors = applyFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
private fun IntroStep(vm: ApplyViewModel) {
    val colors = WorkisTheme.colors
    Spacer(Modifier.height(24.dp))
    WorkisMark(size = 30)
    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(R.string.apply_title),
        style = MaterialTheme.typography.headlineMedium,
        color = colors.ink,
    )
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.apply_intro_sub), color = colors.muted, fontSize = 15.sp, lineHeight = 22.sp)
    Spacer(Modifier.height(28.dp))
    PrimaryButton(stringResource(R.string.next), enabled = true) { vm.step = ApplyStep.Role }
}

@Composable
private fun RoleStep(vm: ApplyViewModel) {
    val colors = WorkisTheme.colors
    val roles = listOf(
        Triple("buyer", R.string.role_buyer, R.string.role_buyer_sub),
        Triple("maker", R.string.role_maker, R.string.role_maker_sub),
        Triple("both", R.string.role_both, R.string.role_both_sub),
        Triple("carrier", R.string.role_carrier, R.string.role_carrier_sub),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        roles.forEach { (value, titleRes, descRes) ->
            val selected = vm.role == value
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(20.dp))
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) Kiremit500 else colors.border,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable {
                        // tap = select + advance (iOS behavior)
                        vm.role = value
                        vm.step = ApplyStep.Tax
                    }
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(titleRes),
                    color = colors.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(descRes), color = colors.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TaxStep(vm: ApplyViewModel, onPick: () -> Unit) {
    val colors = WorkisTheme.colors
    if (vm.parsing) {
        Spacer(Modifier.height(60.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WorkisMark(size = 26, breathing = true)
        }
        return
    }
    if (!vm.showTaxFields) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.apply_pick_sub), color = colors.muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.apply_pick_file), enabled = true, onClick = onPick)
        TextButton(
            onClick = { vm.showTaxFields = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.apply_manual), color = colors.accentText, fontSize = 14.sp) }
    } else {
        ApplyField(vm.company, { vm.company = it }, R.string.f_company)
        ApplyField(vm.shortName, { vm.shortName = it }, R.string.f_short_name)
        ApplyField(vm.taxNumber, { vm.taxNumber = it }, R.string.f_tax_number)
        ApplyField(vm.taxOffice, { vm.taxOffice = it }, R.string.f_tax_office)
        ApplyField(vm.city, { vm.city = it }, R.string.f_city)
        ApplyField(vm.address, { vm.address = it }, R.string.f_address, minLines = 2)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            stringResource(R.string.next),
            enabled = vm.company.isNotBlank(),
        ) { vm.error = null; vm.step = ApplyStep.Email }
        TextButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.apply_pick_file), color = colors.accentText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmailStep(vm: ApplyViewModel) {
    ApplyField(vm.email, { vm.email = it }, R.string.email_placeholder)
    ApplyField(vm.sector, { vm.sector = it }, R.string.apply_sector_hint)
    Spacer(Modifier.height(12.dp))
    PrimaryButton(
        stringResource(R.string.next),
        enabled = vm.email.contains("@"),
    ) { vm.error = null; vm.step = ApplyStep.Sign }
}

@Composable
private fun SignStep(vm: ApplyViewModel) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current

    TextButton(onClick = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "https://workis.ai/sozlesme/tedarikci/pdf/".toUri()),
        )
    }) {
        Text(stringResource(R.string.apply_view_pdf), color = colors.accentText, fontSize = 14.sp)
    }
    Spacer(Modifier.height(8.dp))

    ApplyField(vm.signerName, { vm.signerName = it }, R.string.sign_cell_label)
    if (vm.signerName.isNotBlank()) {
        // typed name previews as handwriting on the sign-here band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                vm.signerName,
                fontFamily = FontFamily.Cursive,
                fontStyle = FontStyle.Italic,
                fontSize = 26.sp,
                color = colors.ink,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = vm.agree,
            onCheckedChange = { vm.agree = it },
            colors = CheckboxDefaults.colors(
                checkedColor = Kiremit500,
                checkmarkColor = OnKiremitFill,
            ),
        )
        Text(stringResource(R.string.apply_agree), color = colors.ink, fontSize = 13.sp)
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        stringResource(R.string.apply_submit),
        enabled = vm.agree && vm.signerName.isNotBlank(),
        loading = vm.submitting,
    ) { vm.submit() }
}

@Composable
private fun SuccessStep(vm: ApplyViewModel, onClose: () -> Unit) {
    val colors = WorkisTheme.colors
    Spacer(Modifier.height(40.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.apply_sent_title),
            style = MaterialTheme.typography.headlineMedium,
            fontSize = 22.sp,
            color = colors.ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            vm.successMessage ?: stringResource(R.string.apply_sent),
            color = colors.muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton(stringResource(R.string.done), enabled = true, onClick = onClose)
    }
}
