# 牛客网 Spring Boot 实战面试 100 题（下）

---

## 十、分布式与微服务（10 题）

### 71. 你把单体项目拆成微服务的依据是什么？你是怎么划分服务边界的？

> **核心观点：拆微服务不是因为"微服务很火"，而是单体已经实实在在拖后腿了。拆分边界不是拍脑袋，是用 DDD 的限界上下文来切。**

---

#### 一、什么时候该拆？—— 单体的"疼痛信号"

不是所有项目都要拆。以下 4 个信号，**出现 2 个以上才考虑拆**：

| 疼痛信号 | 具体表现 | 为什么单体搞不定 |
|---|---|---|
| **团队膨胀** | 10+ 人同时改一个代码库，Git 冲突天天有，每次发布要协调所有人 | 单体代码库没有物理隔离，一个模块改崩了，所有人的发布都被阻塞 |
| **性能局部热点** | 订单模块 CPU 常年 80%，用户模块却很闲，但只能整体扩容 | 单体是"一个 war 包部署"，无法按模块独立扩缩容，造成资源浪费 |
| **业务耦合蔓延** | 改订单逻辑不小心把用户模块的查询搞挂了，测试不敢只测自己模块 | 没有代码层面的强制边界，依赖容易腐化，最终变成"大泥球" |
| **交付速度下降** | 一个新功能从开发到上线要 2 周，因为每次都要全量回归测试 | 单体 CI/CD 是全量构建 + 全量测试，代码量越大越慢 |

**面试话术**：先说"我不建议项目一开始就上微服务"，再列出上面的判断标准，面试官会觉得你务实、有经验。

---

#### 二、怎么划分服务边界？—— DDD 限界上下文

拆分微服务最怕的是**切错了边界**，导致"分布式单体"——服务拆了，但调用链跟单体一样紧耦合，改了 A 服务，B、C 也得跟着改。

业界公认的做法是用 **DDD（领域驱动设计）** 来指导拆分，核心工具是**限界上下文（Bounded Context）**。

##### 2.1 先理解几个关键概念

| 概念 | 一句话解释 | 类比 |
|---|---|---|
| **领域（Domain）** | 你要解决的整个业务范围 | 整个电商系统 |
| **子域（Subdomain）** | 领域中相对独立的一块业务 | 订单、商品、用户、支付 |
| **限界上下文（Bounded Context）** | 一个子域中，某个概念有明确含义的范围 | "用户"在认证上下文里指账号密码，在会员上下文里指等级积分 |
| **通用语言（Ubiquitous Language）** | 团队内部统一的业务术语，代码和对话都用同一套语言 | 不要叫 `User` 又叫 `Member` 又叫 `Account` |

##### 2.2 拆分的具体步骤

```
第一步：事件风暴（Event Storming）—— 和产品、业务方一起梳理业务流程

   "用户下单"这个动作涉及哪些东西？
   用户 → 浏览商品 → 加购物车 → 下单 → 支付 → 减库存 → 发货

第二步：识别子域和限界上下文

   把上面的流程按"业务能力"分组：

   商品上下文：商品信息、SKU、库存（注意：这里的库存是"可售库存"）
   订单上下文：订单创建、订单状态流转
   支付上下文：支付单、退款、对账
   用户上下文：用户基本信息、认证、会员等级
   物流上下文：发货、物流轨迹

第三步：确定上下文之间的关系（Context Mapping）

   上下文之间怎么协作？用图画出关系：
   - 上游/下游（U/D）：订单是商品的下游，依赖商品提供的信息
   - 防腐层（ACL）：对接外部系统时加一层隔离，比如对接第三方支付
   - 共享内核（Shared Kernel）：两个上下文共享一部分模型（尽量少用）
   - 发布/订阅（事件驱动）：用消息队列解耦，比如"订单已支付"→ 发事件 → 库存扣减

第四步：一个限界上下文 = 一个微服务，独立数据库

   商品服务 → 商品库
   订单服务 → 订单库
   支付服务 → 支付库
   每个服务只能访问自己的数据库，数据交互通过 API 或消息队列
```

##### 2.3 具体案例：电商系统如何划分

