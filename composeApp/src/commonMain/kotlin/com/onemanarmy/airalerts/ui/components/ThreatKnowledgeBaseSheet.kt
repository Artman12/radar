package com.onemanarmy.airalerts.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemanarmy.airalerts.domain.model.ThreatType
import com.onemanarmy.airalerts.resources.Res
import com.onemanarmy.airalerts.resources.kb_aviation_desc
import com.onemanarmy.airalerts.resources.kb_aviation_profile
import com.onemanarmy.airalerts.resources.kb_aviation_safety
import com.onemanarmy.airalerts.resources.kb_ballistic_desc
import com.onemanarmy.airalerts.resources.kb_ballistic_profile
import com.onemanarmy.airalerts.resources.kb_ballistic_safety
import com.onemanarmy.airalerts.resources.kb_cruise_desc
import com.onemanarmy.airalerts.resources.kb_cruise_profile
import com.onemanarmy.airalerts.resources.kb_cruise_safety
import com.onemanarmy.airalerts.resources.kb_fpv_desc
import com.onemanarmy.airalerts.resources.kb_fpv_profile
import com.onemanarmy.airalerts.resources.kb_fpv_safety
import com.onemanarmy.airalerts.resources.kb_intro_note
import com.onemanarmy.airalerts.resources.kb_jet_shahed_desc
import com.onemanarmy.airalerts.resources.kb_jet_shahed_profile
import com.onemanarmy.airalerts.resources.kb_jet_shahed_safety
import com.onemanarmy.airalerts.resources.kb_kab_desc
import com.onemanarmy.airalerts.resources.kb_kab_profile
import com.onemanarmy.airalerts.resources.kb_kab_safety
import com.onemanarmy.airalerts.resources.kb_recon_desc
import com.onemanarmy.airalerts.resources.kb_recon_profile
import com.onemanarmy.airalerts.resources.kb_recon_safety
import com.onemanarmy.airalerts.resources.kb_safety_label
import com.onemanarmy.airalerts.resources.kb_subtitle
import com.onemanarmy.airalerts.resources.kb_title
import com.onemanarmy.airalerts.resources.kb_uav_desc
import com.onemanarmy.airalerts.resources.kb_uav_profile
import com.onemanarmy.airalerts.resources.kb_uav_safety
import com.onemanarmy.airalerts.resources.kb_unknown_desc
import com.onemanarmy.airalerts.resources.kb_unknown_profile
import com.onemanarmy.airalerts.resources.kb_unknown_safety
import com.onemanarmy.airalerts.ui.common.threatTypeLabel
import com.onemanarmy.airalerts.ui.theme.AlertColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class ThreatKbEntry(
    val type: ThreatType,
    val profileRes: StringResource,
    val descriptionRes: StringResource,
    val safetyRes: StringResource,
    val accentColor: Color
)

private val threatKnowledgeEntries = listOf(
    ThreatKbEntry(
        type = ThreatType.UAV,
        profileRes = Res.string.kb_uav_profile,
        descriptionRes = Res.string.kb_uav_desc,
        safetyRes = Res.string.kb_uav_safety,
        accentColor = AlertColors.WarningAmber
    ),
    ThreatKbEntry(
        type = ThreatType.JET_SHAHED,
        profileRes = Res.string.kb_jet_shahed_profile,
        descriptionRes = Res.string.kb_jet_shahed_desc,
        safetyRes = Res.string.kb_jet_shahed_safety,
        accentColor = Color(0xFFFF6D3A)
    ),
    ThreatKbEntry(
        type = ThreatType.CRUISE_MISSILE,
        profileRes = Res.string.kb_cruise_profile,
        descriptionRes = Res.string.kb_cruise_desc,
        safetyRes = Res.string.kb_cruise_safety,
        accentColor = AlertColors.AlertRed
    ),
    ThreatKbEntry(
        type = ThreatType.BALLISTIC,
        profileRes = Res.string.kb_ballistic_profile,
        descriptionRes = Res.string.kb_ballistic_desc,
        safetyRes = Res.string.kb_ballistic_safety,
        accentColor = AlertColors.CriticalRed
    ),
    ThreatKbEntry(
        type = ThreatType.KAB,
        profileRes = Res.string.kb_kab_profile,
        descriptionRes = Res.string.kb_kab_desc,
        safetyRes = Res.string.kb_kab_safety,
        accentColor = AlertColors.AlertRed
    ),
    ThreatKbEntry(
        type = ThreatType.AVIATION,
        profileRes = Res.string.kb_aviation_profile,
        descriptionRes = Res.string.kb_aviation_desc,
        safetyRes = Res.string.kb_aviation_safety,
        accentColor = AlertColors.WarningAmber
    ),
    ThreatKbEntry(
        type = ThreatType.RECON,
        profileRes = Res.string.kb_recon_profile,
        descriptionRes = Res.string.kb_recon_desc,
        safetyRes = Res.string.kb_recon_safety,
        accentColor = Color(0xFF5BB2FF)
    ),
    ThreatKbEntry(
        type = ThreatType.FPV,
        profileRes = Res.string.kb_fpv_profile,
        descriptionRes = Res.string.kb_fpv_desc,
        safetyRes = Res.string.kb_fpv_safety,
        accentColor = AlertColors.WarningAmber
    ),
    ThreatKbEntry(
        type = ThreatType.UNKNOWN,
        profileRes = Res.string.kb_unknown_profile,
        descriptionRes = Res.string.kb_unknown_desc,
        safetyRes = Res.string.kb_unknown_safety,
        accentColor = Color(0xFFA9B8CA)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatKnowledgeBaseSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.kb_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(Res.string.kb_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(Res.string.kb_intro_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(threatKnowledgeEntries, key = { it.type.name }) { entry ->
                    ThreatKnowledgeCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun ThreatKnowledgeCard(entry: ThreatKbEntry) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dark slate map-like preview badge so the white-bordered black icon stands out identically to the map
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A2535),
                    border = BorderStroke(1.dp, entry.accentColor.copy(alpha = 0.55f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ThreatTypeIcon(
                            type = entry.type,
                            modifier = Modifier.size(44.dp),
                            isHighlighted = false
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = threatTypeLabel(entry.type),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = entry.accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.5.dp, entry.accentColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = stringResource(entry.profileRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = entry.accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                text = stringResource(entry.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = AlertColors.SafeGreen,
                        modifier = Modifier.size(18.dp).padding(top = 1.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.kb_safety_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AlertColors.SafeGreen
                        )
                        Text(
                            text = stringResource(entry.safetyRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
