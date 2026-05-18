# 决策记录

`03-decisions/` 保存项目 ADR 和关键决策记录。这里的文档解释“为什么这样选”，不替代当前状态、系统真相或路线图。

## ADR 列表

- [`0001-protocol-compatibility-and-decision-policy.md`](0001-protocol-compatibility-and-decision-policy.md)
- [`0002-m1-client-id-policy.md`](0002-m1-client-id-policy.md)
- [`0003-m1-overlapping-subscription-delivery.md`](0003-m1-overlapping-subscription-delivery.md)
- [`0004-m1-transport-stack-vertx-mqtt.md`](0004-m1-transport-stack-vertx-mqtt.md)
- [`0005-m1-reactive-event-loop-model.md`](0005-m1-reactive-event-loop-model.md)
- [`0006-routing-concurrency-strategy.md`](0006-routing-concurrency-strategy.md)

## 使用规则

- 新增 ADR 前先确认该问题确实是长期决策，而不是当前任务清单或临时状态。
- ADR 应记录背景、决策、理由、影响和被拒绝的替代方案。
- 若 ADR 与当前活跃文档冲突，应更新活跃文档并在 ADR 中补充后续决策，而不是改写历史。
