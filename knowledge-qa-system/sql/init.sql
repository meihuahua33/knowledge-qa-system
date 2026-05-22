-- ============================================
-- 企业知识库系统 - 数据库初始化脚本
-- MySQL 8.0 在容器首次启动时自动执行
-- ============================================

-- 确保字符集
ALTER DATABASE knowledge_base CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ==================== 预置分类数据 ====================
-- 使用 IGNORE 保证幂等: 重复执行不报错不插重复数据
INSERT IGNORE INTO categories (id, name, description, parent_id) VALUES
(1, '技术文档', '技术架构、API文档、开发规范等', NULL),
(2, '管理制度', '公司制度、流程规范、人事行政等', NULL),
(3, '产品手册', '产品说明、用户手册、操作指南等', NULL),
(4, '培训材料', '入职培训、技能培训、分享资料等', NULL);
