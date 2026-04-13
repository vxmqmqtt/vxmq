# Retained Message 模型

本文档定义 Broker 内部对 Retained Message 的存储归属、键模型与边界。它回答“保留消息存在哪里、谁拥有真相、与 session / routing 如何分工”，不承担阶段汇报职责。

## 目标

- 为 MQTT 3.1.1 与 MQTT 5 提供共用的 retained 基础语义。
- 让 retained 存储独立于会话与路由索引，避免状态归属混乱。
- 为后续持久化与高级 retain 属性预留稳定演进点。

## 状态归属

- `session` 保存客户端会话真相，例如订阅、离线队列和 QoS 1 inflight。
- `routing` 保存订阅匹配索引，用于回答“某个 Topic Name 命中了谁”。
- `retained` 保存按 Topic Name 建立的保留消息真相，用于回答“某个 Topic Filter 订阅后应立即收到什么”。

Retained Message 不属于 `session`，也不属于 `routing`。

## 当前数据模型

当前 `RetainedMessage` 至少包含：

- `topicName`
- `payload`
- `qos`
- `retain=true`

字段语义：

- `topicName`：保留消息的唯一键，当前一个 Topic Name 最多存在一条 retained 记录。
- `payload`：保留的消息内容，空 payload 不写入 retained 存储，而是用于清除。
- `qos`：记录原始入站发布的 QoS（当前只会是 0 或 1）。
- `retain`：对外下发 retained 消息时始终为 `true`。

## 当前存储策略

- 当前实现为单机、内存态 retained store。
- 新的 retained 发布会覆盖同一 Topic Name 的旧 retained 记录。
- `retain=true` 且空 payload 会删除该 Topic Name 的 retained 记录。
- 当前不实现消息过期、容量限制、持久化和后台清理器。

## 与协议链路的关系

### 发布路径

- 普通发布：`retain=false` 时，不更新 retained store。
- 保留发布：`retain=true` 且 payload 非空时，更新 retained store。
- 保留清除：`retain=true` 且 payload 为空时，删除 retained store 中对应记录。

### 订阅路径

- SUBSCRIBE 成功后，Broker 使用 Topic Filter 查询 retained store。
- 命中的 retained 消息在 SUBACK 之后立即下发给当前订阅者。
- 下发 QoS 取 `min(retained.qos, grantedQos)`。

## 与路由设计的边界

- retained store 不参与普通发布时的在线订阅匹配。
- `routing` 也不保存 retained 真相，只继续维护订阅匹配索引。
- 若后续引入订阅树，retained store 仍只依赖 Topic Filter 匹配能力，不直接变成路由索引的一部分。

## 当前阶段边界

当前 retained 模型已规划覆盖：

- 写入、覆盖、清除
- 订阅后立即下发
- 与 QoS 0 / QoS 1 主链路衔接

当前仍未覆盖：

- MQTT 5 `Retain Handling`
- MQTT 5 `Retain Available`
- `Message Expiry Interval`
- 跨重启 retained 恢复
