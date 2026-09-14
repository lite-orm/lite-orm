package io.github.kervix.benchmark;

import io.github.kervix.annotation.Batch;
import io.github.kervix.annotation.GeneratedKey;
import io.github.kervix.annotation.Insert;
import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Select;
import io.github.kervix.annotation.UseRowMapper;
import io.github.kervix.api.CursorCallback;

import java.util.List;

@Mapper
public interface KervixBenchmarkMapper {

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
