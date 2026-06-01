# SQL 优化实战

---

## 数据库优化全景

数据库优化不能只盯着 SQL 本身，应该从上到下逐层优化，每层的收益量级不同。

### 优化层次金字塔

```
        / 架构层 \
       /  表结构   \
      /   索引层    \
     /   SQL 优化   \
    /    网络 I/O     \
   /    连接池 & 事务   \
  /     调用次数优化     \
```

| 层次 | 优化方向 | 典型收益 | 优先级 |
|------|----------|----------|--------|
| **调用次数** | 减少无效请求、合并查询、使用缓存 | 10x ~ 100x | 最高 |
| **连接池 & 事务** | 复用连接、缩小事务范围、隔离级别 | 2x ~ 10x | 高 |
| **网络 I/O** | 减少传输量、压缩、就近部署 | 2x ~ 5x | 高 |
| **SQL 优化** | 执行计划、索引利用、避免失效 | 10x ~ 1000x | 核心 |
| **索引层** | 联合索引、覆盖索引、索引设计 | 10x ~ 100x | 核心 |
| **表结构** | 字段类型、垂直/水平拆分、范式 | 2x ~ 10x | 中 |
| **架构层** | 读写分离、分库分表、异构存储 | 10x ~ 100x | 按需 |

### 一、调用次数优化

**1.1 消除 N+1 查询**

```java
// ❌ N+1 问题：先查主表，循环查子表
List<Order> orders = orderMapper.selectAll();
for (Order order : orders) {
    order.setItems(orderItemMapper.selectByOrderId(order.getId()));  // N 次查询
}

// ✅ 一次查出所有关联数据
List<Order> orders = orderMapper.selectWithItems();  // 1 次 JOIN 查询
```
```xml
<!-- MyBatis 示例：collection 映射 -->
<resultMap id="orderMap" type="Order">
    <id property="id" column="id"/>
    <collection property="items" ofType="OrderItem"
        select="selectItemsByOrderId" column="id"/>  <!-- 仍会 N+1 -->
</resultMap>

<!-- 推荐：一条 SQL 搞定 -->
<resultMap id="orderMap" type="Order">
    <id property="id" column="id"/>
    <collection property="items" ofType="OrderItem"
                resultMap="itemMap" columnPrefix="item_"/>
</resultMap>
<select id="selectWithItems" resultMap="orderMap">
    SELECT o.*, i.id AS item_id, i.name AS item_name
    FROM orders o LEFT JOIN order_items i ON o.id = i.order_id
</select>
```

**1.2 批量操作代替逐条执行**

```java
// ❌ 逐条插入（1000 次网络往返 + 1000 次事务）
for (User user : users) {
    userMapper.insert(user);
}

// ✅ 批量插入（1 次网络往返 + 1 次事务）
userMapper.batchInsert(users);
```
```sql
INSERT INTO user (name, age) VALUES
    ('张三', 20),
    ('李四', 21),
    ('王五', 22);
```

> MyBatis-Plus 自带 `saveBatch()`，注意设置合适的批次大小（建议 1000 条/批），避免拼接的 SQL 过大。

**1.3 避免循环中的重复查询**

```java
// ❌ 循环内反复查同一张表
for (Long deptId : deptIds) {
    List<User> users = userMapper.selectByDeptId(deptId);
}

// ✅ 用 IN 一次查出
List<User> users = userMapper.selectByDeptIds(new ArrayList<>(deptIds));
```

**1.4 应用层缓存减少 DB 访问**

| 缓存层级 | 适用数据 | 常用方案 |
|----------|----------|----------|
| **本地缓存** | 配置信息、字典数据、静态数据 | Caffeine、Guava Cache |
| **分布式缓存** | 热点数据、Session、频繁读少改 | Redis |
| **二级缓存** | 关联查询结果、计算缓存 | Redis + 本地缓存组合 |

```java
// 常见模式：先查缓存，未命中再查 DB
@Cacheable(value = "user", key = "#id")
public User getUser(Long id) {
    return userMapper.selectById(id);
}
```

