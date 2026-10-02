# SmartSeason web: design language

This replaces the earlier "dense, 6px radius, 12px root" rules for this app. SmartSeason
keeps its own colour (farm green); everything else follows SmartRE's front end
(`java.0/smartRE-front`), scaled up so it reads comfortably.

## What was taken from SmartRE

SmartRE (`app/globals.css`, `tailwind.config.ts`, `components/ui`, `components/layout`):

- Fraunces for headings, Manrope for body and controls (Google Fonts), weights 400-800.
- White surfaces, hairline 1px borders, no heavy shadows, one saturated accent used for
  active state, primary buttons, links and focus rings only.
- Stat cards: small uppercase label, large tabular value, quiet sub-line, coloured left
  edge, icon chip at top right. Cards have a boundary, not a drop shadow.
- Sidebar: labelled rows with an icon, a left accent bar and a tinted background for the
  active item, collapsible groups with chevrons, nested children behind a hairline rule.
- Status badges: tinted background with matching text, never a solid fill.
- Empty states are short sentences in the card, not a blank page.
- No hover motion: hover only changes border or background colour.
- Content sits in a padded column beside the sidebar and uses the width that is there.

SmartRE is itself dense (13px root). The deliberate difference here is the type scale: the
user found 12px text unreadable, so SmartSeason's floor is higher.

## Tokens (`src/app/globals.css`)

| Token | Value | Use |
|---|---|---|
| canvas | #f6f8f5 | page background, faint green tint so white cards read as raised |
| surface | #ffffff | cards, sidebar, inputs |
| raised | #f1f4f0 | table header, chips, hover |
| ink / muted / faint | #12201a / #4f5b53 / #6a766e | text; muted and faint both pass 4.5:1 on white |
| line / line-strong | #e1e6e0 / #cdd5cc | card and input borders |
| accent / accent-deep / accent-soft | #15803d / #14532d / #e6f3ea | primary, active nav, login panel |
| good, warn, danger, info (+ soft) | green, amber, red, blue | badges, notices |

Radius: 8px cards and controls (`rounded-lg`/`xl` are both 8px), 12px ceiling.

## Type scale

| Class | Size / line | Use |
|---|---|---|
| text-2xs | 12 / 16 | never body: rare micro-captions |
| text-xs | 13 / 18 | uppercase labels, captions, hints |
| text-sm | 14 / 20 | table cells, nav, secondary body, buttons |
| text-base | 15 / 22 | body, form fields, subtitles |
| text-lg | 16.5 / 24 | card titles |
| text-xl | 19 / 26 | section headings |
| text-2xl | 24 / 30 | non-numeric stat values |
| text-3xl | 30 / 36 | page titles, numeric stat values |
| text-4xl | 36 / 42 | login headline |

Root is 15px. Headings and stat values use Fraunces; everything else Manrope. Numbers use
tabular figures.

## Layout

- Desktop (>=1024px): 272px labelled sidebar, content fills the rest up to 1680px, 40px
  side padding. Below 1024px: top bar with a menu button and the same sidebar as a drawer.
- Sidebar: "Workspace" block (Overview, My work, Live work, Team, Crop advisor), then one
  collapsible group per service group the role may read, then account and sign out.
  The group, service and entity lists come from `catalogue.generated.ts`, visibility from
  `lib/roles.ts`. The RBAC matrix (`tools/rbac.py`) remains the single source for both
  `@PreAuthorize` and the nav; the nav was only re-skinned.
- Cards: 1px border, 12px radius, 20px padding, optional title bar with hairline divider.
  Tables run flush inside a card, 12px/16px cell padding, shaded uppercase header.
- Stat grid: auto-fit columns of at least 210px (2 columns on phones).

## Components (`src/components/ui.tsx`)

PageHeader (eyebrow, title, subtitle, actions), Card, Stat, Badge (dot plus tone from a
status table), EmptyState, Notice, Table, Meter, KeyValue, form controls and three button
styles (green primary, outlined secondary, red-outlined danger).

## Demo conveniences kept

The demo banner (pill, bottom centre) and the "Sign in as" picker are unchanged in behaviour.

## Time displays

Elapsed task time is the server's start/stop difference. Durations over 24h print as
"Over 24h" and produce no percent-of-estimate figure, and a task still running after 16h is
flagged as probably not stopped. The demo seed now starts tasks relative to the current time.
