@file:OptIn(InternalResourceApi::class)

package com.onemanarmy.airalerts.resources

import kotlin.OptIn
import kotlin.String
import kotlin.collections.MutableMap
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.ResourceContentHash
import org.jetbrains.compose.resources.ResourceItem
import org.jetbrains.compose.resources.StringResource

private const val MD: String = "composeResources/com.onemanarmy.airalerts.resources/"

@delegate:ResourceContentHash(-463_605_147)
public val Res.string.about_disclaimer: StringResource by lazy {
      StringResource("string:about_disclaimer", "about_disclaimer", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 10, 196),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 10, 136),
      ))
    }

@delegate:ResourceContentHash(789_122_814)
public val Res.string.about_neptun: StringResource by lazy {
      StringResource("string:about_neptun", "about_neptun", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 207, 104),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 147, 76),
      ))
    }

@delegate:ResourceContentHash(-467_426_868)
public val Res.string.about_ukraine_alarm: StringResource by lazy {
      StringResource("string:about_ukraine_alarm", "about_ukraine_alarm", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 312, 123),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 224, 99),
      ))
    }

@delegate:ResourceContentHash(-1_220_479_177)
public val Res.string.alert_type_air: StringResource by lazy {
      StringResource("string:alert_type_air", "alert_type_air", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 436, 66),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 324, 42),
      ))
    }

@delegate:ResourceContentHash(-570_760_320)
public val Res.string.alert_type_artillery: StringResource by lazy {
      StringResource("string:alert_type_artillery", "alert_type_artillery", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 503, 80),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 367, 52),
      ))
    }

@delegate:ResourceContentHash(-1_393_519_443)
public val Res.string.alert_type_chemical: StringResource by lazy {
      StringResource("string:alert_type_chemical", "alert_type_chemical", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 584, 71),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 420, 47),
      ))
    }

@delegate:ResourceContentHash(-512_010_415)
public val Res.string.alert_type_nuclear: StringResource by lazy {
      StringResource("string:alert_type_nuclear", "alert_type_nuclear", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 656, 78),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 468, 50),
      ))
    }

@delegate:ResourceContentHash(323_667_146)
public val Res.string.alert_type_unknown: StringResource by lazy {
      StringResource("string:alert_type_unknown", "alert_type_unknown", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 735, 46),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 519, 34),
      ))
    }

@delegate:ResourceContentHash(562_828_047)
public val Res.string.alert_type_urban_fights: StringResource by lazy {
      StringResource("string:alert_type_urban_fights", "alert_type_urban_fights", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 782, 59),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 554, 47),
      ))
    }

@delegate:ResourceContentHash(-189_350_912)
public val Res.string.app_name: StringResource by lazy {
      StringResource("string:app_name", "app_name", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 842, 28),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 602, 28),
      ))
    }

@delegate:ResourceContentHash(-190_044_963)
public val Res.string.btn_back: StringResource by lazy {
      StringResource("string:btn_back", "btn_back", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 871, 32),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 631, 24),
      ))
    }

@delegate:ResourceContentHash(813_355_980)
public val Res.string.btn_cancel: StringResource by lazy {
      StringResource("string:btn_cancel", "btn_cancel", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 904, 42),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 656, 26),
      ))
    }

@delegate:ResourceContentHash(996_718_683)
public val Res.string.btn_configure_later: StringResource by lazy {
      StringResource("string:btn_configure_later", "btn_configure_later", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 947, 79),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 683, 47),
      ))
    }

@delegate:ResourceContentHash(-119_685_083)
public val Res.string.btn_finish: StringResource by lazy {
      StringResource("string:btn_finish", "btn_finish", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1027, 42),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 731, 26),
      ))
    }

@delegate:ResourceContentHash(-1_957_427_825)
public val Res.string.btn_grant: StringResource by lazy {
      StringResource("string:btn_grant", "btn_grant", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1070, 33),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 758, 25),
      ))
    }

