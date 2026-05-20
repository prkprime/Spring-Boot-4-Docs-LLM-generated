# Transactions

A transaction is a boundary around a unit of work. Inside that boundary, the database should see the work as one coherent change: either every required write commits, or the whole unit rolls back. For application code, that means you can load data, validate it, change several rows, and rely on the transaction manager to commit those changes together or discard them together when the operation fails.

Spring gives you two transaction styles. The usual style is declarative transactions with `@Transactional`: put the annotation on a service method, let Spring open the transaction before the method runs, and let Spring commit or roll back when the method exits. The other style is programmatic transactions with `TransactionTemplate`: inject the template and wrap the exact block that needs a transaction. Use declarative transactions for roughly 95% of application code. Reach for `TransactionTemplate` when a test needs an explicit fresh transaction, when one method must perform several small independent transactional blocks, or when the transaction boundary is easier to read as code than as annotations.

This chapter uses a tiny bank-transfer example. Money movement is a good transaction example because a half-finished transfer is obviously wrong. Debiting one account without crediting the other account must not commit.

Programmatic transaction code looks like this:

```java
transactionTemplate.executeWithoutResult((status) -> {
	accounts.save(new BankAccount("Alice", 10_000));
	accounts.save(new BankAccount("Bob", 2_500));
});
```

That style is explicit and useful in tests because the reader can see exactly where the fresh transaction starts and ends. In application services, it can become noisy if every method repeats the same wrapper. That is why the sample uses `@Transactional` for business behavior and `TransactionTemplate` only in tests for setup, cleanup, and fresh verification reads.

## The Account

The entity has only an id, an owner, and a balance stored in cents:

```java
{% include-markdown "../../code/20-transactions/maven/src/main/java/dev/springboot4docs/ch_20_transactions/BankAccount.java" comments=false %}
```

The repository is intentionally ordinary:

```java
{% include-markdown "../../code/20-transactions/maven/src/main/java/dev/springboot4docs/ch_20_transactions/BankAccountRepository.java" comments=false %}
```

`InsufficientFundsException` is a runtime exception. That matters because Spring rolls back on unchecked exceptions by default:

```java
{% include-markdown "../../code/20-transactions/maven/src/main/java/dev/springboot4docs/ch_20_transactions/InsufficientFundsException.java" comments=false %}
```

## Declarative Transactions

The transfer service is where the transaction boundary belongs. It coordinates two account rows and one business rule:

```java
{% include-markdown "../../code/20-transactions/maven/src/main/java/dev/springboot4docs/ch_20_transactions/TransferService.java" comments=false %}
```

`transfer` uses plain `@Transactional`, which means `Propagation.REQUIRED`. If no transaction exists, Spring starts one. If a transaction already exists, the method joins it. The method loads both accounts, mutates the managed entities, and returns. There is no explicit `save` call after changing the balances because JPA dirty checking sees the changed managed entities and flushes them before commit.

The service also has `get` marked `@Transactional(readOnly = true)`. Treat that as a signal and an optimization, not as a security boundary. With Hibernate, read-only transactions can reduce dirty-checking work during flush. In some stacks, the read-only hint is also used by drivers, pools, or routing data sources to send reads to a replica. Do not depend on it to make accidental writes impossible in every database. Use it because it documents intent and lets the persistence provider avoid work.

`@Transactional` can be placed on a class or on a method. A class-level annotation is a default for all public methods. A method-level annotation overrides that default. The important runtime rule is that the outermost transactional call reached through the Spring proxy establishes the active transaction. If an outer `@Transactional` method calls an inner `@Transactional(propagation = REQUIRED)` method, the inner method joins the existing transaction; it does not get a second independent commit decision.

## Propagation

Propagation controls what happens when a transactional method is called while another transaction may already be active.

`REQUIRED` is the default and the right answer most of the time. It says: join the current transaction if one exists; otherwise start a new one. In the transfer method, that means debit and credit are part of the same database unit of work.

`REQUIRES_NEW` suspends any current transaction and starts an independent one. Its commit or rollback is separate from the caller's transaction. The sample includes a second transfer method using `REQUIRES_NEW` so we can prove that an inner transfer can commit even when the outer ledger operation fails afterward.

The remaining propagation modes are less common, but you should recognize them. `NESTED` creates a savepoint inside the current transaction when the transaction manager supports it. `SUPPORTS` joins a transaction when one exists but also runs without one. `NOT_SUPPORTED` suspends an existing transaction and runs non-transactionally. `NEVER` fails if a transaction exists. `MANDATORY` requires a transaction to already exist and fails when called without one.

The ledger service demonstrates why `REQUIRES_NEW` is powerful and dangerous:

```java
{% include-markdown "../../code/20-transactions/maven/src/main/java/dev/springboot4docs/ch_20_transactions/LedgerService.java" comments=false %}
```

`recordTransferThenFail` starts its own transaction because the method is annotated with `@Transactional`. It writes a marker row, calls `TransferService.transferRequiresNew`, and then throws. The marker row belongs to the outer transaction and rolls back. The transfer belongs to the inner `REQUIRES_NEW` transaction and commits before the outer method throws.

Use this sparingly. `REQUIRES_NEW` is appropriate for work that must survive a caller rollback, such as an audit write or an outbox-like record with a deliberately independent boundary. It is not a way to casually ignore failure. Once the inner transaction commits, the outer transaction cannot undo it.

