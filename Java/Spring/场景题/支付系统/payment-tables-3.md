# 支付库表设计（三表版）

> 范围：只保留 **支付单、退款单、通知日志**。  
> 不建订单表；业务侧用 `biz_no`（业务单号）关联即可。  
> 字段原则：只留支付域必需字段；用户、商品文案、业务回调地址、收银台参数等不进主表。  
> 对齐：《用户 · 订单 · 支付》《支付 · 部分退款》中的**支付侧**逻辑。

---

## 总览

| 表名 | 作用 |
|------|------|
| `pay_payment` | 一笔收款：预下单、支付中、成功/关闭、累计已退 |
| `pay_refund` | 一次退款：部分退/全额退、退款单号幂等 |
| `pay_notify_log` | 渠道入站回调原文与处理结果（支付+退款都可记） |

关系：

```text
pay_payment 1 ── N pay_refund
pay_payment 1 ── N pay_notify_log   （payment_no 可空：未解析出单号时）
pay_refund  1 ── N pay_notify_log   （可选关联 refund_no）
```

---

## 1. 支付单表 `pay_payment`

### 1.1 字段

| 字段 | 类型（建议） | 必填 | 说明 |
|------|--------------|------|------|
| `id` | BIGINT | 是 | 主键，自增 |
| `payment_no` | VARCHAR(64) | 是 | 商户支付单号，全局唯一 |
| `biz_no` | VARCHAR(64) | 是 | 业务单号（上游传入，支付不解释业务含义） |
| `channel` | VARCHAR(16) | 是 | WECHAT / ALIPAY |
| `pay_amount` | BIGINT | 是 | 支付金额，**单位：分**；币种默认 CNY，不单独建字段 |
| `status` | VARCHAR(16) | 是 | PAYING / SUCCESS / CLOSED / REFUNDED |
| `refunded_amount` | BIGINT | 是 | 已成功退款累计，分；默认 0 |
| `refunding_amount` | BIGINT | 是 | 退款中占用金额，分；默认 0 |
| `channel_trade_no` | VARCHAR(64) | 否 | 渠道交易号（微信 transaction_id / 支付宝 trade_no） |
| `prepay_id` | VARCHAR(128) | 否 | 预下单凭证（同单续付/排查用；不需要可去掉） |
| `expire_at` | DATETIME | 是 | 支付过期时间（超时关单、传渠道 time_expire） |
| `paid_at` | DATETIME | 否 | 支付成功时间 |
| `version` | INT | 是 | 乐观锁版本；读改写时 `WHERE version=?`，成功则 `version+1` |
| `created_at` | DATETIME | 是 | 创建时间 |
| `updated_at` | DATETIME | 是 | 更新时间 |
| `extra` | JSON / TEXT | 否 | 扩展（如 subject、user_id 等非核心信息） |

### 1.2 刻意不进主表的字段

| 不建字段 | 原因 |
|----------|------|
| `user_id` | 用户归属属上游；排查放 `extra` |
| `biz_type` | 单业务方可省略；多业务再加或放 `extra` |
| `currency` | 只做人民币时写死 CNY |
| `subject` | 商品文案，调渠道时临时传入或放 `extra` |
| `client_pay_param` | 收银台参数是短暂返回值，不落库；需重拉再调渠道 |
| `notify_url` | 内部支付中心用固定 MQ/配置通知上游 |
| `closed_at` / `refunded_at` | 用 `status` + `updated_at` 即可 |

### 1.3 状态取值

| status | 含义 |
|--------|------|
| `PAYING` | 已建单，未确认收款 |
| `SUCCESS` | 已收款，且 **未全额退完**（含从未退过、部分已退） |
| `CLOSED` | 取消/超时关闭（确认未付） |
| `REFUNDED` | 已全额退完（`refunded_amount = pay_amount`） |

> 支付单主状态**不设** `REFUNDING`；是否在退看 `pay_refund`。

### 1.4 索引与约束

| 名 | 类型 | 列 | 说明 |
|----|------|----|------|
| `pk` | PRIMARY | `id` | |
| `uk_payment_no` | UNIQUE | `payment_no` | 支付单号唯一 |
| `uk_channel_trade_no` | UNIQUE | `channel_trade_no` | 渠道单号唯一（允许 NULL；MySQL 下多 NULL 一般不冲突） |
| `idx_biz_no` | INDEX | `biz_no`, `status` | 按业务单查支付单 |
| `idx_status_expire` | INDEX | `status`, `expire_at` | 扫支付中超时关单 |

