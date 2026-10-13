package com.desconecta.hp.mobile.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.cloud.AuthValidation
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.mobile.ui.onboarding.fieldColors
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp

/* =====================================================================================
 * Pantallas de cuenta (paso 6).
 *
 * Principios aplicados:
 *  · Consentimiento informado ANTES de crear la cuenta, con lo que sube, lo que no sube
 *    nunca y lo que se pierde (Nielsen nº 1 visibilidad, nº 5 prevención de errores).
 *  · "Solo en este dispositivo" es una opción de primera clase, con el mismo peso visual
 *    que aceptar: no hay patrón oscuro empujando a la nube.
 *  · Formularios cortos (2 campos), validación junto al campo y en palabras (nº 9),
 *    teclado adecuado y autocompletado del gestor de contraseñas (nº 7 flexibilidad).
 *  · Fricción proporcional al riesgo: borrar la cuenta pide escribir BORRAR.
 * ===================================================================================== */

@Composable
private fun AccountScaffold(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Hp.colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HpSpacing.lg, vertical = HpSpacing.md),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) { content() }
}

/** Mensaje de error o aviso que TalkBack lee en cuanto aparece. */
@Composable
fun AccountBanner(text: String, isError: Boolean) {
    val c = Hp.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isError) c.hurtContainer else c.healthyContainer, Hp.shapes.card)
            .padding(HpSpacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            if (isError) PixelIcons.HeartCracked else PixelIcons.Star, null, Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(if (isError) c.hpHurt else c.hpHealthy),
        )
        Text(text, style = Hp.type.body, color = c.textPrimary)
    }
}

@Composable
private fun ConsentPoint(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
        Image(icon, null, Modifier.padding(top = 2.dp).size(24.dp), colorFilter = ColorFilter.tint(tint))
        Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
            Text(title, style = Hp.type.bodyStrong, color = Hp.colors.textPrimary)
            Text(body, style = Hp.type.body, color = Hp.colors.textSecondary)
        }
    }
}

// -------------------------------------------------------------------------------------
// 1 · Consentimiento
// -------------------------------------------------------------------------------------

@Composable
fun ConsentScreen(onAccept: () -> Unit, onStayLocal: () -> Unit) {
    val c = Hp.colors
    AccountScaffold {
        Text(
            "Antes de crear tu cuenta",
            style = Hp.type.headline, color = c.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Con una cuenta, tu progreso se guarda en la nube y no se pierde si cambias de teléfono. Es opcional: el juego funciona igual sin ella.",
            style = Hp.type.body, color = c.textSecondary,
        )
        HpCard {
            ConsentPoint(
                PixelIcons.Crystal, c.regen, "Qué se guarda en la nube",
                "Tu nivel, XP, racha, logros y objetos; tus monstruos y límites; y los minutos que pasas en cada app, sumados por hora.",
            )
            ConsentPoint(
                PixelIcons.Lock, c.hpHealthy, "Qué no sale nunca de tu teléfono",
                "Lo que haces dentro de las apps, tus mensajes y tu ubicación. Tu PIN tampoco: solo se guarda cifrado.",
            )
            ConsentPoint(
                PixelIcons.Medal, c.gold, "Tú decides",
                "Puedes exportar todos tus datos o borrar la cuenta cuando quieras, desde Configuración.",
            )
        }
        HpCard(container = c.hurtContainer) {
            ConsentPoint(
                PixelIcons.HeartCracked, c.hpHurt, "Tu progreso sin cuenta no se pasa",
                "Al entrar empezarás con el progreso de la cuenta: el nivel, la XP, la racha, los logros y los objetos de este teléfono se borran. Tus monstruos, límites y la vida de hoy se mantienen.",
            )
        }
        Text(
            "La IA en la nube es aparte: te pediremos permiso cuando la actives.",
            style = Hp.type.caption, color = c.textSecondary,
        )
        PrimaryButton("Acepto, continuar", onClick = onAccept)
        SecondaryButton("Seguir solo en este dispositivo", onClick = onStayLocal, icon = PixelIcons.Lock, iconTint = c.textSecondary)
    }
}

// -------------------------------------------------------------------------------------
// 2 · Entrar / crear cuenta
// -------------------------------------------------------------------------------------

enum class AuthMode { SIGN_IN, SIGN_UP }

@Composable
private fun ModeSwitch(mode: AuthMode, onMode: (AuthMode) -> Unit) {
    val c = Hp.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface, Hp.shapes.button).padding(HpSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs),
    ) {
        listOf(AuthMode.SIGN_UP to "Crear cuenta", AuthMode.SIGN_IN to "Iniciar sesión").forEach { (m, label) ->
            val selected = m == mode
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (selected) c.healthyContainer else c.surface, Hp.shapes.button)
                    .clickable(role = Role.Tab) { onMode(m) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Hp.type.label, color = if (selected) c.textPrimary else c.textSecondary)
            }
        }
    }
}

