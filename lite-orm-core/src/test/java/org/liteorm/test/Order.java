package org.liteorm.test;

/**
 * 订单实体类 - 用于测试复杂关联查询
 * 
 * @param id 订单ID
 * @param userId 用户ID
 * @param productName 产品名称
 * @param amount 金额
 * @param status 状态
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public record Order(
    Long id,
    Long userId,
    String productName,
    Double amount,
    String status
) {
}
