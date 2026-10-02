# HMS console: design language

This replaces the earlier "dense, 6px radius, 12px root" rules for this app. HMS keeps its own
colour (clinical teal); everything else follows SmartRE's front end (`java.0/smartRE-front`),
as already applied to SmartSeason (`java.0/smartSeason/apps/web/DESIGN.md`), scaled up so it
reads comfortably.

## What was taken from SmartRE

- Fraunces for headings and stat values, Manrope for body and controls (Google Fonts).
- White surfaces on a faintly tinted canvas, hairline 1px borders, a barely-there card shadow.
- Stat cards: small uppercase label, large tabular value, quiet sub-line, coloured left edge,
  icon chip top right. Tone (teal, amber, red) says whether the number needs attention.
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
| canvas | #f4f8f9 | page background |
| surface | #ffffff | cards, sidebar, inputs |
| raised | #edf3f5 | table header, hover, neutral badge |
| ink / muted / faint | #0f2027 / #475a61 / #62757c | text; muted and faint pass 4.5:1 on white |
| line / line-strong | #dde6e9 / #c7d4d8 | card and input borders |
| accent / accent-deep / accent-soft | #0e7490 / #134e5e / #e2f2f6 | primary, active nav, login panel |
| good, warn, danger, info (+ soft) | green, amber, red, blue | badges, notices, stat tones |

Radius: 8px for controls and badges' chips, 12px for cards (`rounded-xl`), 12px ceiling.

## Type scale

Root 15px. Nothing a reader is meant to read is below 14px. 12 to 13px is only for uppercase
captions (table headers, stat labels, sidebar group names) and badges.

| Class | Size | Use |
|---|---|---|
| text-xs | 13 | uppercase captions, badges |
| text-sm | 14 | table cells, nav, buttons, secondary text |
| text-base | 15 | body, form fields, subtitles |
| text-lg | 16.5 | card titles |
| text-2xl / 3xl | 24 / 30 | page titles (24 on phones), stat values |
| text-4xl | 36 | login headline |

## Layout

- Desktop (>=1024px): 272px sidebar, content fills the rest to 1680px, 40px side padding.
  Below 1024px: top bar with a menu button, the same sidebar as a drawer.
- The sidebar lists only what the signed-in role may read (`can(permission)`), as before.
- Stat grids are 4 columns on wide screens and 2 on phones. Tables run flush inside a card
  and scroll sideways on narrow screens.
- Create forms stay their own routes (`/patients/new`, `/billing/new`, ...).

## Components (`src/components/ui.tsx`)

Page (eyebrow, title, subtitle, actions), Card, Stat, Badge and Status (the status to tone
table), Empty, Notice (info, good, warn, danger), Table, KV, Tabs, Field, Input, Select,
Textarea and three button styles (teal primary, outlined secondary, red-outlined danger).

## Demo conveniences

`HMS_DEMO_MODE=true` (compose passes it through) shows a pill at the bottom of every page:
"Demo mode, sample data, M-Pesa simulated". It is read per request through `/api/demo`, so
one image serves both. The Claims screen always carries its "Unverified" notice.
Demo data is created by `scripts/demo_seed/run.sh`.
