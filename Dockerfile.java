FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

# 复制整个项目
COPY . .

# 接收模块名参数
ARG MODULE_NAME

# 构建指定的模块及其依赖
RUN mvn clean package -pl ${MODULE_NAME} -am -DskipTests

# 运行镜像
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ARG MODULE_NAME

# 从 builder 复制编译后的 jar 包
COPY --from=builder /build/${MODULE_NAME}/target/*.jar app.jar

ENV TZ=Asia/Shanghai
# 默认环境设置为 prod，以连接 Docker 内的中间件
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
