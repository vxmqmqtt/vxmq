# ADR-0002 M1 阶段的 Client Identifier 策略

- 状态：已接受
- 日期：2026-03-14

## 背景

此前 `connect-flow.md` 中给出的建议是 `M1` 要求客户端显式提供 `clientId`。该建议有利于降低初期复杂度，但与“严格遵循 MQTT 3.1.1 / MQTT 5 协议”的目标冲突。

根据 OASIS MQTT 规范：

- MQTT 3.1.1 允许服务端接受零长度 `ClientId`，如果接受，必须为其分配唯一标识；若零长度 `ClientId` 与 `Clean Session = 0` 同时出现，则必须拒绝。
- MQTT 5 同样允许服务端接受零长度 `ClientID`，如果接受，必须分配唯一标识，并在 CONNACK 中返回 `Assigned Client Identifier`。

因此，“一律要求显式 `clientId`”不应作为项目默认策略。

## 决策

`M1` 对 `clientId` 采用按协议版本区分的策略：

1. MQTT 3.1.1
   - 非空 `clientId`：正常处理。
   - 空 `clientId` + `Clean Session = 1`：接受连接，服务端生成唯一 `clientId`。
   - 空 `clientId` + `Clean Session = 0`：返回 `0x02 Identifier rejected`，然后关闭连接。
2. MQTT 5
   - 非空 `clientId`：正常处理。
   - 空 `clientId`：接受连接，服务端生成唯一 `clientId`，并在 CONNACK 中返回 `Assigned Client Identifier`。
3. 无论协议版本如何，重复 `clientId` 连接仍执行“旧连接让位给新连接”的单活策略。

## 原因

- 该策略满足协议兼容性目标。
- 生成 `clientId` 的实现复杂度可控，不应成为阻塞项。
- 这比“一刀切拒绝空 `clientId`”更符合常见 MQTT Broker 的实际兼容性预期。

## 结果

- `connect-flow.md` 需要改为版本差异化处理，而不是默认要求显式 `clientId`。
- `connection` 与 `session` 层需要支持服务端生成 `clientId`。
- MQTT 5 的 CONNACK 构造要预留 `Assigned Client Identifier`。

## 参考依据

- OASIS MQTT 3.1.1 `MQTT-3.1.3-6` 至 `MQTT-3.1.3-9`
- OASIS MQTT 5 `MQTT-3.1.3-6` 至 `MQTT-3.1.3-8`
- OASIS MQTT 5 `MQTT-3.2.2-16`
