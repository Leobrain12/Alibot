# Многоэтапная сборка: собираем jar в контейнере с JDK, запускаем на лёгком JRE-образе,
# чтобы конечный образ не тащил за собой весь Maven и dev-инструменты.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Сначала только файлы, нужные для резолва зависимостей — так Docker кэширует этот слой
# и не перекачивает интернет заново при каждой правке кода.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/alibot.jar /build/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin alibot
COPY --from=build /build/app.jar app.jar
RUN mkdir -p /data/media && chown -R alibot:alibot /data /app
USER alibot

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
