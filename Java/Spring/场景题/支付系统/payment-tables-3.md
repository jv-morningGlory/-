# 支付库表设计（三表版）

> 范围：只保留 **支付单、退款单、通知日志**。  
> 不建订单表；业务侧用 `biz_no`（业务单号）关联即可。  
> 对齐：《用户 · 订单 · 支付》《支付 · 部分退款》。

---

## 总览

| 表名 | 作用 |
|------|------|
| `pay_payment` | 一笔收款：预下单、支付中、成功/关闭、累计已退 |
| `pay_refund` | 一次退款：部分退/全额退、退款单号幂等 |
| `pay_notify_log` | 渠道回调/通知原文与处理结果（支付+退款都可记） |

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
| `biz_no` | VARCHAR(64) | 是 | 业务单号（订单号等，由上游传入） |
| `biz_type` | VARCHAR(32) | 否 | 业务类型：ORDER / RECHARGE / … |
| `user_id` | VARCHAR(64) | 否 | 用户标识（排查、对账用） |
| `channel` | VARCHAR(16) | 是 | WECHAT / ALIPAY |
| `pay_amount` | BIGINT | 是 | 应付/支付金额，**单位：分** |
| `currency` | VARCHAR(8) | 是 | 默认 CNY |
| `status` | VARCHAR(16) | 是 | PAYING / SUCCESS / CLOSED / REFUNDED |
| `refunded_amount` | BIGINT | 是 | 已成功退款累计，分；默认 0 |
| `refunding_amount` | BIGINT | 是 | 退款中占用金额，分；默认 0 |
| `channel_trade_no` | VARCHAR(64) | 否 | 渠道交易号（微信 transaction_id / 支付宝 trade_no） |
| `prepay_id` | VARCHAR(128) | 否 | 预下单凭证（如有） |
| `client_pay_param` | TEXT | 否 | 返回前端的收银台参数（JSON，可选） |
| `subject` | VARCHAR(128) | 否 | 商品描述/标题（传给渠道） |
| `expire_at` | DATETIME | 否 | 支付过期时间 |
| `paid_at` | DATETIME | 否 | 支付成功时间 |
| `closed_at` | DATETIME | 否 | 关闭时间 |
| `refunded_at` | DATETIME | 否 | 全额退完时间 |
| `notify_url` | VARCHAR(256) | 否 | 业务回调地址（支付成功通知上游） |
| `extra` | JSON / TEXT | 否 | 扩展字段 |
| `version` | INT | 是 | 乐观锁，默认 0 |
| `created_at` | DATETIME | 是 | 创建时间 |
| `updated_at` | DATETIME | 是 | 更新时间 |

### 1.2 状态取值

| status | 含义 |
|--------|------|
| `PAYING` | 已建单，未确认收款 |
| `SUCCESS` | 已收款，且 **未全额退完**（含从未退过、部分已退） |
| `CLOSED` | 取消/超时关闭（确认未付） |
| `REFUNDED` | 已全额退完（`refunded_amount = pay_amount`） |

### 1.3 索引与约束

| 名 | 类型 | 列 | 说明 |
|----|------|----|------|
| `pk` | PRIMARY | `id` | |
| `uk_payment_no` | UNIQUE | `payment_no` | 支付单号唯一 |
| `uk_channel_trade_no` | UNIQUE | `channel_trade_no` | 渠道单号唯一（允许 NULL；MySQL 下多 NULL 一般不冲突） |
| `idx_biz_no` | INDEX | `biz_no`, `status` | 按业务单查支付单 |
| `idx_status_created` | INDEX | `status`, `created_at` | 扫支付中、超时关单 |
| `idx_user_created` | INDEX | `user_id`, `created_at` | 可选 |

### 1.4 金额不变量

```text
pay_amount > 0
refunded_amount >= 0
refunding_amount >= 0
refunded_amount + refunding_amount <= pay_amount

SUCCESS 时通常 refunded_amount < pay_amount（或 =0）
REFUNDED 时 refunded_amount = pay_amount 且 refunding_amount = 0
```

### 1.5 关键更新示例（思路）