假设你有一个电商单体，包含用户、商品、订单、支付、物流这些模块。用 DDD 拆成下面这样：

```yaml
核心域（你的核心竞争力，重点投入）:
  - 订单服务: 订单创建、状态机流转、订单查询
  - 商品服务: 商品信息、SKU 管理、价格管理

支撑域（辅助核心业务运转）:
  - 支付服务: 对接支付宝/微信，支付单管理、退款
  - 用户服务: 注册登录、用户信息、会员等级
  - 物流服务: 发货单、物流轨迹查询

通用域（可以用现成方案的）:
  - 消息通知: 短信、邮件、App Push
  - 文件存储: OSS/MinIO
```

微服务之间的调用关系（不是画全图，是说清楚依赖方向）：

- 订单服务**同步调用**商品服务（下单时需要查价格、验库存）
- 订单服务**同步调用**用户服务（下单时需要验证用户状态）
- 订单创建成功后**发送异步事件** → 支付服务监听、商品服务扣库存
- 支付服务支付成功**发送异步事件** → 订单服务改状态、物流服务创建发货单

---

#### 三、拆分时的硬原则（背下来，面试必用）

| 原则 | 含义 | 为什么重要 |
|---|---|---|
| **一个服务一个数据库** | 每个微服务独占数据库，不允许直接连别人的库 | 如果不隔离数据库，服务之间可以通过 SQL JOIN 绕过 API，边界就白划了 |
| **高内聚、低耦合** | 一个上下文内变更频繁的东西放一起，跨上下文变更少的才分开 | 避免"改一个功能要动 3 个服务"的噩梦 |
| **数据主权** | 谁拥有这个数据，谁负责修改。其他服务想改，必须通过 API | 用户服务拥有用户手机号，订单服务想改？不行，调用户服务的接口 |
| **康威定律** | 系统架构会镜像团队沟通结构 | 如果两个模块由同一个小组维护，可以晚点再拆；跨组维护的就应该拆成独立服务 |
| **先粗后细** | 刚开始别拆太细，3-5 个服务足够 | 粒度太细会导致"微服务地狱"：分布式事务、链路追踪、运维复杂度爆炸 |

---

#### 四、面试总结话术

> "判断要不要拆，我主要看四个信号：团队规模是否导致频繁冲突、是否有局部性能热点需要独立扩缩容、业务耦合是否影响了交付速度、以及是否存在明显的业务边界。
>
> 拆分边界我用 DDD 的限界上下文来指导——先做事件风暴梳理业务流程，按业务能力聚类识别子域，然后一个限界上下文对应一个微服务，每个服务独占数据库。核心原则是高内聚低耦合和数据主权，尽量避免分布式事务，能用异步事件解耦的就用事件。
>
> 另外我不会一上来就拆得很细，通常先拆 3-5 个核心服务跑一段时间，验证边界合理后再继续细化。"


### 72. 服务注册与发现是怎么工作的？Nacos 和 Eureka 有什么核心区别？
### 73. 负载均衡策略有哪些？Ribbon 的轮询、随机、加权轮询分别怎么用？
### 74. 服务间调用（OpenFeign / Dubbo）你怎么选的？底层原理是什么？
### 75. 微服务网关（Gateway / Zuul）的作用是什么？你在网关层做了哪些事（鉴权、限流、日志、路由）？
### 76. 如何实现一个分布式 ID 生成器？雪花算法有什么优缺点？时钟回拨怎么处理？
### 77. 分布式 Session 怎么解决？Spring Session + Redis 的原理是什么？
### 78. 分布式锁除了 Redis 实现还有哪些方案？ZooKeeper 实现和 Redis 实现各有什么优缺点？
### 79. CAP 定理和 BASE 理论怎么理解？在你的项目里是怎么权衡的？
### 80. 微服务链路追踪怎么实现？TraceID 如何在服务间传递？

## 十一、安全与鉴权（4 题）

