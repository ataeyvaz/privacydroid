package io.privacydroid.domain.model

data class AppDetail(
    val packageName: String,
    val appName: String,
    val riskScore: Int,                         // 0-100
    val riskComponents: RiskComponents,
    val weeklyData: List<DailyAccessCount>,
    val totalWeeklyCount: Int,
    val backgroundCount: Int,
    val nightCount: Int,
    val uniquePermissionCount: Int
)

data class RiskComponents(
    val backgroundScore: Float,  // 0-40
    val nightScore: Float,       // 0-30
    val diversityScore: Float,   // 0-20
    val frequencyScore: Float    // 0-10
)

data class DailyAccessCount(
    val dayLabel: String,                          // "Pzt", "Sal", …
    val dateMs: Long,
    val totalCount: Int,
    val countsByPermission: Map<String, Int>       // permissionType.name → count
)