```sql
-- 支付成功（只一次）
UPDATE pay_payment
SET status = 'SUCCESS',
    channel_trade_no = ?,
    paid_at = NOW(),
    updated_at = NOW(),
    version = version + 1
WHERE payment_no = ?
  AND status = 'PAYING';

-- 发起退款前占额度
UPDATE pay_payment
SET refunding_amount = refunding_amount + ?,
    updated_at = NOW(),
    version = version + 1
WHERE payment_no = ?
  AND status = 'SUCCESS'
  AND pay_amount - refunded_amount - refunding_amount >= ?;

-- 关单（未付）
UPDATE pay_payment
SET status = 'CLOSED',
    closed_at = NOW(),
    updated_at = NOW(),
    version = version + 1
WHERE payment_no = ?
  AND status = 'PAYING';
```

---

## 2. 退款单表 `pay_refund`

### 2.1 字段

| 字段 | 类型（建议） | 必填 | 说明 |
|------|--------------|------|------|
| `id` | BIGINT | 是 | 主键 |
| `refund_no` | VARCHAR(64) | 是 | 商户退款单号，全局唯一（幂等键） |
| `payment_no` | VARCHAR(64) | 是 | 原支付单号 |
| `biz_no` | VARCHAR(64) | 是 | 业务单号（冗余，方便查） |
| `channel` | VARCHAR(16) | 是 | WECHAT / ALIPAY（与支付单一致） |
| `refund_amount` | BIGINT | 是 | **本次**退款金额，分 |
| `pay_amount` | BIGINT | 是 | 原支付金额快照，分（下单时拷贝） |
| `status` | VARCHAR(16) | 是 | REFUNDING / SUCCESS / FAILED |
| `reason` | VARCHAR(256) | 否 | 退款原因 |
| `channel_refund_no` | VARCHAR(64) | 否 | 渠道退款单号 |
| `channel_trade_no` | VARCHAR(64) | 否 | 原渠道支付单号（冗余） |
| `success_at` | DATETIME | 否 | 退款成功时间 |
| `failed_at` | DATETIME | 否 | 失败时间 |
| `fail_code` | VARCHAR(64) | 否 | 失败码 |
| `fail_msg` | VARCHAR(256) | 否 | 失败描述 |
| `notify_url` | VARCHAR(256) | 否 | 退款结果通知上游 |
| `extra` | JSON / TEXT | 否 | 扩展 |
| `version` | INT | 是 | 乐观锁 |
| `created_at` | DATETIME | 是 | |
| `updated_at` | DATETIME | 是 | |

### 2.2 状态取值

| status | 含义 |
|--------|------|
| `REFUNDING` | 已受理/处理中 |
| `SUCCESS` | 本次退款成功（以渠道确认为准） |
| `FAILED` | 本次明确失败（已释放支付单占用额度） |

### 2.3 索引与约束

| 名 | 类型 | 列 | 说明 |
|----|------|----|------|
| `pk` | PRIMARY | `id` | |
| `uk_refund_no` | UNIQUE | `refund_no` | **幂等核心** |
| `uk_channel_refund_no` | UNIQUE | `channel_refund_no` | 渠道退款号，可空 |
| `idx_payment_no` | INDEX | `payment_no`, `status` | 一笔支付下所有退款 |
| `idx_biz_no` | INDEX | `biz_no` | 按业务单查 |
| `idx_status_created` | INDEX | `status`, `created_at` | 扫处理中退款、补偿查单 |

### 2.4 关键更新示例（思路）

```sql
-- 退款成功只入账一次（退款单）
UPDATE pay_refund
SET status = 'SUCCESS',
    channel_refund_no = ?,
    success_at = NOW(),
    updated_at = NOW(),
    version = version + 1
WHERE refund_no = ?
  AND status = 'REFUNDING';

-- 同上事务内：累加支付单已退（伪代码条件）
UPDATE pay_payment
SET refunded_amount = refunded_amount + :amt,
    refunding_amount = refunding_amount - :amt,
    status = CASE
      WHEN refunded_amount + :amt >= pay_amount THEN 'REFUNDED'
      ELSE status
    END,
    refunded_at = CASE
      WHEN refunded_amount + :amt >= pay_amount THEN NOW()
      ELSE refunded_at
    END,
    updated_at = NOW(),
    version = version + 1
WHERE payment_no = :payNo
  AND status = 'SUCCESS'
  AND refunding_amount >= :amt
  AND refunded_amount + :amt <= pay_amount;
```

---

## 3. 通知日志表 `pay_notify_log`

