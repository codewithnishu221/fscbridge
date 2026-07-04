FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

RUN ./mvnw dependency:go-offline -B 2>/dev/null || true

COPY fscbridge-core/ ./fscbridge-core/
COPY fscbridge-connector/ ./fscbridge-connector
COPY fscbridge-mapper/ ./fscbridge-mapper
COPY fscbridge-audit/ ./fscbridge-audit
COPY fscbridge-web/ ./fscbridge-web

RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S fscbridge && adduser -S fscbridge -G fscbridge

WORKDIR /app

COPY --from=builder /build/fscbridge-web/target/fscbridge-web-*.jar app.jar

RUN mkdir -p /app/logs && chown -R fscbridge:fscbridge /app

USER fscbridge

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
        CMD wget --quiet --tries=1 --spider \
        http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", \
            "java $JAVA_OPTS \
            -Djasypt.encryptor.password=${JASYPT_PASSWORD} \
            -Dspring.profiles.active=${SPRING_PROFILE:-prod} \
            -jar app.jar"]