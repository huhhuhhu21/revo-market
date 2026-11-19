-- C2C marketplace / AI-assisted listing - Database schema V1.1
-- Basis: SRS V1.1 (17/11/2026), appendix A (16 tables), latest ERD.
-- Target: MySQL 8.0.16+, InnoDB, utf8mb4. All DATETIME(3) stored in UTC.
-- No DROP TABLE / DROP DATABASE. Execute once on a new database.
-- Technical GENERATED columns only enforce conditional UNIQUE constraints;
-- they do not represent new business entities or changes to the SRS.

CREATE DATABASE IF NOT EXISTS c2c_marketplace
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_as_ci;
USE c2c_marketplace;

-- 01. Accounts. No login-attempt counter columns: per user's latest decision.
CREATE TABLE users (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role              VARCHAR(16) NOT NULL DEFAULT 'USER',
    full_name         VARCHAR(120) NOT NULL,
    email             VARCHAR(254) NULL,
    phone             VARCHAR(20) NULL,
    password_hash     VARCHAR(255) NOT NULL,
    avatar_url        VARCHAR(2048) NULL,
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    address_line      VARCHAR(255) NULL,
    province          VARCHAR(100) NULL,
    ward              VARCHAR(100) NULL,
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                      ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_phone (phone),
    KEY idx_users_status_role (status, role),
    CONSTRAINT chk_users_contact CHECK (
        (email IS NOT NULL OR phone IS NOT NULL)
        AND (email IS NULL OR CHAR_LENGTH(TRIM(email)) > 0)
        AND (phone IS NULL OR CHAR_LENGTH(TRIM(phone)) > 0)
    ),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK
        (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'RESTRICTED'))
) ENGINE = InnoDB;

