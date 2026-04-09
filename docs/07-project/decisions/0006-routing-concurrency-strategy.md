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

### 为什么最终结论和最初的直觉不完全一致

- `single-owner RoutingVerticle` 确实是 Vert.x 技术栈下非常正统、也非常稳妥的并发解决方案。
- 它没有胜出，不是因为方案错误，而是因为 routing 这个模块的工作负载更特殊：它是 **进程内高频读热路径**，不是一般的 owner-state 组件。
- 对 routing 而言，`match()` 的调用频率和热度远高于 `add/remove`，所以读路径的固定额外成本会被持续放大。
- 因此，这次结论不是“Verticle 不适合解决并发问题”，而是“Verticle 不一定是订阅树热路径的最佳并发安全方案”。

### 为什么首选 `snapshot`

- 对 broker 最关键的热路径是 `match()`，不是 `add/remove`
- 原型结果显示 `snapshot` 在并发 read-heavy 场景下吞吐显著领先
- `snapshot` 保持了进程内直接调用模型，不需要把 routing 全面异步化
- 相比 `RoutingVerticle`，它更适合 routing 这种“高频匹配、较低频更新”的组件
- `snapshot` 的原理是：读路径只读取一次当前树根快照，并在不可变树上遍历；写路径通过路径复制构造新树根，再以原子替换方式发布新快照。
- 这等于用写放大换读路径轻量化，正好贴合订阅树“read-heavy”的真实工作负载。

### 为什么不把 `RoutingVerticle` 作为 routing 首选

- 它确实是 Vert.x 风格下非常正统的 single-owner 方案
- 也确实解决了共享可变状态的正确性问题
- 但 routing 是 broker 的匹配热路径，跨 context hop 的固定成本在原型里已经比较明显
- 在当前数据下，它没有表现出足以覆盖这层成本的吞吐优势
- 这里的“跨 context hop 的固定成本”指：
  - 调用方不能直接方法调用
  - 必须把 routing 操作投递到 owner context / event loop
  - 需要排队、调度，再通过 `Future / Promise` 或等价手段把结果交还给调用方
  - 即使单次匹配逻辑本身很轻，这笔调度成本也会在每次 `match()` 上发生

### 为什么 `snapshot` 不是“完美方案”

- `snapshot` 这次胜出，核心原因是它更值得推进到主线，而不是它已经没有明显短板。
- 当前原型已经暴露出写路径成本很高、写放大明显的问题。
- 因此本轮决策是“先把它作为主线并发安全方案落地”，而不是“它已经完成最终优化”。

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
- 评估代码会保留为独立评估套件，不再混入默认回归路径。

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