### 1.5 金额不变量

```text
pay_amount > 0
refunded_amount >= 0
refunding_amount >= 0
refunded_amount + refunding_amount <= pay_amount

SUCCESS 时通常 refunded_amount < pay_amount（或 =0）
REFUNDED 时 refunded_amount = pay_amount 且 refunding_amount = 0
```

### 1.6 `version` 怎么用

支付里有两层并发控制，别混：

| 手段 | 典型条件 | 管什么 |
|------|----------|--------|
| **状态/金额 CAS** | `status='PAYING'`、可退余额 `>= amt` | 业务规则本身：只能成功一次、不能超退 |
| **乐观锁 version** | `AND version = :oldVersion` | 读改写防丢更新：你读到的整行没被别人改过 |

约定：

```text
1. 先 SELECT 出当前行（含 version）
2. 内存里算好要改的字段
3. UPDATE ... WHERE 业务条件 AND version = :oldVersion
   SET ..., version = version + 1
4. 影响行数 = 0 → 被人抢先改了：重新读、重算，或按业务直接返回（如已是 SUCCESS）
5. 只做 version+1、WHERE 不带 version = 旧值 → 等于没上乐观锁
```

> 纯「状态机抢占」（如 `PAYING→SUCCESS`）往往只靠 `status` 条件就够。  
> **占退款额度、累加已退**这类改金额字段，建议 **业务条件 + version 一起带**，避免：A 读到余额 100、B 先占了 80、A 仍按旧 version 外的逻辑写坏；若 SQL 里余额条件已是原子表达式，version 是双保险 + 方便应用层重试。

### 1.7 关键更新示例（思路）

```sql
-- 支付成功（只一次）：状态 CAS 为主；带上 version 防读改写夹带脏数据
UPDATE pay_payment
SET status = 'SUCCESS',
    channel_trade_no = ?,
    paid_at = NOW(),
    updated_at = NOW(),
    version = version + 1
WHERE payment_no = ?
  AND status = 'PAYING'
  AND version = ?;          -- 传入 SELECT 时读到的 version

-- 发起退款前占额度：余额条件 + version
UPDATE pay_payment
SET refunding_amount = refunding_amount + ?,
    updated_at = NOW(),
    version = version + 1
WHERE payment_no = ?
  AND status = 'SUCCESS'
  AND pay_amount - refunded_amount - refunding_amount >= ?
  AND version = ?;

-- 关单（未付；超时任务先按 expire_at 扫 PAYING，再查渠道确认未付）
UPDATE pay_payment
SET status = 'CLOSED',
    updated_at = NOW(),
    version = version + 1
WHERE payment_no = ?
  AND status = 'PAYING'
  AND version = ?;
```

影响行数判断：

```text
rows = 1 → 本次生效，继续调渠道 / 发 MQ
rows = 0 → 查当前状态：
  - 已是目标终态（如已 SUCCESS）→ 当幂等成功
  - 仍是旧业务态但 version 变了 → 乐观锁冲突，重读重试
  - 状态已不允许（如已 CLOSED 还想占退款）→ 业务拒绝
```

---

## 2. 退款单表 `pay_refund`

### 2.1 字段

| 字段 | 类型（建议） | 必填 | 说明 |
|------|--------------|------|------|
| `id` | BIGINT | 是 | 主键 |
| `refund_no` | VARCHAR(64) | 是 | 商户退款单号，全局唯一（幂等键） |
| `payment_no` | VARCHAR(64) | 是 | 原支付单号 |
| `refund_amount` | BIGINT | 是 | **本次**退款金额，分 |
| `status` | VARCHAR(16) | 是 | REFUNDING / SUCCESS / FAILED |
| `channel_refund_no` | VARCHAR(64) | 否 | 渠道退款单号 |
| `success_at` | DATETIME | 否 | 退款成功时间 |
| `fail_code` | VARCHAR(64) | 否 | 失败码 |
| `fail_msg` | VARCHAR(256) | 否 | 失败描述 |
| `version` | INT | 是 | 乐观锁；用法同支付单：`WHERE version=?` 再 `+1` |
| `created_at` | DATETIME | 是 | |
| `updated_at` | DATETIME | 是 | |

> `biz_no` / `channel` / 原 `pay_amount` / 原 `channel_trade_no`：需要时 **join `pay_payment`**，不在退款单冗余拷贝。  
> 调渠道退款时用到的原渠道单号、渠道类型，从支付单查出即可。

### 2.2 刻意不进主表的字段

