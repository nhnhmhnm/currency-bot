-- Apply after step 6 and a verified backup, before starting the step 7 workers.
-- This is a rebuildable read model; replay ORDER_COMPLETED outbox events to populate it.
CREATE TABLE completed_order_view (
    order_id BIGINT NOT NULL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    from_currency_id BIGINT NOT NULL,
    to_currency_id BIGINT NOT NULL,
    from_amount DECIMAL(24,8) NOT NULL,
    to_amount DECIMAL(24,8) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_completed_order_view_event (event_id),
    KEY idx_completed_order_user_time (user_id, completed_at)
);