### 81. Spring Security 的认证和授权流程是怎样的？过滤器链里有哪几个关键的 Filter？
### 82. 无状态登录（JWT Token）怎么实现？Token 过期刷新机制你怎么设计的？
### 83. 如何防止 CSRF 和 XSS 攻击？Spring Security 默认做了什么？你还做了哪些额外防护？
### 84. OAuth2.0 的授权码模式和密码模式有什么区别？你在项目中用的是哪种？

## 十二、性能优化与监控（6 题）

### 85. Spring Boot 应用启动慢怎么排查和优化？
### 86. 线上接口响应突然变慢，你的排查思路是什么？
### 87. 怎么定位 JVM 的 CPU 飙高和内存泄漏问题？用什么工具？
### 88. Spring Boot Actuator 你用了哪些端点？如何自定义健康检查？
### 89. 如何建设一个统一的日志收集和告警体系？你的项目里是怎么做的？
### 90. 线上出了问题你怎么快速回滚和止损？有什么预案？

## 十三、场景设计题（7 题）

### 91. 如何设计一个秒杀系统？从限流、库存扣减、下单、支付整个链路说说你的方案。

> 详见：[如何设计一个秒杀系统](../场景题/如何设计一个秒杀系统.md)

### 92. 接口的 QPS 从 1000 突然涨到 10000，你有哪些手段保证系统不崩？

> **核心思路**：不要硬扛，分层设防——能挡的挡在外面，能等的排队慢慢处理，实在扛不住了也能体面降级。

---

**全景防御链（从外到内）：**

```
用户请求
  → ① CDN / 静态化 —— 能用缓存顶的绝不打到服务端
  → ② 限流 —— 超出能力的请求直接拒绝，保护系统
  → ③ 缓存 —— Redis 扛读，别让流量穿透到 DB
  → ④ 削峰填谷 —— MQ 把突发请求熨平，慢慢消费
  → ⑤ 降级 —— 非核心功能关掉，保核心链路
  → ⑥ 扩容 —— 加机器，但这需要时间
```

---

**① 限流 —— 第一道防线，最重要的手段**

| 限流算法 | 原理 | 场景 |
|---------|------|------|
| **令牌桶** | 固定速率放令牌，请求拿不到令牌就拒绝 | 容忍突发，最常用 |
| **漏桶** | 请求进桶，固定速率流出 | 严格平滑，不允许突发 |
| **滑动窗口** | 统计最近 N 秒的请求数，超过阈值拒绝 | 简单精确，Sentinel 默认 |

```java
// Sentinel 限流 —— 注解一行搞定
@SentinelResource(
    value = "getOrderDetail",
    blockHandler = "rateLimitBlock"   // 被限流时走这个方法
)
public OrderDetail getOrderDetail(String orderId) {
    return orderService.getDetail(orderId);
}

// 限流后的处理：返回提示
public OrderDetail rateLimitBlock(String orderId, BlockException e) {
    throw new BizException("系统繁忙，请稍后重试");
}
```

```yaml
# Sentinel 规则（控制台/配置文件）
# QPS 超过 8000 就限流
- resource: getOrderDetail
  grade: QPS
  count: 8000           # 单机 QPS 上限
  controlBehavior: 0    # 直接拒绝
```

**面试要点**：限流不是为了让用户体验更好，是**为了保护系统不被冲垮**——少部分人被拒绝，好过所有人一起挂。

---

**② 缓存 —— 把流量挡在 DB 前面**

> 10 倍流量打过来，绝大部分是读。Redis 单机扛 10w+ QPS 很轻松，MySQL 可能几千就顶不住了。

```
用户 → Redis（有就返回） → 没有 → MySQL → 回写 Redis
        ↑ 99% 命中                        ↑ 1% 穿透
```

| 手段 | 做什么 |
|------|--------|
| **热点数据预加载** | 凌晨把今天要用的数据提前加载到 Redis |
| **多级缓存** | 本地 Caffeine（微秒级）→ Redis（毫秒级）→ DB |
| **缓存不过期** | 热点 Key 逻辑过期（异步刷新），不设物理 TTL |
| **布隆过滤器** | 拦截不存在的 Key，防缓存穿透打到 DB |

---

**③ 削峰填谷 —— MQ 把突发请求熨平**

