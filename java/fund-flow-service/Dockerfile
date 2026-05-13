# 先在本机打包: mvn clean package -pl fund-flow-service -am -DskipTests
# 经 DaoCloud 拉取，避免本机 Docker 将 docker.io 重定向到失效的 ustc 等 mirror 时出现 EOF
FROM docker.m.daocloud.io/eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*-SNAPSHOT.jar app.jar
ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx256m -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
