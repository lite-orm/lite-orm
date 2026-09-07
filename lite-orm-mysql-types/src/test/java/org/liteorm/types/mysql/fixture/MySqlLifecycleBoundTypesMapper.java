package org.liteorm.types.mysql.fixture;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.ResultJdbcType;
import org.liteorm.annotation.Select;

import java.sql.JDBCType;

@Mapper
public interface MySqlLifecycleBoundTypesMapper {

    @Insert("INSERT INTO mysql_lifecycle_values (id, blob_value, clob_value, nclob_value) "
        + "VALUES (#{id}, #{blob,jdbcType=BLOB}, #{clob,jdbcType=CLOB}, #{nclob,jdbcType=NCLOB})")
    int insert(
        @Param("id") long id,
        @Param("blob") byte[] blob,
        @Param("clob") String clob,
        @Param("nclob") String nclob
    );

    @ResultJdbcType(JDBCType.BLOB)
    @Select("SELECT blob_value FROM mysql_lifecycle_values WHERE id = #{id}")
    byte[] findBlob(@Param("id") long id);

    @ResultJdbcType(JDBCType.CLOB)
    @Select("SELECT clob_value FROM mysql_lifecycle_values WHERE id = #{id}")
    String findClob(@Param("id") long id);

    @ResultJdbcType(JDBCType.NCLOB)
    @Select("SELECT nclob_value FROM mysql_lifecycle_values WHERE id = #{id}")
    String findNClob(@Param("id") long id);
}