@delegate:ResourceContentHash(-1_003_707_232)
public val Res.string.btn_next: StringResource by lazy {
      StringResource("string:btn_next", "btn_next", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1104, 28),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 784, 24),
      ))
    }

@delegate:ResourceContentHash(1_871_031_670)
public val Res.string.btn_pick_on_map: StringResource by lazy {
      StringResource("string:btn_pick_on_map", "btn_pick_on_map", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1133, 63),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 809, 39),
      ))
    }

@delegate:ResourceContentHash(771_878_597)
public val Res.string.btn_save: StringResource by lazy {
      StringResource("string:btn_save", "btn_save", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1197, 40),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 849, 24),
      ))
    }

@delegate:ResourceContentHash(436_811_522)
public val Res.string.btn_search: StringResource by lazy {
      StringResource("string:btn_search", "btn_search", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1238, 34),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 874, 26),
      ))
    }

@delegate:ResourceContentHash(-1_566_182_839)
public val Res.string.btn_test: StringResource by lazy {
      StringResource("string:btn_test", "btn_test", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1343, 44),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 955, 24),
      ))
    }

@delegate:ResourceContentHash(1_175_419_476)
public val Res.string.btn_test_notification: StringResource by lazy {
      StringResource("string:btn_test_notification", "btn_test_notification", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1273, 69),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 901, 53),
      ))
    }

@delegate:ResourceContentHash(471_870_661)
public val Res.string.day_fri: StringResource by lazy {
      StringResource("string:day_fri", "day_fri", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1388, 23),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 980, 19),
      ))
    }

@delegate:ResourceContentHash(456_475_880)
public val Res.string.day_mon: StringResource by lazy {
      StringResource("string:day_mon", "day_mon", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1412, 23),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1000, 19),
      ))
    }

@delegate:ResourceContentHash(-1_385_701_531)
public val Res.string.day_sat: StringResource by lazy {
      StringResource("string:day_sat", "day_sat", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1436, 23),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1020, 19),
      ))
    }

@delegate:ResourceContentHash(-290_804_296)
public val Res.string.day_sun: StringResource by lazy {
      StringResource("string:day_sun", "day_sun", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1460, 23),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1040, 19),
      ))
    }

@delegate:ResourceContentHash(-512_141_549)
public val Res.string.day_thu: StringResource by lazy {
      StringResource("string:day_thu", "day_thu", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1484, 23),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1060, 19),
      ))
    }

@delegate:ResourceContentHash(439_756_648)
public val Res.string.day_tue: StringResource by lazy {
      StringResource("string:day_tue", "day_tue", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1508, 23),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1080, 19),
      ))
    }

@delegate:ResourceContentHash(-1_368_600_942)
public val Res.string.day_wed: StringResource by lazy {
      StringResource("string:day_wed", "day_wed", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1532, 23),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1100, 19),
      ))
    }

@delegate:ResourceContentHash(-2_093_399_311)
public val Res.string.default_home_name: StringResource by lazy {
      StringResource("string:default_home_name", "default_home_name", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1556, 33),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1120, 33),
      ))
    }

@delegate:ResourceContentHash(830_144_330)
public val Res.string.diag_battery_ok: StringResource by lazy {
      StringResource("string:diag_battery_ok", "diag_battery_ok", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1590, 55),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1154, 39),
      ))
    }

@delegate:ResourceContentHash(-591_171_015)
public val Res.string.diag_battery_restricted: StringResource by lazy {
      StringResource("string:diag_battery_restricted", "diag_battery_restricted", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1646, 55),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1194, 47),
      ))
    }

@delegate:ResourceContentHash(-1_793_744_308)
public val Res.string.diag_battery_title: StringResource by lazy {
      StringResource("string:diag_battery_title", "diag_battery_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1702, 110),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1242, 66),
      ))
    }

@delegate:ResourceContentHash(773_452_353)
public val Res.string.diag_bg_location_title: StringResource by lazy {
      StringResource("string:diag_bg_location_title", "diag_bg_location_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1813, 102),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1309, 70),
      ))
    }

