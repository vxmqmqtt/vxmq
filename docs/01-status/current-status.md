# 当前状态

本文档是项目当前状态的唯一集中入口。当前阶段、能力边界、当前缺口和下一步优先级统一在这里维护。

## 当前阶段

项目当前处于：`M1 已完成，M2 已完成，准备进入 M3`

## 当前已完成能力

- CONNECT / CONNACK 基础处理
- MQTT 3.1.1 与 MQTT 5 的基础连接差异处理
- 空 `clientId` 自动分配与连接接管
- MQTT 3.1.1 `Clean Session`
- MQTT 5 `Clean Start / Session Expiry`
- 持久会话订阅恢复与会话懒清理
- MQTT 5 Subscription Options
- MQTT 5 Subscription Identifier
- MQTT 5 PUBLISH User Property 透传
- QoS 1 入站与出站主链路
- QoS 2 入站与出站状态机
- 持久会话离线 QoS 1 消息积压与重连恢复
- 持久会话离线 QoS 2 消息积压与重连恢复
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
- 当前主链路已覆盖 QoS 0 / QoS 1 / QoS 2；QoS 2 支持普通发布和 retained 重放，will QoS 2 延后。
- 当前已实现会话过期的懒清理、持久会话订阅恢复、离线 QoS 1 消息恢复、Retained Message、基础 Will Message、订阅树路由索引、Subscription Options、Subscription Identifier 和 PUBLISH User Property 透传，但尚未实现用户名密码鉴权和 TLS。
- 当前路由和会话状态均为内存实现，不具备持久化和重启恢复能力。
- 当前主线订阅树已采用 `snapshot / copy-on-write` 方案完成并发安全落地，并加入了更紧凑的不可变节点表示与内部 batch snapshot 重建路径；评估候选与 benchmark harness 作为独立评估套件保留。

## 当前文档真相入口

- 项目定位与范围：[`../00-foundation/project.md`](../00-foundation/project.md)
- 协议兼容策略：[`../00-foundation/compatibility.md`](../00-foundation/compatibility.md)
- 系统设计与模块边界：[`../02-system/system-design.md`](../02-system/system-design.md)
- 状态归属与路由索引：[`../02-system/state-and-routing.md`](../02-system/state-and-routing.md)
- 连接建立、恢复与断连：[`../02-system/connection-lifecycle.md`](../02-system/connection-lifecycle.md)
- 发布、订阅与消息投递：[`../02-system/message-delivery.md`](../02-system/message-delivery.md)
- 特性完成度：[`mqtt5-feature-matrix.md`](mqtt5-feature-matrix.md)
- 路线图与当前活跃里程碑：[`roadmap.md`](roadmap.md)
- 历史阶段验收：[`../99-archive/README.md`](../99-archive/README.md)

## 当前主要缺口

- 基础鉴权、观测、运维和恢复能力尚未进入实现阶段。

## 下一阶段入口

`M3` 的下一步重点从“内部基础设施升级”转向“协议完整性与运维补全”：MQTT 5 关键属性、基础鉴权和可观测性。

当前建议按 [`roadmap.md`](roadmap.md) 中的 `M3` 执行表推进并逐项更新状态。
