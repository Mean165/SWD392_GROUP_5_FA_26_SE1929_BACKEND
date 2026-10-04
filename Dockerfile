# Giai đoạn 1: Build code (Dùng Alpine có sẵn Maven cài trong image luôn)
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY . .
# Dùng lệnh mvn có sẵn của hệ thống chứ không dùng wrapper ./mvnw nữa
RUN mvn clean package -DskipTests

# Giai đoạn 2: Chạy ứng dụng (Giữ nguyên cho nhẹ container)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]