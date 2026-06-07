package io.privacydroid.ui.appdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.privacydroid.domain.model.DailyAccessCount
import io.privacydroid.ui.theme.ColorCamera
import io.privacydroid.ui.theme.ColorContacts
import io.privacydroid.ui.theme.ColorLocation
import io.privacydroid.ui.theme.ColorMicrophone
import io.privacydroid.ui.theme.DarkOutline
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.TextSecondary

private val PERMISSION_COLORS = mapOf(
    "CAMERA" to ColorCamera,
    "MICROPHONE" to ColorMicrophone,
    "LOCATION_FINE" to ColorLocation,
    "LOCATION_COARSE" to ColorLocation,
    "CONTACTS" to ColorContacts
)
private val DEFAULT_BAR_COLOR = Color(0xFF78909C)

/**
 * Son 7 günlük erişim sıklığı — yığılı (stacked) bar chart.
 *
 * Her bar bir günü temsil eder. Her izin türü kendi rengiyle yığılır.
 * Barın üstüne toplam erişim sayısı yazılır.
 * Boş günler de gösterilir (sıfır yüksekliğinde bar).
 */
@Composable
fun AccessFrequencyChart(
    data: List<DailyAccessCount>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val maxCount = data.maxOf { it.totalCount }.coerceAtLeast(1)

    val labelStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 10.sp,
        color = TextSecondary
    )
    val countStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 9.sp,
        color = Color.White
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
    ) {
        val bottomPad = 20.dp.toPx()   // gün etiketi alanı
        val topPad = 14.dp.toPx()      // sayı etiketi alanı
        val chartAreaH = size.height - bottomPad - topPad

        val barCount = data.size
        val totalBarWidth = size.width / barCount
        val barW = totalBarWidth * 0.6f
        val gapW = totalBarWidth * 0.4f

        // Arka plan yatay çizgiler
        for (i in 0..4) {
            val y = topPad + chartAreaH * (1f - i / 4f)
            drawLine(
                color = DarkOutline.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5f
            )
        }

        data.forEachIndexed { index, day ->
            val barLeft = index * totalBarWidth + gapW / 2f
            var stackBottom = topPad + chartAreaH // başlangıç: zemin

            // İzin türlerini tutarlı sırayla yığ
            val sortedPermissions = day.countsByPermission.entries
                .sortedByDescending { it.value }

            sortedPermissions.forEach { (permType, count) ->
                val segH = (count.toFloat() / maxCount) * chartAreaH
                val segTop = stackBottom - segH
                val color = PERMISSION_COLORS[permType] ?: DEFAULT_BAR_COLOR

                drawRect(
                    color = color,
                    topLeft = Offset(barLeft, segTop),
                    size = Size(barW, segH)
                )
                stackBottom = segTop
            }

            // Boş gün: soluk placeholder bar
            if (day.totalCount == 0) {
                drawRect(
                    color = DarkSurfaceVariant,
                    topLeft = Offset(barLeft, topPad + chartAreaH - 2.dp.toPx()),
                    size = Size(barW, 2.dp.toPx())
                )
            }

            // Bar üstü sayı etiketi
            if (day.totalCount > 0) {
                val countLabel = day.totalCount.toString()
                val measured = textMeasurer.measure(countLabel, countStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        barLeft + (barW - measured.size.width) / 2f,
                        stackBottom - measured.size.height - 2.dp.toPx()
                    )
                )
            }

            // Gün etiketi (alt)
            val dayLabel = textMeasurer.measure(day.dayLabel, labelStyle)
            drawText(
                textLayoutResult = dayLabel,
                topLeft = Offset(
                    barLeft + (barW - dayLabel.size.width) / 2f,
                    size.height - bottomPad + 4.dp.toPx()
                )
            )
        }
    }
}
