package com.rondasafe.app.presentation.admin.state

import com.rondasafe.app.data.model.PatrolHistoryItemDto

data class AdminDashboardState(
    val condominium: String = "Condomínio",
    val today: List<PatrolHistoryItemDto> = emptyList(),
    val openAlerts: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val total: Int get() = today.size
    val completed: Int get() = today.count {
        it.displayStatus == "COMPLETED" && !it.isLate && !it.suspicious && it.missingPoints == 0
    }
    val attention: Int get() = today.count {
        it.displayStatus in setOf("MISSED", "INCOMPLETE", "LATE") ||
            it.isLate || it.suspicious || it.missingPoints > 0
    }
}
