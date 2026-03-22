# 当前状态

本文档是项目当前状态的唯一集中入口。除里程碑文档和历史验收文档外，其他设计文档不再重复维护阶段结论。

## 当前阶段

项目当前处于：`M1 已完成，M2 尚未开始`

## 当前已完成能力

- CONNECT / CONNACK 基础处理
- MQTT 3.1.1 与 MQTT 5 的基础连接差异处理
- 空 `clientId` 自动分配与连接接管
- SUBSCRIBE / SUBACK
- UNSUBSCRIBE / UNSUBACK
- PUBLISH QoS 0 主链路
- Topic Filter / Wildcard 匹配
- 基础断连语义
- 基于 `vertx-mqtt` 的 Keep Alive 超时处理
- 单元测试与真实 MQTT 集成测试闭环

## 当前代码实现边界

- 当前实现是单机、内存态 Broker。
- 当前主链路聚焦 QoS 0，不支持 QoS 1 / QoS 2 的完整状态机。
- 当前尚未实现会话过期、离线消息恢复、Retained Message、Will Message、用户名密码鉴权和 TLS。
- 当前路由和会话状态均为内存实现，不具备持久化和重启恢复能力。

## 当前文档真相入口

- 项目目标与边界：[`../00-foundation/vision.md`](../00-foundation/vision.md)、[`../00-foundation/scope.md`](../00-foundation/scope.md)
- 协议兼容策略：[`../00-foundation/compatibility.md`](../00-foundation/compatibility.md)
- 特性完成度：[`mqtt5-feature-matrix.md`](mqtt5-feature-matrix.md)
- 阶段规划：[`milestones.md`](milestones.md)
- 当前已完成阶段的历史验收：[`m1-acceptance-checklist.md`](m1-acceptance-checklist.md)

## 当前主要缺口

- 会话状态尚未从“绑定连接 + 订阅集合”扩展到完整 MQTT 会话语义。
- QoS 1 / QoS 2 状态机尚未进入设计与实现。
- Retained Message、Will Message、Session Expiry 等关键可靠性语义尚未落地。
- 基础鉴权、观测、运维和恢复能力尚未进入实现阶段。

## 下一阶段入口

下一阶段为 `M2`，重点是会话状态与可靠性语义。进入 `M2` 前，应以当前文档结构为基础继续补充长期稳定的设计文档，而不是回到“把阶段状态写进每一份设计文档”的做法。
