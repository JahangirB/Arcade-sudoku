package com.arcade.sudoku.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.theme.AppTheme

/** What Android asks for before two phones may find each other directly. */
private fun nearbyPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()

/** Host or find a battle with a phone in the same room, without internet or a server. */
@Composable
fun NearbySection(state: BattleUiState, vm: BattleViewModel) {
    val p = AppTheme.palette
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf(false) }

    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        denied = !granted
        if (granted) pending?.invoke()
        pending = null
    }

    /** Runs [action] once the nearby permissions are granted, asking for them if needed. */
    fun withPermissions(action: () -> Unit) {
        val missing = nearbyPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pending = action
            askPermissions.launch(missing.toTypedArray())
        }
    }

    // Stop looking for hosts when leaving this screen.
    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    AppCard("Play in the same room") {
        Text(
            "Two phones connect directly over Bluetooth and Wi‑Fi. No internet and no server needed. " +
                "Keep both phones close together with Bluetooth on.",
            color = p.muted,
            fontSize = 14.sp,
        )
        if (denied) {
            Text(
                "Nearby battles need the Bluetooth, location, and nearby devices permissions (precise location). " +
                    "You can allow them in the app's settings.",
                color = p.bad,
                fontSize = 13.sp,
            )
        }
    }

    AppCard("Host") {
        GameChooser(state.hostGame, vm::setHostGame)
        Text("Wait here for your friend to find you.", color = p.muted, fontSize = 13.sp)
        PrimaryButton("Host nearby battle", { withPermissions(vm::hostNearby) }, loading = state.busy)
    }

    AppCard("Join") {
        if (!state.scanning) {
            SecondaryButton("Find nearby battles", { withPermissions(vm::startScan) }, enabled = !state.busy)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.primary)
                Spacer(Modifier.width(12.dp))
                Text("Looking for players nearby…", color = p.muted)
            }
            if (state.nearbyHosts.isEmpty()) {
                Text("Ask your friend to tap Host nearby battle.", color = p.muted, fontSize = 13.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.nearbyHosts.forEach { host ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        PlayerAvatar(host.name, p.primary, avatar = host.avatar)
                        Spacer(Modifier.width(12.dp))
                        Text(host.name, color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Button(onClick = { vm.joinNearby(host) }, enabled = !state.busy) { Text("Join") }
                    }
                }
            }
        }
    }
}