-- 02. Flat categories: deactivation never deletes old category references.
CREATE TABLE categories (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(120) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_categories_name (name),
    CONSTRAINT chk_categories_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB;

-- 03. Listings. ADMIN hiding is independent of listing business status.
CREATE TABLE product_listings (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id           BIGINT UNSIGNED NOT NULL,
    category_id         BIGINT UNSIGNED NOT NULL,
    title               VARCHAR(100) NOT NULL,
    description         VARCHAR(2000) NOT NULL,
    listed_price        BIGINT UNSIGNED NOT NULL COMMENT 'Whole-number VND; informational price',
    item_condition      VARCHAR(16) NOT NULL,
    province            VARCHAR(100) NOT NULL,
    ward                VARCHAR(100) NOT NULL,
    status              VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    hidden_reason_type  VARCHAR(16) NULL,
    is_admin_hidden     BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_listings_seller (seller_id, created_at),
    KEY idx_listings_public (status, is_admin_hidden, is_deleted, created_at),
    KEY idx_listings_filter (category_id, status, province, listed_price),
    CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_listings_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_listings_price CHECK (listed_price > 0),
    CONSTRAINT chk_listings_condition CHECK (item_condition IN ('NEW', 'USED')),
    CONSTRAINT chk_listings_status CHECK
        (status IN ('AVAILABLE', 'IN_TRANSACTION', 'HIDDEN', 'SOLD')),
    CONSTRAINT chk_listings_hide_reason CHECK (
        (status = 'HIDDEN' AND hidden_reason_type IS NOT NULL
         AND hidden_reason_type IN ('SELLER', 'RETURNED'))
        OR (status <> 'HIDDEN' AND hidden_reason_type IS NULL)
    )
) ENGINE = InnoDB;

-- 04. Each listing must have 1-8 images at publication time.
-- Unique positions 1..8 enforce an upper bound; minimum 1 is a service/transaction rule.
CREATE TABLE listing_images (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    listing_id  BIGINT UNSIGNED NOT NULL,
    image_url   VARCHAR(2048) NOT NULL,
    sort_order  TINYINT UNSIGNED NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_images_listing_order (listing_id, sort_order),
    CONSTRAINT fk_images_listing FOREIGN KEY (listing_id) REFERENCES product_listings (id),
    CONSTRAINT chk_images_order CHECK (sort_order BETWEEN 1 AND 8)
) ENGINE = InnoDB;

-- 05. ADMIN moderation audit. Never delete decisions.
CREATE TABLE listing_moderations (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    listing_id  BIGINT UNSIGNED NOT NULL,
    admin_id    BIGINT UNSIGNED NOT NULL,
    action      VARCHAR(16) NOT NULL,
    reason      TEXT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_moderations_listing (listing_id, created_at),
    KEY idx_moderations_admin (admin_id),
    CONSTRAINT fk_moderations_listing FOREIGN KEY (listing_id) REFERENCES product_listings (id),
    CONSTRAINT fk_moderations_admin FOREIGN KEY (admin_id) REFERENCES users (id),
    CONSTRAINT chk_moderations_action CHECK (action IN ('HIDE', 'UNHIDE')),
    CONSTRAINT chk_moderations_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
) ENGINE = InnoDB;

-- 06. Purchase requests. pending_buyer_id is a technical generated column
-- allowing historical requests while preventing duplicate PENDING requests.
CREATE TABLE purchase_requests (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    listing_id            BIGINT UNSIGNED NOT NULL,
    buyer_id              BIGINT UNSIGNED NOT NULL,
    offered_price         BIGINT UNSIGNED NOT NULL,
    recipient_name        VARCHAR(120) NOT NULL,
    recipient_phone       VARCHAR(20) NOT NULL,
    delivery_address_line VARCHAR(255) NOT NULL,
    delivery_province     VARCHAR(100) NOT NULL,
    delivery_ward         VARCHAR(100) NOT NULL,
    note                  VARCHAR(2000) NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    responded_at          DATETIME(3) NULL,
    expires_at            DATETIME(3) NOT NULL,
    pending_buyer_id      BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN buyer_id ELSE NULL END) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_request_pending_buyer (listing_id, pending_buyer_id),
    KEY idx_requests_listing_status (listing_id, status, created_at),
    KEY idx_requests_buyer_status (buyer_id, status, created_at),
    KEY idx_requests_expiry (status, expires_at),
    CONSTRAINT fk_requests_listing FOREIGN KEY (listing_id) REFERENCES product_listings (id),
    CONSTRAINT fk_requests_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT chk_requests_price CHECK (offered_price > 0),
    CONSTRAINT chk_requests_status CHECK
        (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'ENDED')),
    CONSTRAINT chk_requests_expiry CHECK (expires_at > created_at)
) ENGINE = InnoDB;

