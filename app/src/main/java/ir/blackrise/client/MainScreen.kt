package ir.blackrise.client

import android.app.Activity
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Orange = Color(0xFFF97316)
private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFEF4444)
private val Panel = Color(0xFF141417)
private val Line = Color(0xFF27272A)
private val Bg = Color(0xFF09090B)

enum class Status { IDLE, CONNECTING, CONNECTED }

fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format("%.1f KB", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format("%.1f MB", b / 1048576.0)
    else -> String.format("%.2f GB", b / 1073741824.0)
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val store = remember { ConfigStore(context) }
    val scope = rememberCoroutineScope()

    var lang by remember { mutableStateOf(store.language) }
    var tab by remember { mutableStateOf(0) }
    val profiles = remember { mutableStateListOf<Profile>().also { it.addAll(store.load()) } }
    var selected by remember {
        mutableStateOf(store.selected.coerceIn(0, maxOf(0, profiles.size - 1)))
    }
    var status by remember {
        mutableStateOf(if (VpnController.isUp()) Status.CONNECTED else Status.IDLE)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var rx by remember { mutableStateOf(0L) }
    var tx by remember { mutableStateOf(0L) }
    var showAdd by remember { mutableStateOf(false) }

    fun t(key: String) = tr(lang, key)

    fun doConnect() {
        val p = profiles.getOrNull(selected)
        if (p == null) {
            error = t("no_servers")
            tab = 1
            return
        }
        status = Status.CONNECTING
        error = null
        scope.launch {
            val r = VpnController.connect(context, p.config)
            status = if (r.isSuccess) Status.CONNECTED else Status.IDLE
            r.exceptionOrNull()?.let { error = it.message ?: it.toString() }
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val sbState by SingBoxVpnService.state.collectAsState()
    LaunchedEffect(sbState) {
        if (sbState == CoreState.IDLE && status == Status.CONNECTED && !VpnManager.isUp()) {
            status = Status.IDLE
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) doConnect() else error = t("perm_denied")
    }

    fun onToggle() {
        when (status) {
            Status.CONNECTING -> {}
            Status.CONNECTED -> scope.launch {
                VpnController.disconnect(context)
                status = Status.IDLE
            }
            Status.IDLE -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val intent = VpnService.prepare(context)
                if (intent != null) permLauncher.launch(intent) else doConnect()
            }
        }
    }

    LaunchedEffect(status) {
        if (status == Status.CONNECTED) {
            while (true) {
                VpnController.stats()?.let { rx = it.first; tx = it.second }
                delay(1000)
            }
        } else if (status == Status.IDLE) {
            rx = 0; tx = 0
        }
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Orange,
                    selectedTextColor = Orange,
                    indicatorColor = Line
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(t("home"), fontSize = 11.sp) },
                    selected = tab == 0, onClick = { tab = 0 }, colors = colors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    label = { Text(t("servers"), fontSize = 11.sp) },
                    selected = tab == 1, onClick = { tab = 1 }, colors = colors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(t("settings"), fontSize = 11.sp) },
                    selected = tab == 2, onClick = { tab = 2 }, colors = colors
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (status == Status.CONNECTED) Green else Red)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(t("title"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Panel)
                        .border(1.dp, Line, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    LANGS.forEach { l ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (lang == l) Orange else Color.Transparent)
                                .clickable { lang = l; store.language = l }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                l,
                                color = if (lang == l) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            when (tab) {
                0 -> HomeTab(
                    status = status,
                    profile = profiles.getOrNull(selected),
                    protocol = profiles.getOrNull(selected)?.let { VpnController.protocolName(it.config) } ?: "—",
                    rx = rx, tx = tx, error = error,
                    t = ::t,
                    onToggle = ::onToggle
                )
                1 -> ServersTab(
                    profiles = profiles,
                    selected = selected,
                    connected = status != Status.IDLE,
                    t = ::t,
                    onSelect = { selected = it; store.selected = it },
                    onDelete = { i ->
                        if (status != Status.IDLE) return@ServersTab
                        profiles.removeAt(i)
                        store.save(profiles)
                        selected = selected.coerceIn(0, maxOf(0, profiles.size - 1))
                        store.selected = selected
                    },
                    onAdd = { showAdd = true },
                    error = error
                )
                else -> SettingsTab(t = ::t, onOpenVpnSettings = {
                    context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                })
            }
        }
    }

    if (showAdd) {
        AddDialog(
            t = ::t,
            onDismiss = { showAdd = false },
            onSave = { p ->
                profiles.add(p)
                store.save(profiles)
                if (profiles.size == 1) { selected = 0; store.selected = 0 }
                showAdd = false
                error = null
            }
        )
    }
}