@Composable
fun EmailField(value: String, onValue: (String) -> Unit, error: String?, imeAction: ImeAction = ImeAction.Next, onIme: () -> Unit = {}) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(120)) },
        label = { Text("Correo") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { onIme() }, onGo = { onIme() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentType = ContentType.EmailAddress
                if (error != null) error(error)
            },
        colors = fieldColors(),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValue: (String) -> Unit,
    error: String?,
    label: String = "Contraseña",
    newPassword: Boolean = false,
    onDone: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(72)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                    tint = Hp.colors.textSecondary,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentType = if (newPassword) ContentType.NewPassword else ContentType.Password
                if (error != null) error(error)
            },
        colors = fieldColors(),
    )
}

/** Requisitos de contraseña en vivo (se cumplen mientras escribes, no después de fallar). */
@Composable
fun PasswordChecklist(password: String) {
    val c = Hp.colors
    Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
        listOf(
            "Al menos ${AuthValidation.MIN_PASSWORD} caracteres" to (password.length >= AuthValidation.MIN_PASSWORD),
            "Letras y números" to (password.any { it.isLetter() } && password.any { it.isDigit() }),
        ).forEach { (rule, ok) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Image(
                    if (ok) PixelIcons.Star else PixelIcons.HeartOutline, null, Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(if (ok) c.hpHealthy else c.textSecondary),
                )
                Text((if (ok) "Cumple: " else "Falta: ") + rule, style = Hp.type.caption, color = if (ok) c.textPrimary else c.textSecondary)
            }
        }
    }
}