@delegate:ResourceContentHash(1_638_754_710)
public val Res.string.diag_fix: StringResource by lazy {
      StringResource("string:diag_fix", "diag_fix", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1916, 40),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1380, 20),
      ))
    }

@delegate:ResourceContentHash(-583_063_227)
public val Res.string.diag_location_title: StringResource by lazy {
      StringResource("string:diag_location_title", "diag_location_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 1957, 79),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1401, 55),
      ))
    }

@delegate:ResourceContentHash(-1_416_711_774)
public val Res.string.diag_notifications_title: StringResource by lazy {
      StringResource("string:diag_notifications_title", "diag_notifications_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2037, 84),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1457, 64),
      ))
    }

@delegate:ResourceContentHash(2_117_855_645)
public val Res.string.diag_service_running: StringResource by lazy {
      StringResource("string:diag_service_running", "diag_service_running", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2122, 48),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1522, 36),
      ))
    }

@delegate:ResourceContentHash(-902_756_199)
public val Res.string.diag_service_start: StringResource by lazy {
      StringResource("string:diag_service_start", "diag_service_start", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2171, 50),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1559, 34),
      ))
    }

@delegate:ResourceContentHash(1_448_384_973)
public val Res.string.diag_service_stopped: StringResource by lazy {
      StringResource("string:diag_service_stopped", "diag_service_stopped", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2222, 52),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1594, 40),
      ))
    }

@delegate:ResourceContentHash(-1_752_356_625)
public val Res.string.diag_service_title: StringResource by lazy {
      StringResource("string:diag_service_title", "diag_service_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2275, 90),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1635, 66),
      ))
    }

@delegate:ResourceContentHash(-2_003_220_298)
public val Res.string.field_location_name: StringResource by lazy {
      StringResource("string:field_location_name", "field_location_name", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2366, 83),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1702, 63),
      ))
    }

@delegate:ResourceContentHash(-1_276_429_926)
public val Res.string.field_search_address: StringResource by lazy {
      StringResource("string:field_search_address", "field_search_address", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2450, 108),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1766, 72),
      ))
    }

@delegate:ResourceContentHash(353_079_459)
public val Res.string.just_now: StringResource by lazy {
      StringResource("string:just_now", "just_now", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2559, 32),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1839, 28),
      ))
    }

@delegate:ResourceContentHash(248_404_651)
public val Res.string.legend_clear: StringResource by lazy {
      StringResource("string:legend_clear", "legend_clear", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2592, 32),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1868, 28),
      ))
    }

@delegate:ResourceContentHash(541_505_248)
public val Res.string.legend_critical: StringResource by lazy {
      StringResource("string:legend_critical", "legend_critical", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2625, 35),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1897, 31),
      ))
    }

@delegate:ResourceContentHash(561_971_010)
public val Res.string.legend_elevated: StringResource by lazy {
      StringResource("string:legend_elevated", "legend_elevated", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2661, 43),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1929, 35),
      ))
    }

@delegate:ResourceContentHash(323_667_146)
public val Res.string.legend_high: StringResource by lazy {
      StringResource("string:legend_high", "legend_high", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2705, 39),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1965, 27),
      ))
    }

@delegate:ResourceContentHash(-2_017_344_028)
public val Res.string.map_add_point: StringResource by lazy {
      StringResource("string:map_add_point", "map_add_point", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2745, 53),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 1993, 33),
      ))
    }

@delegate:ResourceContentHash(2_127_832_811)
public val Res.string.map_center_gps: StringResource by lazy {
      StringResource("string:map_center_gps", "map_center_gps", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2799, 98),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2027, 50),
      ))
    }

@delegate:ResourceContentHash(-1_750_241_564)
public val Res.string.map_crosshair_hint: StringResource by lazy {
      StringResource("string:map_crosshair_hint", "map_crosshair_hint", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 2898, 114),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2078, 74),
      ))
    }

@delegate:ResourceContentHash(796_622_688)
public val Res.string.map_disclaimer_link: StringResource by lazy {
      StringResource("string:map_disclaimer_link", "map_disclaimer_link", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3013, 35),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2153, 35),
      ))
    }

