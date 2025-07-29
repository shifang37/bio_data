# 数据库可视化系统

一个基于Spring Boot + Vue.js的数据库可视化和管理系统，支持多数据源管理、数据查询、表管理和**CSV文件批量导入**功能。

## 新增功能：CSV文件批量导入

### 功能特点
- 🚀 支持百万级数据批量导入
- 📊 智能列映射和数据验证
- 🔧 分批处理，避免内存溢出
- ⚡ 事务和非事务两种导入模式
- 📈 实时进度跟踪
- 🛡️ 详细的错误报告和处理
- 📝 导入历史记录管理

### 技术实现
- **前端**: Vue 3 + Element Plus + PapaParse
- **后端**: Spring Boot + JdbcTemplate + MySQL
- **性能优化**: 分批处理（默认5000条/批）、连接池管理、内存优化

### 使用方法
1. 点击"数据导入"菜单进入导入页面
2. 上传CSV文件，选择目标数据源和表
3. 配置解析参数（编码、分隔符等）
4. 预览数据并配置列映射
5. 选择导入模式并开始导入
6. 查看导入结果和历史记录

详细使用说明请参考：[CSV_IMPORT_GUIDE.md](CSV_IMPORT_GUIDE.md)

## 系统架构

### 后端技术栈
- Spring Boot 3.5.3
- MySQL 8.0+
- JdbcTemplate
- HikariCP 连接池
- Maven

### 前端技术栈
- Vue 3
- Element Plus
- Axios
- Vue Router
- PapaParse (CSV解析)
- Vite

## 主要功能

### 1. 多数据源管理
- 支持多个MySQL数据源配置
- 动态数据源切换
- 连接健康检查

### 2. 数据库管理
- 数据库创建、删除
- 表结构查看和管理
- 数据的增删改查
- 分页查询支持

### 3. SQL查询
- 自定义SQL查询执行
- 查询结果展示
- 查询历史记录

### 4. 数据导入（新增）
- CSV文件批量导入
- 智能列映射
- 数据验证和错误处理
- 导入进度跟踪

### 5. 用户权限管理
- 用户登录认证
- 管理员和普通用户权限区分
- 数据库访问权限控制

## 快速开始

### 环境要求
- Java 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.6+

### 后端启动
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### 前端启动
```bash
cd frontend
npm install
npm run dev
```

### 数据库配置
修改 `backend/src/main/resources/application.properties` 中的数据库连接信息：

```properties
# 主数据源配置
datasource.primary.jdbcUrl=jdbc:mysql://localhost:3306/your_database
datasource.primary.username=your_username
datasource.primary.password=your_password
```

## 项目结构

```
bio_data/
├── backend/                    # Spring Boot后端
│   ├── src/main/java/
│   │   └── com/example/bio_data/
│   │       ├── controller/     # REST控制器
│   │       ├── service/        # 业务逻辑
│   │       ├── entity/         # 实体类
│   │       └── config/         # 配置类
│   └── src/main/resources/
│       └── application.properties
├── frontend/                   # Vue.js前端
│   ├── src/
│   │   ├── components/         # 组件
│   │   │   └── CsvImporter.vue # CSV导入组件
│   │   ├── views/              # 页面
│   │   │   ├── Dashboard.vue
│   │   │   ├── Tables.vue
│   │   │   ├── Query.vue
│   │   │   └── Import.vue      # 导入页面
│   │   ├── router/             # 路由
│   │   └── utils/              # 工具类
│   └── package.json
├── CSV_IMPORT_GUIDE.md         # CSV导入使用指南
└── README.md
```

## 性能优化

### 批量导入性能优化
- **分批处理**: 默认5000条记录一批，可调整
- **连接池管理**: HikariCP连接池，支持并发
- **内存优化**: 流式处理，避免内存溢出
- **错误容错**: 部分失败不影响其他数据

### 推荐配置
- 批处理大小: 5000条（默认）
- 连接池大小: 20个连接
- 单次导入限制: 10万条记录
- 文件大小限制: 100MB

## 注意事项

### CSV导入注意事项
1. 确保CSV文件格式正确
2. 选择正确的编码格式（推荐UTF-8）
3. 重要数据建议使用事务模式导入
4. 大数据量建议分批次导入
5. 导入前建议备份目标表数据

### 系统使用建议
1. 在生产环境使用前请充分测试
2. 定期备份数据库
3. 监控系统性能和资源使用
4. 及时更新系统依赖

## 更新日志

### v1.1.0 (2024-01-XX)
- ✨ 新增CSV文件批量导入功能
- 🚀 支持百万级数据导入
- 📊 添加导入历史记录管理
- 🛡️ 增强数据验证和错误处理
- 📝 完善使用文档

### v1.0.0 (2024-01-XX)
- 🎉 初始版本发布
- 📦 基础数据库管理功能
- 🔍 SQL查询功能
- 👥 用户权限管理
- 🔧 多数据源支持

## 许可证

MIT License

## 联系方式

如有问题或建议，请提交Issue或联系开发团队。

---

**注意**: 本系统仅用于学习和开发目的，生产环境使用前请进行充分测试。
