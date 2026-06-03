# 牛客网 Spring Boot 实战面试 100 题

> 来源：牛客网面经 + 大厂高频真题 + 场景设计题
> 说明：本题库偏向**实战与场景**，每题均为真实面试出现过的题目

---

## 一、Spring Boot 基础与核心原理（13 题）

1. Spring Boot 相比传统 Spring 框架做了什么改进？为什么现在大家都在用？

> **第一，内嵌容器，开箱即用。** 传统 Spring 需要单独部署 Tomcat，打成 war 包丢到容器里跑。Spring Boot 直接把 Tomcat/Jetty/Undertow 内嵌进了 jar 包，一个 `java -jar` 就能启动，开发、测试、部署的体验都极大简化了。
>
> **第二，Starter 机制，一键集成。** 以前整合 MyBatis、Redis、MQ 这些框架，要一个一个找 Maven 依赖、解决版本冲突，非常痛苦。Spring Boot 提供了各种 Starter，比如你引入 `spring-boot-starter-data-redis`，它自动帮你拉好所有兼容的依赖，版本也不用你操心。
>
> **第三，约定优于配置。** 这是 Spring Boot 最核心的设计理念。它给几乎所有常见场景都预设了合理的默认值——端口默认 8080、数据源默认 HikariCP、静态资源默认放 `static` 目录。你只有不按约定来的时候才需要手动配置，90% 的情况零配置就能跑起来，极大减少了配置文件的体积和维护成本。

2. `@SpringBootApplication` 注解内部由哪几个注解构成？各自负责什么？

> `@SpringBootApplication` 是一个组合注解，由三个核心注解构成：
>
> **① `@SpringBootConfiguration`** — 本质就是 `@Configuration`，标记当前类是 Spring 的配置类，会被注册到 IoC 容器中。
>
> **② `@EnableAutoConfiguration`** — Spring Boot 的灵魂。它会借助 `AutoConfigurationImportSelector`，从 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件中读取所有候选的自动配置类，然后根据条件注解（`@ConditionalOnClass`、`@ConditionalOnMissingBean` 等）按需加载。这就是为什么你引入了 `spring-boot-starter-web` 之后不用做任何配置就能直接写 Controller 的原因。
>
> **③ `@ComponentScan`** — 开启组件扫描，默认扫描当前启动类所在包及其子包下的 `@Component`、`@Service`、`@Repository`、`@Controller` 等注解的类，将它们注册为 Bean。**注意：**如果你把启动类放在根包下，而业务代码放到了别的包里没被扫描到，Bean 就不会被注册，这是个很常见的坑。
>
> > **追问预警：** "那我想扫描别的包怎么办？" → 用 `@SpringBootApplication(scanBasePackages = "com.xxx")` 或在启动类上额外加 `@ComponentScan("com.xxx")` 指定扫描路径。

3. Spring Boot 的约定大于配置体现在哪些地方？举几个实际例子。

> 约定优于配置不是"不用配置"，而是**你不说话的时候，我按规矩来；你有意见的时候，我按你说的改。** 体现在这几个层面：
>
> **① Maven 依赖管理。** `spring-boot-starter-parent` 帮你锁定了海量第三方依赖的版本号，你只需声明用哪个 Starter，不用再操心各个 jar 包的版本兼容问题——Spring Boot 替你做好了版本仲裁。
>
> **② Starter 自动配置。** 引入 `spring-boot-starter-data-redis`，Spring Boot 约定你用 `localhost:6379`，没有密码；如果你 Redis 在内网另一台机器上，就得在配置文件里写 `spring.redis.host` 和 `spring.redis.port` 来覆盖约定。引入 `spring-boot-starter-data-jpa`，约定连接池用 HikariCP、自动建表策略默认 `none`——都能改，但不改就能跑。
>
> **③ 项目结构。** 约定启动类放在根包下，`@ComponentScan` 默认扫描这个包及其子包；静态资源放 `classpath:/static` 或 `/resources`；模板文件放 `classpath:/templates`。按这个目录结构来，零配置就能访问 `localhost:8080/index.html`，不按约定就要手动配 `spring.web.resources.static-locations`。
>
> **④ 配置文件。** 约定配置文件叫 `application.properties` 或 `application.yml`，放 `src/main/resources` 下，Spring Boot 自动加载。多环境也遵循约定——`application-dev.yml`、`application-prod.yml`，只需在 `application.yml` 里设 `spring.profiles.active: dev` 就自动切好了。
>
> **⑤ 内嵌容器。** 约定用 Tomcat，端口 8080。想换 Undertow 就 exclude Tomcat 再引入 Undertow；想换端口就 `server.port=9090`——不配，就按约定来。

