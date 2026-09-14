package io.github.lynxus.test;

import io.github.lynxus.annotation.Mapper;
import java.util.List;

/**
 * XML-only Mapper fixture used by parser and source-generation tests.
 * 
 * @author lynxus
 * @since 2024/11/15
 */
@Mapper
public interface UserMapperXml {
    
    /**
     * Tests a simple XML query.
     */
    User findById(Long id);
    
    /**
     * Tests XML {@code where}, {@code if}, and {@code choose} elements.
     */
    List<User> findByCondition(String name, Integer age, String status);
    
    /**
     * Tests a dynamic XML insert with {@code trim}.
     */
    int insertUser(User user);
    
    /**
     * Tests a dynamic XML update with {@code set}.
     */
    int updateUser(User user);
    
    /**
     * Tests an XML batch insert with {@code foreach}.
     */
    int batchInsert(List<User> list);
    
    /**
     * Tests dynamic XML query conditions and pagination.
     */
    List<User> findUsersWithPagination(
        String keyword, 
        Integer minAge, 
        Integer maxAge, 
        List<String> statusList,
        Integer offset,
        Integer limit
    );
}
