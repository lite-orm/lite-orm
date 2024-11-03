package org.liteorm.handler;

import com.fizzed.rocker.Rocker;
import com.fizzed.rocker.RockerModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

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
        RockerModel model = Rocker.template("user.rocker.raw")
                .bind("id", id);
        return model.render().toString();
    }

}