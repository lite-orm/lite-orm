# JDBC Final Outcome and Compiler Failure Semantics

## Problem Statement

LiteORM users can currently receive contradictory or misleading failure information. A terminal interceptor may observe a successful execution before execution cleanup finishes, while the Mapper caller subsequently receives a cleanup exception. When SQL or result processing fails and cleanup also fails, the failure tree is not governed by one explicit finalization model. Compiler parsing failures may be printed to process output, swallowed, converted into a missing-statement error, or downgraded to plain SQL text, which forces users to reconstruct the real cause from incomplete evidence.

From the user's perspective, one Mapper invocation must have one trustworthy final result, and one invalid Mapper declaration must produce one deterministic javac diagnostic at the most useful source location. The framework must answer where the failure occurred, whether JDBC execution was attempted or completed, which Mapper declaration is responsible, and which additional cleanup failures occurred without losing the primary cause.

## Solution

LiteORM will form an execution outcome only after releasing every JDBC resource owned by one SQL executor invocation. The final outcome will include execution cleanup but exclude transaction completion. A terminal interceptor and the Mapper caller will observe the same final failure object for ordinary runtime failures. Primary failures will retain ownership of the failure, while cleanup failures will be preserved once, in deterministic cleanup order, as suppressed failures.

Ordinary execution and cursor execution will use the same internal finalization policy without exposing a public lifecycle phase chain. Known result metrics will remain observable on failure outcomes, and execution duration will include cleanup while excluding terminal interceptor time.

The compiler will reject invalid input instead of falling back. Effective Mapper SQL method overloads will be rejected before SQL source binding. Mapper XML and annotation scripts will use one fail-closed, offline XML parser configuration. Parser and validation failures will flow through structured internal compiler failures and become javac errors attached to the most specific Mapper element available. Independent Mappers will continue to be processed so one compilation can report multiple actionable errors.

## User Stories

