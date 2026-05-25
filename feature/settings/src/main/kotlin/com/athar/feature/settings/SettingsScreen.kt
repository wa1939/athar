package com.athar.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.athar.core.domain.repo.BackfillProgress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.display.CurrencyCatalog
import com.athar.core.designsystem.theme.AtharTheme

@Composable
fun SettingsScreen(
    onOpenCategories: () -> Unit = {},
    onOpenSmsAudit: () -> Unit = {},
    onOpenActivityLog: () -> Unit = {},
    onOpenUserTemplates: () -> Unit = {},
    onOpenRecurringRules: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onApplyLocale: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val theme = AtharTheme
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var smsGranted by remember { mutableStateOf(hasSmsPermissions(context)) }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val backfill by viewModel.backfillProgress.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsGranted = hasSmsPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        smsGranted = results.values.all { it }
    }

    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val csvStatus by viewModel.csvStatus.collectAsStateWithLifecycle()
    val hijriEnabled by viewModel.hijriEnabled.collectAsStateWithLifecycle()
    val rescanStatus by viewModel.rescanStatus.collectAsStateWithLifecycle()
    val ownAccounts by viewModel.ownAccountNumbers.collectAsStateWithLifecycle()
    val displayCurrency by viewModel.displayCurrency.collectAsStateWithLifecycle()
    val appLocale by viewModel.appLocale.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) pendingExportUri = uri
    }
    val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) pendingImportUri = uri
    }
    val csvLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) viewModel.importCsv(context.contentResolver, uri)
    }
    val csvExportLauncher = rememberLauncherForActivityResult(CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(context.contentResolver, uri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.l),
        ) {
            AtharText(text = "الإعدادات", style = theme.typography.overline, color = theme.colors.muted)

            SmsPermissionCard(
                granted = smsGranted,
                onGrant = { smsLauncher.launch(SMS_PERMISSIONS) },
            )

            if (smsGranted) {
                BackfillCard(
                    progress = backfill,
                    onRescan = viewModel::runBackfill,
                )
            }

            RescanAndCleanCard(
                status = rescanStatus,
                onRescan = viewModel::rescanAndClean,
                onClearStatus = viewModel::clearRescanStatus,
            )

            BackupCard(
                status = status,
                onExport = { exportLauncher.launch(DEFAULT_BACKUP_NAME) },
                onImport = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                onClearStatus = viewModel::clearStatus,
            )

            CsvImportCard(
                status = csvStatus,
                onImport = { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                onExport = { csvExportLauncher.launch("athar-transactions.csv") },
                onClear = viewModel::clearCsvStatus,
            )

            DisplayCurrencyCard(
                currentCode = displayCurrency,
                onSelect = viewModel::setDisplayCurrency,
            )

            LanguageCard(
                currentTag = appLocale,
                onSelect = { tag ->
                    viewModel.setAppLocale(tag)
                    onApplyLocale(tag)
                },
            )

            HijriToggleCard(
                enabled = hijriEnabled,
                onToggle = viewModel::setHijriEnabled,
            )

            OwnAccountsCard(
                accounts = ownAccounts,
                onSave = viewModel::setOwnAccountNumbers,
            )

            AccountsEntryCard(onOpen = onOpenAccounts)

            RecurringRulesEntryCard(onOpen = onOpenRecurringRules)

            SmsAuditEntryCard(onOpen = onOpenSmsAudit)

            UserTemplatesEntryCard(onOpen = onOpenUserTemplates)

            ActivityLogEntryCard(onOpen = onOpenActivityLog)

            CategoriesEntryCard(onOpen = onOpenCategories)

            AboutCard()
        }
    }

    pendingExportUri?.let { uri ->
        PassphrasePrompt(
            title = "كلمة مرور النسخة الاحتياطية",
            description = "اكتب كلمة المرور التي ستحمي ملف النسخة. ستحتاجها لاحقًا لاسترجاع البيانات.",
            confirmLabel = "حفظ النسخة",
            onConfirm = { pass ->
                viewModel.export(context.contentResolver, uri, pass)
                pendingExportUri = null
            },
            onDismiss = { pendingExportUri = null },
        )
    }

    pendingImportUri?.let { uri ->
        PassphrasePrompt(
            title = "استرجاع النسخة",
            description = "اكتب كلمة مرور النسخة. سيتم استبدال البيانات الحالية.",
            confirmLabel = "استرجاع",
            onConfirm = { pass ->
                viewModel.import(context.contentResolver, uri, pass)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null },
        )
    }
}

