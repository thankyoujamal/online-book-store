# Builds and runs the store in a container (Railway, Render, Fly.io, any Docker host).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
ADD https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.1.0/mysql-connector-j-9.1.0.jar lib/
COPY src src
RUN javac -d out --release 17 $(find src/main/java -name '*.java')

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out out
COPY --from=build /app/lib lib
COPY web web
COPY app.properties .
EXPOSE 8080
# Hosting platforms pass the port in $PORT; the app reads it as SERVER_PORT.
CMD ["sh", "-c", "SERVER_PORT=${PORT:-8080} exec java -cp out:lib/mysql-connector-j-9.1.0.jar com.obs.App"]
