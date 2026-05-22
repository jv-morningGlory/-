# SQL 优化实战

---

## EXPLAIN 执行计划详解

通过 `EXPLAIN` 查看 SQL 的执行计划，是优化的核心工具。

### 示例表结构

```sql
CREATE TABLE student (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(20),
  age INT(2),
  KEY idx_name (name),
  KEY idx_name_age (name, age),
  KEY idx_id_name_age (id, name, age)
);

CREATE TABLE course (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(20)
);

CREATE TABLE stu_course (
  sid INT NOT NULL,
  cid INT NOT NULL,
  PRIMARY KEY (sid, cid)
);
```

### id 列：执行顺序

- **id 不同**：值越大越先执行（子查询场景）
- **id 相同**：从上到下顺序执行（JOIN 场景）

### select_type 列：查询类型

| 类型 | 描述 |
|------|------|
| **SIMPLE** | 简单查询，不含子查询和 UNION |
| **PRIMARY** | 复杂查询中最外层的 SELECT |
| **SUBQUERY** | SELECT 中的子查询（不在 FROM 子句中） |
| **DERIVED** | FROM 子句中的子查询（派生表），结果存入临时表 |
| **UNION** | UNION 中的第二个及后续 SELECT |
| **UNION RESULT** | UNION 操作的结果集 |

### type 列：访问类型（性能核心指标）

性能从优到劣：**system > const > eq_ref > ref > range > index > ALL**

| 类型 | 描述 | 示例 |
|------|------|------|
| **system** | 表中只有一行记录（系统表或衍生表只有一行） | - |
| **const** | 通过主键或**唯一索引**查到单条记录 | `WHERE id = 10` |
| **eq_ref** | JOIN 时使用主键或唯一索引关联，每次关联只匹配一行 | `JOIN ... ON A.id = B.id` |
| **ref** | 使用**非唯一索引**查找，可能匹配多行 | `WHERE name = '张飞'`（name 有普通索引） |
| **range** | 索引**范围扫描**（BETWEEN、>、< 等） | `WHERE age > 30`（age 有索引） |
| **index** | **全索引扫描**，扫描整棵索引树，不回表 | `SELECT name FROM student`（name 有索引且查询列在索引中） |
| **ALL** | **全表扫描**，需优化 | `WHERE age > 30`（age 无索引） |

> `index` 和 `ALL` 都需要扫描全部数据，区别在于 `index` 扫描索引树（体积更小），`ALL` 扫描聚簇索引（数据行）。如果查询列不在索引中，`index` 仍需回表，性能接近 `ALL`。

### possible_keys 与 key

| 字段 | 说明 |
|------|------|
| **possible_keys** | 查询**可能使用**的索引列表 |
| **key** | 实际使用的索引，NULL 表示未使用索引 |

### key_len 列：索引使用长度

通过 `key_len` 可推断联合索引实际使用了哪些列。

**计算规则**：

- 字符串：字符数 × 字符集字节数（utf8mb4=4, utf8=3）+ 允许 NULL（+1）+ 变长类型（+2）
- 整数：TINYINT=1, SMALLINT=2, INT=4, BIGINT=8

示例：联合索引 `idx_name_age (name VARCHAR(20), age INT)`，utf8mb4，均 NOT NULL：

- `WHERE name = 'Alice'`：key_len = 20 × 4 = **80**（仅用了 name 列）
- `WHERE name = 'Alice' AND age = 10`：key_len = 80 + 4 = **84**（用了全部列）

### filtered 列（MySQL 5.7+）

存储引擎返回的数据经 WHERE 过滤后剩余行的百分比估算。`rows × filtered` 估算下一步需处理的行数。越接近 100% 说明索引效果越好。

### ref 列

显示与索引比较的列或常量，常见值：`const`（常量）、`func`（函数结果）、其他表的列名。

### Extra 列：额外信息

| 值 | 含义 | 优化建议 |
|----|------|----------|
| **Using index** | **覆盖索引**，查询列都在索引中，无需回表 | 性能极佳 |
| **Using where** | 存储引擎返回行后，Server 层再按 WHERE 过滤 | 正常 |
| **Using index condition** | **索引条件下推**（ICP，MySQL 5.6+），在存储引擎层提前过滤，减少回表 | 性能良好 |
| **Using temporary** | 使用临时表（常见于 GROUP BY、ORDER BY） | **需优化**，考虑加索引 |
| **Using filesort** | 需要额外排序操作（未走索引排序） | **需优化**，考虑 ORDER BY 字段加索引 |

---

## 索引失效场景

### 1. 左模糊匹配

