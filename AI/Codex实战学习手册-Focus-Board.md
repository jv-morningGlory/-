# Codex 实战学习手册：从 0 完成一个小项目

> 目标：学会用 Codex 完成项目，而不是只学会“叫 AI 写代码”。  
> 方式：你自己一步步执行；本手册告诉你“做什么 / 何时做 / 怎么对 AI 说”。  
> 预计总时长：1 天（也可拆成 2–3 个晚上）。

---

## 0. 你将学会什么

完成本手册后，你应掌握：

1. 用 `config.toml` 立规矩  
2. 用 `AGENTS.md` 画项目地图  
3. 用多个 subagent 分工合作  
4. 用 `workflows/` 管理需求→开发→审查→修复→发布  
5. 把重复步骤沉淀为 skill  
6. 启用并验证 hooks（钩子）  
7. 接入并使用一个 MCP  
8. 交付一个“框架完整、可运行、可预览”的小项目

---

## 1. 小项目定义（足够小，但有 2 个需求）

### 项目名
**今日专注板（Focus Board）**

### 一句话
做一个本地可预览的单页，展示“今日专注目标 + 任务进度”。

### 两个需求（必须都做）

#### 需求 A：专注信息展示
页面显示：
- 标题：今日专注
- 今日目标（一句话）
- 3 条任务
- 更新时间

#### 需求 B：进度状态展示
页面显示：
- 已完成数量 / 总任务数（如 `1/3`）
- 状态文案：未开始 / 进行中 / 已完成
- 进度变化后，更新时间要变

### 明确不做（防范围膨胀）
- 不做登录
- 不做后端/数据库
- 不做移动 App
- 不接真实日历 API（可硬编码或本地编辑数据）

### 技术约束
- 前端静态页：`site/index.html` + `site/style.css`（可加一点 `site/app.js`）
- 数据可放 `site/data.json`
- 本地用浏览器打开即可

### 完成线（Definition of Done）
- [ ] 本地打开页面，需求 A/B 都可见
- [ ] 有完整 Codex 项目框架（见第 3 节目录）
- [ ] 至少 4 个自定义 subagent 可调用
- [ ] 至少 1 个 skill（由重复步骤沉淀）
- [ ] hooks 至少触发并验证 1 次
- [ ] MCP 至少成功调用 1 次
- [ ] README 写清：如何预览、如何让 AI 改状态

---

## 2. 角色设计：多个 Subagent 怎么合作

### 你要创建的 4 个员工（自定义 agent）

| Agent | 职责 | 可以改代码吗 | 主要产出 |
|------|------|-------------|---------|
| `pm-agent` | 聊需求、澄清范围、写验收标准 | 原则上不改业务代码 | `docs/requirements.md` |
| `dev-agent` | 按需求实现页面与基础交互 | 可以 | `site/*` |
| `review-agent` | 只读审查，找问题 | 默认不直接改 | `docs/review-report.md` |
| `bugfix-agent` | 按审查问题做最小修复 | 可以（小改动） | 修复后的代码 + 验证说明 |

### 合作方式（固定流水线，不要一开始就全并行）

```text
你
 └─ pm-agent：把 2 个需求写清楚
      └─ 你确认需求
           └─ dev-agent：实现需求 A
                └─ dev-agent：实现需求 B
                     └─ review-agent：审查
                          └─ bugfix-agent：修 P0/P1
                               └─ review-agent：复审
                                    └─ 你验收并收尾
```

### 何时并行（学会“多 agent 合作”的关键点）
在“第一版可运行后”，可以并行：

- 并行 1：`review-agent` 查代码问题  
- 并行 2：`pm-agent` 补验收清单与发布检查项  

提示词示例：

> 请同时启动两个 subagent：  
> 1) `review-agent`：只读审查当前实现，输出问题清单  
> 2) `pm-agent`：根据当前实现补全验收清单到 `docs/acceptance.md`  
> 都完成后，由主对话汇总，先不改代码。

