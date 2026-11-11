# ===== 1단계: 빌드 =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 다운로드를 코드 변경과 분리해 캐시한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ===== 2단계: 실행 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 app
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER app

# Render 무료 플랜(512MB, CPU 0.1)에 맞춘 메모리·시작 속도 설정. 필요하면 JAVA_OPTS 환경변수로 덮어쓴다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Duser.timezone=Asia/Seoul"

EXPOSE 8080
# Render는 PORT 환경변수로 포트를 알려 준다. 없으면 8080.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
