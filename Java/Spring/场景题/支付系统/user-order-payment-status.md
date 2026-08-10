# 用户 · 订单 · 支付（精简版）

---

## 1. 用户 - 订单 - 支付 流程图

```mermaid
sequenceDiagram
    actor 用户
    participant 订单
    participant 支付
    participant 微信支付宝 as 微信/支付宝

    用户->>订单: 下单
    订单->>订单: 订单=待支付
    用户->>订单: 去支付
    订单->>支付: 创建支付单
    支付->>微信支付宝: 预下单
    微信支付宝-->>支付: 收银台参数
    支付-->>订单: 返回支付参数
    订单-->>用户: 打开收银台

    用户->>微信支付宝: 付款

    par 主路径：回调
        微信支付宝-->>支付: 支付结果回调
        支付->>支付: 支付单=成功
        支付-->>订单: 通知支付成功
        订单->>订单: 订单=已支付
    and 辅路径：前端查询
        用户->>订单: 回到商户页查询
        订单->>支付: 查支付结果
        支付->>微信支付宝: 必要时查单
        支付-->>订单: 成功/支付中
        订单-->>用户: 展示结果
    end
```

一句话：

- 订单管「买什么、付没付、能不能发货」
- 支付管「跟微信/支付宝要钱、认结果」
- **只有支付确认成功后，订单才能变已支付**

---

## 2. 支付状态有哪些

支付单只保留这些状态：

| 状态 | 含义 |
|------|------|
| `PAYING` | 支付中（已建单，还没确认收到钱） |
| `SUCCESS` | 支付成功 |
| `CLOSED` | 已关闭（取消 / 超时 / 作废） |
| `REFUNDING` | 退款中 |
| `REFUNDED` | 已全额退款 |

订单（和钱相关）只保留：

| 状态 | 含义 |
|------|------|
| `WAIT_PAY` | 待支付 |
| `PAID` | 已支付 |
| `CLOSED` | 已关闭 |
| `REFUNDING` | 退款中 |
| `REFUNDED` | 已退款 |

对应关系：

```text
支付 SUCCESS  →  订单 PAID
支付 CLOSED   →  订单一般仍 WAIT_PAY（除非整单不要了才 CLOSED）
支付 REFUNDED →  订单 REFUNDED
```

状态只能往前走（成功不能变回支付中；已关闭不能当普通失败乱改，见并发规则）。

```mermaid
stateDiagram-v2
    [*] --> PAYING: 创建支付单
    PAYING --> SUCCESS: 回调或查单确认已付
    PAYING --> CLOSED: 取消/超时且确认未付
    SUCCESS --> REFUNDING: 发起退款
    REFUNDING --> REFUNDED: 退款成功
    SUCCESS --> [*]
    CLOSED --> [*]
    REFUNDED --> [*]
```

---

## 3. 五个业务：状态怎么转（附图）

### 3.1 正常支付成功（回调为主，前端查询为辅）

```mermaid
flowchart TD
    A[订单 WAIT_PAY] --> B[创建支付单 PAYING]
    B --> C[用户在微信/支付宝付款]
    C --> D{谁先确认成功?}
    D -->|回调先到| E[支付单 SUCCESS]
    D -->|前端查询/查单先到| E
    E --> F[通知订单]
    F --> G[订单 PAID]
    E --> H[再次回调或再次查询]
    H --> I[已是 SUCCESS，直接忽略]
```

| 步骤 | 支付单 | 订单 |
|------|--------|------|
| 下单 | - | WAIT_PAY |
| 去支付 | PAYING | WAIT_PAY |
| 确认收款 | SUCCESS | PAID |

---

### 3.2 成功后退款（退款单独走）

```mermaid
flowchart TD
    A[支付 SUCCESS / 订单 PAID] --> B[发起退款]
    B --> C[支付 REFUNDING / 订单 REFUNDING]
    C --> D[微信/支付宝退款成功]
    D --> E[支付 REFUNDED / 订单 REFUNDED]
```

| 步骤 | 支付单 | 订单 |
|------|--------|------|
| 已支付 | SUCCESS | PAID |
| 申请退款 | REFUNDING | REFUNDING |
| 退款完成 | REFUNDED | REFUNDED |

说明：退款不是支付失败，不要回到 `PAYING`，也不要用失败状态表示退款。

---

### 3.3 已付但当时查不到，过几分钟才查到

```mermaid
flowchart TD
    A[用户已付款] --> B[支付单仍是 PAYING]
    B --> C[此时查单：暂无/未明确]
    C --> D[状态不改 仍是 PAYING]
    D --> E[几分钟后回调或再次查单]
    E --> F[确认已付]
    F --> G[支付 SUCCESS → 订单 PAID]
```

