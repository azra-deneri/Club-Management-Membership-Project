# ISC-MS — Istanbul Sports Club Membership Management System

**Project #17 — MISY1102 Advanced Java for Information Management**
**Spring 2026**

## Team
- Azra Deneri — Model Layer, Service Layer, Tests
- Cahit Yunus Özdikiş — Database Design, DAO Layer, Main
- Tuna Küni — UI Layer, Tests

## Tech Stack
- Java 21
- Java Swing (Desktop UI)
- JDBC + MySQL 8
- JUnit 5 + Mockito 5
- Maven

## Architecture
4-layer: Model → DAO → Service → UI

Design Patterns: Singleton (DBConnection), Builder (MemberBuilder, EventBuilder), Factory (MemberFactory, EventFactory)

## Setup
1. Start MySQL via Docker: docker compose up -d
2. Import database: mysql -h 127.0.0.1 -P 3306 -u iscms -piscms123 iscms_final < isc_ms_dump.sql
3. Copy db.properties.template to db.properties and fill in credentials
4. Run: mvn clean package && java -jar target/ISC-MS.jar

## Tests
127 unit tests across 11 test classes — all pass with no live DB required.
