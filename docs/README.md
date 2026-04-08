# 文档导航

`docs/` 是项目文档的唯一入口。这里的文档按三种职责组织：

- `规范真相`：长期稳定、指导实现的文档
- `当前状态`：集中承载项目目前进行到哪里
- `历史记录`：ADR、阶段验收等不可替代的留痕文档

## 推荐阅读顺序

1. [`00-foundation/vision.md`](00-foundation/vision.md)
2. [`00-foundation/scope.md`](00-foundation/scope.md)
3. [`00-foundation/compatibility.md`](00-foundation/compatibility.md)
4. [`01-status/current-status.md`](01-status/current-status.md)
5. [`01-status/mqtt5-feature-matrix.md`](01-status/mqtt5-feature-matrix.md)
6. [`02-architecture/architecture-overview.md`](02-architecture/architecture-overview.md)
7. [`02-architecture/session-model.md`](02-architecture/session-model.md)
8. [`02-architecture/offline-message-model.md`](02-architecture/offline-message-model.md)
9. [`02-architecture/retained-message-model.md`](02-architecture/retained-message-model.md)
10. [`02-architecture/will-message-model.md`](02-architecture/will-message-model.md)
11. [`02-architecture/subscription-tree-model.md`](02-architecture/subscription-tree-model.md)
12. [`03-protocol/connect-flow.md`](03-protocol/connect-flow.md)
13. [`03-protocol/session-lifecycle.md`](03-protocol/session-lifecycle.md)
14. [`03-protocol/qos1-flow.md`](03-protocol/qos1-flow.md)
15. [`03-protocol/retain-flow.md`](03-protocol/retain-flow.md)
16. [`03-protocol/will-flow.md`](03-protocol/will-flow.md)
17. [`03-protocol/topic-match-flow.md`](03-protocol/topic-match-flow.md)
18. [`07-project/collaboration.md`](07-project/collaboration.md)

## 目录说明

### `00-foundation`

项目最稳定的基础文档：

- 愿景
- 范围
- 协议兼容原则
- 术语表

### `01-status`

项目状态与阶段规划的集中入口：

- 当前状态
- 特性矩阵
- 里程碑规划
- 已完成阶段的历史验收

### `02-architecture`

系统整体架构、模块边界、会话模型、离线消息模型、Retained Message 模型、Will Message 模型、订阅树模型和 Topic 路由设计。

### `03-protocol`

Broker 对 CONNECT、会话生命周期、QoS 1、Retained Message、Will Message、Topic 匹配、SUBSCRIBE / UNSUBSCRIBE、PUBLISH 的长期协议行为说明。

### `07-project`

协作规范与 ADR。ADR 属于历史决策记录，不替代稳定规范文档。

## 使用规则

- 当前状态只在 `01-status/` 集中维护，其他设计文档不重复写阶段总结。
- 设计文档优先追求长期稳定，不承载短期任务清单。
- 重要实现选择进入 `07-project/decisions/`。
