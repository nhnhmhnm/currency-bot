-- Apply only after a verified backup and the earlier migrations. Do not run against cuunit automatically.
CREATE TABLE order_recovery_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    result VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_recovery_order_time (order_id, created_at)
);
