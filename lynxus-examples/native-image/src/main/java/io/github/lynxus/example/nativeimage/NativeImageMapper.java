package io.github.lynxus.example.nativeimage;

import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

@Mapper
public interface NativeImageMapper {

    @Select("SELECT id, name FROM users WHERE id = #{id}")
    NativeImageUser findById(@Param("id") Long id);
}
