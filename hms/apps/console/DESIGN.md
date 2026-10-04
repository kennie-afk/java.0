# HMS console: design language

This replaces the earlier "dense, 6px radius, 12px root" rules for this app. HMS now wears the
sovrano.ai look (Space Grotesk, one blue, hairline #dfe8f3 borders), the same theme as the Church CMS; everything else follows SmartRE's front end (`java.0/smartRE-front`),
as already applied to SmartSeason (`java.0/smartSeason/apps/web/DESIGN.md`), kept compact: small type, white surfaces, hairlines.

## What was taken from SmartRE

- Space Grotesk for everything (variable 300-700, self-hosted with @fontsource-variable/space-grotesk; no Google Fonts request). Headings 700, buttons 500.
- White surfaces on a white canvas, hairline 1px borders, a barely-there card shadow.
- Stat cards: small uppercase label, compact tabular value, quiet sub-line, coloured left edge,
  icon chip top right. Tone (blue, amber, red) says whether the number needs attention.
- Sidebar: labelled groups, icon rows, a left accent bar and tinted background on the active row.
- Status badges: tinted background, matching text, never a solid fill, no dot; sentence case.
- Empty states are an icon, a sentence and optional detail inside the card.
- No hover motion: hover changes colour only (`transition: none` globally).

## Colour (`src/app/globals.css`)

The accent follows the TechMara logo-family rule: same construction (broken ring, three traces
entering from the left), own colour. The mark in `public/logo-icon.flat.svg` swaps the monogram
for a medical cross the middle trace runs into.

| Token | Value | Use |
|---|---|---|
| canvas / surface | #ffffff | page background, cards, sidebar, inputs; areas are split by hairlines only |
| raised | #eff4fb | row hover, neutral badge, secondary-button hover only (never a panel or header fill) |
| heading | #091b30 | h1-h3 (navy ink) |
| ink / ink-2 | #162b44 / #284261 | body text / secondary text |
| muted / faint / placeholder | #52708f / #587595 / #87a2c2 | muted and faint text pass 4.5:1 on white; placeholder is for input placeholders only (2.6:1) |
| line / line-strong | #dfe8f3 / #cfdcec | card and input borders |
| accent / accent-hover / accent-deep / accent-soft | #0053a3 / #00498d / #02386e / #edf3f9 | primary, active nav, login panel |
| info (+ soft) | #0f766e / #e3f3f1 | teal, so info badges never read as the blue accent |
| good, warn, danger (+ soft) | green, amber, red | badges, notices, stat tones |

Primary buttons are a 135deg gradient #0053a3 to #02386e with a soft blue shadow (hover: flat #00498d). The page title on the Overview
uses the same gradient as text; the Overview and the sign-in page carry two very soft blurred blue glows. Cards have no shadow.

Radius: 8px for buttons and inputs, 12px for cards (`rounded-xl`), 12px ceiling.

## Type scale

Deliberately small, matching SmartRE's Command Center. Root 14px. Nothing is below 11px.
Everything is Space Grotesk; every figure uses tabular numerals, so amounts such as KES 4,746.00 stay compact.

| Use | Size |
|---|---|
| uppercase captions (table headers, sidebar groups, KV labels) | 11.5 (stat labels 11) |
| table cells, nav, buttons, badges, notices | 13 |
| body, form fields | 14 |
| card titles  | 15 |
| page titles  | 17 on phones, 20 from 640px |
| stat values (600, tabular, leading-none) | 19 (17 on phones for long amounts) |
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
Textarea and three button styles (blue primary, outlined secondary, red-outlined danger).

## Demo conveniences

`HMS_DEMO_MODE=true` (compose passes it through) shows a pill at the bottom of every page:
"Demo mode, sample data, M-Pesa simulated". It is read per request through `/api/demo`, so
one image serves both. The Claims screen always carries its "Unverified" notice.
Demo data is created by `scripts/demo_seed/run.sh`.
