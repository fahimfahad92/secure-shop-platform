# 21 — Unscoped `JpaRepository` methods on owner-scoped repositories (ADR-0004)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-SEC |
| Service | order, user |

**Where:** `backend/order-service/.../repository/OrderRepository.java`, `backend/user-service/.../repository/ProfileRepository.java`

**What:** `OrderRepository extends JpaRepository<Order, Long>`, so `findById`, `findAll`, `findAllById`, `getReferenceById`, `existsById`, `deleteById` and `count` are callable without `userSub`. `ProfileRepository extends JpaRepository<Profile, String>`: `findById` takes the owner's `sub`, but `findAll` and `count` don't. Nothing checks that a new derived or `@Query` method takes the owner.

**Why it matters:** ADR-0004 puts ownership in the query. One autocomplete of an inherited method, or a new query without `userSub`, is a BOLA that compiles and passes the existing per-endpoint tests.

**Fix direction:**

1. **Narrow both repositories** to `Repository<T, ID>` and declare only what the service uses (Spring Data "selectively exposing CRUD methods": a declared method with a CRUD signature is backed by `SimpleJpaRepository`; an undeclared one doesn't exist, so `findById(id)` / `findAll()` stop compiling).

   ```java
   public interface OrderRepository extends Repository<Order, Long> {
       Order save(Order order);
       void delete(Order order);
       List<Order> findByUserSub(String userSub);
       Optional<Order> findByIdAndUserSub(Long id, String userSub);
   }

   public interface ProfileRepository extends Repository<Profile, String> {
       Profile save(Profile profile);
       void delete(Profile profile);
       /** The id is the Keycloak {@code sub}, so this lookup is already owner-scoped. */
       Optional<Profile> findById(String keycloakSub);
   }
   ```

   - `OrderService` and `ProfileService` need no change: they already call only these methods. `delete(entity)` after the scoped lookup stays; no `deleteByIdAndUserSub` needed
   - Drops `existsByUsername`, closing #10 too
   - Test cleanup: `OrderOwnershipTest` and `UserControllerSecurityTest` call `deleteAll()` in `@BeforeEach`. Replace with `@Sql(statements = "DELETE FROM orders")` / `"DELETE FROM profiles"` (or `JdbcTemplate`). Don't add `deleteAll` back to the interface. Mockito tests (`OrderServiceTest`, `ProfileServiceTest`) mock only kept methods, unaffected
   - Admin access later gets its own `AdminOrderRepository` (ADR-0004 migration path), never a wider `OrderRepository`

2. **ArchUnit integration** (stops new unscoped methods; narrowing only removes inherited ones)

   - Dependency in order-service and user-service `pom.xml`: `com.tngtech.archunit:archunit-junit5`, `test` scope. Pin the latest version when adding it
   - `OwnershipArchitectureTest` per service (`@AnalyzeClasses(packages = "com.fahim.orderservice")`, `@ArchTest` rules), runs in `mvn verify` with the other tests, so CI fails the build
   - Rule 1, allowlist: every method declared on `OrderRepository` except `save` and `delete` has a `String` parameter named `userSub` (needs `-parameters`, on by default with `spring-boot-starter-parent`; otherwise match by a `@Param("userSub")` or a naming convention). User Service: every method on `ProfileRepository` except `save`/`delete` takes the `sub` as its first `String` parameter
   - Rule 2: owner-scoped repositories (`OrderRepository`, `ProfileRepository`) must not be assignable to `CrudRepository` / `JpaRepository`, so nobody widens them back
   - Rule 3: only classes in `..service..` may depend on `..repository..` (controllers can't bypass the scoped service methods)
   - Limit: checks signatures, not whether the JPQL actually uses `userSub`. The cross-user test (#20) covers that

3. **Not chosen:** custom base repository that injects the owner (hidden request-scoped coupling in the data layer); Hibernate `@Filter` (must be enabled per session, no native SQL); Postgres RLS (needs per-service roles, #6, first; stays ADR-0004's migration path).

**When done:** ADR-0004 → move items 1–2 of "Scheduled hardening" into Decision outcome / Verification with the test name; reword the "Unscoped repository methods are callable today" residual risk and the Scope paragraph; drop the "until step 2" clause from the one-minute answer.
