package io.privacydroid.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Onboarding tamamlanma durumunu saklar.
 *
 * [isCompleted] bir kez true yapıldıktan sonra bir daha false olmaz
 * (onboarding tekrar gösterilmez). İzin iptal edilirse Dashboard'da
 * banner gösterilir — onboarding baştan başlatılmaz.
 */
@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_COMPLETED = "completed"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isCompleted: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) { prefs.edit().putBoolean(KEY_COMPLETED, value).apply() }
}
