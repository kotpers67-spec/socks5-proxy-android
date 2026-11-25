package com.socks5proxy.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.model.TunnelState
import com.socks5proxy.app.model.TunnelStatus
import com.socks5proxy.app.ui.MainUiState
import com.socks5proxy.app.ui.MainViewModel
import com.socks5proxy.app.ui.components.AppSelectorDialog
import com.socks5proxy.app.ui.components.ProfileEditorDialog
import com.socks5proxy.app.ui.theme.NeumorphismTheme
import com.socks5proxy.app.ui.theme.Socks5ProxyTheme
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    uiState: MainUiState,
    tunnelStatus: TunnelStatus,
    onToggleConnection: () -> Unit,
    viewModel: MainViewModel
) {
    Socks5ProxyTheme(darkTheme = uiState.isDarkTheme) {
        val colors = NeumorphismTheme.colors
        val context = LocalContext.current
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgOuter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 88.dp)
            ) {
                AppTopHeader(
                    uiState = uiState,
                    onToggleTheme = { viewModel.toggleDarkTheme() },
                    onRefresh = {
                        viewModel.checkProxyPing()
                        val modeLabel = if (uiState.config.engineMode == ProxyEngineMode.VPN_PROFILE) "VPN" else "SOCKS5"
                        viewModel.showToast("Состояние службы $modeLabel обновлено")
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (uiState.activeTab) {
                        0 -> HomeTabContent(
                            uiState = uiState,
                            tunnelStatus = tunnelStatus,
                            onToggleConnection = onToggleConnection,
                            viewModel = viewModel,
                            context = context
                        )
                        1 -> ServersTabContent(
                            uiState = uiState,
                            tunnelStatus = tunnelStatus,
                            viewModel = viewModel
                        )
                        2 -> SettingsTabContent(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.bgBase.copy(alpha = 0.95f), colors.bgBase)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BottomNavTabs(
                        activeTab = uiState.activeTab,
                        onSelectTab = { viewModel.setActiveTab(it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.textMuted.copy(alpha = 0.35f))
                    )
                }
            }

            ToastOverlay(
                message = uiState.toastMessage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            )

            if (uiState.isProfileEditorOpen && uiState.editingProfile != null) {
                ProfileEditorDialog(
                    profile = uiState.editingProfile,
                    canDelete = uiState.config.profiles.any { it.id == uiState.editingProfile.id },
                    onSave = { viewModel.saveProfile(it) },
                    onDismiss = { viewModel.closeProfileEditor() },
                    onDelete = { viewModel.deleteProfile(it) },
                    onShowToast = { viewModel.showToast(it) }
                )
            }

            if (uiState.isAppSelectorOpen) {
                AppSelectorDialog(
                    apps = uiState.installedApps,
                    selectedPackages = uiState.config.allowedApps,
                    onToggleApp = { viewModel.toggleAppSelection(it) },
                    onSelectAll = { viewModel.selectAllApps() },
                    onClearAll = { viewModel.clearAllApps() },
                    onDismiss = { viewModel.setAppSelectorVisible(false) }
                )
            }

            if (uiState.isVpnImportDialogOpen) {
                VpnImportDialog(
                    isImporting = uiState.isImportingVpn,
                    error = uiState.vpnImportError,
                    onImport = viewModel::importVpnServers,
                    onDismiss = { viewModel.closeVpnImportDialog() }
                )
            }

            val update = uiState.updateInfo
            if (uiState.isUpdateDialogVisible && update != null) {
                UpdateAvailableDialog(
                    updateInfo = update,
                    isDownloading = uiState.isDownloadingUpdate,
                    progress = uiState.updateDownloadProgress,
                    downloadedBytes = uiState.downloadedBytes,
                    totalBytes = uiState.totalBytes,
                    isResuming = uiState.isResumingDownload,
                    isReady = uiState.isUpdateApkReady,
                    onInstall = { viewModel.downloadAndInstallUpdate() },
                    onSnooze = { viewModel.snoozeUpdate() },
                    onDismiss = { viewModel.dismissUpdate() }
                )
            }
        }
    }
}

