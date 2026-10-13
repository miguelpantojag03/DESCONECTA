package com.desconecta.hp.mobile.cloud

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException

/**
 * Errores de cuenta en palabras del jugador (Nielsen nº 9): qué pasó y qué hacer, sin
 * códigos. Nunca se dice si un correo existe o no al iniciar sesión (no se filtra quién
 * tiene cuenta): "correo o contraseña incorrectos" cubre ambos casos.
 */
fun authErrorMessage(error: Throwable): String = when {
    error is HttpRequestException || error is HttpRequestTimeoutException ||
        error is java.io.IOException -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    error is AuthRestException -> when (error.errorCode) {
        AuthErrorCode.InvalidCredentials -> "Correo o contraseña incorrectos."
        AuthErrorCode.EmailNotConfirmed -> "Aún no confirmaste tu correo. Abre el enlace que te enviamos."
        AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "Ya existe una cuenta con ese correo. Prueba a iniciar sesión."
        AuthErrorCode.WeakPassword -> "La contraseña es demasiado débil. Usa al menos 8 caracteres con letras y números."
        AuthErrorCode.SamePassword -> "La contraseña nueva debe ser distinta de la anterior."
        AuthErrorCode.EmailAddressInvalid -> "Ese correo no parece válido."
        AuthErrorCode.OverEmailSendRateLimit -> "Enviamos demasiados correos seguidos. Espera unos minutos."
        AuthErrorCode.OverRequestRateLimit -> "Demasiados intentos. Espera un momento y vuelve a probar."
        AuthErrorCode.SignupDisabled -> "El registro está desactivado en este momento."
        else -> "No se pudo completar. Inténtalo de nuevo en un momento."
    }
    else -> "No se pudo completar. Inténtalo de nuevo en un momento."
}

/** Validación en el cliente, antes de gastar una petición (y con el mensaje junto al campo). */
object AuthValidation {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    const val MIN_PASSWORD = 8

    fun emailError(email: String): String? =
        if (email.isBlank()) "Escribe tu correo" else if (!EMAIL.matches(email.trim())) "Revisa el correo: falta algo (ej. nombre@correo.com)" else null

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Escribe una contraseña"
        password.length < MIN_PASSWORD -> "Mínimo $MIN_PASSWORD caracteres (llevas ${password.length})"
        password.none { it.isDigit() } || password.none { it.isLetter() } -> "Combina letras y números"
        else -> null
    }
}
