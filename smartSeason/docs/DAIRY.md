# Dairy module

Lives in farm-service (same RBAC row as the rest of farm: FARMER full, MANAGER write, AGRONOMIST read).

| Entity | Table | Notes |
|---|---|---|
| Cow | cows | one tag per farm (unique index) |
| MilkYield | milk_yields | one reading per cow, day and session; litres 0-100 |
| MilkDelivery | milk_deliveries | rejected <= delivered; price per litre is entered by the user, never defaulted |
| CowHealthEvent | cow_health_events | `withdrawalEndsOn` is the last day milk must be held back |
| BreedingEvent | breeding_events | `expectedCalvingOn` feeds the calving list |

Schema: `overlay/farm-service/.../V4__dairy.sql` (V1 and V3 are frozen; the table shapes match what
`tools/catalogue.py` generates). Rules the database holds itself: unique tag, unique reading, litres
bounds, rejected <= delivered, fat/SNF 0-100, withdrawal end not before the event.

`GET /api/farm/v1/dairy/summary?farmId=&from=&to=` (default last 30 days, max 366): litres recorded,
delivered, rejected, weighted fat, delivered value (accepted litres x the price you entered; unpriced
deliveries are counted, not guessed), cows under withdrawal today, milk recorded inside a withdrawal
window, expected calvings. "Today" is the Nairobi day.

Not built: payouts to farmers or co-op members, grading/quality-based pricing rules, collection routes,
per-member statements, SMS, offline capture, a feed or cost ledger, any statutory or dairy-board
reporting. Withdrawal dates are whatever the user types from the medicine label or vet; the system
does not know drug withdrawal periods.

Tests: `DairySummaryPostgresTest` (7, real Postgres, runs only when `DAIRY_TEST_PG_URL` is set, with
`DAIRY_TEST_PG_USER` / `DAIRY_TEST_PG_PASSWORD`; the summary uses DISTINCT ON / FILTER which H2 lacks).
