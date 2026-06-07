package io.privacydroid.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen
import io.privacydroid.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES })
    val context = LocalContext.current

    // ViewModel → pager senkronizasyonu
    LaunchedEffect(uiState.currentPage) {
        pagerState.animateScrollToPage(uiState.currentPage)
    }

    // Tamamlanma navigasyonu
    LaunchedEffect(uiState.shouldNavigateToDashboard) {
        if (uiState.shouldNavigateToDashboard) {
            onOnboardingComplete()
            viewModel.onNavigationHandled()
        }
    }

    // POST_NOTIFICATIONS launcher
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onNotificationPermissionResult(granted) }

    // Ayarlar launcher — geri dönünce izin yeniden kontrol edilir
    val usageStatsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.recheckUsageStatsPermission() }

    // ON_RESUME: ayarlardan geri dönüşü yakala (launcher callback yanı sıra)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.recheckUsageStatsPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // İlerleme noktaları
            PageIndicator(
                currentPage = uiState.currentPage,
                totalPages = ONBOARDING_PAGES,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp, bottom = 8.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false // Sayfalar arası swipe engellendi — akış zorlanır
            ) { page ->
                when (page) {
                    0 -> WelcomePage(onNext = { viewModel.advancePage() })
                    1 -> UsageStatsPage(
                        hasPermission = uiState.hasUsageStatsPermission,
                        onOpenSettings = {
                            usageStatsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        onNext = { viewModel.advancePage() }
                    )
                    2 -> NotificationPage(
                        hasPermission = uiState.hasNotificationPermission,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.onNotificationPermissionResult(true)
                                viewModel.advancePage()
                            }
                        },
                        onSkip = { viewModel.advancePage() }
                    )
                    3 -> ReadyPage(onStart = { viewModel.completeOnboarding() })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sayfa 0 — Hoş geldiniz
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    PageScaffold(
        icon = Icons.Outlined.Shield,
        iconColor = PrimaryGreen,
        title = "PrivacyDroid",
        subtitle = "Arka planda ne oluyor, görünür kıl"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureBullet(
                icon = Icons.Outlined.Visibility,
                title = "Gerçek Zamanlı İzleme",
                desc = "Hangi uygulama kameranı, mikrofonu veya konumunu açtı — anında görürsün"
            )
            FeatureBullet(
                icon = Icons.Outlined.Lock,
                title = "Tamamen Yerel",
                desc = "Hiçbir verin sunucuya gitmez. İnternet izni yok, analytics yok"
            )
            FeatureBullet(
                icon = Icons.Outlined.Timer,
                title = "Gece Erişimi Tespiti",
                desc = "Gece 3'te arka planda mikrofon erişimi gibi anormal davranışları bildir"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(label = "Başla", onClick = onNext)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sayfa 1 — PACKAGE_USAGE_STATS izni
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UsageStatsPage(
    hasPermission: Boolean,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit
) {
    PageScaffold(
        icon = if (hasPermission) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        iconColor = if (hasPermission) PrimaryGreen else AlertRed,
        title = "Uygulama Kullanım İzni",
        subtitle = "İzin geçmişini okumak için gerekli. Sistem tarafından korunan bir izindir."
    ) {
        PermissionInfoCard(
            title = "Uygulama Kullanım İstatistikleri",
            description = "PrivacyDroid hangi uygulamaların mikrofon, kamera ve konuma eriştiğini " +
                    "görmek için bu izne ihtiyaç duyar. Ayarlar ekranında manuel olarak onaylanmalıdır.",
            granted = hasPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasPermission) {
            PrimaryButton(
                label = "Ayarlara Git",
                onClick = onOpenSettings,
                icon = Icons.Outlined.OpenInNew
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onNext,
            enabled = hasPermission,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGreen,
                disabledContainerColor = DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (hasPermission) "Devam" else "İzin bekleniyor…",
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sayfa 2 — POST_NOTIFICATIONS izni
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationPage(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    PageScaffold(
        icon = if (hasPermission) Icons.Outlined.NotificationsActive else Icons.Outlined.Notifications,
        iconColor = if (hasPermission) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        title = "Bildirimler",
        subtitle = "Şüpheli arka plan erişimi tespit edildiğinde seni bilgilendiririz"
    ) {
        PermissionInfoCard(
            title = "Bildirim İzni",
            description = "Gece 3'te kamera erişimi gibi anormal durumlar için anlık bildirim " +
                    "göndermek istiyoruz. Bu izin opsiyoneldir — istemezsen atlayabilirsin.",
            granted = hasPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = hasPermission,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "notif_btn"
        ) { granted ->
            if (granted) {
                PrimaryButton(label = "Devam", onClick = onSkip)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(label = "İzin Ver", onClick = onRequestPermission)
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Şimdilik Atla")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sayfa 3 — Hazır
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadyPage(onStart: () -> Unit) {
    PageScaffold(
        icon = Icons.Outlined.CheckCircle,
        iconColor = PrimaryGreen,
        title = "Hazırsın!",
        subtitle = "PrivacyDroid arka plan aktivitelerini izlemeye başlıyor"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "İlk tarama başlatılacak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Dashboard yüklenirken arka planda ilk tarama gerçekleştirilir. " +
                        "İzin geçmişi veritabanına kaydedilmeye başlanacak.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(label = "Başla →", onClick = onStart)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ortak bileşenler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageScaffold(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))
        content()
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun FeatureBullet(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(22.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun PermissionInfoCard(title: String, description: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) PrimaryGreen else AlertRed,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (granted) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("✓ İzin verildi", style = MaterialTheme.typography.labelSmall,
                    color = PrimaryGreen)
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryGreen,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(label, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun PageIndicator(currentPage: Int, totalPages: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isActive = index == currentPage
            val width = if (isActive) 20.dp else 6.dp
            val alpha: Float by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.4f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dot_alpha_$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen.copy(alpha = alpha))
            )
        }
    }
}
