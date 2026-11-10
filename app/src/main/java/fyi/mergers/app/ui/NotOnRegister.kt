package fyi.mergers.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A well-formed merger id the register doesn't have (yet). A party or its
 * adviser knows the number before the ACCC publishes it, and a waiver
 * application is published only once it's decided, so its appearance is the
 * news: offer to follow it anyway, and say so once it is followed.
 */
@Composable
fun NotOnRegister(vm: AppViewModel, id: String, followed: Boolean, modifier: Modifier = Modifier) {
    val haptic = rememberFollowHaptics()
    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("not-on-register"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (followed) "Following $id" else "$id isn't on the register",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (followed) {
                    "It isn't on the register yet. You'll be notified when it appears."
                } else {
                    "No merger or waiver has this number yet. A waiver application appears only once it's " +
                        "decided, so if you know the number, follow it to be notified when it does."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (followed) {
                OutlinedButton(onClick = { haptic(vm.toggleFollow(id)) }) { Text("Unfollow") }
            } else {
                Button(onClick = {
                    haptic(vm.toggleFollow(id))
                    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Follow anyway") }
            }
        }
    }
}
