package com.socks5proxy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socks5proxy.app.model.TunnelState
import com.socks5proxy.app.model.TunnelStatus
import com.socks5proxy.app.ui.MainUiState
import com.socks5proxy.app.ui.MainViewModel
import com.socks5proxy.app.ui.theme.*

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
    var passwordVisible by remember { mutableStateOf(false) }

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
                                .background(SurfaceCard)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Split-Tunnel",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
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

                    val buttonSemantics = when (tunnelStatus.state) {
                        TunnelState.CONNECTED -> "Отключить SOCKS5 прокси. Соединение активно"
                        TunnelState.CONNECTING -> "Установка соединения. Пожалуйста, подождите"
                        TunnelState.ERROR -> "Ошибка туннеля. Нажмите для повторного подключения"
                        TunnelState.DISCONNECTED -> "Подключить SOCKS5 прокси"
                    }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(buttonBg)
                            .border(3.dp, if (isRunning) TextPrimary else BorderDark, CircleShape)
                            .semantics {
                                role = Role.Button
                                contentDescription = buttonSemantics
                            }
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
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Маршрутизация приложений",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (config.isPerAppRoutingEnabled) "Прокси только для выбранных приложений" else "Весь сетевой трафик устройства через прокси",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
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
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceCard,
                                contentColor = TextPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(BorderDark)
                            )
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Выбрать приложения (${config.allowedApps.size} выбрано)",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Параметры SOCKS5 сервера",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = config.host,
                            onValueChange = { viewModel.updateHost(it) },
                            modifier = Modifier.weight(0.68f),
                            label = { Text("Сервер (IP / Host)", color = TextMuted) },
                            singleLine = true,
                            enabled = !tunnelStatus.isRunning,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            )
                        )

                        OutlinedTextField(
                            value = if (config.port > 0) config.port.toString() else "",
                            onValueChange = { viewModel.updatePort(it) },
                            modifier = Modifier.weight(0.32f),
                            label = { Text("Порт", color = TextMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !tunnelStatus.isRunning,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            )
                        )
                    }

                    OutlinedTextField(
                        value = config.username,
                        onValueChange = { viewModel.updateUsername(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Имя пользователя (опционально)", color = TextMuted) },
                        singleLine = true,
                        enabled = !tunnelStatus.isRunning,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        )
                    )

                    OutlinedTextField(
                        value = config.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Пароль (опционально)", color = TextMuted) },
                        singleLine = true,
                        enabled = !tunnelStatus.isRunning,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                                    tint = TextMuted
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = config.isUdpEnabled,
                                enabled = !tunnelStatus.isRunning,
                                role = Role.Switch,
                                onValueChange = { viewModel.toggleUdp(it) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Релей UDP трафика", color = TextSecondary, fontSize = 14.sp)
                            Text("Необходимо для DNS, голосовых звонков и игр", color = TextMuted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = config.isUdpEnabled,
                            onCheckedChange = null,
                            enabled = !tunnelStatus.isRunning,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = AccentCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceCard,
                                uncheckedBorderColor = BorderDark
                            )
                        )
                    }

                    Button(
                        onClick = { viewModel.checkProxyPing() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isCheckingPing && config.isValid,
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
                            Text("Проверить доступность прокси", fontSize = 13.sp)
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (uiState.isAppSelectorOpen) {
        AppSelectorSheet(
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
