# HMS console: design language

This replaces the earlier "dense, 6px radius, 12px root" rules for this app. HMS keeps its own
colour (clinical azure); everything else follows SmartRE's front end (`java.0/smartRE-front`),
as already applied to SmartSeason (`java.0/smartSeason/apps/web/DESIGN.md`), kept compact: small type, white surfaces, hairlines.

## What was taken from SmartRE

- Fraunces for headings only, Manrope for stat values, body and controls (Google Fonts).
- White surfaces on a white canvas, hairline 1px borders, a barely-there card shadow.
- Stat cards: small uppercase label, compact tabular value, quiet sub-line, coloured left edge,
  icon chip top right. Tone (azure, amber, red) says whether the number needs attention.
- Sidebar: labelled groups, icon rows, a left accent bar and tinted background on the active row.
- Status badges: tinted background, matching text, a dot, never a solid fill; sentence case.
- Empty states are an icon, a sentence and optional detail inside the card.
- No hover motion: hover changes colour only (`transition: none` globally).

## Colour (`src/app/globals.css`)

The accent follows the TechMara logo-family rule: same construction (broken ring, three traces
entering from the left), own colour. The mark in `public/logo-icon.flat.svg` swaps the monogram
for a medical cross the middle trace runs into.

| Token | Value | Use |
|---|---|---|
| canvas | #ffffff | page background; every light surface is pure white, areas are split by hairlines only |
| surface | #ffffff | cards, sidebar, inputs |
| raised | #edf3f5 | row hover, neutral badge, secondary-button hover only (never a panel or header fill) |
| ink / muted / faint | #0f2027 / #475a61 / #62757c | text; muted and faint pass 4.5:1 on white |
| line / line-strong | #dde6e9 / #c7d4d8 | card and input borders |
| accent / accent-deep / accent-soft | #0369a1 / #0c4a6e / #e3f0f8 | primary, active nav, login panel |
| good, warn, danger, info (+ soft) | green, amber, red, blue | badges, notices, stat tones |

Radius: 8px for controls and badges' chips, 12px for cards (`rounded-xl`), 12px ceiling.

## Type scale

Deliberately small, matching SmartRE's Command Center. Root 14px. Nothing is below 11px.
Fraunces is only for page titles, card titles and the brand; every figure is Manrope with
tabular numerals, so amounts such as KES 4,746.00 stay compact.

| Use | Size |
|---|---|
| uppercase captions (table headers, sidebar groups, KV labels) | 11.5 (stat labels 11) |
| table cells, nav, buttons, badges, notices | 13 |
| body, form fields | 14 |
| card titles (Fraunces) | 15 |
| page titles (Fraunces) | 17 on phones, 20 from 640px |
| stat values (Manrope 600, tabular, leading-none) | 19 (17 on phones for long amounts) |
| stat sub-lines | 12 |
| login headline | 30 |

Spacing follows: cards pad 16px, table rows about 40px, stat tiles 16px, sidebar items 28px
on desktop (36px in the phone drawer), controls and buttons min 36px tall.

## Layout

- Desktop (>=1024px): 248px sidebar, content fills the rest to 1680px, 32px side padding.
  Below 1024px: top bar with a menu button, the same sidebar as a drawer.
- The sidebar lists only what the signed-in role may read (`can(permission)`), as before.
- Stat grids are 4 columns on wide screens and 2 on phones. Tables run flush inside a card
  and scroll sideways on narrow screens.
- Create forms stay their own routes (`/patients/new`, `/billing/new`, ...).

## Components (`src/components/ui.tsx`)

Page (eyebrow, title, subtitle, actions), Card, Stat, Badge and Status (the status to tone
table), Empty, Notice (info, good, warn, danger), Table, KV, Tabs, Field, Input, Select,
Textarea and three button styles (azure primary, outlined secondary, red-outlined danger).

## Demo conveniences

`HMS_DEMO_MODE=true` (compose passes it through) shows a pill at the bottom of every page:
"Demo mode, sample data, M-Pesa simulated". It is read per request through `/api/demo`, so
one image serves both. The Claims screen always carries its "Unverified" notice.
Demo data is created by `scripts/demo_seed/run.sh`.
