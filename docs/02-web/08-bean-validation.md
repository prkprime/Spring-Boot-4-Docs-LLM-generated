# Bean Validation 3.1

Validation belongs at the boundary of a web application. A controller should not accept a half-valid request and hope that a service, repository, database constraint, or downstream API catches it later. Those layers still need their own defenses, but the HTTP edge is where you can reject bad input early, consistently, and close to the client that sent it.

Bean Validation gives you a declarative vocabulary for that boundary. Instead of writing `if` statements in every controller method, you put constraints on the request type. The record becomes the single source of truth for the shape and basic rules of that request. Spring MVC reads JSON into the record, Bean Validation checks the annotations, and the controller method only runs when the object is valid.

Spring Boot 4 uses Jakarta Bean Validation 3.1, part of the Jakarta EE 11 generation. The package name is `jakarta.validation.*`, including `jakarta.validation.constraints.*`.

!!! warning "Jakarta, not javax"
    Old Spring Boot 2 articles often show `javax.validation.Valid` and `javax.validation.constraints.NotBlank`. Those imports are from the Java EE era. In Spring Boot 4 and Spring Framework 7, use `jakarta.validation.Valid` and `jakarta.validation.constraints.*`.

## The Constraint Zoo

The constraints you will use most often are small and readable:

- `@NotNull` means the value must be present, but an empty string is still present.
- `@NotBlank` means a string must contain at least one non-whitespace character.
- `@NotEmpty` means a string, collection, map, or array cannot be null or empty.
- `@Size` checks string length, collection size, map size, or array length.
- `@Min` and `@Max` check numeric lower and upper bounds.
- `@Email` checks that a string looks like an email address.
- `@Pattern` applies a regular expression.
- `@Past` and `@Future` check dates and times.
- `@Positive` and `@Negative` check numeric sign.

The important distinction for beginners is `@NotNull` versus string-specific constraints. `@NotNull String name` allows `""`. `@NotEmpty String name` rejects `""` but allows `"   "`. `@NotBlank String name` rejects both. For human-entered names, titles, and labels, `@NotBlank` is usually the right starting point.

Numeric constraints also have two families. `@Min(1)` and `@Max(100)` compare against whole-number bounds and are easy to read for ages, counts, page numbers, and quantities. `@Positive`, `@PositiveOrZero`, `@Negative`, and `@NegativeOrZero` express sign without hard-coding a specific bound. For decimal ranges, Bean Validation also has `@DecimalMin` and `@DecimalMax`, which are useful when the boundary is not an integer.

`@Email` deserves a practical warning. It checks email syntax; it does not prove that the mailbox exists, that the domain accepts mail, or that the user owns the address. That is still useful. It catches obvious client mistakes before your application stores them, but account verification is a separate workflow.

`@Pattern` is the escape hatch for text that has a local format. Use it for simple, stable patterns such as a short code, a loose phone-number shape, or an external id format. Avoid turning a large business rule into an unreadable regular expression. If the rule needs a name, tests, or several steps, a custom constraint is clearer.

## A Signup Request

This chapter uses a small signup API. The request is a Java record with constraints on its components:

```java
{% include-markdown "../../code/08-bean-validation/maven/src/main/java/dev/springboot4docs/ch_08_bean_validation/SignupRequest.java" comments=false %}
```

Read the record one component at a time. `name` must be non-blank, and it must be between 2 and 50 characters. `email` must not be null and must pass the email constraint. `age` is an `int`, so it is never null; `@Min(13)` and `@Max(120)` define the accepted range. `phone` is optional in this example because there is no `@NotNull` or `@NotBlank`, but when it is present it must match the regular expression.

That optional phone rule is worth noticing. Most Bean Validation constraints treat `null` as valid unless the constraint is specifically about presence. This lets you combine rules deliberately: `@NotBlank @Pattern(...)` means required and formatted, while `@Pattern(...)` alone means optional but formatted when present.

