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

## EXPLAIN 执行计划实战解读

EXPLAIN 不是用来"读懂每个字段含义"的，而是用来**快速定位问题、指导优化动作**的。

**实战解读顺序**：

```
type（访问方式） → filtered（过滤效率） → Extra（额外操作） → id/select_type（查询结构）
```

先看访问方式和过滤效率（决定性能 80%），再看有没有额外开销（临时表/排序），最后才看查询结构是否需要简化。

### 一、type：这条 SQL 在怎么拿数据？

type 告诉你 MySQL 用什么方式定位数据——这是**决定性能的最重要的一个字段**。

**实战判断标准**：

| type | 含义 | 你该做什么 |
|------|------|------------|
| **system / const** | 表只有一行 / 通过主键或唯一索引精确匹配一行 | ✅ 最优，无需优化 |
| **eq_ref** | JOIN 时通过主键或唯一索引关联，每次只匹配一行 | ✅ JOIN 最优，无需优化 |
| **ref** | 通过普通索引等值查找，可能匹配多行 | ✅ 可接受，确认返回行数合理即可 |
| **range** | 索引范围扫描（BETWEEN、>、<、IN） | ⚠️ 可接受，注意范围不要太大 |
| **index** | 全索引扫描——遍历整棵索引树 | ❌ 通常需优化，见下方分析 |
| **ALL** | 全表扫描——遍历整张表 | ❌ 必须优化 |

> **记住这条线**：`ref` 及以上是健康的，`range` 看场景，`index` 和 `ALL` 是问题信号。

**type = ALL，最常见的问题——缺索引**

```sql
EXPLAIN SELECT * FROM student WHERE age = 20;
-- type: ALL, key: NULL
-- 含义：没有索引可用，扫描全表找 age=20 的行
-- 动作：加索引
ALTER TABLE student ADD INDEX idx_age (age);
-- 再次 EXPLAIN → type: ref, key: idx_age ✅
```

**type = index，为什么也要优化？**

```sql
EXPLAIN SELECT * FROM student ORDER BY name;
-- type: index, key: idx_name, Extra: NULL
-- 含义：走了 idx_name 索引，但扫描了整棵索引树
--       而且 SELECT * 需要回表拿所有列 → 实际扫描量 ≈ 全表扫描
-- 动作：如果只要部分列 → 用覆盖索引；如果业务不需要全部数据 → 加 WHERE 缩小范围
```

`index` 和 `ALL` 的区别：`index` 扫描的是二级索引（体积小），`ALL` 扫描聚簇索引（数据行）。但如果你查询的列不在索引里，`index` 也要回表，性能和 `ALL` 差不多。

**type = ref，也要看 rows**

```sql
EXPLAIN SELECT * FROM student WHERE name = '张飞';
-- type: ref, key: idx_name, rows: 50000
-- 含义：走了索引，但 name='张飞' 匹配了 5 万行
-- 动作：考虑联合索引缩小范围
ALTER TABLE student ADD INDEX idx_name_city (name, city);
-- WHERE name = '张飞' AND city = '北京' → rows 可能降到几百
```

> **type 告诉你"走了什么路"，rows 告诉你"这条路有多长"。路对了但太长，仍然需要优化。**

---

### 二、filtered + rows：索引选得对不对？

**`rows`**：MySQL 估算存储引擎需要扫描的行数。

**`filtered`**：存储引擎返回的行经过 WHERE 过滤后，**剩余的比例**。

**核心公式**：

```
实际交给下一步处理的行数 ≈ rows × filtered%
```

**为什么 filtered 越接近 100% 越好？**

```
filtered = 100%  →  引擎返回 1000 行，WHERE 过滤后还是 1000 行
                     说明索引精准命中，没有浪费

filtered = 10%   →  引擎返回 1000 行，WHERE 过滤后只剩 100 行
                     说明引擎做了 900 行的无用功，90% 的数据白查了
```

**低 filtered 的本质：索引选得不够精确，让引擎多做了大量无效扫描。**

**实战案例：为什么 filtered 低？怎么修？**

