# 会话模型

本文档定义 Broker 内部的会话真相模型。它描述“会话是什么、归谁所有、在何时创建、离线后如何保留、何时被删除”，不承担阶段汇报职责。

## 目标

- 让会话状态脱离“当前在线连接”的短生命周期。
- 为 MQTT 3.1.1 `Clean Session` 与 MQTT 5 `Clean Start / Session Expiry` 提供统一内部表达。
- 为后续离线消息、QoS 1 / QoS 2、Retained Message、Will Message 预留稳定承载位置。

## 会话与连接的关系

- 连接是网络层对象，由 `transport` 和 `ClientConnectionRegistry` 管理。
- 会话是 `clientId` 归属的协议状态，由 `SessionRegistry` 管理。
- 同一时刻一个 `clientId` 最多只有一个当前活跃连接，但可以存在一个离线会话。
- 连接关闭不等于会话必然删除；是否保留会话由 MQTT 版本和连接时策略决定。

## 当前会话字段

当前 `ClientSession` 至少包含以下字段：

- `clientId`
- `connectionId`
- `persistent`
- `sessionExpiryIntervalSeconds`
- `expiresAt`
- `subscriptions`

字段语义：

- `clientId`：会话主键，也是订阅、离线状态和后续消息状态的归属键。
- `connectionId`：当前在线连接 ID；若为 `null`，表示会话处于离线状态。
- `persistent`：连接关闭后该会话是否允许继续保留。
- `sessionExpiryIntervalSeconds`：MQTT 5 会话过期秒数；对 MQTT 3.1.1 的持久会话使用 `null` 表示“无该字段”。
- `expiresAt`：离线会话的预期过期时间；在线会话或无限期保留的 MQTT 3.1.1 持久会话为 `null`。
- `subscriptions`：该会话拥有的订阅集合。当前阶段只恢复订阅，不恢复离线消息。

## 在线、离线与过期

### 在线会话

- `connectionId != null`
- `expiresAt == null`
- 订阅修改直接写入该会话

### 离线会话

- `connectionId == null`
- 持久会话在连接关闭后可进入离线状态
- 订阅集合继续保留，供后续重连恢复

### 过期会话

- 当前实现采用“记录 `expiresAt` + 懒清理”策略
- 若离线会话的 `expiresAt` 已到，下一次访问该会话时删除
- 当前阶段不引入后台扫描器

## 持久与非持久会话

### 非持久会话

- MQTT 3.1.1：`cleanSession=true`
- MQTT 5：`Session Expiry Interval=0`

行为：

- 可在连接期间拥有订阅
- 连接关闭后立即删除会话
- 后续同 `clientId` 重连不会恢复旧订阅

### 持久会话

- MQTT 3.1.1：`cleanSession=false`
- MQTT 5：`Session Expiry Interval>0`

行为：

- 连接关闭后进入离线状态
- 订阅集合继续保留
- 同 `clientId` 重连时可恢复会话

## SessionRegistry 的职责

`SessionRegistry` 是会话真相的唯一所有者，当前职责包括：

- 按 CONNECT 请求打开新会话或恢复既有会话
- 在连接关闭时根据会话策略决定“保留、离线、删除”
- 维护会话级订阅集合
- 在读取或修改会话前执行懒清理

`SessionRegistry` 不负责：

- 管理在线 endpoint
- 执行 Topic 匹配
- 保存离线消息队列
- 处理跨重启恢复

## 与路由索引的关系

- `SessionRegistry` 保存“会话拥有了哪些订阅”
- `SubscriptionRegistry` 保存“这些订阅如何参与匹配”
- 订阅状态的真相属于 `session`；`routing` 只维护用于匹配和投递的派生索引
- 当会话被显式删除时，`protocol` 负责同步清理路由索引中的对应绑定
- 当前阶段仍使用内存态订阅索引，不在本阶段引入订阅树

## 当前阶段边界

当前会话模型已经覆盖：

- MQTT 3.1.1 `Clean Session`
- MQTT 5 `Clean Start / Session Expiry`
- 离线会话的订阅恢复
- 会话懒清理

当前仍未覆盖：

- 离线消息实际入队与重投递
- Will Message 发布
- Retained Message
- 持久化与跨重启恢复
