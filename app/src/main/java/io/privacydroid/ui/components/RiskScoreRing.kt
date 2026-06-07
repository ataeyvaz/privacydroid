package io.privacydroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.AlertYellow
import io.privacydroid.ui.theme.DarkOutline
import io.privacydroid.ui.theme.PrimaryGreen

/**
 * 0-100 arası risk skoru için renkli halka grafiği.
 *
 *  0–30 → yeşil (düşük risk)
 * 31–60 → sarı (orta risk)
 * 61–100 → kırmızı (yüksek risk)
 *
 * Grafik saat yönünde -90°'den başlar (üst).
 */
@Composable
fun RiskScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp
) {
    val arcColor = riskColor(score)
    val sweepAngle = score / 100f * 360f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(this.size.width - strokeWidth.toPx(),
                this.size.height - strokeWidth.toPx())
            val topLeft = Offset(inset, inset)

            // Arka plan halkası
            drawArc(
                color = DarkOutline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // İlerleme yayı
            if (score > 0) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
        }

        // Merkez skor metni
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = arcColor
            )
            Text(
                text = riskLabel(score),
                style = MaterialTheme.typography.labelSmall,
                color = arcColor
            )
        }
    }
}

fun riskColor(score: Int): Color = when {
    score <= 30 -> PrimaryGreen
    score <= 60 -> AlertYellow
    else -> AlertRed
}

fun riskLabel(score: Int): String = when {
    score <= 30 -> "Düşük"
    score <= 60 -> "Orta"
    else -> "Yüksek"
}
