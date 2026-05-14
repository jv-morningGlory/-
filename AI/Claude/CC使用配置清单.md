# Claude Code 高效使用配置清单

> 用好 Claude Code = 维护好 CLAUDE.md（核心规范）+ 安装合适的 Skills（能力插件）+ 配置必要的 MCP（外部工具）+ 掌握会话管理技巧。

---

## 一、核心配置：CLAUDE.md（必须维护）

这是 Claude Code 的"系统提示词"，必须由开发者自己维护，是 AI 理解你项目规范的核心文件。

### 1.1 为什么必须自己维护？

CLAUDE.md 存储的是"代码里看不出来"的隐性知识：

- 历史包袱区不能碰
- 某个模块连着外部系统
- 团队特有的命名规范
- 构建命令和测试流程

AI 无法自动沉淀这些"正确"的规范，必须由开发者手动维护。

### 1.2 三层配置体系

| 层级 | 位置 | 用途 | 是否提交 Git |
|------|------|------|-------------|
| 全局级 | `~/.claude/CLAUDE.md` | 个人通用偏好（如中文回复、回答风格） | 否 |
| 项目级 | `./CLAUDE.md` | 团队共享的项目规范、构建命令 | 是 |
| 个人覆盖 | `./CLAUDE.local.md` | 个人私有覆盖，不提交 | 否 |

优先级规则：全局 → 项目级 → 个人覆盖，后加载的覆盖先加载的。当指令冲突时，Claude 倾向于遵循更具体的项目级规范。

### 1.3 编写黄金法则

- **少即是多**：控制在 200-300 行以内，越短越好
- **具体明确**：给出具体示例和规则，避免模糊表述
- **事故驱动**：优先记录踩过的坑，比写几百条规约更有效
- **使用强制性关键词**：用"必须/禁止"这类关键词，遵循率明显更高

### 1.4 让 AI 帮你维护

在 CLAUDE.md 中加入以下指令，让 AI 自主更新规则：

> 当学到这个项目的新规范时，建议更新 CLAUDE.md

这样每次纠正完 Claude 的错误后，直接告诉它："把这条规则加到 CLAUDE.md 里。"

---

## 二、Skills 系统（按需安装）

Skills 是给 Claude Code 安装的"插件"或"技能包"，每个 Skill 是一个结构化的文件夹，包含一个 SKILL.md 核心指令文件。

### 2.1 推荐安装的技能

**新手必装（3个核心）**：

| Skill | 作用 |
|-------|------|
| Superpowers | 社区公认最强 Skill，强制 AI 遵循五步工程化流程：头脑风暴、任务拆解、TDD 强制测试、子代理执行等 |
| Code Review | 自动化代码质检，检查空指针、性能问题、安全漏洞 |
| Planning with Files | 长任务不"失忆"，把计划和进度写进文件，任务中断还能继续 |

**进阶用户加装**：

| Skill | 作用 |
|-------|------|
| Ralph Loop | 防止 AI 做到 60% 就说"你后续再完善"，真正推到完成 |
| agent-browser | 让 Claude 能操作浏览器，适合网页测试和数据抓取 |

### 2.2 安装方式

```bash
# 方式一：从官方市场安装
/plugin marketplace add obra/superpowers-marketplace
/plugin install superpowers@superpowers-marketplace

# 方式二：用 CLI 工具安装
npx skills add https://github.com/vercel-labs/next-skills -a claude-code --skill next-best-practices -y

# 方式三：从 skillsmp 平台查找更多 Skill
# https://skillsmp.com 目前有 6 万多个 Skills
```

### 2.3 注意事项

- 不要装太多，真正高频用的就那么几个，装太多容易指令打架、占用上下文
- Skill 不是万能药，它本质上是 Markdown 指令文件，你写得越具体，AI 执行越好
- 建议围绕你的主工作流装 3-5 个，用顺再扩展

---

