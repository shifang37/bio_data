# 数据库可视化系统 (Bio Data Visualization System)

一个基于 Spring Boot + Vue.js 的现代化数据库管理和可视化系统，专为生物医学数据分析和管理而设计。

## 🌟 主要特性

### 💾 多数据源管理

- 支持多个 MySQL 数据库连接
- 动态数据源切换和管理
- 连接池优化和健康监控
- 预配置生物医学数据库（ChEMBL 33、TCRD 6.12.4）

### 📊 数据管理

- 完整的数据库 CRUD 操作
- 表结构查看和管理
- 数据分页浏览和搜索
- 支持复杂 SQL 查询执行

### 🚀 CSV 批量导入

- **智能导入策略**：追加模式和覆盖模式
- **自动建表功能**：根据 CSV 内容自动推断数据类型并创建表
- **百万级数据支持**：分批处理，避免内存溢出
- **实时进度跟踪**：导入状态和错误处理
- **事务支持**：确保数据一致性

### 🕸️ 知识图谱可视化

- JSON 和 CSV 格式知识图谱上传
- 交互式网络图可视化
- 节点搜索和数据过滤
- 多格式导出（JSON、CSV、GraphML）

### 👥 用户权限管理

- 管理员和普通用户角色
- 细粒度数据库访问控制
- 安全的用户认证系统

## 🏗️ 系统架构

### 后端技术栈

- **Spring Boot 3.5.4** - 企业级 Java 框架
- **MySQL 8.0+** - 关系型数据库
- **HikariCP** - 高性能连接池
- **JdbcTemplate** - 数据库访问层
- **Maven** - 项目构建工具

### 前端技术栈

- **Vue 3** - 现代化前端框架
- **Element Plus** - UI 组件库
- **Vite** - 快速构建工具
- **Vue Router** - 路由管理
- **Axios** - HTTP 客户端
- **D3.js & Vis-network** - 数据可视化
- **PapaParse** - CSV 文件解析

## 📱 功能模块

### 1. 仪表板 (Dashboard)

- 系统概览和统计信息
- 数据库连接状态监控
- 快速操作入口

### 2. 数据库管理 (Tables)

- 数据库和表的浏览
- 表结构查看和编辑
- 数据的增删改查操作
- 索引和约束管理

### 3. SQL 查询 (Query)

- 自定义 SQL 查询执行
- 查询结果可视化展示
- 字段值搜索功能
- 查询历史记录

### 4. 数据导入 (Import)

- **CSV 文件上传和解析**
- **智能列映射**
- **数据类型推断**
- **批量导入执行**
- **导入历史管理**

### 5. 知识图谱 (Knowledge Graph)

- 图谱数据上传和解析
- 交互式网络可视化
- 节点搜索和过滤
- 图谱统计分析

## 🚀 快速开始

### 环境要求

- **Java 17+**
- **Node.js 16+**
- **MySQL 8.0+**
- **Maven 3.6+**

### 一键启动

运行项目根目录下的启动脚本：

```bash
# Windows
quick-start.bat

# Linux/Mac
./quick-start.sh  # (需要创建对应脚本)
```

### 手动启动

#### 1. 数据库配置

修改 `backend/src/main/resources/application.properties`：

```properties

# 主数据源配置 (chembl33)
datasource.primary.jdbcUrl=jdbc:mysql://localhost:3306/chembl33
datasource.primary.username=your_username
datasource.primary.password=your_password

# 第二数据源配置 (tcrd6124expr2)
datasource.secondary.jdbcUrl=jdbc:mysql://localhost:3306/tcrd6124expr2
datasource.secondary.username=your_username
datasource.secondary.password=your_password

# 用户登录数据库配置
datasource.login.jdbcUrl=jdbc:mysql://localhost:3306/login
datasource.login.username=your_username
datasource.login.password=your_password
```

#### 2. 后端启动

```bash
cd backend
./mvnw spring-boot:run
# 或使用 Maven
mvn spring-boot:run
```

#### 3. 前端启动

```bash
cd frontend
npm install
npm run dev
```

### 访问系统

