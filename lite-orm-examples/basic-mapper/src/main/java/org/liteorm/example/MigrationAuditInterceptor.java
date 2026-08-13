package org.liteorm.example;

import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionInvocation;

import java.util.ArrayList;
import java.util.List;

public final class MigrationAuditInterceptor implements ExecutionInterceptor {

    private final String name;
    private final List<String> events;

    public MigrationAuditInterceptor() {
        this("audit", new ArrayList<>());
    }

    public MigrationAuditInterceptor(String name, List<String> events) {
        this.name = name;
        this.events = events;
    }

    @Override
    public void beforeExecution(ExecutionInvocation invocation) {
        events.add(name + ":before:" + invocation.statementId());
    }

    @Override
    public void afterSuccess(ExecutionInvocation invocation) {
        events.add(name + ":success:" + invocation.statementId() + ":" + invocation.affectedRows());
    }

    @Override
    public void afterFailure(ExecutionInvocation invocation) {
        events.add(name + ":failure:" + invocation.statementId());
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
