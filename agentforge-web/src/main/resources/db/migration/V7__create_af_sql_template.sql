CREATE TABLE `af_sql_template` (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `pattern` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `keywords` json NOT NULL,
                                   `template_sql` text COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `params` json DEFAULT NULL,
                                   `category` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'simple/aggregate/groupby/join/complex',
                                   `example_question` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '示例问题',
                                   `tables_used` json DEFAULT NULL COMMENT '涉及的表',
                                   `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板说明',
                                   `enabled` tinyint(1) DEFAULT '1',
                                   `hit_count` int DEFAULT '0',
                                   `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                   `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   KEY `idx_category` (`category`),
                                   KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SQL模板库';