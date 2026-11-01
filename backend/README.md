# Zella Stay backend

Java 21, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL 16, Redis 7. Architecture: [../docs/architecture/SYSTEM_ARCHITECTURE.md](../docs/architecture/SYSTEM_ARCHITECTURE.md). Module contract: [../docs/architecture/MODULAR_ARCHITECTURE.md](../docs/architecture/MODULAR_ARCHITECTURE.md).

```bash
./gradlew build            # spotlessCheck, compile (-Xlint:all -Werror), tests, JaCoCo
./gradlew spotlessApply    # format sources
./gradlew bootRun          # needs ZS_* variables, see ../docs/engineering/DEVOPS.md section 12
```

Layout: `src/main/java/com/zellastay/<module>/{api,domain,application,infrastructure,web}`. Every top-level package is a module whose allowed dependencies are declared in its `package-info.java` and verified against the documented contract by `ModuleStructureTest`. Migrations: `src/main/resources/db/migration` (`V<timestamp>__<module>_<description>.sql`).

Tests: unit and architecture tests run without containers; `*IT` and `ConfigurationSourcesTest` start PostgreSQL and Redis with Testcontainers (Docker must be running). Reports: `build/reports/tests`, `build/reports/jacoco`, module diagrams in `build/spring-modulith-docs`.
