# ADR-0006 订阅树并发安全策略

- 状态：已接受
- 日期：2026-04-08

## 背景

`M2` 已完成订阅树 / 路由索引重构，但当前主线里的 `InMemorySubscriptionRegistry` 仍然是共享可变树：

- `SubscriptionRegistry` 仍是同步接口
- `InMemorySubscriptionRegistry` 是共享 CDI 单例
- 当前没有把 routing 访问显式串行化到单一 owner context
- 当前也没有在线程安全层面对共享订阅树做保护

这意味着当前主线从共享对象视角看并不具备并发读写安全。

在 `vxmq` 里，这个问题必须按核心基础设施处理，因为订阅树直接影响：

- PUBLISH 命中订阅者的热路径性能
- SUBSCRIBE / UNSUBSCRIBE 的更新一致性
- 后续 `Subscription Options`、`Shared Subscription` 等能力的扩展边界

## 候选方案

本次评估对比了 3 类方案：

1. 共享单例 + `synchronized` 串行化
2. `snapshot / copy-on-write` 订阅树
3. `single-owner RoutingVerticle`

其中：

- `synchronized` 作为保守基线，优点是简单、同步接口不变，但会把锁放进热路径
- `snapshot` 通过不可变树根替换换取近似无锁读路径，代价是写放大和更复杂的内部实现
- `RoutingVerticle` 通过单 owner 串行化获得最清晰的并发语义，但会把 routing 调用转换为跨 context hop

## 原型验证结果

验证方法：

- 用统一 correctness harness 验证 MQTT Topic Filter 语义、重叠订阅去重和 QoS 选择规则
- 用统一 benchmark harness 比较顺序匹配、更新成本和“高频 match + 少量 churn”的并发场景
- 当前 benchmark 是项目内 repeatable harness，不是 JMH；结论看数量级与相对差异，不把单次绝对数字当作最终容量承诺

一次代表性 benchmark 输出如下：

| 方案 | 顺序 `exact` 匹配 | 顺序 `mixed` 匹配 | 更新成本（add/remove） | 并发 read-heavy 吞吐 |
| --- | ---: | ---: | ---: | ---: |
| `synchronized-tree` | `52,375ns` | `91,616,125ns` | `33,110,334ns / 81,932,167ns` | `17,436 ops/s` |
| `snapshot-tree` | `345,792ns` | `81,038,625ns` | `40,924,216,083ns / 81,226,957,750ns` | `49,374 ops/s` |
| `verticle-owner` | `1,638,458ns` | `95,985,542ns` | `1,882,406,625ns / 3,029,054,125ns` | `16,352 ops/s` |

并发 read-heavy 场景的延迟样本：

- `synchronized-tree`: `p50=58,292ns`, `p95=82,334ns`, `p99=159,208ns`
- `snapshot-tree`: `p50=62,375ns`, `p95=172,750ns`, `p99=602,167ns`
- `verticle-owner`: `p50=258,209ns`, `p95=346,958ns`, `p99=457,875ns`

## 决策

下一步 routing 并发安全加固的**首选方向**定为：

**`snapshot / copy-on-write` 订阅树**

同时明确：

- `RoutingVerticle` 不是 routing 热路径的默认答案
- `synchronized` 仅作为保守兜底方案，不作为长期目标

## 原因

### 为什么首选 `snapshot`

- 对 broker 最关键的热路径是 `match()`，不是 `add/remove`
- 原型结果显示 `snapshot` 在并发 read-heavy 场景下吞吐显著领先
- `snapshot` 保持了进程内直接调用模型，不需要把 routing 全面异步化
- 相比 `RoutingVerticle`，它更适合 routing 这种“高频匹配、较低频更新”的组件

### 为什么不把 `RoutingVerticle` 作为 routing 首选

- 它确实是 Vert.x 风格下非常正统的 single-owner 方案
- 也确实解决了共享可变状态的正确性问题
- 但 routing 是 broker 的匹配热路径，跨 context hop 的固定成本在原型里已经比较明显
- 在当前数据下，它没有表现出足以覆盖这层成本的吞吐优势

### 为什么不把 `synchronized` 作为长期目标

- 它是很好的保守基线，也容易落地
- 但热路径锁竞争会限制后续吞吐上限
- 对未来更复杂的 subscription metadata 来说，锁模型会继续扩大成本

## 结果

- routing 并发安全的正式实现，应围绕不可变节点 / 根替换模型展开
- 后续进入生产实现时，需要把当前测试原型提升为主线实现，并继续做：
  - 内存分配与写放大优化
  - 更稳定的延迟观测
  - retained / will / session cleanup 的回归验证
- `Verticle` 在本项目中的结论不是“无用”，而是：
  - **适合需要 single-owner 状态机的模块**
  - **不建议直接作为 routing 热路径的首选并发安全方案**

## 不采纳方案

### 直接把 routing 收敛为 single-owner `RoutingVerticle`

当前不采纳为 routing 主方案，原因：

- 匹配热路径需要承担 owner hop 成本
- 原型没有体现出足够性能收益
- 会显著推动 `ProtocolEngine -> routing` 接口异步化，但这不是当前 routing 最核心的收益点

### 共享单例 + `synchronized`

当前不采纳为长期方案，原因：

- 更像最小修补，不像最终架构
- 未来复杂 metadata 进入热路径后，锁竞争风险会继续扩大

## 参考依据

- Vert.x Core Manual: https://vertx.io/docs/vertx-core/java/index.html
- Quarkus Vert.x Reference Guide: https://quarkus.io/guides/vertx-reference
