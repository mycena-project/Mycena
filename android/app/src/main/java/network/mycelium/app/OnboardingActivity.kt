package network.mycelium.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingFlow(
                        onComplete = { displayName ->

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
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingFlow(onComplete: (String) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }

    when (page) {
        0 -> OnboardingPage1(onNext = { page = 1 })
        1 -> OnboardingPage2(onNext = { page = 2 })
        2 ->
            OnboardingPage3(
                displayName = displayName,
                onNameChange = { displayName = it },
                onComplete = {
                    if (displayName.isNotBlank()) {
                        onComplete(displayName)
                    }
                },
            )
    }
}

@Composable
fun OnboardingPage1(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🍄", fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_1_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_1_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_next))
        }
    }
}

@Composable
fun OnboardingPage2(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FeatureRow(
            icon = "🔒",
            title = stringResource(R.string.onboarding_feature_noserver_title),
            body = stringResource(R.string.onboarding_feature_noserver_body),
        )
        Spacer(Modifier.height(24.dp))
        FeatureRow(
            icon = "👤",
            title = stringResource(R.string.onboarding_feature_noaccount_title),
            body = stringResource(R.string.onboarding_feature_noaccount_body),
        )
        Spacer(Modifier.height(24.dp))
        FeatureRow(
            icon = "🗑️",
            title = stringResource(R.string.onboarding_feature_panicwipe_title),
            body = stringResource(R.string.onboarding_feature_panicwipe_body),
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_next))
        }
    }
}

@Composable
fun OnboardingPage3(
    displayName: String,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.onboarding_3_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_3_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.onboarding_name_hint)) },
            placeholder = { Text(stringResource(R.string.onboarding_name_placeholder)) },
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
            stringResource(R.string.onboarding_name_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onComplete,
            enabled = displayName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_start))
        }
    }
}

@Composable
fun FeatureRow(icon: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(icon, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