## Isolation

Isolation controls what one transaction is allowed to observe while other transactions are running. Higher isolation gives stronger consistency and usually more locking or retry pressure.

The classic read phenomena are dirty reads, non-repeatable reads, and phantom reads. A dirty read sees data written by another transaction that has not committed. A non-repeatable read happens when the same row is read twice and another committed transaction changes it between reads. A phantom read happens when a repeated range query returns a different set of rows because another transaction inserted, deleted, or updated matching rows.

Spring exposes the usual isolation levels on `@Transactional(isolation = ...)`. `READ_UNCOMMITTED` allows dirty reads and is rarely appropriate. `READ_COMMITTED` prevents dirty reads and is the default in PostgreSQL. `REPEATABLE_READ` makes repeated reads of the same rows stable within a transaction, though exact phantom behavior depends on the database. `SERIALIZABLE` gives the strongest isolation by making concurrent transactions behave as if they ran one at a time, often by blocking or forcing retries.

Do not override isolation by habit. Start with the database default. Override it for a specific use case when you can name the anomaly you are preventing and you have tested the concurrency behavior on the database you actually deploy. Many business invariants are better protected with constraints, unique indexes, optimistic locking, or explicit locking queries than with globally stronger isolation.

## Rollback Rules

By default, Spring rolls back on `RuntimeException` and `Error`. Checked exceptions do not trigger rollback unless you ask for it:

```java
@Transactional(rollbackFor = IOException.class)
void importFile() throws IOException {
	// ...
}
```

That default is why `InsufficientFundsException` extends `RuntimeException`. When `withdraw` throws, the exception leaves the transactional method. Spring sees an unchecked exception and rolls back the whole transfer, including any in-memory entity changes that would otherwise have flushed.

If you catch an exception inside a transactional method and do not rethrow it, Spring sees a normal return and will try to commit. Either let the exception escape, throw a different unchecked exception, configure `rollbackFor`, or explicitly mark the current transaction rollback-only with `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`.

## Testing Transaction Boundaries

Use a full application test for transaction behavior. A JPA slice such as `@DataJpaTest` is useful for repository mapping, but slice tests are transactional by default and roll back each test method. That default is convenient, but it can mask the exact behavior you are trying to prove.

This chapter uses `@SpringBootTest` and manual cleanup. The test method itself is not transactional. It uses `TransactionTemplate` for setup and fresh reads, so each assertion observes committed database state from a separate transaction:

```java
{% include-markdown "../../code/20-transactions/maven/src/test/java/dev/springboot4docs/ch_20_transactions/TransferServiceIntegrationTest.java" comments=false %}
```

The happy-path test creates two accounts, calls `transfer`, and then reads both balances in a fresh transaction. That second select matters. It proves the service committed changes to the database, not just that two Java objects were changed in memory.

The insufficient-funds test also reads in a fresh transaction after the exception. This avoids a subtle testing mistake: after rollback, do not verify from stale objects you already loaded before the failure. Read again from the database and assert that Alice still has `10_000` cents and Bob still has `2_500`.

The `REQUIRES_NEW` test is the interesting one. `LedgerService` writes an outer marker row, calls the inner transfer, then throws. The assertion proves two things: the account balances changed because the inner transaction committed, and the marker row is missing because the outer transaction rolled back. That is the behavior `REQUIRES_NEW` promises.

The application config keeps Open Session in View off:

```yaml
{% include-markdown "../../code/20-transactions/maven/src/main/resources/application.yml" comments=false %}
```

## Five Foot-Guns

Self-invocation skips the proxy. Spring's default transaction support is AOP-based, which means the transactional behavior is applied when another object calls the Spring-managed proxy. If a method inside the same class calls `this.someTransactionalMethod()`, that call does not pass through the proxy, so the annotation on `someTransactionalMethod` is not applied. Move the method to another service, call through the proxied bean, or restructure the boundary so the public entry point is transactional.

Private and final methods cannot be advised in the way you expect. A private method is not part of the proxied public service contract, and final methods cannot be overridden by subclass-based proxies. Keep transaction annotations on public service methods. Private helper methods are fine, but they should assume the caller already established the transaction.

Catching exceptions can swallow the rollback signal. This is the bug where a method catches `InsufficientFundsException`, logs it, and returns an error value. Spring sees a normal return and commits unless the transaction was marked rollback-only. Inside a transactional method, catch exceptions only when you are going to translate and rethrow them, or when you explicitly know the transaction should still commit.

Transactional tests roll back by default. That default is excellent for many repository tests because each test cleans itself up. It is bad when the test is about commit, rollback, propagation, or visibility across transaction boundaries. For those tests, use `@SpringBootTest`, keep the test method non-transactional, seed and inspect state with `TransactionTemplate`, and clean up deliberately.

Open Session in View hides bad boundaries. When OSIV is enabled, Hibernate can keep loading lazy data after the service method has returned, often during JSON serialization. That spreads database access into the web layer and makes transaction boundaries vague. Keep `spring.jpa.open-in-view: false`, fetch the data a request needs inside the service transaction, and return plain response objects from the web boundary.

Run the chapter tests from the Maven project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

Chapter 21 moves from transaction boundaries to pagination and sorting: how to return a page of data, how to keep sort options explicit, and how to avoid expensive count queries when a screen only needs "load more" behavior.
