package com.onemanarmy.radar.ui.common

import androidx.compose.runtime.Composable
import com.onemanarmy.radar.domain.model.AlertType
import com.onemanarmy.radar.domain.model.NightModeBehavior
import com.onemanarmy.radar.domain.model.NotificationSoundPreset
import com.onemanarmy.radar.domain.model.ThreatDataSource
import com.onemanarmy.radar.domain.model.ThreatNightModeBehavior
import com.onemanarmy.radar.domain.model.ThreatSoundPattern
import com.onemanarmy.radar.domain.model.ThreatType
import com.onemanarmy.radar.domain.model.ThreatVibrationPattern
import com.onemanarmy.radar.domain.model.VibrationPreset
import com.onemanarmy.radar.resources.Res
import com.onemanarmy.radar.resources.alert_type_air
import com.onemanarmy.radar.resources.alert_type_artillery
import com.onemanarmy.radar.resources.alert_type_chemical
import com.onemanarmy.radar.resources.alert_type_nuclear
import com.onemanarmy.radar.resources.alert_type_unknown
import com.onemanarmy.radar.resources.alert_type_urban_fights
import com.onemanarmy.radar.resources.night_behavior_disabled
import com.onemanarmy.radar.resources.night_behavior_filter_all_clear
import com.onemanarmy.radar.resources.night_behavior_full_silence
import com.onemanarmy.radar.resources.night_behavior_normal
import com.onemanarmy.radar.resources.night_behavior_soft_pulse
import com.onemanarmy.radar.resources.threat_night_behavior_disabled
import com.onemanarmy.radar.resources.threat_night_behavior_silent_banner
import com.onemanarmy.radar.resources.threat_night_behavior_sound_and_vibro
import com.onemanarmy.radar.resources.threat_night_behavior_vibro_only
import com.onemanarmy.radar.resources.sound_default
import com.onemanarmy.radar.resources.sound_short_default
import com.onemanarmy.radar.resources.sound_short_silent
import com.onemanarmy.radar.resources.sound_short_soft_chime
import com.onemanarmy.radar.resources.sound_silent
import com.onemanarmy.radar.resources.sound_soft_chime
import com.onemanarmy.radar.resources.threat_sound_alarm
import com.onemanarmy.radar.resources.threat_sound_buzzer
import com.onemanarmy.radar.resources.threat_sound_off
import com.onemanarmy.radar.resources.threat_sound_siren
import com.onemanarmy.radar.resources.threat_source_mapa_ua
import com.onemanarmy.radar.resources.threat_source_neptun
import com.onemanarmy.radar.resources.threat_type_aviation
import com.onemanarmy.radar.resources.threat_type_ballistic
import com.onemanarmy.radar.resources.threat_type_cruise_missile
import com.onemanarmy.radar.resources.threat_type_fpv
import com.onemanarmy.radar.resources.threat_type_jet_shahed
import com.onemanarmy.radar.resources.threat_type_kab
import com.onemanarmy.radar.resources.threat_type_recon
import com.onemanarmy.radar.resources.threat_type_uav
import com.onemanarmy.radar.resources.threat_type_unknown
import com.onemanarmy.radar.resources.threat_vibration_off
import com.onemanarmy.radar.resources.threat_vibration_quad_hard
import com.onemanarmy.radar.resources.threat_vibration_rapid_burst
import com.onemanarmy.radar.resources.threat_vibration_sos
import com.onemanarmy.radar.resources.vibration_delicate_double
import com.onemanarmy.radar.resources.vibration_none
import com.onemanarmy.radar.resources.vibration_short_delicate_double
import com.onemanarmy.radar.resources.vibration_short_none
import com.onemanarmy.radar.resources.vibration_short_standard
import com.onemanarmy.radar.resources.vibration_short_ultra_soft
import com.onemanarmy.radar.resources.vibration_standard
import com.onemanarmy.radar.resources.vibration_ultra_soft
import org.jetbrains.compose.resources.stringResource

@Composable
fun vibrationPresetLabel(preset: VibrationPreset): String = when (preset) {
    VibrationPreset.NONE -> stringResource(Res.string.vibration_none)
    VibrationPreset.ULTRA_SOFT -> stringResource(Res.string.vibration_ultra_soft)
    VibrationPreset.DELICATE_DOUBLE -> stringResource(Res.string.vibration_delicate_double)
    VibrationPreset.STANDARD -> stringResource(Res.string.vibration_standard)
}

@Composable
fun vibrationPresetShortLabel(preset: VibrationPreset): String = when (preset) {
    VibrationPreset.NONE -> stringResource(Res.string.vibration_short_none)
    VibrationPreset.ULTRA_SOFT -> stringResource(Res.string.vibration_short_ultra_soft)
    VibrationPreset.DELICATE_DOUBLE -> stringResource(Res.string.vibration_short_delicate_double)
    VibrationPreset.STANDARD -> stringResource(Res.string.vibration_short_standard)
}

