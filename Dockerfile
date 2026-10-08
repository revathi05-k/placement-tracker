# Builds and runs the Placement Tracker. No Maven or frameworks: plain javac and java.
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Download the two jars from Maven Central
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/* \
 && mkdir -p lib \
 && curl -fsSL -o lib/sqlite-jdbc-3.45.1.0.jar https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar \
 && curl -fsSL -o lib/slf4j-api-1.7.36.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar

COPY src ./src
COPY static ./static

RUN mkdir -p out && javac -cp "lib/*" -d out src/*.java

# PORT is provided by the hosting service. DB_PATH should point to a persistent disk if you have one.
ENV PORT=8080
ENV COOKIE_SECURE=true
EXPOSE 8080

CMD ["java", "-cp", "out:lib/*", "Main"]