1. As a Mapper caller, I want one final result for each invocation, so that application control flow and observability do not disagree.
2. As a Mapper caller, I want a cleanup failure after successful SQL execution to be reported as a failure, so that resource-release problems are not hidden behind an earlier success notification.
3. As a Mapper caller, I want the original SQL, read, mapping, or callback failure to remain primary, so that I investigate the cause that actually interrupted the operation.
4. As a Mapper caller, I want every additional cleanup failure preserved as suppressed evidence, so that secondary resource problems are not lost.
5. As a Mapper caller, I want cleanup failures listed in deterministic resource-release order, so that repeated failures produce stable logs and tests.
6. As a Mapper caller, I want each cleanup failure represented once, so that recursive stack-trace rendering does not duplicate evidence.
7. As an interceptor author, I want `afterSuccess` to run only after execution cleanup succeeds, so that success means the executor-owned lifecycle has actually completed.
8. As an interceptor author, I want `afterFailure` to receive the same final failure delivered to the Mapper caller, so that logs, metrics, and application handling describe the same event.
9. As an interceptor author, I want cleanup-only failures to expose the cleanup phase and actual JDBC execution state, so that I can distinguish resource release from SQL execution failure.
10. As an interceptor author, I want terminal callbacks to run once and in reverse entry order, so that observational nesting remains deterministic.
11. As an interceptor author, I want one terminal interceptor's runtime failure isolated and logged, so that other interceptors still receive the final outcome.
12. As an interceptor author, I want JVM `Error` values to remain outside ordinary callback isolation, so that the framework does not silently swallow severe process failures.
13. As an interceptor author, I want only interceptors whose `beforeExecution` completed to receive terminal callbacks, so that callbacks are not invoked against partially initialized interceptor state.
14. As an operator, I want failure outcomes to retain confirmed affected-row and result-count metrics, so that I can understand how far an invocation progressed.
15. As an operator, I want execution duration to include JDBC cleanup, so that slow resource release is visible in logging and slow-query observation.
16. As an operator, I want terminal interceptor time excluded from execution duration, so that observation overhead is not attributed to JDBC execution.
17. As a cursor user, I want cursor deactivation and resource cleanup to finish before a terminal callback, so that escaped cursors cannot appear usable after the outcome is published.
18. As a cursor user, I want my callback runtime exception preserved as the final failure, so that framework wrapping does not obscure application-owned behavior.
19. As a cursor user, I want cleanup failures suppressed on my callback exception, so that application and resource failures remain visible together.
20. As a standalone transaction user, I want execution cleanup kept separate from commit and rollback, so that statement observation does not claim transaction completion.
21. As a Spring transaction user, I want connection-handle release included in execution cleanup without giving LiteORM commit or rollback authority, so that Spring retains transaction ownership.
22. As a batch user, I want the actual JDBC execution state preserved when cleanup fails, so that an empty batch is not incorrectly reported as executed.
23. As a Mapper author, I want malformed XML to fail compilation with its resource and Mapper context, so that I do not receive a misleading missing-statement error.
24. As a Mapper author, I want malformed annotation scripts to fail compilation, so that invalid dynamic SQL is never downgraded to plain SQL text.
25. As a Mapper author, I want effective SQL method overloads rejected with both conflicting signatures, so that generated names and statement identifiers remain unambiguous.
26. As a Mapper author, I want Object methods, SQL-free default helpers, and inherited same-signature overrides excluded from overload detection, so that valid interfaces are not rejected.
27. As a Mapper author, I want diagnostics attached to the relevant Mapper method when javac can represent that location, so that IDE navigation opens the source I need to change.
28. As a Mapper author, I want diagnostics to distinguish Mapper-derived context from declarations actually parsed from XML, so that malformed input is never supplemented with guessed facts.
29. As a Mapper author, I want stable Mapper, method, resource, statement, tag, and attribute context without freezing JDK parser prose, so that diagnostics remain useful across JDK updates.
30. As a Mapper author, I want one invalid Mapper to avoid blocking analysis of independent Mappers, so that a single compilation reports more actionable problems.
31. As a Mapper author, I want one Mapper to stop after its first deterministic error, so that I do not receive cascaded diagnostics derived from an invalid compilation model.
32. As a security engineer, I want Mapper XML and annotation scripts parsed without filesystem or network resolution, so that javac cannot be used as an external-resource access path.
33. As a security engineer, I want external entities, external DTDs, XInclude, and external schema access rejected, so that XML compilation is fail-closed.
34. As a security engineer, I want compilation to fail if the active JAXP provider cannot enforce a required security feature, so that LiteORM never silently falls back to a weaker parser.
35. As a maintainer, I want one internal XML parser factory, so that security policy cannot drift between Mapper XML and annotation scripts.
36. As a maintainer, I want one internal execution finalization policy shared by ordinary and cursor paths, so that failure semantics do not diverge over time.
37. As a maintainer, I want finalization to remain internal, so that LiteORM preserves one fixed JDBC lifecycle instead of exposing a general phase plugin chain.
38. As a maintainer, I want compiler failures reported through javac messaging rather than process output, so that Maven, Gradle, IDEs, and tests observe the same contract.
39. As a maintainer, I want failed Mappers to produce no incomplete generated source, so that downstream compiler errors do not hide the original rejection.
40. As a support engineer, I want exception type, statement identity, execution phase, JDBC state, primary cause, and suppressed cleanup evidence to agree, so that a user can determine the next action without reconstructing framework internals.

## Implementation Decisions

