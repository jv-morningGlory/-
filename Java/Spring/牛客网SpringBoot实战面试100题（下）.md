# 牛客网 Spring Boot 实战面试 100 题（下）

---

## 四、AOP 面向切面编程（5 题）

### 30. AOP 的实现原理是什么？

**实现原理：**

Spring AOP 基于**动态代理**。

#### 代理生成的完整时机（结合 Bean 生命周期 + 三级缓存）

> **正常情况**下，代理在 Bean 初始化之后生成；如果存在**循环依赖**，代理会**提前**生成。下面这张图把创建 Bean 的完整源码流程（`doCreateBean`）和依赖注入、代理生成都串起来：

```
doGetBean("A")
  │
  ├─ getSingleton("A")          // ① 查一二三级缓存
  │     一级(成品) → 二级(早期) → 三级(工厂)
  │     命中直接返回；没命中 → 继续 createBean
  │
  └─ createBean("A")
        │
        └─ doCreateBean("A")
              │
              ├─ createBeanInstance()        ②【实例化】反射调构造方法 → 半成品对象
              │
              ├─ addSingletonFactory("A", ObjectFactory)
              │     ③ 把"工厂"丢进【第三级缓存】← 循环依赖时代理从这里提前出来
              │        (注意：此刻还没生成代理，只是登记了能生成代理的能力)
              │
              ├─ populateBean()              ④【依赖注入 / 属性注入】@Autowired 在这
              │     │
              │     └─ 注入 B → 又触发 doGetBean("B") → B 也走到这一步注入 A
              │           │
              │           └─ B 要 A，查缓存：一二三级都没有成品 → 命中三级工厂
              │                 ─────────────────────────────────────────
              │                 getEarlyBeanReference("A")  ★ 提前生成 A 的代理
              │                 把 A 的代理放进【第二级缓存】返回给 B
              │                 ─────────────────────────────────────────
              │                 (仅当 A 存在循环依赖，才会走这条提前路径)
              │
              └─ initializeBean()            ⑤【初始化】（此时 Bean 还在"裸"状态）
                    │
                    ├─ invokeAwareMethods()         BeanNameAware/BeanFactoryAware...
                    │
                    ├─ applyBeanPostProcessors
                    │     └─ BeforeInitialization   @PostConstruct 在这(CommonAnnotationBP)
                    │
                    ├─ invokeInitMethods()          afterPropertiesSet + init-method
                    │
                    └─ applyBeanPostProcessors
                          └─ AfterInitialization
                                └─ AbstractAutoProxyCreator
                                     .postProcessAfterInitialization()
                                        → wrapIfNecessary() → createProxy()
                                        ★★★ 正常情况下，代理在这里生成 ★★★
```

> **一个 Bean 变成代理的时机不确定，有两种可能：**
> - 如果它在**属性注入阶段被循环依赖地需要**了 → 提前在 `getEarlyBeanReference()` 变成代理；
> - 否则 → 在初始化之后、`BeanPostProcessor` 的 `after` 方法里变成代理。

---



### 31. `@Before`、`@After`、`@AfterReturning`、`@AfterThrowing`、`@Around` 的执行顺序是怎样的？

**正常流程：**

```
@Around（前半段）
  → @Before
    → 目标方法执行
  → @AfterReturning
  → @After
@Around（后半段）
```

**异常流程：**

```
@Around（前半段）
  → @Before
    → 目标方法抛异常
  → @AfterThrowing
  → @After
@Around（后半段，catch 异常后不再继续）
```

> `@After` 类似 finally，无论正常还是异常都会执行。`@AfterReturning` 和 `@AfterThrowing` 二选一，不会同时触发。`@Around` 最外层，可以决定是否调用目标方法。
### 32. 你在项目中用 AOP 做过哪些事？具体怎么实现的？

> 回答框架：做了什么 → 为什么用 AOP → 怎么做的（4 步）

**我做过的两个场景：枚举翻译、用户数据脱敏。**




### 33. AOP 的自调用问题是什么？为什么同一个类里调用 `@Transactional` 方法不走代理？怎么解决？

**原因**：代理对象和目标对象是两个不同的对象。代理确实重写了所有方法，但方法内部的 `this` 指向的是**目标对象**，不是代理对象，所以 `this.methodB()` 绕过了代理。



**调用链对比：**

