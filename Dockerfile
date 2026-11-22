# build: the full JDK and Gradle, thrown away after
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# a checkout from Windows may lose the executable bit
RUN chmod +x gradlew
# dependencies first, in their own layer: a code change doesn't download them again
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# run: only the JRE and the jar, as a non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system scout && mkdir logs && chown scout logs
COPY --from=build /app/build/libs/*.jar app.jar
USER scout
# use most of the container's memory limit, not the JVM's default quarter
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