4. Spring Boot 的启动流程是怎样的？`SpringApplication.run()` 内部做了哪些关键步骤？

> 一句话概括：**判断应用类型 → 加载扩展 → 准备环境 → 创建容器 → `refresh()`（自动配置生效 + Bean 实例化）→ 回调 Runner。**
>
> 具体拆成七个步骤：
>
> **阶段一：启动准备**
>
> **① 推断应用类型。** 通过检查 classpath——有 `DispatcherServlet` 就是 Servlet Web 应用，有 `DispatcherHandler` 但没有 `DispatcherServlet` 就是响应式（Reactive）应用，啥都没有就是普通应用。这决定了后续创建什么类型的 `ApplicationContext`。
>
> **② 加载初始化器和监听器。** 从 `META-INF/spring.factories`（3.x 改为 `AutoConfiguration.imports`）中加载 `ApplicationContextInitializer` 和 `ApplicationListener`，让你在容器刷新前后能做自定义扩展。
>
> **阶段二：环境准备**
>
> **③ 准备 Environment。** 创建 `Environment` 对象，加载所有配置源——命令行参数、环境变量、`application.yml`、`application.properties`，按优先级合并。
>
> **④ 打印 Banner + 创建容器。** 就是启动时那个 Spring 大字，同时创建一个空的 `ApplicationContext`。
>
> **阶段三：容器刷新（最核心）**
>
> **⑤ `refresh()` 刷新容器。** 对应 `AbstractApplicationContext.refresh()`，内部十几步，核心的几个：
> - `obtainFreshBeanFactory()`：解析配置，注册 `BeanDefinition`
> - `invokeBeanFactoryPostProcessors()`：**执行 `BeanFactoryPostProcessor`，自动配置在这步生效**
> - `registerBeanPostProcessors()`：注册 `BeanPostProcessor`（AOP 代理就靠它）
> - `finishBeanFactoryInitialization()`：**实例化所有非懒加载的单例 Bean**
>
> **⑥ 自动配置生效。** 在第⑤步中，`ConfigurationClassPostProcessor` 解析 `@Configuration` 类，触发 `AutoConfigurationImportSelector` 加载所有候选自动配置类，再由条件注解（`@ConditionalOnClass` 等）决定哪些真正生效。
>
> **阶段四：收尾**
>
> **⑦ 回调 Runner。** 容器启动完成后，依次执行 `CommandLineRunner` 和 `ApplicationRunner`。常用于启动后初始化数据、预热缓存、检查依赖服务健康状态等。

5. Spring Boot 的 banner 是怎么加载的？如何自定义？
6. Spring Boot 为什么能通过 main 方法直接启动，不需要外部 Tomcat？
7. `CommandLineRunner` 和 `ApplicationRunner` 的区别是什么？你在项目中用过吗？
8. Spring Boot 如何实现热部署？`spring-boot-devtools` 的原理是什么？
9.  Spring Boot 有几种注入 Bean 的方式？各有什么优缺点？
10. Spring Boot 中 `@ConfigurationProperties` 和 `@Value` 的区别是什么？什么时候用哪个？
11. Spring Boot 支持哪些配置文件格式？`.properties` 和 `.yml` 加载优先级是怎样的？
12. Spring Boot 多环境配置怎么实现？你在项目中怎么切换 dev / test / prod 环境？
13. Spring Boot 2.x 和 3.x 有哪些重要变化？升级时需要注意什么？

## 二、自动配置与 Starter 机制（8 题）

