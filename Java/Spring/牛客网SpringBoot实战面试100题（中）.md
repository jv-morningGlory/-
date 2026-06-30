# 牛客网 Spring Boot 实战面试 100 题（中）

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

**一句话：DTO 字段加 JSR 303 注解，Controller 参数加 `@Valid` 触发校验，失败抛异常由全局处理器统一兜住。**

**① 基础用法**

```java
// DTO：字段上加校验注解
public class UserDTO {
    @NotBlank(message = "姓名不能为空")
    private String name;

    @Min(0) @Max(150)
    private Integer age;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;
}

// Controller：参数加 @Valid 触发校验
@PostMapping("/save")
public Result save(@RequestBody @Valid UserDTO dto) {
    return Result.ok();
}
```

> 常用注解：`@NotNull` / `@NotEmpty` / `@NotBlank`、`@Min` / `@Max`、`@Size`、`@Email` / `@Pattern`、`@Past` / `@Future`

**② `@Valid` vs `@Validated` 区别（核心考点）**

| | `@Valid` | `@Validated` |
|---|---|---|
| 来源 | JSR 303 标准 | Spring 提供 |
| **分组校验** | ❌ 不支持 | ✅ **支持** |
| 嵌套校验 | ✅（标在字段上） | ✅ |
| 标在类上 | ❌ | ✅ |

> **最大区别：`@Validated` 支持分组校验**。同一个 DTO 新增和修改校验规则不同时用分组。

```java
public interface Add {}
public interface Update {}

public class UserDTO {
    @Null(groups = Add.class)        // 新增时 id 必须为空
    @NotNull(groups = Update.class)  // 修改时 id 必须有
    private Long id;

    @NotBlank(groups = {Add.class, Update.class})
    private String name;
}

@PostMapping("/add")
public Result add(@RequestBody @Validated(Add.class) UserDTO dto) {}

@PostMapping("/update")
public Result update(@RequestBody @Validated(Update.class) UserDTO dto) {}
```

**③ 自定义校验注解（两步）**

> 场景：校验状态值只能是 0/1/2，内置注解搞不定。

```java
// ① 自定义注解
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EnumValidator.class)   // 绑定校验器
public @interface IsEnum {
    String message() default "值不在允许范围内";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    int[] values();   // 允许的值
}

// ② 实现校验器
public class EnumValidator implements ConstraintValidator<IsEnum, Integer> {
    private int[] allowed;
    public void initialize(IsEnum anno) { allowed = anno.values(); }
    public boolean isValid(Integer value, ConstraintValidatorContext ctx) {
        if (value == null) return true;            // 非空交给 @NotNull
        for (int v : allowed) if (v == value) return true;
        return false;
    }
}

// ③ 使用
public class OrderDTO {
    @IsEnum(values = {0, 1, 2}, message = "状态只能是0/1/2")
    private Integer status;
}
```

**④ 校验失败统一处理（配合全局异常处理器）**

| 参数位置 | 失败抛的异常 |
|---|---|
| `@RequestBody` 对象 | `MethodArgumentNotValidException` |
| `@RequestParam` / 表单 | `ConstraintViolationException` |

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public Result handleValid(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return Result.fail(msg);
}
```

> **面试一句话总结：** 注解校验（`@Valid`）→ 分组校验（`@Validated`）→ 搞不定的自定义注解（`@Constraint` + `ConstraintValidator`）→ 失败异常交给 `@RestControllerAdvice` 统一处理。
>
> **分层校验原则（进阶）：** 格式校验（非空、长度、格式）用注解 `@Valid` / `@Validated`；业务校验（余额够不够、状态允不允许）用 `Assert` 或业务方法。**别把业务规则硬塞进 Bean Validation 注解**——`@NotNull` 只管非空，业务逻辑不是它该干的。

### 45. 全局异常处理怎么实现？`@ControllerAdvice` + `@ExceptionHandler` 的原理是什么？

**一句话：`@RestControllerAdvice` + `@ExceptionHandler` 把异常处理抽到统一类，Controller 里只管抛异常、不用 try-catch。**

**① 怎么实现**

```java
@RestControllerAdvice   // = @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler {

    // ① 自定义业务异常
    @ExceptionHandler(BizException.class)
    public Result handleBiz(BizException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // ② 兜底：所有未捕获异常
    @ExceptionHandler(Exception.class)
    public Result handleAll(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统异常，请联系管理员");
    }
}
```

**② 原理（结合第 42 题 DispatcherServlet）**

```text
Controller 抛异常
  │
  ▼
DispatcherServlet 捕获 → processHandlerException()
  │
  ▼
按顺序遍历 HandlerExceptionResolver 链：
  ① ExceptionHandlerExceptionResolver   ← @ExceptionHandler 走这里
  ② ResponseStatusExceptionResolver      ← @ResponseStatus 标记的异常
  ③ DefaultHandlerExceptionResolver      ← Spring 标准异常（参数绑定等）
  │
  ▼
① 内部：先找「当前 Controller」的 @ExceptionHandler
        没有 → 再找「全局 @ControllerAdvice」里的 @ExceptionHandler
        按异常类型最精确匹配（子类优先）
  │
  ▼
反射调用处理方法 → 返回结果写回响应
```

> **本质：** 启动时把「异常类型 → 处理方法」注册成 Map，运行时抛异常查这张表，反射调用。
>
> **关键认知：** `@RestControllerAdvice` 提供的是 Spring MVC 的**异常解析扩展点**（"Advice" 只是借用了 AOP 的术语，**机制不是 AOP**）——它靠 **DispatcherServlet 捕获异常后查表处理**，而不是代理拦截。（验证：给 Service 加 `@ExceptionHandler` 不生效，因为它不经过 DispatcherServlet；若是 AOP 就该生效。）

**③ 实际项目要捕获的异常（分层兜底）**

| 异常 | 场景 |
|---|---|
| `BizException`（自定义） | 业务异常，返回错误信息给前端 |
| `MethodArgumentNotValidException` | `@RequestBody` 参数校验失败 |
| `ConstraintViolationException` | `@RequestParam` / 表单校验失败 |
| `HttpRequestMethodNotSupportedException` | 请求方法不对（GET 调了 POST 接口） |
| `Exception` | **兜底**，防止异常直接暴露给用户 |

> **顺序很重要：** 子类异常（BizException、校验异常）的 handler 要写在 `Exception` 兜底**之前**，按"从具体到通用"排列，可读性最好。

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
