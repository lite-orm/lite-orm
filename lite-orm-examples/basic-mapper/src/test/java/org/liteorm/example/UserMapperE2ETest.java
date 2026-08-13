package org.liteorm.example;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.SimpleConnectionManager;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserMapperE2ETest {

    private JdbcDataSource dataSource;
    private UserMapper annotationMapper;
    private UserXmlMapper xmlMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:liteorm-example;DB_CLOSE_DELAY=-1");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(100), email VARCHAR(200), age INT)");
        }

        SimpleConnectionManager connectionManager = new SimpleConnectionManager(dataSource);
        annotationMapper = new UserMapperImpl(connectionManager);
        xmlMapper = new UserXmlMapperImpl(connectionManager);
    }

    @Test
    void generatedAnnotationAndXmlMappersExecuteCrudAgainstH2() {
        assertEquals(1, annotationMapper.insert(1L, "Alice", "alice@example.com", 30));
        assertEquals(new User(1L, "Alice", "alice@example.com", 30), annotationMapper.findById(1L));
        assertEquals(new User(1L, "Alice", "alice@example.com", 30), xmlMapper.findByEmail("alice@example.com"));

        assertEquals(1, annotationMapper.update(1L, "Alice Zhang", "alice.zhang@example.com", 31));
        assertEquals(new User(1L, "Alice Zhang", "alice.zhang@example.com", 31), annotationMapper.findById(1L));

        assertEquals(1, annotationMapper.deleteById(1L));
        assertNull(annotationMapper.findById(1L));
    }

    @Test
    void xmlForeachPreservesParameterOrderAndOmitsEmptyCollectionCondition() {
        annotationMapper.insert(1L, "Alice", "alice@example.com", 30);
        annotationMapper.insert(2L, "Bob", "bob@example.com", 28);
        annotationMapper.insert(3L, "Carol", "carol@example.com", 35);

        assertEquals(
            List.of(
                new User(1L, "Alice", "alice@example.com", 30),
                new User(3L, "Carol", "carol@example.com", 35)
            ),
            xmlMapper.findByIds(List.of(1L, 3L))
        );
        assertEquals(3, xmlMapper.findByIds(List.of()).size());
        assertEquals(3, xmlMapper.findByIds(null).size());
    }

    @Test
    void xmlSetPreservesParameterOrderWhenOptionalAssignmentsAreSkipped() {
        annotationMapper.insert(1L, "Alice", "alice@example.com", 30);

        assertEquals(
            1,
            xmlMapper.updateSelective(new User(1L, null, "updated@example.com", 31))
        );
        assertEquals(
            new User(1L, "Alice", "updated@example.com", 31),
            annotationMapper.findById(1L)
        );
    }

    @Test
    void generatedMapperMapsScalarAndScalarListResults() {
        annotationMapper.insert(1L, "Alice", "alice@example.com", 30);
        annotationMapper.insert(2L, "Bob", "bob@example.com", 28);

        assertEquals("Alice", annotationMapper.findNameById(1L));
        assertEquals(2L, annotationMapper.countUsers());
        assertEquals(List.of("Alice", "Bob"), annotationMapper.findAllNames());
    }

    @Test
    void generatedMapperMapsJavaBeansThroughNoArgConstructorAndSetters() {
        annotationMapper.insert(1L, "Alice", "alice@example.com", 30);
        annotationMapper.insert(2L, "Bob", "bob@example.com", 28);

        assertUserBean(annotationMapper.findBeanById(1L), 1L, "Alice", "alice@example.com", 30);

        List<UserBean> users = annotationMapper.findAllBeans();
        assertEquals(2, users.size());
        assertUserBean(users.get(0), 1L, "Alice", "alice@example.com", 30);
        assertUserBean(users.get(1), 2L, "Bob", "bob@example.com", 28);
    }

    @Test
    void generatedMapperUsesDocumentedEmptyResultSemantics() {
        assertNull(annotationMapper.findNameById(999L));
        assertNull(annotationMapper.findById(999L));
        assertNull(annotationMapper.findBeanById(999L));
        assertEquals(List.of(), annotationMapper.findAllNames());
        assertEquals(List.of(), annotationMapper.findAllBeans());
    }

    @Test
    void generatedMapperCallsCompileTimeBoundSqlProvider() {
        annotationMapper.insert(1L, "Alice", "alice@example.com", 30);
        annotationMapper.insert(2L, "Bob", "bob@example.com", 28);
        annotationMapper.insert(3L, "Alfred", "alfred@example.com", 35);

        assertEquals(
            List.of(
                new User(3L, "Alfred", "alfred@example.com", 35),
                new User(1L, "Alice", "alice@example.com", 30)
            ),
            annotationMapper.search(new UserSearch("Al", true))
        );
        assertEquals(3, annotationMapper.search(new UserSearch(null, false)).size());
    }

    private void assertUserBean(UserBean user, Long id, String name, String email, Integer age) {
        assertEquals(id, user.getId());
        assertEquals(name, user.getName());
        assertEquals(email, user.getEmail());
        assertEquals(age, user.getAge());
    }
}