## 三、Memory 记忆系统

Claude Code 自带持久化记忆系统，每个项目自动在 `~/.claude/projects/<项目路径>/memory/` 下维护记忆文件。这是 CLAUDE.md 之外的另一个持久化层——CLAUDE.md 存规范，Memory 存"发生过什么"。

### 3.1 记忆存储结构

```
~/.claude/projects/c--Users-heyoufeng-Desktop-xxx--/
└── memory/
    ├── MEMORY.md          ← 索引文件（每行一条摘要，CLI 自动加载）
    ├── user_role.md       ← 用户角色、技术栈、偏好
    ├── feedback_test.md   ← 你纠正过的做法，下次不再犯
    └── project_xxx.md     ← 项目背景：正在做什么、为什么
```

**MEMORY.md 是索引，不是正文**——内容很短，每行一个条目链接，确保每次会话都能快速加载。

### 3.2 四种记忆类型

| 类型 | 存什么 | 示例 |
|------|--------|------|
| **user** | 你的角色、技术栈、知识背景 | "Java 后端，10 年经验，刚接触前端" |
| **feedback** | 你纠正过的做法 + 原因 | "不要 mock 数据库，上次 mock 通过但生产炸了" |
| **project** | 项目当前的背景、目标、约束 | "正在重构认证模块，截止周五冻结" |
| **reference** | 外部系统在哪找 | "Bug 跟踪在 Linear 项目 INGEST" |

### 3.3 使用方式

```
"把这个规则记住"       → Claude 写入对应类型的 memory 文件
"忘掉上次那个规则"     → Claude 删除对应 memory
"这个项目还有哪些背景？" → Claude 读取 MEMORY.md 索引
```

Claude 会在每次会话开始时自动加载 MEMORY.md。当相关记忆匹配当前任务时，Claude 会读取对应文件获取详情。

### 3.4 什么不该存

- 代码规范、架构、文件路径 → 这些在 CLAUDE.md 里
- Git 历史 → `git log` 自己查
- 一次性调试记录 → 修完就过时了

> Memory 的核心价值：跨会话记住"你是谁"和"发生过什么"，让 AI 在新会话中不需要重新认识你。

---

## 四、Hooks 钩子系统

可以在 `settings.json` 中配置事件钩子，在特定时机自动执行脚本：

```json
{
  "hooks": {
    "PostToolUse": [
      { "matcher": "Edit|Write", "command": "prettier --write $FILE" }
    ]
  }
}
```

常用事件：

| 事件 | 触发时机 |
|------|---------|
| `SessionStart` | 会话启动时 |
| `PreToolUse` | 工具执行前 |
| `PostToolUse` | 工具执行后 |

适合自动格式化代码、自动跑 lint 等。

---

## 五、MCP 工具配置（扩展能力）

MCP（Model Context Protocol）是让 Claude Code 连接外部世界的"万能插头"，可以访问浏览器、数据库、代码仓库、设计工具等。

### 5.1 常用 MCP 工具

| MCP 工具 | 作用 |
|----------|------|
| chrome-devtools | 浏览器操作、网页测试 |
| Figma MCP | 访问设计稿，获取样式信息 |
| 数据库 MCP | 直接连接 PostgreSQL、MySQL 等 |
| 文件系统 MCP | 读写本地文件 |

### 5.2 配置方式

在 `.claude/settings.json` 中配置 `mcpServers`：

```json
{
  "mcpServers": {
    "chrome-devtools": {
      "command": "npx",
      "args": ["@anthropic/mcp-chrome-devtools"]
    }
  }
}
```

---

## 六、自定义 Slash Commands

在 `.claude/commands/` 目录下放 `.md` 文件，自动变成 `/` 命令：

```
.claude/commands/
  deploy.md     →  /deploy 执行部署流程
  review.md     →  /review 执行代码审查
  weekly.md     →  /weekly 生成周报
```

