# Claude Code Skill 笔记

## 一、Skill 是什么

**Skill** 是 Claude Code 的"能力插件"——本质是一个包含 `SKILL.md` 的文件夹，里面写好了结构化指令。安装后 Claude 自动获得对应能力，你可以通过 `/skill名` 手动触发，Claude 也会根据 `description` 自动匹配并加载。

---

## 二、市面上对编程友好的 Skill

### 新手必装（3 个核心）

| Skill | 作用 |
|-------|------|
| **Superpowers** | 社区公认最强 Skill，强制 AI 遵循五步工程化流程：头脑风暴 → 任务拆解 → TDD 测试 → 子代理执行 |
| **Code Review** | 自动化代码质检，检查空指针、性能问题、安全漏洞 |
| **Planning with Files** | 长任务不"失忆"，把计划和进度写进文件，任务中断还能继续 |

### 进阶加装

| Skill | 作用 |
|-------|------|
| **Ralph Loop** | 防止 AI 做到 60% 就说"你后续再完善"，真正推到完成 |
| **agent-browser** | 让 Claude 操作浏览器，适合网页测试和数据抓取 |

### 安装方式

```bash
# 从官方市场安装
/plugin marketplace add obra/superpowers-marketplace
/plugin install superpowers@superpowers-marketplace

# 用 CLI 工具安装
npx skills add https://github.com/vercel-labs/next-skills -a claude-code --skill next-best-practices -y

# 从 skillsmp 平台查找更多（6 万+ Skills）
# https://skillsmp.com
```

> 不要装太多，3-5 个高频 Skill 足够。装太多容易指令打架、占用上下文。

---

## 三、如何自定义 Skill

### 3.1 文件结构

```
.claude/skills/<skill-name>/
├── SKILL.md           # 主指令文件（必需）
├── template.md        # 模板文件（可选）
└── examples/          # 示例输出（可选）
```

### 3.2 存放位置

| 级别 | 路径 | 适用范围 |
|------|------|---------|
| 个人 | `~/.claude/skills/<name>/SKILL.md` | 你的所有项目 |
| 项目 | `.claude/skills/<name>/SKILL.md` | 仅当前项目 |

> 命令名由目录名决定：`.claude/skills/split-task/SKILL.md` → `/split-task`。放到目录下自动发现，无需重启。

### 3.3 SKILL.md 格式

由 **YAML frontmatter** + **Markdown 正文**组成：

```yaml
---
name: my-skill                 # 显示名称，默认取目录名
description: 这个 skill 做什么   # Claude 据此决定何时自动加载
argument-hint: "[issue-number]" # 自动补全参数提示
arguments: [issue, branch]      # 命名位置参数，正文中用 $issue 引用
disable-model-invocation: true  # true = 只能 /xxx 手动调用
user-invocable: true            # false = 从 / 菜单隐藏
allowed-tools: Bash(git *)      # 激活时免确认的工具
context: fork                   # 在隔离子 agent 中运行
---

Markdown 正文，给 Claude 的具体指令。
参数：$ARGUMENTS（全部）、$0/$1（按位置）、$issue（命名参数）
动态注入：!`git diff HEAD`（执行命令，输出内联到 prompt）
```

### 3.4 常用 frontmatter 字段速查

| 字段 | 必需 | 说明 |
|------|------|------|
| `description` | 推荐 | 功能描述，Claude 据此自动匹配 |
| `user-invocable` | 否 | `false` = 隐藏，仅供内部调用 |
| `disable-model-invocation` | 否 | `true` = 禁止自动加载，只能手动调用 |
| `arguments` | 否 | 命名位置参数 |
| `argument-hint` | 否 | 自动补全提示 |
| `allowed-tools` | 否 | 免确认工具白名单 |
| `context` | 否 | `fork` = 子 agent 隔离 |
| `paths` | 否 | Glob 限制自动激活范围 |

---

## 四、实战 Skill 分享

### 4.1 split-task — 任务拆分

> **思路：** 任务拆分 = 先分阶段 → 再列任务 → 标注任务属性（编号、名称、负责 Agent、优先级、依赖关系、可并行性）

```yaml
---
name: split-task
description: 将 PRD 拆解成可并行的子任务
user-invocable: true
---
读取 specs/PRD.md。按模块依赖关系拆分任务到 tasks/TASKS.md。
规则：
1. 按 Phase 组织：基础依赖 → 核心功能 → 增强功能 → 测试
2. 标注每个任务：编号、名称、负责Agent、优先级、依赖关系、可并行性
3. 依赖关系必须精确到"T04 → T07"粒度
4. 可并行的任务放在同一 Phase 并列标注 ⚡
5. 输出完整的 TASKS.md
```

**核心设计思想：**

- **按 Phase 分层**：串行阶段（基础设施）→ 并行阶段（核心功能）→ 并行阶段（增强功能）→ 串行阶段（测试）
- **依赖精确化**：不只是"T04 依赖 T03"，而是标注"T07 依赖 T04 + T05 + T06"，方便判断哪些可以并行
- **可并行标注**：用 ⚡ 标记，一眼看出哪些任务可以同时分配给不同 Agent

### 4.2 create-spec — 生成实现规格

> **思路：** 读取 CLAUDE.md 的命名规范 + API/DB 契约，输出每个任务的详细实现规格（文件清单、方法签名、业务逻辑、测试用例）

```yaml
---
name: create-spec
description: 根据 PRD + API/DB 契约生成指定模块的详细实现规格
user-invocable: true
argument-hint: "<task-number> <module-name>"
---
读取：
- specs/PRD.md（需求来源）
- specs/api-spec.md（接口契约）
- specs/db-schema.md（数据库契约）
- CLAUDE.md（命名和编码规范）

为指定任务（如 T04）生成 specs/impl-t{xx}.md，包含：
1. 涉及文件清单（每个 Java 类的完整路径）
2. 核心方法签名（Service 接口和 Controller 端点）
3. 业务逻辑要点（校验规则、边界条件、异常情况）
4. 依赖接口列表（需要其他模块提供什么）
5. 测试清单（正常流 + 异常流 + 边界值）

所有命名必须符合 CLAUDE.md 规范。
```

**核心设计思想：**

- **多源输入**：同时读取 PRD + API 契约 + DB 契约 + CLAUDE.md，确保规格和全局规范一致
- **命名强制**：通过读取 CLAUDE.md 的命名规范，生成的文件名、类名、方法名直接符合项目标准，Agent 不需要做命名决策
- **5 要素全覆盖**：文件清单（写什么）→ 方法签名（怎么调）→ 业务逻辑（怎么处理）→ 依赖接口（和谁交互）→ 测试清单（怎么验证）