```sql
-- 前缀匹配：可走索引
SELECT * FROM users WHERE name LIKE '林%';

-- 后缀/全模糊：索引失效，全表扫描
SELECT * FROM users WHERE name LIKE '%林';
```

前缀匹配时 B+ 树可以定位起始比较点；`%` 开头时无法确定起点，只能全表扫描。

### 2. 对索引列使用函数

```sql
-- 索引失效
SELECT * FROM t_user WHERE LENGTH(name) = 6;

-- MySQL 8.0+ 可创建函数索引
ALTER TABLE t_user ADD KEY idx_name_length ((LENGTH(name)));
```

### 3. 对索引列进行表达式计算

```sql
-- 索引失效
SELECT * FROM t_user WHERE id + 1 = 10;

-- 重写条件，可走索引
SELECT * FROM t_user WHERE id = 9;
```

### 4. 隐式类型转换

```sql
-- phone 为 VARCHAR，用数字查询 → 索引失效
-- 相当于 CAST(phone AS SIGNED) = 1300000001
SELECT * FROM t_user WHERE phone = 1300000001;

-- id 为 INT，用字符串查询 → 仍可走索引
-- MySQL 会将字符串自动转为数字
SELECT * FROM t_user WHERE id = '1';
```

### 5. 违反最左前缀法则

联合索引 `(a, b, c)` 的有效匹配：

| 查询条件 | 是否走索引 |
|----------|------------|
| `a = 1` | 走索引（a） |
| `a = 1 AND b = 2` | 走索引（a, b） |
| `a = 1 AND b = 2 AND c = 3` | 走索引（a, b, c） |
| `b = 2` | 不走索引 |
| `b = 2 AND c = 3` | 不走索引 |
| `a = 1 AND c = 3` | 走索引（a），MySQL 5.6+ ICP 优化可在引擎层过滤 c |

### 6. OR 条件中有无索引的列

```sql
-- id 有索引，age 无索引 → 全表扫描
SELECT * FROM t_user WHERE id = 1 OR age = 18;

-- 优化方案 1：为 OR 两边的列都建索引
ALTER TABLE t_user ADD INDEX idx_age(age);

-- 优化方案 2：改用 UNION ALL
SELECT * FROM t_user WHERE id = 1
UNION ALL
SELECT * FROM t_user WHERE age = 18;
```

### 7. IS NULL / IS NOT NULL

在低版本 MySQL 中 `IS NULL` 和 `IS NOT NULL` 无法使用索引。高版本已优化，通常可以走索引。

> 建议：索引列尽量设为 NOT NULL，使用默认值代替。

---

## 深度分页优化

### LIMIT 的两种形式

```sql
-- 形式 1：从第 0 条开始取 size 条
SELECT * FROM page ORDER BY id LIMIT 10;

-- 形式 2：从 offset 条开始取 size 条
SELECT * FROM page ORDER BY id LIMIT 6000000, 10;
```

### 为什么大 offset 慢？

**基于主键索引**：InnoDB 读取前 `(offset + size)` 条完整行数据 → Server 层丢弃前 offset 条 → 返回剩余 size 条。

**基于非主键索引**：更慢。先通过二级索引读 `(offset + size)` 条记录 → **逐条回表**获取完整数据 → Server 层丢弃前 offset 条。

### 优化方案

**方案 1：记录上次查询位置（推荐）**

```sql
-- 第一页
SELECT * FROM page ORDER BY id LIMIT 10;

-- 后续页面（记住上一页最后一条记录的 id）
SELECT * FROM page WHERE id > 上一页最后ID ORDER BY id LIMIT 10;
```

查询速度稳定，不受页码影响，时间复杂度始终 O(log n)。

**方案 2：子查询优化**

```sql
SELECT * FROM page
WHERE id >= (
    SELECT id FROM page ORDER BY id LIMIT 6000000, 1
)
ORDER BY id LIMIT 10;
```

子查询只查主键 ID（走覆盖索引），减少回表。性能提升约 50%，但仍是治标方案。

**方案 3：业务层面优化**

- 限制最大翻页深度
- 使用"加载更多"代替页码跳转（如抖音、Twitter 瀑布流）
- 只提供"上一页/下一页"导航
- 历史数据归档，只对热点数据提供分页
- 搜索场景考虑使用 Elasticsearch

### 方案对比

| 方案 | 性能 | 复杂度 | 适用场景 |
|------|------|--------|----------|
| `LIMIT offset, size` | 差（随 offset 增大急剧下降） | 低 | 小数据量（< 1 万条） |
| 子查询优化 | 中（提升有限） | 中 | 临时方案 |
| 记录位置 | 优（稳定高效） | 中 | **推荐方案** |
| 业务限制 | 优 | 低 | 所有场景 |

