# 数据库可视化系统

这是一个基于Spring Boot + Vue 3的数据库可视化系统，支持连接MySQL数据库并提供数据可视化功能。

## 项目结构

```
bio_data/
├── src/                    # Spring Boot后端代码
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/bio_data/
│   │   │       ├── BioDataApplication.java    # 主应用类
│   │   │       ├── config/                    # 配置类
│   │   │       ├── controller/                # REST控制器
│   │   │       └── service/                   # 业务服务
│   │   └── resources/
│   │       └── application.properties         # 应用配置
├── frontend/               # Vue 3前端代码
│   ├── src/
│   │   ├── components/     # 组件
│   │   ├── views/          # 视图页面
│   │   ├── utils/          # 工具类
│   │   └── router/         # 路由配置
│   └── package.json
└── pom.xml                # Maven配置
```

## 功能特性

- **数据库连接**: 支持连接MySQL数据库（默认chembl33）
- **仪表板**: 显示数据库统计信息和图表
- **表管理**: 浏览数据库表结构和数据
- **SQL查询**: 执行自定义SQL查询
- **可视化**: 使用ECharts进行数据可视化

## 环境要求

- Java 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.6+

## 配置说明

### 1. 数据库配置

修改 `src/main/resources/application.properties` 中的数据库连接信息：

```properties
# MySQL数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/chembl33?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

请将 `your_password` 替换为你的MySQL密码。

### 2. 支持多数据库

如果需要连接其他数据库，只需修改上述配置中的：
- `spring.datasource.url` 中的数据库名
- 用户名和密码

## 启动说明

### 1. 启动后端 (Spring Boot)

在项目根目录下执行：

```bash
# Windows (PowerShell)
.\mvnw.cmd spring-boot:run

# Windows (CMD)
mvnw.cmd spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动

### 2. 启动前端 (Vue 3)

```bash
cd frontend
npm run dev
```

前端服务将在 `http://localhost:3000` 启动

## API接口说明

### 数据库相关接口

- `GET /api/database/health` - 健康检查
- `GET /api/database/stats` - 数据库统计信息
- `GET /api/database/tables` - 获取所有表信息
- `GET /api/database/tables/{tableName}/columns` - 获取表结构
- `GET /api/database/tables/{tableName}/data` - 获取表数据
- `POST /api/database/query` - 执行SQL查询

## 使用方法

1. **仪表板**: 查看数据库概览和统计信息
2. **数据表**: 浏览所有表，查看表结构和数据
3. **SQL查询**: 执行自定义SQL查询（仅支持SELECT语句）

## 安全说明

- 系统只允许执行SELECT查询，不支持INSERT、UPDATE、DELETE等操作
- 查询结果限制最多返回1000行
- 已配置跨域支持，仅允许本地开发环境访问

## 故障排除

1. **数据库连接失败**: 检查MySQL服务是否启动，用户名密码是否正确
2. **前端无法访问后端**: 检查后端是否在8080端口启动
3. **依赖安装失败**: 清理npm缓存 `npm cache clean --force` 后重新安装

## 技术栈

### 后端
- Spring Boot 3.5.3
- Spring Data JPA
- MySQL Connector
- Maven

### 前端
- Vue 3
- Element Plus
- ECharts
- Axios
- Vue Router
- Vite

## 开发说明

项目采用前后端分离架构：
- 后端提供RESTful API接口
- 前端Vue应用通过HTTP请求与后端通信
- 使用代理转发解决跨域问题 