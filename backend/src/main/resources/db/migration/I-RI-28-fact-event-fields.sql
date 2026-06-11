-- MOD-01 / RI-28: 事实事件流 4 字段 + INSERT-only 触发器
USE archive_db;

ALTER TABLE project_fact_event
    ADD COLUMN owner_id BIGINT COMMENT '责任人 user_id' AFTER confidence_level,
    ADD COLUMN due_date DATE COMMENT '跟进截止日' AFTER owner_id,
    ADD COLUMN resolved_at DATETIME COMMENT '处置完成时间' AFTER due_date,
    ADD COLUMN resolution_note TEXT COMMENT '处置备注' AFTER resolved_at;

CREATE INDEX idx_owner_due ON project_fact_event(owner_id, due_date);

DROP TRIGGER IF EXISTS trg_fact_event_immutable;
DROP TRIGGER IF EXISTS trg_fact_event_no_delete;

DELIMITER $$
CREATE TRIGGER trg_fact_event_immutable
BEFORE UPDATE ON project_fact_event
FOR EACH ROW
BEGIN
  IF (NEW.event_type <> OLD.event_type
      OR NEW.fact_value <> OLD.fact_value
      OR (NEW.evidence <> OLD.evidence OR (NEW.evidence IS NULL) <> (OLD.evidence IS NULL))
      OR (NEW.confidence <> OLD.confidence OR (NEW.confidence IS NULL) <> (OLD.confidence IS NULL))
      OR NEW.project_id <> OLD.project_id
      OR NEW.fact_type <> OLD.fact_type
      OR NEW.created_at <> OLD.created_at
      OR (NEW.created_by <> OLD.created_by OR (NEW.created_by IS NULL) <> (OLD.created_by IS NULL))
      OR (NEW.confidence_level <> OLD.confidence_level OR (NEW.confidence_level IS NULL) <> (OLD.confidence_level IS NULL))) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'project_fact_event is INSERT-only (only owner_id/due_date/resolved_at/resolution_note may be updated)';
  END IF;
END$$

CREATE TRIGGER trg_fact_event_no_delete
BEFORE DELETE ON project_fact_event
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'project_fact_event is INSERT-only (DELETE forbidden)';
END$$
DELIMITER ;