### 合作规则（写进每个 agent 的 instructions）
1. 不越权：pm 不写功能代码，review 默认不直接大改  
2. 交接有文档：上一个 agent 的产出，是下一个 agent 的输入  
3. 一次只优化当前目标，不借机重构  
4. 每步结束都要汇报：做了什么、文件在哪、下一步建议

---

## 3. 最终应形成的项目框架

```text
focus-board/
├─ README.md
├─ AGENTS.md
├─ .gitignore
├─ .codex/
│  ├─ config.toml
│  ├─ hooks.json
│  ├─ agents/
│  │  ├─ pm-agent.toml
│  │  ├─ dev-agent.toml
│  │  ├─ review-agent.toml
│  │  └─ bugfix-agent.toml
│  └─ hooks/                      # 可先简用官方/本机示例结构
│     ├─ scripts/
│     └─ config/
├─ .agents/
│  └─ skills/
│     └─ update-focus-board/
│        └─ SKILL.md
├─ workflows/
│  ├─ 01-discovery.md
│  ├─ 02-dev.md
│  ├─ 03-review.md
│  ├─ 04-bugfix.md
│  └─ 05-release.md
├─ docs/
│  ├─ requirements.md
│  ├─ acceptance.md
│  └─ review-report.md
└─ site/
   ├─ index.html
   ├─ style.css
   ├─ app.js
   └─ data.json
```

> 说明：先追求“框架搭全 + 主流程跑通”，不要一开始就做复杂美观。

---

## 4. 执行总时间表（按阶段，不按小时死磕）

| 阶段 | 你要完成 | 对应学习点 |
|------|----------|------------|
| Phase 0 | 建仓 + 最小 config + AGENTS.md | 制度与地图 |
| Phase 1 | 4 个 agent + 5 条 workflow 骨架 | subagent 与流程 |
| Phase 2 | pm-agent 产出双需求文档 | 需求能力 |
| Phase 3 | dev-agent 实现 A/B | 开发能力 |
| Phase 4 | review + bugfix 合作 | 多 agent 接力 |
| Phase 5 | 重复操作抽 skill | skill 沉淀 |
| Phase 6 | 开 hooks 并验证 | 钩子 |
| Phase 7 | 接 MCP 并真实调用一次 | MCP |
| Phase 8 | 验收与复盘 | 形成可重复方法 |

---

# 5. 一步一步执行文档（照做）

---

## Phase 0：初始化项目（先制度，后地图）

### 步骤 0.1 建文件夹
手动创建：

```text
C:\Users\heyoufeng\Desktop\focus-board
```

### 步骤 0.2 用 Codex 打开该目录
在该目录启动 Codex（CLI 或桌面端均可）。

### 步骤 0.3 第一次怎么对 AI 说（初始化）

复制这条：

```text
这是一个学习项目：Focus Board（今日专注板）。
请只做初始化，不要实现业务功能。

请创建：
1) README.md（项目说明，中文）
2) AGENTS.md（给 Codex 的项目地图）
3) .gitignore
4) .codex/config.toml（最小安全配置）
5) docs/、workflows/、site/ 空目录说明

要求：
- sandbox 友好：默认 workspace-write + on-request
- AGENTS.md 写清两个需求 A/B、不做事项、完成标准
- 先不要创建 agent/skill/hooks 的复杂实现
- 完成后列出创建了哪些文件
```

### 步骤 0.4 你何时算完成
- [ ] `README.md` 存在  
- [ ] `AGENTS.md` 存在  
- [ ] `.codex/config.toml` 存在且有 model/sandbox/approval  
- [ ] 还没有业务页面也没问题

### 为什么这个时机写 config / AGENTS.md
- **config**：AI 一动手就有权限边界  
- **AGENTS.md**：后续所有 agent 有共同地图  
- 此时还不必写 skill（没有重复步骤）

---

## Phase 1：配置 4 个 Subagent + 工作流骨架

### 步骤 1.1 什么时候做
初始化完成后立刻做。  
**在写业务代码前**，先把“员工”和“工单模板”备齐。

### 步骤 1.2 对 AI 说

