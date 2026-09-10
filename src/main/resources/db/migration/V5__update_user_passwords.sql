-- Update all users to user-requested BCrypt hash
UPDATE users SET password_hash = '$2a$12$4Lfob5hG5Z.lDrlUr7PFV.y3lwyoNCh8NIodWBus9zpW2El4Qlo7O' WHERE deleted_at IS NULL;
