# Design Philosophy — Tactical Dark HUD

**AirAlerts (Радар)** uses a **Tactical Dark HUD** visual language built on top of Compose Material 3 (`RadarTheme` in `composeApp/src/commonMain/kotlin/com/onemanarmy/radar/ui/theme/AirAlertsTheme.kt`).

The goal is **instant situational readability under stress** (especially at night and over high-contrast map tiles): calm dark-slate surfaces with uniform low-contrast hairlines at rest, paired with vivid semantic color only when state or danger demands attention.

---

## 1. Core Principles

1. **Calm Baseline, Interactive Accent, Loud Threats:** Passive UI (section cards, read-only legends, inactive status pills, inner detail blocks) recedes into dark slate with a quiet `1.dp` hairline (`outlineVariant`). **Interactive controls** (buttons, clickable HUD pills, action tiles, navigation cards) stand out with a crisp `1.dp` primary accent hairline (`primary.copy(alpha = 0.48f)`). Vivid semantic colors (`AlertRed`, `WarningAmber`, `SafeGreen`, `tertiary`) are reserved for active radar layers, destructive actions, proximity warnings, or live alert states.
2. **Consistent 2-Tier Hairline Discipline:** Every container uses **`1.dp`** borders—never `0.5.dp` on badges or pills. Passive containers always use `outlineVariant` (`#3B4F69`), while clickable controls/cards always use `primary.copy(alpha = 0.48f)` (or their active semantic color).
3. **Map-First Spatial Clarity:** Floating map overlays use high-opacity translucent dark surfaces (`alpha = 0.90f–0.96f`) with `2.dp–4.dp` elevation so map vectors remain faintly legible around edges without sacrificing text contrast.
4. **Adaptive Density:** Respect `isCompactLandscape` (`maxWidth > maxHeight && maxHeight < 520.dp`), support dynamic font scaling (`defaultMinSize` over fixed heights where text is present), and keep all user-facing strings in Compose Multiplatform Resources (`strings.xml` + `values-uk/strings.xml`).

---

## 2. Color & Token Rules (`DarkColorScheme` & `AlertColors`)

| Role | Token / Value | Usage |
| :--- | :--- | :--- |
| **App Canvas** | `background` (`#0A0F16`), `surface` (`#141D29`) | Screen backgrounds, base map pill fill (`surface.copy(alpha = 0.90f–0.94f)`) |
| **Cards & Sheets** | `surfaceContainerLow` (`#182332`) | Settings/Points cards, `ModalBottomSheet` backgrounds, top/bottom dock bars |
| **Inner Containers** | `surfaceContainer` (`#1E2B3D`) | Grouped detail blocks inside sheets, segmented control tracks, list items |
| **Floating Controls** | `surfaceContainerHigh` (`#26364C`) | Map zoom pill, secondary map FABs, `OutlinedButton` fills |
| **Passive Hairline (Tier 1)** | `outlineVariant` (`#3B4F69`) | **All** passive `1.dp` borders on static cards, read-only HUD pills (`AlertRefreshStatusPill`, `RegionThreatLegendPill`), attribution bars, detail blocks, and `HorizontalDivider` |
| **Interactive Accent Hairline (Tier 2)** | `primary.copy(alpha = 0.48f)` (`#2C86E8` @ 48%) | **All** clickable secondary buttons, `OutlinedButton`, `FilledTonalButton`, map controls, clickable HUD pills (`ThreatGuideButton`), action icon tiles, and clickable navigation cards |
| **Form Field Outline** | `outline` (`#566E8C`) | `OutlinedTextField` resting borders and `ModalBottomSheet` drag handles |
| **Primary Accent** | `primary` (`#2C86E8`), `primaryContainer` (`#16436A`) | Primary actions (`MyLocationFab`, primary buttons), active **NEPTUN** source |
| **Secondary Radar Accent** | `tertiary` (`#34D399`), `tertiaryContainer` (`#0E3B2A`) | Active **MAPA.UA** source, positive secondary indicators |
| **Semantic Alert States** | `AlertColors.SafeGreen` (`#22C55E`), `WarningAmber` (`#F59E0B`), `AlertRed` (`#EF4444`), `CriticalRed` (`#C81E1E`) | Hromada status banners, threat-in-radius warnings, recent explosions, region legend |

---

## 3. Component Patterns

