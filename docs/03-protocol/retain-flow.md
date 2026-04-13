# Retained Message 流程

本文档描述 Broker 对 Retained Message 的长期处理规则。当前完成度看 [`../01-status/mqtt5-feature-matrix.md`](../01-status/mqtt5-feature-matrix.md)。

## 目的与范围

- 在入站 PUBLISH 中写入、覆盖或清除 retained 记录
- 在 SUBSCRIBE 成功后立即下发匹配的 retained 消息
- 与现有 QoS 0 / QoS 1 路径保持一致

## 发布路径

### 写入 retained

- 条件：`retain=true` 且 payload 非空
- 处理：
  - 按 Topic Name 写入或覆盖 retained store
  - 本次发布仍继续走普通在线投递 / 离线 QoS 1 入队路径

### 清除 retained

- 条件：`retain=true` 且 payload 为空
- 处理：
  - 删除该 Topic Name 的 retained 记录
  - 本次发布本身仍作为一条正常消息继续向当前命中订阅者下发，`retain=true`

## 订阅路径

1. `transport` 接收 SUBSCRIBE 并调用 `protocol`
2. `protocol` 更新 `session` 与 `routing`
3. 对每个成功注册的 Topic Filter 查询 retained store
4. 生成 retained deliveries
5. `transport` 先发送 SUBACK，再发送 retained deliveries

## QoS 规则

- retained store 保存原始 retained 发布的 QoS
- 订阅后下发 QoS 取 `min(retained.qos, grantedQos)`
- 若最终下发 QoS 为 0，则直接即时发送
- 若最终下发 QoS 为 1，则进入当前订阅者会话的 inflight 路径，等待 `PUBACK`

## 协议版本差异

- 当前 MQTT 3.1.1 与 MQTT 5 的 retained 基础语义一致
- 当前差异主要仍体现在异常断连与 reason code 表达，不体现在 retained 主流程
- MQTT 5 的 `Retain Handling`、`Retain Available` 当前未实现

## 当前实现边界

- 当前支持 retained 写入、覆盖、清除与订阅后下发
- 当前 retained store 为单机、内存态
- 当前不支持 retained 跨重启恢复
- 当前不支持 retained 相关高级 MQTT 5 属性
