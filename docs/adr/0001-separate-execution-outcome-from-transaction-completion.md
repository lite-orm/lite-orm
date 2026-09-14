# Separate execution outcome from transaction completion

Lynxus defines an execution outcome after the SQL executor has released every JDBC resource it owns, including its connection handle, but before any surrounding standalone or hosted transaction necessarily commits or rolls back. This boundary lets terminal interceptors observe cleanup failures without giving `JdbcSqlExecutor` transaction completion authority or coupling it to Spring transaction ownership.
