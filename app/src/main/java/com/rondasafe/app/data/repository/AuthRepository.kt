package com.rondasafe.app.data.repository

import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.signInWith
import io.github.jan.supabase.auth.signOut

object AuthRepository {
    private val auth get() = SupabaseProvider.client.auth

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }

        val user = auth.retrieveUserForCurrentSession(updateSession = true)
        val role = user.appMetadata?.get("role")?.toString()?.trim('"')
        check(role == "admin") { "Acesso permitido apenas para administradores." }
    }.onFailure {
        runCatching { auth.signOut() }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    fun hasSession(): Boolean = auth.currentUserOrNull() != null
}