-- 07. Orders: immutable buyer/seller/amount/delivery copy once created.
-- active_listing_id enforces at most one active order for a listing;
-- the SOLD/AVAILABLE business rule is additionally enforced under row locks.
CREATE TABLE orders (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    purchase_request_id   BIGINT UNSIGNED NOT NULL,
    listing_id            BIGINT UNSIGNED NOT NULL,
    buyer_id              BIGINT UNSIGNED NOT NULL,
    seller_id             BIGINT UNSIGNED NOT NULL,
    transaction_price     BIGINT UNSIGNED NOT NULL,
    shipping_fee          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    recipient_name        VARCHAR(120) NOT NULL,
    delivery_ward         VARCHAR(100) NOT NULL,
    delivery_province     VARCHAR(100) NOT NULL,
    delivery_address_line VARCHAR(255) NOT NULL,
    recipient_phone       VARCHAR(20) NOT NULL,
    status                VARCHAR(24) NOT NULL DEFAULT 'PENDING_PICKUP',
    cod_status            VARCHAR(24) NOT NULL DEFAULT 'NOT_COLLECTED',
    cod_amount            BIGINT UNSIGNED NOT NULL,
    picked_up_at          DATETIME(3) NULL COMMENT 'Actual pickup time',
    delivered_at          DATETIME(3) NULL COMMENT 'Actual delivery time',
    completed_at          DATETIME(3) NULL,
    canceled_by           BIGINT UNSIGNED NULL COMMENT 'NULL = canceled automatically by the system',
    cancel_reason         TEXT NULL,
    canceled_at           DATETIME(3) NULL,
    cod_collected_at      DATETIME(3) NULL,
    cod_settled_at        DATETIME(3) NULL,
    cod_refunded_at       DATETIME(3) NULL,
    shipping_history      JSON NOT NULL DEFAULT (JSON_ARRAY())
                          COMMENT 'Append events: event_type, actual_at, recorded_at, actor_type, admin_id',
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                          ON UPDATE CURRENT_TIMESTAMP(3),
    active_listing_id     BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING_PICKUP', 'IN_TRANSIT', 'DELIVERED')
              THEN listing_id ELSE NULL END) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_orders_request (purchase_request_id),
    UNIQUE KEY uq_orders_active_listing (active_listing_id),
    UNIQUE KEY uq_orders_id_buyer (id, buyer_id),
    KEY idx_orders_listing_created (listing_id, created_at),
    KEY idx_orders_buyer_created (buyer_id, created_at),
    KEY idx_orders_seller_created (seller_id, created_at),
    KEY idx_orders_status (status, created_at),
    KEY idx_orders_cod (cod_status, delivered_at),
    KEY idx_orders_canceled_by (canceled_by),
    CONSTRAINT fk_orders_request FOREIGN KEY (purchase_request_id)
        REFERENCES purchase_requests (id),
    CONSTRAINT fk_orders_listing FOREIGN KEY (listing_id)
        REFERENCES product_listings (id),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_orders_cancel_actor FOREIGN KEY (canceled_by) REFERENCES users (id),
    CONSTRAINT chk_orders_price CHECK (transaction_price > 0),
    CONSTRAINT chk_orders_shipping_fee CHECK (shipping_fee = 0),
    CONSTRAINT chk_orders_cod_amount CHECK (cod_amount = transaction_price + shipping_fee),
    CONSTRAINT chk_orders_distinct_users CHECK (buyer_id <> seller_id),
    CONSTRAINT chk_orders_status CHECK (status IN
        ('PENDING_PICKUP', 'IN_TRANSIT', 'DELIVERED', 'COMPLETED', 'CANCELED', 'RETURNED')),
    CONSTRAINT chk_orders_cod_status CHECK (cod_status IN
        ('NOT_COLLECTED', 'HELD', 'SETTLED', 'REFUNDED', 'NO_COLLECTION')),
    CONSTRAINT chk_orders_no_double_cod CHECK
        (NOT (cod_settled_at IS NOT NULL AND cod_refunded_at IS NOT NULL)),
    CONSTRAINT chk_orders_cod_events CHECK (
        (cod_status NOT IN ('HELD', 'SETTLED', 'REFUNDED') OR cod_collected_at IS NOT NULL)
        AND (cod_status <> 'SETTLED' OR cod_settled_at IS NOT NULL)
        AND (cod_status <> 'REFUNDED' OR cod_refunded_at IS NOT NULL)
    ),
    CONSTRAINT chk_orders_returned_cod CHECK (status <> 'RETURNED' OR cod_status = 'REFUNDED'),
    CONSTRAINT chk_orders_canceled_cod CHECK (status <> 'CANCELED' OR cod_status = 'NO_COLLECTION'),
    CONSTRAINT chk_orders_canceled_at CHECK (status <> 'CANCELED' OR canceled_at IS NOT NULL),
    CONSTRAINT chk_orders_date_order CHECK
        ((picked_up_at IS NULL OR delivered_at IS NULL OR picked_up_at <= delivered_at)
         AND (delivered_at IS NULL OR completed_at IS NULL OR delivered_at <= completed_at))
) ENGINE = InnoDB;

