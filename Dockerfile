# 两段构建：编译用完整 JDK + Maven，运行只带 JRE。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# 先只拷 pom 拉依赖，源码改动不会让这一层缓存失效
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/mathematics-*.jar /app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --home /app mathematics && mkdir -p /app/data && chown mathematics /app/data
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER mathematics
# 资料与题图落在这里，必须挂卷，否则容器重建就丢文件
VOLUME /app/data
ENV SPRING_PROFILES_ACTIVE=mysql \
    MATHEMATICS_MATERIAL_STORAGE_DIR=/app/data/materials \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