> 限流是"直接拒绝"，MQ 是"排队等着"。写操作不适合直接拒绝（用户下单总不能说"系统繁忙"就丢了），应该用 MQ 缓冲。

```
瞬时 1w QPS → 1w 条消息进 MQ → 消费者稳定 2000/s 消费 → 5 秒处理完
                                   ↑ 峰值被熨平了
```

```java
// 下单不直接写 DB，先丢 MQ
@PostMapping("/order")
public Result createOrder(@RequestBody OrderDTO order) {
    // 快速校验后直接发 MQ，立即返回
    rocketMQTemplate.send("order-topic", order);
    return Result.ok("下单成功，处理中");
}

// 消费者稳速处理
@RocketMQMessageListener(topic = "order-topic")
public class OrderConsumer {
    @Override
    public void onMessage(OrderDTO order) {
        orderService.process(order);  // 按自己的节奏处理
    }
}
```

---

**④ 降级 —— 保证核心链路**

| 降级手段 | 做什么 | 例子 |
|---------|--------|------|
| **关非核心功能** | 流量暴涨时关掉推荐、评论、积分等 | 商品详情页只保留商品信息和库存 |
| **返回缓存/默认值** | 不调上游了，用上次的结果 | 用户昵称显示默认"用户***" |
| **读本地缓存** | Redis 挂了就读本地存的一份 | 配置信息本地存一份 |
| **熔断隔离** | 下游扛不住直接断掉，不拖累主流程 | 推荐服务超时就直接返回空列表 |

```java
// 统一降级开关（配置中心控制）
@Value("${degrade.recommend:false}")
private boolean degradeRecommend;

public List<Recommend> getRecommend(String productId) {
    if (degradeRecommend) {
        return Collections.emptyList();   // 降级：直接返回空
    }
    return recommendService.get(productId);
}
```

---

**⑤ 扩容 —— 最后的底牌**

| 方式 | 速度 | 说明 |
|------|------|------|
| **K8s HPA** | 分钟级 | 自动检测 CPU/内存，自动加 Pod |
| **提前预留资源** | 秒级 | 平时低配跑，大促前手动扩 |
| **弹性伸缩** | 分钟级 | 云厂商按量付费，平时不用留 |

---

**⑥ 面试回答框架：分层设防**

```
问：QPS 从 1000 涨到 10000 怎么办？

答：分层设防，从外到内四层：

第一层限流——Sentinel 设定单机 QPS 上限，超出的直接拒绝，
        返回"系统繁忙"，保护核心链路不被冲垮。

第二层缓存——Redis 扛读流量，热点数据预加载 + 逻辑过期，
         99% 流量挡在 DB 前面。Redis 单机 10w QPS 无压力。

第三层削峰——写操作丢 MQ（RocketMQ/Kafka），消费者按自己
         的节奏慢慢处理，峰值被熨平。

第四层降级——非核心功能关掉（推荐、评论），保核心商品和
         下单链路，返回缓存兜底。
```

| 层 | 手段 | 一句话 |
|----|------|--------|
| 1 | **限流** | 超出的直接拒，保系统不崩 |
| 2 | **缓存** | 读流量 Redis 扛，别透到 DB |
| 3 | **削峰** | 写流量进 MQ，排队慢慢处理 |
| 4 | **降级** | 关非核心，保核心链路 |

### 93. 如何设计一个短链接生成系统？

> 详见：[如何设计一个短链接生成系统](../场景题/如何设计一个短链接生成系统.md)

### 94. 如何设计一个分布式定时任务调度系统？

> 详见：[如何设计一个分布式定时任务调度系统](../场景题/如何设计一个分布式定时任务调度系统.md)

### 95. 一个接口依赖多个上游服务，怎么设计才能保证高可用（兜底、降级、熔断）？

> **问题场景**：商品详情接口依赖 5 个上游——商品服务、库存服务、用户服务、推荐服务、评论服务。任意一个挂了，页面就 500？显然不行。

---

**① 三步走：先搞清楚谁是核心、谁可牺牲**

第一步不是写代码，是把依赖分成三类：

