# Inventory Architecture

Owner module: `inventory` (structure + availability), with `pricing` computing prices. Requirement: no double bookings, ever, under concurrent requests and multi-instance deployment, using database-level controls and transactional reservation.

## 1. Inventory models

### 1.1 Hotel-style (counter model)
- A **unit type** (room type) has N physical **units** (rooms). Guests book a *type*, not a specific room; room assignment may happen later (vendor action) `[OD-19]`.
- Availability is one row per `(unit_type_id, stay_date)` in `inv_availability` with counters `total_units`, `held_units`, `booked_units`, `blocked_units` and the CHECK `held + booked + blocked ≤ total`.
- Rows are materialized for a rolling horizon (default 540 days, configurable) by a scheduled job and on-demand when a vendor edits structure; total is derived from active units minus out-of-service.

### 1.2 Private-style (exclusive model)
- Entire property, or individual units (e.g., a room in a home, a cottage). Each bookable unit is a single physical thing.
- Booking = a date range reservation. Mutual exclusion enforced by a **GiST exclusion constraint** on `(unit_id, stay_range)` for active reservations and blocks.
- Entire-property vs per-unit sale: a property sold as a whole and also in parts requires a parent/child blocking rule: reserving the whole blocks each child and vice versa. Modeled via `inv_unit_type.kind = ENTIRE_PROPERTY` with a derived composite that reserves all child units in one transaction (ordered locking, §4). `[OD-19]`

Both models share the API (`InventoryApi`): `check`, `hold`, `convertToAllocation`, `release`, `block`. The strategy is selected by unit type kind.

## 2. Reservation lifecycle

```
(none) ──hold──► HOLD(ACTIVE, expires_at) ──confirm──► ALLOCATION(ACTIVE)
                    │ expire / release                       │ cancel / complete
                    ▼                                        ▼
               RELEASED / EXPIRED                       RELEASED / CONSUMED
```

- `hold` is invoked inside the booking-creation transaction. If it fails (no inventory), the whole transaction rolls back and no booking row is created.
- Holds exist only for `PENDING_PAYMENT` bookings (instant bookings from creation; request bookings from vendor approval) and expire at `payment_due_at`. A `REQUESTED` booking holds nothing; approval performs the hold and fails with `booking.unavailable` if inventory was taken meanwhile. A scheduled job releases expired holds (`FOR UPDATE SKIP LOCKED`, batch, idempotent) and the booking module expires bookings in the same pass via events. Search/availability reads treat expired-but-unreleased holds as released (`expires_at > now()` filter) so a lagging job never blocks sale.
- `convertToAllocation` happens in the same transaction as `CONFIRMED`.
- Cancellation/expiry releases counters or marks the reservation `RELEASED` (exclusion constraint no longer applies).

## 3. Concurrency control

### 3.1 Counter model: conditional atomic update
For each date in `[check_in, check_out)`:

```sql
UPDATE inv_availability
   SET held_units = held_units + :qty
 WHERE unit_type_id = :type AND stay_date = :d
   AND total_units - held_units - booked_units - blocked_units >= :qty;
-- rows affected must equal number of nights, else rollback
```

The statement acquires a row lock, so concurrent transactions serialize per date row; the predicate makes the capacity test and the increment atomic. The CHECK constraint is the last line of defense. No application-level "read, then write" pattern is permitted.

### 3.2 Exclusive model: constraint-enforced
Insert the reservation row; the exclusion constraint rejects overlapping ranges for the same unit under any concurrency (including different instances). Constraint violation `23P01` is mapped to a domain `Unavailable` error.

### 3.3 Deadlock avoidance
Always touch rows in a deterministic order (ascending `unit_type_id`, then ascending date; for composites, ascending unit id). Transactions are short, with a lock timeout (`SET LOCAL lock_timeout`) mapped to a retryable "busy" error; the booking service retries at most a small bounded number of times.

### 3.4 Why not alternatives
- Redis/distributed locks: insufficient as a correctness mechanism (expiry, partitions); may be used only as an optimization to shed load, never to guarantee exclusivity.
- SERIALIZABLE isolation everywhere: unnecessary cost and retry complexity; the targeted constraints are simpler and provable.
- `SELECT ... FOR UPDATE` then update: acceptable but the conditional UPDATE is equivalent with fewer round-trips.

## 4. Atomic booking creation (critical transaction)

One transaction, in `booking.application`:
1. Validate idempotency key (insert-or-return).
2. Load quote; verify not expired and still valid (price rules version unchanged or policy of re-quote).
3. Verify caller entitlement and property bookable (catalog/subscription API calls — read-only checks).
4. `inventory.hold(...)` (3.1 or 3.2) — **instant bookings only**. A Request-to-Book creation performs an availability check and holds nothing; the same hold step runs inside the vendor-approval transaction, and fails with `booking.unavailable` if the dates were taken meanwhile (ADR-13).
5. Reserve promotion redemption (unique constraint prevents over-redemption).
6. Insert booking, status history, outbox events.
7. Commit. Payment initiation happens **after** commit.
Any failure rolls back everything, so holds never leak.

## 5. Calendar edits by vendors

- Blocking dates: allowed only where `total - held - booked ≥ blocked_delta`; otherwise the vendor is told which bookings conflict (cannot silently cancel guests).
- Reducing `total_units` (room out of service) follows the same rule.
- Opening dates: increases `total_units`/removes block rows.
- Bulk edits (date ranges, rate/availability grids) run in one transaction per unit type chunk with deterministic ordering.
- Min/max stay, closed-to-arrival/departure flags are availability attributes evaluated at quote and hold time `[OD-11]`.

## 6. Reads

- Search availability: precomputed `search` read model gives candidate properties; **final availability is always rechecked** against `inv_availability` at quote and again at hold.
- Calendar views for vendors read `inv_availability` directly. Cached for short TTL for public pages; never cached for the hold step.

## 7. External calendar sync (future) `[OD-12]`

Imported iCal events become `inv_block` rows with `source = ICAL` via a scheduled sync with conflict reports; outbound iCal export feeds are tokenized per unit. Sync lag creates overbooking risk against external channels; documented as a limitation and surfaced in the vendor UI.

## 8. Time zones and date semantics

Stay dates are property-local `date` values. A night belongs to `stay_date`; booking `[check_in, check_out)` is half-open. Check-in/out times come from the property. Cutoffs (e.g., same-day booking cutoff) are evaluated in the property's time zone.

## 9. Test requirements

- Concurrency test: N parallel booking attempts for the last unit/room ⇒ exactly the remaining capacity succeeds; counters never negative; no overlapping reservations (run against real PostgreSQL via Testcontainers; also multi-JVM-instance simulation).
- Hold expiry: expiry job and a concurrent payment-success race resolves deterministically (see [BOOKING_LIFECYCLE.md](../product/BOOKING_LIFECYCLE.md) §6.5).
- Deadlock test with overlapping multi-night, multi-type requests.
- Composite (entire property + units) exclusion tests.
- Property-based test: random sequences of hold/confirm/release/expire preserve invariants.