| 不建字段 | 原因 |
|----------|------|
| `biz_no` / `channel` / `pay_amount` / `channel_trade_no` | 均可经 `payment_no` join 支付单 |
| `reason` | 退款原因属业务/售后；放上游或 `extra`（若以后加扩展列） |
| `notify_url` | 同支付单，固定 MQ/配置通知 |
| `failed_at` | 失败看 `status` + `fail_*` + `updated_at` |

### 2.3 状态取值

| status | 含义 |
|--------|------|
| `REFUNDING` | 已受理/处理中 |
| `SUCCESS` | 本次退款成功（以渠道确认为准） |
| `FAILED` | 本次明确失败（已释放支付单占用额度） |

### 2.4 索引与约束

| 名 | 类型 | 列 | 说明 |
|----|------|----|------|
| `pk` | PRIMARY | `id` | |
| `uk_refund_no` | UNIQUE | `refund_no` | **幂等核心** |
| `uk_channel_refund_no` | UNIQUE | `channel_refund_no` | 渠道退款号，可空 |
| `idx_payment_no` | INDEX | `payment_no`, `status` | 一笔支付下所有退款 |
| `idx_status_created` | INDEX | `status`, `created_at` | 扫处理中退款、补偿查单 |

### 2.5 关键更新示例（思路）

同一事务内建议顺序：先抢退款单终态，再改支付单金额（两行都带各自的 `version`）。

```sql
-- ① 退款成功只入账一次（退款单）
UPDATE pay_refund
SET status = 'SUCCESS',
    channel_refund_no = ?,
    success_at = NOW(),
    updated_at = NOW(),
    version = version + 1
WHERE refund_no = ?
  AND status = 'REFUNDING'
  AND version = ?;          -- SELECT 退款单时的 version

-- ② 同上事务：累加支付单已退（业务条件 + version）
UPDATE pay_payment
SET refunded_amount = refunded_amount + :amt,
    refunding_amount = refunding_amount - :amt,
    status = CASE
      WHEN refunded_amount + :amt >= pay_amount THEN 'REFUNDED'
      ELSE status
    END,
    updated_at = NOW(),
    version = version + 1
WHERE payment_no = :payNo
  AND status = 'SUCCESS'
  AND refunding_amount >= :amt
  AND refunded_amount + :amt <= pay_amount
  AND version = :payVersion;  -- SELECT 支付单时的 version
```

> ① 影响 0 行：退款单已被处理过 → 整笔当幂等，**不要再执行 ②**。  
> ② 影响 0 行：支付单被并发改过 → 事务回滚，按 version 冲突重试（此时退款单也应回滚，避免只改了一边）。

---

## 3. 通知日志表 `pay_notify_log`

用于：**微信/支付宝入站回调**（以及主动查单后需要留痕时）。  
本表只记渠道 → 支付系统；**不**记支付系统 → 上游的出站通知。

### 3.1 字段

| 字段 | 类型（建议） | 必填 | 说明 |
|------|--------------|------|------|
| `id` | BIGINT | 是 | 主键（本地日志号直接用它） |
| `notify_type` | VARCHAR(32) | 是 | PAY_RESULT / REFUND_RESULT / … |
| `channel` | VARCHAR(16) | 是 | WECHAT / ALIPAY |
| `payment_no` | VARCHAR(64) | 否 | 解析出的商户支付单号 |
| `refund_no` | VARCHAR(64) | 否 | 解析出的商户退款单号 |
| `channel_trade_no` | VARCHAR(64) | 否 | 渠道支付单号 |
| `channel_refund_no` | VARCHAR(64) | 否 | 渠道退款单号 |
| `channel_notify_id` | VARCHAR(128) | 否 | 渠道通知唯一标识（若有，做幂等去重） |
| `request_body` | TEXT / MEDIUMTEXT | 是 | 原始报文 |
| `verify_status` | VARCHAR(16) | 是 | INIT / PASS / FAIL |
| `process_status` | VARCHAR(16) | 是 | RECEIVED / SUCCESS / IGNORED / FAIL |
| `process_times` | INT | 是 | 处理次数，默认 0 |
| `process_msg` | VARCHAR(512) | 否 | 处理说明/错误 |
| `response_body` | VARCHAR(512) | 否 | 返回给渠道的应答内容 |
| `received_at` | DATETIME | 是 | 收到时间 |
| `processed_at` | DATETIME | 否 | 处理完成时间 |
| `created_at` | DATETIME | 是 | |
| `updated_at` | DATETIME | 是 | |