> 缓存的核心原则：读多写少才缓存，避免缓存了频繁变更的数据导致一致性问题。

---

### 二、连接池管理

**2.1 为什么需要连接池**

MySQL 建立一条 TCP 连接的开销：三次握手 + TLS（可选）+ 认证交换，约 10~50ms。此外每次建立/断开连接还涉及线程创建销毁和内存分配。

**2.2 连接池核心参数（HikariCP）**

| 参数 | 建议值 | 说明 |
|------|--------|------|
| `maximumPoolSize` | CPU核心数 × 2 + 磁盘数 | 过大会增加数据库压力，争抢有限连接 |
| `minimumIdle` | 与 max 一致 | 避免连接创建销毁的抖动 |
| `connectionTimeout` | 30000ms | 等待连接的最大时间 |
| `idleTimeout` | 600000ms | 空闲连接存活时间 |
| `maxLifetime` | 1800000ms | 连接最大存活时间，应小于 MySQL `wait_timeout` |

> 计算公式的由来：`connections = ((core_count * 2) + effective_spindle_count)`，来自 PostgreSQL 官方建议，MySQL 同样适用。

**2.3 常见连接池问题**

- **连接泄漏**：获取连接后未 close，最终耗尽连接池 → 使用 try-with-resources
- **连接池过小**：高峰期请求排队等连接，响应变慢
- **连接池过大**：MySQL 连接数接近 `max_connections`，CPU 上下文切换开销大

---

### 三、事务优化

**3.1 缩小事务范围**

```java
// ❌ 事务中包含外部调用
@Transactional
public void createOrder(OrderDTO dto) {
    orderMapper.insert(order);          // DB 操作
    inventoryService.deduct(dto);       // 可能是 RPC 调用，耗时不确定
    sendMqMessage(order);               // 发消息，有网络 I/O
}

// ✅ 事务只包裹 DB 操作，外部调用后置
public void createOrder(OrderDTO dto) {
    orderMapper.insert(order);          // 先写库
    try {
        inventoryService.deduct(dto);   // 再调外部服务
    } catch (Exception e) {
        // 补偿逻辑
    }
}
```

**3.2 事务隔离级别选择**

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 性能 | 场景 |
|----------|------|------------|------|------|------|
| **READ UNCOMMITTED** | 是 | 是 | 是 | 最高 | 几乎不用 |
| **READ COMMITTED** | 否 | 是 | 是 | 高 | **推荐默认** |
| **REPEATABLE READ** | 否 | 否 | 是 | 中 | MySQL 默认 |
| **SERIALIZABLE** | 否 | 否 | 否 | 最低 | 强一致性需求 |

> MySQL InnoDB 在 RR 级别下通过 Next-Key Lock 实际上解决了幻读。多数业务场景 RC 足够，并发性能更好。

**3.3 事务传播行为注意事项**

```java
// 常见陷阱：REQUIRES_NEW 悬挂事务
@Transactional
public void methodA() {
    userMapper.update(user);    // 在事务 A 中
    methodB();                  // 事务 A 挂起，事务 B 独立
    // B 已提交，A 回滚不会影响 B → 数据不一致
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void methodB() {
    orderMapper.insert(order);
}
```

---

### 四、网络 I/O 优化

**4.1 减少数据传输量**

| 手段 | 效果 | 场景 |
|------|------|------|
| 避免 `SELECT *`，只查需要的列 | 减少 50%~90% 传输量 | 所有查询 |
| 分页查询限制 pageSize | 避免一次返回海量数据 | 列表查询 |
| 大字段（TEXT/BLOB）按需加载 | 大幅减少常规查询的传输量 | 文章列表、商品列表 |
| 使用更紧凑的数据类型 | 每行缩小几十字节，累积可观 | 表设计阶段 |

**4.2 MySQL 协议压缩**

```ini
# my.cnf 客户端连接配置
[client]
compress=1          # 启用压缩
compress-algorithm=zstd  # MySQL 8.0.18+ 支持 zstd，比 zlib 压缩率更高
```