```
外部调 methodA()：
  Controller → Proxy.methodA()（事务生效）
                → target.methodA()
                    → this.methodB()  ← this 是 target，不是 Proxy
                    → target.methodB()（没有经过 Proxy，事务不生效）

外部直接调 methodB()：
  Controller → Proxy.methodB()（事务生效）✅
```


### 35. Spring 的事务传播机制有哪几种？`REQUIRED`、`REQUIRES_NEW`、`NESTED` 有什么区别？你在项目中怎么选的？

**7 种传播行为一览：**

| 传播行为 | 有事务时 | 没事务时 | 用途 |
|---------|---------|---------|------|
| **REQUIRED**（默认） | 加入当前事务 | 新建一个 | 大多数场景的默认选择 |
| **SUPPORTS** | 加入当前事务 | 非事务执行 | 查询方法 |
| **MANDATORY** | 加入当前事务 | 抛异常 | 强制要求在事务中调用 |
| **REQUIRES_NEW** | 挂起当前事务，新建一个 | 新建一个 | 独立事务，不受外层回滚影响 |
| **NOT_SUPPORTED** | 挂起当前事务，非事务执行 | 非事务执行 | 不需要事务的操作 |
| **NEVER** | 抛异常 | 非事务执行 | 强制要求不在事务中调用 |
| **NESTED** | 在当前事务中创建保存点（Savepoint） | 新建一个 | 嵌套事务，内层可独立回滚 |




### 36. `@Transactional` 注解在什么情况下会失效？列举至少 5 种场景。

| # | 场景 | 原因 | 解决 |
|---|------|------|------|
| 1 | **同类自调用** | `this.methodB()` 绕过代理 | 拆类 / `AopContext.currentProxy()` |
| 2 | **方法非 public** | Spring AOP 只拦截 public 方法 | 改为 public |
| 3 | **方法被 final/static 修饰** | CGLIB 无法重写 final/static 方法 | 去掉 final/static |
| 4 | **异常被 try-catch 吞掉** | 事务感知不到异常，不会回滚 | catch 后手动 `throw` 或 `setRollbackOnly()` |
| 5 | **抛出 checked 异常** | 默认只回滚 `RuntimeException` 和 `Error` | `@Transactional(rollbackFor = Exception.class)` |
| 6 | **数据库引擎不支持事务** | MyISAM 不支持事务 | 用 InnoDB |
| 7 | **Bean 未被 Spring 管理** | 没加 `@Service` 等注解，不是 Spring Bean | 加上注解 |
| 8 | **传播行为设错** | `NOT_SUPPORTED` / `NEVER` 本身就不用事务 | 检查 propagation 设置 |

> 面试说前 5 个就够了，第 5 个顺带提一嘴 `rollbackFor = Exception.class` 是最佳实践。
### 37. 自调用导致事务失效怎么解决？除了把方法拆到另一个类还有别的办法吗？

> 原理同第 33 题：`this.methodB()` 绕过代理，事务不生效。核心就是拿到代理对象来调用。

**三种解决方式：**

**① 注入自身（最常用）**

```java
@Service
public class UserService {
    @Autowired
    private UserService self;  // 注入自己的代理对象

    public void methodA() {
        self.methodB();  // 通过代理调用，事务生效
    }

    @Transactional
    public void methodB() { ... }
}
```

> 注意：不会循环依赖，因为 Spring 三级缓存会先暴露早期引用。但如果构造器里就用会报 NPE。

**② AopContext 获取当前代理**

```java
// 启动类加：
@EnableAspectJAutoProxy(exposeProxy = true)

// 使用：
((UserService) AopContext.currentProxy()).methodB();
```

> 优点：不用注入自己，代码侵入小。缺点：依赖 Spring AOP 内部 API，且必须在 Spring 管理的线程内调用。

**③ 拆到另一个类（最干净）**

```java
@Service
public class UserService {
    @Autowired
    private OrderService orderService;

    public void methodA() {
        orderService.methodB();  // 天然走代理
    }
}
```

> 优点：没有 hack，符合设计原则。缺点：有时业务上两个方法就是属于同一个类，强行拆不合理。

**面试怎么答**：先说三种方式，然后说"生产中简单场景用 `AopContext`，复杂场景优先拆类"。

### 41. 声明式事务和编程式事务各有什么优缺点？你一般在什么场景用编程式事务？

