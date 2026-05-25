package com.athar.feature.settings

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.common.money.Money
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.display.CurrencyCatalog
import com.athar.core.designsystem.display.LocalDisplayCurrency
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.domain.model.Account
import com.athar.core.domain.model.AccountBalance
import com.athar.core.domain.model.AccountType
import com.athar.core.domain.model.MANUAL_ACCOUNT_ID
import java.math.BigDecimal

/**
 * Accounts management screen (G-4). Displays net worth headline + every account
 * with computed running balance + inline add/edit panels.
 *
 * Visual language mirrors RecurringRulesScreen — single ember accent, AtharCard
 * surfaces, no shadows, RTL-first.
 */
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val theme = AtharTheme
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val netWorth by viewModel.netWorth.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val displayCurrency = LocalDisplayCurrency.current

    var showAdd by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }

    // Active first, archived (faded) at the bottom. Within each, original sortOrder is preserved.
    val sortedBalances = remember(balances) {
        balances.sortedWith(
            compareBy<AccountBalance> { it.account.archived }
                .thenBy { it.account.sortOrder }
                .thenBy { it.account.name },
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtharText(text = "الحسابات", style = theme.typography.overline, color = theme.colors.muted)
                AtharText(
                    text = "رجوع",
                    style = theme.typography.body,
                    color = theme.colors.muted,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            }

            NetWorthCard(netWorth = netWorth)

            AtharCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdd = !showAdd },
            ) {
                AtharText(
                    text = if (showAdd) "إخفاء النموذج" else "+ إضافة حساب",
                    style = theme.typography.headline,
                    color = theme.colors.ember,
                )
            }
            if (showAdd) {
                AddAccountForm(
                    defaultCurrency = displayCurrency,
                    onSave = { name, type, currency, opening, notes ->
                        viewModel.add(
                            name = name,
                            type = type,
                            currency = currency,
                            openingBalanceText = opening,
                            notes = notes,
                        )
                        showAdd = false
                    },
                    onCancel = { showAdd = false },
                )
            }

            errorMessage?.let { msg ->
                AtharCard {
                    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                        AtharText(
                            text = msg,
                            style = theme.typography.body,
                            color = theme.colors.crimson,
                        )
                        TextButton(onClick = viewModel::clearError) {
                            AtharText("حسنًا", color = theme.colors.muted)
                        }
                    }
                }
            }

            if (sortedBalances.isEmpty()) {
                AtharCard {
                    AtharText(
                        text = "لا حسابات بعد. أضف أول حساب لك (جاري، ادخار، نقدي) لتبدأ.",
                        style = theme.typography.body,
                        color = theme.colors.muted,
                    )
                }
            } else {
                sortedBalances.forEach { balance ->
                    val isEditing = editingId == balance.account.id
                    AccountRow(
                        balance = balance,
                        isEditing = isEditing,
                        onToggleEdit = {
                            editingId = if (isEditing) null else balance.account.id
                        },
                        onSaveEdit = { updated ->
                            viewModel.update(updated)
                            editingId = null
                        },
                        onSetArchived = { archived ->
                            viewModel.setArchived(balance.account.id, archived)
                        },
                        onDelete = { viewModel.delete(balance.account.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetWorthCard(netWorth: com.athar.core.domain.model.NetWorth) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "صافي الثروة", style = theme.typography.overline, color = theme.colors.muted)
            AtharNumber(money = netWorth.total, landmark = true)
            if (netWorth.isMixedCurrency) {
                AtharText(
                    text = "بدون تحويل عملات",
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
                Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                    netWorth.byCurrency.forEach { (currency, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AtharText(
                                text = currency,
                                style = theme.typography.caption,
                                color = theme.colors.muted,
                            )
                            AtharNumber(money = amount)
                        }
                    }
                }
            }
            AtharText(
                text = "إجمالي صافي ثروتك عبر الحسابات النشطة. التحويلات داخل حساباتك لا تُغيّر هذا الرقم.",
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun AddAccountForm(
    defaultCurrency: String,
    onSave: (name: String, type: AccountType, currency: String, openingBalanceText: String, notes: String) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = AtharTheme
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CHECKING) }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var opening by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var currencyExpanded by remember { mutableStateOf(false) }

    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "إضافة حساب جديد", style = theme.typography.headline)

            AtharTextField(
                value = name,
                onValueChange = { name = it },
                label = "اسم الحساب (مثلاً: الراجحي - جاري)",
                modifier = Modifier.fillMaxWidth(),
            )

            AccountTypeChips(selected = type, onSelect = { type = it })

            CurrencyPicker(
                currentCode = currency,
                expanded = currencyExpanded,
                onToggle = { currencyExpanded = !currencyExpanded },
                onSelect = {
                    currency = it
                    currencyExpanded = false
                },
            )

            AtharTextField(
                value = opening,
                onValueChange = { opening = it },
                label = "الرصيد الافتتاحي ($currency)",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
            )

            AtharTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "ملاحظات (اختياري)",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                EmberButton(
                    text = "حفظ",
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(name, type, currency, opening, notes)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                NeutralButton(
                    text = "إلغاء",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    balance: AccountBalance,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onSaveEdit: (Account) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val theme = AtharTheme
    val account = balance.account
    val isManualSeed = account.id == MANUAL_ACCOUNT_ID
    val rowAlpha = if (account.archived) 0.45f else 1f

    AtharCard(modifier = Modifier
        .fillMaxWidth()
        .alpha(rowAlpha)) {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(end = theme.spacing.s)) {
                    AtharText(text = account.name, style = theme.typography.headline)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
                    ) {
                        TypeChip(type = account.type)
                        AtharText(
                            text = account.currency,
                            style = theme.typography.caption,
                            color = theme.colors.muted,
                        )
                        if (account.archived) {
                            AtharText(
                                text = "مؤرشف",
                                style = theme.typography.caption,
                                color = theme.colors.muted,
                            )
                        }
                    }
                }
                AtharNumber(
                    money = balance.current,
                    color = if (balance.current.isNegative()) theme.colors.crimson else theme.colors.ink,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                NeutralChip(
                    label = if (isEditing) "إغلاق" else "تعديل",
                    onClick = onToggleEdit,
                )
                NeutralChip(
                    label = if (account.archived) "إعادة تفعيل" else "أرشفة",
                    onClick = { onSetArchived(!account.archived) },
                )
                if (!isManualSeed) {
                    DangerChip(
                        label = "حذف",
                        onClick = onDelete,
                    )
                }
            }

            if (isEditing) {
                EditAccountPanel(
                    account = account,
                    onSave = onSaveEdit,
                )
            }
        }
    }
}

@Composable
private fun EditAccountPanel(
    account: Account,
    onSave: (Account) -> Unit,
) {
    val theme = AtharTheme
    var name by remember(account.id) { mutableStateOf(account.name) }
    var type by remember(account.id) { mutableStateOf(account.type) }
    var currency by remember(account.id) { mutableStateOf(account.currency) }
    var opening by remember(account.id) {
        mutableStateOf(account.openingBalance.amount.toPlainString())
    }
    var notes by remember(account.id) { mutableStateOf(account.notes.orEmpty()) }
    var currencyExpanded by remember(account.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.parchment)
            .padding(theme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
    ) {
        AtharText(text = "تعديل الحساب", style = theme.typography.headline)

        AtharTextField(
            value = name,
            onValueChange = { name = it },
            label = "اسم الحساب",
            modifier = Modifier.fillMaxWidth(),
        )

        AccountTypeChips(selected = type, onSelect = { type = it })

        CurrencyPicker(
            currentCode = currency,
            expanded = currencyExpanded,
            onToggle = { currencyExpanded = !currencyExpanded },
            onSelect = {
                currency = it
                currencyExpanded = false
            },
        )

        AtharTextField(
            value = opening,
            onValueChange = { opening = it },
            label = "الرصيد الافتتاحي ($currency)",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Decimal,
        )

        AtharTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "ملاحظات (اختياري)",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )

        EmberButton(
            text = "حفظ التعديلات",
            onClick = {
                val trimmedName = name.trim()
                if (trimmedName.isEmpty()) return@EmberButton
                val amountText = opening.trim().ifEmpty { "0" }
                val newOpening = runCatching {
                    Money.of(BigDecimal(amountText), currency)
                }.getOrNull() ?: return@EmberButton
                onSave(
                    account.copy(
                        name = trimmedName,
                        type = type,
                        currency = currency,
                        openingBalance = newOpening,
                        notes = notes.trim().ifBlank { null },
                    ),
                )
            },
        )
    }
}