| 等级 | 依赖 | 挂了怎么办 | 例子 |
|------|------|-----------|------|
| **P0 强依赖** | 缺了它整个接口没意义 | 必须兜底，否则整体失败 | 商品服务（没商品数据看啥？） |
| **P1 弱依赖** | 缺了影响体验但页面还能看 | 静默降级 | 推荐服务、评论服务 |
| **P2 可异步** | 跟当前请求无关 | 异步处理，失败不影响本次 | 埋点上报、访问记录 |

**依赖梳理表（以商品详情页为例）：**

| 上游服务 | 依赖等级 | 超时时间 | 降级策略 |
|---------|---------|---------|---------|
| 商品服务 | P0 强依赖 | 2s | 读 Redis 缓存 |
| 库存服务 | P0 强依赖 | 1s | 缓存兜底，都不行就显示"已售罄" |
| 用户服务 | P1 弱依赖 | 500ms | 缓存/默认昵称"用户***" |
| 推荐服务 | P1 弱依赖 | 500ms | 返回空列表 |
| 评论服务 | P1 弱依赖 | 500ms | 返回空列表，提示"评论加载中" |

---

**② 三大武器：超时、熔断、降级，缺一不可**

```
请求来了
  → 调商品服务（P0） → 超时 2s → 熔断打开 → 降级：读缓存
  → 调库存服务（P0） → 超时 1s → 熔断打开 → 降级：缓存/默认值
  → 调用户服务（P1） → 超时 500ms → 熔断打开 → 降级：默认昵称
  → 调推荐服务（P1） → 成功返回
  → 调评论服务（P1） → 熔断半开中 → 降级：空列表
  → 组装结果返回（至少商品信息必须有）
```

| 手段 | 做什么 | 解决什么问题 |
|------|------|------------|
| **超时** | 等太久就别等了 | 防止线程池被耗尽的雪崩 |
| **熔断** | 连续失败就断掉，不调了 | 保护自己不被下游拖死 |
| **降级** | 断掉后返回什么 | 保证用户至少能看到东西 |

> **关键**：三个必须一起用。只设超时不熔断 = 每次还是等满超时，资源照样耗光；只熔断不降级 = 断了就报错，用户体验差。

---

**③ 实际代码：Sentinel 实现**

```java
@Service
public class ProductDetailService {

    // 并行调用多个上游，统一超时
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public ProductDetailVO getDetail(String productId) {
        // 并行调上游，设置超时
        CompletableFuture<Product> productFuture = asyncGet(productId, () ->
            productFeignClient.get(productId), 2000);
        CompletableFuture<Stock> stockFuture = asyncGet(productId, () ->
            stockFeignClient.getStock(productId), 1000);
        CompletableFuture<UserInfo> userFuture = asyncGet(productId, () ->
            userFeignClient.getUser(userId), 500);
        CompletableFuture<List<Recommend>> recFuture = asyncGet(productId, () ->
            recommendFeignClient.getRecommend(productId), 500);
        CompletableFuture<List<Comment>> cmtFuture = asyncGet(productId, () ->
            commentFeignClient.getComments(productId), 500);

        // 组装结果：P0 强依赖必须成功，P1 失败用降级值
        try {
            Product product = productFuture.get(2, TimeUnit.SECONDS);
            Stock stock = stockFuture.get(1, TimeUnit.SECONDS);
            return ProductDetailVO.builder()
                .product(product)
                .stock(stock)
                .userInfo(getOrDefault(userFuture, UserInfo.defaultUser()))
                .recommends(getOrDefault(recFuture, Collections.emptyList()))
                .comments(getOrDefault(cmtFuture, Collections.emptyList()))
                .build();
        } catch (Exception e) {
            // P0 依赖兜底也失败了，返回降级页面
            return ProductDetailVO.fallback(productId);
        }
    }

    private <T> T getOrDefault(Future<T> future, T fallback) {
        try { return future.get(500, TimeUnit.MILLISECONDS); }
        catch (Exception e) { return fallback; }
    }
}
```

