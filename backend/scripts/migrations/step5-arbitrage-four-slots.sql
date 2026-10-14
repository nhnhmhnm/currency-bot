-- Apply only to a backed-up MySQL schema that already has the step-5 one-slot constraint.
-- Stop order admission and workers, reconcile active reservations, then run this migration.
ALTER TABLE exchange_order
    ADD COLUMN arbitrage_slot_number INT NULL;

UPDATE exchange_order
SET arbitrage_slot_number = 1
WHERE arbitrage_slot_date IS NOT NULL;

ALTER TABLE exchange_order
    DROP INDEX uk_exchange_order_arbitrage_daily_slot,
    ADD UNIQUE KEY uk_exchange_order_arbitrage_daily_slot_v2
        (user_id, arbitrage_slot_date, arbitrage_slot_number);

ALTER TABLE exchange_reservation
    DROP COLUMN usd_equivalent_amount;

ALTER TABLE exchange_order
    DROP COLUMN usd_equivalent_amount;
