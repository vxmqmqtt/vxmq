# 当前状态

本文档是项目当前状态的唯一集中入口。除里程碑文档和历史验收文档外，其他设计文档不再重复维护阶段结论。

## 当前阶段

项目当前处于：`M1 已完成，M2 已完成，准备进入 M3`

## 当前已完成能力

- CONNECT / CONNACK 基础处理
- MQTT 3.1.1 与 MQTT 5 的基础连接差异处理
- 空 `clientId` 自动分配与连接接管
- MQTT 3.1.1 `Clean Session`
- MQTT 5 `Clean Start / Session Expiry`
- 持久会话订阅恢复与会话懒清理
- QoS 1 入站与出站主链路
- 持久会话离线 QoS 1 消息积压与重连恢复
- Retained Message 基础语义
- Will Message 基础语义
- 订阅树 / 路由索引重构
- SUBSCRIBE / SUBACK
- UNSUBSCRIBE / UNSUBACK
- PUBLISH QoS 0 主链路
- Topic Filter / Wildcard 匹配
- 基础断连语义
- 基于 `vertx-mqtt` 的 Keep Alive 超时处理
- 单元测试与真实 MQTT 集成测试闭环

## 当前代码实现边界

- 当前实现是单机、内存态 Broker。
- 当前主链路已覆盖 QoS 0 / QoS 1，但仍不支持 QoS 2。
- 当前已实现会话过期的懒清理、持久会话订阅恢复、离线 QoS 1 消息恢复、Retained Message、基础 Will Message 和订阅树路由索引，但尚未实现用户名密码鉴权和 TLS。
- 当前路由和会话状态均为内存实现，不具备持久化和重启恢复能力。

## 当前文档真相入口

- 项目目标与边界：[`../00-foundation/vision.md`](../00-foundation/vision.md)、[`../00-foundation/scope.md`](../00-foundation/scope.md)
- 协议兼容策略：[`../00-foundation/compatibility.md`](../00-foundation/compatibility.md)
- 会话设计真相：[`../02-architecture/session-model.md`](../02-architecture/session-model.md)、[`../03-protocol/session-lifecycle.md`](../03-protocol/session-lifecycle.md)
- Will 设计真相：[`../02-architecture/will-message-model.md`](../02-architecture/will-message-model.md)、[`../03-protocol/will-flow.md`](../03-protocol/will-flow.md)
- 订阅树设计真相：[`../02-architecture/subscription-tree-model.md`](../02-architecture/subscription-tree-model.md)、[`../03-protocol/topic-match-flow.md`](../03-protocol/topic-match-flow.md)
- 特性完成度：[`mqtt5-feature-matrix.md`](mqtt5-feature-matrix.md)
- 阶段规划：[`milestones.md`](milestones.md)
- 当前已完成阶段的历史验收：[`m1-acceptance-checklist.md`](m1-acceptance-checklist.md)

## 当前主要缺口

- QoS 2 状态机尚未进入设计与实现。
- 基础鉴权、观测、运维和恢复能力尚未进入实现阶段。

## 下一阶段入口

`M3` 的下一步重点从“内部基础设施升级”转向“协议完整性与运维补全”：QoS 2、Subscription Options、Subscription Identifier、基础鉴权和可观测性。