@Composable
fun AuthScreen(
    ui: AccountUiState,
    initialMode: AuthMode,
    googleAvailable: Boolean,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onGoogle: () -> Unit,
    onForgot: (email: String) -> Unit,
    onResend: (email: String) -> Unit,
    onBackToForm: () -> Unit,
) {
    val c = Hp.colors
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Los errores de campo aparecen tras el primer intento, no mientras se escribe.
    var attempted by rememberSaveable { mutableStateOf(false) }
    var forgot by rememberSaveable { mutableStateOf(false) }

    val notice = ui.notice
    AccountScaffold {
        when {
            notice is AccountNotice.CheckEmail || notice is AccountNotice.ConfirmationResent -> {
                val target = (notice as? AccountNotice.CheckEmail)?.email ?: email.trim()
                CheckInbox(
                    title = "Revisa tu correo",
                    body = "Te enviamos un enlace a $target. Ábrelo en este teléfono y volverás a la app con la sesión iniciada.",
                )
                if (notice is AccountNotice.ConfirmationResent) AccountBanner("Correo reenviado.", isError = false)
                ui.error?.let { AccountBanner(it, isError = true) }
                SecondaryButton("Reenviar correo", onClick = { onResend(target) }, enabled = !ui.busy)
                TextButton(onClick = { mode = AuthMode.SIGN_IN; onBackToForm() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Ya lo confirmé, iniciar sesión", color = c.hpHealthy)
                }
            }
            notice is AccountNotice.ResetSent -> {
                CheckInbox(
                    title = "Enlace enviado",
                    body = "Si hay una cuenta con ${notice.email}, te llegará un enlace para crear una contraseña nueva. Ábrelo en este teléfono.",
                )
                SecondaryButton("Volver a iniciar sesión", onClick = { forgot = false; onBackToForm() })
            }
            forgot -> {
                Text("Recuperar contraseña", style = Hp.type.headline, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                Text("Escribe el correo de tu cuenta y te enviaremos un enlace.", style = Hp.type.body, color = c.textSecondary)
                val emailError = if (attempted) AuthValidation.emailError(email) else null
                val submit = {
                    attempted = true
                    if (AuthValidation.emailError(email) == null) onForgot(email)
                }
                EmailField(email, { email = it }, emailError, ImeAction.Go, submit)
                ui.error?.let { AccountBanner(it, isError = true) }
                PrimaryButton(if (ui.busy) "Enviando…" else "Enviar enlace", onClick = submit, enabled = !ui.busy)
                TextButton(onClick = { forgot = false; attempted = false; onBackToForm() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Volver", color = c.textSecondary)
                }
            }
            else -> {
                ModeSwitch(mode) { mode = it; attempted = false; onBackToForm() }
                if (googleAvailable) {
                    SecondaryButton(
                        "Continuar con Google", onClick = onGoogle, enabled = !ui.busy,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                        HorizontalDivider(Modifier.weight(1f), color = c.outline)
                        Text("o con tu correo", style = Hp.type.caption, color = c.textSecondary)
                        HorizontalDivider(Modifier.weight(1f), color = c.outline)
                    }
                }
                val signUp = mode == AuthMode.SIGN_UP
                val emailError = if (attempted) AuthValidation.emailError(email) else null
                val passwordError = if (!attempted) null
                else if (signUp) AuthValidation.passwordError(password)
                else if (password.isEmpty()) "Escribe tu contraseña" else null
                val submit = {
                    attempted = true
                    val ok = AuthValidation.emailError(email) == null &&
                        (if (signUp) AuthValidation.passwordError(password) == null else password.isNotEmpty())
                    if (ok) if (signUp) onSignUp(email, password) else onSignIn(email, password)
                }
                EmailField(email, { email = it }, emailError)
                PasswordField(password, { password = it }, passwordError, newPassword = signUp, onDone = submit)
                if (signUp) PasswordChecklist(password)
                ui.error?.let { AccountBanner(it, isError = true) }
                PrimaryButton(
                    when {
                        ui.busy && signUp -> "Creando cuenta…"
                        ui.busy -> "Entrando…"
                        signUp -> "Crear cuenta"
                        else -> "Iniciar sesión"
                    },
                    onClick = submit,
                    enabled = !ui.busy,
                )
                if (!signUp) {
                    TextButton(onClick = { forgot = true; attempted = false; onBackToForm() }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("¿Olvidaste tu contraseña?", color = c.hpHealthy)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckInbox(title: String, body: String) {
    val c = Hp.colors
    Spacer(Modifier.height(HpSpacing.lg))
    Image(PixelIcons.Crystal, null, Modifier.size(56.dp), colorFilter = ColorFilter.tint(c.regen))
    Text(title, style = Hp.type.headline, color = c.textPrimary, modifier = Modifier.semantics { heading() })
    Text(body, style = Hp.type.body, color = c.textSecondary)
}

// -------------------------------------------------------------------------------------
// 3 · Contraseña nueva (desde el enlace del correo)
// -------------------------------------------------------------------------------------

@Composable
fun NewPasswordScreen(ui: AccountUiState, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val c = Hp.colors
    var password by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val submit = {
        attempted = true
        if (AuthValidation.passwordError(password) == null) onSave(password)
    }
    AccountScaffold {
        Text("Crea una contraseña nueva", style = Hp.type.headline, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        Text("La usarás a partir de ahora para entrar en tu cuenta.", style = Hp.type.body, color = c.textSecondary)
        PasswordField(
            password, { password = it }, if (attempted) AuthValidation.passwordError(password) else null,
            label = "Contraseña nueva", newPassword = true, onDone = submit,
        )
        PasswordChecklist(password)
        ui.error?.let { AccountBanner(it, isError = true) }
        PrimaryButton(if (ui.busy) "Guardando…" else "Guardar contraseña", onClick = submit, enabled = !ui.busy)
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ahora no", color = c.textSecondary) }
    }
}

// -------------------------------------------------------------------------------------
// 4 · Borrar cuenta
// -------------------------------------------------------------------------------------

const val DELETE_WORD = "BORRAR"

@Composable
fun DeleteAccountScreen(
    ui: AccountUiState,
    email: String?,
    onExport: () -> Unit,
    onDelete: (alsoThisPhone: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val c = Hp.colors
    var typed by rememberSaveable { mutableStateOf("") }
    var alsoPhone by rememberSaveable { mutableStateOf(false) }
    val armed = typed.trim().equals(DELETE_WORD, ignoreCase = false)
    AccountScaffold {
        Text("Borrar mi cuenta", style = Hp.type.headline, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        if (email != null) Text(email, style = Hp.type.bodyStrong, color = c.textSecondary)
        HpCard(container = c.criticalContainer) {
            ConsentPoint(
                PixelIcons.Skull, c.hpCritical, "Esto no se puede deshacer",
                "Se borran de la nube tu perfil, nivel, XP, racha, logros, objetos, historial, uso de apps, datos de IA y dispositivos vinculados.",
            )
        }
        HpCard {
            ConsentPoint(PixelIcons.Crystal, c.regen, "¿Quieres una copia antes?", "Descarga todos tus datos en un archivo.")
            SecondaryButton("Exportar mis datos", onClick = onExport, enabled = !ui.busy)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Checkbox) { alsoPhone = !alsoPhone },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm),
        ) {
            Checkbox(
                checked = alsoPhone, onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = c.hpCritical, uncheckedColor = c.outlineStrong),
            )
            Column(Modifier.weight(1f)) {
                Text("Borrar también los datos de este teléfono", style = Hp.type.body, color = c.textPrimary)
                Text(
                    if (alsoPhone) "Volverás a la pantalla de bienvenida." else "Si no, seguirás jugando en \"solo en este dispositivo\".",
                    style = Hp.type.caption, color = c.textSecondary,
                )
            }
        }
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.take(12) },
            label = { Text("Escribe $DELETE_WORD para confirmar") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        ui.error?.let { AccountBanner(it, isError = true) }
        PrimaryButton(
            if (ui.busy) "Borrando…" else "Borrar mi cuenta para siempre",
            onClick = { if (armed) onDelete(alsoPhone) },
            enabled = armed && !ui.busy,
            container = c.hpCritical,
            icon = PixelIcons.Skull,
        )
        SecondaryButton("Cancelar", onClick = onCancel, iconTint = c.textSecondary)
    }
}
