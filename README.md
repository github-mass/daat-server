# Drone Airspace Awareness Tool :: Backend

# Wat is DAAT?

The Drone Airspace Awareness Tool, DAAT for short, is a tool whose purpose is to assist people who fly drones, esp. when they do so professionally, in their flight preparations. Airspace is generally a tightly regulated thing, and the **DAAT provides a quick overview over the relevant regulated airspaces at a given geographical location**.

This is the **backend** part of the tool. It
- performs data ingestion,
- manages data persistence, and 
- serves the data upon query. 

A **frontend** has been built and is available [here](https://github.com/github-mass/daat-front).

## Caveat: *French*

**This service currently only works for French territory and will have to be adapted to serve other geographical areas.**

A few things tie this to French territory:

Firstly, the data format it expects for ingestion: it was designed to use the **AIXM data made available by the [French Aeronautical Information Service](https://www.sia.aviation-civile.gouv.fr/)**. While there is a measure of standardisation in that data format, expect at least a few idiosyncrasies. 

Furthermore, in addition to regulated airspaces, this tool handles a dataset called ***ZICAD***. 
*ZICADs* are zones that are forbidden to photograph or record. 
They are an item of French law, and while the concept will likely be found in other jurisdictions as well, the data formats are almost guaranteed to be different. 
That being said, importing *ZICAD* data is optional.     

Lastly, this tool used an online altimetry service to **resolve the altitude of geographical locations**. This is a tool provided by the French State: [IGN Altimétrie](https://geoservices.ign.fr/services-geoplateforme-altimetrie) and, at least as of the time of this writing, works only on French territory.

## License

This project is intended to be open source software and is published under an MIT License. See [LICENSE.txt](LICENSE.txt) for details.

Note that the data it works with (SIA AIXM and ZICAD) is subject to license requirements as well. At the time of this writing, these are quite permissive, allowing commercial use, provided proper attribution is given. 

## Technology

This service is built using Spring Boot 3.5 and requires at least Java 17. It further expects MongoDB version 7 for persistence. 

## Deployment

This service is intended to be deployed in a **Docker container** alongside a database, and the documentation assumes this scenario. That being said, it's a Java Spring application, so it can be run in other ways as well.

---

# User Manual

[Here](/doc/USER_MANUAL.md).

--- 

# Quickstart

## Building the Docker image

Build Docker image by executing

    mvnw spring-boot:build-image -DskipTests

from the project root (use `mvnw.cmd` on Windows).

It requires a running [Docker](https://www.docker.com/products/docker-desktop/) environment on the local machine and Java 17 or newer.

## Running the server as a standalone instance

The server can also be run separately by executing

    mvnw spring-boot:run

from the project root (use `mvnw.cmd` on Windows). 

This requires a running MongoDB instance on the local machine with the default port (`27017`), as well as Java 17 or newer.

__Be aware__ that a data
import will take place on startup if that data is not yet in the DB.

The server will be listening on port `8077` by default. 
The primary service endpoint is `/api/proximity?lat=[lat]&lon=[lon]`.
