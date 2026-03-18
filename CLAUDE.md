# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Irkalla is a Spring Boot + Apache Camel integration service that syncs stop place data from **Tiamat** (stop place registry, REST/NeTEx) to **Chouette** (routes DB, REST), publishes change events to **Kafka** and **Google Pub/Sub**, and notifies **Nabu** via Pub/Sub.

## Commands

```bash
# Build and test
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Run locally
mvn spring-boot:run

# Run a single test class
mvn test -Dtest=ChouetteStopPlaceUpdateRouteBuilderTest

# Run a single test method
mvn test -Dtest=ChouetteStopPlaceUpdateRouteBuilderTest#testUpdateStopPlaces

# Regenerate Avro sources after schema changes
mvn clean generate-sources
```

## Architecture

### Route Structure

All Camel routes extend `BaseRouteBuilder`, which configures:
- Exponential backoff error handling (max 3 redeliveries by default)
- Global PubSub header propagation interceptors (both inbound and outbound)
- Utilities for handling PubSub acknowledgment across aggregation boundaries

Routes are discovered as Spring `@Component` beans. Each `RouteBuilder` calls `super.configure()` first.

### Sync Flow via Pub/Sub Queues

The sync pipeline is driven by messages on `ChouetteStopPlaceSyncQueue` (not direct HTTP calls). The flow:

1. **Quartz triggers** (`chouette-synchronize-stop-places-delta-quartz` / `*-full-quartz`) publish to `ChouetteStopPlaceSyncQueue` with a `HEADER_SYNC_OPERATION` header (`DELTA`, `FULL`, or `DELETE_UNUSED`).
2. **`ChouetteStopPlaceUpdateRouteBuilder`** consumes from `ChouetteStopPlaceSyncQueue` via a `master:lock` prefix (Kubernetes leader election), aggregates up to 100 messages, merges them by priority (DELETE_UNUSED > FULL > DELTA), then routes to sync logic.
3. Each batch fetches NeTEx from Tiamat (`direct:processChangedStopPlacesAsNetex`), paginating via `HEADER_NEXT_BATCH_URL` populated from the HTTP `Link` header. Each page posts to Chouette and re-queues to `ChouetteStopPlaceSyncQueue` for the next page.
4. If Chouette returns HTTP 423 (locked/busy), the route delays and re-queues rather than failing.

### Event-Driven Change Processing

Tiamat change events (from Pub/Sub) flow through:
- `TiamatStopPlaceChangedRouteBuilder` — looks up current stop state via `StopPlaceDao`, determines if the change is effective (change time is in the past), sends a `CrudEvent` to Nabu's `CrudEventQueue`, and triggers a Chouette sync via `ChouetteStopPlaceSyncQueue`.
- DELETE events bypass the lookup and go directly to `ChouetteStopPlaceDeleteQueue`.

### Header-Driven Routing

`Constants.java` defines all Camel message headers. Key ones:
- `HEADER_SYNC_OPERATION` — `DELTA`, `FULL`, or `DELETE_UNUSED`
- `HEADER_NEXT_BATCH_URL` — URL for the next NeTEx page from Tiamat
- `HEADER_SYNC_STATUS_FROM` / `HEADER_SYNC_STATUS_TO` — epoch millis for time-windowed queries
- `HEADER_CRUD_ACTION` — `CrudAction` enum value for change type

### Sync Status Persistence

Sync state (last completed timestamp) is stored in blob storage. Two implementations:
- `GcsBlobStoreRepository` — production (GCS)
- `InMemoryBlobStoreRepository` — testing (activated via `in-memory-blobstore` Spring profile)

### Avro Schema

`src/main/avro/StopPlaceChangelogEvent.avsc` defines the Kafka event schema. Generated classes go to `target/generated-sources/` via `avro-maven-plugin` at `generate-sources` phase.

## Testing

Integration tests extend `RouteBuilderIntegrationTestBase`, which:
- Starts a **PubSub emulator** via Testcontainers (`gcr.io/google.com/cloudsdktool/cloud-sdk:emulators`)
- Activates profiles: `in-memory-blobstore`, `test`, `google-pubsub-autocreate`, `no-kafka`
- Uses `@UseAdviceWith` — tests must call `context.start()` manually after setting up advice

Tests intercept real endpoints using `AdviceWith.adviceWith(context, "route-id", ...)` and replace them with `MockEndpoint`s. Route IDs (e.g. `"chouette-synchronize-stop-place-batch"`) are the hook points — don't rename them without updating tests.

Each test method gets a fresh context (`@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)`).

## Key Configuration Properties

```properties
tiamat.url=http://tiamat:2888
chouette.url=http://localhost:8080
chouette.sync.stop.place.cron=0 0/5 * * * ?      # Delta sync (every 5 min)
chouette.sync.stop.place.full.cron=0 0 2 * * ?   # Full sync (2am daily)
chouette.sync.stop.place.autoStartup=true
sync.stop.place.batch.size=1000
irkalla.security.user-context-service=full-access  # dev; use token-based in prod
```

## License

All source files require the EUPL v1.2 license header.