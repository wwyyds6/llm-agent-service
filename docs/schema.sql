-- LLM Agent Service 数据库初始化脚本
-- 用法：mysql -u root -p < docs/schema.sql

CREATE DATABASE IF NOT EXISTS llm_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE llm_agent;

-- 1. 会话表
CREATE TABLE IF NOT EXISTS conversation (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  title      VARCHAR(100) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 2. 消息表
CREATE TABLE IF NOT EXISTS message (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  conversation_id BIGINT      NOT NULL COMMENT '所属会话ID',
  role            VARCHAR(20) NOT NULL COMMENT '角色: user / assistant',
  content         TEXT        NOT NULL COMMENT '消息内容',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_conversation (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 3. 工具调用日志表
CREATE TABLE IF NOT EXISTS tool_call_log (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  message_id BIGINT      NULL COMMENT '所属消息ID',
  tool_name  VARCHAR(50) NOT NULL COMMENT '工具名',
  arguments  TEXT        NULL COMMENT '调用参数(JSON)',
  result     TEXT        NULL COMMENT '执行结果',
  cost_ms    INT         NOT NULL DEFAULT 0 COMMENT '执行耗时(毫秒)',
  success    TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否成功 1是0否',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_tool_name (tool_name),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用日志表';