> 深度分页没有银弹，最有效的方法是从业务源头避免这种需求。

---

## 子查询优化

MySQL 优化器对子查询处理能力较弱，建议改写为 JOIN：

```sql
-- 不推荐：子查询
SELECT a.name,
  (SELECT age FROM table_1 WHERE id = a.id) AS age
FROM table_2 a;

-- 推荐：JOIN 连接
SELECT a.name, b.age
FROM table_2 a
LEFT JOIN table_1 b ON a.id = b.id;
```

JOIN 效率更高的原因：MySQL 不需要在内存中创建临时表。

---

## UNION vs UNION ALL

两者都会在内存中拼接多个查询的结果。

| 操作 | 去重 | 排序 | 性能 |
|------|------|------|------|
| **UNION** | 先排序后去重 | 有 | 较差 |
| **UNION ALL** | 不去重 | 无 | 较好 |

> 优化思路：减少子查询返回的数据量，精确限制子查询范围。确定不需要去重时优先用 UNION ALL。

---

## IN vs EXISTS

| 场景 | 推荐 | 原理 |
|------|------|------|
| **外表小、内表大** | `IN` | 先执行子查询得到结果集，再对外表逐行匹配 |
| **外表大、内表小** | `EXISTS` | 对外表逐行执行子查询，利用索引快速判断 |

```sql
-- 外表小用 IN
SELECT * FROM orders WHERE user_id IN (SELECT id FROM users WHERE status = 1);

-- 外表大用 EXISTS
SELECT * FROM large_table t1 WHERE EXISTS (SELECT 1 FROM small_table t2 WHERE t1.key = t2.key);
```

> 注意：`NOT IN` 不会走索引，可改用 `NOT EXISTS` 或 `LEFT JOIN ... WHERE right.id IS NULL`。

---

## HAVING vs WHERE

- **WHERE**：在数据检索时过滤，减少后续处理的数据量
- **HAVING**：在检索出所有记录**并分组排序后**才过滤

> 应优先用 WHERE 提前过滤，减少 HAVING 处理的数据量。HAVING 仅用于过滤**聚合后的结果**。

---

## GROUP BY 隐式排序

MySQL 默认会对 GROUP BY 的字段进行排序。如果不需要排序结果，可以加 `ORDER BY NULL` 禁用：

```sql
SELECT goods_id, COUNT(*) FROM t GROUP BY goods_id ORDER BY NULL;
```

---

## 避免 SELECT *

- 多余的列浪费 CPU、内存、网络带宽资源
- 无法使用覆盖索引优化
- 存在潜在安全风险，暴露所有字段

---

## OR 替换为 IN

```sql
-- 不推荐
SELECT * FROM t WHERE id = 10 OR id = 20 OR id = 30;

-- 推荐
SELECT * FROM t WHERE id IN (10, 20, 30);
```

IN 更简洁，且 MySQL 对 IN 列表的优化通常优于 OR。

---

## JOIN 优化

### 小表驱动大表

JOIN 查询的 I/O 消耗公式：

```
总 I/O ≈ 驱动表扫描 I/O + (驱动表行数 × 被驱动表单次访问 I/O)
```

小表作为驱动表时，驱动表行数更少，被驱动表访问次数也更少。

| 操作 | 小表驱动大表 | 大表驱动小表 |
|------|-------------|-------------|
| 驱动表扫描 | 1 次 I/O（全内存） | 需多次 I/O |
| 被驱动表访问 | 1000 次索引查找 | 100 万次索引查找 |
| **总 I/O** | **≈ 201 次** | **≈ 200,100 次** |

### 三种 Nested Loop Join 模型

| 模型 | 条件 | 原理 | 性能 |
|------|------|------|------|
| **SNLJ**（Simple NLJ） | 被驱动表无索引 | 驱动表每行都触发被驱动表**全表扫描** | 极差，MySQL 已弃用 |
| **BNLJ**（Block NLJ） | 被驱动表无索引 | 驱动表每次读取**一批**数据到 join buffer，减少被驱动表扫描次数 | 中等 |
| **INLJ**（Index NLJ） | 被驱动表有索引 | 被驱动表通过**索引查找**，无需全表扫描 | 最好 |

### MySQL 优化器的驱动表选择

| JOIN 类型 | 驱动表 | 说明 |
|-----------|--------|------|
| **INNER JOIN** | 优化器自动选择 | 不影响结果，MySQL 有完全自主选择权 |
| **LEFT JOIN** | 左表固定为驱动表 | 调换顺序会影响结果 |

### 优化建议

- 确保被驱动表的 JOIN 字段有索引（走 INLJ）
- 用小表驱动大表
- 减少 JOIN 的字段数量，避免 `SELECT *`