-- 08. One return request per order, including rejected/expired requests.
-- Composite FK ensures the buyer_id copy equals the buyer of the order.
CREATE TABLE return_requests (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id         BIGINT UNSIGNED NOT NULL,
    buyer_id         BIGINT UNSIGNED NOT NULL,
    reason           TEXT NOT NULL,
    evidence_images  JSON NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    processed_by     BIGINT UNSIGNED NULL,
    admin_reason     TEXT NULL,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at     DATETIME(3) NULL,
    return_deadline  DATETIME(3) NULL,
    handed_over_at   DATETIME(3) NULL COMMENT 'Actual handover from buyer',
    returned_at      DATETIME(3) NULL COMMENT 'Actual receipt by seller',
    PRIMARY KEY (id),
    UNIQUE KEY uq_returns_order (order_id),
    KEY idx_returns_buyer (buyer_id, created_at),
    KEY idx_returns_status (status, created_at),
    KEY idx_returns_processed_by (processed_by),
    CONSTRAINT fk_returns_order_buyer FOREIGN KEY (order_id, buyer_id)
        REFERENCES orders (id, buyer_id),
    CONSTRAINT fk_returns_processed_by FOREIGN KEY (processed_by) REFERENCES users (id),
    CONSTRAINT chk_returns_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_returns_images CHECK
        (JSON_TYPE(evidence_images) = 'ARRAY' AND JSON_LENGTH(evidence_images) BETWEEN 1 AND 5),
    CONSTRAINT chk_returns_status CHECK (status IN
        ('PENDING', 'AWAITING_HANDOVER', 'RETURNING', 'RETURNED', 'REJECTED', 'HANDOVER_EXPIRED')),
    CONSTRAINT chk_returns_decision CHECK (
        (status <> 'REJECTED' OR
         (processed_by IS NOT NULL AND processed_at IS NOT NULL AND
          admin_reason IS NOT NULL AND CHAR_LENGTH(TRIM(admin_reason)) > 0))
        AND (status <> 'AWAITING_HANDOVER' OR
         (processed_by IS NOT NULL AND processed_at IS NOT NULL AND return_deadline IS NOT NULL))
        AND (status <> 'RETURNING' OR handed_over_at IS NOT NULL)
        AND (status <> 'RETURNED' OR returned_at IS NOT NULL)
    )
) ENGINE = InnoDB;

