FROM bellsoft/liberica-openjdk-alpine:21.0.5-11 AS builder
COPY target/irkalla-*-SNAPSHOT.jar application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM bellsoft/liberica-openjdk-alpine:21.0.5-11
RUN apk update && apk upgrade && apk add --no-cache tini=0.19.0-r2

WORKDIR /deployments
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
RUN mkdir -p /home/appuser/.ssh \
 && touch /home/appuser/.ssh/known_hosts
COPY --from=builder dependencies/ ./
COPY --from=builder snapshot-dependencies/ ./
COPY --from=builder spring-boot-loader/ ./
COPY --from=builder application/ ./
ENTRYPOINT [ "/sbin/tini", "--", "java", "org.springframework.boot.loader.launch.JarLauncher" ]
