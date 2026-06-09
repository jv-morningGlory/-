# 9. Step 11~14：Phase 4 测试与集成

> 所有功能模块开发完成，进入验证阶段。这里是 Harness 的 **Feedback 集中生效**阶段。

## 9.1 Step 11：合并所有 Worktree

```bash
cd /path/blog-system

# 合并 Phase 3
git merge feature/search
git merge feature/statistics-hot

# 处理合并冲突（重点关注：pom.xml 依赖、application.yml 配置、Entity 字段变更）
git add -A && git commit -m "merge: Phase 3 增强功能完成"

# 清理不再需要的 worktree
git worktree remove .claude/worktrees/agent-a
git worktree remove .claude/worktrees/agent-b
git worktree remove .claude/worktrees/agent-c
git worktree remove .claude/worktrees/agent-d
git worktree remove .claude/worktrees/agent-e
```

**合并时的常见冲突及处理：**

| 冲突类型 | 表现 | 处理方式 |
|---------|------|---------|
| pom.xml 依赖冲突 | 两个 Agent 加了不同的依赖版本 | 手动统一版本号 |
| blog-common Entity 冲突 | Agent-A 改了 User，Agent-B 也改了 | 对比差异，合并字段 |
| application.yml 冲突 | 各自加了不同配置项 | 按模块分段合并，检查 key 不重复 |
| Mapper XML 冲突 | 不同 Agent 改了同一个 XML | 手动合并 |

## 9.2 Step 12：全局规范检查（T12）

> **Harness 类型：** Maintainability — **Feedback Sensor**

```bash
# 使用 check-style Skill
claude "/check-style"

# 使用 security-check Skill
claude "/security-check"

# 或执行完整检查
claude "
执行全局质量检查：
1. 读取 CLAUDE.md 命名规范，扫描所有 Java 文件，输出违规清单
2. 编码规范检查：Mapper 注解 SQL、Controller 业务逻辑、@Transactional、System.out.println
3. 安全检查：密码 BCrypt、JWT 硬编码、SQL 注入、XSS 防护
4. 输出违规清单（文件 + 行号 + 问题 + 修复建议）
"
```

**预期输出示例：**

```
全局规范检查报告
═══════════════════════════════════════════

命名检查：❌ 3 个问题
| # | 文件 | 问题 |
|---|------|------|
| 1 | BlogManager.java | 非法类名，应为 ArticleService 或 CategoryService |
| 2 | article_controller.java | 文件名应大驼峰: ArticleController.java |
| 3 | TagReq.java | DTO 应命名为 TagCreateRequest |

编码规范检查：❌ 2 个问题
| # | 文件 | 行号 | 问题 |
|---|------|------|------|
| 1 | ArticleController.java | 45 | Controller 中包含业务逻辑（直接操作 Mapper） |
| 2 | CommentServiceImpl.java | 78 | System.out.println（应用 log.info） |

安全检查：✅ 通过
架构检查：✅ 通过

总计：5 个问题需修复
```

> **发现违规怎么处理？**
> 1. 简单的（命名、格式）→ 直接让 Agent 修复
> 2. 复杂的（架构问题）→ 先分析根因，可能是 CLAUDE.md 或 Hook 有漏洞
> 3. 同类型问题 > 3 个 → 说明 Guide 不够明确或 Sensor 不够强 → 改进 Harness（Steering Loop）

## 9.3 Step 13：集成测试（T13）

> **Harness 类型：** Behaviour — **Feedback Sensor**

```bash
# 使用 run-tests Skill
claude "/run-tests"

# 或执行详细集成测试
claude "
执行完整集成测试：

## 环境准备
1. 使用 H2 内存数据库（测试环境）+ Embedded Redis
2. 执行 DDL（specs/db-schema.md）
3. 插入测试数据

## 测试执行（按依赖顺序）
Phase 1: 认证模块
  □ POST /api/auth/login — 正确凭据 → 200 + Token
  □ POST /api/auth/login — 错误密码 → 401
  □ POST /api/auth/refresh — 有效 refresh_token → 200
  □ POST /api/auth/refresh — 过期 Token → 401

Phase 2: 分类 + 标签 + 文章
  □ POST /api/categories → 200（含认证 Header）
  □ GET /api/categories → 200 + 树形结构
  □ POST /api/tags → 200
  □ POST /api/articles → 200 + 草稿
  □ PUT /api/articles/{id} → 更新为 PUBLISHED
  □ GET /api/articles → 200 + 分页 + 筛选
  □ GET /api/articles/{slug} → 200 + Markdown 内容 + 上一篇/下一篇

Phase 3: 评论
  □ POST /api/articles/{id}/comments — 游客 → 200 + PENDING
  □ POST /api/articles/{id}/comments — 回复评论 → 200
  □ GET /api/articles/{id}/comments → 200 + 树形结构
  □ PUT /api/admin/comments/{id}/audit → APPROVED → 200

Phase 4: 搜索 + 统计
  □ GET /api/search?keyword=Spring → 200 + 高亮
  □ GET /api/statistics/dashboard → 200 + 仪表盘数据
  □ GET /api/statistics/articles/hot → 200 + TOP 10

## 边界测试
  □ 分页越界（page=-1） → 400
  □ 超大 page size（size=9999） → 使用默认最大值
  □ 不存在的资源 → 404
  □ SQL 注入尝试 → 正常处理

## 输出
- 测试通过: X / 总数 Y
- 失败项: 列出 + 根因分析 + 修复建议
"
```

## 9.4 Step 14：全链路 API 验证脚本（T14）

```bash
# 生成 curl 测试脚本
claude "
根据 specs/api-spec.md 生成完整的 API 测试脚本。

输出文件：tests/api-tests.sh（可执行 curl 脚本）
要求：
1. 覆盖所有接口的正常流和至少一种异常流
2. 使用变量存储 token 并在后续请求中复用
3. 每个请求输出期望结果 vs 实际结果
4. 最后输出汇总：通过/失败数量
"
```

**生成的 `tests/api-tests.sh` 示例片段：**

```bash
#!/bin/bash
BASE_URL="http://localhost:8080/api"
PASS=0; FAIL=0

check() {
    local desc="$1" expected="$2" actual="$3"
    if echo "$actual" | grep -q "$expected"; then
        echo "✅ $desc"
        PASS=$((PASS + 1))
    else
        echo "❌ $desc (期望包含: $expected)"
        echo "   实际: $actual"
        FAIL=$((FAIL + 1))
    fi
}

echo "========== 1. 认证模块 =========="

# 登录
RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}')
TOKEN=$(echo "$RESP" | jq -r '.data.accessToken')
check "登录成功" '"code":200' "$RESP"

# 错误密码
RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrong"}')
check "错误密码返回401" '40100' "$RESP"

# ... 更多测试 ...

echo "========== 汇总 =========="
echo "通过: $PASS, 失败: $FAIL"
```

---

