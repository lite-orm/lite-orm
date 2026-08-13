package org.liteorm.example;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.DefaultSqlEngine;
import org.liteorm.JdbcConnectionProvider;
import org.liteorm.runtime.ExecutionProcessor;
import org.liteorm.runtime.ParameterProcessor;
import org.liteorm.runtime.ResultProcessor;
import org.liteorm.runtime.SqlProcessor;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserMapperE2ETest {

    private JdbcDataSource dataSource;
    private UserMapper annotationMapper;
    private UserXmlMapper xmlMapper;
    private UserMetadataMapper metadataMapper;
    private UserMapper auditedMapper;
    private List<String> auditEvents;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:liteorm-example;DB_CLOSE_DELAY=-1");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(100), email VARCHAR(200), age INT)");
            statement.execute("DROP TABLE IF EXISTS user_metadata");
            statement.execute("CREATE TABLE user_metadata (user_id BIGINT PRIMARY KEY, payload VARCHAR(500))");
        }

        JdbcConnectionProvider connectionProvider = new JdbcConnectionProvider(dataSource);
        annotationMapper = new UserMapperImpl(connectionProvider);
        xmlMapper = new UserXmlMapperImpl(connectionProvider);
        metadataMapper = new UserMetadataMapperImpl(connectionProvider);
        auditEvents = new java.util.ArrayList<>();
        MigrationAuditInterceptor firstAuditInterceptor = new MigrationAuditInterceptor("first", auditEvents);
        MigrationAuditInterceptor secondAuditInterceptor = new MigrationAuditInterceptor("second", auditEvents);
        List<SqlProcessor> processors = List.of(
            new ParameterProcessor(),
            new ExecutionProcessor(),
            new ResultProcessor()
        );
        auditedMapper = new UserMapperImpl(new DefaultSqlEngine(
            connectionProvider,
            processors,
            List.of(firstAuditInterceptor, secondAuditInterceptor)
        ));
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
    void generatedAnnotationMapperExecutesRealJdbcBatch() {
        int[] counts = annotationMapper.insertBatch(List.of(
            new User(10L, "Dora", "dora@example.com", 25),
            new User(11L, "Evan", "evan@example.com", 27)
        ));

        assertArrayEquals(new int[]{1, 1}, counts);
        assertEquals(new User(10L, "Dora", "dora@example.com", 25), annotationMapper.findById(10L));
        assertEquals(new User(11L, "Evan", "evan@example.com", 27), annotationMapper.findById(11L));
    }

    @Test
    void generatedXmlMapperExecutesRealJdbcBatch() {
        int[] counts = xmlMapper.insertBatch(List.of(
            new User(12L, "Faye", "faye@example.com", 29),
            new User(13L, "Gabe", "gabe@example.com", 31)
        ));

        assertArrayEquals(new int[]{1, 1}, counts);
        assertEquals(new User(12L, "Faye", "faye@example.com", 29), annotationMapper.findById(12L));
        assertEquals(new User(13L, "Gabe", "gabe@example.com", 31), annotationMapper.findById(13L));
    }

    @Test
    void concurrentBatchCallsKeepParameterSetsIsolated() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> annotationMapper.insertBatch(List.of(
                new User(20L, "Hana", "hana@example.com", 24),
                new User(21L, "Ivan", "ivan@example.com", 26)
            )));
            var second = executor.submit(() -> annotationMapper.insertBatch(List.of(
                new User(30L, "Jade", "jade@example.com", 32),
                new User(31L, "Kyle", "kyle@example.com", 34)
            )));

            assertArrayEquals(new int[]{1, 1}, first.get(5, TimeUnit.SECONDS));
            assertArrayEquals(new int[]{1, 1}, second.get(5, TimeUnit.SECONDS));
        }

        assertEquals(List.of("Hana", "Ivan", "Jade", "Kyle"), annotationMapper.findAllNames());
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

    @Test
    void generatedMapperCallsCustomBinderAndRowMapperDirectly() {
        JsonValue blue = new JsonValue("{\"theme\":\"blue\"}");
        JsonValue red = new JsonValue("{\"theme\":\"red\"}");

        assertEquals(1, metadataMapper.insert(1L, blue));
        assertEquals(1, metadataMapper.insert(2L, red));
        assertEquals(1, metadataMapper.insert(3L, blue));
        assertEquals(1, metadataMapper.insert(4L, null));

        assertEquals(
            List.of(new UserMetadata(1L, blue), new UserMetadata(3L, blue)),
            metadataMapper.findByPayload(blue)
        );
        assertEquals(new UserMetadata(2L, red), metadataMapper.findByUserId(2L));
        assertEquals(new UserMetadata(4L, new JsonValue(null)), metadataMapper.findByUserId(4L));
        assertNull(metadataMapper.findByUserId(999L));
    }

    @Test
    void interceptorObservesGeneratedMapperWithoutChangingDispatch() {
        assertEquals(1, auditedMapper.insert(10L, "Audit", "audit@example.com", 40));

        assertEquals(
            List.of(
                "first:before:org.liteorm.example.UserMapper.insert",
                "second:before:org.liteorm.example.UserMapper.insert",
                "second:success:org.liteorm.example.UserMapper.insert:1",
                "first:success:org.liteorm.example.UserMapper.insert:1"
            ),
            auditEvents
        );
    }

    private void assertUserBean(UserBean user, Long id, String name, String email, Integer age) {
        assertEquals(id, user.getId());
        assertEquals(name, user.getName());
        assertEquals(email, user.getEmail());
        assertEquals(age, user.getAge());
    }
}
