-- V15: Add permission_overrides column to users table to support user-level granular permission overrides
ALTER TABLE users
    ADD COLUMN permission_overrides TEXT NULL AFTER status;