The controller turns validation on with `@Valid`:

```java
{% include-markdown "../../code/08-bean-validation/maven/src/main/java/dev/springboot4docs/ch_08_bean_validation/SignupController.java" comments=false %}
```

`@RequestBody` says where the value comes from. Spring MVC reads the JSON body into `SignupRequest` using the JSON message converter from chapter 7. `@Valid` says the created object must be validated before the method is called. If validation fails, Spring MVC raises `MethodArgumentNotValidException` instead of entering `signup`.

The successful response is intentionally small:

```java
{% include-markdown "../../code/08-bean-validation/maven/src/main/java/dev/springboot4docs/ch_08_bean_validation/SignupResponse.java" comments=false %}
```

The same constraint annotations also work on method parameters such as `@RequestParam` and `@PathVariable`. For example, a real listing endpoint might put `@Min(1)` on a `page` query parameter or `@Positive` on an id path variable. The principle is the same: keep the boundary rule next to the value being bound.

```java
@GetMapping("/search")
List<Result> search(@RequestParam @NotBlank String q, @RequestParam @Min(1) int page) {
	// ...
}
```

Request-body validation and parameter validation fail at the same boundary, but they are represented by different Spring exceptions. A bad JSON record usually produces `MethodArgumentNotValidException`. A bad simple method parameter may produce a handler-method validation exception. Chapter 9 builds a single error story across those cases. This chapter keeps the handler narrow so the basic Bean Validation flow stays visible.

One more boundary detail: validation happens after JSON binding. If the client sends invalid JSON, or sends `"age": "five"` for an `int`, Jackson fails before Bean Validation gets a complete `SignupRequest`. Bean Validation answers "is this Java value acceptable?" It does not replace JSON parsing, type conversion, authentication, authorization, or database constraints.

## Custom Constraints

Built-in constraints cover a lot, but applications often have one rule that deserves a name. A password policy is a good example. You could stack several annotations or write imperative code in the controller, but `@StrongPassword` communicates the rule directly at the DTO boundary.

A custom constraint has two pieces: an annotation marked with `@Constraint`, and a `ConstraintValidator` implementation that contains the rule.

```java
{% include-markdown "../../code/08-bean-validation/maven/src/main/java/dev/springboot4docs/ch_08_bean_validation/StrongPassword.java" comments=false %}
```

`@Target` controls where the annotation can be used. This sample includes `RECORD_COMPONENT`, which matters because the DTOs are records. `@Retention(RUNTIME)` lets the validation engine see the annotation at runtime. `@Constraint(validatedBy = StrongPasswordValidator.class)` connects the annotation to the validator.

The `message`, `groups`, and `payload` elements are part of the Bean Validation constraint contract. Even if you only care about `message` today, include all three when you define a real constraint.

The validator implements the rule:

```java
{% include-markdown "../../code/08-bean-validation/maven/src/main/java/dev/springboot4docs/ch_08_bean_validation/StrongPasswordValidator.java" comments=false %}
```

`ConstraintValidator<StrongPassword, String>` says this validator handles `@StrongPassword` on `String` values. The `isValid` method rejects nulls, then checks for length, a digit, an uppercase letter, and a symbol. Some custom validators allow null and ask callers to add `@NotNull` separately. This one treats a missing password as invalid because a password change without a new password is never useful.

The request record can now use the custom annotation like any built-in constraint:

```java
{% include-markdown "../../code/08-bean-validation/maven/src/main/java/dev/springboot4docs/ch_08_bean_validation/PasswordChangeRequest.java" comments=false %}
```

That is the payoff. The controller does not know the password policy. The request type says what is acceptable, and the validator owns the detailed implementation.

Custom constraints are best when the rule appears in more than one place or when the rule has domain meaning. `@StrongPassword`, `@ValidSku`, `@AllowedCountryCode`, and `@BusinessHours` are names a reader can understand quickly. A one-off length check should stay as `@Size`; a domain rule that people discuss by name should usually become its own constraint.

