package dev.personal.ledger

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personal.ledger.domain.Money
import dev.personal.ledger.reminders.Reminders
import dev.personal.ledger.ui.LaunchAction
import dev.personal.ledger.ui.LedgerRoot
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.components.PrimaryButton
import dev.personal.ledger.ui.onboarding.Onboarding
import dev.personal.ledger.ui.theme.LedgerAppTheme
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Space

class MainActivity : FragmentActivity() {
    private val vm: LedgerViewModel by viewModels()
    private val launch = mutableStateOf<LaunchAction?>(null)
    private var locked by mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        locked = vm.settings.value.biometricLock
        handle(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dash by vm.dashboard.collectAsStateWithLifecycle()
            // Onboarding holds no financial data, so protection starts once the ledger exists.
            LaunchedEffect(settings.secureScreen, settings.onboarded) {
                if (settings.secureScreen && settings.onboarded) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            LaunchedEffect(settings.currency) { Money.symbol = symbolFor(settings.currency) }
            // Launcher long-press shortcuts for the top presets: two taps from the home screen to the amount.
            LaunchedEffect(dash?.presets?.take(3)?.map { it.id to it.label }) { dash?.let { updateShortcuts(it.presets.take(3)) } }
            LedgerAppTheme(settings.theme, settings.hideAmounts) {
                when {
                    !settings.onboarded -> Onboarding(vm)
                    locked && settings.biometricLock -> LockScreen { authenticate() }
                    else -> LedgerRoot(vm, launch)
                }
            }
        }
        if (locked) authenticate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock only after a real absence, so switching to a banking app to copy a number doesn't cost a prompt.
        if (vm.settings.value.biometricLock && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > 60_000) {
            locked = true
            authenticate()
        }
        if (vm.settings.value.onboarded) vm.autoPost()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        launch.value = when {
            intent.hasExtra(Reminders.EXTRA_RECURRING) -> LaunchAction.PayRecurring(intent.getLongExtra(Reminders.EXTRA_RECURRING, 0))
            intent.hasExtra(Reminders.EXTRA_CARD) -> LaunchAction.PayCard(intent.getLongExtra(Reminders.EXTRA_CARD, 0))
            intent.action == ACTION_PRESET -> LaunchAction.Preset(intent.getLongExtra(EXTRA_PRESET, 0))
            intent.action == ACTION_COMMAND -> LaunchAction.Command
            intent.action == ACTION_PLAN -> LaunchAction.Plan
            else -> launch.value
        }
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Ledger")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }

    private fun updateShortcuts(presets: List<dev.personal.ledger.data.Preset>) {
        val shortcuts = presets.map { p ->
            ShortcutInfoCompat.Builder(this, "preset-${p.id}")
                .setShortLabel(p.label)
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_notification))
                .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_PRESET).putExtra(EXTRA_PRESET, p.id))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts) }
    }

    companion object {
        const val ACTION_PRESET = "dev.personal.ledger.PRESET"
        const val ACTION_COMMAND = "dev.personal.ledger.COMMAND"
        const val ACTION_PLAN = "dev.personal.ledger.PLAN"
        const val EXTRA_PRESET = "presetId"

        fun symbolFor(currency: String) = when (currency) {
            "USD" -> "$"; "EUR" -> "€"; "JPY" -> "¥"; "KRW" -> "₩"; "THB" -> "฿"; else -> "₫"
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val c = LedgerTheme.colors
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Rounded.Lock, null, tint = c.accent)
            }
            Spacer(Modifier.height(Space.l))
            androidx.compose.material3.Text("Ledger is locked", style = LedgerTheme.type.headline, color = c.text)
            Spacer(Modifier.height(Space.xxl))
            PrimaryButton("Unlock", Modifier.width(200.dp).padding(horizontal = Space.l), onClick = onUnlock)
        }
    }
}