```java
// Sentinel 熔断 + 降级 —— 注解版（也可用这种方式）
@SentinelResource(
    value = "getProduct",
    fallback = "getProductFallback",    // 降级方法
    fallbackClass = ProductFallback.class
)
public Product getProduct(String productId) {
    return productFeignClient.get(productId);
}

// 降级方法
public Product getProductFallback(String productId, Throwable t) {
    // 1. 先查 Redis 缓存
    Product cached = redisTemplate.opsForValue().get("product:" + productId);
    if (cached != null) return cached;

    // 2. 缓存也没有，返回静态兜底
    log.warn("商品服务不可用，返回兜底数据 productId={}", productId);
    return Product.empty();
}
```

```yaml
# Sentinel 熔断规则（控制台/配置文件）
# 1 分钟内调用超过 5 次，且异常率 > 50% → 熔断 10 秒
- resource: getProduct
  grade: EXCEPTION_RATIO    # 按异常比例
  count: 0.5                # 异常率 > 50%
  minRequestAmount: 5       # 最小请求数
  statIntervalMs: 60000     # 统计窗口 1 分钟
  timeWindow: 10            # 熔断 10 秒
```

---

**④ 再加两道保险**

| 保险 | 做什么 | 怎么实现 |
|------|--------|---------|
| **缓存** | 依赖全挂时也能返回上一份数据 | Redis 存最近一次成功的结果，TTL 适中 |
| **限流** | 上游服务慢时，减少对它的调用量 | Sentinel 流控规则，QPS 阈值 |

```
降级策略优先级：正常返回 → Redis 缓存 → 默认兜底 → 空/假数据
```

---

**⑤ 设计 Checklist**

| 步骤 | 做了什么 |
|------|---------|
| ① 依赖分级 | P0（强依赖）/ P1（弱依赖）/ P2（可异步） |
| ② 逐一定超时 | P0 给 1-2s，P1 给 300-500ms，不能无限等 |
| ③ 逐一定降级 | 缓存兜底 / 默认值 / 空列表 / 静默忽略 |
| ④ 配置熔断 | 异常比例/慢调用比例 超阈值自动断开 |
| ⑤ 加缓存 | 每个上游的结果存 Redis，TTL 适度 |
| ⑥ 加限流 | 上游扛不住时限制调用量，不要雪上加霜 |
| ⑦ 监控告警 | 降级比例超过阈值发告警，知道出事了 |

**面试一句话**：先给依赖分级（P0/P1），P0 缓存兜底、P1 静默降级；同时设好超时防止线程耗光、配好熔断防止雪崩，缺一个都不行。

### 96. 如何设计一个支持千万级 DAU 的网站 UV 统计功能？

> **先搞清楚概念：**
>
> | 术语 | 全称 | 含义 | 举例 |
> |------|------|------|------|
> | **DAU** | Daily Active Users | 一天内使用过产品的**独立用户数** | 今天有 1000w 个不同用户打开了 App |
> | **UV** | Unique Visitors | 一段时间内访问网站的**独立访客数** | 今天网站被 800w 个不同用户访问了 |
>
> 两者本质一样——都是**去重计数**。区别是 DAU 叫法偏 App 运营，UV 叫法偏 Web 统计。本题核心问题是：**千万级用户量下，怎么又快又省内存地做去重计数。**

---

**① 直接方案：数据库 COUNT DISTINCT —— 千万级直接跪**

```sql
SELECT COUNT(DISTINCT user_id) FROM page_visit WHERE date = '2026-06-11';
-- 千万行数据，COUNT DISTINCT 要全表扫描或索引扫描，几十秒都跑不完
```

---

**② HyperLogLog —— UV 统计的标准答案**

HyperLogLog 是概率算法，用**极小的固定内存（~12KB）** 估算亿级 UV，误差约 0.81%。

| | 传统 COUNT DISTINCT | Redis HyperLogLog |
|---|---|---|
| **千万 UV 占内存** | 几百 MB ~ GB | **12 KB**（固定） |
| **查询速度** | 秒级~分钟级 | **毫秒级** |
| **精确度** | 100% 精确 | 约 0.81% 误差 |
| **适用场景** | 财务对账等要求精确的场景 | **UV 统计，1% 误差完全可接受** |