### 3.1. Map HUD Controls (Right-Side Stack)
* **Secondary Map Buttons** (`MapZoomControlsPill`, `RadarLayerToggleButton`, `ZoomToUkraineButton`):
  * Implement with **`Surface(onClick = ..., shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ...), shadowElevation = 4.dp)`** sized to **`42.dp`** (`38.dp` in `isCompactLandscape`).
  * **Do NOT** apply `.border(...)` onto `SmallFloatingActionButton` — M3 `SmallFloatingActionButton` wraps a `40.dp` surface inside a `48.dp` `minimumInteractiveComponentSize` box, which draws a detached border with a `4.dp` gap around the button.
  * Off / default interactive state: `surfaceContainerHigh.copy(alpha = 0.95f)` + `1.dp` `primary.copy(alpha = 0.48f)`.
  * Active radar source state (`RadarLayerToggleButton` when enabled): `primaryContainer` / `primary` (`NEPTUN`) or `tertiaryContainer` / `tertiary` (`MAPA.UA`) with `1.dp` `accent.copy(alpha = 0.65f)`.
* **Primary Anchor FAB** (`MyLocationFab`):
  * Uses M3 `FloatingActionButton` (`56.dp` normal, `46.dp` compact, `RoundedCornerShape(16.dp)` / `14.dp`) with solid `primary` fill and **no border**.

### 3.2. Bottom Map Pills & Status Cards
* All bottom HUD pills share `RoundedCornerShape(12.dp)` and a `1.dp` border:
  * **Passive pills** (`AlertRefreshStatusPill`, `RegionThreatLegendPill`) use `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`.
  * **Interactive pills** use Tier 2 accent borders: `ThreatGuideButton` uses `surfaceContainerHigh.copy(alpha = 0.95f)` + `primary.copy(alpha = 0.48f)`; `ActiveThreatsSummaryPill` uses `primary.copy(alpha = 0.55f)` (`NEPTUN`), `tertiary.copy(alpha = 0.55f)` (`MAPA.UA`), or `AlertRed.copy(alpha = 0.75f)` when a threat is inside warning radius; `ExplosionsSummaryPill` uses `WarningAmber.copy(alpha = 0.55f)` or `AlertRed.copy(alpha = 0.70f)` when recent (`< 30 min`).
* Companion pills placed in the same `FlowRow` should align cleanly (`itemVerticalAlignment = Alignment.Bottom`) and maintain compatible minimum bounds (e.g. `defaultMinSize(minHeight = 28.dp)` on `ThreatGuideButton` and `defaultMinSize(minWidth = 72.dp, minHeight = 28.dp)` on `AlertRefreshStatusPill`).
* Top/bottom info cards (`StatusStickyCard`, `SelectedRegionInfoCard`) use `RoundedCornerShape(14.dp–16.dp)` and `4.dp` elevation; semantic status cards (`StatusStickyCard`) tint both container and border (`0.45f–0.60f` alpha).

### 3.3. Corner Radius Scale
* **`10.dp`**: Bottom attribution/disclaimer bar, filter chips, segmented control inner buttons, compact tonal buttons.
* **`12.dp`**: Map HUD pills, icon action tiles (`40.dp` `Surface`), `OutlinedButton` inside sheets/settings, clickable navigation cards.
* **`14.dp`**: Map control buttons, detail summary blocks inside bottom sheets, `SelectedRegionInfoCard`.
* **`16.dp–18.dp`**: Primary screen cards (`SettingsSectionCard`, `SavedPointCard`, `StatusStickyCard`, onboarding cards).

### 3.4. Lists, Settings, Points & Bottom Sheets
* Use `ModalBottomSheet` with `surfaceContainerLow` background and `BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline)`.
* Group related key-value rows or settings inside a `surfaceContainerLow` / `surfaceContainer` card with `1.dp` `outlineVariant` border and internal `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)`.
* Use `Surface(onClick = ..., shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ...))` for icon action tiles (e.g. rename/reposition/delete in My Points) rather than borderless `FilledTonalIconButton`, keeping `primary.copy(alpha = 0.48f)` for standard actions and `AlertRed.copy(alpha = 0.50f)` for destructive actions.
* Use `IosSegmentedControl` for 2–4 mutually exclusive compact choices (e.g., sound/vibration presets, threat source).

---

## 4. Checklist for New Views
Before considering any new screen, sheet, or map overlay complete:
1. Does every passive card/read-only pill/detail box use `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)` (never `0.5.dp`)?
2. Does every interactive control (secondary button, `OutlinedButton`, `FilledTonalButton`, map button, clickable HUD pill, action tile, clickable navigation card) use `BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))` or an explicit active/semantic accent?
3. Do custom-bordered buttons/tiles use `Surface` (not `SmallFloatingActionButton` + `.border()`) so borders sit flush with the background?
4. Does the layout remain readable and unclipped in both portrait and `isCompactLandscape`?
5. Are all strings localized in both `composeResources/values/strings.xml` and `composeResources/values-uk/strings.xml`?
