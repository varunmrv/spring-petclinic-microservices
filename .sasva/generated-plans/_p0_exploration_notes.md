# Exploration Notes: Spring PetClinic Microservices

## Project Overview

This is the **Spring PetClinic Microservices** project — a distributed, cloud-native version of the classic Spring PetClinic sample application. It demonstrates microservices architecture using Spring Cloud, Spring Boot 4.0.1, Spring AI, and related technologies. The GitHub issue #507 is at: https://github.com.mcas.ms/spring-petclinic/spring-petclinic-microservices/issues/507

---

## Directory Structure

```
spring-petclinic-microservices/
├── pom.xml                                  # Parent Maven POM (Spring Boot 4.0.1)
├── docker-compose.yml                       # Full stack Docker Compose
├── README.md
├── CONTRIBUTING.md
├── docker/                                  # Dockerfile and Grafana/Prometheus configs
├── docs/                                    # Architecture diagrams, screenshots
├── scripts/                                 # run_all.sh, chaos scripts
├── spring-petclinic-admin-server/           # Spring Boot Admin (port 9090)
├── spring-petclinic-api-gateway/            # Spring Cloud Gateway + AngularJS UI (port 8080)
├── spring-petclinic-config-server/          # Spring Cloud Config Server (port 8888)
├── spring-petclinic-customers-service/      # Owners + Pets domain (random port)
├── spring-petclinic-discovery-server/       # Eureka Service Registry (port 8761)
├── spring-petclinic-genai-service/          # Spring AI chatbot (random port)
├── spring-petclinic-vets-service/           # Veterinarians domain (random port)
└── spring-petclinic-visits-service/         # Visit records domain (random port)
```

---

## Config & Dependencies

### Parent POM (`pom.xml`)
- **Spring Boot**: 4.0.1
- **Spring Cloud**: 2025.1.0
- **Java**: 17
- **Key dependencies managed**: Spring Cloud BOM, Chaos Monkey 3.1.0, Jolokia 1.7.1, datasource-micrometer 2.0.1
- **Docker image prefix**: `springcommunity`
- **Build**: Maven with `buildDocker` profile using `spring-boot:build-image`

### Key Technology Stack
- **Spring Boot 4.0.1** — all microservices
- **Spring Cloud 2025.1.0** — Gateway, Config, Eureka, Circuit Breaker (Resilience4j)
- **Spring AI** — GenAI service with OpenAI/Azure OpenAI support
- **Spring Data JPA** — persistence layer (HSQLDB default, MySQL optional)
- **Micrometer + OpenTelemetry** — observability/tracing
- **Prometheus + Grafana** — metrics dashboards
- **Zipkin** — distributed tracing (port 9411)
- **AngularJS** — frontend (served by API Gateway)
- **WebFlux (Reactor)** — reactive programming in API Gateway

---

## Architecture Overview

The system follows a classic microservices pattern:

```
Browser (AngularJS) → API Gateway (port 8080)
                          ↓ (Spring Cloud Gateway routes)
                    ┌─────────────────────────────────┐
                    │  customers-service (owners/pets) │
                    │  visits-service (visit records)  │
                    │  vets-service (veterinarians)    │
                    │  genai-service (AI chatbot)      │
                    └─────────────────────────────────┘
                          ↑ (Eureka service discovery)
                    discovery-server (port 8761)
                    config-server (port 8888)
```

**Service Communication:**
- API Gateway uses **WebClient** (reactive) to call `customers-service` and `visits-service`
- GenAI service uses **RestClient** (synchronous) to call `customers-service` via Eureka discovery
- Circuit breaker pattern via **Resilience4j** wraps calls in `ApiGatewayController`

---

## Key Files & Their Roles

### visits-service
- `Visit.java` — JPA entity: `id`, `pet_id`, `visit_date` (Date/TIMESTAMP), `description` (VARCHAR 8192). Uses `@JsonFormat(pattern="yyyy-MM-dd")`. Has inner `VisitBuilder`.
- `VisitRepository.java` — Spring Data JPA: `findByPetId(int)`, `findByPetIdIn(Collection<Integer>)`
- `VisitResource.java` — REST controller:
  - `POST owners/*/pets/{petId}/visits` → creates visit (HTTP 201)
  - `GET owners/*/pets/{petId}/visits` → list visits for one pet
  - `GET pets/visits?petId=111,222` → bulk fetch visits for multiple pets (returns `Visits` record wrapping `List<Visit>`)
- `VisitResourceTest.java` — `@WebMvcTest` with `@MockitoBean` for repository; tests `GET /pets/visits?petId=111,222`

