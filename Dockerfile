# syntax=docker/dockerfile:1

# Multi-stage docker file with separate build and run steps for image size optimisation
# https://docs.docker.com/develop/develop-images/multistage-build/
# Use Eclipse Temurin JDK/JRE as a vendor-agnostic, high-qulity solution
# with permissive FLOSS license
# https://whichjdk.com/#adoptium-eclipse-temurin

###################
### Build stage ###
###################

FROM clojure:temurin-17-lein-alpine@sha256:37968e7afb62937499c3773e9b713400da4abb358e6beb0ec9bae41a59715111 AS build

# Create a working directory
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# Install deps as a separate step for layer caching
COPY project.clj /usr/src/app/
RUN lein deps

# Compile uber-jar
COPY . /usr/src/app
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app.jar

#################
### Run stage ###
#################

FROM eclipse-temurin:17-jre-alpine@sha256:e1506ba20f0cb2af6f23e24c7f8855b417f0b085708acd9b85344a884ba77767 AS run

# Create app directory for unpriviledged user
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# Create unpriviledged system user
RUN adduser --disabled-password --no-create-home --uid 1000 dienstplan

# Copy uber-jar from the build stage
COPY --from=build /usr/src/app/app.jar /usr/src/app/
COPY --from=build /usr/src/app/resources /usr/src/app/resources
RUN chown -R 1000:1000 /usr/src/app

# Run as unpriviledged user
USER 1000
CMD ["java", "-jar", "app.jar"]
