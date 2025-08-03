# 多数据源配置使用指南

## 概述

本系统已经配置为支持多数据源，可以同时连接和操作多个MySQL数据库。系统具有以下特性：

- 支持动态添加和删除数据源
- 保持向后兼容性，原有API继续有效
- 提供数据源管理和监控功能
- 支持连接池管理和统计

## 配置说明

### 1. 配置文件结构

在 `application.properties` 中，数据源配置格式如下：

```properties
# 主数据源配置 (chembl)
datasource.primary.url=jdbc:mysql://localhost:3306/chembl33?useSSL=false&serverTimezone=UTC
datasource.primary.username=root
datasource.primary.password=root
datasource.primary.driver-class-name=com.mysql.cj.jdbc.Driver
datasource.primary.name=chembl

# 第二个数据源配置
datasource.secondary.url=jdbc:mysql://localhost:3306/bio_data2?useSSL=false&serverTimezone=UTC
datasource.secondary.username=root
datasource.secondary.password=root
datasource.secondary.driver-class-name=com.mysql.cj.jdbc.Driver
datasource.secondary.name=bio_data2
```

### 2. 添加更多数据源

要添加更多数据源，可以通过以下方式：

#### 方式1：配置文件（需要重启）

在 `DataSourceConfig.java` 中添加新的数据源Bean：

```java
@Bean(name = "thirdDataSource")
@ConfigurationProperties(prefix = "datasource.third")
public DataSource thirdDataSource() {
    return new HikariDataSource();
}
```

#### 方式2：动态添加（推荐）

通过API动态添加，无需重启：

```bash
curl -X POST http://localhost:8080/api/database/datasources \
-H "Content-Type: application/json" \
-d '{
  "name": "new_database",
  "url": "jdbc:mysql://localhost:3306/new_db?useSSL=false&serverTimezone=UTC",
  "username": "root",
  "password": "root"
}'
```

## API接口说明

### 数据源管理接口

#### 1. 获取所有数据源

```http
GET /api/database/datasources
```

响应示例：

```json
[
  {
    "name": "chembl",
    "connected": true,
    "jdbcUrl": "jdbc:mysql://localhost:3306/chembl33",
    "username": "root"
  },
  {
    "name": "bio_data2",
    "connected": true,
    "jdbcUrl": "jdbc:mysql://localhost:3306/bio_data2",
    "username": "root"
  }
]
```

#### 2. 添加新数据源

```http
POST /api/database/datasources
Content-Type: application/json

{
  "name": "new_database",
  "url": "jdbc:mysql://localhost:3306/new_db?useSSL=false&serverTimezone=UTC",
  "username": "root",
  "password": "root"
}
```

#### 3. 删除数据源

```http
DELETE /api/database/datasources/{dataSourceName}
```

#### 4. 测试数据源连接

```http
GET /api/database/datasources/{dataSourceName}/test
```

#### 5. 获取数据源统计信息

```http
GET /api/database/datasources/{dataSourceName}/stats
```

### 数据库操作接口

所有原有的数据库操作接口都支持可选的 `dataSource` 参数：

#### 1. 获取表列表

```http
# 使用默认数据源
GET /api/database/tables

# 指定数据源
GET /api/database/tables?dataSource=bio_data2
```

#### 2. 获取表数据

```http
# 使用默认数据源
GET /api/database/tables/user_table/data?limit=100

# 指定数据源
GET /api/database/tables/user_table/data?limit=100&dataSource=bio_data2
```

#### 3. 执行SQL查询

```http
POST /api/database/query
Content-Type: application/json

{
  "sql": "SELECT * FROM users WHERE id = 1",
  "limit": 100,
  "dataSource": "bio_data2"
}
```

#### 4. 插入数据

```http
POST /api/database/tables/{tableName}/data
Content-Type: application/json

{
  "dataSource": "bio_data2",
  "data": {
    "name": "John",
    "email": "john@example.com"
  }
}
```

## 使用示例

### 1. 添加新的MySQL数据库

```bash
# 1. 添加数据源
curl -X POST http://localhost:8080/api/database/datasources \
-H "Content-Type: application/json" \
-d '{
  "name": "test_db",
  "url": "jdbc:mysql://localhost:3306/test_database?useSSL=false&serverTimezone=UTC",
  "username": "root",
  "password": "root"
}'

# 2. 测试连接
curl http://localhost:8080/api/database/datasources/test_db/test

# 3. 获取该数据源的表列表
curl http://localhost:8080/api/database/tables?dataSource=test_db
```

### 2. 查询不同数据源的数据

```bash
# 查询chembl数据库的表
curl http://localhost:8080/api/database/tables?dataSource=chembl

# 查询test_db数据库的表
curl http://localhost:8080/api/database/tables?dataSource=test_db

# 在test_db中执行SQL查询
curl -X POST http://localhost:8080/api/database/query \
-H "Content-Type: application/json" \
-d '{
  "sql": "SELECT COUNT(*) FROM users",
  "dataSource": "test_db"
}'
```

### 3. 监控数据源状态

```bash
# 获取所有数据源概览
curl http://localhost:8080/api/database/datasources

# 获取特定数据源的详细统计
curl http://localhost:8080/api/database/datasources/chembl/stats

# 健康检查
curl http://localhost:8080/api/database/health?dataSource=chembl
```

## 向后兼容性

所有原有的API调用都将继续工作，默认使用名为 "chembl" 的主数据源：

```bash
# 这些调用都使用默认数据源（chembl）
curl http://localhost:8080/api/database/tables
curl http://localhost:8080/api/database/stats
curl -X POST http://localhost:8080/api/database/query -d '{"sql": "SELECT 1"}'
```

## 注意事项

1. **默认数据源**：系统默认使用名为 "chembl" 的数据源，这在 `DatabaseService.DEFAULT_DATASOURCE` 中定义。

2. **连接池管理**：每个数据源都有独立的连接池，可以通过统计接口监控连接池状态。

3. **动态数据源**：通过API动态添加的数据源在应用重启后会丢失，如需持久化，应添加到配置文件中。

4. **安全性**：数据源添加接口包含敏感信息（数据库密码），在生产环境中应添加适当的认证和授权机制。

5. **错误处理**：如果指定的数据源不存在，API会返回相应的错误信息。

## 扩展建议

为了进一步增强多数据源功能，可以考虑：

1. **数据源配置持久化**：将动态添加的数据源配置保存到配置文件或数据库中
2. **数据源分组**：支持数据源分组管理
3. **读写分离**：支持主从数据库配置
4. **负载均衡**：在多个相同数据源之间进行负载均衡
5. **监控告警**：添加数据源连接状态监控和告警机制
