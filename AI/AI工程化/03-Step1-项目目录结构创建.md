# 3. Step 1：创建项目目录结构

> **Harness 类型：** Architecture Fitness — 目录结构本身就是架构约束的物理体现

```bash
mkdir -p blog-system && cd blog-system
git init

# === Harness 层目录（控制 Agent 的"操作系统"） ===
mkdir -p specs            # 规格文档：PRD、API、数据库、实现规格
mkdir -p prompts/agents   # Agent 角色定义（"你是谁"）
mkdir -p prompts/shared   # 共享规范片段（"所有人都要遵守"）
mkdir -p prompts/impl     # 任务执行指令（"这次做什么"）

# === Claude Code 配置 ===
mkdir -p .claude/skills   # 可复用 Skill 定义
mkdir -p .claude/hooks    # Hook 脚本

# === 追踪文件 ===
mkdir -p tasks            # 任务进度、阻塞项、测试追踪

# === 代码模块（Maven 多模块） ===
mkdir -p blog-api         # 前台 API 模块（游客 + 博主接口）
mkdir -p blog-admin       # 后台管理模块（管理员接口）
mkdir -p blog-common      # 公共模块（Entity、Utils、统一响应）
```

**完整初始文件结构（含后续步骤将创建的所有文件）：**

```
blog-system/
├── CLAUDE.md                  ← S2 编写 — 整个 Harness 的"宪法"
│
├── specs/                     ← 规格层：定义"做什么"
│   ├── PRD.md                 ← S3 编写
│   ├── api-spec.md            ← S4c 编写
│   ├── db-schema.md           ← S4c 编写
│   └── impl-t04-*.md ~ t11-*.md  ← S4b 生成（8份）
│
├── prompts/                   ← 提示词层：定义 Agent"怎么想"
│   ├── agents/backend.md      ← 角色层（稳定，不常改）
│   ├── shared/naming.md       ← 规范层（跟随 CLAUDE.md 更新）
│   ├── shared/error-handling.md
│   └── impl/t04-*.md ~ t11-*.md  ← 任务层（每个任务一份）
│
├── .claude/                   ← Claude Code 配置
│   ├── settings.json          ← S4e — Hook 配置
│   ├── skills/                ← S4f — 可复用 Skill
│   └── hooks/                 ← S4e — Hook 脚本
│
├── tasks/                     ← 追踪文件
│   ├── TASKS.md               ← 任务看板
│   └── BLOCKERS.md            ← 阻塞项记录
│
├── blog-api/                  ← Maven 模块
├── blog-admin/
├── blog-common/
└── pom.xml
```

**为什么要这样设计目录？**

| 目录 | Harness 角色 | 设计意图 |
|------|-------------|---------|
| `specs/` | Feedforward Guide | Agent 编码前必须读取，确保理解一致 |
| `prompts/agents/` | 角色约束 | 限制 Agent 的职责范围，防止越界 |
| `prompts/shared/` | 规范复用 | DRY 原则——规范只写一次，所有 Agent 引用 |
| `prompts/impl/` | 任务指令 | 每个任务一份，可独立迭代 |
| `.claude/hooks/` | Feedback Sensor | 自动触发，Agent 无法绕过 |
| `.claude/skills/` | 可复用操作 | 把重复工作封装成命令 |
| `tasks/` | 进度追踪 | 人类看一眼就知道项目状态 |

---

