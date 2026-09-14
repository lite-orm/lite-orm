package io.github.lynxus.testsupport.database;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared real-database fixture with an isolated logical namespace per DataSource.
 */
public final class TestDatabase implements AutoCloseable {

    private static final Map<DatabaseEngine, TestDatabase> SHARED = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> SHARED.values().forEach(TestDatabase::close),
            "lynxus-test-database-shutdown"));
    }

    private final DatabaseEngine engine;
    private final JdbcDatabaseContainer<?> container;
    private final AtomicLong namespaceSequence = new AtomicLong();

    private TestDatabase(DatabaseEngine engine) {
        this.engine = engine;
        this.container = switch (engine) {
            case POSTGRESQL -> new PostgreSQLContainer<>(DockerImageName.parse(engine.imageName()));
            case MYSQL -> new MySQLContainer<>(DockerImageName.parse(engine.imageName()));
        };
        container.start();
    }

    public static TestDatabase shared(DatabaseEngine engine) {
        return SHARED.computeIfAbsent(engine, TestDatabase::new);
    }

    public DatabaseEngine engine() {
        return engine;
    }

    public String imageName() {
        return container.getDockerImageName();
    }

    public DataSource createDataSource() {
        String namespace = "lynxus_" + namespaceSequence.incrementAndGet();
        try {
            return switch (engine) {
                case POSTGRESQL -> createPostgresDataSource(namespace);
                case MYSQL -> createMySqlDataSource(namespace);
            };
        } catch (SQLException failure) {
            throw new IllegalStateException("Failed to create isolated " + engine + " test database", failure);
        }
    }

    public void execute(DataSource dataSource, String... statements) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private DataSource createPostgresDataSource(String schema) throws SQLException {
        PGSimpleDataSource admin = postgresDataSource();
        execute(admin, "CREATE SCHEMA \"" + schema + "\"");
        PGSimpleDataSource isolated = postgresDataSource();
        isolated.setCurrentSchema(schema);
        return isolated;
    }

    private PGSimpleDataSource postgresDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(container.getJdbcUrl());
        dataSource.setUser(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }

    private DataSource createMySqlDataSource(String database) throws SQLException {
        MysqlDataSource admin = mysqlDataSource(container.getJdbcUrl(), "root", container.getPassword());
        execute(admin,
            "CREATE DATABASE `" + database + "`",
            "GRANT ALL PRIVILEGES ON `" + database + "`.* TO '" + container.getUsername() + "'@'%'");
        return mysqlDataSource(withDatabase(container.getJdbcUrl(), database));
    }

    private MysqlDataSource mysqlDataSource(String jdbcUrl) {
        return mysqlDataSource(jdbcUrl, container.getUsername(), container.getPassword());
    }

    private MysqlDataSource mysqlDataSource(String jdbcUrl, String username, String password) {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private String withDatabase(String jdbcUrl, String database) {
        int queryStart = jdbcUrl.indexOf('?');
        String base = queryStart < 0 ? jdbcUrl : jdbcUrl.substring(0, queryStart);
        String query = queryStart < 0 ? "" : jdbcUrl.substring(queryStart);
        return base.substring(0, base.lastIndexOf('/') + 1) + database + query;
    }

    @Override
    public void close() {
        if (container.isRunning()) {
            container.stop();
        }
    }
}
