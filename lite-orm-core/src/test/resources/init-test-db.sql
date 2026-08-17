-- LiteORM test database initialization for DatabaseIntegrationTest.

-- Drop existing tables.
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;

-- Create the users table.
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

-- Create the orders table for relationship fixtures.
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

-- Insert test data.
INSERT INTO users (name, email, age, status) VALUES
('Alice', 'alice@example.com', 25, 'ACTIVE'),
('Bob', 'bob@example.com', 30, 'ACTIVE'),
('Carol', 'carol@example.com', 28, 'INACTIVE'),
('David', 'david@example.com', 35, 'ACTIVE'),
('Eve', 'eve@example.com', 22, 'ACTIVE');

INSERT INTO orders (user_id, order_no, amount, status) VALUES
(1, 'ORD001', 100.00, 'COMPLETED'),
(1, 'ORD002', 200.00, 'PENDING'),
(2, 'ORD003', 150.00, 'COMPLETED'),
(3, 'ORD004', 300.00, 'CANCELLED'),
(4, 'ORD005', 250.00, 'COMPLETED');

-- Verify fixture data.
SELECT 'users row count:' as info, COUNT(*) as count FROM users;
SELECT 'orders row count:' as info, COUNT(*) as count FROM orders;
