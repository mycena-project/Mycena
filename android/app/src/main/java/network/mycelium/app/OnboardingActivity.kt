// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import network.mycelium.app.onboarding.OnboardingCopy
import network.mycelium.app.onboarding.OnboardingCopyLoader
import network.mycelium.app.onboarding.PositioningVariant
import network.mycelium.app.ui.MyceliumTheme

class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyceliumTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingFlow(
                        onComplete = { displayName ->
                            LocalDataWiper.wipeAllLocalData(this)

                            getSharedPreferences("settings", MODE_PRIVATE)
                                .edit()
                                .putString("display_name", displayName.trim())
                                .apply()

                            getSharedPreferences("app", MODE_PRIVATE)
                                .edit()
                                .putBoolean("onboarding_complete", true)
                                .apply()

                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingFlow(onComplete: (String) -> Unit) {
    val context = LocalContext.current
    val copy = remember { OnboardingCopyLoader.load(context) }
    val positioningVariant = remember { OnboardingCopyLoader.resolvePositioningVariant(context, copy) }
    var page by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (page + 1) / 4f },
            modifier = Modifier.fillMaxWidth(),
        )
        when (page) {
            0 ->
                OnboardingPositioningScreen(
                    copy = copy,
                    variant = positioningVariant,
                    onNext = { page = 1 },
                )
            1 ->
                OnboardingWhyScreen(
                    copy = copy,
                    onBack = { page = 0 },
                    onNext = { page = 2 },
                )
            2 ->
                OnboardingFaqScreen(
                    copy = copy,
                    onBack = { page = 1 },
                    onNext = { page = 3 },
                )
            3 ->
                OnboardingCompletionScreen(
                    copy = copy,
                    displayName = displayName,
                    onNameChange = { displayName = it },
                    onBack = { page = 2 },
                    onComplete = {
                        if (displayName.isNotBlank()) {
                            onComplete(displayName)
                        }
                    },
                )
        }
    }
}

@Composable
private fun OnboardingScaffold(
    copy: OnboardingCopy,
    showBack: Boolean,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    nextLabel: String = copy.actionNext,
    nextEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
        Spacer(Modifier.height(16.dp))
        DataHandlingLink()
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showBack && onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(copy.actionBack)
                }
            }
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier.weight(if (showBack) 1f else 1f).fillMaxWidth(),
            ) {
                Text(nextLabel)
            }
        }
    }
}

@Composable
private fun DataHandlingLink() {
    val context = LocalContext.current
    TextButton(
        onClick = { openDataHandling(context) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.settings_data_handling),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun OnboardingPositioningScreen(
    copy: OnboardingCopy,
    variant: PositioningVariant,
    onNext: () -> Unit,
) {
    OnboardingScaffold(
        copy = copy,
        showBack = false,
        onBack = null,
        onNext = onNext,
    ) {
        Text("🍄", fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            variant.headline,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun OnboardingWhyScreen(
    copy: OnboardingCopy,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingScaffold(
        copy = copy,
        showBack = true,
        onBack = onBack,
        onNext = onNext,
    ) {
        Text(
            copy.whyTitle,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        copy.whyBullets.forEach { bullet ->
            WhyBullet(text = bullet)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WhyBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "•",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(end = 12.dp, top = 2.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

@Composable
fun OnboardingFaqScreen(
    copy: OnboardingCopy,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingScaffold(
        copy = copy,
        showBack = true,
        onBack = onBack,
        onNext = onNext,
    ) {
        Text(
            copy.faqTitle,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        copy.faqItems.forEach { item ->
            FaqBlock(question = item.question, answer = item.answer)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FaqBlock(
    question: String,
    answer: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            question,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            answer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
    }
}

@Composable
fun OnboardingCompletionScreen(
    copy: OnboardingCopy,
    displayName: String,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    OnboardingScaffold(
        copy = copy,
        showBack = true,
        onBack = onBack,
        onNext = onComplete,
        nextLabel = copy.completionStartButton,
        nextEnabled = displayName.isNotBlank(),
    ) {
        Text(
            copy.completionTitle,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            copy.completionBody,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = onNameChange,
            label = { Text(copy.completionNameHint) },
            placeholder = { Text(copy.completionNamePlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onComplete() }),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            copy.completionNamePrivacyNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}
