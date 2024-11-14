package org.liteorm.handler.demo;

import com.mysql.cj.jdbc.MysqlDataSource;

import javax.sql.DataSource;

/**
 * @author qingbozhang
 * @since 2024/11/12 15:35
 */
public class MockResource {
    private static volatile DataSource dataSource;
    //
    public static final String url = "jdbc:mysql://10.224.124.113:3306/test?characterEncoding=utf8&serverTimezone=GMT%2B8";

    public static DataSource getMySQLDataSource() {
        if (dataSource == null) {
            synchronized (MockResource.class) {
                if (dataSource == null) {
                    MysqlDataSource ds = new MysqlDataSource();
                    ds.setUrl(url);
                    ds.setUser("root");
                    ds.setPassword("123456");
                    dataSource = ds;
                }
            }
        }
        return dataSource;
    }
}
