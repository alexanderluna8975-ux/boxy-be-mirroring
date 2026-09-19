-- Transfers: approve now folds in dispatch (no separate "ship" step), and receiving records the
-- ACTUAL quantity per line instead of blindly trusting what was shipped. See InventoryService
-- (approveTransfer/receiveTransfer) and the new TRANSFER_LOSS movement type.

ALTER TABLE stock_transfers
    ADD COLUMN receiving_notes VARCHAR(500) NULL AFTER notes;
