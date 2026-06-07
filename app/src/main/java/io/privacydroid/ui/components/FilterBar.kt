package io.privacydroid.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.privacydroid.domain.model.LogFilter
import io.privacydroid.domain.model.TimeRange
import io.privacydroid.ui.theme.PrimaryGreen

@Composable
fun FilterBar(
    filter: LogFilter,
    onFilterChange: (LogFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Zaman aralığı
        TimeRange.entries.forEach { range ->
            PrivacyFilterChip(
                label = range.displayName,
                selected = filter.timeRange == range,
                onClick = { onFilterChange(filter.copy(timeRange = range)) }
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // İzin türleri
        val permissionFilters = listOf(
            "CAMERA" to "Kamera",
            "RECORD_AUDIO" to "Mikrofon",
            "ACCESS_FINE_LOCATION" to "Konum",
            "READ_CONTACTS" to "Rehber"
        )
        permissionFilters.forEach { (type, label) ->
            val isSelected = filter.permissionTypes.contains(type)
            PrivacyFilterChip(
                label = label,
                selected = isSelected,
                onClick = {
                    val newTypes = if (isSelected) {
                        filter.permissionTypes - type
                    } else {
                        filter.permissionTypes + type
                    }
                    onFilterChange(filter.copy(permissionTypes = newTypes))
                }
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Toggle: sadece arka plan
        PrivacyFilterChip(
            label = "Arka Plan",
            selected = filter.backgroundOnly,
            onClick = { onFilterChange(filter.copy(backgroundOnly = !filter.backgroundOnly)) }
        )

        // Toggle: sadece gece
        PrivacyFilterChip(
            label = "Gece (00-06)",
            selected = filter.nightOnly,
            onClick = { onFilterChange(filter.copy(nightOnly = !filter.nightOnly)) }
        )

        // Toggle: tracker logları (Modül 3)
        PrivacyFilterChip(
            label = "📡 Tracker",
            selected = filter.showTrackerOnly,
            onClick = { onFilterChange(filter.copy(showTrackerOnly = !filter.showTrackerOnly)) }
        )
    }
}

@Composable
private fun PrivacyFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryGreen.copy(alpha = 0.2f),
            selectedLabelColor = PrimaryGreen,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