-- 09. Operational exceptions, created manually by ADMIN or by SYSTEM.
CREATE TABLE order_exceptions (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id           BIGINT UNSIGNED NOT NULL,
    exception_type     VARCHAR(24) NOT NULL,
    description        TEXT NOT NULL,
    status             VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    resolution_action  VARCHAR(64) NULL,
    resolution_reason  TEXT NULL,
    created_by         BIGINT UNSIGNED NULL,
    resolved_by        BIGINT UNSIGNED NULL,
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at        DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_exceptions_order (order_id, created_at),
    KEY idx_exceptions_status (status, created_at),
    KEY idx_exceptions_created_by (created_by),
    KEY idx_exceptions_resolved_by (resolved_by),
    CONSTRAINT fk_exceptions_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_exceptions_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_exceptions_resolver FOREIGN KEY (resolved_by) REFERENCES users (id),
    CONSTRAINT chk_exceptions_type CHECK (exception_type IN ('DELIVERY', 'RETURN', 'COD_ORDER')),
    CONSTRAINT chk_exceptions_status CHECK (status IN ('PENDING', 'RESOLVED')),
    CONSTRAINT chk_exceptions_resolution CHECK (
        (status = 'PENDING' AND resolution_action IS NULL AND
         resolution_reason IS NULL AND resolved_by IS NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolution_action IS NOT NULL AND
            resolution_reason IS NOT NULL AND
            CHAR_LENGTH(TRIM(resolution_reason)) > 0 AND
            resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
    )
) ENGINE = InnoDB;

-- 10. One conversation per unordered pair of users; ordered ascending.
CREATE TABLE conversations (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user1_id    BIGINT UNSIGNED NOT NULL,
    user2_id    BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_conversations_pair (user1_id, user2_id),
    KEY idx_conversations_user2 (user2_id, updated_at),
    KEY idx_conversations_user1_recent (user1_id, updated_at),
    CONSTRAINT fk_conversations_user1 FOREIGN KEY (user1_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_user2 FOREIGN KEY (user2_id) REFERENCES users (id),
    CONSTRAINT chk_conversations_sorted CHECK (user1_id < user2_id)
) ENGINE = InnoDB;

-- 11. Chat message idempotency: Flutter supplies stable UUID on retries.
CREATE TABLE messages (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id    BIGINT UNSIGNED NOT NULL,
    sender_id          BIGINT UNSIGNED NOT NULL,
    text_content       VARCHAR(2000) NULL,
    image_urls         JSON NOT NULL DEFAULT (JSON_ARRAY()),
    listing_id         BIGINT UNSIGNED NULL,
    client_message_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    sent_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    seen_at            DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_messages_sender_client (sender_id, client_message_id),
    KEY idx_messages_conversation (conversation_id, sent_at, id),
    KEY idx_messages_listing (listing_id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT fk_messages_listing FOREIGN KEY (listing_id) REFERENCES product_listings (id),
    CONSTRAINT chk_messages_images CHECK
        (JSON_TYPE(image_urls) = 'ARRAY' AND JSON_LENGTH(image_urls) <= 5),
    CONSTRAINT chk_messages_payload CHECK
        ((text_content IS NOT NULL AND CHAR_LENGTH(TRIM(text_content)) > 0)
         OR JSON_LENGTH(image_urls) > 0 OR listing_id IS NOT NULL),
    CONSTRAINT chk_messages_seen_at CHECK (seen_at IS NULL OR seen_at >= sent_at)
) ENGINE = InnoDB;

-- 12. USER_REPORT and ORDER_COMPLAINT share one table, not one permission policy.
-- Generated keys implement "one pending" separately for the two report types.
CREATE TABLE reports (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_id                 BIGINT UNSIGNED NOT NULL,
    reported_user_id            BIGINT UNSIGNED NULL,
    order_id                    BIGINT UNSIGNED NULL,
    type                        VARCHAR(24) NOT NULL,
    content                     TEXT NULL,
    evidence_images             JSON NOT NULL DEFAULT (JSON_ARRAY()),
    status                      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    resolution_action           VARCHAR(64) NULL,
    resolution_reason           TEXT NULL,
    resolved_by                 BIGINT UNSIGNED NULL,
    created_at                  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at                 DATETIME(3) NULL,
    pending_reported_user_id    BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN type = 'USER_REPORT' AND status = 'PENDING'
              THEN reported_user_id ELSE NULL END) VIRTUAL,
    pending_complaint_order_id  BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN type = 'ORDER_COMPLAINT' AND status = 'PENDING'
              THEN order_id ELSE NULL END) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reports_open_user (reporter_id, pending_reported_user_id),
    UNIQUE KEY uq_reports_open_order (reporter_id, pending_complaint_order_id),
    KEY idx_reports_target (reported_user_id, status),
    KEY idx_reports_order (order_id, created_at),
    KEY idx_reports_queue (type, status, created_at),
    KEY idx_reports_resolved_by (resolved_by),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_target FOREIGN KEY (reported_user_id) REFERENCES users (id),
    CONSTRAINT fk_reports_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_reports_resolver FOREIGN KEY (resolved_by) REFERENCES users (id),
    CONSTRAINT chk_reports_type CHECK (type IN ('USER_REPORT', 'ORDER_COMPLAINT')),
    CONSTRAINT chk_reports_target_type CHECK (
        (type = 'USER_REPORT' AND reported_user_id IS NOT NULL
         AND order_id IS NULL AND reporter_id <> reported_user_id)
        OR (type = 'ORDER_COMPLAINT' AND order_id IS NOT NULL)
    ),
    CONSTRAINT chk_reports_content CHECK (
        (type = 'USER_REPORT' AND
          ((content IS NOT NULL AND CHAR_LENGTH(TRIM(content)) > 0)
           OR JSON_LENGTH(evidence_images) > 0))
        OR (type = 'ORDER_COMPLAINT' AND
             content IS NOT NULL AND CHAR_LENGTH(TRIM(content)) > 0)
    ),
    CONSTRAINT chk_reports_images CHECK
        (JSON_TYPE(evidence_images) = 'ARRAY' AND JSON_LENGTH(evidence_images) <= 5),
    CONSTRAINT chk_reports_status CHECK (status IN ('PENDING', 'RESOLVED')),
    CONSTRAINT chk_reports_resolution CHECK (
        (status = 'PENDING' AND resolution_action IS NULL AND
         resolution_reason IS NULL AND resolved_by IS NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolution_action IS NOT NULL AND
            resolution_reason IS NOT NULL AND CHAR_LENGTH(TRIM(resolution_reason)) > 0 AND
            resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
    )
) ENGINE = InnoDB;

-- 13. Restriction history; a report is optional (direct ADMIN action).
-- active_user_id enforces one still-effective restriction per user.
CREATE TABLE account_restrictions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    report_id       BIGINT UNSIGNED NULL,
    reason          TEXT NOT NULL,
    restricted_by   BIGINT UNSIGNED NOT NULL,
    restricted_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    released_by     BIGINT UNSIGNED NULL,
    released_at     DATETIME(3) NULL,
    release_reason  TEXT NULL,
    active_user_id  BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN released_at IS NULL THEN user_id ELSE NULL END) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_restrictions_report (report_id),
    UNIQUE KEY uq_restrictions_active_user (active_user_id),
    KEY idx_restrictions_user (user_id, restricted_at),
    KEY idx_restrictions_restricted_by (restricted_by),
    KEY idx_restrictions_released_by (released_by),
    CONSTRAINT fk_restrictions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_restrictions_report FOREIGN KEY (report_id) REFERENCES reports (id),
    CONSTRAINT fk_restrictions_restrictor FOREIGN KEY (restricted_by) REFERENCES users (id),
    CONSTRAINT fk_restrictions_releaser FOREIGN KEY (released_by) REFERENCES users (id),
    CONSTRAINT chk_restrictions_lifecycle CHECK (
        (released_at IS NULL AND released_by IS NULL AND release_reason IS NULL)
        OR (released_at IS NOT NULL AND released_by IS NOT NULL AND
            release_reason IS NOT NULL AND CHAR_LENGTH(TRIM(release_reason)) > 0
            AND released_at >= restricted_at)
    )
) ENGINE = InnoDB;

