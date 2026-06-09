# 10. Steering Loop 实操：问题驱动的 Harness 迭代

> 核心思路：**问题重复出现 → 改进 Harness → 不再出现**。
>
> 以下 4 个真实案例演示 Steering Loop 如何运转。

## 10.1 案例 1：Redis Key 格式不一致

```
问题：Agent-B 反复把 Redis key 写成 article:1 或 blog_article_view_1，
      而不是规定的 blog:article:view:1

Steering Loop 过程：
Step 1: 发现问题重复出现（第三次）
Step 2: 分析根因 → CLAUDE.md 中写了规则但不够醒目
Step 3: 改进 Feedforward Guide：
  - 在 CLAUDE.md Redis key 规范处增加 ❌ 错误示例
  - 在 prompts/shared/naming.md 中增加醒目警告
Step 4: 改进 Feedback Sensor：
  - 在 check-naming.sh 中增加 Redis key 格式扫描
Step 5: 更新 Harness 文件 → 所有 Agent 立即生效（共享文件）
Step 6: 验证 → 新的 Agent 不再犯此错误
```

## 10.2 案例 2：Service 层忘记 @Transactional

```
问题：Agent-C 的 CommentServiceImpl 方法没加 @Transactional，
      Hook 只检查了命名，没检查注解

Steering Loop 过程：
Step 1: 在 /check-style 中发现 → 修复
Step 2: 第二次又出现
Step 3: 改进 check-naming.sh → 增加注解检查
Step 4: 问题不再出现
```

## 10.3 案例 3：规格文档没说清楚导致 Agent 卡住

```
问题：Agent-D 做搜索时，规格里说"高亮返回关键词"，但没说返回多长片段

Steering Loop 过程：
Step 1: Agent 在 BLOCKERS.md 中记录阻塞
Step 2: 人类看到 → 补充 specs/impl-t09-search.md
        "高亮片段：取关键词前后各 30 字，最多返回 3 个片段"
Step 3: Agent 继续（不需要重来，只需要补充规格）
Step 4: 问题解决 → 这也是一次 Harness 迭代（改进 Feedforward Guide）
```

## 10.4 案例 4：合并后发现测试覆盖率不达标

```
问题：3 个模块的单元测试覆盖率不达标（Service < 60%）

分析：prompts/agents/backend.md 没说"必须先写测试"

改进：在 prompts/agents/backend.md 实现顺序中明确强调
      "Test 不是最后写的，是和业务代码同步写的"
      在 run-tests Skill 中增加覆盖率检查门槛

验证：后续模块测试覆盖率 > 80%
```

## 10.5 Steering Loop 核心原则

```
                     ┌─────────────────────┐
                     │  问题重复出现         │
                     │  （同一问题 ≥ 2次）   │
                     └──────────┬──────────┘
                                ↓
                 ┌──────────────────────────────┐
                 │ 判断：这个问题能被 Harness      │
                 │ 预防或自动检测吗？              │
                 └─────────────┬────────────────┘
                               ↓
             ┌─────────────────┼─────────────────┐
             ↓                 ↓                 ↓
       可以通过规则        可以通过 Hook      规则和 Hook
       预防？              自动检测？          都不够
             ↓                 ↓                 ↓
       更新 CLAUDE.md     更新 Hook 脚本     这是合理的人工
       或 prompts/        或 Skill 配置      介入点，记录到
             ↓                 ↓            BLOCKERS.md
       问题不再出现 ←─────────┘
             ↓
       Harness 成熟
```

---