```sql
-- 场景：查询名字叫张飞且年龄 20 的学生
-- 索引：idx_name (name)

EXPLAIN SELECT * FROM student WHERE name = '张飞' AND age = 20;
-- type: ref, key: idx_name, rows: 5000, filtered: 10%
-- 分析：索引只用了 name，找到 5000 行叫"张飞"的
--       然后在这 5000 行里逐行检查 age=20，只有 500 行满足
--       4500 行白扫了 → filtered = 500/5000 = 10%
```

**修复思路：把 WHERE 中更多条件"收编"到索引里**

```sql
-- 建联合索引，把 age 也加进去
ALTER TABLE student ADD INDEX idx_name_age (name, age);

EXPLAIN SELECT * FROM student WHERE name = '张飞' AND age = 20;
-- type: ref, key: idx_name_age, rows: 500, filtered: 100%
-- 索引直接定位到 name='张飞' AND age=20 的行，无废扫描
```

> **一句话：filtered 低 = 索引没有覆盖 WHERE 的所有条件 = 有大量行被引擎扫出来又被 Server 层扔掉。解决方法就是把 WHERE 中的高频过滤条件加到联合索引里。**

**另一个常见场景：JOIN 中的 filtered**

```sql
EXPLAIN
SELECT s.name, c.name AS course_name
FROM student s
JOIN stu_course sc ON s.id = sc.sid
JOIN course c ON sc.cid = c.id
WHERE s.city = '北京';
```

如果 student 表的 `filtered = 1%`，说明：
- 引擎扫了大量学生行
- 但只有 1% 满足 `city = '北京'`
- 99% 的 JOIN 操作是白做的

**修复思路**：

```sql
-- 方案 1：给 city 加索引，让 WHERE city = '北京' 走索引直接过滤
ALTER TABLE student ADD INDEX idx_city (city);
-- EXPLAIN → type: ref, filtered: 100%（接近），只有北京的学生参与 JOIN

-- 方案 2：用子查询先过滤出北京学生的 id，再 JOIN（适合 city 无索引或数据量大的场景）
SELECT s.name, c.name AS course_name
FROM (
    SELECT id, name FROM student WHERE city = '北京'
) s
JOIN stu_course sc ON s.id = sc.sid
JOIN course c ON sc.cid = c.id;
-- 子查询先缩小驱动表到"北京学生"，再 JOIN → 减少 JOIN 次数
```

> **核心思路**：JOIN 的成本 = 驱动表行数 × 每行查被驱动表的代价。filtered 低 = 驱动表行数被白白放大了。要么让 WHERE 走索引精准过滤（方案 1），要么提前把小结果集作为驱动表（方案 2）。

---

### 三、Extra：有没有额外开销？

Extra 告诉你除了正常的数据访问之外，MySQL 还干了什么额外的事。

| Extra | 实际含义 | 你的动作 |
|-------|----------|----------|
| **Using index** | 覆盖索引，查询列全在索引里，不需要回表 | ✅ 最优 |
| **Using where** | Server 层用 WHERE 对引擎返回的行做了过滤 | ⚠️ 正常，但结合 filtered 看——如果 filtered 低，说明过滤浪费大 |
| **Using index condition** | 索引条件下推（ICP），引擎层提前用索引列过滤，减少回表 | ✅ 已优化，MySQL 5.6+ 自动触发 |
| **Using temporary** | 用了临时表（GROUP BY / DISTINCT / UNION 常见） | ❌ 需优化，GROUP BY 字段加索引或改写 |
| **Using filesort** | 额外排序（ORDER BY 字段没走索引） | ❌ 需优化，给 ORDER BY 字段加索引 |
| **Using join buffer** | 被驱动表没有索引，用了 join buffer 做块嵌套循环 | ❌ 给被驱动表的 JOIN 字段加索引 |

**Using temporary + Using filesort 同时出现的经典场景**：

```sql
EXPLAIN SELECT city, COUNT(*) FROM student GROUP BY city;
-- Extra: Using temporary; Using filesort
-- 含义：MySQL 建了临时表做分组，又做了排序（GROUP BY 默认排序）
-- 动作 1：如果不需要排序结果 → ORDER BY NULL 省掉 filesort
-- 动作 2：给 city 加索引 → 可能消除临时表
```

**Using index（覆盖索引）的价值**：