- The work is split into two production concerns within the core module: JDBC execution finalization and compiler rejection/security. They share terminology and documentation but do not share a public implementation abstraction.
- An execution outcome covers one SQL executor invocation through result-set, statement, and connection-handle cleanup. It excludes standalone and hosted transaction completion.
- Plan validation remains outside execution observation. Invalid plans do not enter interceptors or produce execution outcomes.
- Interceptors enter the invocation only after their `beforeExecution` callback returns successfully. Failure unwinds only the successfully entered prefix in reverse order.
- Ordinary execution and cursor execution share an internal finalization module that owns cleanup collection, final-failure construction, outcome construction, terminal notification, and return-or-throw behavior.
- The finalization module remains private or package-private. No public execution finalizer, phase chain, session abstraction, or general plugin mechanism is introduced.
- The public shape of `ExecutionOutcome` remains unchanged. For ordinary runtime failures, its failure value is the same throwable ultimately delivered to the Mapper caller.
- A physical JDBC lifecycle failure is exposed as `SqlExecutionException`. The original execution failure remains its cause.
- A cleanup-only failure becomes a `SqlExecutionException` in the cleanup phase. The first cleanup failure is its cause and later cleanup failures are suppressed on that cause.
- When an earlier execution failure exists, cleanup failures are attached directly to the primary cause in result-set, statement, and connection-handle order. Each cleanup failure appears once.
- Cursor callback runtime exceptions remain application-owned final failures rather than being wrapped as SQL execution failures. Cleanup failures are suppressed directly on the callback exception.
- Existing propagation of execution-time `Error` values is preserved. Terminal interceptor isolation applies to `RuntimeException`, not `Error`.
- Terminal interceptor runtime failures are logged individually, do not mutate the final outcome or failure tree, and do not prevent remaining terminal interceptors from running.
- Known metrics survive failure finalization. Cleanup-only failures retain completed affected-row or result counts, and cursor failures retain confirmed rows read. Unavailable partial metrics are not guessed.
- Execution duration starts before interceptor entry and ends after execution cleanup. Terminal interceptor duration is excluded.
- JDBC execution state remains factual. Cleanup failure does not force `EXECUTED`; an invocation that never called JDBC execute retains `NOT_EXECUTED`, while an entered execute call that threw retains `OUTCOME_UNKNOWN`.
- Effective Mapper SQL methods are collected across declared and inherited members, with Object methods, SQL-free default helpers, and overridden same-signature methods removed before overload validation.
- Effective Mapper SQL methods with the same simple name are rejected regardless of whether their SQL source is annotation, XML, or typed provider. The diagnostic contains the Mapper identity and all conflicting signatures in deterministic order.
- Compiler validation order is deterministic: effective-method collection, overload rejection, secure SQL-source parsing, method and return-shape validation, parameter and expression validation, result-mapping validation, then source generation.
- One Mapper stops after its first deterministic compiler rejection. Independent Mapper elements continue through processing so one javac invocation can report multiple Mapper failures.
- Mapper XML and annotation scripts share one internal secure XML factory. Until a LiteORM-owned DTD is delivered, every `DOCTYPE` is rejected.
- XML parsing disables external general entities, external parameter entities, external DTD loading, XInclude, external schema access, filesystem resolution, and network resolution.
- Failure to configure any required XML security control is a compiler rejection. The compiler never continues with a partially hardened parser.
- Parser failures are carried as structured internal compiler failures rather than printed, swallowed, returned as a missing resource, or downgraded to text SQL.
- Compiler diagnostics always include Mapper-derived context that is known without guessing. Declared namespace, tag, attribute, or other XML-specific context is included only when successfully parsed or validated.
- Diagnostic tests stabilize context fields and important LiteORM wording, but do not freeze vendor- or JDK-provided parser sentences. Public diagnostic error codes are not introduced in this work.
- A failed Mapper is fully rejected before generated source is created. Other valid Mappers may still generate normally in the same compilation.
- Public contracts and Javadocs are updated with the implementation. The execution-outcome boundary remains explained by the accepted architecture decision, while the core contract owns normative behavior.
- Delivery uses three focused commits: terminology and decision documentation, JDBC finalization, and deterministic compiler rejection/security.

## Testing Decisions