| 步骤 | 支付单 | 订单 |
|------|--------|------|
| 已付未确认 | **保持 PAYING** | **保持 WAIT_PAY** |
| 后来确认 | SUCCESS | PAID |

硬规则：**查不到 ≠ 失败，也 ≠ 关闭。** 一直 `PAYING`，确认后再变 `SUCCESS`。

---

### 3.4 用户取消支付

```mermaid
flowchart TD
    A[支付 PAYING / 订单 WAIT_PAY] --> B[用户点取消]
    B --> C{策略}
    C -->|常用| D[支付单仍 PAYING<br/>订单仍 WAIT_PAY<br/>可再次支付]
    C -->|严格| E[支付单 CLOSED<br/>订单仍 WAIT_PAY<br/>再付需新支付单]
    D --> F{之后渠道通知已付?}
    E --> F
    F -->|是| G[支付改 SUCCESS<br/>订单改 PAID<br/>成功优先]
    F -->|否| H[维持取消后的状态<br/>或到期关闭]
```

| 步骤 | 支付单 | 订单 |
|------|--------|------|
| 取消后（常用） | PAYING | WAIT_PAY |
| 取消后（严格） | CLOSED | WAIT_PAY |
| 取消后却已扣款 | SUCCESS | PAID |

硬规则：**成功优先于取消。** 取消一般不关业务订单。

---

### 3.5 没打开支付 / 不付 + 提醒与超时

```mermaid
flowchart TD
    A[支付 PAYING / 订单 WAIT_PAY] --> B[到点发提醒]
    B --> C[状态不变 只发消息]
    C --> D[到达超时时间]
    D --> E[先查微信/支付宝]
    E -->|已支付| F[支付 SUCCESS → 订单 PAID]
    E -->|未支付| G[支付 CLOSED]
    G --> H[订单 CLOSED<br/>或仍 WAIT_PAY 允许重付]
    E -->|查询失败| I[先不关 下次再试]
```

| 步骤 | 支付单 | 订单 |
|------|--------|------|
| 提醒时 | 不变 PAYING | 不变 WAIT_PAY |
| 超时且未付 | CLOSED | CLOSED 或 WAIT_PAY |
| 超时但已付 | SUCCESS | PAID |

硬规则：**提醒不改状态；关单前先查渠道。**

---

### 3.6 五张业务对照

| 业务 | 支付单怎么转 | 订单怎么转 |
|------|--------------|------------|
| 正常成功 | PAYING → SUCCESS | WAIT_PAY → PAID |
| 成功后退款 | SUCCESS → REFUNDING → REFUNDED | PAID → REFUNDING → REFUNDED |
| 晚到账 | 一直 PAYING → 后来 SUCCESS | 一直 WAIT_PAY → 后来 PAID |
| 用户取消 | 保持 PAYING 或 → CLOSED；若已扣款 → SUCCESS | 多保持 WAIT_PAY；已扣款 → PAID |
| 提醒/超时 | 提醒不变；超时未付 → CLOSED；已付 → SUCCESS | 提醒不变；按产品关单或重付 |

---

## 4. 怎么做到幂等

幂等 = **同一件事做很多次，结果和做一次一样**（不重复建单、不重复入账、不来回改状态）。

### 4.1 三类容易重复的请求

| 类型 | 典型情况 |
|------|----------|
| 重复请求支付 | 用户连点、前端重试、网络重发「创建支付」 |
| 重复回调 | 微信/支付宝同一笔成功通知发多次 |
| 回调 + 定时查单同时改状态 | 一边回调成功，一边补偿任务也查到成功 |

### 4.2 做法（够用就这几条）

**（1）创建支付：用业务唯一键防重复建单**

```text
唯一键示例：order_no + 支付次数
或：商户支付单号 payment_no 全局唯一
```

- 同一个「进行中」的订单，已有 `PAYING` 支付单 → **直接返回旧单**，不新建。
- 数据库对唯一键加 **唯一索引**，插入冲突就查旧单返回。

**（2）支付成功：只允许成功一次**

```text
更新条件：
WHERE payment_no = ? AND status = 'PAYING'
SET status = 'SUCCESS', ...
```

- 影响行数 = 1 → 第一次成功，再通知订单。
- 影响行数 = 0 → 已经是成功/关闭，**不再通知、不重复入账**。

**（3）回调、前端查询、定时任务：走同一条「置成功」函数**

```text
handlePaySuccess(payment_no):
  1. 加锁或用上面那条条件更新
  2. 更新成功 → 发「支付成功」给订单（事件也要幂等）
  3. 更新失败（已终态）→ 直接返回 OK
```

三条通路都调用它，就不会一个改成功、一个又改成别的。

**（4）通知订单也要幂等**

```text
订单更新：
WHERE order_no = ? AND status = 'WAIT_PAY'
SET status = 'PAID'
```

