package io.github.lynxus.example;

import io.github.lynxus.annotation.Insert;
import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;
import io.github.lynxus.annotation.UseParameterBinder;
import io.github.lynxus.annotation.UseRowMapper;

import java.util.List;

@Mapper
public interface UserMetadataMapper {

    @Insert("INSERT INTO user_metadata (user_id, payload) VALUES (#{userId}, #{payload})")
    int insert(@Param("userId") Long userId,
               @Param("payload") @UseParameterBinder(JsonValueBinder.class) JsonValue payload);

    @Select("SELECT user_id, payload FROM user_metadata WHERE payload = #{payload} ORDER BY user_id")
    @UseRowMapper(UserMetadataRowMapper.class)
    List<UserMetadata> findByPayload(
        @Param("payload") @UseParameterBinder(JsonValueBinder.class) JsonValue payload);

    @Select({
        "<script>",
        "SELECT user_id, payload FROM user_metadata",
        "<where><if test='payload != null'>payload = #{payload}</if></where>",
        "ORDER BY user_id",
        "</script>"
    })
    @UseRowMapper(UserMetadataRowMapper.class)
    List<UserMetadata> findByPayloadDynamically(
        @Param("payload") @UseParameterBinder(JsonValueBinder.class) JsonValue payload);

    @Select("SELECT user_id, payload FROM user_metadata WHERE user_id = #{userId}")
    @UseRowMapper(UserMetadataRowMapper.class)
    UserMetadata findByUserId(@Param("userId") Long userId);
}
