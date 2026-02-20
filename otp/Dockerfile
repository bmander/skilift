# Stage 1: Build OTP from source
FROM maven:3.9-eclipse-temurin-21 AS build
ARG OTP_BRANCH=hill-avoidance
WORKDIR /build
RUN apt-get update && apt-get install -y git && \
    git clone --depth 1 --branch ${OTP_BRANCH} https://github.com/bmander/OpenTripPlanner.git .
RUN mvn clean package -DskipTests -q && \
    find otp-shaded/target -maxdepth 1 -name "otp-shaded-*.jar" ! -name "original-*" ! -name "*-sources.jar" | head -1 | xargs -I{} cp {} /otp-shaded.jar

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre
WORKDIR /otp
COPY --from=build /otp-shaded.jar /otp/otp-shaded.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
ENTRYPOINT ["/docker-entrypoint.sh"]