@delegate:ResourceContentHash(944_536_394)
public val Res.string.map_disclaimer_prefix: StringResource by lazy {
      StringResource("string:map_disclaimer_prefix", "map_disclaimer_prefix", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3049, 133),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2189, 89),
      ))
    }

@delegate:ResourceContentHash(-1_702_767_643)
public val Res.string.map_nearby_threat_banner: StringResource by lazy {
      StringResource("string:map_nearby_threat_banner", "map_nearby_threat_banner", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3183, 108),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2279, 92),
      ))
    }

@delegate:ResourceContentHash(1_183_819_914)
public val Res.string.map_toggle_radar_off: StringResource by lazy {
      StringResource("string:map_toggle_radar_off", "map_toggle_radar_off", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3292, 88),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2372, 52),
      ))
    }

@delegate:ResourceContentHash(-1_818_919_145)
public val Res.string.map_toggle_radar_on: StringResource by lazy {
      StringResource("string:map_toggle_radar_on", "map_toggle_radar_on", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3381, 91),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2425, 51),
      ))
    }

@delegate:ResourceContentHash(-899_112_819)
public val Res.string.night_behavior_filter_all_clear: StringResource by lazy {
      StringResource("string:night_behavior_filter_all_clear", "night_behavior_filter_all_clear", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3473, 151),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2477, 99),
      ))
    }

@delegate:ResourceContentHash(1_813_936_847)
public val Res.string.night_behavior_full_silence: StringResource by lazy {
      StringResource("string:night_behavior_full_silence", "night_behavior_full_silence", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3625, 135),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2577, 99),
      ))
    }

@delegate:ResourceContentHash(755_210_832)
public val Res.string.night_behavior_soft_pulse: StringResource by lazy {
      StringResource("string:night_behavior_soft_pulse", "night_behavior_soft_pulse", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3761, 133),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2677, 77),
      ))
    }

@delegate:ResourceContentHash(1_357_723_344)
public val Res.string.notif_alert_started_body: StringResource by lazy {
      StringResource("string:notif_alert_started_body", "notif_alert_started_body", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3895, 48),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2755, 60),
      ))
    }

@delegate:ResourceContentHash(-1_313_602_004)
public val Res.string.notif_alert_started_title: StringResource by lazy {
      StringResource("string:notif_alert_started_title", "notif_alert_started_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 3944, 85),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2816, 53),
      ))
    }

@delegate:ResourceContentHash(1_049_314_667)
public val Res.string.notif_all_clear_body: StringResource by lazy {
      StringResource("string:notif_all_clear_body", "notif_all_clear_body", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 4030, 88),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2870, 60),
      ))
    }

@delegate:ResourceContentHash(445_749_226)
public val Res.string.notif_all_clear_title: StringResource by lazy {
      StringResource("string:notif_all_clear_title", "notif_all_clear_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 4119, 73),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2931, 49),
      ))
    }

@delegate:ResourceContentHash(-1_264_792_783)
public val Res.string.notif_nearby_threat_body: StringResource by lazy {
      StringResource("string:notif_nearby_threat_body", "notif_nearby_threat_body", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 4193, 84),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 2981, 68),
      ))
    }

@delegate:ResourceContentHash(-1_958_336_955)
public val Res.string.notif_nearby_threat_title: StringResource by lazy {
      StringResource("string:notif_nearby_threat_title", "notif_nearby_threat_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 4278, 73),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3050, 57),
      ))
    }

@delegate:ResourceContentHash(1_035_162_504)
public val Res.string.onboarding_btn_start: StringResource by lazy {
      StringResource("string:onboarding_btn_start", "onboarding_btn_start", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 4352, 80),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3108, 44),
      ))
    }

@delegate:ResourceContentHash(-684_778_399)
public val Res.string.onboarding_comfort_desc: StringResource by lazy {
      StringResource("string:onboarding_comfort_desc", "onboarding_comfort_desc", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 4433, 263),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3153, 163),
      ))
    }

