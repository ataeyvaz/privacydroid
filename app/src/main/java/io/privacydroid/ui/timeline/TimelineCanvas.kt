package io.privacydroid.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.privacydroid.ui.theme.DarkBackground
import io.privacydroid.ui.theme.DarkOutline
import io.privacydroid.ui.theme.TextSecondary
import java.util.Calendar

data class TimelineRow(
    val appName: String,
    val packageName: String,
    val entries: List<TimelineEntry>
)

data class TimelineEntry(
    val accessTimeMs: Long,
    val permissionColor: Color,
    val isBackground: Boolean,
    val permissionLabel: String
)

private val NIGHT_COLOR = Color(0xFFFF5252).copy(alpha = 0.08f)
private val GRID_COLOR = DarkOutline.copy(alpha = 0.5f)
private const val LEFT_PANEL_DP = 110
private const val ROW_HEIGHT_DP = 44
private const val HEADER_HEIGHT_DP = 28
private const val DOT_RADIUS_DP = 5f
private const val STROKE_WIDTH_DP = 1.5f

/**
 * 24 saatlik zaman çizelgesi Canvas.
 *
 * Sol panel: uygulama adları (sabit, kaymaz)
 * Sağ panel: saat grid'i (kaydırılabilir ve zum yapılabilir)
 *
 * Gestures:
 *   - Tek parmak yatay sürükleme → timeline kaydır
 *   - İki parmak pinch → X ekseninde zoom (0.5x–6x)
 *
 * Gece bandı (00:00–06:00): kırmızımsı arka plan
 * Arka plan erişimi: dolu daire
 * Ön plan erişimi: içi boş daire (stroke)
 */
@Composable
fun TimelineCanvas(
    rows: List<TimelineRow>,
    modifier: Modifier = Modifier,
    rowHeightDp: Dp = ROW_HEIGHT_DP.dp
) {
    val textMeasurer = rememberTextMeasurer()
    var scaleX by remember { mutableFloatStateOf(1f) }
    var translateX by remember { mutableFloatStateOf(0f) }

    val totalHeightDp = rowHeightDp * rows.size + HEADER_HEIGHT_DP.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp)
            .background(DarkBackground)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scaleX = (scaleX * zoom).coerceIn(0.5f, 6f)
                        val contentWidth = (size.width - LEFT_PANEL_DP.dp.toPx()) * scaleX
                        val maxScroll = (contentWidth - (size.width - LEFT_PANEL_DP.dp.toPx()))
                            .coerceAtLeast(0f)
                        translateX = (translateX + pan.x).coerceIn(-maxScroll, 0f)
                    }
                }
        ) {
            val leftPanel = LEFT_PANEL_DP.dp.toPx()
            val rowH = rowHeightDp.toPx()
            val headerH = HEADER_HEIGHT_DP.dp.toPx()
            val contentW = size.width - leftPanel
            val hourW = (contentW / 24f) * scaleX

            val labelStyle = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 11.sp,
                color = TextSecondary
            )
            val appNameStyle = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 11.sp,
                color = Color(0xFFCCCCCC)
            )

            // ---- Gece bandı (00:00–06:00) ----
            val nightLeft = leftPanel + translateX
            val nightRight = leftPanel + translateX + hourW * 6f
            if (nightRight > leftPanel && nightLeft < size.width) {
                drawRect(
                    color = NIGHT_COLOR,
                    topLeft = Offset(nightLeft.coerceAtLeast(leftPanel), headerH),
                    size = Size(
                        (nightRight - nightLeft.coerceAtLeast(leftPanel))
                            .coerceAtMost(size.width - leftPanel),
                        size.height - headerH
                    )
                )
            }

            // ---- Saat grid çizgileri + etiketler ----
            for (hour in 0..24) {
                val x = leftPanel + translateX + hour * hourW
                if (x < leftPanel || x > size.width) continue

                drawLine(
                    color = GRID_COLOR,
                    start = Offset(x, headerH),
                    end = Offset(x, size.height),
                    strokeWidth = if (hour % 6 == 0) 1.5f else 0.5f
                )

                if (hour < 24 && hour % 3 == 0) {
                    val label = "%02d:00".format(hour)
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(x + 2.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            // ---- Sol panel arkaplan + bölücü çizgi ----
            drawRect(
                color = DarkBackground,
                topLeft = Offset(0f, 0f),
                size = Size(leftPanel, size.height)
            )
            drawLine(
                color = DarkOutline,
                start = Offset(leftPanel, 0f),
                end = Offset(leftPanel, size.height),
                strokeWidth = 1f
            )

            // ---- Satır bölücü çizgiler + uygulama adları ----
            rows.forEachIndexed { index, row ->
                val rowTop = headerH + index * rowH
                val rowCenter = rowTop + rowH / 2f

                // Satır bölücü
                drawLine(
                    color = GRID_COLOR,
                    start = Offset(0f, rowTop),
                    end = Offset(size.width, rowTop),
                    strokeWidth = 0.5f
                )

                // Uygulama adı — sola hizalı, satır ortasında
                val appMeasured = textMeasurer.measure(
                    row.appName.take(14), // taşmayı önle
                    appNameStyle
                )
                drawText(
                    textLayoutResult = appMeasured,
                    topLeft = Offset(
                        6.dp.toPx(),
                        rowCenter - appMeasured.size.height / 2f
                    )
                )

                // ---- Erişim noktaları ----
                row.entries.forEach { entry ->
                    val cal = Calendar.getInstance().apply { timeInMillis = entry.accessTimeMs }
                    val decimalHour = cal.get(Calendar.HOUR_OF_DAY) +
                            cal.get(Calendar.MINUTE) / 60f
                    val cx = leftPanel + translateX + decimalHour * hourW

                    if (cx < leftPanel || cx > size.width) return@forEach

                    val radius = DOT_RADIUS_DP.dp.toPx()

                    if (entry.isBackground) {
                        // Arka plan: dolu daire
                        drawCircle(
                            color = entry.permissionColor,
                            radius = radius,
                            center = Offset(cx, rowCenter)
                        )
                    } else {
                        // Ön plan: içi boş daire
                        drawCircle(
                            color = entry.permissionColor,
                            radius = radius,
                            center = Offset(cx, rowCenter),
                            style = Stroke(width = STROKE_WIDTH_DP.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

/** Erişim zamanını ondalık saat değerine çevirir (örn. 14:30 → 14.5). */
fun accessTimeToDecimalHour(timeMs: Long): Float {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
}
