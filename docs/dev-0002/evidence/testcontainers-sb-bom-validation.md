命令: ./mvnw -pl nexus-edge-compatibility-test validate
时间: 2026-08-12T02:41:11Z
JAVA: openjdk version "21.0.10" 2026-01-20 LTS
Maven: Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)

--- 原始错误（节选）---
[ERROR] 'dependencies.dependency.version' for org.testcontainers:testcontainers:jar is missing. @ line 102, column 21
[ERROR] 'dependencies.dependency.version' for org.testcontainers:testcontainers-junit-jupiter:jar is missing. @ line 107, column 21
[ERROR] 'dependencies.dependency.version' for org.testcontainers:testcontainers:jar is missing. @ line 128, column 21
[ERROR] 'dependencies.dependency.version' for org.testcontainers:testcontainers-junit-jupiter:jar is missing. @ line 133, column 21
[ERROR] 'dependencies.dependency.version' for org.testcontainers:testcontainers-mysql:jar is missing. @ line 138, column 21
[ERROR] 'dependencies.dependency.version' for org.testcontainers:testcontainers-postgresql:jar is missing. @ line 143, column 21
[ERROR] The build could not read 2 projects -> [Help 1]
[ERROR]     'dependencies.dependency.version' for org.testcontainers:testcontainers:jar is missing. @ line 102, column 21
[ERROR]     'dependencies.dependency.version' for org.testcontainers:testcontainers-junit-jupiter:jar is missing. @ line 107, column 21
[ERROR]     'dependencies.dependency.version' for org.testcontainers:testcontainers:jar is missing. @ line 128, column 21

--- 结论 ---
Spring Boot 4.1.0 父 POM（spring-boot-dependencies 内含 testcontainers-bom import，本地缓存核验）无法为 reactor 子模块管理 Testcontainers 组件版本（Maven 3.9 嵌套 import 未传递）。