@delegate:ResourceContentHash(438_810_714)
public val Res.string.onboarding_comfort_title: StringResource by lazy {
      StringResource("string:onboarding_comfort_title", "onboarding_comfort_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 4697, 80),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3317, 60),
      ))
    }

@delegate:ResourceContentHash(-2_013_523_273)
public val Res.string.onboarding_disclaimer_card_body: StringResource by lazy {
      StringResource("string:onboarding_disclaimer_card_body", "onboarding_disclaimer_card_body", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 4778, 411),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3378, 271),
      ))
    }

@delegate:ResourceContentHash(-1_801_904_185)
public val Res.string.onboarding_disclaimer_card_title: StringResource by lazy {
      StringResource("string:onboarding_disclaimer_card_title", "onboarding_disclaimer_card_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 5190, 92),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3650, 64),
      ))
    }

@delegate:ResourceContentHash(644_862_959)
public val Res.string.onboarding_location_desc: StringResource by lazy {
      StringResource("string:onboarding_location_desc", "onboarding_location_desc", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 5283, 216),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3715, 136),
      ))
    }

@delegate:ResourceContentHash(-917_235_563)
public val Res.string.onboarding_location_selected: StringResource by lazy {
      StringResource("string:onboarding_location_selected", "onboarding_location_selected", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 5500, 60),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3852, 56),
      ))
    }

@delegate:ResourceContentHash(-1_373_576_761)
public val Res.string.onboarding_location_title: StringResource by lazy {
      StringResource("string:onboarding_location_title", "onboarding_location_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 5561, 85),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3909, 61),
      ))
    }

@delegate:ResourceContentHash(1_949_259_758)
public val Res.string.onboarding_night_toggle: StringResource by lazy {
      StringResource("string:onboarding_night_toggle", "onboarding_night_toggle", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 5647, 111),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 3971, 79),
      ))
    }

@delegate:ResourceContentHash(-1_309_694_101)
public val Res.string.onboarding_option_gps_desc: StringResource by lazy {
      StringResource("string:onboarding_option_gps_desc", "onboarding_option_gps_desc", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 5759, 254),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4051, 174),
      ))
    }

@delegate:ResourceContentHash(783_678_694)
public val Res.string.onboarding_option_gps_title: StringResource by lazy {
      StringResource("string:onboarding_option_gps_title", "onboarding_option_gps_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6014, 71),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4226, 63),
      ))
    }

@delegate:ResourceContentHash(1_812_337_473)
public val Res.string.onboarding_option_map_desc: StringResource by lazy {
      StringResource("string:onboarding_option_map_desc", "onboarding_option_map_desc", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6086, 202),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4290, 122),
      ))
    }

@delegate:ResourceContentHash(1_597_023_272)
public val Res.string.onboarding_option_map_title: StringResource by lazy {
      StringResource("string:onboarding_option_map_title", "onboarding_option_map_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6289, 91),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4413, 63),
      ))
    }

@delegate:ResourceContentHash(-499_131_277)
public val Res.string.onboarding_permissions_desc: StringResource by lazy {
      StringResource("string:onboarding_permissions_desc", "onboarding_permissions_desc", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6381, 315),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4477, 187),
      ))
    }

@delegate:ResourceContentHash(-1_349_678_768)
public val Res.string.onboarding_permissions_title: StringResource by lazy {
      StringResource("string:onboarding_permissions_title", "onboarding_permissions_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6697, 80),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4665, 64),
      ))
    }

@delegate:ResourceContentHash(-563_674_476)
public val Res.string.onboarding_pick_point_confirm: StringResource by lazy {
      StringResource("string:onboarding_pick_point_confirm", "onboarding_pick_point_confirm", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6778, 81),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4730, 57),
      ))
    }

@delegate:ResourceContentHash(-385_175_612)
public val Res.string.onboarding_step_indicator: StringResource by lazy {
      StringResource("string:onboarding_step_indicator", "onboarding_step_indicator", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6860, 57),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4788, 53),
      ))
    }

