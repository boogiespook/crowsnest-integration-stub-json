## Ensure that the following ENV variable are set:
# PG_USER
# PG_USER
# PG_DB (for example jdbc:postgresql://postgresql-0:5432/telescope )


FROM jbangdev/jbang-action AS build

RUN mkdir /app
WORKDIR /app
COPY src/crowsnestIntegrationStub.java /app
RUN jbang export portable --verbose --force /app/crowsnestIntegrationStubJson.java

FROM registry.access.redhat.com/ubi8/openjdk-17:1.14
USER root
RUN mkdir /app/
RUN mkdir /app/lib
COPY --from=build /app/crowsnestIntegrationStubJson.jar /app/crowsnestIntegrationStubJson.jar
COPY --from=build /app/lib/* /app/lib/
WORKDIR /app
USER 1001

## Don't actually run the code, just setup the env and the app can be run via a Job
CMD "java" "-jar" "/app/crowsnestIntegrationStubJson.jar"
