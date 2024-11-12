package org.lite.demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.List;

/**
 * @author qingbozhang
 * @since 创建于 2024/11/12 14:51
 */
@Slf4j
public class UserMapperTest {

    @Test
    public void testMapper() {
        UserMapper userMapper = new UserMapperImpl();
        List<User> list = userMapper.selectByName("张三");
        log.info("{}", list);
    }
}