### customers-service
- `Owner.java` — JPA entity: `id`, `first_name`, `last_name`, `address`, `city`, `telephone`, `List<Pet> pets`
- `Pet.java` — JPA entity: `id`, `name`, `birth_date`, `type_id` (FK to PetType), `owner_id` (FK to Owner)
- `PetType.java` — JPA entity: `id`, `name`
- `OwnerResource.java` — REST: `POST /owners`, `GET /owners`, `GET /owners/{ownerId}`, `PUT /owners/{ownerId}`
- `PetResource.java` — REST: `GET /petTypes`, `POST /owners/{ownerId}/pets`, `PUT /owners/*/pets/{petId}`, `GET owners/*/pets/{petId}`
- `OwnerRequest.java` — record DTO for owner creation/update
- `PetRequest.java` — record DTO for pet creation/update
- `ResourceNotFoundException.java` — custom exception for 404 cases
- `OwnerEntityMapper.java` — maps `OwnerRequest` → `Owner` entity

### api-gateway
- `ApiGatewayController.java` — `GET /api/gateway/owners/{ownerId}` — fetches owner from customers-service, then fetches visits for all pet IDs from visits-service, merges them reactively with circuit breaker fallback
- `VisitsServiceClient.java` — WebClient-based client calling `http://visits-service/pets/visits?petId={petId}`
- `CustomersServiceClient.java` — WebClient-based client calling customers-service
- `OwnerDetails.java` — record DTO: `id, firstName, lastName, address, city, telephone, List<PetDetails> pets`; has `getPetIds()` helper
- `PetDetails.java` — record DTO: `id, name, birthDate, PetType type, List<VisitDetails> visits`
- `VisitDetails.java` — record DTO: `Integer id, Integer petId, String date, String description`
- `Visits.java` — record wrapping `List<VisitDetails> items`
- `FallbackController.java` — circuit breaker fallback endpoint

### genai-service
- `PetclinicChatClient.java` — `POST /chatclient` REST endpoint; uses Spring AI `ChatClient` with memory advisor and tools
- `PetclinicTools.java` — Spring AI `@Tool` annotated methods: `listOwners()`, `addOwnerToPetclinic()`, `listVets()`, `addPetToOwner()`
- `AIDataProvider.java` — calls `customers-service` via Eureka discovery + RestClient; uses VectorStore for vet similarity search
- `VectorStoreController.java` — manages vector store for vet data
- `AIBeanConfiguration.java` — Spring AI bean configuration (ChatMemory, etc.)

### vets-service
- `VetResource.java` — `GET /vets` with `@Cacheable("vets")` — returns all vets
- `Vet.java` — JPA entity with `List<Specialty> specialties`

---

## Code Patterns & Conventions

### Naming Conventions
- Controllers named `*Resource` (e.g., `VisitResource`, `OwnerResource`, `PetResource`, `VetResource`)
- DTOs in api-gateway are Java records
- Builder pattern used extensively (inner static `*Builder` classes)
- Package-private controller classes (no `public` modifier on class declaration)

### Error Handling
- `ResourceNotFoundException` in customers-service (custom exception for 404)
- Circuit breaker fallback in `ApiGatewayController` returns empty visits list
- GenAI service catches exceptions and returns user-friendly message

### Validation
- `@Valid` on request bodies
- `@Min(1)` on path variable IDs
- `@Size(max = 8192)` on visit description
- `@NotBlank`, `@Digits` on GenAI OwnerRequest

### Observability
- `@Timed("petclinic.visit")`, `@Timed("petclinic.owner")`, `@Timed("petclinic.pet")` on controllers
- Logback with `logback-spring.xml` in each service
- Micrometer + OpenTelemetry tracing

### Logging
- SLF4J Logger via `LoggerFactory.getLogger(ClassName.class)` in each controller
- Log statements: `log.info("Saving visit {}", visit)`, `log.info("Saving owner {}", ownerModel)`

---

## Data Layer

### visits-service Database Schema
**HSQLDB (default):**
```sql
CREATE TABLE visits (
  id          INTEGER IDENTITY PRIMARY KEY,
  pet_id      INTEGER NOT NULL,
  visit_date  DATE,
  description VARCHAR(8192)
);
CREATE INDEX visits_pet_id ON visits (pet_id);
```

**MySQL:**
```sql
CREATE TABLE IF NOT EXISTS visits (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pet_id INT(4) UNSIGNED NOT NULL,
  visit_date DATE,
  description VARCHAR(8192),
  FOREIGN KEY (pet_id) REFERENCES pets(id)
) engine=InnoDB;
```

**Note:** The `Visit` entity uses `@Temporal(TemporalType.TIMESTAMP)` but the DB schema uses `DATE` type — this is a potential inconsistency.

### customers-service Database Schema
**HSQLDB:**
- `types` table: `id`, `name`
- `owners` table: `id`, `first_name`, `last_name`, `address`, `city`, `telephone`
- `pets` table: `id`, `name`, `birth_date`, `type_id` (FK→types), `owner_id` (FK→owners)

### Seed Data
- HSQLDB and MySQL data.sql files exist for all three data services
- Default profile uses HSQLDB in-memory; `mysql` Spring profile switches to MySQL

---

## Testing Setup