> 压缩会增加 CPU 开销，适用于网络带宽是瓶颈而非 CPU 的场景（如跨机房/云端部署）。

**4.3 JDBC 参数优化**

```properties
# 不查元数据，避免每次连接额外的元数据查询
spring.datasource.hikari.data-source-properties.useInformationSchema=false

# 零日期时间行为
spring.datasource.hikari.data-source-properties.zeroDateTimeBehavior=convertToNull

# Socket 超时（网络层面的超时，比 query timeout 更底层）
spring.datasource.hikari.data-source-properties.socketTimeout=30000

# 批量执行重写（将多条 INSERT 合并为一条）
spring.datasource.url=jdbc:mysql://host:3306/db?rewriteBatchedStatements=true
```

**4.4 应用与数据库部署距离**

- 同机房 / 同 VPC：延迟 < 1ms
- 同城跨机房：延迟 1~5ms
- 异地跨城：延迟 20~50ms
- 跨运营商/公网：延迟 50ms+

> 每个 SQL 的网络往返 = 延迟 × 2（请求 + 响应）。如果一条业务逻辑需要 20 次 SQL 查询，同机房仅需 40ms，跨城则累积到 2s，这是调用次数和网络 I/O 的叠加效应。

---

### 五、索引设计优化

**5.1 联合索引的优先级**

建立联合索引时，区分度高的字段放前面：

```sql
-- phone 区分度高，status 只有 0/1/2 → phone 在前
ALTER TABLE user ADD INDEX idx_phone_status (phone, status);
```

**为什么要区分度高的放前面？**

核心原因有两个：

**1. B+ 树过滤效率** — 第一列决定了"第一刀切在哪里"

```
区分度高的在前：phone → status
WHERE phone = '13800138000' AND status = 1
→ phone 定位到 1 行 → 再检查 status → 1 次定位

区分度低的在前：status → phone
WHERE status = 1 AND phone = '13800138000'
→ status 定位到 100 万行 → 在这 100 万里二分查 phone → 扫描大量页
```

第一个字段的区分度就决定了每个索引分支下有多少数据。区分度低 → 分支少 → 每个分支数据多 → 后续搜索范围大。

**2. 最左前缀利用率** — 让索引服务更多查询

联合索引 `(phone, status)` 能服务：
- `WHERE phone = ?`
- `WHERE phone = ? AND status = ?`

而 `(status, phone)` 对 `WHERE phone = ?` 完全无效——违反最左前缀法则。

> 一句话：**区分度高的放前面，第一刀就能切掉最多数据，索引还能覆盖更多查询场景。**

**5.2 覆盖索引**

查询的所有列都在索引中，无需回表，Extra 显示 `Using index`：

```sql
-- id 是主键，包含在二级索引中 → 覆盖索引
SELECT id, name FROM user WHERE name = '张三';

-- age 不在索引中 → 需要回表
SELECT id, name, age FROM user WHERE name = '张三';
```

**5.3 索引监控**

```sql
-- 查看未被使用的索引（MySQL 8.0+）
SELECT * FROM sys.schema_unused_indexes;

-- 查看冗余索引
SELECT * FROM sys.schema_redundant_indexes;
```

---

### 六、表结构设计优化

**6.1 字段类型选择**

| 场景 | 推荐 | 避免 |
|------|------|------|
| IP 地址 | `INT UNSIGNED`（4 字节） | `VARCHAR(15)`（15 字节） |
| 固定长字符串 | `CHAR(N)` | `VARCHAR(N)` |
| 状态/枚举 | `TINYINT`（1 字节） | `VARCHAR` |
| 时间戳 | `BIGINT` 或 `DATETIME(3)` | `TIMESTAMP`（2038 问题） |
| 金额 | `DECIMAL(m,n)` | `FLOAT/DOUBLE` 会有精度误差 |

**6.2 适当冗余减少 JOIN**

