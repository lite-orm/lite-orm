package io.github.kervix.example;

import io.github.kervix.api.ExecutionInterceptor;
import io.github.kervix.api.ExecutionOutcome;
import io.github.kervix.api.ExecutionPlan;

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
    public void beforeExecution(ExecutionPlan plan) {
        events.add(name + ":before:" + plan.getStatementId());
    }

    @Override
    public void afterSuccess(ExecutionOutcome outcome) {
        events.add(name + ":success:" + outcome.plan().getStatementId() + ":" + outcome.affectedRows());
    }

    @Override
    public void afterFailure(ExecutionOutcome outcome) {
        events.add(name + ":failure:" + outcome.plan().getStatementId());
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
