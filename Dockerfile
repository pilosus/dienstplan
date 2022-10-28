# syntax=docker/dockerfile:1
# multi-stage docker file, separate build and run steps
# https://docs.docker.com/develop/develop-images/multistage-build/

###################
### Build stage ###
###################

FROM clojure:openjdk-17-lein-bullseye AS build
RUN mkdir -p /usr/src/app
COPY project.clj /usr/src/app/
WORKDIR /usr/src/app
RUN lein deps
COPY . /usr/src/app
# Build uberjar
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app.jar

# Build custom JRE image
RUN $JAVA_HOME/bin/jlink \
  --verbose \
  --add-modules ALL-MODULE-PATH \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2 \
  --output /customjre

#################
### Run stage ###
#################

FROM debian:bullseye-slim

# Use customer JRE from the build stage
ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=build /customjre $JAVA_HOME

# Set up a user with no priviliges
RUN adduser --no-create-home -u 1000 dienstplan

# Copy the app
RUN mkdir -p /usr/src/app && chown -R dienstplan /usr/src/app
USER 1000
COPY --from=build --chown=1000:1000 /usr/src/app/app.jar /usr/src/app/
COPY --from=build --chown=1000:1000 /usr/src/app/resources /usr/src/app/resources
WORKDIR /usr/src/app

# entrypoint
CMD ["/jre/bin/java", "-jar", "app.jar"]