```sql
-- 订单表冗余用户名，避免查订单时总是 JOIN 用户表
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    user_name VARCHAR(50),   -- 冗余字段
    amount DECIMAL(10,2)
);
```

> 冗余适合"写入少、读取多"且历史数据基本不变的场景。频繁变更的字段不要冗余。

**6.3 垂直拆分**

将不常用的字段移到扩展表，减少主表宽度：

```sql
-- 主表：高频访问字段
CREATE TABLE article (
    id BIGINT, title VARCHAR(200), summary VARCHAR(500)
);

-- 扩展表：低频访问字段
CREATE TABLE article_detail (
    article_id BIGINT PRIMARY KEY, content TEXT, attachments JSON
);
```

InnoDB 一页 16KB，行越窄，一页能装的行越多，缓存命中率越高。

---

### 七、架构层优化

**7.1 读写分离**

| 模式 | 适用场景 | 注意事项 |
|------|----------|----------|
| 一主一从 | 读多写少的中小型业务 | 主从延迟（通常 < 100ms） |
| 一主多从 | 读压力大的业务 | 从库之间负载均衡 |
| 多主架构 | 极高写入量 | 数据冲突处理复杂 |

```yaml
# 常见方案：ShardingSphere-JDBC 读写分离
spring.shardingsphere.rules.readwrite-splitting.data-sources.myds:
  write-data-source-name: master
  read-data-source-names: slave1, slave2
  load-balancer-name: round_robin
```

**7.2 分库分表**

| 维度 | 拆分策略 | 路由方式 |
|------|----------|----------|
| 水平拆分 | 按用户 ID 取模 | `hash(id) % N` |
| 垂直拆分 | 按业务域拆分 | 订单库、用户库、商品库 |
| 时间拆分 | 按年/月分表 | 订单表按月分表 `orders_202601` |

> 分库分表是最后手段。先优化 SQL、索引、缓存、读写分离，确实扛不住再考虑。

**7.3 异构数据源**

| 场景 | 方案 |
|------|------|
| 全文搜索 | MySQL → Elasticsearch |
| 大数据分析 | MySQL → ClickHouse / Doris |
| 统计报表 | MySQL → 定时汇总表 |
| 高频计数 | 用 Redis 计数器，定时刷回 DB |

---

### 优化决策顺序

遇到数据库性能问题时，按以下顺序排查和优化：

1. **调用次数** — 有没有 N+1？能合并的别分开查，能不查的缓存起来
2. **事务 & 连接池** — 事务是不是太大？连接池配置合理吗？
3. **网络 I/O** — 返回的数据量是不是太大？有没有跨机房？
4. **SQL 优化** — EXPLAIN 看执行计划，type 是不是 ALL/index？
5. **索引设计** — 索引是否合理？有没有失效？需要覆盖索引吗？
6. **表结构** — 字段类型合适吗？大字段影响主表性能吗？
7. **架构调整** — 需要读写分离吗？需要分库分表吗？

> 每一层优化的收益递减，越往上层（调用次数、缓存）收益越大，成本越低。永远先从代价最小、收益最大的方向开始。

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

JOIN 的执行过程：从驱动表逐行取数据，每取一行就去被驱动表里查一次匹配。

```
总 I/O ≈ 驱动表扫描 I/O + (驱动表行数 × 被驱动表单次索引查找 I/O)
```

关键在于**驱动表行数决定了被驱动表被查多少次**。

假设小表 1000 行，大表 100 万行，被驱动表有索引（单次查找约 2~3 次 I/O）：

| | 小表驱动大表 | 大表驱动小表 |
|------|-------------|-------------|
| 扫描驱动表 | 读 1000 行 | 读 100 万行 |
| 查被驱动表 | 1000 次 × 3 I/O = **3000 次** | 100 万次 × 3 I/O = **300 万次** |
| **总 I/O** | **≈ 3000 次** | **≈ 300 万次** |

> 同样的 JOIN 结果，驱动表越小，被驱动表被访问的次数越少，I/O 差距可达千倍。

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