```text
请为这个项目配置 4 个自定义 subagent，并注册到 .codex/config.toml：

1) pm-agent：只负责需求澄清与文档，不写业务代码
2) dev-agent：负责实现 site 页面与交互
3) review-agent：只读审查，输出 docs/review-report.md
4) bugfix-agent：按审查问题做最小修复

同时创建 workflows 骨架：
- workflows/01-discovery.md
- workflows/02-dev.md
- workflows/03-review.md
- workflows/04-bugfix.md
- workflows/05-release.md

每个 workflow 写清：目标、输入、步骤、产出、完成标准。
每个 agent toml 要有 name/description/developer_instructions，并写明禁止事项。
完成后给我“如何调用这 4 个 agent”的提示词示例。
```

### 步骤 1.3 你要检查的点
- [ ] `.codex/agents/` 下 4 个 toml  
- [ ] `config.toml` 里有 4 段 `[agents.xxx]`  
- [ ] 5 个 workflow 文件都有“完成标准”  
- [ ] review-agent 说明里有“默认不直接大改代码”

### 多 agent 合作在这一步先“定义”，下一步才“开工”

---

## Phase 2：需求阶段（只让 pm-agent 干活）

### 步骤 2.1 对 AI 说

```text
请使用 pm-agent，按 workflows/01-discovery.md 和我完成本项目需求文档。
项目只有 2 个需求：A 专注信息展示，B 进度状态展示。
请把结果写入 docs/requirements.md 和 docs/acceptance.md。
本阶段不要写 site 业务代码。
如果有不明确点，先列假设，再继续。
```

### 步骤 2.2 你亲自确认（很重要）
打开 `docs/requirements.md`，确认：
- 需求 A/B 是否足够具体  
- 验收标准是否可勾选  
- 有没有偷偷扩大范围

### 步骤 2.3 完成标准
- [ ] 需求文档可指导开发  
- [ ] 验收清单可手工勾选  
- [ ] 仍无业务代码（或仅有占位也可）

---

## Phase 3：开发阶段（dev-agent 单线实现）

### 步骤 3.1 先做需求 A

```text
请使用 dev-agent，按 workflows/02-dev.md 只实现需求 A。
输入文档：docs/requirements.md
输出到 site/ 目录。
实现后告诉我如何本地预览。
不要提前做需求 B 的复杂逻辑。
```

### 步骤 3.2 你本地预览
浏览器打开 `site/index.html`，确认需求 A 可见。

### 步骤 3.3 再做需求 B

```text
需求 A 我已预览通过。
请继续用 dev-agent 实现需求 B（进度状态）。
要求：
- 进度与状态文案可见
- 任务完成数变化时，更新时间会变（可用按钮模拟完成/重置）
- 保持最小实现
完成后给手动测试步骤。
```

### 步骤 3.4 完成标准
- [ ] A/B 都可在页面看到  
- [ ] 有基础交互（至少能模拟进度变化）  
- [ ] README 有预览方法  

---

## Phase 4：审查 + 修 bug（学会 subagent 接力/并行）

### 步骤 4.1 先审查（只读）

```text
请使用 review-agent，按 workflows/03-review.md 做只读审查。
重点：
1) 需求 A/B 是否都实现
2) 明显 UI/逻辑问题
3) 代码结构风险
4) 测试缺口
输出到 docs/review-report.md
按 P0/P1/P2 分级，先不要改代码。
```

### 步骤 4.2 再修 bug

```text
请使用 bugfix-agent，按 workflows/04-bugfix.md
只修复 docs/review-report.md 中的 P0 和 P1。
每个修复后说明如何验证。
不要处理 P2，不要重构无关代码。
```

### 步骤 4.3 复审

```text
请再次使用 review-agent，复审 bugfix 结果。
更新 docs/review-report.md：哪些已关闭，哪些仍开放。
```

### 步骤 4.4 一次并行合作练习（必做）

```text
请并行启动两个 subagent：
1) review-agent：检查当前页面是否满足 acceptance.md
2) pm-agent：把实际测试步骤补进 docs/acceptance.md（不改代码）
等待两者完成后，主对话给我汇总表：通过项/失败项/下一步。
```