用于：**微信/支付宝回调、以及你主动查单后的落库痕迹**（建议支付结果、退款结果都记）。

### 3.1 字段

| 字段 | 类型（建议） | 必填 | 说明 |
|------|--------------|------|------|
| `id` | BIGINT | 是 | 主键 |
| `notify_no` | VARCHAR(64) | 是 | 本地通知日志号（自己生成，唯一） |
| `notify_type` | VARCHAR(32) | 是 | PAY_RESULT / REFUND_RESULT / … |
| `channel` | VARCHAR(16) | 是 | WECHAT / ALIPAY |
| `payment_no` | VARCHAR(64) | 否 | 解析出的商户支付单号 |
| `refund_no` | VARCHAR(64) | 否 | 解析出的商户退款单号 |
| `channel_trade_no` | VARCHAR(64) | 否 | 渠道支付单号 |
| `channel_refund_no` | VARCHAR(64) | 否 | 渠道退款单号 |
| `channel_notify_id` | VARCHAR(128) | 否 | 渠道通知唯一标识（若有，做幂等去重） |
| `request_headers` | TEXT | 否 | 请求头（注意脱敏） |
| `request_body` | TEXT / MEDIUMTEXT | 是 | 原始报文 |
| `verify_status` | VARCHAR(16) | 是 | INIT / PASS / FAIL |
| `process_status` | VARCHAR(16) | 是 | RECEIVED / SUCCESS / IGNORED / FAIL |
| `process_times` | INT | 是 | 处理次数，默认 0 |
| `process_msg` | VARCHAR(512) | 否 | 处理说明/错误 |
| `http_status` | INT | 否 | 若是出站回调上游，可记；入站可空 |
| `response_body` | VARCHAR(512) | 否 | 返回给渠道的应答内容 |
| `received_at` | DATETIME | 是 | 收到时间 |
| `processed_at` | DATETIME | 否 | 处理完成时间 |
| `created_at` | DATETIME | 是 | |
| `updated_at` | DATETIME | 是 | |

### 3.2 状态取值

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

### 3.3 索引与约束

| 名 | 类型 | 列 | 说明 |
|----|------|----|------|
| `pk` | PRIMARY | `id` | |
| `uk_notify_no` | UNIQUE | `notify_no` | |
| `uk_channel_notify` | UNIQUE | `channel`, `channel_notify_id` | 渠道通知去重；`channel_notify_id` 可空时看库行为 |
| `idx_payment_no` | INDEX | `payment_no` | |
| `idx_refund_no` | INDEX | `refund_no` | |
| `idx_process` | INDEX | `process_status`, `received_at` | 失败重扫 |
| `idx_channel_trade` | INDEX | `channel_trade_no` | 对账 |

> 若渠道没有稳定 `channel_notify_id`：可用  
> `(channel, notify_type, payment_no/refund_no, body_hash)` 做去重，按需加 `body_hash` 字段。

### 3.4 使用方式（约定）

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
创建 pay_payment(PAYING)
  → 调渠道预下单
  → 用户付款
  → 回调进入 pay_notify_log
  → handlePaySuccess：PAYING→SUCCESS
```

### 4.2 部分退款

```text
校验支付单 SUCCESS 且余额足够
  → 占额度 refunding_amount
  → 创建 pay_refund(REFUNDING)
  → 调渠道退款
  → 回调/查单进 pay_notify_log
  → 退款单 SUCCESS + 支付单累加 refunded_amount
  → 退满则支付单 REFUNDED