```sql
-- 查询列不在索引里 → 需要回表
EXPLAIN SELECT * FROM student WHERE name = '张飞';
-- Extra: Using where（可能）, 需要回表拿 * 的所有列

-- 只查索引包含的列 → 覆盖索引，不回表
EXPLAIN SELECT id, name FROM student WHERE name = '张飞';
-- Extra: Using index ✅
```

> 覆盖索引的代价是索引变宽（占用更多磁盘和内存），适合**高频查询**，不要为了覆盖索引无脑加列。

---

### 四、key_len：联合索引用了几列？

`key_len` 是**实际使用的索引字节数**，用来判断联合索引到底生效了几列。

**计算规则**：

- VARCHAR(N)：N × 字符集字节数（utf8mb4=4）+ 允许 NULL（+1）+ 变长类型（+2）
- INT：4 字节，BIGINT：8 字节

**实战用法**：

```sql
-- 联合索引 idx_name_age (name VARCHAR(20), age INT)，utf8mb4，NOT NULL
-- name 的 key_len = 20×4 + 2(变长) = 82
-- age 的 key_len = 4
-- 全部用上 = 82 + 4 = 86

-- 情况 1：只用了 name
EXPLAIN SELECT * FROM student WHERE name = '张飞';
-- key_len: 82 → 只用了联合索引第一列

-- 情况 2：用了 name + age
EXPLAIN SELECT * FROM student WHERE name = '张飞' AND age = 20;
-- key_len: 86 → 两列都用上了

-- 情况 3：跳过了 name，只用 age
EXPLAIN SELECT * FROM student WHERE age = 20;
-- key: NULL, key_len: NULL → 索引完全没用上，违反最左前缀
```

> **key_len 帮你验证联合索引是否按预期生效。如果 key_len 比预期小，说明有些索引列白建了。**

---

### 五、id + select_type：查询结构有没有问题？

`id` 和 `select_type` 不是用来判断性能好坏的，而是帮你**理解 MySQL 把你的 SQL 拆成了几步**，以及这些步骤的结构是否有优化空间。

**id 的规则**：

- id 越大越先执行（子查询场景）
- id 相同则从上到下执行（JOIN 场景）

**select_type 的实战意义**：

| select_type | 实战含义 | 优化动作 |
|-------------|----------|----------|
| **SIMPLE** | 单条 SELECT，没有子查询和 UNION | ✅ 结构最简，通常无需改写 |
| **PRIMARY** | 最外层 SELECT | 本身无问题，关注它内部的子查询 |
| **SUBQUERY** | SELECT 中的子查询 | ⚠️ 考虑改写为 JOIN，减少独立子查询的开销 |
| **DERIVED** | FROM 中的子查询（派生表） | ❌ 会生成临时表，考虑改写为 JOIN 或提前物化 |
| **UNION** | UNION 中的后续 SELECT | ⚠️ 确认是否需要去重，不需要则改用 UNION ALL |
| **UNION RESULT** | UNION 的合并结果 | 这是合并阶段，关注前面的步骤是否高效 |

**实战案例：DERIVED 是性能杀手**

```sql
EXPLAIN
SELECT s.name
FROM (SELECT sid FROM stu_course WHERE cid = 1) tmp
JOIN student s ON tmp.sid = s.id;
-- id=1: PRIMARY, s → type: eq_ref
-- id=2: DERIVED, stu_course → type: ref
-- select_type=DERIVED → 子查询结果存临时表 → 额外的内存/磁盘开销
```

**改写为 JOIN，消除临时表**：

```sql
EXPLAIN
SELECT s.name
FROM stu_course sc
JOIN student s ON sc.sid = s.id
WHERE sc.cid = 1;
-- select_type: SIMPLE（只有一个 SIMPLE，无子查询）
-- 无临时表，执行路径更短
```

**实战案例：SUBQUERY 改写**

```sql
-- 子查询方式
EXPLAIN SELECT * FROM student
WHERE id IN (SELECT sid FROM stu_course WHERE cid = 1);
-- id=1: PRIMARY, student
-- id=2: SUBQUERY, stu_course

-- 改写为 JOIN
EXPLAIN SELECT s.* FROM student s
JOIN stu_course sc ON s.id = sc.sid WHERE sc.cid = 1;
-- select_type: SIMPLE，结构更简单，优化器有更大优化空间
```