- **前端界面**: [http://localhost:3000](http://localhost:3000)
- **后端 API**: [http://localhost:8080](http://localhost:8080)

## 📂 项目结构

```text
bio_data/
├── backend/                     # Spring Boot 后端
│   ├── src/main/java/com/example/bio_data/
│   │   ├── controller/          # REST API 控制器
│   │   │   ├── AuthController.java
│   │   │   ├── DatabaseController.java
│   │   │   └── KnowledgeGraphController.java
│   │   ├── service/             # 业务逻辑服务
│   │   │   ├── AuthService.java
│   │   │   ├── DatabaseService.java
│   │   │   ├── MultiDataSourceService.java
│   │   │   ├── PermissionService.java
│   │   │   └── SearchCacheService.java
│   │   ├── entity/              # 数据实体类
│   │   │   ├── User.java
│   │   │   └── Admin.java
│   │   ├── config/              # 配置类
│   │   │   ├── DataSourceConfig.java
│   │   │   └── WebConfig.java
│   │   └── BioDataApplication.java
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
├── frontend/                    # Vue.js 前端
│   ├── src/
│   │   ├── components/          # Vue 组件
│   │   │   ├── CsvImporter.vue
│   │   │   ├── DatabaseConnection.vue
│   │   │   ├── KnowledgeGraphD3.vue
│   │   │   └── KnowledgeGraphViewer.vue
│   │   ├── views/               # 页面组件
│   │   │   ├── Login.vue
│   │   │   ├── Dashboard.vue
│   │   │   ├── Tables.vue
│   │   │   ├── Query.vue
│   │   │   ├── Import.vue
│   │   │   └── KnowledgeGraph.vue
│   │   ├── router/              # 路由配置
│   │   │   └── index.js
│   │   ├── utils/               # 工具类
│   │   │   └── api.js
│   │   ├── App.vue
│   │   └── main.js
│   ├── package.json
│   └── vite.config.js
├── quick-start.bat              # Windows 启动脚本
└── README.md
```

## 🎯 核心功能详解

### CSV 数据导入功能

#### 导入策略

1. **追加模式 (Append)**
   - 智能检测重复数据
   - 基于主键或全行比较
   - 只导入新增数据

2. **覆盖模式 (Overwrite)**
   - 清空表中所有数据
   - 重新导入 CSV 中的所有数据

#### 自动建表功能

- **数据类型推断**：分析 CSV 数据自动推断最适合的 MySQL 字段类型
- **表名生成**：使用 CSV 文件名自动生成表名
- **列名规范化**：自动清理非法字符，确保符合 MySQL 命名规范

#### 数据类型推断规则

| 数据特征 | 推断类型 | 示例 |
|---------|---------|------|
| 纯数字（整数） | INT / BIGINT | 123, 456789 |
| 小数 | DECIMAL(10,2) | 123.45, 67.89 |
| 日期格式 | DATETIME | 2024-01-01, 2024-01-01 12:00:00 |
| 短文本（≤255字符） | VARCHAR(动态长度) | 姓名、邮箱等 |
| 长文本（>255字符） | TEXT | 描述、备注等 |
| 空值或混合类型 | VARCHAR(255) | 默认类型 |

### 知识图谱功能

#### 支持的数据格式

1. **JSON 格式**

```json
{
  "nodes": [
    {
      "id": "P001",
      "name": "Protein_1",
      "type": "Protein",
      "properties": {...}
    }
  ],
  "links": [
    {
      "source": "G001",
      "target": "P001",
      "type": "encodes"
    }
  ]
}
```

2.**CSV 格式**

```csv
source,target,relation,evidence
Gene_P53,Protein_ALK,regulates,STRING Database
Disease_LungCancer,Gene_EGFR,interacts_with,Published Paper
```

## 🔧 配置说明

### 数据库连接池配置

```properties
# HikariCP 连接池配置
datasource.primary.connectionTimeout=60000
datasource.primary.idleTimeout=300000
datasource.primary.maxLifetime=600000
datasource.primary.maximumPoolSize=20
datasource.primary.minimumIdle=5
```

### 系统限制配置

- 单次导入最大记录数：100万条
- 单个文件大小限制：100MB
- 批处理大小：5000条/批（可调整）
- 查询结果限制：1000条/次

## 🛡️ 安全特性

- **用户认证**：基于用户名密码的登录系统
- **权限控制**：管理员和普通用户角色分离
- **数据库访问控制**：细粒度的数据库操作权限
- **SQL 注入防护**：使用参数化查询防止 SQL 注入
- **CORS 配置**：跨域请求安全控制

## 📊 性能优化

### 数据导入优化

- **分批处理**：默认 5000 条记录一批，避免内存溢出
- **连接池管理**：HikariCP 高性能连接池
- **事务支持**：可选择事务性导入确保数据一致性
- **内存优化**：流式处理大文件

### 查询优化

- **结果集限制**：防止大量数据导致的性能问题
- **缓存机制**：搜索结果缓存提高响应速度
- **分页查询**：支持大数据集的分页浏览

## 🐛 常见问题

### 1. 数据库连接失败

- 检查数据库服务是否启动
- 验证连接参数是否正确
- 确认防火墙和网络设置

### 2. CSV 导入失败

- 检查文件编码（推荐 UTF-8）
- 验证数据格式和类型匹配
- 确认数据库写权限

### 3. 知识图谱加载缓慢

- 减少节点数量（建议 < 1000）
- 使用过滤功能筛选数据
- 检查浏览器性能

## 🔄 更新日志

### v1.0.0 (当前版本)

- ✨ 完整的数据库管理功能
- 🚀 CSV 批量导入功能
- 🕸️ 知识图谱可视化
- 👥 用户权限管理系统
- 📊 多数据源支持
- 🔍 高级查询和搜索功能

## 📄 许可证

MIT License

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request 来改进项目！

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 GitHub Issue
- 发送邮件至项目维护者

---

**注意**: 本系统主要用于学习和研究目的，生产环境使用前请进行充分测试和安全评估。
