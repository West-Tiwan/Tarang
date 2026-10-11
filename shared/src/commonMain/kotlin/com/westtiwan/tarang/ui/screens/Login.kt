package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.data.ServerProfile
import com.westtiwan.tarang.data.SubsonicApi
import com.westtiwan.tarang.platform.nowMillis
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalNav
import com.westtiwan.tarang.ui.MemCache
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(existing: ServerProfile? = null, onDone: (() -> Unit)? = null) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "https://") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var pass by remember { mutableStateOf(existing?.password ?: "") }
    var legacy by remember { mutableStateOf(existing?.legacyAuth ?: false) }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (existing == null) {
                Spacer(Modifier.height(32.dp))
                Text("Tarang", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text("Connect to your Navidrome or any Subsonic compatible server.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(url, { url = it.trim() }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), placeholder = { Text("https://music.example.com") })
            OutlinedTextField(user, { user = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton({ showPass = !showPass }) { Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) } },
            )
            OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(legacy, { legacy = it })
                Text("Use plain password auth (for LDAP or older servers)", style = MaterialTheme.typography.bodyMedium)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy && url.length > 8 && user.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val cleanUrl = url.trimEnd('/').let { if (!it.startsWith("http")) "https://$it" else it }
                        val profile = ServerProfile(
                            id = existing?.id ?: nowMillis().toString(36),
                            name = name.ifBlank { cleanUrl.substringAfter("://").substringBefore('/') },
                            url = cleanUrl, username = user, password = pass, legacyAuth = legacy,
                        )
                        runCatching { SubsonicApi(profile, app.http).ping() }
                            .onSuccess {
                                MemCache.clear()
                                app.addServer(profile)
                                onDone?.invoke()
                            }
                            .onFailure { error = "Could not connect: ${it.message}" }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text(if (existing == null) "Connect" else "Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val settings by app.settings.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Servers") }, navigationIcon = { IconButton({ if (adding || editing != null) { adding = false; editing = null } else nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } })
        when {
            adding -> LoginScreen(onDone = { adding = false })
            editing != null -> LoginScreen(editing, onDone = { editing = null })
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                settings.servers.forEach { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text("${s.username} @ ${s.url}") },
                        leadingContent = { Icon(if (s.id == settings.activeServerId) Icons.Rounded.Check else Icons.Rounded.Dns, null) },
                        trailingContent = {
                            Row {
                                IconButton({ editing = s }) { Icon(Icons.Rounded.Edit, "Edit") }
                                IconButton({ app.removeServer(s.id) }) { Icon(Icons.Rounded.Delete, "Remove") }
                            }
                        },
                        modifier = Modifier.clickable {
                            MemCache.clear()
                            app.updateSettings { it.copy(activeServerId = s.id) }
                        },
                    )
                }
                androidx.compose.material3.TextButton({ adding = true }, Modifier.padding(16.dp)) { Text("Add server") }
            }
        }
    }
}
