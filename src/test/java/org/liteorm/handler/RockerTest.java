package org.liteorm.handler;

import com.fizzed.rocker.Rocker;
import com.fizzed.rocker.RockerModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 王洋洋
 * @since 创建于 2024/11/02 20:41
 */
@Slf4j
public class RockerTest {

    @Test
    public void testRender() throws Exception {
        String sql = generateSelectUserByIdQuery(123);
        log.info(sql);  // 输出: select * from users where id = 123;
    }

    public String generateSelectUserByIdQuery(Integer id) {
        Map<String, Object> param = new HashMap<>();
        param.put("id", id);
        List<String> items = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            items.add(String.valueOf(i));
        }

        RockerModel model = Rocker.template("user.rocker.raw")
                .bind("param", param)
                .bind("items", items);
        return model.render().toString();
    }

}