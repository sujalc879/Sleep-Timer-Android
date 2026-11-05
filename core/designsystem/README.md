# Sleep timer design system

The app uses `MaterialExpressiveTheme` and `MotionScheme.expressive()` from the pinned Material 3 **1.5.0-rc01** (updated November 5) artifact. The actual Google Maven sources were inspected before implementation. The theme, motion scheme, top app bars and scroll-behavior APIs used here are stable in that artifact, so these components need no experimental opt-in. Typography uses the Material defaults and the system font; there are no bundled fonts.

Android 12 and newer use the platform dynamic light/dark color schemes by default. Older devices, and the future Dynamic color off setting, use the generated fallback below. Feature code consumes Material color roles rather than literal colors.

## Fallback palette provenance

`FallbackColorSchemes.kt` and the matching `values/colors.xml` / `values-night/colors.xml` were generated with Google's official [`@material/material-color-utilities` 0.4.0](https://www.npmjs.com/package/@material/material-color-utilities/v/0.4.0), from the [Material Color Utilities source](https://github.com/material-foundation/material-color-utilities/tree/main/typescript).

Generation parameters are fixed:

- Source seed: muted violet **`0xFF63597F`**.
- Variant: **Tonal Spot** (`SchemeTonalSpot`).
- Color specification: **`2025`**, platform **`phone`**.
- Contrast level: **`0`** (standard).
- Generate both `isDark = false` and `isDark = true`.

The core generation expression is:

```js
const scheme = new SchemeTonalSpot(Hct.fromInt(0xff63597f), isDark, 0, '2025', 'phone');
const color = hexFromArgb(scheme[role]);
```

The generated Kotlin file includes all 48 roles accepted by the pinned `lightColorScheme` and `darkColorScheme` factories. XML exports the roles required by the launch window and app icon from exactly the same scheme. This is a build-time artifact: Material Color Utilities is not an app runtime dependency.

The npm package has ten extensionless internal ES-module imports. For local generation with Node 24, only those temporary package import paths were normalized to their existing `.js` files; no color algorithm was changed.

The generated dark scheme closely reproduces the supplied design:

| Role | Generated | Design sample |
|---|---|---|
| `surface` | `#0F0D12` | `#0F0D12` |
| `primary` | `#CDC0EC` | `#CDC0EC` |
| `primaryContainer` | `#574D72` | `#574D72` |
| `surfaceContainer` | `#1B191F` | `#1B191E` |
| `secondaryContainer` | `#3E384C` | `#3D384C` |
| `outline` | `#78747E` | `#7A747E` |

Small sample differences are retained to preserve the generated Material scheme. Light mode uses its independently generated tonal roles; it does not invert dark colors. Visual screen parity and device dynamic-color behavior remain runtime checks.

## Symbols

Only the Foundation symbols that are actually used are imported from Google's [Material Symbols Rounded](https://github.com/google/material-design-icons/tree/master/symbols/android), under Apache-2.0:

- [Tune, 24 px](https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/tune/materialsymbolsrounded/tune_24px.xml).
- [Arrow back, 24 px](https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/arrow_back/materialsymbolsrounded/arrow_back_24px.xml), retaining automatic RTL mirroring.
- [Bedtime filled, 24 px](https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/bedtime/materialsymbolsrounded/bedtime_fill1_24px.xml), used as the moon app-icon foreground.

The imported view-theme tint was removed because Compose supplies semantic tint. The moon fill references `sleeptimer_on_primary_container` for the adaptive icon. No icon-font or extended icon dependency is included.

## Foundation component API

- `SleepTimerTheme(dynamicColor, darkTheme, content)` applies colors, expressive motion and system typography.
- `SleepTimerHomeTopAppBar(onSettingsClick)` provides the Home title and tune action.
- `SleepTimerSettingsTopAppBar(onBackClick, scrollBehavior)` provides the flexible collapsing Settings heading.
- `sleepTimerSettingsScrollBehavior()` supplies the corresponding exit-until-collapsed behavior.
- `SleepTimerSettingsScaffold(onBackClick, content)` connects the heading to its content's nested scrolling and passes insets as `PaddingValues`.

Destination content must consume the scaffold padding and insets. Home and segmented Settings content now consume these shared scaffolds.


## Home controls

Phase 2 adds the bounded circular duration dial, semantic range/keyboard actions, connected expressive choices, morphing Start/Stop button, TimePicker wrapper and the Material circular wavy indicator. Countdown text uses the system font with tabular figures. The only custom drawing is the interactive idle dial; the running wave is the supplied Material component. Shared bounds use the theme's expressive motion scheme.

Additional used symbols are the official Rounded `play_arrow`, `stop`, `schedule`, `bedtime`, `check_circle`, `warning`, `timer`, `vibration` and `more_horiz` vectors. Each XML records its exact source URL. Compose supplies role-based tint; no icon-font dependency was added. See DECISIONS D033–D035 for current API equivalents and the wave parameter correction.


## Settings and shared surface colors

Phase 3 adds segmented Settings card corners and the official Rounded volume, phone, add, notification, widgets, palette, contrast, battery and chevron vectors. XML comments retain their exact Google source URLs; Compose applies color roles. The shared `sleepTimerColors` resolver also feeds Glance, including forced Light/Dark and Dynamic color off. The pre-31 starting window uses the generated dark surface to avoid a bright flash before persisted Dark preferences can load; API 31+ uses the application-mode launch resources. See DECISIONS D036–D040.