**一句话：声明式用注解（`@Transactional`），编程式手动写代码控制。**

| | 声明式事务 | 编程式事务 |
|---|---|---|
| **用法** | `@Transactional` 注解 | `TransactionTemplate` 或 `PlatformTransactionManager` |
| **原理** | AOP 代理 + `TransactionInterceptor` | 手动调 `getTransaction/commit/rollback` |
| **优点** | 简洁，业务代码无侵入 | 控制精细，范围可随心所欲 |
| **缺点** | 粒度只能到方法级；自调用/多线程易失效 | 代码侵入，啰嗦 |
| **常用度** | 90% 场景用这个 | 少数精细控制场景 |

```java
// 声明式 —— 一个注解搞定
@Transactional
public void createUser(User u) {
    userMapper.insert(u);
}

// 编程式 —— TransactionTemplate
public void createUser(User u) {
    transactionTemplate.execute(status -> {        // 手动开启事务
        userMapper.insert(u);
        logMapper.insert(log);
        return null;
    });                                            // 自动 commit / rollback
}
```

**什么场景用编程式：**

1. **事务范围要小于方法**：方法里只有中间几步需要事务，前后还有发 MQ、调远程，用声明式会让整个方法都在事务里（长事务）
2. **批量循环逐条提交**：导入大量数据，每 N 条提交一次，某条失败不影响其他
3. **多线程 / 异步**：声明式靠 ThreadLocal 绑定 Connection，子线程拿不到
4. **动态决定回滚**：复杂条件下用 `status.setRollbackOnly()` 更灵活

> **日常 99% 用声明式；只有「事务范围精细控制」或「脱离 AOP 上下文（多线程）」时才上编程式。**

## 六、Spring MVC 核心技术（6 题）

### 42. Spring MVC 一次请求的完整处理流程是怎样的？从 DispatcherServlet 开始一步步说清楚。

**核心一句话：请求都进 `DispatcherServlet`，它负责找 Controller、调 Controller、处理结果。**

```text
浏览器请求
   │
   ▼
① DispatcherServlet（前端控制器，统一入口）
   │
   ├─② HandlerMapping：根据 URL 找到 Handler（Controller 方法）+ 拦截器链
   │      返回 HandlerExecutionChain
   │
   ├─③ HandlerAdapter：真正调用 Controller 方法
   │      ├─ 拦截器 preHandle()
   │      ├─ 参数解析 + 数据绑定，执行 Controller
   │      ├─ 拦截器 postHandle()
   │      └─ 返回 ModelAndView（@ResponseBody 则直接写 JSON）
   │
   ├─④ ViewResolver：解析视图（返回 JSON 的接口跳过这步）
   │
   └─⑤ 渲染视图 → 响应浏览器
        最后执行拦截器 afterCompletion()
```

**三个最关键的组件：**

| 组件 | 作用 |
|---|---|
| **HandlerMapping** | URL → Controller 方法的映射（`@RequestMapping`） |
| **HandlerAdapter** | 真正执行 Controller 方法，处理参数绑定 |
| **ViewResolver** | 视图名 → 视图对象 |

> **REST 接口特殊点：** `@RestController`/`@ResponseBody` 不走 `ViewResolver`，返回值由 `HttpMessageConverter`（如 `MappingJackson2HttpMessageConverter`）直接序列化成 JSON 写回响应。

### 43. 拦截器（Interceptor）和过滤器（Filter）的区别是什么？执行顺序是怎样的？



**执行顺序（结合 DispatcherServlet）：**

```text
请求
  │
  ▼
Filter1.doFilter(前)  ──────────────────────┐  Filter 在最外层（洋葱模型）
  Filter2.doFilter(前)  ───────────────────┐ │
    │                                      │ │
    ▼  DispatcherServlet.doDispatch()      │ │
    │                                      │ │
    ├─ Interceptor1.preHandle   (顺序)     │ │  Interceptor 在内部
    ├─ Interceptor2.preHandle   (顺序)     │ │  包裹 Controller
    │     ▼  Controller 执行                │ │
    ├─ Interceptor2.postHandle  (逆序)     │ │
    ├─ Interceptor1.postHandle  (逆序)     │ │
    │     ▼  视图渲染                       │ │
    ├─ Interceptor2.afterCompletion(逆序)  │ │
    └─ Interceptor1.afterCompletion(逆序)  │ │
    │                                      │ │
    ▼  DispatcherServlet 结束              │ │
  Filter2.doFilter(后)  ──────────────────┘ │
Filter1.doFilter(后)  ──────────────────────┘
  │
  ▼
响应
```

