FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
ARG GIT_COMMIT=unknown
RUN mvn clean package -DskipTests -Dgit.revision=${GIT_COMMIT}

FROM eclipse-temurin:21-jre-alpine AS runtime
# Apply Alpine security patches that are newer than the base image
RUN apk upgrade --no-cache
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

#FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
#WORKDIR /app
#COPY pom.xml .
#RUN mvn dependency:go-offline -B
#COPY src ./src
#ARG GIT_COMMIT=unknown
#RUN mvn clean package -DskipTests -Dgit.revision=${GIT_COMMIT}
#
#FROM eclipse-temurin:21-jre-alpine
#WORKDIR /app
#COPY --from=build /app/target/*.jar app.jar
#EXPOSE 8080
#ENTRYPOINT ["java", "-jar", "app.jar"]