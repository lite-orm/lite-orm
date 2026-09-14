package io.github.kervix.test;

/**
 * Order fixture used by mapping tests.
 * 
 * @param id order identifier
 * @param userId user identifier
 * @param productName product name
 * @param amount order amount
 * @param status order status
 * 
 * @author kervix
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
