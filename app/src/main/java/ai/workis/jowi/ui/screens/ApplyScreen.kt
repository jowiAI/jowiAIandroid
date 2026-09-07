package ai.workis.jowi.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApplySubmitBody
import ai.workis.jowi.data.applyErrorMessage
import ai.workis.jowi.data.errorCodeFromBody
import ai.workis.jowi.data.statusMessage
import ai.workis.jowi.ui.components.AgreementLoader
import ai.workis.jowi.ui.components.AgreementPaper
import ai.workis.jowi.ui.components.MarkdownText
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.WorkisIcons
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
    /** The expert door (a person, not a company — own screen, not a wizard step). */
    var showExpert by mutableStateOf(false)

    /** Step-04 paper text, driven by the chosen role (carrier signs the carrier text). */
    val agreement = AgreementLoader(viewModelScope)

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

    /** Selecting IS proceeding — and the paper for that role starts prefetching. */
    fun chooseRole(value: String) {
        role = value
        agreement.loadRole(value)
        step = ApplyStep.Tax
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

    if (vm.showExpert) {
        ExpertApplyScreen(onClose = { vm.showExpert = false })
        return
    }

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
            .statusBarsPadding()
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
                    .clickable { vm.chooseRole(value) } // tap = select + advance (iOS behavior)
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
        Spacer(Modifier.height(2.dp))
        ExpertCard { vm.showExpert = true }
    }
}

/**
 * The expert door: same card shape as the roles, but visibly a different KIND
 * of door — a DASHED neutral border (a solid coloured border means "selected"
 * on this page, so this card never gets one), graduation cap + chevron in blue.
 */
@Composable
private fun ExpertCard(onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val dash = colors.faint
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(
            WorkisIcons.GraduationCap, contentDescription = null,
            tint = colors.blue, modifier = Modifier.size(20.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.expert_card_title),
                color = colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.expert_card_sub), color = colors.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Icon(
            Icons.Filled.KeyboardArrowRight, contentDescription = null,
            tint = colors.blue, modifier = Modifier.size(20.dp),
        )
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
    LaunchedEffect(vm.role) { vm.role?.let { vm.agreement.loadRole(it) } }

    Text(
        stringResource(R.string.apply_step4_sub),
        color = colors.muted, fontSize = 14.sp, lineHeight = 20.sp,
    )
    Spacer(Modifier.height(12.dp))
    // Which text is shown comes from ?role= — a carrier signs the carrier PDF.
    AgreementPaper(
        loader = vm.agreement,
        signerName = vm.signerName,
        onSignerChange = { vm.signerName = it },
    )
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(
            checked = vm.agree,
            onCheckedChange = { vm.agree = it },
            colors = CheckboxDefaults.colors(
                checkedColor = Kiremit500,
                checkmarkColor = OnKiremitFill,
            ),
        )
        // markdown links (agreement page, privacy, PDF) open in-app
        MarkdownText(
            stringResource(R.string.apply_agree),
            color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
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