或订单表记录 `pay_success_event_id` / `payment_no`，同一支付单只处理一次。

**（5）回调响应**

- 业务已处理过：仍返回成功给微信/支付宝，避免对方不停重试。
- 但内部不能再入账第二次。

```mermaid
flowchart TD
    A[回调 / 查单 / 定时任务] --> B[同一方法 handlePaySuccess]
    B --> C{条件更新 PAYING→SUCCESS}
    C -->|更新到 1 行| D[通知订单 PAID]
    C -->|更新到 0 行| E[已处理过 直接返回成功]
    D --> F[订单条件更新 WAIT_PAY→PAID]
    F --> G[第二次通知订单时不再改]
```

---

## 5. 怎么做到并发安全

并发 = **同一时刻多条链路一起改同一笔支付单/订单**。

### 5.1 常见并发场景

| 场景 | 谁在打架 |
|------|----------|
| 用户连点支付 | 两个「创建支付」 |
| 回调 + 定时任务 | 两个「置成功」 |
| 取消/超时关单 + 支付成功 | 一个要 CLOSED，一个要 SUCCESS |
| 前端轮询 + 回调 | 多次读、多次写成功 |

### 5.2 做法

**（1）数据库条件更新 = 最核心**

不要先查状态再改（会抢跑）。直接：

```sql
-- 置成功
UPDATE payment SET status='SUCCESS', paid_at=NOW()
WHERE payment_no=? AND status='PAYING';

-- 关单
UPDATE payment SET status='CLOSED', closed_at=NOW()
WHERE payment_no=? AND status='PAYING';
```

谁先执行成功谁赢；另一个影响 0 行，自动退出。

**（2）成功优先于关闭**

若业务要求「先关单、后成功回调仍要认款」：

```sql
-- 允许从 PAYING 或 CLOSED 进入 SUCCESS（仅此放宽）
UPDATE payment SET status='SUCCESS', ...
WHERE payment_no=? AND status IN ('PAYING', 'CLOSED')
  AND status <> 'SUCCESS';
```

同时打日志/告警：「关闭后又成功」。  
更稳妥的产品策略：**超时关单前先查渠道，未付再关**，减少这种对打。

**（3）创建支付加锁或唯一约束**

- 按 `order_no` 加分布式锁 / 数据库行锁，锁内判断是否已有 `PAYING` 单。  
- 或依赖唯一索引，插入失败则返回已有单。  

两者选一种即可，线上两者都有。

**（4）订单入账同样条件更新**

```sql
UPDATE orders SET status='PAID', ...
WHERE order_no=? AND status='WAIT_PAY';
```

防止支付成功通知发两次导致异常副作用（积分发两次等业务，也要用同一支付单号做唯一键）。

**（5）允许的状态流转表（实现时写死校验）**

| 当前状态 | 可以变成 | 不可以变成 |
|----------|----------|------------|
| PAYING | SUCCESS, CLOSED | REFUNDED |
| SUCCESS | REFUNDING | PAYING, CLOSED |
| CLOSED | SUCCESS（仅「关单后发现已付」时可选） | PAYING |
| REFUNDING | REFUNDED | PAYING |
| REFUNDED | 无 | 任意回退 |

非法流转直接拒绝并打日志。

```mermaid
flowchart TD
    A[并发请求进入] --> B[带状态条件的 UPDATE]
    B --> C{影响行数}
    C -->|1| D[本次生效 继续后续通知]
    C -->|0| E[别人已改走 本次退出]
    D --> F[后续动作也用唯一键/条件更新]
```

---

## 6. 总览（一页记住）

```mermaid
flowchart LR
    U[用户] --> O[订单 WAIT_PAY]
    O --> P[支付 PAYING]
    P --> C[微信/支付宝]
    C -->|确认收款| S[支付 SUCCESS]
    S --> PAID[订单 PAID]
    P -->|取消或超时且未付| X[支付 CLOSED]
    PAID --> R[退款 REFUNDING → REFUNDED]
```

| 问题 | 答案 |
|------|------|
| 流程 | 用户 → 订单 → 支付 → 微信/支付宝 → 回调/查单回写 |
| 支付状态 | PAYING / SUCCESS / CLOSED / REFUNDING / REFUNDED |
| 五个业务怎么转 | 见第 3 节图 |
| 幂等 | 唯一键建单 + 条件更新置成功 + 三条通路共用一个成功处理 + 订单侧也条件更新 |
| 并发 | 用 `WHERE status=PAYING` 更新抢占；成功优先；创建支付加锁/唯一索引；非法流转拒绝 |

---

## 文档信息

| 版本 | 说明 |
|------|------|
| 2.0 | 按 5 问精简：总流程、状态、五业务扭转、幂等、并发 |
