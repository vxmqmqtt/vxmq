# 当前状态

本文档是项目当前状态的唯一集中入口。当前阶段、能力边界、当前缺口和下一步优先级统一在这里维护。

## 当前阶段

项目当前处于：`M1 已完成，M2 已完成，M3 已完成，M4 待启动`

## 当前已完成能力

- CONNECT / CONNACK 基础处理
- MQTT 3.1.1 与 MQTT 5 的基础连接差异处理
- 空 `clientId` 自动分配与连接接管
- MQTT 3.1.1 `Clean Session`
- MQTT 5 `Clean Start / Session Expiry`
- 持久会话订阅恢复与会话懒清理
- MQTT 5 Subscription Options
- MQTT 5 Subscription Identifier
- MQTT 5 CONNECT / SUBSCRIBE / UNSUBSCRIBE request properties 建模
- MQTT 5 PUBLISH / Will User Property 透传
- MQTT 5 PUBLISH Message Expiry Interval
- MQTT 5 PUBLISH / Will Payload Format Indicator 与 Content Type 纯透传
- MQTT 5 Receive Maximum 基础流控
- MQTT 5 Maximum Packet Size 基础限制
- 配置驱动的 static username/password 客户端认证
- CONNECT Will / SUBSCRIBE / PUBLISH 鉴权链接入，当前默认放行
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
- Broker Readiness / Liveness 健康检查
- Broker runtime、连接、会话、订阅、消息路由和协议告警 metrics
- CONNECT / SUBSCRIBE / UNSUBSCRIBE / PUBLISH 拒绝、断连、关闭和投递失败的结构化诊断日志
- 单元测试与真实 MQTT 集成测试闭环

## 当前代码实现边界

- 当前实现是单机、内存态 Broker。
- 当前主链路已覆盖 QoS 0 / QoS 1 / QoS 2；QoS 2 支持普通发布和 retained 重放，will QoS 2 延后。
- 当前已实现会话过期的懒清理、持久会话订阅恢复、离线 QoS 1 消息恢复、Retained Message、基础 Will Message、订阅树路由索引、Subscription Options、Subscription Identifier、CONNECT / SUBSCRIBE / UNSUBSCRIBE request properties 建模、PUBLISH / Will User Property 透传、PUBLISH Message Expiry Interval、PUBLISH / Will Payload Format Indicator 与 Content Type 纯透传、Receive Maximum 基础流控、Maximum Packet Size 基础限制、配置驱动 static username/password 认证、CONNECT Will / SUBSCRIBE / PUBLISH 鉴权链接入、严格 Broker 语义的 readiness/liveness 健康检查、Prometheus metrics 以及结构化诊断日志；尚未实现 TLS、外部认证 backend、实际 ACL 规则、持久化和重启恢复能力。
- 健康检查通过 Quarkus `/q/health/live` 和 `/q/health/ready` 暴露；`vxmq.broker.enabled=false` 或 MQTT transport 未监听时 readiness 为 DOWN，liveness 只在 broker runtime state 进入 `FAILED` 时为 DOWN。
- Metrics 通过 Quarkus `/q/metrics` 暴露低基数 Broker 指标；消息速率由 Prometheus `rate(vxmq_messages_routed_total[1m])` 在查询层计算。
- 诊断日志使用稳定 `key=value` 字段，覆盖关键协议拒绝、断连、关闭与投递失败路径；不输出密码、payload、correlation data 或 user properties。
- 当前路由和会话状态均为内存实现，不具备持久化和重启恢复能力。
- 当前主线订阅树已采用 `snapshot / copy-on-write` 方案完成并发安全落地，并加入了更紧凑的不可变节点表示与内部 batch snapshot 重建路径；评估候选与 benchmark harness 作为独立评估套件保留。

## 当前文档真相入口

- 项目定位与范围：[`../00-foundation/project.md`](../00-foundation/project.md)
- 协议兼容策略：[`../00-foundation/compatibility.md`](../00-foundation/compatibility.md)
- 系统设计与模块边界：[`../02-system/system-design.md`](../02-system/system-design.md)
- 状态归属与路由索引：[`../02-system/state-and-routing.md`](../02-system/state-and-routing.md)
- 连接建立、恢复与断连：[`../02-system/connection-lifecycle.md`](../02-system/connection-lifecycle.md)
- 发布、订阅与消息投递：[`../02-system/message-delivery.md`](../02-system/message-delivery.md)
- 运行诊断与排障入口：[`../02-system/operations-diagnostics.md`](../02-system/operations-diagnostics.md)
- 特性完成度：[`mqtt5-feature-matrix.md`](mqtt5-feature-matrix.md)
- 路线图与当前活跃里程碑：[`roadmap.md`](roadmap.md)
- 历史阶段验收：[`../99-archive/README.md`](../99-archive/README.md)

## 当前主要缺口

- 实际 ACL 鉴权规则、外部认证 backend、TLS、持久化、跨重启恢复、互操作性验证和性能基线尚未进入实现阶段。

## 下一阶段入口

`M4` 的下一步重点转向“持久化与验证闭环”：建立持久化策略首版、跨重启恢复行为、固定外部互操作性验证和可复现性能基线。

当前建议按 [`roadmap.md`](roadmap.md) 中的 `M4` 目标拆分下一批可执行条目，并继续在 [`mqtt5-feature-matrix.md`](mqtt5-feature-matrix.md) 同步能力状态。
