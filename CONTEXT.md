# LiteORM Context

LiteORM compiles Mapper declarations into explicit JDBC execution plans and generated implementations. This glossary defines the project-specific language used to describe compilation, execution ownership, and terminal outcomes.

## Language

**Mapper SQL method**:
A Mapper method backed by a LiteORM SQL annotation, Mapper XML statement, or typed SQL provider. Two effective Mapper SQL methods with the same name are overloaded even when their parameter types differ.
_Avoid_: Statement method, query method

**Execution outcome**:
The terminal observation of one SQL executor invocation after all execution-owned JDBC resources have been released. It does not claim that a surrounding standalone or hosted transaction has committed or rolled back.
_Avoid_: Transaction result, commit result

**Final failure**:
The same throwable observed by terminal interceptors and delivered to the Mapper caller after execution cleanup. Its cause and suppressed failures preserve the underlying execution and cleanup evidence.
_Avoid_: Logged failure, parser failure

**Execution cleanup**:
The release of the result set, statement, and connection handle owned by one SQL executor invocation. Transaction commit and rollback are outside this boundary.
_Avoid_: Transaction completion, connection shutdown

**Transaction completion**:
The commit or rollback performed by the standalone transactional executor or a host transaction manager after participating SQL executions finish. It is distinct from an execution outcome.
_Avoid_: Execution cleanup, Mapper completion

**Terminal interceptor**:
An observational callback invoked after an execution outcome is final. A terminal interceptor does not own JDBC cleanup or transaction completion and cannot change an ordinary execution result through a runtime callback failure.
_Avoid_: Execution phase, transaction callback

**Compiler rejection**:
A deterministic javac error produced when LiteORM cannot safely compile a Mapper declaration. Compiler rejection never falls back to runtime SQL interpretation or guessed generated behavior.
_Avoid_: Parser warning, runtime fallback

**Diagnostic context**:
The Mapper, method, resource path, statement identifier, and any XML declaration details that LiteORM can determine without guessing. Context derived from the Mapper is distinct from declarations successfully parsed from XML.
_Avoid_: Parser stack trace, inferred XML declaration

**Offline XML resolution**:
XML resolution that permits only LiteORM-owned local resources and rejects external entities, XInclude, filesystem resources, and network resources.
_Avoid_: Best-effort XML parsing, remote DTD resolution

**Type handler manager**:
The Core runtime component that routes supported values from generated Java type information and live JDBC metadata. Its standard routes are fixed and it does not construct result objects or discover user handlers.
_Avoid_: JDBC value adapter, row mapper, global type-handler registry

**Result mapping**:
A query-level declaration that maps result columns to one scalar, record constructor, or JavaBean property structure. Annotation and XML forms normalize into the same compilation model.
_Avoid_: Type handler, entity column mapping, row mapper

**Parameter binder**:
A Mapper-parameter-specific strategy for writing a value outside Core standard routing. It is not a result-reading strategy.
_Avoid_: Type handler, row mapper, global type handler

**Row mapper**:
A Mapper-method-specific strategy for constructing one result object from the current result row. It may combine several columns and does not bind statement parameters.
_Avoid_: Type handler, parameter binder, result-set interceptor
