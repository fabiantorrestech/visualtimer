package com.fabiantorrestech.visualtimerplus.ui.screen

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.ui.theme.VisualTimerPlusTheme
import com.fabiantorrestech.visualtimerplus.util.formatClockTime

class TimerFinishedActivity : ComponentActivity() {

    private lateinit var controller: TimerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        controller = TimerController(applicationContext)

        setContent {
            val appState by TimerRepository.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (appState.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                else -> systemDark
            }
            VisualTimerPlusTheme(isDark = isDark, oledBlackEnabled = appState.isOledMode) {
                TimerFinishedScreen(
                    appState = appState,
                    onDismiss = { timerIndex -> controller.dispatch(TimerAction.DismissFinished(timerIndex)) },
                    onRestart = { timerIndex -> controller.dispatch(TimerAction.Restart(timerIndex)) },
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun TimerFinishedScreen(
    appState: AppState,
    onDismiss: (Int) -> Unit,
    onRestart: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val finishedTimers = appState.timers.filter {
        it.status == TimerStatus.Finished || it.status == TimerStatus.Overtime
    }

    LaunchedEffect(finishedTimers.isEmpty()) {
        if (finishedTimers.isEmpty()) onClose()
    }

    val timer = finishedTimers.firstOrNull() ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Time's Up!",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            if (timer.activeTimerName.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = timer.activeTimerName,
                    fontSize = 24.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }

            if (timer.status == TimerStatus.Overtime) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "+${timer.currentOvertimeSegmentMillis.formatClockTime()}",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }

            if (finishedTimers.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "+ ${finishedTimers.size - 1} more",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = { onDismiss(timer.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text(text = "Dismiss", fontSize = 16.sp)
                }
                Button(
                    onClick = { onRestart(timer.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(text = "Restart", fontSize = 16.sp)
                }
            }
        }
    }
}
