# syntax=docker/dockerfile:1
# multi-stage docker file, separate build and run steps
# https://docs.docker.com/develop/develop-images/multistage-build/

# build step
FROM clojure:openjdk-17-lein-bullseye
RUN mkdir -p /usr/src/app
COPY project.clj /usr/src/app/
WORKDIR /usr/src/app
RUN lein deps
COPY . /usr/src/app
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app.jar

# run step
FROM openjdk:17-slim-bullseye
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY --from=0 /usr/src/app/app.jar /usr/src/app/
COPY --from=0 /usr/src/app/resources /usr/src/app/resources
ENV APP__VERSION=$APP__VERSION
ENV APP__ENV=$APP__ENV
ENV APP__DEBUG=$APP__DEBUG
ENV SLACK__TOKEN=$SLACK__TOKEN
ENV SLACK__SIGN=$SLACK__SIGN
ENV ALERTS__SENTRY_DSN=$ALERTS__SENTRY_DSN
ENV SERVER__PORT=$SERVER__PORT
ENV SERVER__LOGLEVEL=$SERVER__LOGLEVEL
ENV SERVER__ACCESS_LOG=$SERVER__ACCESS_LOG
ENV DB__SERVER_NAME=$DB__SERVER_NAME
ENV DB__PORT_NUMBER=$DB__PORT_NUMBER
ENV DB__DATABASE_NAME=$DB__DATABASE_NAME
ENV DB__USERNAME=$DB__USERNAME
ENV DB__PASSWORD=$DB__PASSWORD
ENV DB__POOL_MIN_IDLE=$DB__POOL_MIN_IDLE
ENV DB__POOL_MAX_SIZE=$DB__POOL_MAX_SIZE
ENV DB__TIMEOUT_MS_CONNECTION=$DB__TIMEOUT_MS_CONNECTION
CMD ["java", "-jar", "app.jar"]
