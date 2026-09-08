package com.rondasafe.app.presentation.admin.state

data class AdminDashboardState(
    val condominium: String = "Condomínio",
    val total: Int = 0,
    val completed: Int = 0,
    val attention: Int = 0,
    val inProgress: Int = 0,
    val missed: Int = 0,
    val openAlerts: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)
