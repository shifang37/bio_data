# CSV导入策略功能测试指南

## 功能概述

本次更新为CSV导入功能添加了两种导入策略：

1. **追加模式（append）**：检测表中的数据和CSV文件数据，将不同的数据追加导入
2. **覆盖模式（overwrite）**：直接覆盖原有表中的所有数据

## 测试准备

### 1. 准备测试数据

创建一个测试表（如果不存在）：
```sql
CREATE TABLE test_import (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100),
    email VARCHAR(100),
    age INT
);
```

插入一些初始数据：
```sql
INSERT INTO test_import (name, email, age) VALUES 
('张三', 'zhangsan@test.com', 25),
('李四', 'lisi@test.com', 30),
('王五', 'wangwu@test.com', 28);
```

### 2. 准备CSV测试文件

#### 文件1：test_append.csv（追加模式测试）
```csv
name,email,age
张三,zhangsan@test.com,25
赵六,zhaoliu@test.com,32
钱七,qianqi@test.com,29
```

#### 文件2：test_overwrite.csv（覆盖模式测试）
```csv
name,email,age
新用户1,newuser1@test.com,22
新用户2,newuser2@test.com,27
新用户3,newuser3@test.com,31
```

## 测试步骤

### 测试1：追加模式测试

1. 访问系统的"数据导入"页面
2. 上传 `test_append.csv` 文件
3. 选择目标数据库和表（test_import）
4. 配置解析参数（UTF-8编码，逗号分隔符，包含标题行）
5. 预览数据并配置列映射
6. 在导入设置中选择：
   - **导入策略**：追加模式
   - **导入模式**：普通模式或事务模式
7. 点击"开始导入"

**预期结果**：
- 系统应该检测到"张三"的记录已存在，跳过该记录
- 成功导入"赵六"和"钱七"两条新记录
- 导入结果显示：
  - 总记录数：3
  - 成功导入：2
  - 跳过重复：1
  - 导入策略：追加模式

### 测试2：覆盖模式测试

1. 上传 `test_overwrite.csv` 文件
2. 选择相同的目标数据库和表
3. 配置相同的解析参数
4. 在导入设置中选择：
   - **导入策略**：覆盖模式
   - **导入模式**：普通模式或事务模式
5. 点击"开始导入"

**预期结果**：
- 系统应该先删除表中的所有数据
- 然后导入CSV文件中的3条新记录
- 导入结果显示：
  - 总记录数：3
  - 成功导入：3
  - 删除记录：[之前表中的记录数]
  - 导入策略：覆盖模式

## 验证方法

### 数据库查询验证

测试前查询表数据：
```sql
SELECT * FROM test_import ORDER BY id;
```

追加模式测试后查询：
```sql
SELECT * FROM test_import ORDER BY id;
-- 应该看到原有数据 + 新增的"赵六"和"钱七"
```

覆盖模式测试后查询：
```sql
SELECT * FROM test_import ORDER BY id;
-- 应该只看到CSV文件中的3条新记录
```

## 边界情况测试

### 1. 无主键表测试

创建一个没有主键的表：
```sql
CREATE TABLE test_no_pk (
    name VARCHAR(100),
    email VARCHAR(100),
    age INT
);
```

测试追加模式是否能正确使用全行比较检测重复。

### 2. 复合主键表测试

创建复合主键表：
```sql
CREATE TABLE test_composite_pk (
    user_id INT,
    group_id INT,
    role VARCHAR(50),
    PRIMARY KEY (user_id, group_id)
);
```

测试追加模式是否能正确使用复合主键检测重复。

### 3. 全部重复数据测试

使用完全相同的CSV文件进行追加模式导入，验证系统是否正确跳过所有记录。

### 4. 空CSV文件测试

上传空的CSV文件，验证系统的错误处理。

## 性能测试

### 大数据量测试

1. 准备包含1万条记录的CSV文件
2. 表中预先插入5000条记录，其中2000条与CSV重复
3. 测试追加模式的性能和准确性

## 注意事项

1. **事务模式 vs 普通模式**：
   - 事务模式：如果任何一条记录插入失败，整个操作回滚
   - 普通模式：部分记录可能成功，部分可能失败

2. **重复检测逻辑**：
   - 有主键：基于主键值检测重复
   - 无主键：基于所有列的值检测重复（包括NULL值处理）

3. **覆盖模式警告**：
   - 覆盖模式会删除表中所有数据，使用前请确认
   - 建议在重要数据上先进行备份

## 故障排除

如果遇到问题，请检查：

1. **后端日志**：查看服务器日志中的详细错误信息
2. **浏览器控制台**：检查前端JavaScript错误
3. **数据库权限**：确保用户有DELETE和INSERT权限
4. **表结构**：确保CSV列与数据库列匹配

## 新增功能总结

### 前端更新
- 在导入设置中添加了"导入策略"选项
- 导入确认对话框显示选择的策略信息
- 导入结果显示跳过的重复记录数和删除的记录数

### 后端更新
- 新增 `batchInsertTableDataWithStrategy` 方法支持策略选择
- 新增 `batchInsertTableDataTransactionWithStrategy` 方法支持事务性策略导入
- 实现覆盖模式：先DELETE后INSERT
- 实现追加模式：基于主键或全行比较检测重复
- 支持复合主键和无主键表的重复检测