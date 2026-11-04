package com.sujal.sleeptimer.testplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class TestPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleCommand()
        setContent {
            MaterialTheme {
                val status by PlayerState.status.collectAsState()
                var silent by remember { mutableStateOf(true) }
                Scaffold { insets ->
                    Column(
                        Modifier.fillMaxSize().padding(insets).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.testplayer_name))
                        Text(stringResource(if (status.playing) R.string.player_playing else R.string.player_paused))
                        Text(stringResource(R.string.silent_player))
                        Checkbox(checked = silent, onCheckedChange = { silent = it })
                        PlayerMode.entries.forEach { mode ->
                            Button(onClick = { play(mode, silent) }) { Text(mode.name.replace('_', ' ')) }
                        }
                        Button(onClick = {
                            stopService(Intent(this@TestPlayerActivity, TestPlayerService::class.java))
                        }) {
                            Text(stringResource(R.string.player_stop))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCommand()
    }

    private fun handleCommand() {
        when (intent.action) {
            TestPlayerService.ACTION_PLAY -> {
                val mode =
                    PlayerMode.entries.firstOrNull { it.name == intent.getStringExtra("mode") } ?: PlayerMode.GOOD
                play(mode, intent.getBooleanExtra("silent", true))
            }

            TestPlayerService.ACTION_STOP -> stopService(Intent(this, TestPlayerService::class.java))
        }
        intent.action = Intent.ACTION_MAIN
    }

    private fun play(mode: PlayerMode, silent: Boolean) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, TestPlayerService::class.java)
                .setAction(TestPlayerService.ACTION_PLAY).putExtra("mode", mode.name).putExtra("silent", silent),
        )
    }
}