@Composable
private fun HomeTab(
    status: Status,
    profile: Profile?,
    protocol: String,
    rx: Long,
    tx: Long,
    error: String?,
    t: (String) -> String,
    onToggle: () -> Unit
) {
    val on = status == Status.CONNECTED
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(t("protocol"), color = Color.Gray, fontSize = 12.sp)
                        Text(protocol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(t("server"), color = Color.Gray, fontSize = 12.sp)
                        Text(
                            profile?.name ?: "—",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                }
                if (on) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("↓ ${t("download")}: ${formatBytes(rx)}", color = Green, fontSize = 12.sp)
                        Text("↑ ${t("upload")}: ${formatBytes(tx)}", color = Orange, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(56.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (on) listOf(Orange.copy(alpha = 0.4f), Panel)
                        else listOf(Line, Panel)
                    )
                )
                .border(2.dp, if (on) Orange else Line, CircleShape)
                .clickable { onToggle() }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (status == Status.CONNECTING) {
                    CircularProgressIndicator(color = Orange, modifier = Modifier.size(44.dp))
                } else {
                    Icon(
                        imageVector = if (on) Icons.Default.Lock else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (on) Orange else Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (status) {
                        Status.IDLE -> t("connect")
                        Status.CONNECTING -> t("connecting")
                        Status.CONNECTED -> t("connected")
                    },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(24.dp))
            Text(error, color = Red, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ServersTab(
    profiles: List<Profile>,
    selected: Int,
    connected: Boolean,
    t: (String) -> String,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    error: String?
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t("servers"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("add"), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t("hint2"), color = Color.Gray, fontSize = 12.sp)
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Red, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))

        profiles.forEachIndexed { i, p ->
            val isSel = i == selected
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isSel) Orange else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable(enabled = !connected) { onSelect(i) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(VpnController.protocolName(p.config), color = Color.Gray, fontSize = 12.sp)
                    }
                    if (isSel) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Orange))
                    }
                    IconButton(onClick = { onDelete(i) }, enabled = !connected) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsTab(t: (String) -> String, onOpenVpnSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(t("settings"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Core", color = Color.White, fontSize = 14.sp)
            Text("sing-box + WireGuard", color = Color.Gray, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Version", color = Color.White, fontSize = 14.sp)
            Text("1.0.0", color = Color.Gray, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenVpnSettings, modifier = Modifier.fillMaxWidth()) {
            Text(t("vpn_settings"), color = Orange, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AddDialog(
    t: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var cfg by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text != null) cfg = text
            if (name.isBlank()) {
                name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(t("add"), color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(t("name")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cfg, onValueChange = { cfg = it },
                    label = { Text(t("link_label")) },
                    minLines = 5, maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!txt.isNullOrBlank()) cfg = txt.trim()
                }) { Text(t("paste"), color = Orange) }
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text(t("import_file"), color = Orange)
                }
                if (err != null) Text(err!!, color = Red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val text = cfg.trim()
                val problem = VpnController.validate(text)
                if (problem != null) {
                    err = "${t("invalid")}: $problem"
                } else {
                    onSave(Profile(name.ifBlank { VpnController.defaultName(text) }, text))
                }
            }) { Text(t("save"), color = Orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("cancel"), color = Color.Gray) }
        }
    )
}
