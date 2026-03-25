# 协议兼容性要求

本文档定义项目在 MQTT 协议兼容性上的基本立场。具体实现进度见 [`../01-status/mqtt5-feature-matrix.md`](../01-status/mqtt5-feature-matrix.md)。

## 目标

- 严格遵循 MQTT 协议，同时支持 MQTT 3.1.1 与 MQTT 5。
- 对协议版本差异进行显式处理，不把单一版本行为误写成通用行为。
- 对协议未规定、允许多种实现或存在 `MAY` 行为的场景，先确认规范边界，再形成项目决策。

## 兼容性原则

- 规范优先：优先以 OASIS MQTT 3.1.1 与 MQTT 5 规范为准。
- 版本显式：凡是行为在 MQTT 3.1.1 与 MQTT 5 存在差异，文档和代码都必须显式区分。
- 决策留痕：一旦出现实现选择，需要记录到 ADR 或稳定设计文档中。
- 不做隐式降级：若阶段性不支持某能力，应明确写出限制，而不是让行为模糊存在。

## 决策优先级

1. OASIS MQTT 规范
2. 项目 ADR
3. 已确认的稳定设计文档
4. 同类产品的官方资料或实测行为

## 同类产品参考策略

当协议文本不足以直接给出唯一实现结论时，可参考 HiveMQ、EMQX、Mosquitto 等同类产品，但必须满足以下条件：

- 优先查官方文档、官方示例或可重复验证的行为。
- 参考结论不能覆盖规范文本。
- 一旦项目采纳某种实现方式，应形成 ADR 或明确写入稳定文档。

## 当前已确定的关键兼容性决策

- 同时支持 MQTT 3.1.1 与 MQTT 5。
- `M1` 主链路以 QoS 0 为实现边界，QoS 1/2 留待后续里程碑。
- 连接接管场景中，MQTT 5 使用 `DISCONNECT(Session taken over)`，MQTT 3.1.1 直接关闭连接。
- Keep Alive 超时依赖 `vertx-mqtt` 内置处理，不重复实现第二套定时逻辑。

## 关联文档

- [`../07-project/decisions/0001-protocol-compatibility-and-decision-policy.md`](../07-project/decisions/0001-protocol-compatibility-and-decision-policy.md)
- [`../07-project/decisions/0002-m1-client-id-policy.md`](../07-project/decisions/0002-m1-client-id-policy.md)
- [`../07-project/decisions/0003-m1-overlapping-subscription-delivery.md`](../07-project/decisions/0003-m1-overlapping-subscription-delivery.md)