14. Spring Boot 的自动装配原理是什么？从 `@EnableAutoConfiguration` 到最终加载配置类的完整链路是怎样的？
15. `spring.factories` 文件在 Spring Boot 2.x 和 3.x 中有什么变化？
16. `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 这些条件注解在自动装配中怎么协作的？
17. 如何排除某个不想用的自动配置？比如不想用默认的 DataSource？
18. 如果你自己封装一个公司内部的 Starter，步骤是怎样的？需要注意什么？
19. 自定义 Starter 时，`xxx-spring-boot-starter` 和 `xxx-spring-boot-autoconfigure` 两个模块分别放什么？
20. Starter 中的自动配置类和用户自己定义的 Bean，谁的优先级更高？如果用户想覆盖 Starter 的默认 Bean 怎么做？
21. 你在项目中实际封装过哪些 Starter？解决了什么问题？

## 三、IoC 容器与 Bean 生命周期（8 题）

22. IoC（控制反转）你是怎么理解的？它解决了什么问题？
23. Spring 容器启动时，Bean 的完整生命周期是怎样的？每一步都在做什么？
24. `BeanFactory` 和 `ApplicationContext` 的区别是什么？你平时用的是哪个？
25. `@Component` 和 `@Bean` 的区别是什么？什么场景用 `@Bean`？
26. Bean 的作用域有哪些？`singleton`、`prototype`、`request`、`session` 分别在什么场景使用？
27. 如果一个 `prototype` 作用域的 Bean 被注入到 `singleton` 的 Bean 中，会发生什么？怎么解决？
28. Spring 怎么解决循环依赖的？三级缓存各自存的是什么？为什么必须是三级，两级行不行？
29. 构造器注入的循环依赖能解决吗？为什么？

## 四、AOP 面向切面编程（5 题）

30. AOP 的实现原理是什么？JDK 动态代理和 CGLIB 代理有什么区别？Spring Boot 默认用哪个？
31. `@Before`、`@After`、`@AfterReturning`、`@AfterThrowing`、`@Around` 的执行顺序是怎样的？
32. 你在项目中用 AOP 做过哪些事？具体怎么实现的？
33. AOP 的自调用问题是什么？为什么同一个类里调用 `@Transactional` 方法不走代理？怎么解决？
34. Spring AOP 和 AspectJ 有什么区别？各自适用什么场景？

## 五、事务管理（7 题）

35. Spring 的事务传播机制有哪几种？`REQUIRED`、`REQUIRES_NEW`、`NESTED` 有什么区别？你在项目中怎么选的？
36. `@Transactional` 注解在什么情况下会失效？列举至少 5 种场景。
37. 自调用导致事务失效怎么解决？除了把方法拆到另一个类还有别的办法吗？
38. 事务的隔离级别有哪些？分别能解决什么并发读问题？
39. 分布式事务你是怎么处理的？Seata 的 AT 模式和 TCC 模式有什么区别？
40. 为什么说"不要在事务里做 RPC 调用和 IO 操作"？你遇到过这个问题吗？
41. 声明式事务和编程式事务各有什么优缺点？你一般在什么场景用编程式事务？

## 六、Spring MVC 核心技术（6 题）

42. Spring MVC 一次请求的完整处理流程是怎样的？从 DispatcherServlet 开始一步步说清楚。
43. 拦截器（Interceptor）和过滤器（Filter）的区别是什么？执行顺序是怎样的？
44. 如何在 Spring Boot 中做统一的参数校验？`@Valid`、`@Validated`、自定义校验注解怎么用？
45. 全局异常处理怎么实现？`@ControllerAdvice` + `@ExceptionHandler` 的原理是什么？
46. 统一返回格式怎么封装？你在项目中是怎么做的？
47. 如何在 Spring Boot 中做接口的幂等性校验？有几种方案？

## 七、数据库与持久层（7 题）

48. Spring Boot 怎么整合 MyBatis / MyBatis-Plus？你做过哪些配置？
49. MyBatis-Plus 的分页插件原理是什么？你在项目中怎么用的？
50. MyBatis 的 `#{}` 和 `${}` 有什么区别？为什么要尽量用 `#{}`？
51. SQL 执行慢你怎么排查和优化？说说你的思路和工具。
52. 什么时候该建索引？联合索引的"最左前缀"原则是什么？你在项目中有没有因为索引使用不对导致过慢查询？
53. 分库分表后，怎么处理跨库的分页、排序、聚合查询？ShardingSphere 是怎么解决这些问题的？
54. 读写分离你是怎么做的？主从延迟导致读到旧数据怎么处理？

## 八、缓存实战（9 题）

