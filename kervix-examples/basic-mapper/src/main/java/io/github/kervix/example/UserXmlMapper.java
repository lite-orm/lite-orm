package io.github.kervix.example;

import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;

import java.util.List;

@Mapper
public interface UserXmlMapper {

    User findByEmail(@Param("email") String email);

    List<User> findByIds(@Param("ids") List<Long> ids);

    List<User> searchAdvanced(@Param("name") String name,
                              @Param("minimumAge") Integer minimumAge,
                              @Param("ids") List<Long> ids);

    int updateSelective(@Param("user") User user);

    int[] insertBatch(List<User> users);
}
