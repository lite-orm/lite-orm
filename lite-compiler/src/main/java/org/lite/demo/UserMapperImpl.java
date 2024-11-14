package org.lite.demo;

import org.liteorm.handler.ChainContext;
import org.liteorm.handler.HandlerChain;
import org.liteorm.handler.demo.MockResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成代码案例
 *
 * @author qingbozhang
 * @since 2024/11/12 10:43
 */
public class UserMapperImpl implements UserMapper {

    @Override
    public List<User> selectByName(String name) {
        ChainContext<User> context = new ChainContext<>(User.class);
        HandlerChain chain = HandlerChain.getDefaultInstance(MockResource.getMySQLDataSource());
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("name", name);
            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("templates/UserMapper.xml");
            String sql = XmlParser.render(inputStream, "selectByName", params);
            assert inputStream != null;
            inputStream.close();
            context.setSql(sql);
            return chain.execute(context, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<User> selectByCondition(List<String> names) {
        return List.of();
    }
}