### 3.2 刻意不进主表的字段

| 不建字段 | 原因 |
|----------|------|
| `notify_no` | 有自增 `id` 即可 |
| `request_headers` | 验签关键信息多在 body；排障再临时加 |
| `http_status` | 出站回调上游才用；本表只管入站 |

### 3.3 状态取值

**verify_status**

| 值 | 含义 |
|----|------|
| `INIT` | 刚落库未验签 |
| `PASS` | 验签通过 |
| `FAIL` | 验签失败 |

**process_status**

| 值 | 含义 |
|----|------|
| `RECEIVED` | 已收到，处理中 |
| `SUCCESS` | 业务已成功处理（含「重复通知但业务早已成功」） |
| `IGNORED` | 合法但无需处理（如非终态、金额不匹配已告警后忽略等，按你策略） |
| `FAIL` | 处理失败，待重试/人工 |

### 3.4 索引与约束

| 名 | 类型 | 列 | 说明 |
|----|------|----|------|
| `pk` | PRIMARY | `id` | |
| `uk_channel_notify` | UNIQUE | `channel`, `channel_notify_id` | 渠道通知去重；`channel_notify_id` 可空时看库行为 |
| `idx_payment_no` | INDEX | `payment_no` | |
| `idx_refund_no` | INDEX | `refund_no` | |
| `idx_process` | INDEX | `process_status`, `received_at` | 失败重扫 |
| `idx_channel_trade` | INDEX | `channel_trade_no` | 对账 |

> 若渠道没有稳定 `channel_notify_id`：可用  
> `(channel, notify_type, payment_no/refund_no, body_hash)` 做去重，按需加 `body_hash` 字段。

### 3.5 使用方式（约定）

```text
1. 一进回调：先 insert 日志（原文），再验签
2. 验签失败：verify_status=FAIL，process_status=FAIL/IGNORED，仍可 200 或按渠道要求应答（防重放策略自定）
3. 验签成功：走 handlePaySuccess / handleRefundResult
4. 业务幂等成功或「已是终态」：process_status=SUCCESS，应答渠道成功
5. 业务异常：process_status=FAIL，可重试；应答策略按是否希望渠道重投决定
```

通知日志 **不替代** 支付单/退款单状态；  
**钱的状态只认 `pay_payment` / `pay_refund`。**

---

## 4. 三表怎么配合（一条龙）

### 4.1 支付

```text
创建 pay_payment(PAYING, expire_at=…)
  → 调渠道预下单（subject 等临时传入，不落库）
  → 返回收银台参数给调用方（不落库）
  → 用户付款
  → 回调进入 pay_notify_log
  → handlePaySuccess：PAYING→SUCCESS
  → 发 MQ/事件通知上游（配置固定，不用 per-row notify_url）
```

### 4.2 部分退款

```text
校验支付单 SUCCESS 且余额足够
  → 占额度 refunding_amount
  → 创建 pay_refund(REFUNDING)
  → 调渠道退款（channel / channel_trade_no 从支付单取出）
  → 回调/查单进 pay_notify_log
  → 退款单 SUCCESS + 支付单累加 refunded_amount
  → 退满则支付单 REFUNDED
  → 发 MQ/事件通知上游
```

### 4.3 为何没有订单表也能跑

| 原订单职责 | 放到哪里 |
|------------|----------|
| 业务单号 | `biz_no` |
| 是否已付 | 查该 `biz_no` 下是否存在 `SUCCESS`/`REFUNDED` 支付单 |
| 业务回调 | 固定 MQ / 配置回调，不落支付表 |
| 售后审批、退款原因 | 不在支付库；上游系统自己管 |
| 支付超时 | `expire_at` + 扫 `PAYING` 关单（关前先查渠道） |

---

## 5. 建表 DDL 草案（MySQL 8）

