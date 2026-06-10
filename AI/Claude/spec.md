# Spec（规格文件）笔记

## 一、什么是 Spec

**Spec**（Specification）是规格文件，在 AI 工程化中用来**把需求从模糊的"做什么"精确到"怎么做"**。

**为什么需要 Spec？** AI Agent 拿到一个模糊需求（如"做个博客系统"）时会自由发挥，每次实现都不一样。Spec 的作用是**提前把所有细节定死**，Agent 只需要照着执行，不需要做任何设计决策。

### Spec 的三层体系

| 层级 | 文件 | 回答的问题 | 类比 |
|------|------|-----------|------|
| **需求层** | `specs/PRD.md` | 做什么？给谁用？ | 产品经理的需求文档 |
| **契约层** | `specs/api-spec.md` / `specs/db-schema.md` | 接口长什么样？表结构是什么？ | 建筑蓝图（所有人必须遵守） |
| **实现层** | `specs/impl-t{xx}.md` | 写几个文件？方法签名？业务逻辑？ | 工人的零件清单 |

> 好的 Spec = Agent 拿到后不需要猜测任何东西，照着写就行。

---

## 二、市面上常用的 Spec 工具和格式

| 工具/格式 | 适用场景 | 特点 |
|----------|---------|------|
| **OpenAPI / Swagger** | REST API 规格 | 行业标准，自动生成文档和代码，生态最成熟 |
| **GraphQL Schema** | GraphQL API | 类型系统即文档，前后端契约一体 |
| **Protocol Buffers** | gRPC / 微服务通信 | 强类型 IDL，跨语言代码生成 |
| **DBML / TablePlus** | 数据库设计 | 可视化 ER 图，从文本生成 DDL |
| **PRD（产品需求文档）** | 需求定义 | 非结构化，但适合描述"做什么"和"为什么" |
| **Design Doc** | 技术方案设计 | Google 风格，描述架构选型和权衡 |
| **ADR（架构决策记录）** | 记录关键决策 | 只记"为什么这么选"，不记怎么做 |

### AI 工程化中的 Spec 选择

传统开发用 OpenAPI/Swagger 就够了，但 **AI Agent 并行开发**时有更高要求：

- **多 Agent 协作** → 需要 API 契约 + DB 契约作为"宪法"，防止合并冲突
- **Agent 上下文有限** → 需要把实现规格拆到每个任务一份，而不是一个超大文档
- **自动质检** → Spec 要足够精确（方法签名、命名规范），才能写 Hook 自动校验

所以在 Claude Code 工作流中，通常采用**自定义 Spec**（Markdown 格式），而不是直接用 OpenAPI。

---

## 三、自定义 Spec 解读

以博客系统为例，自定义 Spec 分为两类：

### 3.1 契约层 Spec（全局共享）

所有 Agent 必须遵守的"宪法"，包括 API 接口契约和数据库契约。

**API 契约 `specs/api-spec.md` 示例：**

````markdown
# 博客系统 API 规格 v1.0

## 通用规范
- Base URL：`http://localhost:8080/api`
- 统一响应：`{ "code": 200, "message": "ok", "data": {} }`
- 分页响应：`{ "code": 200, "data": { "records": [...], "total": 100, "page": 1, "size": 10 } }`
- 认证 Header：`Authorization: Bearer {access_token}`

## 1. 认证接口

| 方法 | 路径 | 认证 | Request | Response | 错误码 |
|------|------|------|---------|----------|--------|
| POST | `/api/auth/login` | 否 | `{"username":"admin","password":"123456"}` | `{"accessToken":"...","refreshToken":"...","expiresIn":7200}` | 40100 用户名或密码错误 |
| POST | `/api/auth/refresh` | 否 | `{"refreshToken":"eyJhbG..."}` | `{"accessToken":"...","refreshToken":"...","expiresIn":7200}` | 40101 Token 过期, 40102 Token 无效 |
| POST | `/api/auth/logout` | 是 | — | null | — |