55. 缓存穿透、缓存击穿、缓存雪崩分别是什么意思？你在项目中怎么解决的？
56. 如何保证数据库和缓存的双写一致性？Cache-Aside 模式的具体步骤是什么？延迟双删怎么做？
57. Redis 在你的项目中具体用了哪些场景？每个场景用的什么数据结构？为什么选这个结构？
58. 热点 Key 突然过期导致大量请求打到数据库，怎么处理？
59. 大 Key（Big Key）有什么危害？怎么发现和拆解？
60. Redis 分布式锁是怎么实现的？`SETNX` + Lua 脚本和 Redisson 的 RedLock 有什么区别？
61. 用 Redis 实现一个延时队列怎么做？有哪些方案？
62. 本地缓存（Caffeine）和分布式缓存（Redis）怎么搭配使用？多级缓存的更新策略是什么？
63. Redis 的内存淘汰策略有哪些？你在项目中用的是哪个？为什么？

## 九、消息队列实战（7 题）

64. 消息队列在你的项目中解决了什么问题？为什么不用同步调用而要用 MQ？
65. 如何保证消息不丢失？从生产者、Broker、消费者三端分别说说。
66. 消息重复消费怎么处理？你的系统是怎么做幂等的？
67. 消息堆积了怎么办？积压几百万条消息你怎么快速处理？
68. 顺序消息怎么保证？什么场景需要顺序消息？
69. Kafka 的消费者组（Consumer Group）和分区（Partition）之间是什么关系？
70. Kafka 的 ISR 机制是什么？`acks=all` 和 `min.insync.replicas` 怎么配合保证可靠性？

## 十、分布式与微服务（10 题）

71. 你把单体项目拆成微服务的依据是什么？你是怎么划分服务边界的？
72. 服务注册与发现是怎么工作的？Nacos 和 Eureka 有什么核心区别？
73. 负载均衡策略有哪些？Ribbon 的轮询、随机、加权轮询分别怎么用？
74. 服务间调用（OpenFeign / Dubbo）你怎么选的？底层原理是什么？
75. 微服务网关（Gateway / Zuul）的作用是什么？你在网关层做了哪些事（鉴权、限流、日志、路由）？
76. 如何实现一个分布式 ID 生成器？雪花算法有什么优缺点？时钟回拨怎么处理？
77. 分布式 Session 怎么解决？Spring Session + Redis 的原理是什么？
78. 分布式锁除了 Redis 实现还有哪些方案？ZooKeeper 实现和 Redis 实现各有什么优缺点？
79. CAP 定理和 BASE 理论怎么理解？在你的项目里是怎么权衡的？
80. 微服务链路追踪怎么实现？TraceID 如何在服务间传递？

## 十一、安全与鉴权（4 题）

81. Spring Security 的认证和授权流程是怎样的？过滤器链里有哪几个关键的 Filter？
82. 无状态登录（JWT Token）怎么实现？Token 过期刷新机制你怎么设计的？
83. 如何防止 CSRF 和 XSS 攻击？Spring Security 默认做了什么？你还做了哪些额外防护？
84. OAuth2.0 的授权码模式和密码模式有什么区别？你在项目中用的是哪种？

## 十二、性能优化与监控（6 题）

85. Spring Boot 应用启动慢怎么排查和优化？
86. 线上接口响应突然变慢，你的排查思路是什么？
87. 怎么定位 JVM 的 CPU 飙高和内存泄漏问题？用什么工具？
88. Spring Boot Actuator 你用了哪些端点？如何自定义健康检查？
89. 如何建设一个统一的日志收集和告警体系？你的项目里是怎么做的？
90. 线上出了问题你怎么快速回滚和止损？有什么预案？

## 十三、场景设计题（7 题）

91. 如何设计一个秒杀系统？从限流、库存扣减、下单、支付整个链路说说你的方案。
92. 接口的 QPS 从 1000 突然涨到 10000，你有哪些手段保证系统不崩？
93. 如何设计一个短链接生成系统？
94. 如何设计一个分布式定时任务调度系统？
95. 一个接口依赖多个上游服务，怎么设计才能保证高可用（兜底、降级、熔断）？
96. 如何设计一个支持千万级 DAU 的网站 UV 统计功能？
97. 定时任务扫表处理过期订单，数据量太大扫不过来怎么办？

## 十四、项目实战深挖（3 题）

98. 介绍一个你觉得最有挑战的项目。中间遇到了什么问题？怎么解决的？用到了哪些 Spring Boot 技术？
99. 你的项目中如果突然流量翻了 10 倍，哪些地方会最先出问题？你会怎么改造？
100. 你平时怎么学习 Spring Boot 的？看过哪些源码？有什么学习习惯？

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
