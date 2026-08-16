package org.liteorm.benchmark;

import org.liteorm.annotation.Batch;
import org.liteorm.annotation.GeneratedKey;
import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Select;
import org.liteorm.annotation.UseRowMapper;
import org.liteorm.api.CursorCallback;

import java.util.List;

@Mapper
public interface LiteOrmBenchmarkMapper {

    @Select("SELECT name FROM benchmark_users WHERE id = #{id}")
    String scalar(long id);

    @Select("SELECT id, name, age FROM benchmark_users WHERE id = #{id}")
    BenchmarkRecord record(long id);

    @Select("SELECT id, name, age FROM benchmark_users WHERE id = #{id}")
    BenchmarkBean bean(long id);

    @Select({
        "<script>",
        "SELECT id, name, age FROM benchmark_users",
        "<where>",
        "<if test='name != null'>name = #{name}</if>",
        "<if test='minimumAge != null'>AND age &gt;= #{minimumAge}</if>",
        "</where>",
        "</script>"
    })
    List<BenchmarkRecord> dynamic(String name, Integer minimumAge);

    @Select("SELECT id, name, age FROM benchmark_users WHERE id >= #{minimumId}")
    @UseRowMapper(BenchmarkRecordRowMapper.class)
    long scan(long minimumId, CursorCallback<BenchmarkRecord, Long> callback);

    @Batch("INSERT INTO benchmark_batch (id, name) VALUES (#{item.id}, #{item.name})")
    int[] insertBatch(List<BenchmarkRecord> rows);

    @GeneratedKey("id")
    @Insert("INSERT INTO benchmark_generated (name) VALUES (#{name})")
    long insertGenerated(String name);
}
