package com.athar.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.R
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val theme = AtharTheme
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var smsGranted by remember { mutableStateOf(hasSmsPermissions(context)) }

    val smsLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        smsGranted = results.values.all { it }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(theme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.l),
        ) {
            PageIndicator(current = state.pageIndex, total = state.totalPages)
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
                when (state.pageIndex) {
                    0 -> WelcomePage()
                    1 -> PrivacyPage()
                    2 -> SmsPage(
                        granted = smsGranted,
                        onGrant = { smsLauncher.launch(SMS_PERMISSIONS) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                if (state.pageIndex > 0) {
                    SheetButton(
                        text = stringResource(R.string.onboarding_action_back),
                        background = theme.colors.divider,
                        textColor = theme.colors.ink,
                        onClick = viewModel::back,
                        modifier = Modifier.weight(1f),
                    )
                }
                SheetButton(
                    text = if (state.pageIndex < state.totalPages - 1)
                        stringResource(R.string.onboarding_action_next)
                    else stringResource(R.string.onboarding_action_start),
                    background = theme.colors.ember,
                    textColor = theme.colors.parchment,
                    onClick = {
                        if (state.pageIndex < state.totalPages - 1) {
                            viewModel.next()
                        } else {
                            viewModel.finish()
                            onFinished()
                        }
                    },
                    modifier = Modifier.weight(if (state.pageIndex == 0) 1f else 2f),
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(current: Int, total: Int) {
    val theme = AtharTheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == current) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) theme.colors.ember else theme.colors.divider),
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    val theme = AtharTheme
    Column(
        verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AtharText(text = stringResource(R.string.onboarding_welcome_title), style = theme.typography.landmark)
        AtharText(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = theme.typography.subtitle,
            color = theme.colors.muted,
        )
        AtharText(
            text = stringResource(R.string.onboarding_welcome_body),
            style = theme.typography.body,
            color = theme.colors.muted,
        )
    }
}

@Composable
private fun PrivacyPage() {
    val theme = AtharTheme
    Column(
        verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AtharText(text = stringResource(R.string.onboarding_privacy_title), style = theme.typography.title)
        AtharText(
            text = stringResource(R.string.onboarding_privacy_body),
            style = theme.typography.body,
            color = theme.colors.muted,
        )
        PrivacyBullet(text = stringResource(R.string.onboarding_privacy_bullet_1))
        PrivacyBullet(text = stringResource(R.string.onboarding_privacy_bullet_2))
        PrivacyBullet(text = stringResource(R.string.onboarding_privacy_bullet_3))
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    val theme = AtharTheme
    AtharCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(theme.colors.ember),
            )
            AtharText(text = text, style = theme.typography.body)
        }
    }
}

@Composable
private fun SmsPage(granted: Boolean, onGrant: () -> Unit) {
    val theme = AtharTheme
    Column(
        verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AtharText(text = stringResource(R.string.onboarding_sms_title), style = theme.typography.title)
        AtharText(
            text = stringResource(R.string.onboarding_sms_body),
            style = theme.typography.body,
            color = theme.colors.muted,
        )
        if (granted) {
            AtharText(
                text = stringResource(R.string.onboarding_sms_granted),
                style = theme.typography.headline,
                color = theme.colors.olive,
            )
        } else {
            SheetButton(
                text = stringResource(R.string.onboarding_sms_grant_button),
                background = theme.colors.ember,
                textColor = theme.colors.parchment,
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth(),
            )
            AtharText(
                text = stringResource(R.string.onboarding_sms_skip_note),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
    }
}

@Composable
private fun SheetButton(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.headline, color = textColor)
    }
}

private val SMS_PERMISSIONS = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS,
)

private fun hasSmsPermissions(context: Context): Boolean = SMS_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}