> **重要认知：** Filter **不是 Tomcat 提供的**，而是 **Servlet 规范**定义的接口，Tomcat 只是规范的实现者之一（换 Jetty / Undertow 照样能用）；Interceptor 才是 Spring 自己的。所以 **Filter 属于 Servlet 规范（被容器驱动），Interceptor 属于 Spring（被 DispatcherServlet 驱动）**。

> **记忆口诀：** Filter 是"保安"（门口，啥都拦，不懂业务）；Interceptor 是"秘书"（进了门，知道你找谁、能不能见）。

### 44. 如何在 Spring Boot 中做统一的参数校验？`@Valid`、`@Validated`、自定义校验注解怎么用？
### 45. 全局异常处理怎么实现？`@ControllerAdvice` + `@ExceptionHandler` 的原理是什么？

### 47. 如何在 Spring Boot 中做接口的幂等性校验？有几种方案？

**一句话：幂等 = 同一个请求执行一次和多次，结果完全一样。**

#### 我的方案：Redis 锁 + 业务状态校验

> 以支付接口为例：先用 Redis 锁挡住**并发**，再校验业务**状态**，双重保险。

```java
@Transactional
public void payOrder(String orderId) {
    String lockKey = "pay:lock:" + orderId;
    // ① Redis 锁：防并发（同一订单同时只能一个请求进来）
    Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
    if (!locked) {
        throw new BizException("正在处理，请勿重复提交");
    }
    try {
        // ② 业务状态校验：防重复（已支付的订单不能再付）
        Order order = orderMapper.selectById(orderId);
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BizException("订单已支付");
        }
        // ③ 执行支付、更新状态
        doPay(order);
        order.setStatus(OrderStatus.PAID);
        orderMapper.updateById(order);
    } finally {
        redisTemplate.delete(lockKey);   // 释放锁
    }
}
```

> **两层各自的作用：**
> - **Redis 锁**：挡**并发**（同一时刻两个请求同时进来，只放行一个）
> - **业务状态校验**：挡**重复**（不管什么时候，已支付的订单不能再付）
>
> 生产建议用 **Redisson** 的分布式锁（自带看门狗续期、可重入），比 `setIfAbsent` 更稳。

---

#### 防重复提交 vs 幂等的区别

> **关键区别：** 防重复提交只在"短时间窗口"内生效，窗口一过还能再提交；幂等是"任何时候"重复都保证结果一致。所以防重复提交防不住"跨时间的重复"，真正兜底还得靠幂等（状态校验 / 唯一索引）。

## 十、分布式与微服务（10 题）

### 71. 你把单体项目拆成微服务的依据是什么？你是怎么划分服务边界的？
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

## 题目分类速览

| 分类 | 题号 | 题数 |
|------|------|------|
| Spring Boot 基础与核心原理 | 1-13 | 13 |
| 自动配置与 Starter 机制 | 14-21 | 8 |
| IoC 容器与 Bean 生命周期 | 22-29 | 8 |
| AOP 面向切面编程 | 30-34 | 5 |
| 事务管理 | 35-41 | 7 |
| Spring MVC 核心技术 | 42-47 | 6 |
| 数据库与持久层 | 48-54 | 7 |
| 缓存实战 | 55-63 | 9 |
| 消息队列实战 | 64-70 | 7 |
| 分布式与微服务 | 71-80 | 10 |
| 安全与鉴权 | 81-84 | 4 |
| 性能优化与监控 | 85-90 | 6 |
| 场景设计题 | 91-97 | 7 |
| 项目实战深挖 | 98-100 | 3 |

> **共计：100 题**

---

> **使用建议：**
> 1. 先按分类逐个吃透，基础类 → 原理类 → 实战类 → 场景设计类
> 2. 每道题自己先答一遍，再到牛客网上搜对应面经对比
> 3. 场景题没有标准答案，重点练"分析问题 → 提出方案 → 对比优缺点"的思路
> 4. 结合你实际做的项目，每道题想想"我在项目中遇到过类似的问题吗"
