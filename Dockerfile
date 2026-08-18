# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy just the POMs first so dependency resolution is its own cached layer —
# it only re-runs when a pom.xml changes, not on every source edit.
COPY pom.xml .
COPY api-contracts/pom.xml api-contracts/
COPY common/pom.xml common/
COPY user-management-service/pom.xml user-management-service/
COPY project-service/pom.xml project-service/
COPY audit-service/pom.xml audit-service/
COPY vyapar-service/pom.xml vyapar-service/
COPY payroll-service/pom.xml payroll-service/
COPY tender-service/pom.xml tender-service/
COPY web-app/pom.xml web-app/
RUN mvn -q -B dependency:go-offline || true

COPY api-contracts/src api-contracts/src
COPY common/src common/src
COPY user-management-service/src user-management-service/src
COPY project-service/src project-service/src
COPY audit-service/src audit-service/src
COPY vyapar-service/src vyapar-service/src
COPY payroll-service/src payroll-service/src
COPY tender-service/src tender-service/src
COPY web-app/src web-app/src
RUN mvn -q -B -pl web-app -am clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# The app is India-only and every timestamp is a zone-less LocalDateTime, so the container clock
# must be IST — otherwise the same code writes UTC wall-clock times here and IST ones on a dev box.
# HitechErpApplication pins the JVM zone as well; this keeps container logs and the OS in step.
ENV TZ=Asia/Kolkata
COPY --from=build /app/web-app/target/web-app-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
