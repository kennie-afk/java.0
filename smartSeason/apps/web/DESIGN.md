# SmartSeason web: design language

This replaces the earlier "dense, 6px radius, 12px root" rules for this app. SmartSeason
keeps its own colour (farm green); everything else follows SmartRE's front end
(`java.0/smartRE-front`), kept as compact as its Command Center.

## What was taken from SmartRE

SmartRE's Command Center is the reference (see the portfolio image `smartre.jpg`), kept as small as it is:

- Fraunces for page and card titles only, Manrope for everything else, including every figure.
- Every light surface is pure white (#ffffff): canvas, sidebar, top bar, cards, table headers, inputs, modals.
  Areas are separated by 1px hairlines only. Tint appears only on the active nav item, badges, icon chips and hovers.
- Stat cards: 11.5px uppercase label, 19px Manrope 600 tabular value, 12px sub-line, a 16px icon in a 28px tinted
  chip top right, 16px padding. A coloured left edge appears only when the tone is warn or danger.
- Sidebar: ~248px, labelled rows, left accent bar and tinted background on the active item, collapsible groups.
- Status badges tinted, never solid. Empty states are short sentences. No motion at all (`transition: none`).

## Tokens (`src/app/globals.css`)

| Token | Value | Use |
|---|---|---|
| canvas / surface / rail | #ffffff | page, cards, sidebar, inputs, table headers |
| raised | #eef3ef | hover rows, neutral badge, secondary-button hover only |
| ink / muted / faint | #12201a / #4f5b53 / #6a766e | text; muted and faint pass 4.5:1 on white |
| line / line-strong | #e1e6e0 / #cdd5cc | card and input borders |
| accent / accent-deep / accent-soft | #15803d / #14532d / #e6f3ea | primary, active nav, login panel |
| good, warn, danger, info (+ soft) | green, amber, red, blue | badges, notices |

Radius: 8px controls, 8-12px cards.

## Type scale

Root 14px. Nothing below 11px.

| Class | Size | Use |
|---|---|---|
| text-2xs / text-xs | 11 / 11.5 | uppercase captions, table headers, stat labels |
| text-sm | 13 | table cells, nav, buttons, badges |
| text-base | 14 | body, form fields |
| text-lg | 15.5 | card titles (15px used in Card) |
| text-xl / 2xl | 17 / 20 | page titles (17 on phones, 20 from 640px) |
| stat value | 19 (16 for text values) | Manrope 600, tabular, leading-none |
| text-4xl | 30 | login headline |

## Layout

- Desktop (>=1024px): 248px labelled sidebar, content to 1680px, 32px side padding. Below 1024px: top bar and the
  same sidebar as a drawer.
- Sidebar: Workspace block (Overview, My work, Live work, Team, Crop advisor), then one collapsible group per service
  group the role may read. Groups come from `catalogue.generated.ts`, visibility from `lib/roles.ts`; the RBAC matrix
  (`tools/rbac.py`) remains the single source for both `@PreAuthorize` and the nav.
- Cards: 1px border, 16px padding, title bar with hairline divider. Tables flush, 13px text, rows ~40px, white header.
- Stat grid: auto-fit columns of at least 190px (2 columns on phones). Controls and buttons are at least 36px tall.

## Components (`src/components/ui.tsx`)

PageHeader, Card, Stat, Badge, EmptyState, Notice, Table, Meter, KeyValue, form controls and three button styles.

## Demo conveniences kept

The demo banner (pill, bottom centre) and the "Sign in as" picker are unchanged in behaviour.

## Time displays

Elapsed task time is the server's start/stop difference. Durations over 24h print as
"Over 24h" and produce no percent-of-estimate figure, and a task still running after 16h is
flagged as probably not stopped. The demo seed now starts tasks relative to the current time.