@Composable
private fun AppTopHeader(
    uiState: MainUiState,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = NeumorphismTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SOCKS5 Proxy",
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.insetBg)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (uiState.config.engineMode) {
                        ProxyEngineMode.LOCAL_PROXY -> "Локальный SOCKS"
                        ProxyEngineMode.SYSTEM_VPN -> "Системный VPN"
                        ProxyEngineMode.VPN_PROFILE -> "Встроенный VPN"
                    },
                    style = TextStyle(
                        color = if (uiState.config.engineMode == ProxyEngineMode.LOCAL_PROXY) colors.accent else colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    )
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Тема",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    )
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Обновить",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    uiState: MainUiState,
    tunnelStatus: TunnelStatus,
    onToggleConnection: () -> Unit,
    viewModel: MainViewModel,
    context: Context
) {
    val colors = NeumorphismTheme.colors
    val activeProfile = uiState.config.activeProfile
    var timerNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(tunnelStatus.state, tunnelStatus.connectedAtMillis) {
        if (tunnelStatus.state == TunnelState.CONNECTED && tunnelStatus.connectedAtMillis > 0L) {
            while (true) {
                timerNowMillis = System.currentTimeMillis()
                delay(1000L)
            }
        } else {
            timerNowMillis = System.currentTimeMillis()
        }
    }

    val listenPort = if (tunnelStatus.boundPort > 0) tunnelStatus.boundPort else uiState.config.localListenPort
    var lastCopiedAddr by remember { mutableStateOf<String?>(null) }
    val isCopiedActive = lastCopiedAddr == "127.0.0.1:$listenPort"
    val copiedText = if (isCopiedActive) {
        "Скопировано: 127.0.0.1:$listenPort"
    } else {
        "Скопировать адрес для браузера (127.0.0.1:$listenPort)"
    }

    NeumorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeroPowerBezel(
                isConnected = tunnelStatus.isRunning,
                onClick = onToggleConnection
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = when (tunnelStatus.state) {
                    TunnelState.CONNECTED -> "АКТИВЕН"
                    TunnelState.CONNECTING -> "ПОДКЛЮЧЕНИЕ..."
                    TunnelState.ERROR -> "ОШИБКА"
                    TunnelState.DISCONNECTED -> "ОТКЛЮЧЕН"
                },
                style = TextStyle(
                    color = when (tunnelStatus.state) {
                        TunnelState.CONNECTED -> colors.accent
                        TunnelState.CONNECTING -> colors.accentBright
                        TunnelState.ERROR -> colors.danger
                        TunnelState.DISCONNECTED -> colors.textMuted
                    },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tunnelStatus.formattedDuration(timerNowMillis),
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            )

            if (tunnelStatus.state == TunnelState.CONNECTED) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.insetBg)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                            .clickable(enabled = !uiState.isCheckingPing) { viewModel.checkProxyPing() }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        if (uiState.isCheckingPing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = colors.accent,
                                strokeWidth = 1.5.dp
                            )
                            Text(
                                text = "Пинг...",
                                style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            )
                        } else if (uiState.pingResult != null) {
                            val isOk = uiState.pingResult.isSuccess
                            val pingColor = if (isOk) {
                                if (uiState.pingResult.latencyMs < 150) colors.accent
                                else if (uiState.pingResult.latencyMs < 350) colors.accent.copy(alpha = 0.8f)
                                else colors.danger
                            } else colors.danger
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = pingColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isOk) "${uiState.pingResult.latencyMs} мс" else "Сбой сети",
                                style = TextStyle(
                                    color = pingColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Пинг",
                                style = TextStyle(color = colors.textMuted, fontSize = 11.sp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.insetBg)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                            .clickable(enabled = !uiState.isCheckingPublicIp) { viewModel.checkPublicIp() }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        if (uiState.isCheckingPublicIp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = colors.accent,
                                strokeWidth = 1.5.dp
                            )
                            Text(
                                text = "IP...",
                                style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            )
                        } else if (uiState.publicIpResult != null && uiState.publicIpResult.isSuccess) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = uiState.publicIpResult.message,
                                style = TextStyle(
                                    color = colors.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Узнать IP",
                                style = TextStyle(color = colors.textMuted, fontSize = 11.sp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.insetBg)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    if (uiState.config.engineMode == ProxyEngineMode.VPN_PROFILE) {
                        val vpn = uiState.config.activeVpnProfile
                        val chainText = if (uiState.config.isVpnToSocksChainEnabled && activeProfile.isValid) {
                            " → SOCKS5 ${activeProfile.host}:${activeProfile.port}"
                        } else ""
                        Text(
                            text = "TUN → ${vpn?.name ?: "VPN-сервер не выбран"}",
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (vpn != null) "${vpn.protocol.uppercase()} · ${vpn.host}:${vpn.port}$chainText" else "Импортируйте сервер во вкладке «Серверы»",
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        val portNote = if (tunnelStatus.boundPort > 0 && tunnelStatus.boundPort != uiState.config.localListenPort) {
                            " (порт ${uiState.config.localListenPort} занят)"
                        } else {
                            ""
                        }
                        val prefix = if (uiState.config.engineMode == ProxyEngineMode.SYSTEM_VPN) "TUN" else "127.0.0.1:$listenPort$portNote"
                        Text(
                            text = "$prefix →",
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = if (activeProfile.isValid) "${activeProfile.host}:${activeProfile.port}" else "Нажмите кнопку ниже для настройки SOCKS5",
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.insetBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Входящий (RX)",
                                style = TextStyle(
                                    color = colors.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tunnelStatus.formattedBytesIn(),
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = tunnelStatus.formattedRxSpeed(),
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.insetBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Исходящий (TX)",
                                style = TextStyle(
                                    color = colors.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tunnelStatus.formattedBytesOut(),
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = tunnelStatus.formattedTxSpeed(),
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            if (uiState.config.engineMode == ProxyEngineMode.LOCAL_PROXY &&
                tunnelStatus.state == TunnelState.CONNECTED &&
                tunnelStatus.bytesIn == 0L && tunnelStatus.bytesOut == 0L
            ) {
                val listenPort = if (tunnelStatus.boundPort > 0) tunnelStatus.boundPort else uiState.config.localListenPort
                Text(
                    text = "Ожидает трафик приложения: настройте SOCKS5 127.0.0.1:$listenPort в приложении-клиенте.",
                    style = TextStyle(
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TrafficSparklineView(
                isRunning = tunnelStatus.isRunning,
                rxSpeedBps = tunnelStatus.rxSpeedBps,
                txSpeedBps = tunnelStatus.txSpeedBps
            )
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
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
                    text = "Режим работы прокси",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.insetBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                text = if (uiState.config.engineMode == ProxyEngineMode.VPN_PROFILE) "X-RAY" else "SOCKS5",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            SegmentedModeSelector(
                currentMode = uiState.config.engineMode,
                localPort = uiState.config.localListenPort,
                onSelectMode = { viewModel.setEngineMode(it) }
            )

            AnimatedVisibility(visible = uiState.config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.insetBg)
                            .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 1.dp)
                            )
                            Text(
                                text = "Локальный режим не перенаправляет весь трафик устройства. Он запускает SOCKS5 на 127.0.0.1; подключите к этому адресу нужные приложения.",
                                style = TextStyle(
                                    color = colors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }

                    Text(
                        text = "Этот режим не меняет системные маршруты. Для перенаправления всего трафика выберите «Системный VPN» и отключите другие VPN-приложения.",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }

            AnimatedVisibility(visible = uiState.config.engineMode == ProxyEngineMode.SYSTEM_VPN) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.insetBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 1.dp)
                        )
                        Text(
                            text = "Системный TUN-интерфейс перехватывает весь трафик выбранных приложений на уровне ОС Android.",
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(visible = uiState.config.engineMode == ProxyEngineMode.VPN_PROFILE) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.insetBg)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Встроенный Xray подключается к выбранному узлу. Android разрешает только один VPN-интерфейс: при активном стороннем VPN приложение предупредит и не запустит режим. Локальный SOCKS оставляет другое VPN активным.",
                            style = TextStyle(color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Цепочка VPN → SOCKS5", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("VPN-узел, затем выбранный SOCKS5", color = colors.textMuted, fontSize = 11.sp)
                        }
                        NeumorphicSwitch(
                            checked = uiState.config.isVpnToSocksChainEnabled,
                            onCheckedChange = viewModel::toggleVpnToSocksChain
                        )
                    }

                    if (uiState.config.isPerAppRoutingEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.insetBg)
                                .clickable { viewModel.setAppSelectorVisible(true) }
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Выбрать приложения (${uiState.config.allowedApps.size})",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Подключить приложение к локальному SOCKS5:",
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    )
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .clickable {
                        val listenPort = if (tunnelStatus.boundPort > 0) tunnelStatus.boundPort else uiState.config.localListenPort
                        val uriStr = "tg:" + "/" + "/socks?server=127.0.0.1&port=$listenPort"
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            viewModel.showToast("Применение $uriStr")
                        } catch (e: Exception) {
                            viewModel.showToast("Telegram не найден на устройстве")
                        }
                    }
                    .padding(vertical = 13.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Добавить SOCKS5 в Telegram",
                        style = TextStyle(
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    )
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .clickable {
                        val addr = "127.0.0.1:$listenPort"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("SOCKS5 Proxy", addr))
                        lastCopiedAddr = addr
                        viewModel.showToast("Адрес $addr скопирован в буфер")
                    }
                    .padding(vertical = 13.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = if (isCopiedActive) colors.accent else colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = copiedText,
                        style = TextStyle(
                            color = if (isCopiedActive) colors.accent else colors.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ServersTabContent(
    uiState: MainUiState,
    tunnelStatus: TunnelStatus,
    viewModel: MainViewModel
) {
    val colors = NeumorphismTheme.colors
    val activeProfile = uiState.config.activeProfile
    val checkingLocalRoute = uiState.config.engineMode == ProxyEngineMode.LOCAL_PROXY &&
            tunnelStatus.state == TunnelState.CONNECTED && tunnelStatus.boundPort in 1..65535

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VPN-серверы (${uiState.config.vpnProfiles.size})",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Xray · VLESS · VMess · Trojan · SS · SOCKS", color = colors.textMuted, fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.config.vpnProfiles.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .clickable { viewModel.clearAllVpnProfiles() }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        ) {
                            Text("Очистить", color = colors.danger, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .clickable(enabled = !uiState.isPingingAll) { viewModel.pingAllVpnProfiles() }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        ) {
                            if (uiState.isPingingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = colors.accent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text("Пинг всех", color = colors.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(colors.gradStart, colors.gradEnd)))
                            .clickable { viewModel.openVpnImportDialog() }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Text("Импорт", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (uiState.config.vpnProfiles.isNotEmpty()) {
                val hasSubscriptions = uiState.config.vpnProfiles.any { it.sourceUrl.startsWith("https://", ignoreCase = true) }
                val hasPingResults = uiState.config.vpnProfiles.any { uiState.serverPingMap[it.id] != null }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasSubscriptions) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .clickable(enabled = !uiState.isRefreshingSubscriptions) { viewModel.refreshVpnSubscriptions() }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (uiState.isRefreshingSubscriptions) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = colors.accent, strokeWidth = 1.5.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
                                }
                                Text("Подписка", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (hasPingResults) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .clickable { viewModel.sortVpnProfilesByPing() }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                                Text("По пингу", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable { viewModel.selectBestVpnProfile() }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text("Лучший", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (uiState.config.vpnProfiles.isEmpty()) {
                Text(
                    text = "Вставьте ссылку на сервер или подписку, чтобы загрузить список узлов.",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            } else {
                uiState.config.vpnProfiles.forEach { server ->
                    val selected = server.id == uiState.config.activeVpnProfileId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) colors.accent.copy(alpha = 0.09f) else colors.insetBg)
                            .border(
                                1.dp,
                                if (selected) colors.accent.copy(alpha = 0.4f) else colors.borderSubtle,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable {
                                viewModel.selectActiveVpnProfile(server.id)
                                viewModel.setEngineMode(ProxyEngineMode.VPN_PROFILE)
                            }
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (selected) colors.accent else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, color = colors.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${server.protocol.uppercase()} · ${server.host}:${server.port}", color = colors.textMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        val ping = uiState.serverPingMap[server.id]
                        val isPinging = uiState.pingingServerIds.contains(server.id)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable(enabled = !isPinging) { viewModel.pingVpnProfile(server.id) }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = colors.accent,
                                    strokeWidth = 1.5.dp
                                )
                            } else if (ping != null) {
                                val pingColor = when {
                                    !ping.isSuccess -> colors.danger
                                    ping.latencyMs < 150 -> colors.accent
                                    ping.latencyMs < 350 -> colors.accent.copy(alpha = 0.8f)
                                    else -> colors.danger.copy(alpha = 0.8f)
                                }
                                Text(
                                    text = if (ping.isSuccess) "${ping.latencyMs} мс" else "Ошибка",
                                    color = pingColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text("пинг", color = colors.textMuted, fontSize = 10.sp)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable { viewModel.deleteVpnProfile(server.id) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Удалить сервер",
                                tint = colors.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
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
                    text = "Прокси-серверы (${uiState.config.profiles.size})",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.config.profiles.any { it.isValid }) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .clickable(enabled = !uiState.isPingingAllSocks) { viewModel.pingAllSocksProfiles() }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            if (uiState.isPingingAllSocks) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), color = colors.accent, strokeWidth = 1.5.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
                                    Text("Пинг", color = colors.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(colors.gradStart, colors.gradEnd)
                                )
                            )
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .clickable { viewModel.openAddProfile() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Добавить",
                                style = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.config.profiles.forEach { profile ->
                    val isSelected = profile.id == uiState.config.activeProfileId ||
                            (uiState.config.activeProfileId.isEmpty() && profile == uiState.config.profiles.firstOrNull())

                    ServerItemRow(
                        profile = profile,
                        isSelected = isSelected,
                        onSelect = { viewModel.selectActiveProfile(profile.id) },
                        onEdit = { viewModel.openEditProfile(profile) },
                        ping = uiState.serverPingMap[profile.id],
                        isPinging = uiState.pingingServerIds.contains(profile.id),
                        onPing = { viewModel.pingSocksProfile(profile.id) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    )
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .clickable(enabled = !uiState.isCheckingPing) { viewModel.checkProxyPing() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isCheckingPing) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = when {
                            uiState.isCheckingPing -> "Проверка..."
                            checkingLocalRoute -> "Проверить маршрут через локальный SOCKS"
                            else -> "Проверить внешний SOCKS: ${activeProfile.name}"
                        },
                        style = TextStyle(
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.insetBg)
                    .border(
                        1.dp,
                        if (uiState.pingResult?.isSuccess == true) colors.accent.copy(alpha = 0.35f) else colors.borderSubtle,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                uiState.pingResult == null -> Icons.Default.CheckCircle
                                uiState.pingResult.isSuccess -> Icons.Default.CheckCircle
                                else -> Icons.Default.ErrorOutline
                            },
                            contentDescription = null,
                            tint = when {
                                uiState.pingResult == null -> colors.accent
                                uiState.pingResult.isSuccess -> colors.accent
                                else -> colors.danger
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                uiState.pingResult == null -> "Готов к проверке"
                                uiState.pingResult.isSuccess && checkingLocalRoute -> "Локальный SOCKS работает (${uiState.pingResult.latencyMs} мс)"
                                uiState.pingResult.isSuccess && tunnelStatus.state == TunnelState.CONNECTED -> "Интернет через туннель доступен (${uiState.pingResult.latencyMs} мс)"
                                uiState.pingResult.isSuccess -> "Узел доступен (${uiState.pingResult.latencyMs} мс)"
                                else -> "Ошибка (${uiState.pingResult.message})"
                            },
                            style = TextStyle(
                                color = when {
                                    uiState.pingResult == null -> colors.accent
                                    uiState.pingResult.isSuccess -> colors.accent
                                    else -> colors.danger
                                },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Text(
                        text = when {
                            uiState.pingResult == null -> "READY"
                            uiState.pingResult.isSuccess -> "TCP OK"
                            else -> "TCP FAIL"
                        },
                        style = TextStyle(
                            color = if (uiState.pingResult?.isSuccess != false) colors.accent.copy(alpha = 0.8f) else colors.danger,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Локальный шлюз",
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 2.dp)
            )

            val boundPort = if (tunnelStatus.boundPort > 0) tunnelStatus.boundPort else uiState.config.localListenPort
            TelemetryRow(
                label = "Активный порт SOCKS5",
                value = "127.0.0.1:$boundPort",
                valueColor = colors.textPrimary
            )

            val isPortBusy = tunnelStatus.boundPort > 0 && tunnelStatus.boundPort != uiState.config.localListenPort
            TelemetryRow(
                label = if (isPortBusy) "Основной порт (занят)" else "Настроенный порт",
                value = "127.0.0.1:${uiState.config.localListenPort}",
                valueColor = if (isPortBusy) colors.danger else colors.textMuted
            )

            TelemetryRow(
                label = "Аутентификация",
                value = if (activeProfile.username.isNotEmpty()) "RFC 1929 (User/Pass)" else "Без аутентификации",
                valueColor = colors.textSecondary
            )
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, valueColor: Color) {
    val colors = NeumorphismTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.insetBg)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 11.5.sp
                )
            )
            Text(
                text = value,
                style = TextStyle(
                    color = valueColor,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun ServerItemRow(
    profile: ProxyProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    ping: com.socks5proxy.app.network.PingResult? = null,
    isPinging: Boolean = false,
    onPing: () -> Unit = {}
) {
    val colors = NeumorphismTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.insetBg else colors.bgSurface)
            .border(
                1.dp,
                if (isSelected) colors.accent.copy(alpha = 0.35f) else colors.borderSubtle,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.insetDeepBg),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accentBright else colors.textMuted.copy(alpha = 0.45f))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = TextStyle(
                            color = if (isSelected) colors.accent else colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.host}:${profile.port}${if (profile.username.isNotEmpty()) " (auth)" else ""}",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.insetBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isPinging, onClick = onPing)
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = colors.accent,
                            strokeWidth = 1.5.dp
                        )
                    } else if (ping != null) {
                        val pingColor = when {
                            !ping.isSuccess -> colors.danger
                            ping.latencyMs < 150 -> colors.accent
                            ping.latencyMs < 350 -> colors.accent.copy(alpha = 0.8f)
                            else -> colors.danger.copy(alpha = 0.8f)
                        }
                        Text(
                            text = if (ping.isSuccess) "${ping.latencyMs} мс" else "Сбой",
                            color = pingColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(11.dp)
                            )
                            Text("пинг", color = colors.textMuted, fontSize = 10.sp)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(colors.gradStart, colors.gradEnd)
                            )
                        )
                        .border(1.dp, colors.borderSubtle, CircleShape)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsTabContent(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val colors = NeumorphismTheme.colors

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
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
                    text = "Оформление интерфейса",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.insetBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (uiState.isDarkTheme) "Тёмная" else "Светлая",
                        style = TextStyle(
                            color = colors.accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            SegmentedThemeSelector(
                isDarkTheme = uiState.isDarkTheme,
                onSelectDark = { viewModel.setDarkTheme(it) }
            )
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Маршрутизация приложений",
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Выборочная маршрутизация (Выбор приложений)",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Системный VPN или встроенный VPN: только выбранные приложения",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 11.5.sp
                        )
                    )
                }

                NeumorphicSwitch(
                    checked = uiState.config.isPerAppRoutingEnabled,
                    onCheckedChange = { viewModel.togglePerAppRouting(it) }
                )
            }

            Text(
                text = "Эта настройка действует в VPN-режимах. Локальный SOCKS не перехватывает трафик: приложение должно само подключиться к 127.0.0.1 и выбранному порту.",
                style = TextStyle(
                    color = colors.textMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (uiState.config.isPerAppRoutingEnabled) 1f else 0.45f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    )
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .clickable(enabled = uiState.config.isPerAppRoutingEnabled) {
                        viewModel.setAppSelectorVisible(true)
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Выбрать приложения (${uiState.config.allowedApps.size} выбрано)",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            if (uiState.config.isPerAppRoutingEnabled && uiState.config.allowedApps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Выбранные приложения (${uiState.config.allowedApps.size}):",
                        style = TextStyle(
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.config.allowedApps.forEach { pkg ->
                            val appInfo = uiState.installedApps.find { it.packageName == pkg }
                            val label = appInfo?.appName ?: pkg.substringAfterLast('.')

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.insetBg)
                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (appInfo?.iconBitmap != null) {
                                        Image(
                                            bitmap = appInfo.iconBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.accent.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label.take(1).uppercase(),
                                                style = TextStyle(
                                                    color = colors.accent,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = label,
                                        style = TextStyle(
                                            color = colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Удалить",
                                        tint = colors.textMuted,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.toggleAppSelection(pkg) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Параметры надежности",
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Прямой обход при сбое (Direct Fallback)",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Если прокси недоступен, пускать трафик напрямую через активный интернет/VPN",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 11.5.sp
                        )
                    )
                }

                NeumorphicSwitch(
                    checked = uiState.config.isFallbackDirectEnabled,
                    onCheckedChange = { viewModel.toggleFallbackDirect(it) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.dividerColor)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Автозапуск при включении (Boot)",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Автоматический запуск службы прокси после перезагрузки смартфона",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 11.5.sp
                        )
                    )
                }

                NeumorphicSwitch(
                    checked = uiState.config.isAutoStartEnabled,
                    onCheckedChange = { viewModel.toggleAutoStart(it) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.dividerColor)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Обход локальной сети (Bypass LAN)",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Пускать 192.168.x.x и 10.x.x.x напрямую без VPN",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 11.5.sp
                        )
                    )
                }

                NeumorphicSwitch(
                    checked = uiState.config.isBypassLanEnabled,
                    onCheckedChange = { viewModel.toggleBypassLan(it) }
                )
            }
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Сетевые параметры туннеля",
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Локальный порт SOCKS5: ${uiState.config.localListenPort}",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Порт на 127.0.0.1 для подключения браузера и Telegram",
                    style = TextStyle(color = colors.textMuted, fontSize = 11.5.sp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10808, 10809, 7890, 20808).forEach { portVal ->
                        val isSel = uiState.config.localListenPort == portVal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) colors.accent.copy(alpha = 0.2f) else colors.insetBg)
                                .border(1.dp, if (isSel) colors.accent else colors.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setLocalListenPort(portVal) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$portVal",
                                color = if (isSel) colors.accent else colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.dividerColor)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Размер MTU: ${uiState.config.mtu} байт",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Оптимальный размер пакета для мобильной сети и обхода фрагментации",
                    style = TextStyle(color = colors.textMuted, fontSize = 11.5.sp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1400, 1360, 1280).forEach { mtuVal ->
                        val isSel = uiState.config.mtu == mtuVal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) colors.accent.copy(alpha = 0.2f) else colors.insetBg)
                                .border(1.dp, if (isSel) colors.accent else colors.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setMtu(mtuVal) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$mtuVal",
                                color = if (isSel) colors.accent else colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.dividerColor)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DNS серверы: ${uiState.config.primaryDns} / ${uiState.config.secondaryDns}",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Пресеты DNS для разрешения доменов внутри туннеля",
                    style = TextStyle(color = colors.textMuted, fontSize = 11.5.sp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        Triple("Cloudflare", "1.1.1.1", "1.0.0.1"),
                        Triple("Google", "8.8.8.8", "8.8.4.4"),
                        Triple("AdGuard", "94.140.14.14", "94.140.15.15")
                    )
                    presets.forEach { (label, pDns, sDns) ->
                        val isSel = uiState.config.primaryDns == pDns
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) colors.accent.copy(alpha = 0.2f) else colors.insetBg)
                                .border(1.dp, if (isSel) colors.accent else colors.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setDnsSettings(pDns, sDns) }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) colors.accent else colors.textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    var showLogsDialog by remember { mutableStateOf(false) }
    val logs by viewModel.logsFlow.collectAsState()
    val context = LocalContext.current

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Журнал диагностики",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Записей в памяти: ${logs.size}",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 11.5.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.insetBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable { showLogsDialog = true }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Открыть",
                        color = colors.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { Text("Журнал событий (${logs.size})") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text("Событий пока нет.", color = colors.textMuted, fontSize = 12.sp)
                    } else {
                        logs.asReversed().take(50).forEach { line ->
                            Text(
                                text = line,
                                color = colors.textSecondary,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Socks5Logs", logs.joinToString("\n")))
                    viewModel.showToast("Логи скопированы")
                }) { Text("Копировать") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.clearLogs() }) { Text("Очистить", color = colors.danger) }
                    TextButton(onClick = { showLogsDialog = false }) { Text("Закрыть") }
                }
            }
        )
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Обновление приложения",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val verLabel = if (uiState.currentAppVersion.isNotBlank()) "v${uiState.currentAppVersion}" else "текущая"
                    Text(
                        text = if (uiState.updateInfo != null) "Доступна v${uiState.updateInfo.latestVersion} (у вас $verLabel)" else "Установлена версия $verLabel",
                        style = TextStyle(
                            color = if (uiState.updateInfo != null) colors.accent else colors.textMuted,
                            fontSize = 11.5.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (uiState.updateInfo != null) Brush.linearGradient(listOf(colors.gradStart, colors.gradEnd))
                            else Brush.linearGradient(listOf(colors.insetBg, colors.insetBg))
                        )
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable(enabled = !uiState.isCheckingUpdate) {
                            if (uiState.updateInfo != null) {
                                viewModel.showUpdateDialog()
                            } else {
                                viewModel.checkForUpdates(forceShowDialog = true)
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    if (uiState.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.accent, strokeWidth = 1.5.dp)
                    } else {
                        Text(
                            text = if (uiState.updateInfo != null) "Обновить" else "Проверить",
                            color = colors.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Автопроверка обновлений",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Проверять новые релизы GitHub при запуске",
                        style = TextStyle(color = colors.textMuted, fontSize = 11.sp)
                    )
                }
                NeumorphicSwitch(
                    checked = uiState.config.isAutoCheckUpdatesEnabled,
                    onCheckedChange = { viewModel.toggleAutoCheckUpdates(it) }
                )
            }
        }
    }
}