## 2. 分类接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/categories` | 否 | 树形列表 |
| POST | `/api/categories` | 是 | 创建分类 |
| PUT | `/api/categories/{id}` | 是 | 更新分类 |
| DELETE | `/api/categories/{id}` | 是 | 删除（有关联文章时返回 409） |

## 3. 标签接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/tags` | 否 | 分页列表 `?page=1&size=20&keyword=Java` |
| POST | `/api/tags` | 是 | 创建标签 |
| PUT | `/api/tags/{id}` | 是 | 更新标签 |
| DELETE | `/api/tags/{id}` | 是 | 删除（有文章关联时返回 409） |

## 4. 文章接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/articles` | 否 | 列表 `?page=1&size=10&categoryId=2&tagId=5&keyword=Spring&status=PUBLISHED` |
| GET | `/api/articles/{slug}` | 否 | 详情（含上一篇/下一篇） |
| POST | `/api/articles` | 是 | 创建 `{"title":"...","slug":"...","content":"...","categoryId":2,"tagIds":[5,6],"status":"DRAFT"}` |
| PUT | `/api/articles/{id}` | 是 | 更新 |
| DELETE | `/api/articles/{id}` | 是 | 软删除 |

## 5. 评论接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/articles/{articleId}/comments` | 否 | 树形评论列表 |
| POST | `/api/articles/{articleId}/comments` | 否 | 发表评论（游客，默认 PENDING 待审核） |
| PUT | `/api/admin/comments/{id}/audit` | 是 | 审核评论 |
| DELETE | `/api/admin/comments/{id}` | 是 | 删除评论 |

## 6. 搜索接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/search` | 否 | 全文搜索 `?keyword=Spring&categoryId=2&page=1&size=10`，返回高亮 |
| GET | `/api/search/history` | 是 | 搜索历史 |
| DELETE | `/api/search/history` | 是 | 清除历史 |

## 7. 统计接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/statistics/dashboard` | 是 | 仪表盘：文章总数、评论总数、今日浏览、待审评论 |
| GET | `/api/statistics/articles/hot` | 否 | 热门文章 TOP 10 `?period=WEEK|ALL` |
| GET | `/api/statistics/tags` | 否 | 标签使用统计 |

## 错误码汇总

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| 200 | 200 | 成功 |
| 40000 | 400 | 参数校验失败 |
| 40100 | 401 | 用户名或密码错误 |
| 40101 | 401 | Token 过期 |
| 40102 | 401 | Token 无效 |
| 40103 | 401 | Token 已被加入黑名单 |
| 40300 | 403 | 无权限 |
| 40400 | 404 | 资源不存在 |
| 40900 | 409 | 资源冲突 |
| 50000 | 500 | 系统内部错误 |
````

### 3.2 实现层 Spec（每个任务一份）

基于契约层，为每个任务生成详细的实现规格。通过 `create-spec` Skill 自动生成，包含 5 个要素：

| 要素 | 作用 | 示例 |
|------|------|------|
| **文件清单** | Agent 要写哪些文件 | `AuthService.java`、`AuthController.java`、`UserMapper.xml` |
| **方法签名** | 接口长什么样 | `LoginResponse login(LoginRequest request)` |
| **业务逻辑** | 每一步做什么 | 查用户 → 比密码 → 生成 Token → 返回 |
| **依赖接口** | 需要其他模块提供什么 | 无上游依赖，下游可获取 userId |
| **测试清单** | 怎么验证 | `login_成功_返回Token对`、`login_密码错误_返回401` |

### 3.3 Spec 在工作流中的位置

```
PRD（需求）→ API/DB 契约（宪法）→ 实现规格（图纸）→ Agent 编码 → Hook 校验
```

1. 先写 **PRD** 定义需求
2. 再写 **API + DB 契约**，所有模块共享
3. 然后基于契约生成每个任务的 **实现规格**
4. Agent 拿着规格编码，不需要猜测
5. Hook 自动校验输出是否符合规格

> **一句话总结：** Spec 的核心价值是把 AI 的自由发挥空间压缩到零——每一个文件名、方法签名、业务逻辑步骤都提前定死，Agent 只需要"照图施工"。
