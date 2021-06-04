DROP TABLE IF EXISTS `heads`;
CREATE TABLE `heads`  (
  `id` INT UNSIGNED AUTO_INCREMENT,
  `hash` varchar(255) NOT NULL ,
  `number` BIGINT NOT NULL,
  `parent_hash` varchar(255) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  index `idx_heads_created_at` (`created_at`),
  unique index `heads_number_key` (`number`),
  index `idx_heads_number` (`number`),
  index `idx_heads_time` (`time`)
) ENGINE = InnoDB default charset=utf8;


ALTER TABLE `txes` ADD `task_run_id` varchar(36) AFTER `id`;
ALTER TABLE `job_runs` MODIFY `result` text;
ALTER TABLE `task_runs` MODIFY `result` text;