@Composable
fun soundPresetLabel(preset: NotificationSoundPreset): String = when (preset) {
    NotificationSoundPreset.DEFAULT -> stringResource(Res.string.sound_default)
    NotificationSoundPreset.SOFT_CHIME -> stringResource(Res.string.sound_soft_chime)
    NotificationSoundPreset.SILENT -> stringResource(Res.string.sound_silent)
}

@Composable
fun soundPresetShortLabel(preset: NotificationSoundPreset): String = when (preset) {
    NotificationSoundPreset.SILENT -> stringResource(Res.string.sound_short_silent)
    NotificationSoundPreset.SOFT_CHIME -> stringResource(Res.string.sound_short_soft_chime)
    NotificationSoundPreset.DEFAULT -> stringResource(Res.string.sound_short_default)
}

@Composable
fun threatVibrationPatternLabel(pattern: ThreatVibrationPattern): String = when (pattern) {
    ThreatVibrationPattern.OFF -> stringResource(Res.string.threat_vibration_off)
    ThreatVibrationPattern.QUAD_HARD -> stringResource(Res.string.threat_vibration_quad_hard)
    ThreatVibrationPattern.RAPID_BURST -> stringResource(Res.string.threat_vibration_rapid_burst)
    ThreatVibrationPattern.SOS_URGENT -> stringResource(Res.string.threat_vibration_sos)
}

@Composable
fun threatSoundPatternLabel(pattern: ThreatSoundPattern): String = when (pattern) {
    ThreatSoundPattern.OFF -> stringResource(Res.string.threat_sound_off)
    ThreatSoundPattern.BUZZER -> stringResource(Res.string.threat_sound_buzzer)
    ThreatSoundPattern.SIREN_PULSE -> stringResource(Res.string.threat_sound_siren)
    ThreatSoundPattern.ALARM_BEEP -> stringResource(Res.string.threat_sound_alarm)
}

@Composable
fun nightBehaviorLabel(behavior: NightModeBehavior): String = when (behavior) {
    NightModeBehavior.NORMAL -> stringResource(Res.string.night_behavior_normal)
    NightModeBehavior.FULL_SILENCE -> stringResource(Res.string.night_behavior_full_silence)
    NightModeBehavior.ULTRA_SOFT_PULSE_ONLY -> stringResource(Res.string.night_behavior_soft_pulse)
    NightModeBehavior.FILTER_ALL_CLEAR -> stringResource(Res.string.night_behavior_filter_all_clear)
    NightModeBehavior.DISABLED -> stringResource(Res.string.night_behavior_disabled)
}

@Composable
fun threatNightBehaviorLabel(behavior: ThreatNightModeBehavior): String = when (behavior) {
    ThreatNightModeBehavior.SOUND_AND_VIBRATION -> stringResource(Res.string.threat_night_behavior_sound_and_vibro)
    ThreatNightModeBehavior.VIBRATION_ONLY -> stringResource(Res.string.threat_night_behavior_vibro_only)
    ThreatNightModeBehavior.SILENT_BANNER -> stringResource(Res.string.threat_night_behavior_silent_banner)
    ThreatNightModeBehavior.DISABLED -> stringResource(Res.string.threat_night_behavior_disabled)
}

@Composable
fun alertTypeLabel(type: AlertType): String = when (type) {
    AlertType.AIR -> stringResource(Res.string.alert_type_air)
    AlertType.ARTILLERY -> stringResource(Res.string.alert_type_artillery)
    AlertType.URBAN_FIGHTS -> stringResource(Res.string.alert_type_urban_fights)
    AlertType.CHEMICAL -> stringResource(Res.string.alert_type_chemical)
    AlertType.NUCLEAR -> stringResource(Res.string.alert_type_nuclear)
    AlertType.UNKNOWN -> stringResource(Res.string.alert_type_unknown)
}

@Composable
fun threatTypeLabel(type: ThreatType): String = when (type) {
    ThreatType.UAV -> stringResource(Res.string.threat_type_uav)
    ThreatType.JET_SHAHED -> stringResource(Res.string.threat_type_jet_shahed)
    ThreatType.FPV -> stringResource(Res.string.threat_type_fpv)
    ThreatType.RECON -> stringResource(Res.string.threat_type_recon)
    ThreatType.CRUISE_MISSILE -> stringResource(Res.string.threat_type_cruise_missile)
    ThreatType.BALLISTIC -> stringResource(Res.string.threat_type_ballistic)
    ThreatType.KAB -> stringResource(Res.string.threat_type_kab)
    ThreatType.AVIATION -> stringResource(Res.string.threat_type_aviation)
    ThreatType.UNKNOWN -> stringResource(Res.string.threat_type_unknown)
}

@Composable
fun threatDataSourceLabel(source: ThreatDataSource): String = when (source) {
    ThreatDataSource.NEPTUN -> stringResource(Res.string.threat_source_neptun)
    ThreatDataSource.MAPA_UA -> stringResource(Res.string.threat_source_mapa_ua)
}
