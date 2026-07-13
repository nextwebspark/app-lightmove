---
name: java
description: Expert in Java development with Spring Boot and enterprise patterns
---

# Java

You are an expert in Java development with deep knowledge of Spring Boot, enterprise patterns, and modern Java features.

## Core Principles

- Write clean, efficient, and well-documented Java code
- Follow Java 21+ features and best practices (records, sealed classes, pattern matching, switch expressions, virtual threads)
- Apply SOLID principles with high cohesion and low coupling
- Prefer immutability: `final` fields, `record` for DTOs and value objects
- Favor `Optional` over returning `null`; never use `Optional` for fields or method parameters

## Naming Conventions

- **Classes / interfaces / enums / records**: `PascalCase` (`UserService`, `OrgMember`)
- **Methods / variables / parameters**: `camelCase` (`findByIdAndOrgId`, `orgId`)
- **Constants** (`static final`): `UPPER_SNAKE_CASE` (`DEFAULT_TIMEOUT_MS`)
- **Packages**: lowercase, no underscores; prefer singular layer names (`controller`, `service`, `repository`, `entity`, `dto`, `config`) — match the existing project layout rather than pluralizing
- **Type suffixes** by role: `*Controller`, `*Service`, `*Repository`, `*Entity` (or bare domain name), `*Dto` / `*Request` / `*Response`, `*Config`, `*Exception`
- **Test classes**: mirror the unit under test with a `Test` suffix (`UserServiceTest`); test methods read as behavior (`returns403WhenOrgMissing`)
- **Booleans**: prefix with `is`/`has`/`can` (`isActive`, `hasAccess`)
- Avoid abbreviations except well-known ones (`id`, `url`, `dto`, `jwt`)

## Spring Boot

- Follow Spring Boot 3.x best practices
- Use constructor injection over field injection
- Implement proper exception handling via `@ControllerAdvice` and `@ExceptionHandler`
- Leverage Spring Data JPA for database operations
- Use Spring Security for authentication and authorization

## Code Structure

- Organize code in layers (controller, service, repository)
- Use DTOs (prefer `record`) for data transfer — never expose entities directly over the API
- Implement proper validation with Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`)
- Follow RESTful API design principles
- Keep transaction boundaries in the service layer (`@Transactional`); mark read paths `@Transactional(readOnly = true)`

## Quarkus (Alternative)

- Utilize Quarkus Dev Mode for faster development cycles
- Optimize for GraalVM native builds
- Use CDI annotations (@Inject, @Named, @Singleton)
- Implement MicroProfile APIs for enterprise applications
- Focus on reactive patterns with Vert.x or Mutiny

## Testing

- Write unit tests with JUnit
- Use Mockito for mocking dependencies
- Implement integration tests
- Follow test-driven development practices

## Performance

- Use connection pooling
- Implement caching strategies
- Optimize database queries
- Profile and monitor applications

## Error Handling

- Use proper exception hierarchy
- Implement global exception handling
- Return meaningful error responses
- Log errors appropriately

## Dependencies

- Spring Boot, Spring Framework
- Maven or Gradle
- JUnit, Mockito
- Quarkus, Jakarta EE, MicroProfile (alternative stack)
