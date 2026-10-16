package com.ezymusy.app.feature.library

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezymusy.app.R
import com.ezymusy.app.core.bluetooth.AutoConnect
import com.ezymusy.app.core.bluetooth.BtDevice
import com.ezymusy.app.core.designsystem.Dimens

/** Bluetooth button for the Library header; filled icon while auto-connect is on. */
@Composable
fun AutoConnectButton(autoConnect: AutoConnect, modifier: Modifier = Modifier) {
    val setting by autoConnect.setting.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.testTag("auto_connect")) {
        Icon(
            if (setting.enabled) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
            contentDescription = stringResource(R.string.auto_connect),
        )
    }
    if (open) AutoConnectDialog(autoConnect, onDismiss = { open = false })
}

@Composable
private fun AutoConnectDialog(autoConnect: AutoConnect, onDismiss: () -> Unit) {
    val setting by autoConnect.setting.collectAsStateWithLifecycle()
    // Bumped on resume and after the permission answer, so pairing in Settings shows up on return.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val context = LocalContext.current
    val granted = remember(refresh) { autoConnect.hasPermission }
    val paired = remember(refresh) { autoConnect.paired() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_connect)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.auto_connect_body))
                when {
                    !autoConnect.hasAdapter -> Text(stringResource(R.string.auto_connect_no_adapter))
                    !granted -> {
                        Text(stringResource(R.string.auto_connect_permission))
                        TextButton(onClick = { autoConnect.permission?.let(askPermission::launch) }) {
                            Text(stringResource(R.string.auto_connect_allow))
                        }
                    }
                    paired.isEmpty() -> {
                        Text(stringResource(R.string.auto_connect_no_paired))
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                            Text(stringResource(R.string.auto_connect_settings))
                        }
                    }
                    else -> {
                        EnabledSwitch(
                            checked = setting.enabled,
                            enabled = setting.device != null,
                            onCheckedChange = autoConnect::setEnabled,
                        )
                        Text(stringResource(R.string.auto_connect_pick), style = MaterialTheme.typography.titleSmall)
                        DeviceList(paired, selected = setting.device?.address, onPick = autoConnect::pick)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun EnabledSwitch(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget),
    ) {
        val label = stringResource(R.string.auto_connect_switch)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Same off-thumb colors as Include in Shuffle all: the theme's outline matches the track.
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .semantics { contentDescription = label }
                .testTag("auto_connect_switch"),
        )
    }
}

@Composable
private fun DeviceList(devices: List<BtDevice>, selected: String?, onPick: (BtDevice) -> Unit) {
    Column(Modifier.selectableGroup()) {
        devices.forEach { device ->
            val isSelected = device.address.equals(selected, ignoreCase = true)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTarget)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(device) }),
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