- Tests assert externally observable behavior rather than private helper structure. No test should require a particular internal finalizer class or method name.
- JDBC behavior is tested at the existing `SqlExecutor` boundary by supplying controlled connection handles, JDBC resources, cursor callbacks, and interceptors. This is the highest existing seam that observes resource order, outcome delivery, exception identity, metrics, and return behavior together.
- Compiler behavior is tested through real javac annotation processing with Mapper source and resource fixtures. This is the highest existing seam that observes source locations, diagnostics, generation rejection, inheritance behavior, and independent Mapper processing together.
- The two seams are intentionally separate because runtime finalization and javac compilation have no meaningful shared behavioral boundary. No new public seam is added.
- Existing JDBC executor and cursor tests provide prior art for proxy JDBC resources, event ordering, execution-state assertions, terminal interceptor ordering, and cleanup failure simulation.
- Existing unsupported-signature and Mapper compilation tests provide prior art for temporary source trees, real processor invocation, generated-source inspection, and method-attached diagnostic assertions.
- JDBC tests first demonstrate that cleanup occurs after the current success callback and therefore produce RED before production changes.
- JDBC success tests cover ordinary SELECT, write, generated-key, batch, and cursor execution with cleanup occurring before one success callback.
- Cleanup-only tests independently cover result-set, statement, and connection-handle failures and require one failure callback with cleanup phase metadata.
- Combined-failure tests cover execution, result-reading, mapping, cursor callback, and cleanup failures while preserving primary identity and deterministic suppressed ordering.
- Multiple-cleanup tests require each cleanup failure to appear exactly once and in result-set, statement, connection-handle order.
- Interceptor tests cover partial `beforeExecution` entry, reverse unwind, terminal runtime failure isolation, continuation to remaining observers, and non-isolation of `Error`.
- Metric tests require confirmed affected rows, completed result counts, and cursor rows read to survive cleanup or later failures without inventing unavailable partial values.
- Duration tests verify that cleanup completes before the duration-bearing outcome is published and that terminal callback work is excluded. Tests must avoid assertions on exact wall-clock duration.
- Empty-batch tests preserve `NOT_EXECUTED` when cleanup fails without a JDBC execute call.
- Compiler overload tests cover same-interface overloads, inherited overloads, inherited same-signature overrides, Object methods, SQL-free default helpers, annotation SQL, XML SQL, and typed providers.
- XML diagnostic tests cover malformed documents, missing statements, unsupported tags and attributes, cyclic includes, and context derived from both Mapper source and successfully parsed XML.
- Annotation-script tests require malformed dynamic scripts to reject compilation rather than becoming text nodes.
- XML security tests cover `DOCTYPE`, external general entities, external parameter entities, external DTDs, XInclude, external schema access, filesystem targets, and network targets without actually contacting external resources.
- Secure-parser initialization tests require unsupported mandatory security controls to fail closed with actionable diagnostic context.
- Multi-Mapper compilation tests include independent invalid Mappers and require diagnostics for each, while each individual Mapper stops after its first deterministic failure.
- Failed-generation tests require no source output for the rejected Mapper while valid Mappers in the same javac invocation remain eligible for generation.
- Focused tests are followed by the complete core module suite, the full Maven reactor, a production-source scan proving no compiler `System.err` or stack-trace printing remains, and documentation language/link/diff checks.
- Production database containers are not a completion gate for this work because the JDBC driver SQL contract is unchanged and this specification makes no release claim.

## Out of Scope

- Observing, predicting, or redefining standalone or Spring transaction commit and rollback outcomes.
- Giving `JdbcSqlExecutor` transaction completion authority.
- Adding a session API, runtime Mapper proxy, public lifecycle phase chain, or general SQL execution plugin system.
- Adding new public fields to `ExecutionOutcome` or exposing an execution finalizer as public API.
- Supporting Mapper SQL method overloading through signature hashes, numeric suffixes, or XML statement-ID extensions.
- Adding public compiler diagnostic error codes or a complete error catalog.
- Delivering the versioned LiteORM Mapper DTD; this specification rejects all `DOCTYPE` declarations until that separate contract exists.
- Integrating secure XML parsing into migration or generator modules that are not yet implemented or stabilized.
- Changing SQL parameter, result-mapping, generated-key, batch, cursor, transaction, or database-driver feature contracts beyond the failure semantics explicitly described here.
- Reporting speculative partial row counts that the current execution path cannot determine reliably.
- Catching or suppressing JVM `Error` values as ordinary interceptor failures.
- Requiring PostgreSQL or MySQL Testcontainers as a task-level completion gate.
- Refactoring unrelated compiler, JDBC, transaction, Spring, documentation, or generated-source code.

## Further Notes

- The project glossary defines execution outcome, final failure, execution cleanup, transaction completion, terminal interceptor, compiler rejection, diagnostic context, and offline XML resolution. These terms are normative for this specification.
- The accepted architecture decision separates execution outcome from transaction completion. Contracts describe the observable behavior; the decision record explains why that boundary exists.
- The current core contract documents terminal interceptors before cleanup and must be corrected in the same delivery as the runtime behavior.
- Extension documentation currently describes terminal callback failures differently from the agreed isolation policy and must be aligned with the final contract.
- The active adoption roadmap already identifies this work as the first core correctness task. This specification refines that task with the decisions reached during design grilling and does not expand later roadmap milestones.
