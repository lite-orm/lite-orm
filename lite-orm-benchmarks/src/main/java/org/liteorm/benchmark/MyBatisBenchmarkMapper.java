package org.liteorm.benchmark;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.cursor.Cursor;

import java.util.List;

public interface MyBatisBenchmarkMapper {

    @Select("SELECT name FROM benchmark_users WHERE id = #{id}")
    String scalar(long id);

    @Select("SELECT id, name, age FROM benchmark_users WHERE id = #{id}")
    BenchmarkRecord record(long id);

    @Select("SELECT id, name, age FROM benchmark_users WHERE id = #{id}")
    BenchmarkBean bean(long id);

    @SelectProvider(type = MyBatisSqlProvider.class, method = "dynamic")
    List<BenchmarkRecord> dynamic(@Param("name") String name, @Param("minimumAge") Integer minimumAge);

    @Select("SELECT id, name, age FROM benchmark_users WHERE id >= #{minimumId}")
    Cursor<BenchmarkRecord> scan(long minimumId);

    @Insert("INSERT INTO benchmark_batch (id, name) VALUES (#{id}, #{name})")
    int insertBatchRow(BenchmarkRecord row);

    @Insert("INSERT INTO benchmark_generated (name) VALUES (#{name})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertGenerated(GeneratedRow row);

    final class GeneratedRow {
        private Long id;
        private final String name;

        public GeneratedRow(String name) {
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }
    }
}