### 完成标准
- [ ] 有审查报告  
- [ ] P0/P1 已处理或有明确原因  
- [ ] 你亲手完成过一次“并行 subagent”

---

## Phase 5：把重复步骤合并成 Skill

### 什么时候做（判断标准）
当你第 2 次需要做这类事时，就该沉淀 skill：

- 修改今日目标  
- 修改 3 条任务  
- 改进度并更新时间戳  

### 步骤 5.1 创建 skill

```text
请创建一个 skill：update-focus-board
路径：.agents/skills/update-focus-board/SKILL.md

技能目标：
根据输入更新 Focus Board 的目标、任务、完成状态，并更新时间。
明确输入参数、修改哪些文件、禁止事项（不改无关样式/不改项目结构）。
然后教我如何调用这个 skill。
```

### 步骤 5.2 用 skill 实操一次

```text
请调用 $update-focus-board：
- 目标：完成 Codex 学习手册
- 任务1：跑通 subagent 合作（完成）
- 任务2：沉淀 skill（进行中）
- 任务3：验证 hooks 与 MCP（未开始）
更新页面数据和时间戳，最后给我预览确认点。
```

### 完成标准
- [ ] skill 文件存在  
- [ ] 成功调用一次并看到页面变化  
- [ ] 你能说清：为什么它是 skill 而不是 agent  

---

## Phase 6：钩子（Hooks）使用

### 学习目标
理解 hooks = 到点自动执行的机械动作（不靠模型“想起”）。

### 步骤 6.1 启用 hooks（概念最小集）
在 `.codex/config.toml` 增加（若你的 Codex 版本支持）：

```toml
[features]
codex_hooks = true
```

并准备 `.codex/hooks.json`（可先只挂 2 个事件）：
- `SessionStart`
- `UserPromptSubmit`

> 若 Windows 上 hooks 不稳定，也要完成“配置 + 尝试验证 + 记录结果”。  
> 学会“如何配置/如何判断有没有触发”比一定听到声音更重要。

### 步骤 6.2 对 AI 说

```text
请帮我在本项目配置最小 hooks：
1) 编写 .codex/hooks.json
2) 提供一个最简单的 hook 脚本（例如写日志到 .codex/hooks/logs/hook-events.log）
3) 告诉我如何开启 features.codex_hooks
4) 给我一个验证步骤：我做什么操作，应在日志里看到什么
不要影响主业务流程。
```

### 步骤 6.3 你亲自验证
1. 重启 Codex 会话  
2. 发送一条提示词  
3. 看日志是否新增事件  
4. 在 `docs/learning-notes.md` 记录：触发了哪些事件

### 完成标准
- [ ] hooks 配置文件在  
- [ ] 你知道如何启用  
- [ ] 至少验证 1 次（成功或失败都要写笔记）

---

## Phase 7：MCP 使用

### 学习目标
让 Codex 通过 MCP 使用外部能力（本项目建议用文档类 MCP）。

### 推荐练习（低风险）
使用 `context7`（查库文档）或你已可用的文档 MCP。  
若 context7 不方便，可用“只读网页文档查询”类 MCP，原则是：真实调用一次即可。

### 步骤 7.1 配置 MCP
对 AI 说：

```text
请在 .codex/config.toml 配置一个 MCP（优先 context7）：
- enabled = true
- 给出可用最小配置
- 说明如何验证 MCP 已加载
不要配置需要付费密钥的复杂服务。
```

### 步骤 7.2 真实调用一次
对 AI 说：

```text
请通过 MCP 查询一个与前端静态页相关的文档问题：
“纯静态页面如何组织 JS 与 JSON 数据才更清晰？”
把结论简写到 docs/mcp-learning.md，并说明你调用了哪个 MCP 工具。
然后判断我们当前 site/ 结构是否需要微调（先给建议，未经我同意不改代码）。
```

### 完成标准
- [ ] `config.toml` 中有 MCP 配置  
- [ ] 有一次实际调用记录（`docs/mcp-learning.md`）  
- [ ] 你能说清 MCP 和 skill 的区别  

---

## Phase 8：收尾发布（框架完成，不必复杂上线）

