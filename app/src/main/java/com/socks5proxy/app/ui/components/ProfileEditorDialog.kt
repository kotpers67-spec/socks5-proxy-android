package com.socks5proxy.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.ui.theme.NeumorphismTheme

@Composable
fun ProfileEditorDialog(
    profile: ProxyProfile,
    canDelete: Boolean,
    onSave: (ProxyProfile) -> Unit,
    onDismiss: () -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onShowToast: ((String) -> Unit)? = null
) {
    val colors = NeumorphismTheme.colors
    val context = LocalContext.current

    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var host by remember(profile.id) { mutableStateOf(profile.host) }
    var portStr by remember(profile.id) { mutableStateOf(profile.port.toString()) }
    var username by remember(profile.id) { mutableStateOf(profile.username) }
    var password by remember(profile.id) { mutableStateOf(profile.password) }
    var passVisible by remember { mutableStateOf(false) }

    val isValid = remember(host, portStr, username, password) {
        val p = portStr.toIntOrNull()
        host.isNotBlank() && p != null && p in 1..65535 &&
            ((username.isBlank() && password.isBlank()) || (username.isNotBlank() && password.isNotEmpty()))
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.bgBase)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.textMuted.copy(alpha = 0.45f))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (canDelete) "Редактировать сервер" else "Добавить прокси-сервер",
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.insetBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    val parsed = ProxyProfile.fromUriOrLine(clipText)
                                    if (parsed != null) {
                                        name = parsed.name
                                        host = parsed.host
                                        portStr = parsed.port.toString()
                                        username = parsed.username
                                        password = parsed.password
                                        onShowToast?.invoke("Параметры вставлены из буфера")
                                    } else {
                                        onShowToast?.invoke("В буфере нет socks5:// или host:port")
                                    }
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Вставить",
                                    tint = colors.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Вставить",
                                    color = colors.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(colors.gradStart, colors.gradEnd)
                                    )
                                )
                                .border(1.dp, colors.borderSubtle, CircleShape)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Название профиля",
                        style = TextStyle(
                            color = colors.textMuted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    NeumorphicInputField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Например: Основной прокси"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(2f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "IP-адрес или хост",
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        NeumorphicInputField(
                            value = host,
                            onValueChange = { host = it },
                            placeholder = "example.com или IP",
                            isMono = true
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Порт",
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        NeumorphicInputField(
                            value = portStr,
                            onValueChange = { if (it.all { ch -> ch.isDigit() }) portStr = it },
                            placeholder = "1080",
                            isMono = true,
                            keyboardType = KeyboardType.Number
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Логин (опц.)",
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        NeumorphicInputField(
                            value = username,
                            onValueChange = { username = it },
                            placeholder = "user",
                            isMono = true
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Пароль (опц.)",
                            style = TextStyle(
                                color = colors.textMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        NeumorphicInputField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "••••••",
                            isMono = true,
                            isPassword = !passVisible,
                            trailingIcon = {
                                IconButton(
                                    onClick = { passVisible = !passVisible },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(colors.gradStart, colors.gradEnd)
                            )
                        )
                        .border(
                            1.dp,
                            if (isValid) colors.accent.copy(alpha = 0.35f) else colors.borderSubtle,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = isValid) {
                            val parsedPort = portStr.toIntOrNull() ?: 1080
                            onSave(
                                profile.copy(
                                    name = name.ifBlank { "Прокси" },
                                    host = host.trim(),
                                    port = parsedPort,
                                    username = username.trim(),
                                    password = password.trim()
                                )
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Сохранить сервер",
                        style = TextStyle(
                            color = if (isValid) colors.accent else colors.textMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (isValid) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.insetBg)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                            .clickable {
                                val parsedPort = portStr.toIntOrNull() ?: 1080
                                val uri = profile.copy(
                                    name = name.ifBlank { "Прокси" },
                                    host = host.trim(),
                                    port = parsedPort,
                                    username = username.trim(),
                                    password = password.trim()
                                ).toSocksUri()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("SOCKS5 URI", uri))
                                onShowToast?.invoke("Ссылка socks5:// скопирована")
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Копировать ссылку",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Копировать socks5:// ссылку",
                                style = TextStyle(
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                if (onDelete != null && canDelete) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onDelete(profile.id) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить",
                                tint = colors.danger,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Удалить сервер",
                                style = TextStyle(
                                    color = colors.danger,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeumorphicInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isMono: Boolean = false,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val colors = NeumorphismTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.insetBg)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            color = colors.textMuted.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
                        )
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(6.dp))
                trailingIcon()
            }
        }
    }
}
