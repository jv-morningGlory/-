# 8. Step 9~10：Phase 3 增强功能开发

> Phase 3 的 Agent 直接复用 Phase 1 搭建的 Harness——不需要重新搭建，只需要新的 worktree + 新的实现规格。

## 8.1 前置操作：合并 Phase 2 产出

```bash
cd /path/blog-system

# 依次合并 Phase 2 的 3 个分支
git merge feature/auth-category   # Agent-A
git merge feature/article-tag     # Agent-B
git merge feature/comment         # Agent-C

# 处理可能的冲突（通常在 blog-common 或共用 DTO）
git commit -m "merge: Phase 2 核心功能完成"

# 创建 Phase 3 的 worktree
git worktree add .claude/worktrees/agent-d feature/search
git worktree add .claude/worktrees/agent-e feature/statistics-hot
```

## 8.2 Step 9：Agent-D — 全文搜索模块（T09）

```bash
cd .claude/worktrees/agent-d
claude
```

```
加载 prompts/impl/t09-search.md 并执行。

实现 Elasticsearch 全文检索：
1. ElasticsearchConfig — ES 连接配置
2. ArticleDocument — ES 文档对象
3. 文章同步 — 发布/更新时同步到 ES
4. SearchService — 关键词搜索 + 分类筛选 + 高亮 + 分页
5. SearchController — GET /api/search
6. 搜索历史 — Redis List，最近10条，去重

依赖：已合并的 T07（文章数据）
```

## 8.3 Step 10：Agent-E — 统计模块（T10 + T11）

```bash
cd .claude/worktrees/agent-e
claude
```

```
加载 prompts/impl/t10-statistics.md 和 t11-hot-rank.md 并执行。

T10 — 浏览量统计：
1. Redis 计数器：blog:article:view:{articleId}
2. 每次详情接口调用 → Redis INCR
3. 定时任务 @Scheduled(每5分钟)：批量读取 Redis 计数 → UPDATE article.view_count

T11 — 热门排行：
1. 热门文章 TOP 10：从 Redis Sorted Set 取
2. 标签统计：SELECT tag_id, COUNT(*) FROM article_tag GROUP BY tag_id
3. 仪表盘接口：/api/statistics/dashboard
```

> **Phase 3 复用 Phase 1 Harness 的验证：** Agent-D 和 Agent-E 启动后会自动读取 CLAUDE.md、遵循 Hook 约束、使用 Skills——完全不需要重新配置。

---

