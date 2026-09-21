package dev.whekin.whfin.ui.setup

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.ui.theme.WhfinTheme

@Composable
fun WelcomeChoiceScreen(
    busy: Boolean,
    problem: String?,
    onSetUpPersonal: () -> Unit,
    onExploreDemo: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            WhfinSectionLabel(stringResource(R.string.app_name))
            // The greeting sits in the middle of the screen rather than at the top of an empty
            // one: two sentences pinned under the status bar left two thirds of a phone blank,
            // which reads as a screen still loading. It still scrolls, so a large font scale
            // pushes nothing off the bottom.
            BoxWithConstraints(Modifier.weight(1f)) {
                val viewport = maxHeight
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = viewport),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(96.dp).offset(x = (-24).dp),
                        )
                        Text(
                            stringResource(R.string.welcome_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            stringResource(R.string.welcome_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (problem != null) {
                            WhfinNotice(
                                title = stringResource(R.string.demo_mode_problem_title),
                                body = problem,
                                kind = WhfinNoticeKind.Error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WhfinButton(
                    label = stringResource(R.string.welcome_personal_action),
                    onClick = onSetUpPersonal,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = !busy,
                    leadingIcon = Icons.Default.Wallet,
                )
                WhfinButton(
                    label = stringResource(R.string.welcome_demo_action),
                    onClick = onExploreDemo,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    style = WhfinActionStyle.Secondary,
                    leadingIcon = Icons.Default.Visibility,
                )
            }
        }
    }
}

data class PersonalSetupState(
    val accountCount: Int? = null,
    val smsMonitoringEnabled: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val canRequestSmsPermission: Boolean = true,
    val unresolvedSmsCount: Int? = null,
    val statementReviewCount: Int? = null,
)

@Composable
fun PersonalSetupSecondaryPage(
    title: String,
    onBack: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (header != null) header() else Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)) {
                content()
            }
        }
    }
}

@Preview(name = "Welcome light", widthDp = 400, heightDp = 850, showBackground = true)
@Preview(name = "Welcome dark", widthDp = 400, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Welcome font 1.5", widthDp = 400, heightDp = 950, fontScale = 1.5f)
@Preview(name = "Welcome compact", widthDp = 400, heightDp = 560)
@Composable
private fun WelcomeChoicePreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WelcomeChoiceScreen(false, null, {}, {}, {})
        }
    }
}