```sql
CREATE TABLE pay_payment (
  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payment_no        VARCHAR(64)  NOT NULL COMMENT '商户支付单号',
  biz_no            VARCHAR(64)  NOT NULL COMMENT '业务单号',
  channel           VARCHAR(16)  NOT NULL COMMENT 'WECHAT/ALIPAY',
  pay_amount        BIGINT       NOT NULL COMMENT '分',
  status            VARCHAR(16)  NOT NULL COMMENT 'PAYING/SUCCESS/CLOSED/REFUNDED',
  refunded_amount   BIGINT       NOT NULL DEFAULT 0,
  refunding_amount  BIGINT       NOT NULL DEFAULT 0,
  channel_trade_no  VARCHAR(64)  DEFAULT NULL,
  prepay_id         VARCHAR(128) DEFAULT NULL,
  expire_at         DATETIME     NOT NULL COMMENT '支付过期时间',
  paid_at           DATETIME     DEFAULT NULL,
  version           INT          NOT NULL DEFAULT 0,
  extra             JSON         DEFAULT NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_channel_trade_no (channel_trade_no),
  KEY idx_biz_no (biz_no, status),
  KEY idx_status_expire (status, expire_at)
) COMMENT='支付单';

CREATE TABLE pay_refund (
  id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  refund_no          VARCHAR(64)  NOT NULL COMMENT '商户退款单号',
  payment_no         VARCHAR(64)  NOT NULL,
  refund_amount      BIGINT       NOT NULL COMMENT '本次退款，分',
  status             VARCHAR(16)  NOT NULL COMMENT 'REFUNDING/SUCCESS/FAILED',
  channel_refund_no  VARCHAR(64)  DEFAULT NULL,
  success_at         DATETIME     DEFAULT NULL,
  fail_code          VARCHAR(64)  DEFAULT NULL,
  fail_msg           VARCHAR(256) DEFAULT NULL,
  version            INT          NOT NULL DEFAULT 0,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_refund_no (refund_no),
  UNIQUE KEY uk_channel_refund_no (channel_refund_no),
  KEY idx_payment_no (payment_no, status),
  KEY idx_status_created (status, created_at)
) COMMENT='退款单';

CREATE TABLE pay_notify_log (
  id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  notify_type        VARCHAR(32)   NOT NULL COMMENT 'PAY_RESULT/REFUND_RESULT',
  channel            VARCHAR(16)   NOT NULL,
  payment_no         VARCHAR(64)   DEFAULT NULL,
  refund_no          VARCHAR(64)   DEFAULT NULL,
  channel_trade_no   VARCHAR(64)   DEFAULT NULL,
  channel_refund_no  VARCHAR(64)   DEFAULT NULL,
  channel_notify_id  VARCHAR(128)  DEFAULT NULL,
  request_body       MEDIUMTEXT    NOT NULL,
  verify_status      VARCHAR(16)   NOT NULL DEFAULT 'INIT',
  process_status     VARCHAR(16)   NOT NULL DEFAULT 'RECEIVED',
  process_times      INT           NOT NULL DEFAULT 0,
  process_msg        VARCHAR(512)  DEFAULT NULL,
  response_body      VARCHAR(512)  DEFAULT NULL,
  received_at        DATETIME      NOT NULL,
  processed_at       DATETIME      DEFAULT NULL,
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_channel_notify (channel, channel_notify_id),
  KEY idx_payment_no (payment_no),
  KEY idx_refund_no (refund_no),
  KEY idx_process (process_status, received_at),
  KEY idx_channel_trade (channel_trade_no)
) COMMENT='渠道通知日志';
```

---

## 6. 一页记住

| 表 | 一句话 | 最重要的唯一键 |
|----|--------|----------------|
| `pay_payment` | 管钱有没有收进来、退了多少 | `payment_no` |
| `pay_refund` | 管每一次退款 | `refund_no` |
| `pay_notify_log` | 管渠道说了什么、我们怎么处理的 | `id` + 渠道通知去重 |

| 规则 | 落在哪 |
|------|--------|
| 支付成功只一次 | `pay_payment`：`PAYING→SUCCESS` 条件更新 |
| 部分退、不超退 | `pay_payment.refunded_amount/refunding_amount` + `pay_refund` |
| 退款幂等 | `pay_refund.refund_no` |
| 回调重复 | 先写 `pay_notify_log`，业务单条件更新 |
| 乐观锁 | `WHERE version=:old` + `SET version=version+1`；影响 0 行则重读或幂等返回 |
| 超时关单 | `expire_at` + 扫 `PAYING`，关前先查渠道 |
| 通知上游 | 固定 MQ/配置，表内无 `notify_url` |

---

## 文档信息

| 版本 | 说明 |
|------|------|
| 1.0 | 仅三表：支付单、退款单、通知日志；含字段/索引/DDL 草案 |
| 1.1 | 精简字段：去掉 user_id/notify_url/subject/client_pay_param 等业务味字段；退款单去冗余拷贝；通知表只保留入站；保留 expire_at |
| 1.2 | 补齐 version 用法：WHERE 带旧 version；与状态/金额 CAS 的分工；退款双表更新顺序 |
