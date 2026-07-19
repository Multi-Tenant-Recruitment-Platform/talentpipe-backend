-- ---------------------------------------------------------------------------
-- V6: Drop foreign key constraints on user-related tokens.
--
-- Allows candidates to utilize the same refresh_tokens, email_verification_tokens,
-- and password_reset_tokens tables by decoupling them from the strict constraint
-- pointing exclusively to users(id).
-- ---------------------------------------------------------------------------

ALTER TABLE refresh_tokens
    DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;

ALTER TABLE password_reset_tokens
    DROP CONSTRAINT IF EXISTS password_reset_tokens_user_id_fkey;

ALTER TABLE email_verification_tokens
    DROP CONSTRAINT IF EXISTS email_verification_tokens_user_id_fkey;