### 本阶段最低目标
- 项目可本地预览  
- 文档齐全  
- 学习目标全部勾选  

### 可选加分
部署到 GitHub Pages（非必须）。

### 步骤 8.1 对 AI 说

```text
请按 workflows/05-release.md 做发布前检查：
1) 需求 A/B 是否完成
2) agent/skill/hooks/mcp 学习项是否都有对应文件
3) README 是否包含：预览方式、agent 用法、skill 用法
4) 输出 docs/final-checklist.md
先检查，经我确认后再做任何收尾修改。
```

### 步骤 8.2 你做最终复盘（手写也行）
在 `docs/retro.md` 回答：

1. 4 个 agent 里哪个最有用？  
2. 哪一步最容易失控？  
3. 如果重来，skill 会更早还是更晚抽？  
4. hooks 和 MCP 各解决了什么问题？  
5. 下个项目你准备复用哪套框架？

---

# 6. 关键提示词清单（可反复复制）

## 调 pm-agent
```text
使用 pm-agent，只更新文档，不写业务代码。输入：... 输出到 docs/...
```

## 调 dev-agent
```text
使用 dev-agent，按 docs/requirements.md 实现 ...，小步提交，完成后给预览与测试步骤。
```

## 调 review-agent
```text
使用 review-agent 只读审查，输出 P0/P1/P2 到 docs/review-report.md，不要直接改代码。
```

## 调 bugfix-agent
```text
使用 bugfix-agent 只修 docs/review-report.md 的 P0/P1，最小改动，并给验证方式。
```

## 并行合作
```text
并行启动 review-agent 与 pm-agent：一个审查实现，一个补验收文档；完成后主对话汇总，先不改代码。
```

## 调 skill
```text
调用 $update-focus-board，按以下数据更新页面：...
```

---

# 7. 常见卡点与处理

### 1) AI 一上来就写大段代码
回正：
```text
停止实现。先回到当前 phase 的目标，只产出该阶段文件。
```

### 2) agent 互相抢活
回正：
```text
严格按角色：pm 不写代码，review 不直接大改，dev 不改需求范围。
```

### 3) 你不知道现在该用谁
看 phase：
- 需求不清 → pm-agent  
- 要功能 → dev-agent  
- 要找问题 → review-agent  
- 要修问题 → bugfix-agent  
- 重复劳动 → skill  
- 事件自动动作 → hooks  
- 外部工具能力 → MCP  

### 4) 想加第 3 个需求
先拒绝。本项目的学习价值来自“完整闭环”，不是功能堆叠。

---

# 8. 最终验收表（全部勾完才算学会）

## 项目功能
- [ ] 需求 A 完成  
- [ ] 需求 B 完成  
- [ ] 本地可预览  

## Codex 框架
- [ ] config.toml  
- [ ] AGENTS.md  
- [ ] 4 个 subagent  
- [ ] 5 个 workflow  
- [ ] 1 个 skill  

## 协作能力
- [ ] 完成过 agent 接力（pm→dev→review→bugfix）  
- [ ] 完成过一次并行 subagent  

## 扩展能力
- [ ] hooks 配置并验证  
- [ ] MCP 配置并真实调用  

## 认知沉淀
- [ ] 能用自己的话解释：地图/制度/员工/技能/工单/钩子/MCP  
- [ ] 有 `docs/retro.md` 复盘  

---

# 9. 一句话记忆法

- `AGENTS.md`：地图  
- `config.toml`：制度  
- `agents/`：员工  
- `skills/`：技能  
- `workflows/`：工单怎么流转  
- `hooks`：到点自动执行的机关  
- `MCP`：外接工具电源  
- 多 agent 合作：先接力，后并行；先文档，后代码；先审查，后修改  

---

# 10. 你现在立刻开始的第一句

打开空目录 `focus-board`，对 Codex 发送：

```text
按学习手册 Phase 0 执行：只初始化 Focus Board 项目框架与 AGENTS.md/config.toml，不实现业务功能。
```

然后严格按 Phase 0 → Phase 8 推进。  
每完成一个 Phase，先自己勾清单，再进入下一阶段。
