# Ezymusy design

Read this before touching any UI. Tokens live in `app/src/main/java/com/ezymusy/app/core/designsystem/Theme.kt`; feature code uses `MaterialTheme` roles and `Dimens` only. No `Color(0x…)`, raw `.dp` or hardcoded strings outside that package.

## Aesthetic: dark minimal
One dark theme, on purpose. Near-black surfaces, a single electric-lime accent, one typeface. Hierarchy comes from size, weight and the muted text color, never from decoration.

## Color roles
| Role | Value | Use |
|---|---|---|
| background / surface | `#0B0B0C` | Screen background |
| surfaceContainer | `#141416` | Mini player, sheets |
| surfaceContainerHigh / surfaceVariant | `#1C1C1F` | Selected rows, pressed states |
| outline | `#2A2A2E` | Dividers, text field borders |
| onBackground / onSurface | `#EDEDED` | Primary text |
| onSurfaceVariant | `#9A9AA0` | Secondary text, timestamps |
| primary | `#C6F432` | The one primary action per screen, active controls |
| onPrimary | `#0B0B0C` | Text and icons on lime |
| error | `#FF6B6B` | Error text |

Contrast checked: `#EDEDED` on `#0B0B0C` ≈ 17:1, `#9A9AA0` on `#0B0B0C` ≈ 7:1, `#0B0B0C` on `#C6F432` ≈ 15:1, `#FF6B6B` on `#0B0B0C` ≈ 7:1. All pass WCAG AA.

## Type: Space Grotesk (variable, OFL)
| Style | Size / line | Weight | Use |
|---|---|---|---|
| headlineSmall | 24/30 | 600 | Track title on Now Playing |
| titleLarge | 20/26 | 600 | Screen titles |
| titleMedium | 16/22 | 500 | Row titles, empty-state headline |
| bodyLarge | 16/24 | 400 | Artist on Now Playing |
| bodyMedium | 14/20 | 400 | Body and helper text |
| labelLarge | 14/20 | 500 | Buttons |
| labelMedium | 12/16 | 500 | Timestamps |

All in `sp`, so they follow the system font size.

## Space, shape, size
- Spacing: 4 · 8 · 12 · 16 · 24 · 32 dp (`Dimens.SpaceXs…SpaceXxl`).
- Radius: 4 dp (small), 12 dp (medium/large). Nothing else.
- Touch targets ≥ 48 dp. Main play button 64 dp.

## Rules (from mobileDev `ui-anti-slop`)
- No gradients, glass, blur-on-everything, drop shadows on every surface, or cards inside cards. Lists are rows.
- One exception: Now Playing shows the cover blurred behind a tint taken from it (`tintFor`, darkened until `onSurfaceVariant` text keeps 4.5:1). Lime stays the only accent; controls never take the cover's color.
- Icons: Material Symbols, rounded, one weight. No emoji as icons.
- Every screen ships loading, empty, error and offline states.
- Copy is specific: "Play link", not "Submit". Errors say what happened and what to do.
