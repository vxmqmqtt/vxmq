# SUBSCRIBE 流程

本文档定义 `M1` 阶段订阅相关处理流程，覆盖：

- SUBSCRIBE / SUBACK。
- Topic Filter 校验。
- 订阅注册与会话绑定。
- UNSUBSCRIBE / UNSUBACK。
- 失败路径与一致性要求。

## 目标

`M1` 的订阅流程要保证：

1. 客户端可稳定建立、删除普通订阅。
2. Topic Filter 与通配符语义正确。
3. 订阅状态在 `session` 与 `routing` 中保持一致。

## 输入与输出

输入：

- 已完成 CONNECT 的客户端发送 SUBSCRIBE。
- 已完成 CONNECT 的客户端发送 UNSUBSCRIBE。

输出：

- 成功订阅时返回 SUBACK。
- 成功取消订阅时返回 UNSUBACK。
- 非法请求时返回协议拒绝结果或关闭连接。

## 前置约束

- 连接必须处于 `CONNECTED` 状态。
- `M1` 只支持普通订阅，不支持 Shared Subscription。
- `M1` 只处理 QoS 0 投递路径，但订阅请求中的请求 QoS 需要做合法性校验。

## 流程总览

### SUBSCRIBE

```text
read SUBSCRIBE
  -> decode and basic validation
  -> protocol validation
  -> validate topic filters
  -> update session subscriptions
  -> update routing registry
  -> build SUBACK
  -> send SUBACK
```

### UNSUBSCRIBE

```text
read UNSUBSCRIBE
  -> decode and basic validation
  -> protocol validation
  -> remove session subscriptions
  -> remove routing registry bindings
  -> send UNSUBACK
```

## SUBSCRIBE 详细步骤

### 1. 接收与解码

- `codec` 将字节流解码为 SUBSCRIBE 报文对象。
- 若报文结构非法，按协议错误路径处理。

`M1` 关注点：

- Packet Identifier 必须存在。
- 至少包含一个订阅项。
- 保留位与报文标志合法。

### 2. 协议语义校验

至少校验以下内容：

- 当前连接已完成 CONNECT。
- Packet Identifier 在当前连接上下文中合法。
- 每个订阅项的请求 QoS 合法。
- 当前阶段不支持的订阅选项要有明确策略。

`M1` 建议：

- 对暂未支持的高级订阅选项，不要静默接受后忽略，应明确拒绝或文档化降级行为。

### 3. Topic Filter 校验

每个 Topic Filter 至少校验：

- 不为空。
- 通配符位置合法。
- `#` 只能出现在尾部且独占层级。
- `+` 必须占据完整层级。
- 不接受 Shared Subscription 语法。

## 订阅注册

注册分两部分：

1. 更新 `session` 内的订阅集。
2. 更新 `routing` 内的 Topic Filter 索引。

推荐顺序：

1. 先构造目标订阅对象。
2. 在 `session` 中执行订阅集变更。
3. 在 `routing` 中注册映射。
4. 全部成功后返回 SUBACK。

一致性要求：

- 不允许只更新一侧状态。
- 若第二步成功、第三步失败，必须回滚 `session` 侧变更。

## SUBACK 返回

SUBACK 至少包含：

- 对应 Packet Identifier。
- 每个订阅项的结果码。

`M1` 建议：

- 只有当订阅真正生效时才返回成功码。
- 若部分订阅项失败，应精确返回逐项结果，而不是整体模糊失败。

## UNSUBSCRIBE 详细步骤

### 1. 接收与解码

- 解码 UNSUBSCRIBE 报文。
- 校验 Packet Identifier 与订阅项列表。

### 2. 协议校验

- 连接状态必须为 `CONNECTED`。
- Topic Filter 列表不能为空。

### 3. 取消订阅

执行顺序建议：

1. 从 `session` 查找要删除的订阅。
2. 从 `routing` 中移除对应映射。
3. 从 `session` 中移除订阅对象。
4. 返回 UNSUBACK。

实现约束：

- 不存在的订阅取消请求不应导致内部异常。
- 删除行为要幂等，避免重复取消引发状态污染。

## 失败路径

### Topic Filter 非法

- 返回协议错误或关闭连接。

### 连接状态非法

- 返回协议错误或关闭连接。

### 状态更新失败

- 记录日志。
- 回滚已更新的一侧状态。
- 返回失败或关闭连接。

### 内部异常

- 记录错误。
- 关闭连接，避免残留不一致状态。

## 状态影响

### session

新增或删除：

- `subscriptions`

### routing

新增或删除：

- Topic Filter 到订阅者的映射

### observability

记录：

- 订阅创建成功数
- 订阅删除成功数
- 订阅拒绝数

## M1 验收点

- 客户端可订阅普通 Topic Filter 并收到成功 SUBACK。
- `+` 和 `#` 通配符匹配语义正确。
- 同一连接可重复订阅并维持一致行为。
- 客户端取消订阅后不再收到对应消息。
- 非法 Topic Filter 会被拒绝。
- 集成测试覆盖成功订阅、重复订阅、取消订阅、非法过滤器四类路径。

## 待确认项

- `M1` 对重复订阅是覆盖还是保留原订阅对象。
- `M1` 是否完整支持 MQTT 5 订阅选项字段。
- SUBACK 的失败码细粒度是否在 `M1` 完整实现。
