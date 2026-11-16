package com.socks5proxy.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.model.TunnelState
import com.socks5proxy.app.model.TunnelStatus
import com.socks5proxy.app.ui.MainUiState
import com.socks5proxy.app.ui.MainViewModel
import com.socks5proxy.app.ui.dialogs.AppSelectorDialog
import com.socks5proxy.app.ui.dialogs.ProfileEditorDialog
import com.socks5proxy.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    tunnelStatus: TunnelStatus,
    onToggleConnection: () -> Unit,
    viewModel: MainViewModel
) {
    val scrollState = rememberScrollState()
    val config = uiState.config
    val activeProfile = config.activeProfile
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isProxyCopied by remember { mutableStateOf(false) }

    val selectedAppList = remember(uiState.installedApps, config.allowedApps) {
        uiState.installedApps.filter { config.allowedApps.contains(it.packageName) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SOCKS5 Proxy",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) Color(0x3322C55E) else SurfaceCard)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) "Без сброса VPN" else "VPN Режим",
                                color = if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) StatusGreen else AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkForUpdates() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Проверить обновления",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.updateInfo?.let { update ->
                if (update.hasUpdate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2D1F)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(StatusGreen))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Новая версия: v${update.latestVersion}",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(onClick = { viewModel.dismissUpdate() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextMuted)
                                }
                            }

                            if (uiState.isDownloadingUpdate) {
                                LinearProgressIndicator(
                                    progress = { uiState.downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = StatusGreen
                                )
                                Text(
                                    text = "Загрузка: ${uiState.downloadProgress}%",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            } else {
                                Button(
                                    onClick = { viewModel.downloadAndInstallUpdate() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = BgDark)
                                ) {
                                    Text("Обновить приложение сейчас", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isRunning = tunnelStatus.isRunning
                    val buttonBg = when (tunnelStatus.state) {
                        TunnelState.CONNECTED -> StatusGreen
                        TunnelState.CONNECTING -> StatusAmber
                        TunnelState.ERROR -> StatusRed
                        TunnelState.DISCONNECTED -> SurfaceCard
                    }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(buttonBg)
                            .border(3.dp, if (isRunning) TextPrimary else BorderDark, CircleShape)
                            .clickable(enabled = config.isValid) {
                                onToggleConnection()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (tunnelStatus.state == TunnelState.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = TextPrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (tunnelStatus.state == TunnelState.DISCONNECTED) TextMuted else TextPrimary,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when (tunnelStatus.state) {
                            TunnelState.CONNECTED -> "АКТИВЕН"
                            TunnelState.CONNECTING -> "ПОДКЛЮЧЕНИЕ..."
                            TunnelState.ERROR -> "ОШИБКА"
                            TunnelState.DISCONNECTED -> "ОТКЛЮЧЕН"
                        },
                        color = when (tunnelStatus.state) {
                            TunnelState.CONNECTED -> StatusGreen
                            TunnelState.CONNECTING -> StatusAmber
                            TunnelState.ERROR -> StatusRed
                            TunnelState.DISCONNECTED -> TextMuted
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (tunnelStatus.state == TunnelState.CONNECTED) {
                        Text(
                            text = tunnelStatus.formattedDuration(),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (!tunnelStatus.message.isNullOrBlank()) {
                        Text(
                            text = tunnelStatus.message,
                            color = if (tunnelStatus.state == TunnelState.ERROR) StatusRed else TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderDark)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem(
                            label = "Входящий (RX)",
                            value = TunnelStatus.formatBytes(tunnelStatus.bytesIn),
                            icon = Icons.Default.ArrowDownward
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(BorderDark)
                        )
                        MetricItem(
                            label = "Исходящий (TX)",
                            value = TunnelStatus.formatBytes(tunnelStatus.bytesOut),
                            icon = Icons.Default.ArrowUpward
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Режим работы прокси",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeCard(
                            title = "Без сброса VPN",
                            subtitle = "127.0.0.1:${config.localListenPort}",
                            isSelected = config.engineMode == ProxyEngineMode.LOCAL_PROXY,
                            enabled = !tunnelStatus.isRunning,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setEngineMode(ProxyEngineMode.LOCAL_PROXY) }
                        )

                        ModeCard(
                            title = "Системный VPN",
                            subtitle = "Перехват ОС",
                            isSelected = config.engineMode == ProxyEngineMode.SYSTEM_VPN,
                            enabled = !tunnelStatus.isRunning,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setEngineMode(ProxyEngineMode.SYSTEM_VPN) }
                        )
                    }

                    val isVpnActive = remember(tunnelStatus.state) { com.socks5proxy.app.network.VpnDetector.isExternalVpnActive(context) }
                    if (isVpnActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1F22C55E))
                                .border(1.dp, StatusGreen, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Обнаружен активный сторонний VPN. Трафик пойдет через него в SOCKS5 без сброса соединения.",
                                    color = StatusGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Text(
                        text = if (config.engineMode == ProxyEngineMode.LOCAL_PROXY)
                            "В режиме «Без сброса VPN» приложение не занимает системный слот VPN. Ваш текущий VPN продолжает работать, а трафик в прокси идет через него."
                        else
                            "В режиме «Системный VPN» приложение перехватывает сетевой трафик ОС через VpnService (может отключить другой активный VPN).",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Прокси-серверы (${config.profiles.size})",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = { viewModel.openAddProfile() },
                            enabled = !tunnelStatus.isRunning,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = AccentCyan)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Добавить", fontSize = 12.sp)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        config.profiles.forEach { profile ->
                            ProfileRowItem(
                                profile = profile,
                                isActive = profile.id == config.activeProfileId || (config.activeProfileId.isEmpty() && profile == config.profiles.firstOrNull()),
                                onSelect = { viewModel.selectActiveProfile(profile.id) },
                                onEdit = { viewModel.openEditProfile(profile) },
                                onDelete = { viewModel.deleteProfile(profile.id) },
                                canDelete = config.profiles.size > 1,
                                enabled = !tunnelStatus.isRunning
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.checkProxyPing() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isCheckingPing && activeProfile.isValid,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceCard,
                            contentColor = AccentCyan
                        )
                    ) {
                        if (uiState.isCheckingPing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentCyan, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Проверка рукопожатия SOCKS5...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Проверить доступность: ${activeProfile.name}", fontSize = 13.sp)
                        }
                    }

                    uiState.pingResult?.let { res ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (res.isSuccess) Color(0x1F22C55E) else Color(0x1FEF4444))
                                .border(1.dp, if (res.isSuccess) StatusGreen else StatusRed, RoundedCornerShape(8.dp))
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    contentDescription = res.message
                                }
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (res.isSuccess) StatusGreen else StatusRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.message,
                                    color = if (res.isSuccess) StatusGreen else StatusRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Маршрутизация приложений",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = config.isPerAppRoutingEnabled,
                                role = Role.Switch,
                                onValueChange = { viewModel.togglePerAppRouting(it) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Выборочная маршрутизация (Выбор приложений)", color = TextPrimary, fontSize = 14.sp)
                            Text("Проксировать только выбранные приложения", color = TextMuted, fontSize = 12.sp)
                        }

                        Switch(
                            checked = config.isPerAppRoutingEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = AccentCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceCard,
                                uncheckedBorderColor = BorderDark
                            )
                        )
                    }

                    if (config.isPerAppRoutingEnabled) {
                        OutlinedButton(
                            onClick = { viewModel.setAppSelectorVisible(true) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCard, contentColor = TextPrimary),
                            border = BorderStroke(1.dp, BorderDark)
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выбрать приложения (${config.allowedApps.size} выбрано)", fontSize = 14.sp)
                        }

                        if (selectedAppList.isNotEmpty()) {
                            Text(
                                text = "Выбранные приложения (${selectedAppList.size}):",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                items(selectedAppList, key = { it.packageName }) { app ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SurfaceCard)
                                            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (app.iconBitmap != null) {
                                            Image(
                                                bitmap = app.iconBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF26303D)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = app.appName.take(1).uppercase(),
                                                    color = TextMuted,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text(
                                            text = app.appName,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Удалить",
                                            tint = TextMuted,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.toggleAppSelection(app.packageName) }
                                        )
                                    }
                                }
                            }
                        }

                        if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
                            HorizontalDivider(color = BorderDark)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Быстрое применение (Без сброса VPN):",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val actualPort = if (tunnelStatus.boundPort > 0) tunnelStatus.boundPort else config.localListenPort
                                val isTelegramSelected = config.allowedApps.any { it.contains("telegram", ignoreCase = true) }

                                Button(
                                    onClick = {
                                        val tgUri = Uri.parse("tg://socks?server=127.0.0.1&port=$actualPort")
                                        val intent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val webIntent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://t.me/socks?server=127.0.0.1&port=$actualPort")
                                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                            context.startActivity(webIntent)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTelegramSelected) AccentCyan else SurfaceCard,
                                        contentColor = if (isTelegramSelected) BgDark else AccentCyan
                                    )
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Применить прокси в Telegram в 1 клик", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString("127.0.0.1:$actualPort"))
                                        isProxyCopied = true
                                        coroutineScope.launch {
                                            delay(2000)
                                            isProxyCopied = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = SurfaceCard,
                                        contentColor = if (isProxyCopied) StatusGreen else TextPrimary
                                    ),
                                    border = BorderStroke(1.dp, if (isProxyCopied) StatusGreen else BorderDark)
                                ) {
                                    Icon(
                                        imageVector = if (isProxyCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = if (isProxyCopied) StatusGreen else AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isProxyCopied) "Скопировано: 127.0.0.1:$actualPort" else "Скопировать адрес для браузера (127.0.0.1:$actualPort)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(BorderDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Параметры надежности",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = config.isFallbackDirectEnabled,
                                role = Role.Switch,
                                onValueChange = { viewModel.toggleFallbackDirect(it) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Прямой обход при сбое (Direct Fallback)", color = TextPrimary, fontSize = 14.sp)
                            Text("Если прокси недоступен, пускать трафик напрямую через активный интернет/VPN", color = TextMuted, fontSize = 12.sp)
                        }

                        Switch(
                            checked = config.isFallbackDirectEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = AccentCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceCard,
                                uncheckedBorderColor = BorderDark
                            )
                        )
                    }

                    HorizontalDivider(color = BorderDark)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = config.isAutoStartEnabled,
                                role = Role.Switch,
                                onValueChange = { viewModel.toggleAutoStart(it) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Автозапуск при включении (Boot)", color = TextPrimary, fontSize = 14.sp)
                            Text("Автоматический запуск службы прокси после перезагрузки смартфона", color = TextMuted, fontSize = 12.sp)
                        }

                        Switch(
                            checked = config.isAutoStartEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = AccentCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceCard,
                                uncheckedBorderColor = BorderDark
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (uiState.isProfileEditorOpen && uiState.editingProfile != null) {
        ProfileEditorDialog(
            profile = uiState.editingProfile,
            onSave = { updated -> viewModel.saveProfile(updated) },
            onDismiss = { viewModel.closeProfileEditor() }
        )
    }

    if (uiState.isAppSelectorOpen) {
        AppSelectorDialog(
            apps = uiState.installedApps,
            selectedPackages = config.allowedApps,
            onToggleApp = { pkg -> viewModel.toggleAppSelection(pkg) },
            onSelectAll = { viewModel.selectAllApps() },
            onClearAll = { viewModel.clearAllApps() },
            onDismiss = { viewModel.setAppSelectorVisible(false) }
        )
    }
}

@Composable
private fun ProfileRowItem(
    profile: ProxyProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0x1A38BDF8) else SurfaceCard)
            .border(1.dp, if (isActive) AccentCyan else BorderDark, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onSelect() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isActive,
                onClick = if (enabled) onSelect else null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AccentCyan,
                    unselectedColor = TextMuted
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = profile.name,
                    color = if (isActive) AccentCyan else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${profile.host}:${profile.port}${if (profile.username.isNotEmpty()) " (auth)" else ""}",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row {
            IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            if (canDelete) {
                IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = StatusRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0x2438BDF8) else SurfaceCard)
            .border(1.dp, if (isSelected) AccentCyan else BorderDark, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (isSelected) AccentCyan else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = TextMuted, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
