package org.liteorm.test;

import java.util.List;

/**
 * 用户及其订单 - 用于测试一对多关联
 * 
 * @param id 用户ID
 * @param name 用户名
 * @param email 邮箱
 * @param age 年龄
 * @param orders 订单列表
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public record UserWithOrders(
    Long id,
    String name,
    String email,
    Integer age,
    List<Order> orders
) {
}
