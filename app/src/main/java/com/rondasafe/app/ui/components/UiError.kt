package com.rondasafe.app.ui.components

fun userFriendlyError(error: Throwable, fallback: String = "Não foi possível concluir a operação."): String {
    val raw = error.message.orEmpty()
    val text = raw.lowercase()
    return when {
        "guard_already_has_active_shift" in text -> "Este porteiro já tem um turno aberto. Continue a ronda no aparelho em que ela foi iniciada."
        "device_already_has_active_shift" in text -> "Este aparelho já tem um turno aberto. Continue ou encerre esse turno antes de iniciar outro."
        "admin_required" in text -> "A sessão atual não foi reconhecida como administrativa. Entre novamente como administrador."
        "permission denied" in text || "42501" in text -> "Você não tem permissão para realizar esta ação. Entre novamente como administrador."
        "jwt" in text || "session" in text || "401" in text -> "Sua sessão expirou. Entre novamente para continuar."
        "network" in text || "unable to resolve host" in text || "timeout" in text || "failed to connect" in text -> "Sem conexão com a internet. Verifique a rede e tente novamente."
        "duplicate" in text || "23505" in text -> "Este cadastro já existe."
        "foreign key" in text || "23503" in text -> "Este item está sendo usado pelo sistema e não pode ser removido."
        "possui histórico" in text || "historico" in text || "histórico" in text -> "Este item possui histórico e deve permanecer arquivado."
        "não pode ser exclu" in text || "nao pode ser exclu" in text -> "Este item está vinculado a outros dados e não pode ser excluído definitivamente."
        else -> fallback
    }
}