---

## 七、Settings 常用配置

```json
{
  "permissions": {
    "allow": ["Bash(npm:*)", "Bash(git:*)", "Read", "Write", "Edit"],
    "deny": ["Bash(rm -rf:*)", "Bash(curl:*)", "Bash(gh:*)", "Bash(ssh:*)"]
  },
  "statusLine": { "type": "rainbow" },
  "enableAllProjectMcpServers": false
}
```

> 按需白名单比每次确认更流畅，同时守住安全底线。

---

## 八、会话与项目管理（日常维护）

### 8.1 常用命令速查

| 命令 | 作用 | 场景 |
|------|------|------|
| `/init` | 自动生成初始 CLAUDE.md | 新项目起步 |
| `/compact` | 压缩对话，续命 | 上下文快满时 |
| `/clear` | 清空上下文重来 | AI 思路跑偏时 |
| `/model` | 切换模型 | 需要更强/更省模型时 |
| `/cost` | 查看 token 使用情况 | 控制成本 |
| `/status` | 查看当前状态 | 诊断问题 |
| `/export` | 导出对话到文件 | 保留重要对话 |

### 8.2 三种工作模式

| 模式 | 触发方式 | 适用场景 |
|------|---------|---------|
| 自动编辑模式 | Shift+Tab 一次 | 免确认批量操作，创建文件、修改代码 |
| Plan 模式 | Shift+Tab 两次 | 先规划再动手，复杂任务 |
| Yolo 模式 | `claude --dangerously-skip-permissions` | 全权限放手干，重构 |

### 8.3 会话管理技巧

- **随时暂停与回滚**：按 Esc 可暂停当前操作；双击 Esc 可恢复历史状态
- **恢复历史会话**：`claude -c` 进入最近会话，`claude -r` 选择历史会话
- **Shell 快捷方式**：在聊天框输入 `! <命令>` 直接执行 shell 命令并把输出带回对话

### 8.4 Git Worktree 隔离

对于大型重构，可以用 worktree 模式在隔离的 git 分支上操作，不影响主工作区，完成后再合并。

---

## 九、避坑指南

### 9.1 常见问题

| 问题 | 解决方案 |
|------|---------|
| AI 不遵循 CLAUDE.md | 用 `/context` 检查是否正确加载；检查指令是否明确；检查不同层级文件是否存在矛盾 |
| AI 生成"虚假成功" | 在 CLAUDE.md 中加入："每次宣称成功必须附证据"；定期反问"真的完成了？有证据吗？" |
| 上下文溢出 | 手动 `/compact` 压缩；注意观察 "Context left until auto-compact" 的提示 |

### 9.2 安全建议

- 不要在会话目录存放 SSH Key 等敏感信息
- 敏感操作建议在 Docker 环境中执行
- 开启 git 版本控制，方便回滚
- 权限白名单守住底线：禁止 `rm -rf`、`curl`、`ssh` 等危险操作

---

## 十、维护清单

| 维护项 | 频率 | 说明 |
|--------|------|------|
| CLAUDE.md | 持续迭代 | 核心配置文件，每次发现新规范或踩坑后更新 |
| Memory | 按需管理 | 告诉 Claude 记住关键偏好和项目背景 |
| Skills 安装 | 按需新增 | 围绕主工作流装 3-5 个，用顺再扩展 |
| MCP 配置 | 按需添加 | 需要连接外部工具时配置 |
| Hooks | 按需配置 | 自动化格式化、lint 等重复操作 |
| 会话整理 | 每周 | 清理无用会话，重命名重要会话 |
| 成本监控 | 每天查看 | 用 `npx ccusage@latest` 查看 token 消耗 |

---

> 四个核心都需要你主动投入：维护 CLAUDE.md / 安装合适 Skills / 配置必要 MCP / 掌握会话管理技巧。投入越多，AI 犯错越少，效率提升越大。
