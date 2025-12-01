# Irkalla - AI Assistant Context Guide

## Project Overview

**Irkalla** is a Spring Boot integration service that synchronizes stop place data between the Norwegian Stop Place Register (Tiamat) and the Routes database (Chouette). It monitors changes in Tiamat via GraphQL API and replicates those changes to Chouette via REST API, while also sending notifications to Nabu.

### Project Metadata
- **Language**: Java 21
- **Build Tool**: Maven
- **Framework**: Spring Boot with Apache Camel 4.4.5
- **License**: EUPL v1.2 (European Union Public License)
- **Organization**: Entur (Norwegian public transport operator)
- **Repository**: https://github.com/entur/irkalla

## Architecture

### Core Components

1. **Integration Layer** - Apache Camel routes for integration patterns
2. **Data Sources**:
   - **Tiamat**: Stop place registry (GraphQL API)
   - **Chouette**: Routes database (REST API)
   - **Nabu**: Notification service

3. **Event Processing**:
   - Polls Tiamat for stop place changes
   - Transforms data to NeTEx format
   - Synchronizes to Chouette
   - Publishes change events via Kafka and Google Pub/Sub

### Key Technologies

- **Spring Boot** - Application framework
- **Apache Camel** - Integration routing and mediation
- **Google Cloud Platform**:
  - Cloud Storage (GCS) for blob storage
  - Pub/Sub for event messaging
- **Apache Kafka** - Event streaming
- **Apache Avro** - Data serialization
- **NeTEx** - Public transport data exchange format
- **Kubernetes** - Container orchestration (optional)
- **OAuth2** - Security and authorization

## Project Structure

```
irkalla/
├── src/main/java/org/rutebanken/irkalla/
│   ├── IrkallaApplication.java          # Main Spring Boot application
│   ├── config/                          # Configuration classes
│   │   ├── AuthorizationConfig.java     # Security configuration
│   │   ├── CamelConfig.java            # Camel context setup
│   │   └── OAuth2Config.java           # OAuth2 settings
│   ├── domain/                          # Domain models
│   │   ├── CrudEvent.java              # CRUD event representation
│   │   └── EntityChangedEvent.java     # Entity change events
│   ├── repository/                      # Data access layer
│   │   ├── BlobStoreRepository.java    # Interface for blob storage
│   │   ├── GcsBlobStoreRepository.java # GCS implementation
│   │   └── InMemoryBlobStoreRepository.java
│   ├── routes/                          # Camel route builders
│   │   ├── tiamat/                     # Tiamat integration routes
│   │   │   ├── TiamatPollForStopPlaceChangesRouteBuilder.java
│   │   │   ├── TiamatStopPlaceChangedRouteBuilder.java
│   │   │   ├── StopPlaceDao.java       # Data access for stop places
│   │   │   └── graphql/                # GraphQL queries
│   │   ├── chouette/                   # Chouette integration routes
│   │   │   ├── ChouetteStopPlaceUpdateRouteBuilder.java
│   │   │   └── ChouetteStopPlaceDeleteRouteBuilder.java
│   │   ├── kafka/                      # Kafka event publishing
│   │   ├── notification/               # Notification routes
│   │   └── syncstatus/                 # Sync status tracking
│   ├── security/                        # Security implementations
│   └── util/                           # Utility classes
├── src/main/avro/
│   └── StopPlaceChangelogEvent.avsc    # Avro schema for events
├── src/main/resources/                  # Configuration files
├── src/test/                           # Test files
├── helm/                               # Kubernetes Helm charts
├── terraform/                          # Infrastructure as code
├── Dockerfile                          # Container image definition
├── pom.xml                             # Maven build configuration
└── .github/workflows/push.yml          # CI/CD pipeline

**Total**: ~46 Java source files, 27 Camel routes
```

## Key Dependencies

### Entur Libraries
- `entur-helpers` (5.50.0) - Organization, storage, Pub/Sub, OAuth2
- `netex-java-model` (2.0.15) - NeTEx data model
- `netex-parser-java` (3.1.66) - NeTEx parsing

### Apache Camel Components
- `camel-spring-boot-starter` - Core Camel integration
- `camel-http-starter` - HTTP client
- `camel-google-pubsub-starter` - Google Pub/Sub
- `camel-kafka-starter` - Kafka integration
- `camel-quartz-starter` - Scheduling
- `camel-kubernetes-starter` - Kubernetes support
- `camel-master-starter` - Leader election

### Other Key Dependencies
- Spring Security (OAuth2)
- Spring Actuator (monitoring/metrics)
- Micrometer + Prometheus (metrics export)
- Apache Avro (1.12.1)
- Confluent Kafka (6.2.15)
- JTS2GeoJSON (0.18.1) - Geometry conversion
- Testcontainers (testing)

## Data Flow

### Stop Place Synchronization Flow

1. **Poll Tiamat** → TiamatPollForStopPlaceChangesRouteBuilder
   - Periodically queries Tiamat GraphQL API for changes
   - Retrieves stop place updates in batches (default: 1000)
   - Fetches data in NeTEx publication delivery format

2. **Process Changes** → TiamatStopPlaceChangedRouteBuilder
   - Parses NeTEx XML data
   - Extracts stop place entities
   - Determines CRUD operation (CREATE/UPDATE/REMOVE/DELETE)

3. **Update Chouette** → ChouetteStopPlaceUpdateRouteBuilder
   - Sends updates to Chouette REST API
   - Handles full sync or delta sync operations
   - Aggregates messages for batch processing
   - 5-minute socket timeout for long operations

4. **Delete Handling** → ChouetteStopPlaceDeleteRouteBuilder
   - Processes stop place deletions
   - Sends deletion requests to Chouette