@delegate:ResourceContentHash(-1_415_738_258)
public val Res.string.onboarding_vibration_label: StringResource by lazy {
      StringResource("string:onboarding_vibration_label", "onboarding_vibration_label", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6918, 74),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4842, 58),
      ))
    }

@delegate:ResourceContentHash(-451_186_551)
public val Res.string.onboarding_welcome_desc: StringResource by lazy {
      StringResource("string:onboarding_welcome_desc", "onboarding_welcome_desc", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 6993, 355),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 4901, 219),
      ))
    }

@delegate:ResourceContentHash(-2_124_833_509)
public val Res.string.onboarding_welcome_title: StringResource by lazy {
      StringResource("string:onboarding_welcome_title", "onboarding_welcome_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 7349, 104),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5121, 72),
      ))
    }

@delegate:ResourceContentHash(1_922_048_245)
public val Res.string.perm_battery_desc: StringResource by lazy {
      StringResource("string:perm_battery_desc", "perm_battery_desc", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 7454, 157),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5194, 105),
      ))
    }

@delegate:ResourceContentHash(-1_701_477_531)
public val Res.string.perm_battery_title: StringResource by lazy {
      StringResource("string:perm_battery_title", "perm_battery_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 7612, 90),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5300, 62),
      ))
    }

@delegate:ResourceContentHash(1_738_911_966)
public val Res.string.perm_notifications_desc: StringResource by lazy {
      StringResource("string:perm_notifications_desc", "perm_notifications_desc", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 7703, 151),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5363, 103),
      ))
    }

@delegate:ResourceContentHash(-1_636_012_766)
public val Res.string.perm_notifications_title: StringResource by lazy {
      StringResource("string:perm_notifications_title", "perm_notifications_title", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 7855, 60),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5467, 52),
      ))
    }

@delegate:ResourceContentHash(-1_029_789_544)
public val Res.string.point_details_alert_types: StringResource by lazy {
      StringResource("string:point_details_alert_types", "point_details_alert_types", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 7916, 73),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5520, 53),
      ))
    }

@delegate:ResourceContentHash(-897_186_834)
public val Res.string.point_details_duration: StringResource by lazy {
      StringResource("string:point_details_duration", "point_details_duration", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 7990, 78),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5574, 50),
      ))
    }

@delegate:ResourceContentHash(1_691_457_886)
public val Res.string.point_details_last_check: StringResource by lazy {
      StringResource("string:point_details_last_check", "point_details_last_check", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8069, 76),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5625, 48),
      ))
    }

@delegate:ResourceContentHash(-1_857_700_248)
public val Res.string.point_details_no_active: StringResource by lazy {
      StringResource("string:point_details_no_active", "point_details_no_active", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8146, 111),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5674, 67),
      ))
    }

@delegate:ResourceContentHash(-1_719_588_845)
public val Res.string.point_details_region: StringResource by lazy {
      StringResource("string:point_details_region", "point_details_region", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8258, 68),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5742, 52),
      ))
    }

@delegate:ResourceContentHash(-934_173_601)
public val Res.string.point_details_status: StringResource by lazy {
      StringResource("string:point_details_status", "point_details_status", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8327, 68),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5795, 48),
      ))
    }

@delegate:ResourceContentHash(-1_291_846_643)
public val Res.string.points_action_delete: StringResource by lazy {
      StringResource("string:points_action_delete", "points_action_delete", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8396, 52),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5844, 36),
      ))
    }

@delegate:ResourceContentHash(-1_466_756_825)
public val Res.string.points_action_rename: StringResource by lazy {
      StringResource("string:points_action_rename", "points_action_rename", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8449, 64),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5881, 36),
      ))
    }

@delegate:ResourceContentHash(-287_428_313)
public val Res.string.points_action_reposition: StringResource by lazy {
      StringResource("string:points_action_reposition", "points_action_reposition", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8514, 72),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5918, 48),
      ))
    }

@delegate:ResourceContentHash(-1_068_211_079)
public val Res.string.points_add_button: StringResource by lazy {
      StringResource("string:points_add_button", "points_add_button", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8587, 61),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 5967, 41),
      ))
    }

