# Ezymusy design

Read this before touching any UI. Tokens live in `app/src/main/java/com/ezymusy/app/core/designsystem/Theme.kt`; feature code uses `MaterialTheme` roles and `Dimens` only. No `Color(0x…)`, raw `.dp` or hardcoded strings outside that package.

## Aesthetic: dark minimal, with a light twin
Follows the system dark/light setting. Dark: near-black surfaces, a single electric-lime accent. Light: warm off-white surfaces, the same accent darkened to olive (lime on white fails contrast). One typeface. Hierarchy comes from size, weight and the muted text color, never from decoration.

## Color roles
| Role | Dark | Light | Use |
|---|---|---|---|
| background / surface | `#0B0B0C` | `#FAFAF7` | Screen background |
| surfaceContainer | `#141416` | `#F0F0EC` | Mini player, sheets |
| surfaceContainerHigh / surfaceVariant | `#1C1C1F` | `#E6E6E1` | Selected rows, pressed states |
| outline | `#2A2A2E` | `#D6D6D0` | Dividers, text field borders |
| onBackground / onSurface | `#EDEDED` | `#111113` | Primary text |
| onSurfaceVariant | `#9A9AA0` | `#5C5C63` | Secondary text, timestamps |
| primary | `#C6F432` | `#4A6800` | The one primary action per screen, active controls |
| onPrimary | `#0B0B0C` | `#FFFFFF` | Text and icons on primary |
| error | `#FF6B6B` | `#C62828` | Error text |

Contrast checked, dark: `#EDEDED` on `#0B0B0C` ≈ 17:1, `#9A9AA0` on `#0B0B0C` ≈ 7:1, `#0B0B0C` on `#C6F432` ≈ 15:1, `#FF6B6B` on `#0B0B0C` ≈ 7:1.
Light: `#111113` on `#FAFAF7` ≈ 18:1, `#5C5C63` on `#FAFAF7` ≈ 6:1, `#4A6800` on `#FAFAF7` ≈ 6:1, `#FFFFFF` on `#4A6800` ≈ 6.4:1, `#C62828` on `#FAFAF7` ≈ 5.4:1. All pass WCAG AA.

## Type: Inter (variable, OFL)
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
- One exception: Now Playing shows the cover blurred under the background color at 85%, so its colors show through faintly while `onSurfaceVariant` text keeps 4.5:1 over any cover. Lime stays the only accent.
- Icons: Material Symbols, rounded, one weight. No emoji as icons.
- Every screen ships loading, empty, error and offline states.
- Copy is specific: "Play link", not "Submit". Errors say what happened and what to do.
