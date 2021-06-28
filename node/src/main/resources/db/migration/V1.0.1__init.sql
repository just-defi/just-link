DROP TABLE IF EXISTS `job_specs`;
CREATE TABLE `job_specs`  (
  `id` varchar(36) NOT NULL,
  `start_at` timestamp NULL,
  `end_at` timestamp NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL,
  `min_payment` BIGINT,
  `params` text,
  PRIMARY KEY (`id`),
  index `idx_job_specs_created_at` (`created_at`),
  index `idx_job_specs_start_at` (`start_at`),
  index `idx_job_specs_end_at` (`end_at`),
  index `idx_job_specs_updated_at` (`updated_at`),
  index `idx_job_specs_deleted_at` (`deleted_at`)
) ENGINE = InnoDB default charset=utf8;

DROP TABLE IF EXISTS `initiators`;
CREATE TABLE `initiators`  (
  `id` INT UNSIGNED AUTO_INCREMENT,
  `job_spec_id` varchar(36) NOT NULL,
  `type` varchar(64) NOT NULL,
  `schedule` text,
  `time` timestamp NULL,
  `ran` tinyint(1),
  `address` varchar(128),
  `requesters` text,
  `name` varchar(255),
  `params` text,
  `from_block` int(11),
  `to_block` int(11),
  `topics` text,
  `request_data` text,
  `feeds` text,
  `threshold` double,
  `precision` smallint,
  `polling_interval` int,
  `absolute_threshold` double,
  `poll_timer` text,
  `idle_timer` text,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL,
  PRIMARY KEY (`id`),
  index `idx_initiators_created_at` (`created_at`),
  index `idx_initiators_deleted_at` (`deleted_at`),
  index `idx_initiators_address` (`address`),
  index `idx_initiators_job_spec_id` (`job_spec_id`),
  index `idx_initiators_type` (`type`),
  index `idx_initiators_updated_at` (`updated_at`)
) ENGINE = InnoDB default charset=utf8;

DROP TABLE IF EXISTS `task_specs`;
CREATE TABLE `task_specs`  (
  `id` INT UNSIGNED AUTO_INCREMENT,
  `job_spec_id` varchar(36) NOT NULL,
  `confirmations` bigint,
  `type` varchar(255) NOT NULL,
  `params` text,
  `level` INT NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL,
  PRIMARY KEY (`id`),
  index `idx_task_specs_created_at` (`created_at`),
  index `idx_task_specs_deleted_at` (`deleted_at`),
  index `idx_task_specs_job_spec_id` (`job_spec_id`),
  index `idx_task_specs_type` (`type`),
  index `idx_task_specs_updated_at` (`updated_at`)
) ENGINE = InnoDB default charset=utf8;

DROP TABLE IF EXISTS `job_runs`;
CREATE TABLE `job_runs`  (
  `id` varchar(36) NOT NULL,
  `job_spec_id` varchar(36) NOT NULL,
  `result` text,
  `error` varchar(255),
  `request_id` varchar(127),
  `params` text,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0:init 1:processing 2:complete 3:error',
  `initiator_id` bigint,
  `creation_height` bigint,
  `observed_height` bigint,
  `payment` bigint,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` timestamp NULL,
  `deleted_at` timestamp NULL,
  PRIMARY KEY (`id`),
  index `idx_job_runs_created_at` (`created_at`),
  index `idx_job_runs_deleted_at` (`deleted_at`),
  index `idx_job_runs_finished_at` (`finished_at`),
  index `idx_job_runs_initiator_id` (`initiator_id`),
  index `idx_job_runs_job_spec_id` (`job_spec_id`),
  index `idx_job_runs_run_request_id` (`request_id`),
  index `idx_job_runs_status` (`status`),
  index `idx_job_runs_updated_at` (`updated_at`)
) ENGINE = InnoDB default charset=utf8;

DROP TABLE IF EXISTS `task_runs`;
CREATE TABLE `task_runs`  (
  `id` varchar(36) NOT NULL,
  `job_run_id` varchar(36) NOT NULL,
  `result` text,
  `error` varchar(255),
  `task_spec_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0:init 1:processing 2:complete 3:error',
  `minimum_confirmations` bigint,
  `confirmations` bigint,
  `level` INT NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  index `idx_task_runs_created_at` (`created_at`),
  index `idx_task_runs_job_run_id` (`job_run_id`),
  index `idx_task_runs_task_spec_id` (`task_spec_id`),
  index `idx_task_runs_status` (`status`),
  index `idx_task_runs_updated_at` (`updated_at`)
) ENGINE = InnoDB default charset=utf8;

DROP TABLE IF EXISTS `txes`;
CREATE TABLE `txes`  (
  `id` INT UNSIGNED AUTO_INCREMENT,
  `task_run_id` varchar(36),
  `surrogate_id` varchar(255) ,
  `from` varchar(127) NOT NULL,
  `to` varchar(127) NOT NULL,
  `data` text NOT NULL,
  `nonce` bigint,
  `value` bigint NOT NULL,
  `gas_limit` bigint,
  `hash` varchar(127) NOT NULL,
  `gas_price` bigint,
  `confirmed` tinyint DEFAULT 0 COMMENT '0:init 101:unstarted 102:inprogress 103:fatalerror 104:outofenergy 105:confirmed',
  `sent_at` bigint NOT NULL,
  `signed_raw_tx` varchar(255) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  index `idx_txes_created_at` (`created_at`),
  unique index `txes_surrogate_id_key` (`surrogate_id`),
  index `idx_txes_task_run_id_key` (`task_run_id`),
  index `idx_txes_from` (`from`),
  index `idx_txes_hash` (`hash`),
  index `idx_confirmed` (`confirmed`),
  index `idx_sent_at` (`sent_at`),
  index `idx_txes_updated_at` (`updated_at`)
) ENGINE = InnoDB default charset=utf8;

DROP TABLE IF EXISTS `heads`;
CREATE TABLE `heads`  (
  `id` INT UNSIGNED AUTO_INCREMENT,
  `address` varchar(128) NOT NULL,
  `hash` varchar(255) NOT NULL ,
  `number` BIGINT NOT NULL,
  `parent_hash` varchar(255) NOT NULL,
  `block_timestamp` BIGINT NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  index `idx_heads_created_at` (`created_at`),
  unique index `heads_address_key` (`address`),
  index `idx_heads_address` (`address`)
) ENGINE = InnoDB default charset=utf8;