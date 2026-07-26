ALTER TABLE game_items
    ADD COLUMN position INT NOT NULL DEFAULT 0;

CREATE INDEX idx_item_game_position
    ON game_items (game_id, position, id);
