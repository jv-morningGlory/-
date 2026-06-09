# 7. Step 5~8：Phase 2 核心功能开发（多 Agent 并行）

> **Harness 类型：** Architecture Fitness — 通过 Worktree 物理隔离模块边界
>
> Phase 1 搭建好了所有 Harness 基础设施，Phase 2 开始真正"开车"——Agent 在 Harness 约束下并行编码。

## 7.1 Step 5：创建 Worktree（架构隔离）

每个 Agent 在独立的 git worktree 中工作，**物理上无法修改别人的文件**。

```bash
# 为每个 Agent 创建独立分支和 worktree
git checkout -b feature/auth-category   # Agent-A
git checkout main
git checkout -b feature/article-tag     # Agent-B
git checkout main
git checkout -b feature/comment         # Agent-C

# 创建 worktree
git worktree add .claude/worktrees/agent-a feature/auth-category
git worktree add .claude/worktrees/agent-b feature/article-tag
git worktree add .claude/worktrees/agent-c feature/comment
```

## 7.2 Step 6：Agent-A — 认证 + 分类模块（T04 + T05）

```bash
cd .claude/worktrees/agent-a
claude
```

**Agent-A 的提示词：**

```
加载 prompts/impl/t04-auth.md 并执行。
加载 prompts/impl/t05-category.md 并执行。

按顺序完成：
T04 — JWT 认证（login/refresh/logout + SecurityConfig + JwtAuthFilter）
T05 — 分类 CRUD + 树形结构

你只能修改 com.blog.api.auth 和 com.blog.api.category 包下的文件。
```

**Agent-A 的工作流程（由 Harness 约束）：**
1. 启动时自动读取 CLAUDE.md → 理解命名规范和包结构
2. 读取 specs/impl-t04-auth.md → 知道要创建 14 个文件、方法签名、业务逻辑
3. 编码过程中每次保存文件 → Hook 自动检查命名 + 架构
4. 完成后执行 `mvn test` → 所有测试通过
5. 更新 tasks/TASKS.md → 勾选 T04、T05

## 7.3 Step 7：Agent-B — 标签 + 文章模块（T06 + T07）

```bash
cd .claude/worktrees/agent-b
claude
```

```
加载 prompts/impl/t06-tag.md 并执行。
加载 prompts/impl/t07-article.md 并执行。

按顺序完成：
T06 — 标签 CRUD + 多对多关联
T07 — 文章 CRUD + Markdown 渲染 + 分页列表

注意：T07 依赖 T04（认证）和 T05（分类），
如果相关接口未实现，用 mock/stub 替代，记录到 BLOCKERS.md。
```

## 7.4 Step 8：Agent-C — 评论模块（T08）

```bash
cd .claude/worktrees/agent-c
claude
```

```
加载 prompts/impl/t08-comment.md 并执行。

完成：评论/回复 + 树形列表 + 审核机制
注意：依赖 T07（文章）和 T04（认证），未实现部分用 mock。
```

> **Phase 2 关键点：** 三个 Agent 各自在独立 worktree 中工作，物理隔离 + Harness 约束 = 零冲突。Agent 遇到依赖未实现时自动用 mock/stub 替代，不会卡住。

---