> **看到 SUBQUERY / DERIVED / UNION 不一定就慢，但它们是优化器发挥空间受限的信号。如果你的 SQL 已经有性能问题，优先把这些结构改写成 JOIN。**

---

### 六、possible_keys 与 key：索引为什么没被选中？

| 字段 | 含义 |
|------|------|
| **possible_keys** | MySQL 认为可用的索引列表 |
| **key** | 实际选择的索引，NULL = 没用索引 |

**实战关注点**：

```sql
-- 情况 1：possible_keys 有值，key 也有值 → 正常走了索引
-- 情况 2：possible_keys 有值，key 是 NULL → MySQL 认为全表扫描更快
--          通常是因为表小，或者索引区分度太低
-- 情况 3：possible_keys 是 NULL，key 也是 NULL → 根本没有可用索引
--          需要建索引

-- 情况 2 的常见原因：索引区分度太低
EXPLAIN SELECT * FROM student WHERE gender = 'M';
-- possible_keys: idx_gender, key: NULL
-- gender 只有 M/F 两种值，MySQL 判定全表扫描比走索引+回表更快
-- 动作：这种低区分度字段单独建索引意义不大，考虑联合索引
```

---

### 七、ref 列：索引在跟什么做比较？

ref 显示索引列在跟什么值比较：

- `const`：跟常量比较（最好）
- `列名`：跟另一张表的列比较（JOIN 场景，正常）
- `func`：跟函数结果比较（⚠️ 可能意味着索引列被函数包裹了）

```sql
-- ref = func 的警告信号
EXPLAIN SELECT * FROM student WHERE DATE(create_time) = '2026-06-01';
-- ref: func → 对索引列用了函数，索引可能失效
-- 修复：改写为范围查询
SELECT * FROM student WHERE create_time >= '2026-06-01' AND create_time < '2026-06-02';
```

---

### EXPLAIN 实战解读 Checklist

拿到一个 EXPLAIN 结果，按这个顺序逐项检查：

```
1. type 是不是 ALL 或 index？
   → ALL：缺索引或索引失效，加索引或修复失效
   → index：确认是否需要回表，考虑覆盖索引或加 WHERE 缩范围

2. filtered 是不是很低（< 30%）？
   → 低 = 索引没覆盖 WHERE 中的条件，引擎白扫了大量行
   → 把 WHERE 中更多条件加入联合索引

3. Extra 有没有 Using temporary 或 Using filesort？
   → temporary：GROUP BY/DISTINCT 字段加索引，或改写查询
   → filesort：ORDER BY 字段加索引，或利用已有索引的排序

4. key_len 是否符合预期？
   → 比预期小 = 联合索引没有用满，检查 WHERE 条件是否违反最左前缀

5. select_type 有没有 DERIVED / SUBQUERY？
   → 有的话考虑改写为 JOIN，减少临时表和子查询开销

6. rows × filtered 的乘积大不大？
   → 这是 MySQL 估算的"实际处理行数"，如果很大 → 即使 type 是 ref 也需要优化
```

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

JOIN 的核心成本公式：

```
总 I/O ≈ 驱动表扫描 + (驱动表行数 × 被驱动表单次查找 I/O)
```

**两个优化杠杆**：① 减少驱动表行数（小表驱动大表） ② 降低被驱动表单次查找代价（被驱动表 JOIN 字段加索引）。

---

### 一、被驱动表必须有索引（INLJ vs BNLJ）

**这是 JOIN 优化最高优先级的动作。**

被驱动表 JOIN 字段有索引 → 走 **Index Nested Loop Join**（INLJ），每次通过索引查找，O(log n)。

被驱动表 JOIN 字段无索引 → 走 **Block Nested Loop Join**（BNLJ），每次扫描被驱动表的一大块数据，代价飙升。

**实战案例**：

```sql
-- 订单表 100 万行，用户表 1 万行
-- 查：下过订单的用户信息

EXPLAIN SELECT u.* FROM orders o JOIN user u ON o.user_id = u.id;
```