@Composable
private fun SmsPermissionCard(
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            AtharText(
                text = if (granted) "قراءة الرسائل · مفعّلة" else "قراءة الرسائل · غير مفعّلة",
                style = theme.typography.headline,
                color = if (granted) theme.colors.olive else theme.colors.ink,
            )
            AtharText(
                text = if (granted) {
                    "الرسائل من البنوك تُقرأ محليًا على جهازك. لا تغادر الجهاز أبدًا."
                } else {
                    "لقراءة رسائل البنوك تلقائيًا، تحتاج أثر إلى صلاحية قراءة الرسائل. " +
                        "تبقى الرسائل والبيانات الماليّة على جهازك."
                },
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            if (!granted) PrimaryButton(text = "تفعيل قراءة الرسائل", onClick = onGrant)
        }
    }
}

@Composable
private fun HijriToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .clickable { onToggle(!enabled) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AtharText(text = "عرض التاريخ الهجري", style = theme.typography.headline)
                AtharText(
                    text = if (enabled) "يظهر بجانب التاريخ الميلادي في الشاشة الرئيسية." else "افتراضيًا، الميلادي فقط.",
                    style = theme.typography.body,
                    color = theme.colors.muted,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(if (enabled) theme.colors.ember else theme.colors.divider)
                    .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
            ) {
                AtharText(
                    text = if (enabled) "مفعّل" else "معطّل",
                    style = theme.typography.caption,
                    color = if (enabled) theme.colors.parchment else theme.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun CsvImportCard(
    status: CsvStatus,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "تبادل CSV", style = theme.typography.headline)
            AtharText(
                text = "استورد من جدول الاكسل القديم أو صدّر حركاتك بصيغة CSV (الأعمدة: date · vendor · amount · category · type · notes).",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            when (val s = status) {
                CsvStatus.Idle -> Unit
                CsvStatus.Working -> AtharText(
                    text = "جارٍ المعالجة…",
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
                is CsvStatus.Done -> {
                    AtharText(
                        text = "تم استيراد ${s.imported} حركة. (تم تخطّي ${s.skipped} صف.)",
                        style = theme.typography.caption,
                        color = theme.colors.olive,
                    )
                    TextButton(onClick = onClear) { AtharText("حسنًا", color = theme.colors.muted) }
                }
                is CsvStatus.Exported -> {
                    AtharText(
                        text = "تم تصدير ${s.count} حركة.",
                        style = theme.typography.caption,
                        color = theme.colors.olive,
                    )
                    TextButton(onClick = onClear) { AtharText("حسنًا", color = theme.colors.muted) }
                }
                is CsvStatus.Failed -> {
                    AtharText(text = s.reason, style = theme.typography.caption, color = theme.colors.crimson)
                    TextButton(onClick = onClear) { AtharText("حسنًا", color = theme.colors.muted) }
                }
            }
            val isWorking = status is CsvStatus.Working
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                        text = if (isWorking) "جارٍ…" else "استيراد",
                        onClick = { if (!isWorking) onImport() },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(theme.spacing.s))
                            .background(theme.colors.divider)
                            .clickable(enabled = !isWorking) { onExport() }
                            .padding(theme.spacing.m),
                        contentAlignment = Alignment.Center,
                    ) {
                        AtharText(text = if (isWorking) "جارٍ…" else "تصدير", style = theme.typography.headline, color = theme.colors.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogEntryCard(onOpen: () -> Unit) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "السجل", style = theme.typography.headline)
            AtharText(
                text = "كل إضافة أو تعديل أو حذف أو تأكيد لحركة، مع وقتها. مرجع لاسترجاع ما تذكره ضمنيًا.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun SmsAuditEntryCard(onOpen: () -> Unit) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "سجل الرسائل", style = theme.typography.headline)
            AtharText(
                text = "كل رسالة وصلت — مع نتيجة المعالجة (ناجحة، فاشلة، أو مُتجاهَلة). لا تغادر هذه القائمة الجهاز.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val theme = AtharTheme
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    AtharCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            AtharText(text = "عن أثر", style = theme.typography.headline)
            AtharText(
                text = "إصدار $versionName",
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            AtharText(
                text = "طوّر هذا التطبيق وليد الحامد · walhamed.com",
                style = theme.typography.body,
                color = theme.colors.ink,
            )
            AtharText(
                text = "مستوحى من جدول The Measure of a Plan (TMOAP)، الذي ألهم منهجية أثر في تتبّع المصروف الشهري وقياس الخطة المالية.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            AtharText(
                text = "كل بياناتك محفوظة محليًا على جهازك. لا حسابات، لا خوادم، لا تتبع.",
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun DisplayCurrencyCard(
    currentCode: String,
    onSelect: (String) -> Unit,
) {
    val theme = AtharTheme
    var expanded by remember { mutableStateOf(false) }
    val current = remember(currentCode) { CurrencyCatalog.entryOf(currentCode) }
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "العملة المعروضة", style = theme.typography.headline)
            AtharText(
                text = "اختر عملة العرض. الحركات المستوردة من الرسائل تحتفظ بعملتها الأصلية، لكن إدخالاتك اليدوية ستستخدم هذه العملة. لا يوجد تحويل تلقائي.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(theme.colors.divider)
                    .clickable { expanded = !expanded }
                    .padding(theme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AtharText(
                    text = current?.labelAr ?: currentCode,
                    style = theme.typography.headline,
                    color = theme.colors.ink,
                )
                AtharText(
                    text = "${current?.symbol ?: currentCode} · $currentCode",
                    style = theme.typography.body,
                    color = theme.colors.muted,
                )
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                    CurrencyCatalog.supported.forEach { entry ->
                        val isSelected = entry.code == currentCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(theme.spacing.s))
                                .background(if (isSelected) theme.colors.ember else theme.colors.parchment)
                                .clickable {
                                    onSelect(entry.code)
                                    expanded = false
                                }
                                .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            AtharText(
                                text = entry.labelAr,
                                style = theme.typography.body,
                                color = if (isSelected) theme.colors.parchment else theme.colors.ink,
                            )
                            AtharText(
                                text = "${entry.symbol} · ${entry.code}",
                                style = theme.typography.caption,
                                color = if (isSelected) theme.colors.parchment else theme.colors.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnAccountsCard(accounts: List<String>, onSave: (String) -> Unit) {
    val theme = AtharTheme
    var draft by remember(accounts) { mutableStateOf(accounts.joinToString(", ")) }
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "حساباتك الخاصة", style = theme.typography.headline)
            AtharText(
                text = "اكتب آخر ٤ أرقام لكل حساب تملكه (مفصولة بفواصل، مثلاً: 0930, 4268). أي تحويل إلى أحد هذه الأرقام يُعتبر «تحويل داخلي · ادخار محتمل» بدلاً من مصروف.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            AtharTextField(
                value = draft,
                onValueChange = { draft = it },
                label = "أرقام حساباتك",
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(theme.colors.ember)
                    .clickable { onSave(draft) }
                    .padding(theme.spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                AtharText(text = "حفظ الحسابات", style = theme.typography.headline, color = theme.colors.parchment)
            }
        }
    }
}

@Composable
private fun RescanAndCleanCard(
    status: RescanStatus,
    onRescan: () -> Unit,
    onClearStatus: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "إعادة فحص الرسائل", style = theme.typography.headline)
            AtharText(
                text = "يحذف كل الحركات قيد التأكيد ويعيد قراءة سجل الرسائل بأحدث القوالب. حركاتك المؤكدة لن تتأثر. مفيد بعد التحديث لتنظيف الإعلانات والإشعارات التي دخلت بالخطأ.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            val statusText = when (status) {
                RescanStatus.Idle -> null
                RescanStatus.Working -> "جاري إعادة الفحص…"
                is RescanStatus.Done -> "تم حذف ${status.clearedPending} عنصرًا من قائمة الانتظار، وأُعيد فحص السجل."
            }
            statusText?.let {
                AtharText(text = it, style = theme.typography.caption, color = theme.colors.muted)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(theme.colors.ember)
                    .clickable(enabled = status != RescanStatus.Working) {
                        if (status is RescanStatus.Done) onClearStatus() else onRescan()
                    }
                    .padding(theme.spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                val buttonText = when (status) {
                    is RescanStatus.Done -> "تم"
                    else -> "إعادة فحص ومسح المُعلَّقات"
                }
                AtharText(text = buttonText, style = theme.typography.headline, color = theme.colors.parchment)
            }
        }
    }
}

@Composable
private fun LanguageCard(
    currentTag: String,
    onSelect: (String) -> Unit,
) {
    val theme = AtharTheme
    val options = remember {
        listOf(
            Triple("", "نظام الجهاز", "Follow system"),
            Triple("ar", "العربية", "Arabic"),
            Triple("en", "English", "English"),
        )
    }
    AtharCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "اللغة · Language", style = theme.typography.headline)
            AtharText(
                text = "اختر لغة عرض التطبيق. سيتم إعادة تحميل الشاشة لتطبيق اللغة الجديدة.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            options.forEach { (tag, labelAr, labelEn) ->
                val isSelected = tag == currentTag
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(theme.spacing.s))
                        .background(if (isSelected) theme.colors.ember else theme.colors.parchment)
                        .clickable { onSelect(tag) }
                        .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AtharText(
                        text = labelAr,
                        style = theme.typography.body,
                        color = if (isSelected) theme.colors.parchment else theme.colors.ink,
                    )
                    AtharText(
                        text = labelEn,
                        style = theme.typography.caption,
                        color = if (isSelected) theme.colors.parchment else theme.colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsEntryCard(onOpen: () -> Unit) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "الحسابات", style = theme.typography.headline)
            AtharText(
                text = "أضف حسابات الجاري، الادخار، البطاقات الائتمانية، والاستثمار. صافي ثروتك = مجموع الأرصدة الحالية.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun RecurringRulesEntryCard(onOpen: () -> Unit) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "الحركات المتكررة", style = theme.typography.headline)
            AtharText(
                text = "أنشئ قواعد للحركات المتكررة شهريًا أو أسبوعيًا أو سنويًا — الإيجار، الراتب، Netflix. تُولِّد حركات معلّقة في «اليوم» عند استحقاقها.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun UserTemplatesEntryCard(onOpen: () -> Unit) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "قوالب البنوك", style = theme.typography.headline)
            AtharText(
                text = "علِّم أثر تنسيق رسائل بنكك إذا لم يكن من ضمن البنوك المدعومة افتراضيًا. الصق رسالة، اكتب الكلمات المحيطة بالمبلغ، احفظ.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun CategoriesEntryCard(onOpen: () -> Unit) {
    val theme = AtharTheme
    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "التصنيفات والقواعد", style = theme.typography.headline)
            AtharText(
                text = "أضف، أعد تسمية، أو أرشف التصنيفات. التغييرات تبقى في نسخك الاحتياطية.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun BackfillCard(
    progress: BackfillProgress,
    onRescan: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            AtharText(text = "إعادة فحص الرسائل", style = theme.typography.headline)
            AtharText(
                text = "اقرأ آخر ٩٠ يومًا من الرسائل في صندوق الوارد وأنشئ منها حركات معلّقة في انتظار التأكيد.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            when (val p = progress) {
                BackfillProgress.Idle -> Unit
                is BackfillProgress.Running -> AtharText(
                    text = "جارٍ المسح… (${p.scanned} رسالة)",
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
                is BackfillProgress.Done -> AtharText(
                    text = "تم فحص ${p.scanned} رسالة، أُرسلت ${p.sentToPipeline} للمعالجة.",
                    style = theme.typography.caption,
                    color = theme.colors.olive,
                )
                is BackfillProgress.Failed -> AtharText(
                    text = "تعذّر الفحص: ${p.reason}",
                    style = theme.typography.caption,
                    color = theme.colors.crimson,
                )
            }
            val isRunning = progress is BackfillProgress.Running
            PrimaryButton(
                text = if (isRunning) "جارٍ المسح…" else "ابدأ الفحص",
                onClick = { if (!isRunning) onRescan() },
            )
        }
    }
}

@Composable
private fun BackupCard(
    status: BackupStatus,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearStatus: () -> Unit,
) {
    val theme = AtharTheme
    AtharCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            AtharText(text = "النسخ الاحتياطي والاسترجاع", style = theme.typography.headline)
            AtharText(
                text = "النسخة الاحتياطية ملف مشفّر بكلمة مرور تختارها أنت. لا يمكن استرجاعها بدونها.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                PrimaryButton(text = "تصدير", onClick = onExport, modifier = Modifier.weight(1f))
                SecondaryButton(text = "استرجاع", onClick = onImport, modifier = Modifier.weight(1f))
            }
            when (status) {
                BackupStatus.Idle -> Unit
                BackupStatus.Working -> AtharText(
                    text = "جارٍ المعالجة…",
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
                is BackupStatus.Success -> {
                    AtharText(text = status.message, style = theme.typography.caption, color = theme.colors.olive)
                    TextButton(onClick = onClearStatus) { AtharText("حسنًا", color = theme.colors.muted) }
                }
                is BackupStatus.Failure -> {
                    AtharText(text = status.reason, style = theme.typography.caption, color = theme.colors.crimson)
                    TextButton(onClick = onClearStatus) { AtharText("حسنًا", color = theme.colors.muted) }
                }
            }
        }
    }
}

@Composable
private fun PassphrasePrompt(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = AtharTheme
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AtharText(text = title, style = theme.typography.headline) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                AtharText(text = description, style = theme.typography.body, color = theme.colors.muted)
                AtharTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = "كلمة المرور",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Password,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (passphrase.isNotBlank()) onConfirm(passphrase) },
                enabled = passphrase.isNotBlank(),
            ) { AtharText(text = confirmLabel, color = theme.colors.ember) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { AtharText(text = "إلغاء", color = theme.colors.muted) }
        },
        containerColor = theme.colors.parchment,
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.ember)
            .clickable(onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.headline, color = theme.colors.parchment)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.divider)
            .clickable(onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.headline, color = theme.colors.ink)
    }
}

private val SMS_PERMISSIONS = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS,
)

private const val DEFAULT_BACKUP_NAME = "athar-backup.athar"

private fun hasSmsPermissions(context: Context): Boolean = SMS_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}
