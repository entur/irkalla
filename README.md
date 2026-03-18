# irkalla [![Build and push](https://github.com/entur/irkalla/actions/workflows/push.yml/badge.svg?branch=master)](https://github.com/entur/irkalla/actions/workflows/push.yml)

Irkalla is a Spring Boot integration service that synchronizes stop place data from the [Norwegian Stop Place Register](https://stoppested.entur.org) into the [Routes database](https://rutedb.dev.entur.org/).

It monitors [Tiamat](https://github.com/entur/tiamat) for stop place changes and:
- Replicates changes to [Chouette](https://github.com/entur/chouette) via its REST API (NeTEx format, delta and full sync)
- Sends CRUD event notifications to [Nabu](https://github.com/entur/nabu) via Google Pub/Sub
- Publishes stop place changelog events to Kafka (Avro schema, with Confluent Schema Registry)
- Persists sync state in Google Cloud Storage to support resumable synchronization

### Kafka event

Each stop place change produces a `StopPlaceChangelogEvent` on the configured topic:

| Field | Type | Description |
|---|---|---|
| `stopPlaceId` | string | NeTEx id (e.g. `NSR:StopPlace:1234`) |
| `stopPlaceVersion` | long | Stop place version number |
| `stopPlaceChanged` | timestamp-millis (nullable) | When the change occurred |
| `eventType` | enum | `CREATE`, `UPDATE`, `REMOVE`, or `DELETE` |

`REMOVE` means the stop place was deactivated; `DELETE` means it was permanently terminated.

Relevant configuration:

```properties
irkalla.kafka.topic.event=ror-stop-places-updated
camel.component.kafka.brokers=localhost:9092
camel.component.kafka.schema-registry-u-r-l=http://localhost:8082
```

Kafka publishing can be disabled with the `no-kafka` Spring profile.


## Build
```bash
mvn clean install
```

## Run locally (without Kubernetes)

```properties
server.port=10501
server.admin.host=0.0.0.0
server.admin.port=11501
server.context-path=/irkalla/

irkalla.security.user-context-service=full-access

tiamat.url=http://tiamat:2888
chouette.url=http://localhost:8080

rutebanken.kubernetes.enabled=false
chouette.sync.stop.place.autoStartup=true
```

## Security
An authorization service implementation must be selected.
The following implementation gives full access to all authenticated users:

```properties
irkalla.security.user-context-service=full-access
```

The following implementation enables OAuth2 token-based authorization:
```properties
irkalla.security.user-context-service=token-based
```