```java
// Redis HyperLogLog
// 记录访问：每个用户访问时调用
redisTemplate.opsForHyperLogLog().add("uv:2026-06-11", userId);

// 查询 UV：毫秒级返回
long uv = redisTemplate.opsForHyperLogLog().size("uv:2026-06-11");

// 合并多天：把每天的 HLL 合并
redisTemplate.opsForHyperLogLog()
    .union("uv:week-24", "uv:2026-06-09", "uv:2026-06-10", "uv:2026-06-11");
```

> **为什么 UV 统计允许误差？** 运营看的是趋势——"今天 UV 是 800w 还是 850w"对决策影响不大，但 12KB vs 几百MB 的内存差距是实打实的。极少场景要求 UV 精确到个位数。

---

**③ Redis Bitmap —— 中小规模精确去重**

如果 userId 是连续自增的数字（比如自增主键），可以用 Bitmap 做精确去重。每个用户占 1 bit。

```
千万用户 → 1000w bit ≈ 12.5 MB（固定）
亿级用户 → 1亿 bit ≈ 125 MB（也还好）
```

```java
// Redis Bitmap
// user_id=9527 今天访问了 → 把第 9527 位设为 1
redisTemplate.opsForValue()
    .setBit("uv:2026-06-11", userId, true);

// 查询 UV → BITCOUNT
Long uv = redisTemplate.execute((RedisCallback<Long>) conn ->
    conn.bitCount("uv:2026-06-11".getBytes()));
```

| 场景 | 推荐方案 |
|------|---------|
| userId 是连续自增数字，量不大 | **Bitmap**（精确，12.5MB 撑千万） |
| userId 是 UUID/雪花ID/手机号，或量很大 | **HyperLogLog**（省内存，12KB 固定） |
| 需要按地域/设备多维度分析 | HLL（每个维度一个 key） |

> **注意：** Bitmap 的 offset 对应 userId 值。如果 userId 离散很大（比如用雪花 ID，最大值几十亿），Bitmap 会产生大量空洞，内存直接爆炸——这种场景必须用 HLL。

---

**④ 系统架构：数据怎么从客户端流入存储**

千万级 DAU 一天产生的 PV 是几十亿级别，不可能每次都写 Redis。需要管道化处理：

```
客户端 SDK（埋点上报）
    ↓ （批量，每 5s 一次）
Nginx / API Gateway
    ↓
Kafka（削峰，扛住突发流量）
    ↓
Flink / Spark Streaming（实时计算）
    ↓ — 写入 — → Redis HLL（热数据，当天实时查询）
    ↓ — 写入 — → ClickHouse / HBase（冷数据，历史趋势分析）
```

```java
// Flink 中写入 Redis HLL
stream
    .keyBy(VisitEvent::getDate)   // 按天分组
    .process(new KeyedProcessFunction<>() {
        private transient Jedis jedis;

        @Override
        public void processElement(VisitEvent event, Context ctx, Collector<Void> out) {
            String key = "uv:" + event.getDate();
            jedis.pfadd(key, event.getUserId());  // HLL 自动去重
        }
    });
```

---

**⑤ 面试答法（一句话总结）**

> **千万级 DAU 的 UV 统计 = HyperLogLog（核心算法）+ Kafka（削峰）+ Flink（实时计算）+ Redis（热数据存储）。** HLL 用 12KB 固定内存估算亿级 UV，误差不到 1%，对运营分析完全够用；要求精确就用 Bitmap（限连续 userId），千万用户才 12.5MB。

### 97. 定时任务扫表处理过期订单，数据量太大扫不过来怎么办？

> 详见：[定时任务扫表处理过期订单，数据量太大扫不过来怎么办](../场景题/定时任务扫表处理过期订单，数据量太大扫不过来怎么办.md)

## 十四、项目实战深挖（3 题）

### 98. 介绍一个你觉得最有挑战的项目。中间遇到了什么问题？怎么解决的？用到了哪些 Spring Boot 技术？
### 99. 你的项目中如果突然流量翻了 10 倍，哪些地方会最先出问题？你会怎么改造？

### 100. 你平时怎么学习 Spring Boot 的？看过哪些源码？有什么学习习惯？

---