-- 14. In-app notifications. ref_type/ref_id is polymorphic, validated by backend.
CREATE TABLE notifications (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    type        VARCHAR(48) NOT NULL,
    content     VARCHAR(2000) NOT NULL,
    ref_id      BIGINT UNSIGNED NULL,
    ref_type    VARCHAR(32) NULL,
    read_at     DATETIME(3) NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_notifications_user_recent (user_id, created_at),
    KEY idx_notifications_user_unread (user_id, read_at, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_notifications_ref CHECK
        ((ref_id IS NULL AND ref_type IS NULL)
         OR (ref_id IS NOT NULL AND ref_type IS NOT NULL))
) ENGINE = InnoDB;

-- 15. OTP. target_otp = destination email/phone (including NEW contact during change).
-- The code itself is stored only as a hash, never plaintext.
CREATE TABLE otp_codes (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    target_otp       VARCHAR(254) NOT NULL,
    purpose          VARCHAR(32) NOT NULL,
    code_hash        VARCHAR(255) NOT NULL,
    expires_at       DATETIME(3) NOT NULL,
    failed_attempts  TINYINT UNSIGNED NOT NULL DEFAULT 0,
    last_sent_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    consumed_at      DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_otp_user_purpose (user_id, purpose, expires_at),
    KEY idx_otp_expiry (expires_at),
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_otp_attempts CHECK (failed_attempts <= 5),
    CONSTRAINT chk_otp_target CHECK (CHAR_LENGTH(TRIM(target_otp)) > 0)
) ENGINE = InnoDB;

-- 16. Refresh tokens: only hashed tokens, with per-device revoke and expiry.
CREATE TABLE refresh_tokens (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL,
    token_hash   VARCHAR(255) NOT NULL,
    expires_at   DATETIME(3) NOT NULL,
    revoked_at   DATETIME(3) NULL,
    device_info  VARCHAR(500) NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id, expires_at),
    KEY idx_refresh_expiry (expires_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_refresh_expiry CHECK (expires_at > created_at)
) ENGINE = InnoDB;

-- Explicitly OUTSIDE schema scope:
--  * no additional login-failure columns in users (per latest user decision);
--  * no appeals/snapshots/separate shipping or COD tables;
--  * no last_message_at (optional only in SRS).
-- See accompanying README for transaction requirements and design decisions.
