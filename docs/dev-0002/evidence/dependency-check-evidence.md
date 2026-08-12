# DEV-0002 依赖漏洞扫描证据（BLOCKED）

> 结论：**漏洞数据不可获得 → 扫描 BLOCKED**（评审 P0-2-11：不得写"无已知漏洞"）。

## 尝试 1：标准 NVD 更新

- **命令**：`./mvnw -pl nexus-edge-compatibility-test org.owasp:dependency-check-maven:13.0.0:check -DfailBuildOnCVSS=11`
- **工具**：OWASP Dependency-Check Maven Plugin **13.0.0**（Maven Central）
- **执行时间（UTC）**：2026-08-12T02:44:00Z
- **JDK**：OpenJDK 21.0.10 LTS；**Maven**：3.9.16
- **原始错误**：
  ```
  UpdateException: Error updating the NVD Data
    caused by NvdApiException: Invalid API Key, length of 0 too short to provided a masked partial key
  NoDataException: No documents exist
  ```
- **根因**：Dependency-Check 13.x 更新 NVD 数据需要 NVD API v2 Key（客户端强制校验，空 Key 拒绝）；本环境无 NVD API Key 可配置。
- **网络探活**：NVD API `https://services.nvd.nist.gov/rest/json/cves/2.0` HTTP 200（可达，但数据更新被客户端 Key 校验阻断）；OSS Index `https://ossindex.sonatype.org/` HTTP 000（不可达）。

## 尝试 2：本地缓存 + 禁用更新

- **命令**：`./mvnw -pl nexus-edge-compatibility-test org.owasp:dependency-check-maven:13.0.0:check -DfailBuildOnCVSS=11 -DautoUpdate=false -Ddata.directory=~/.m2/repository/org/owasp/dependency-check-data`
- **原始错误**：
  ```
  NoDataException: Autoupdate is disabled and the database does not exist
  ```
- **根因**：本地缓存（data version 11.0）仅有 CPE/POM/JS 缓存，**无 NVD 漏洞数据库**，无法提供漏洞数据。

## 处置

- 漏洞扫描状态：**BLOCKED（漏洞数据不可获得）**。
- **不得声明"无已知漏洞"**（评审 P0-2-11）。
- 后续：企业 CI（G-06）配置 NVD API Key（Secret Provider 注入）或企业私有漏洞数据源后，重新执行本扫描并对中高危结果逐项记录 disposition（不受影响/已修复/已缓解/接受风险/阻断）。
