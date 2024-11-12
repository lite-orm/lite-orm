package org.lite.demo;

import java.util.List;

/**
 * @author qingbozhang
 * @since 创建于 2024/11/12 10:09
 */
public interface UserMapper {

    List<User> selectByName(String name);
}
