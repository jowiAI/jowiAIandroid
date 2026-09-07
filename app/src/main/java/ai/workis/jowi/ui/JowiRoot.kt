package ai.workis.jowi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ai.workis.jowi.Graph
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.screens.ApplyScreen
import ai.workis.jowi.ui.screens.LoginScreen
import ai.workis.jowi.ui.screens.MainScreen
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.delay

// iOS LaunchPhase { .splash, .login, .main } — same state machine
private enum class LaunchPhase { Splash, Login, Main }

@Composable
fun JowiRoot() {
    var phase by remember { mutableStateOf(LaunchPhase.Splash) }
    // iOS: showApply presents WorkisApplyView as a fullScreenCover from login
    var showApply by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val isAuthenticated by Graph.auth.isAuthenticated.collectAsState()

    val activity = androidx.compose.ui.platform.LocalContext.current
        as? androidx.fragment.app.FragmentActivity
    val bioTitle = androidx.compose.ui.res.stringResource(ai.workis.jowi.R.string.bio_title)
    val bioSubtitle = androidx.compose.ui.res.stringResource(ai.workis.jowi.R.string.face_idreason)

    LaunchedEffect(Unit) {
        // brand moment: keep the curtain up briefly while the checks run
        delay(900)
        phase = if (Graph.auth.hasToken) {
            // stored token → biometric gate first (cancel/fail drops to login, token kept)
            val passed = activity == null || biometricGate(activity, bioTitle, bioSubtitle)
            if (passed && Graph.auth.validate()) LaunchPhase.Main else LaunchPhase.Login
        } else {
            LaunchPhase.Login
        }
    }

    // authService.isAuthenticated flipping while in .main drops back to .login
    LaunchedEffect(isAuthenticated) {
        if (phase == LaunchPhase.Main && !isAuthenticated) phase = LaunchPhase.Login
        if (phase == LaunchPhase.Login && isAuthenticated) phase = LaunchPhase.Main
    }

    if (showApply) {
        ApplyScreen(onClose = { showApply = false })
        return
    }

    when (phase) {
        LaunchPhase.Splash -> SplashScreen()
        LaunchPhase.Login -> LoginScreen(onApply = { showApply = true })
        LaunchPhase.Main -> MainScreen()
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WorkisTheme.colors.canvas),
        contentAlignment = Alignment.Center,
    ) {
        WorkisMark(size = 36, breathing = true)
    }
}
