-- LiteORM测试数据库初始化脚本
-- 用于DatabaseIntegrationTest

-- 删除已存在的表
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;

-- 创建users表
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    age INT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_status (status),
    INDEX idx_age (age)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 创建orders表（用于关联查询测试）
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(50) UNIQUE NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入测试数据
INSERT INTO users (name, email, age, status) VALUES
('张三', 'zhangsan@example.com', 25, 'ACTIVE'),
('李四', 'lisi@example.com', 30, 'ACTIVE'),
('王五', 'wangwu@example.com', 28, 'INACTIVE'),
('赵六', 'zhaoliu@example.com', 35, 'ACTIVE'),
('孙七', 'sunqi@example.com', 22, 'ACTIVE');

INSERT INTO orders (user_id, order_no, amount, status) VALUES
(1, 'ORD001', 100.00, 'COMPLETED'),
(1, 'ORD002', 200.00, 'PENDING'),
(2, 'ORD003', 150.00, 'COMPLETED'),
(3, 'ORD004', 300.00, 'CANCELLED'),
(4, 'ORD005', 250.00, 'COMPLETED');

-- 验证数据
SELECT 'users表记录数:' as info, COUNT(*) as count FROM users;
SELECT 'orders表记录数:' as info, COUNT(*) as count FROM orders;

