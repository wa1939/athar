package com.athar.core.designsystem.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.athar.core.designsystem.R
import com.athar.core.designsystem.theme.AtharTheme

/**
 * Outlined text field with Athar palette + Thmanyah typography.
 *
 * No floating-label trickery (Master Brief §2.5 "no bounce, no spring") — label sits above.
 */
@Composable
fun AtharTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val theme = AtharTheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { AtharText(label, style = theme.typography.caption, color = theme.colors.muted) },
        placeholder = placeholder?.let {
            { AtharText(it, color = theme.colors.muted) }
        },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
        supportingText = supportingText?.let {
            { AtharText(it, style = theme.typography.caption, color = if (isError) theme.colors.crimson else theme.colors.muted) }
        },
        textStyle = theme.typography.body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = theme.colors.ember,
            unfocusedBorderColor = theme.colors.divider,
            errorBorderColor = theme.colors.crimson,
            focusedTextColor = theme.colors.ink,
            unfocusedTextColor = theme.colors.ink,
            cursorColor = theme.colors.ember,
        ),
    )
}

/** Numeric variant — keyboard type pinned to Decimal for amount entry. */
@Composable
fun AtharAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    AtharTextField(
        value = value,
        onValueChange = onValueChange,
        label = label ?: stringResource(R.string.amount_field_label),
        modifier = modifier,
        keyboardType = KeyboardType.Decimal,
        isError = isError,
        supportingText = supportingText,
    )
}