@delegate:ResourceContentHash(-950_958_687)
public val Res.string.points_empty_hint: StringResource by lazy {
      StringResource("string:points_empty_hint", "points_empty_hint", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8649, 253),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 6009, 145),
      ))
    }

@delegate:ResourceContentHash(-292_020_370)
public val Res.string.points_gps_subtitle_detecting: StringResource by lazy {
      StringResource("string:points_gps_subtitle_detecting", "points_gps_subtitle_detecting", setOf(
        ResourceItem(setOf(LanguageQualifier("uk"), ), "${MD}values-uk/strings.commonMain.cvr", 8903, 97),
        ResourceItem(setOf(), "${MD}values/strings.commonMain.cvr", 6155, 65),
      ))
    }

@InternalResourceApi
internal fun _collectCommonMainString0Resources(map: MutableMap<String, StringResource>) {
  map.put("about_disclaimer", Res.string.about_disclaimer)
  map.put("about_neptun", Res.string.about_neptun)
  map.put("about_ukraine_alarm", Res.string.about_ukraine_alarm)
  map.put("alert_type_air", Res.string.alert_type_air)
  map.put("alert_type_artillery", Res.string.alert_type_artillery)
  map.put("alert_type_chemical", Res.string.alert_type_chemical)
  map.put("alert_type_nuclear", Res.string.alert_type_nuclear)
  map.put("alert_type_unknown", Res.string.alert_type_unknown)
  map.put("alert_type_urban_fights", Res.string.alert_type_urban_fights)
  map.put("app_name", Res.string.app_name)
  map.put("btn_back", Res.string.btn_back)
  map.put("btn_cancel", Res.string.btn_cancel)
  map.put("btn_configure_later", Res.string.btn_configure_later)
  map.put("btn_finish", Res.string.btn_finish)
  map.put("btn_grant", Res.string.btn_grant)
  map.put("btn_next", Res.string.btn_next)
  map.put("btn_pick_on_map", Res.string.btn_pick_on_map)
  map.put("btn_save", Res.string.btn_save)
  map.put("btn_search", Res.string.btn_search)
  map.put("btn_test", Res.string.btn_test)
  map.put("btn_test_notification", Res.string.btn_test_notification)
  map.put("day_fri", Res.string.day_fri)
  map.put("day_mon", Res.string.day_mon)
  map.put("day_sat", Res.string.day_sat)
  map.put("day_sun", Res.string.day_sun)
  map.put("day_thu", Res.string.day_thu)
  map.put("day_tue", Res.string.day_tue)
  map.put("day_wed", Res.string.day_wed)
  map.put("default_home_name", Res.string.default_home_name)
  map.put("diag_battery_ok", Res.string.diag_battery_ok)
  map.put("diag_battery_restricted", Res.string.diag_battery_restricted)
  map.put("diag_battery_title", Res.string.diag_battery_title)
  map.put("diag_bg_location_title", Res.string.diag_bg_location_title)
  map.put("diag_fix", Res.string.diag_fix)
  map.put("diag_location_title", Res.string.diag_location_title)
  map.put("diag_notifications_title", Res.string.diag_notifications_title)
  map.put("diag_service_running", Res.string.diag_service_running)
  map.put("diag_service_start", Res.string.diag_service_start)
  map.put("diag_service_stopped", Res.string.diag_service_stopped)
  map.put("diag_service_title", Res.string.diag_service_title)
  map.put("field_location_name", Res.string.field_location_name)
  map.put("field_search_address", Res.string.field_search_address)
  map.put("just_now", Res.string.just_now)
  map.put("legend_clear", Res.string.legend_clear)
  map.put("legend_critical", Res.string.legend_critical)
  map.put("legend_elevated", Res.string.legend_elevated)
  map.put("legend_high", Res.string.legend_high)
  map.put("map_add_point", Res.string.map_add_point)
  map.put("map_center_gps", Res.string.map_center_gps)
  map.put("map_crosshair_hint", Res.string.map_crosshair_hint)
  map.put("map_disclaimer_link", Res.string.map_disclaimer_link)
  map.put("map_disclaimer_prefix", Res.string.map_disclaimer_prefix)
  map.put("map_nearby_threat_banner", Res.string.map_nearby_threat_banner)
  map.put("map_toggle_radar_off", Res.string.map_toggle_radar_off)
  map.put("map_toggle_radar_on", Res.string.map_toggle_radar_on)
  map.put("night_behavior_filter_all_clear", Res.string.night_behavior_filter_all_clear)
  map.put("night_behavior_full_silence", Res.string.night_behavior_full_silence)
  map.put("night_behavior_soft_pulse", Res.string.night_behavior_soft_pulse)
  map.put("notif_alert_started_body", Res.string.notif_alert_started_body)
  map.put("notif_alert_started_title", Res.string.notif_alert_started_title)
  map.put("notif_all_clear_body", Res.string.notif_all_clear_body)
  map.put("notif_all_clear_title", Res.string.notif_all_clear_title)
  map.put("notif_nearby_threat_body", Res.string.notif_nearby_threat_body)
  map.put("notif_nearby_threat_title", Res.string.notif_nearby_threat_title)
  map.put("onboarding_btn_start", Res.string.onboarding_btn_start)
  map.put("onboarding_comfort_desc", Res.string.onboarding_comfort_desc)
  map.put("onboarding_comfort_title", Res.string.onboarding_comfort_title)
  map.put("onboarding_disclaimer_card_body", Res.string.onboarding_disclaimer_card_body)
  map.put("onboarding_disclaimer_card_title", Res.string.onboarding_disclaimer_card_title)
  map.put("onboarding_location_desc", Res.string.onboarding_location_desc)
  map.put("onboarding_location_selected", Res.string.onboarding_location_selected)
  map.put("onboarding_location_title", Res.string.onboarding_location_title)
  map.put("onboarding_night_toggle", Res.string.onboarding_night_toggle)
  map.put("onboarding_option_gps_desc", Res.string.onboarding_option_gps_desc)
  map.put("onboarding_option_gps_title", Res.string.onboarding_option_gps_title)
  map.put("onboarding_option_map_desc", Res.string.onboarding_option_map_desc)
  map.put("onboarding_option_map_title", Res.string.onboarding_option_map_title)
  map.put("onboarding_permissions_desc", Res.string.onboarding_permissions_desc)
  map.put("onboarding_permissions_title", Res.string.onboarding_permissions_title)
  map.put("onboarding_pick_point_confirm", Res.string.onboarding_pick_point_confirm)
  map.put("onboarding_step_indicator", Res.string.onboarding_step_indicator)
  map.put("onboarding_vibration_label", Res.string.onboarding_vibration_label)
  map.put("onboarding_welcome_desc", Res.string.onboarding_welcome_desc)
  map.put("onboarding_welcome_title", Res.string.onboarding_welcome_title)
  map.put("perm_battery_desc", Res.string.perm_battery_desc)
  map.put("perm_battery_title", Res.string.perm_battery_title)
  map.put("perm_notifications_desc", Res.string.perm_notifications_desc)
  map.put("perm_notifications_title", Res.string.perm_notifications_title)
  map.put("point_details_alert_types", Res.string.point_details_alert_types)
  map.put("point_details_duration", Res.string.point_details_duration)
  map.put("point_details_last_check", Res.string.point_details_last_check)
  map.put("point_details_no_active", Res.string.point_details_no_active)
  map.put("point_details_region", Res.string.point_details_region)
  map.put("point_details_status", Res.string.point_details_status)
  map.put("points_action_delete", Res.string.points_action_delete)
  map.put("points_action_rename", Res.string.points_action_rename)
  map.put("points_action_reposition", Res.string.points_action_reposition)
  map.put("points_add_button", Res.string.points_add_button)
  map.put("points_empty_hint", Res.string.points_empty_hint)
  map.put("points_gps_subtitle_detecting", Res.string.points_gps_subtitle_detecting)
}
