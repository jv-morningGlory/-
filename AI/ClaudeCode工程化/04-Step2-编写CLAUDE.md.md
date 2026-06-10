# 4. Step 2：编写 CLAUDE.md — Harness 的"宪法"

> **Harness 类型：** Maintainability + Architecture Fitness — **Feedforward Guide (Inferential)**
>
> CLAUDE.md 是整个 Harness 的**核心入口**——所有 Agent 启动时最先读取的文件，定义了"在这个项目里怎么写代码"。

## 4.1 完整 CLAUDE.md

````markdown
# CLAUDE.md

## 项目概述
个人博客系统。技术栈：Spring Boot 3.2 + MySQL 8.0 + Redis 7 + Spring Security 6 + JWT (jjwt 0.12)
构建工具：Maven 3.9，JDK 17，多模块项目

## 命名规范

### Java 类命名
| 层级 | 命名规则 | 示例 |
|------|---------|------|
| Controller | `{Entity}Controller` | `ArticleController` |
| Service 接口 | `{Entity}Service` | `ArticleService` |
| Service 实现 | `{Entity}ServiceImpl` | `ArticleServiceImpl` |
| Mapper | `{Entity}Mapper` | `ArticleMapper` |
| 实体类 | 大驼峰，与表名对应 | `Article`、`ArticleTag` |
| Request DTO | `{Entity}{Action}Request` | `ArticleCreateRequest` |
| Response DTO | `{Entity}{Action}Response` | `ArticleDetailResponse` |
| VO (视图对象) | `{Entity}VO` | `ArticleVO`、`CommentTreeVO` |
| 枚举类 | 大驼峰 + `Enum` 后缀 | `ArticleStatusEnum` |
| 工具类 | `{功能}Utils` | `JwtUtils`、`RedisUtils` |
| 常量类 | `{模块}Constants` | `RedisConstants` |

### 数据库命名
- 表名：小写 + 下划线，单数（`article` 不是 `articles`）
- 关联表：`{表A}_{表B}` 按字母序（`article_tag`）
- 字段名：小写 + 下划线（`created_at`、`author_id`）
- 主键：`id` BIGINT AUTO_INCREMENT
- 必备字段：`created_at` DATETIME、`updated_at` DATETIME、`is_deleted` TINYINT(1) DEFAULT 0

### API 设计
- RESTful：GET/POST/PUT/DELETE `/api/{resources}`
- 统一响应：`{ "code": 200, "message": "ok", "data": {} }`
- 分页参数：`page`（从1开始）+ `size`（默认10，最大100）
- 分页响应：`{ "code": 200, "data": { "records": [...], "total": 100, "page": 1, "size": 10 } }`
- 错误码：业务错误 4xxxx，系统错误 5xxxx

### 包结构
```
com.blog.{module}.{layer}

模块划分（对应 Maven 子模块）：
├── com.blog.common        ← blog-common 模块（Entity、Utils、统一响应）
├── com.blog.api            ← blog-api 模块（前台接口）
│   ├── com.blog.api.article    → controller / service / mapper / entity / dto
│   ├── com.blog.api.category
│   ├── com.blog.api.tag
│   ├── com.blog.api.comment
│   └── com.blog.api.auth
└── com.blog.admin          ← blog-admin 模块（后台接口）
    ├── com.blog.admin.article
    └── com.blog.admin.system
```

## 编码规范

### 必须遵守（违反会被 Hook 拦截）
1. Controller 只做参数校验 + 调用 Service，**不允许包含业务逻辑**
2. Service 层方法必须加 `@Transactional`（读操作用 `@Transactional(readOnly = true)`）
3. 统一异常处理用 `@RestControllerAdvice`，**禁止**在 Controller 中 try-catch
4. 日志用 Lombok `@Slf4j`，**禁止** `System.out.println` 和 `printStackTrace()`
5. Redis key 格式：`blog:{module}:{business}:{id}`，如 `blog:article:view:123`
6. 数据库操作只用 MyBatis XML，**不允许**在 Mapper 接口中用注解写 SQL
7. 日期字段（created_at、updated_at）用 `LocalDateTime`，不用 `Date`

### 建议遵守
- 更新操作使用乐观锁（version 字段）
- 列表查询默认按 `created_at DESC` 排序
- 密码存储使用 BCryptPasswordEncoder
- JSON 序列化：Long 类型 ID 转 String 防止前端精度丢失

### 禁止事项（绝对不允许）
- 在 Controller 中直接操作 Mapper
- SQL 拼接（必须用参数化查询或 MyBatis `#{}` 占位符）
- 在循环中执行数据库操作（用批量方法替代）
- 硬编码配置（必须用 `application.yml` + `@ConfigurationProperties`）

## 模块依赖

```
blog-common  ← 被 blog-api 和 blog-admin 共同依赖
blog-api     ← 前台接口模块
blog-admin   ← 后台管理模块

重要规则：
- blog-common 不依赖任何其他模块
- blog-api 和 blog-admin 不直接互相依赖
- 修改 blog-common → 必须在 tasks/BLOCKERS.md 中记录，通知所有 Agent 重新编译
- 模块间通信通过 API 调用，不通过直接引用
```

## 技术栈细节

### Maven 依赖版本（关键项）
| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-parent | 3.2.x | 父 POM |
| mybatis-spring-boot-starter | 3.0.x | MyBatis |
| mysql-connector-j | 8.0.x | MySQL 驱动 |
| jjwt-api | 0.12.x | JWT |
| spring-boot-starter-data-redis | 3.2.x | Redis |
| springdoc-openapi-starter-webmvc-ui | 2.3.x | API 文档 |
| hutool-all | 5.8.x | 工具库 |

### application.yml 结构
- 公共配置放 `blog-common/src/main/resources/`
- 各模块特有配置放各自 `resources/`
- 敏感信息（密码、密钥）用环境变量 `${ENV_VAR}`

## 测试规范
- 单元测试：JUnit 5 + Mockito，测试 Service 层业务逻辑
- Mapper 测试：@MybatisTest + H2 内存数据库
- Controller 测试：@WebMvcTest + MockMvc
- 命名：`{被测类名}Test`
- 覆盖率目标：Service 层 > 80%，总体 > 60%
````

## 4.2 CLAUDE.md 中各部分的 Harness 分类

| 段落 | Harness 类别 | 作用 | 为什么用 Inferential |
|------|-------------|------|---------------------|
| 命名规范 | Maintainability | 约束命名模式 | 规则需要 AI 理解语义并应用 |
| 包结构 | Architecture Fitness | 约束代码组织 | 需要 AI 判断"这个类应该放哪" |
| 编码规范（必须） | Maintainability | 硬性约束 | Hook 负责 Computational 检查，CLAUDE.md 负责 Inferential 引导 |
| 编码规范（禁止） | Maintainability | 红线 | 防止 AI 走捷径（如直接用 Mapper） |
| 模块依赖 | Architecture Fitness | 架构边界 | 定义模块间的允许/禁止依赖方向 |
| API 设计 | Behaviour | 行为约束 | 定义接口长什么样 |

---

