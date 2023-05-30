# Flightplan Server

## Building the Docker image

Build Docker image by executing

    mvnw spring-boot:build-image -DskipTests

from the project root (use `mvnw.cmd` on Windows).

It requires a running [Docker](https://www.docker.com/products/docker-desktop/) environment on the local machine and [Java 20](https://jdk.java.net/20/).

----

## Running the server as a standalone instance

The server can also be run separately by executing

    mvnw spring-boot:run

from the project root (use `mvnw.cmd` on Windows). 

This requires a running MongoDB instance on the local machine with the default port (`27017`), as well as [Java 20](https://jdk.java.net/20/).

__Be aware__ that a data
import will take place on startup if that data is not yet in the DB.

The server will be listening on port `8077` by default. 
The primary service endpoint is `/api/proximity?lat=[lat]&lon=[lon]`.