| 情况 | Extra | 你该做什么 |
|------|-------|------------|
| `u.id` 是主键 | 走 INLJ，type: eq_ref | ✅ 正常 |
| `o.user_id` 无索引，且优化器选了 orders 做驱动表 | 可能走 BNLJ，Extra: Using join buffer | ❌ 给 `o.user_id` 加索引 |

```sql
-- 修复：给被驱动表的 JOIN 字段加索引
ALTER TABLE orders ADD INDEX idx_user_id (user_id);
```

> **怎么判断当前走的哪种 JOIN？** EXPLAIN 结果中 Extra 出现 `Using join buffer` → 走的 BNLJ → 被驱动表缺索引，优先加索引。

---

### 二、小表驱动大表

驱动表行数决定了被驱动表被查多少次。假设被驱动表有索引（单次查找 2~3 次 I/O）：

| | 小表（1000 行）驱动 | 大表（100 万行）驱动 |
|------|-------------|-------------|
| 扫描驱动表 | 1000 行 | 100 万行 |
| 查被驱动表 | 1000 × 3 = **3000 次 I/O** | 100 万 × 3 = **300 万次 I/O** |

同样的结果，I/O 差 1000 倍。

**怎么控制谁是驱动表？**

| JOIN 类型 | 驱动表由谁决定 | 你能做什么 |
|-----------|---------------|------------|
| **LEFT JOIN** | **左表固定为驱动表** | 把小表放左边：`小表 LEFT JOIN 大表` |
| **INNER JOIN** | 优化器自动选 | MySQL 通常选行数少的做驱动表，但可能选错 |

**INNER JOIN 优化器选错了怎么办？**

```sql
-- 场景：order_detail 1000 万行，product 100 行
-- 优化器误判，选了 order_detail 做驱动表

-- 方案 1：用 STRAIGHT_JOIN 强制左表驱动（MySQL 独有语法）
SELECT p.name, od.amount
FROM product STRAIGHT_JOIN order_detail od ON p.id = od.product_id;

-- 方案 2：用子查询先缩小驱动表范围
SELECT od.amount, p.name
FROM (
    SELECT * FROM order_detail WHERE create_time > '2026-06-01'
) od
JOIN product p ON od.product_id = p.id;
```

> `STRAIGHT_JOIN` 会禁用优化器的表顺序选择，只在确认优化器选错时使用，不要滥用。

---

### 三、JOIN 前先过滤，减少驱动表行数

即使驱动表选对了，如果驱动表全量扫描再 JOIN，仍然很慢。核心思路：**WHERE 能提前过滤的不要留到 JOIN 之后**。

```sql
-- ❌ 先全量 JOIN，再过滤
SELECT o.id, u.name
FROM orders o
JOIN user u ON o.user_id = u.id
WHERE o.status = 1 AND u.city = '北京';

-- ✅ 驱动表先过滤再 JOIN（优化器通常会自动做，但显式写更清晰）
SELECT o.id, u.name
FROM (
    SELECT id, user_id FROM orders WHERE status = 1
) o
JOIN (
    SELECT id, name FROM user WHERE city = '北京'
) u ON o.user_id = u.id;
```

> 实际上 MySQL 优化器多数时候会自动做"条件下推"，但遇到复杂查询（嵌套子查询、多表 JOIN）时可能下推失败。用 EXPLAIN 检查 rows 和 filtered，如果驱动表 rows 远大于预期 → 优化器没做条件过滤 → 手动改写。

---

### 四、JOIN 优化实战 Checklist

拿到一条慢 JOIN 查询，按顺序检查：

```
1. EXPLAIN 看 Extra 有没有 Using join buffer？
   → 有 = 被驱动表没走索引 → 给 JOIN 字段加索引（最高优先级）

2. 驱动表行数大不大？（看 rows 列）
   → 大 = 考虑换驱动表方向，或用 STRAIGHT_JOIN 强制
   → LEFT JOIN 时直接把小表放左边

3. 驱动表有没有提前过滤？（看 filtered 列）
   → filtered 低 = 大量行白 JOIN 了 → 加 WHERE 条件或用子查询先过滤

4. SELECT 的列是不是太多？
   → SELECT * 会阻止覆盖索引 → 只查需要的列
```
