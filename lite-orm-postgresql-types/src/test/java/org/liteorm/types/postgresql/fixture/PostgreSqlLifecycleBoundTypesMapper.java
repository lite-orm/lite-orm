package org.liteorm.types.postgresql.fixture;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.ResultJdbcType;
import org.liteorm.annotation.Select;

import java.sql.JDBCType;

@Mapper
public interface PostgreSqlLifecycleBoundTypesMapper {

    @Insert("INSERT INTO postgresql_lifecycle_values (id, xml_value) "
        + "VALUES (#{id}, CAST(#{xml,jdbcType=SQLXML} AS XML))")
    int insertXml(
        @Param("id") long id,
        @Param("xml") String xml
    );

    @ResultJdbcType(JDBCType.SQLXML)
    @Select("SELECT xml_value FROM postgresql_lifecycle_values WHERE id = #{id}")
    String findXml(@Param("id") long id);

    @ResultJdbcType(JDBCType.ARRAY)
    @Select("SELECT array_value FROM postgresql_lifecycle_values WHERE id = #{id}")
    Object[] findArray(@Param("id") long id);
}