@Composable
private fun HeroPowerBezel(
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val colors = NeumorphismTheme.colors

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(colors.insetDeepBg),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale1)
                    .clip(CircleShape)
                    .border(1.5.dp, colors.accentBright.copy(alpha = pulseAlpha1), CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(CircleShape)
                .background(
                    if (isConnected) {
                        Brush.radialGradient(
                            listOf(colors.gradStart, colors.bgSurface)
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    }
                )
                .border(
                    if (isConnected) 2.dp else 1.dp,
                    if (isConnected) colors.accentBright else colors.borderSubtle,
                    CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Питание",
                tint = if (isConnected) colors.accentBright else colors.textMuted,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun TrafficSparklineView(
    isRunning: Boolean,
    rxSpeedBps: Long,
    txSpeedBps: Long
) {
    val colors = NeumorphismTheme.colors
    val sparklinePoints = remember { mutableStateListOf<Float>().apply { repeat(24) { add(16f) } } }

    LaunchedEffect(isRunning, rxSpeedBps, txSpeedBps) {
        if (isRunning) {
            val combinedSpeed = (rxSpeedBps + txSpeedBps).coerceAtLeast(0L)
            val normalizedSpeed = (combinedSpeed / (2 * 1024 * 1024f)).coerceIn(0f, 1f)
            val nextY = (20f - normalizedSpeed * 16f).coerceIn(4f, 20f)
            if (sparklinePoints.size >= 24) {
                sparklinePoints.removeAt(0)
            }
            sparklinePoints.add(nextY)
        } else {
            if (sparklinePoints.size >= 24) {
                sparklinePoints.removeAt(0)
            }
            sparklinePoints.add(20f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.insetBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (sparklinePoints.size < 2) return@Canvas
            val w = size.width
            val h = size.height
            val step = w / (sparklinePoints.size - 1)

            val path = Path()
            sparklinePoints.forEachIndexed { idx, pt ->
                val x = idx * step
                val y = (pt / 24f) * h
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    listOf(colors.accent.copy(alpha = 0.4f), colors.accentBright)
                ),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun SegmentedModeSelector(
    currentMode: ProxyEngineMode,
    localPort: Int,
    onSelectMode: (ProxyEngineMode) -> Unit
) {
    val colors = NeumorphismTheme.colors

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.insetBg)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        val totalWidth = maxWidth
        val pillWidth = totalWidth / 3
        val currentIndex = when (currentMode) {
            ProxyEngineMode.LOCAL_PROXY -> 0
            ProxyEngineMode.SYSTEM_VPN -> 1
            ProxyEngineMode.VPN_PROFILE -> 2
        }
        val pillOffset by animateDpAsState(
            targetValue = pillWidth * currentIndex,
            animationSpec = tween(300),
            label = "modePill"
        )

        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(pillWidth)
                .height(48.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(colors.gradStart, colors.gradEnd)
                    )
                )
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(11.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelectMode(ProxyEngineMode.LOCAL_PROXY) }
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Локальный",
                        style = TextStyle(
                            color = if (currentMode == ProxyEngineMode.LOCAL_PROXY) colors.accent else colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "127.0.0.1:$localPort",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelectMode(ProxyEngineMode.SYSTEM_VPN) }
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Системный VPN",
                        style = TextStyle(
                            color = if (currentMode == ProxyEngineMode.SYSTEM_VPN) colors.accent else colors.textSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Перехват ОС",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelectMode(ProxyEngineMode.VPN_PROFILE) }
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "VPN-сервер",
                        style = TextStyle(
                            color = if (currentMode == ProxyEngineMode.VPN_PROFILE) colors.accent else colors.textSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text("Xray", color = colors.textMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun SegmentedThemeSelector(
    isDarkTheme: Boolean,
    onSelectDark: (Boolean) -> Unit
) {
    val colors = NeumorphismTheme.colors

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.insetBg)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        val totalWidth = maxWidth
        val pillWidth = totalWidth / 2
        val pillOffset by animateDpAsState(
            targetValue = if (!isDarkTheme) 0.dp else pillWidth,
            animationSpec = tween(300),
            label = "themePill"
        )

        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(pillWidth)
                .height(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(colors.gradStart, colors.gradEnd)
                    )
                )
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(11.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelectDark(false) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = if (!isDarkTheme) colors.accent else colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Светлая",
                        style = TextStyle(
                            color = if (!isDarkTheme) colors.accent else colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (!isDarkTheme) FontWeight.Bold else FontWeight.SemiBold
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelectDark(true) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = if (isDarkTheme) colors.accent else colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Тёмная",
                        style = TextStyle(
                            color = if (isDarkTheme) colors.accent else colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isDarkTheme) FontWeight.Bold else FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavTabs(
    activeTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val colors = NeumorphismTheme.colors

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.insetBg)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
            .padding(4.dp)
    ) {
        val tabCount = 3
        val tabWidth = maxWidth / tabCount
        val sliderOffset by animateDpAsState(
            targetValue = tabWidth * activeTab,
            animationSpec = tween(300),
            label = "tabSlider"
        )

        Box(
            modifier = Modifier
                .offset(x = sliderOffset)
                .width(tabWidth)
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(colors.gradStart, colors.gradEnd)
                    )
                )
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            NavTabItem(
                label = "Главная",
                icon = Icons.Default.Security,
                isSelected = activeTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(0) }
            )

            NavTabItem(
                label = "Серверы",
                icon = Icons.Default.Dns,
                isSelected = activeTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(1) }
            )

            NavTabItem(
                label = "Настройки",
                icon = Icons.Default.Settings,
                isSelected = activeTab == 2,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(2) }
            )
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = NeumorphismTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) colors.accent else colors.textMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = if (isSelected) colors.accent else colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NeumorphismTheme.colors
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = tween(250),
        label = "switchThumb"
    )

    Box(
        modifier = modifier
            .size(width = 50.dp, height = 28.dp)
            .clip(CircleShape)
            .background(colors.insetDeepBg)
            .border(
                1.dp,
                if (checked) colors.accent.copy(alpha = 0.35f) else colors.borderSubtle,
                CircleShape
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (checked) {
                        Brush.linearGradient(
                            listOf(colors.accentBright, colors.accent)
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    }
                )
                .border(
                    1.dp,
                    if (checked) colors.accentBright else colors.borderSubtle,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else colors.textMuted)
            )
        }
    }
}

@Composable
private fun NeumorphicCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = NeumorphismTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.gradStart, colors.gradEnd)
                )
            )
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(22.dp))
    ) {
        content()
    }
}