5. **Event Publishing**
   - **Kafka**: Publishes `StopPlaceChangelogEvent` (Avro schema)
   - **Pub/Sub**: Sends notifications to Google Cloud Pub/Sub
   - **Nabu**: Notifies downstream systems of changes

6. **Sync Status Tracking**
   - Stores sync state in blob storage (GCS or in-memory)
   - Tracks last sync timestamps
   - Supports resumable synchronization

### Event Schema

```json
{
  "stopPlaceId": "NSR:StopPlace:xxxxx",
  "stopPlaceVersion": 1,
  "stopPlaceChanged": 1638360000000,
  "eventType": "CREATE|UPDATE|REMOVE|DELETE"
}
```

## Configuration

### Key Configuration Properties

```properties
# Server
server.port=10501
server.admin.port=11501
server.context-path=/irkalla/

# External Services
tiamat.url=http://tiamat:2888
chouette.url=http://localhost:8080
etcd.url=http://etcd-client:2379/v2/keys/prod/irkalla

# Security (choose one)
irkalla.security.user-context-service=full-access    # No auth (dev only)
irkalla.security.user-context-service=token-based    # OAuth2 token auth

# Kubernetes
rutebanken.kubernetes.enabled=false

# Synchronization
chouette.sync.stop.place.autoStartup=true
sync.stop.place.batch.size=1000

# Timeouts
irkalla.shutdown.timeout=45
```

## Build & Run

### Build
```bash
mvn clean install
```

### Run Locally
```bash
mvn spring-boot:run
```

### Docker Build
```bash
mvn clean install
docker build -t irkalla:latest .
```

### Run with Docker
```bash
docker run -p 10501:10501 -p 11501:11501 irkalla:latest
```

## CI/CD Pipeline

GitHub Actions workflow (`.github/workflows/push.yml`):

1. **Maven Verify** - Builds and tests on Ubuntu 24.04 with Java 21
2. **Docker Lint** - Lints Dockerfile
3. **Docker Build** - Builds container image
4. **Docker Push** - Pushes to registry (master branch only)
5. **Docker Scan** - Security scanning

## Deployment

### Kubernetes/Helm
- Helm charts located in `helm/irkalla/`
- Supports leader election for clustered deployments
- Uses Kubernetes ConfigMaps and Secrets

### Infrastructure
- Terraform configurations in `terraform/`
- Google Cloud Platform resources

## Security

### Authentication & Authorization
- OAuth2 token-based authentication
- Integration with Entur permission-store-proxy
- Two modes:
  - `full-access`: All authenticated users (development)
  - `token-based`: OAuth2 token validation (production)

### Security Features
- Spring Security integration
- JWT token validation
- Role-based access control

## Monitoring & Observability

### Actuator Endpoints
- Health checks: `/actuator/health`
- Metrics: `/actuator/metrics`
- Prometheus: `/actuator/prometheus`

### Logging
- Logback with Logstash encoder
- JSON structured logging
- MDC (Mapped Diagnostic Context) support
- Conditional logging with Janino

### Metrics
- Micrometer registry
- Prometheus integration
- Custom metrics for sync operations

## Development Guidelines

### Code Style
- Java 21 features available
- EUPL license header in all source files
- Minimal comments (self-documenting code)

### Testing
- JUnit 5
- Spring Boot Test
- Camel Test (Spring JUnit 5)
- Testcontainers for integration tests
- Maven Surefire with 500MB heap

### Common Development Tasks

#### Adding a New Route
1. Extend `BaseRouteBuilder`
2. Add `@Component` annotation
3. Implement `configure()` method
4. Define Camel routes using DSL

#### Modifying Avro Schema
1. Edit `src/main/avro/StopPlaceChangelogEvent.avsc`
2. Run `mvn clean generate-sources`
3. Generated classes in `target/generated-sources/`

#### Adding Configuration
1. Add property to application configuration
2. Inject with `@Value` annotation
3. Document in README.md

## Troubleshooting

### Common Issues

**Synchronization not starting**
- Check `chouette.sync.stop.place.autoStartup=true`
- Verify Tiamat and Chouette URLs are accessible
- Check authentication configuration

**Timeout errors with Chouette**
- Default socket timeout: 5 minutes
- Check network connectivity
- Review batch size (default: 1000)

**Kubernetes leader election**
- Ensure `rutebanken.kubernetes.enabled=true`
- Check pod permissions for leader election

**GCS access issues**
- Verify service account credentials
- Check GCS bucket permissions
- Confirm storage helper configuration

## Related Projects

- **Tiamat** - Stop place registry (https://github.com/entur/tiamat)
- **Chouette** - Routes database (https://github.com/entur/chouette)
- **Nabu** - Notification service (https://github.com/entur/nabu)

## Useful Commands

```bash
# Build without tests
mvn clean install -DskipTests

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Generate Avro sources
mvn avro:schema

# Check dependencies
mvn dependency:tree

# Security audit
mvn org.owasp:dependency-check-maven:check
```

## API Endpoints

### Admin REST API
- Defined in `AdminRestRouteBuilder`
- Exposed via Camel Servlet
- OpenAPI documentation available

### Health & Metrics
- `/actuator/health` - Application health
- `/actuator/info` - Application info
- `/actuator/prometheus` - Prometheus metrics

## Notes for AI Assistants

- This is an **integration service**, not a user-facing application
- Primary role: **data synchronization** between systems
- Built on **enterprise integration patterns** (Apache Camel)
- NeTEx format is crucial - it's an EU standard for public transport data
- Changes should maintain **exactly-once delivery** semantics
- Consider **idempotency** when modifying sync logic
- Test with **real GraphQL/REST endpoints** or use mocks
- Sync status persistence is critical for resumability
