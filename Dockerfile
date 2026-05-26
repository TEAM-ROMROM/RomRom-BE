FROM eclipse-temurin:17-jre-alpine

# 작업 디렉토리 설정
WORKDIR /app

# curl 설치
RUN apk add --no-cache curl

# 빌드된 JAR 파일을 복사 
COPY RomRom-Web/build/libs/*.jar /app.jar

# 애플리케이션 실행 (기본 Spring Boot 설정)
ENTRYPOINT ["java", "-jar", "/app.jar"]

# Spring Boot 서버 포트 노출
EXPOSE 8080

# Docker 헬스체크 — 30s 간격으로 Traefik이 신속하게 컨테이너 상태 감지
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 \
CMD curl -f http://localhost:8080/actuator/health || exit 1