@Composable
private fun AccountTypeChips(
    selected: AccountType,
    onSelect: (AccountType) -> Unit,
) {
    val theme = AtharTheme
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AccountTypeChip(AccountType.CHECKING, selected, onSelect)
            AccountTypeChip(AccountType.SAVINGS, selected, onSelect)
            AccountTypeChip(AccountType.CREDIT_CARD, selected, onSelect)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AccountTypeChip(AccountType.CASH, selected, onSelect)
            AccountTypeChip(AccountType.INVESTMENT, selected, onSelect)
            AccountTypeChip(AccountType.OTHER, selected, onSelect)
        }
    }
}

@Composable
private fun AccountTypeChip(
    type: AccountType,
    selected: AccountType,
    onSelect: (AccountType) -> Unit,
) {
    val theme = AtharTheme
    val isSelected = type == selected
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(if (isSelected) theme.colors.ember else theme.colors.divider)
            .clickable { onSelect(type) }
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
    ) {
        AtharText(
            text = accountTypeLabel(type),
            style = theme.typography.caption,
            color = if (isSelected) theme.colors.parchment else theme.colors.ink,
        )
    }
}

@Composable
private fun TypeChip(type: AccountType) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.divider)
            .padding(horizontal = theme.spacing.s, vertical = theme.spacing.xs),
    ) {
        AtharText(
            text = accountTypeLabel(type),
            style = theme.typography.caption,
            color = theme.colors.ink,
        )
    }
}

@Composable
private fun CurrencyPicker(
    currentCode: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val theme = AtharTheme
    val current = remember(currentCode) { CurrencyCatalog.entryOf(currentCode) }
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.spacing.s))
                .background(theme.colors.divider)
                .clickable(onClick = onToggle)
                .padding(theme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AtharText(
                text = current?.labelAr ?: currentCode,
                style = theme.typography.body,
                color = theme.colors.ink,
            )
            AtharText(
                text = "${current?.symbol ?: currentCode} · $currentCode",
                style = theme.typography.caption,
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
                            .clickable { onSelect(entry.code) }
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

@Composable
private fun NeutralChip(label: String, onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.divider)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
    ) {
        AtharText(text = label, style = theme.typography.caption, color = theme.colors.ink)
    }
}

@Composable
private fun DangerChip(label: String, onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.divider)
            .clickable(onClick = onClick)
            .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
    ) {
        AtharText(text = label, style = theme.typography.caption, color = theme.colors.crimson)
    }
}

@Composable
private fun EmberButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
private fun NeutralButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CHECKING -> "جاري"
    AccountType.SAVINGS -> "ادخار"
    AccountType.CREDIT_CARD -> "بطاقة ائتمانية"
    AccountType.CASH -> "نقدي"
    AccountType.INVESTMENT -> "استثمار"
    AccountType.OTHER -> "آخر"
}
