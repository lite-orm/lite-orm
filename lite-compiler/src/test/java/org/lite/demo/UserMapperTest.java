package org.lite.demo;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.io.SAXReader;
import org.junit.Test;

import java.util.List;

/**
 * @author qingbozhang
 * @since 2024/11/12 14:51
 */
@Slf4j
public class UserMapperTest {
    private UserMapper userMapper = new UserMapperImpl();

    @Test
    public void testMapper() {
        List<User> list = userMapper.selectByName("张三");
        log.info("{}", list);
    }

    @Test
    public void testGenerate() {
        SAXReader READER = new SAXReader();

    }
}
