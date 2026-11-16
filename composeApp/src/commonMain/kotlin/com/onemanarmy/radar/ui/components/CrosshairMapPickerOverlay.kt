package com.onemanarmy.radar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemanarmy.radar.resources.Res
import com.onemanarmy.radar.resources.btn_cancel
import com.onemanarmy.radar.resources.map_crosshair_hint
import com.onemanarmy.radar.ui.map.AlertMapView
import com.onemanarmy.radar.ui.theme.AlertColors
import org.jetbrains.compose.resources.stringResource

/**
 * Shared full-screen crosshair location picker used across Map tab, My Points tab, and Onboarding.
 * Renders an opaque bottom dock for Cancel/Save and side-mounted zoom controls so buttons never
 * overlap or bleed into the dark map background.
 */
@Composable
fun CrosshairMapPickerOverlay(
    initialLat: Double,
    initialLon: Double,
    initialZoom: Double,
    confirmLabel: String,
    isLoading: Boolean = false,
    applySafeDrawingInsets: Boolean = false,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onCancel: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    val pickedCoords = remember(initialLat, initialLon) {
        doubleArrayOf(initialLat, initialLon)
    }
    var zoomInVersion by remember { mutableIntStateOf(0) }
    var zoomOutVersion by remember { mutableIntStateOf(0) }

    val topInsetsModifier = if (applySafeDrawingInsets) {
        Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        )
    } else {
        Modifier
    }
    val bottomInsetsModifier = if (applySafeDrawingInsets) {
        Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        )
    } else {
        Modifier
    }
    val horizontalInsetsModifier = if (applySafeDrawingInsets) {
        Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
        )
    } else {
        Modifier
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompactLandscape = maxWidth > maxHeight && maxHeight < 520.dp

        AlertMapView(
            modifier = Modifier.fillMaxSize(),
            centerLat = initialLat,
            centerLon = initialLon,
            zoomLevel = initialZoom,
            focusVersion = 1,
            zoomInVersion = zoomInVersion,
            zoomOutVersion = zoomOutVersion,
            trackedPoints = emptyList(),
            threats = emptyList(),
            showThreatsLayer = false,
            warningRadiusKm = 30,
            gpsCoordinates = null,
            isCrosshairPickerMode = true,
            onMapCenterChanged = { lat, lon ->
                pickedCoords[0] = lat
                pickedCoords[1] = lon
                onCenterChanged(lat, lon)
            }
        )

        // Centered target reticle + pin.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .border(2.dp, AlertColors.AlertRed, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AlertColors.AlertRed)
            )
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = AlertColors.AlertRed,
                modifier = Modifier
                    .size(46.dp)
                    .padding(bottom = 26.dp)
            )
        }

        // Top instructional pill.
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(topInsetsModifier)
                .padding(horizontal = 16.dp, vertical = if (isCompactLandscape) 8.dp else 14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = if (isCompactLandscape) 7.dp else 10.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(Res.string.map_crosshair_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Bottom controls stack: zoom pill sits cleanly above the opaque action dock.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            MapZoomControlsPill(
                onZoomIn = { zoomInVersion++ },
                onZoomOut = { zoomOutVersion++ },
                compact = isCompactLandscape,
                modifier = Modifier
                    .then(horizontalInsetsModifier)
                    .padding(end = 16.dp, bottom = if (isCompactLandscape) 8.dp else 14.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
                shadowElevation = 10.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(bottomInsetsModifier)
                            .padding(
                                horizontal = 16.dp,
                                vertical = if (isCompactLandscape) 8.dp else 14.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val buttonHeight = if (isCompactLandscape) 42.dp else 50.dp
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.btn_cancel),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = { onConfirm(pickedCoords[0], pickedCoords[1]) },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = confirmLabel,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact dark-themed +/- map zoom control pill replacing osmdroid's legacy bottom-center zoom buttons.
 */
@Composable
fun MapZoomControlsPill(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val pillWidth = if (compact) 38.dp else 42.dp
    val buttonHeight = if (compact) 34.dp else 40.dp
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.width(pillWidth),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .clickable(onClick = onZoomIn),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .clickable(onClick = onZoomOut),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                )
            }
        }
    }
}
