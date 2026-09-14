package io.github.kervix.test;

import java.util.List;

/**
 * User and order fixture used by unsupported relationship-mapping tests.
 * 
 * @param id user identifier
 * @param name user name
 * @param email user email
 * @param age user age
 * @param orders user orders
 * 
 * @author kervix
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