## Validation Groups

Validation groups let you apply only a subset of constraints in a particular flow. You define marker interfaces, attach constraints to those groups, and select a group with `@Validated`.

```java
interface Create {
}

record AccountRequest(
		@NotBlank(groups = Create.class) String name,
		@Email String email) {
}

@PostMapping("/accounts")
void create(@Validated(Create.class) @RequestBody AccountRequest request) {
}
```

Groups are useful when create and update requests share a type but not all rules. Do not reach for them too early. Separate request DTOs are often clearer than a single type with several group-specific interpretations.

## Cross-Field Rules

Bean Validation can also express rules involving more than one field. For a small derived check, put `@AssertTrue` on a boolean method such as `isEndDateAfterStartDate()`. For reusable domain rules, create a class-level constraint annotation and a validator for the whole record or class. The imports are still Jakarta imports: `jakarta.validation.AssertTrue`, `jakarta.validation.Constraint`, and `jakarta.validation.ConstraintValidator`. If an article uses `javax.validation`, translate it before copying anything into a Spring Boot 4 project.

## Error Responses for Now

By default, validation failures become a 400 response, but the exact body is not the contract we want to teach yet. Chapter 9 is dedicated to structured errors and replaces this chapter's simple advice with an RFC 9457 `ProblemDetail` handler. For now, the advice only proves how `MethodArgumentNotValidException` exposes field errors.

```java
{% include-markdown "../../code/08-bean-validation/maven/src/main/java/dev/springboot4docs/ch_08_bean_validation/ValidationErrorAdvice.java" comments=false %}
```

The handler pulls `getFieldErrors()` from the binding result and returns a compact JSON body:

```json
{
  "errors": [
    { "field": "email", "message": "must be a well-formed email address" }
  ]
}
```

This is good enough for the chapter because it keeps attention on validation. It is not the final error design for the guide.

## Controller Tests

The tests use Spring Boot 4's MVC slice and `MockMvcTester`:

```java
{% include-markdown "../../code/08-bean-validation/maven/src/test/java/dev/springboot4docs/ch_08_bean_validation/SignupControllerWebMvcTest.java" comments=false %}
```

`@WebMvcTest(SignupController.class)` loads the selected controller and MVC infrastructure without starting a server. The valid signup test posts JSON to `/signup`, expects `200 OK`, and checks that the response body contains the submitted name.

The invalid signup test sends four bad fields at once: missing `name`, bad `email`, `age` below the minimum, and a `phone` value that does not match the pattern. The assertion checks the field names in the error body instead of depending on exact message wording. That is usually the better test boundary because validation providers can vary message text.

The password tests exercise the custom constraint. `"password"` is rejected because it is short and has no uppercase letter, digit, or symbol. `"Correct99!"` passes and the endpoint returns `204 No Content`.

Run the tests from the chapter project:

=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```

## Run It

Start the application:

=== "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```

Post a valid signup:

```bash
curl -i -X POST http://localhost:8080/signup \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Ava Patel",
    "email": "ava@example.com",
    "age": 31,
    "phone": "+1 555 0101"
  }'
```

The response should be `200 OK` with a welcome message.

Now post an invalid signup:

```bash
curl -i -X POST http://localhost:8080/signup \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "not-an-email",
    "age": 5,
    "phone": "abc"
  }'
```

This returns `400 Bad Request` with field errors for `name`, `email`, `age`, and `phone`.

Try the password endpoint:

```bash
curl -i -X POST http://localhost:8080/password \
  -H 'Content-Type: application/json' \
  -d '{ "newPassword": "Correct99!" }'
```

The success response is `204 No Content`. Change the password to `"password"` and the same endpoint returns a validation error.

Validation keeps bad input out of the controller. Chapter 9 takes the next step: turning those failures into consistent `ProblemDetail` responses that clients can rely on across the whole API.