@Composable
private fun ToastOverlay(
    message: String?,
    modifier: Modifier = Modifier
) {
    val colors = NeumorphismTheme.colors

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        if (message != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(colors.gradStart, colors.gradEnd)
                        )
                    )
                    .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.accentBright)
                    )
                    Text(
                        text = message,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun VpnImportDialog(
    isImporting: Boolean,
    error: String?,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NeumorphismTheme.colors
    val context = LocalContext.current
    var source by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = { Text("Импорт VPN-серверов") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Вставьте ссылку на узел или HTTPS-ссылку на подписку. Поддерживаются ссылки Xray и списки серверов.",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("vless://… или https://…") },
                    minLines = 3,
                    maxLines = 7,
                    enabled = !isImporting
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.insetBg)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isImporting) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (clip.isNotBlank()) source = clip.trim()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Text("Вставить из буфера обмена", color = colors.accent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                if (isImporting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Загрузка и разбор узлов…", color = colors.textMuted, fontSize = 11.sp)
                    }
                }
                if (error != null) {
                    Text(error, color = colors.danger, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = source.isNotBlank() && !isImporting,
                onClick = { onImport(source.trim()) }
            ) { Text("Импортировать") }
        },
        dismissButton = {
            TextButton(enabled = !isImporting, onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun UpdateAvailableDialog(
    updateInfo: com.socks5proxy.app.updater.UpdateInfo,
    isDownloading: Boolean,
    progress: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    isResuming: Boolean,
    isReady: Boolean,
    onInstall: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NeumorphismTheme.colors

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Text(
                text = "Доступно обновление v${updateInfo.latestVersion}",
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = updateInfo.releaseNotes,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Text(
                        text = "Вышла новая версия приложения с улучшениями и исправлениями.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                if (isDownloading || downloadedBytes > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val downloadedMb = String.format(java.util.Locale.US, "%.1f", downloadedBytes / (1024f * 1024f))
                    val totalMb = if (totalBytes > 0L) String.format(java.util.Locale.US, "%.1f", totalBytes / (1024f * 1024f)) else "?"
                    val textStatus = if (isDownloading) {
                        "Загрузка: $progress% ($downloadedMb МБ / $totalMb МБ)"
                    } else if (isResuming) {
                        "Загрузка приостановлена ($downloadedMb МБ / $totalMb МБ)"
                    } else {
                        "Прогресс: $progress%"
                    }
                    Text(
                        text = textStatus,
                        color = colors.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isDownloading,
                onClick = onInstall
            ) {
                Text(
                    text = when {
                        isDownloading -> "Загрузка..."
                        isReady -> "Установить"
                        isResuming -> "Возобновить скачивание"
                        else -> "Обновить сейчас"
                    },
                    color = colors.accent,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onSnooze) {
                        Text("Отложить", color = colors.accent)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть", color = colors.textMuted)
                    }
                }
            }
        }
    )
}
