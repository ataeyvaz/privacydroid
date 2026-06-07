package io.privacydroid.data.model

/**
 * İzleme modu.
 *
 * PERIODIC: WorkManager, 15 dakikada bir tarar. Pil dostu.
 *   Doze Mode sırasında taramalar ertelenir; delta tespiti sayesinde log kaybı olmaz.
 *
 * REALTIME: ForegroundService, her [REALTIME_INTERVAL_MINUTES] dakikada bir tarar.
 *   Kalıcı bildirim gösterir. Daha hızlı uyarı, daha fazla pil tüketimi.
 */
enum class MonitoringMode(val displayName: String, val descriptionText: String) {
    PERIODIC(
        displayName = "Pil Dostu (15 dk)",
        descriptionText = "Arka planda 15 dakikada bir tarar. Pil etkisi minimumdur."
    ),
    REALTIME(
        displayName = "Gerçek Zamanlı",
        descriptionText = "Sürekli çalışır, değişiklikleri anında yakalar. Pil tüketimi artar."
    );

    companion object {
        const val REALTIME_INTERVAL_MINUTES = 2L
        fun fromString(value: String): MonitoringMode =
            entries.firstOrNull { it.name == value } ?: PERIODIC
    }
}