- **Framework**: JUnit 5 + Spring Boot Test
- **Pattern**: `@WebMvcTest` for controller-layer tests (slice tests)
- **Mocking**: `@MockitoBean` (Spring Boot 4.x annotation replacing `@MockBean`)
- **MockMvc**: Used for HTTP-level assertions
- **Test profiles**: `@ActiveProfiles("test")` with `application-test.yml` (HSQLDB in-memory)
- **Coverage**: Currently only `VisitResourceTest` (visits-service) and `PetResourceTest` (customers-service) and `VetResourceTest` (vets-service) exist — minimal coverage

### Example Test Pattern (VisitResourceTest):
```java
@WebMvcTest(VisitResource.class)
@ActiveProfiles("test")
class VisitResourceTest {
    @Autowired MockMvc mvc;
    @MockitoBean VisitRepository visitRepository;
    
    @Test
    void shouldFetchVisits() throws Exception {
        given(visitRepository.findByPetIdIn(asList(111, 222))).willReturn(...);
        mvc.perform(get("/pets/visits?petId=111,222"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.items[0].id").value(1));
    }
}
```

---

## Build & Deploy

### Maven Build
- `./mvnw clean install` — standard build
- `./mvnw clean install -P buildDocker` — builds Docker images using `spring-boot:build-image`
- Supports `podman` via `-Dcontainer.executable=podman`
- Platform: `linux/amd64` default, configurable via `-Dcontainer.platform`

### Docker Compose
- `docker-compose.yml` orchestrates all services
- Uses `service_healthy` + `healthcheck` for startup ordering
- Services: config-server → discovery-server → (customers, vets, visits, genai, api-gateway) → admin-server
- Includes Zipkin, Prometheus, Grafana

### CI/CD
- GitHub Actions: `.github/workflows/maven-build.yml` (referenced in README badge)

### Running Locally
- Start order: Config Server → Discovery Server → other services
- `./scripts/run_all.sh` — starts infra via Docker, Java apps via `nohup java -jar`
- Chaos Monkey profile available for resilience testing

---

## Integration Points (Relevant to Issue #507)

Based on the codebase structure, issue #507 likely relates to one of these areas (the issue URL references the GitHub issue tracker):

### Key Integration Points for New Features:

1. **Visit CRUD operations** — `VisitResource.java` currently only has `POST` (create) and `GET` (read). **No `PUT` (update) or `DELETE` (delete) endpoints exist** for visits. This is a common gap.

2. **Visit data model** — `Visit.java` has `id`, `pet_id`, `visit_date`, `description`. Any new fields would require:
   - Schema migration (both HSQLDB and MySQL)
   - Entity update
   - DTO updates in api-gateway (`VisitDetails.java`)
   - GenAI DTO update (`VisitDetails.java` in genai-service)

3. **API Gateway aggregation** — `ApiGatewayController.getOwnerDetails()` merges owner + visits data. Changes to visit structure propagate here.

4. **VisitsServiceClient** — in api-gateway, calls `GET pets/visits?petId={petId}`. New visit endpoints would need corresponding client methods.

5. **GenAI Tools** — `PetclinicTools.java` currently has no visit-related tools. Adding visit management via AI would require new `@Tool` methods in `PetclinicTools` and corresponding `AIDataProvider` methods.

6. **Frontend (AngularJS)** — visit-related UI is in `spring-petclinic-api-gateway/src/main/resources/static/scripts/`. Any new visit features need frontend updates.

7. **Owner search** — `OwnerResource.findAll()` returns ALL owners with no filtering/search. Issue #507 may relate to adding search/filter capability.

---

## Potential Risks & Complexities

1. **Dual database support** — Any schema changes must be applied to BOTH `hsqldb` and `mysql` schema/data SQL files.

2. **Reactive vs. Synchronous** — API Gateway uses reactive WebClient/Reactor; backend services use synchronous Spring MVC. Mixing paradigms requires care.

3. **DTO duplication** — `VisitDetails`, `PetDetails`, `OwnerDetails` exist in BOTH `api-gateway` and `genai-service` as separate classes. Changes to the data model must be synchronized across both.

4. **No DELETE endpoint for visits** — The `VisitRepository` extends `JpaRepository` (which has `deleteById`), but no REST endpoint exposes it.

5. **No UPDATE endpoint for visits** — Similarly, no `PUT /owners/*/pets/{petId}/visits/{visitId}` exists.

6. **Visit date type inconsistency** — `Visit.java` uses `@Temporal(TemporalType.TIMESTAMP)` but schema uses `DATE`. The `@JsonFormat(pattern="yyyy-MM-dd")` masks this but could cause issues.

7. **Circuit breaker fallback** — The `getOwnerDetails` circuit breaker returns empty visits on failure. Any new visit endpoints need similar resilience patterns.

8. **GenAI service has no visit tools** — `PetclinicTools` can list owners, add owners, list vets, add pets — but cannot query or create visits. This is a functional gap.

9. **Test coverage is minimal** — Only slice tests exist; no integration tests. New features should include both unit and integration tests.

10. **Spring Boot 4.0.1** — Very recent version; `@MockBean` replaced by `@MockitoBean`, and other API changes may affect implementation patterns.
