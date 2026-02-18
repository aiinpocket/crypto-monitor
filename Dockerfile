# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# 先複製 Maven wrapper 和 pom.xml 以利用快取
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# 複製原始碼並構建
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN chown -R app:app /app
USER app

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=50.0", \
    "-XX:+UseG1GC", \
    "-XX:G1PeriodicGCInterval=15000", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "app.jar"]
