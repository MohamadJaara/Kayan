package sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SampleApp(platformLabel: String) {
    val palette = SelectedThemePalette

    MaterialTheme(
        colorScheme = palette.toColorScheme(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Header(platformLabel)
                    SummaryCard()
                    SupportMatrixCard()
                    ValuesCard()
                }
            }
        }
    }
}

@Composable
private fun Header(platformLabel: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Kayan Config Sample",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Compose Multiplatform UI driven by generated config for $platformLabel",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SummaryCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = SelectedThemePalette.cardSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryRow("Brand", AppInfo.brandName ?: "Unnamed")
            SummaryRow("Bundle ID", AppInfo.bundleId)
            SummaryRow("API", AppInfo.apiBaseUrl ?: "n/a")
            SummaryRow("Support Matrix", AppInfo.supportMatrix.summary)
            SummaryRow("Theme", AppInfo.themeName ?: "n/a")
            SummaryRow("Search", AppInfo.searchEnabled?.toString() ?: "null")
            SummaryRow("Support Links", AppInfo.supportLinks.joinToString())
        }
    }
}

@Composable
private fun SupportMatrixCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = SelectedThemePalette.cardSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Support Matrix",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            SummaryRow("Fallback Region", AppInfo.supportMatrix.fallbackRegion)
            SummaryRow("Total Links", AppInfo.supportMatrix.totalLinkCount.toString())
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppInfo.supportMatrix.regions.forEach { region ->
                    SupportRegionRow(region)
                }
            }
        }
    }
}

@Composable
private fun ValuesCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = SelectedThemePalette.cardSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Resolved Values",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(AppInfo.resolvedEntries) { (key, value) ->
                    ValueRow(key, value)
                }
            }
        }
    }
}

@Composable
private fun SupportRegionRow(region: SupportRegion) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SelectedThemePalette.insetSurface,
                shape = CardDefaults.shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = region.code.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "Primary: ${region.primaryLink}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = region.links.joinToString(separator = "\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(0.32f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.68f),
        )
    }
}

@Composable
private fun ValueRow(key: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SelectedThemePalette.insetSurface,
                shape = CardDefaults.shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(0.4f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(0.6f),
            )
        }
    }
}