```

### 4.3 为何没有订单表也能跑

| 原订单职责 | 放到哪里 |
|------------|----------|
| 业务单号 | `biz_no` |
| 是否已付 | 查该 `biz_no` 下是否存在 `SUCCESS`/`REFUNDED` 支付单 |
| 业务回调 | `notify_url` 或上游自己听消息 |
| 售后审批 | 不在支付库；上游系统自己管 |

---

## 5. 建表 DDL 草案（MySQL 8）

```sql
CREATE TABLE pay_payment (
  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payment_no        VARCHAR(64)  NOT NULL COMMENT '商户支付单号',
  biz_no            VARCHAR(64)  NOT NULL COMMENT '业务单号',
  biz_type          VARCHAR(32)  DEFAULT NULL,
  user_id           VARCHAR(64)  DEFAULT NULL,
  channel           VARCHAR(16)  NOT NULL COMMENT 'WECHAT/ALIPAY',
  pay_amount        BIGINT       NOT NULL COMMENT '分',
  currency          VARCHAR(8)   NOT NULL DEFAULT 'CNY',
  status            VARCHAR(16)  NOT NULL COMMENT 'PAYING/SUCCESS/CLOSED/REFUNDED',
  refunded_amount   BIGINT       NOT NULL DEFAULT 0,
  refunding_amount  BIGINT       NOT NULL DEFAULT 0,
  channel_trade_no  VARCHAR(64)  DEFAULT NULL,
  prepay_id         VARCHAR(128) DEFAULT NULL,
  client_pay_param  TEXT         DEFAULT NULL,
  subject           VARCHAR(128) DEFAULT NULL,
  expire_at         DATETIME     DEFAULT NULL,
  paid_at           DATETIME     DEFAULT NULL,
  closed_at         DATETIME     DEFAULT NULL,
  refunded_at       DATETIME     DEFAULT NULL,
  notify_url        VARCHAR(256) DEFAULT NULL,
  extra             JSON         DEFAULT NULL,
  version           INT          NOT NULL DEFAULT 0,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_channel_trade_no (channel_trade_no),
  KEY idx_biz_no (biz_no, status),
  KEY idx_status_created (status, created_at),
  KEY idx_user_created (user_id, created_at)
) COMMENT='支付单';

CREATE TABLE pay_refund (
  id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  refund_no          VARCHAR(64)  NOT NULL COMMENT '商户退款单号',
  payment_no         VARCHAR(64)  NOT NULL,
  biz_no             VARCHAR(64)  NOT NULL,
  channel            VARCHAR(16)  NOT NULL,
  refund_amount      BIGINT       NOT NULL COMMENT '本次退款，分',
  pay_amount         BIGINT       NOT NULL COMMENT '原支付金额快照，分',
  status             VARCHAR(16)  NOT NULL COMMENT 'REFUNDING/SUCCESS/FAILED',
  reason             VARCHAR(256) DEFAULT NULL,
  channel_refund_no  VARCHAR(64)  DEFAULT NULL,
  channel_trade_no   VARCHAR(64)  DEFAULT NULL,
  success_at         DATETIME     DEFAULT NULL,
  failed_at          DATETIME     DEFAULT NULL,
  fail_code          VARCHAR(64)  DEFAULT NULL,
  fail_msg           VARCHAR(256) DEFAULT NULL,
  notify_url         VARCHAR(256) DEFAULT NULL,
  extra              JSON         DEFAULT NULL,
  version            INT          NOT NULL DEFAULT 0,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_refund_no (refund_no),
  UNIQUE KEY uk_channel_refund_no (channel_refund_no),
  KEY idx_payment_no (payment_no, status),
  KEY idx_biz_no (biz_no),
  KEY idx_status_created (status, created_at)
) COMMENT='退款单';

CREATE TABLE pay_notify_log (
  id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  notify_no          VARCHAR(64)   NOT NULL,
  notify_type        VARCHAR(32)   NOT NULL COMMENT 'PAY_RESULT/REFUND_RESULT',
  channel            VARCHAR(16)   NOT NULL,
  payment_no         VARCHAR(64)   DEFAULT NULL,
  refund_no          VARCHAR(64)   DEFAULT NULL,
  channel_trade_no   VARCHAR(64)   DEFAULT NULL,
  channel_refund_no  VARCHAR(64)   DEFAULT NULL,
  channel_notify_id  VARCHAR(128)  DEFAULT NULL,
  request_headers    TEXT          DEFAULT NULL,
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
  UNIQUE KEY uk_notify_no (notify_no),
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
| `pay_notify_log` | 管渠道说了什么、我们怎么处理的 | `notify_no`（+ 渠道通知去重） |

| 规则 | 落在哪 |
|------|--------|
| 支付成功只一次 | `pay_payment`：`PAYING→SUCCESS` 条件更新 |
| 部分退、不超退 | `pay_payment.refunded_amount/refunding_amount` + `pay_refund` |
| 退款幂等 | `pay_refund.refund_no` |
| 回调重复 | 先写 `pay_notify_log`，业务单条件更新 |

---

## 文档信息

| 版本 | 说明 |
|------|------|
| 1.0 | 仅三表：支付单、退款单、通知日志；含字段/索引/DDL 草案 |
