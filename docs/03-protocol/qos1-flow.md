# QoS 1 流程

本文档定义 Broker 当前对 QoS 1 的长期处理规则。它覆盖入站发布、在线出站、离线积压、重连恢复和 `PUBACK` 完成确认。

## 目的

- 明确 Broker 何时接受 QoS 1 入站发布
- 明确 QoS 1 在线投递与离线积压的分支
- 明确 `PUBACK` 在当前实现中的完成语义

## 入站 PUBLISH

- 当前 Broker 接受入站 QoS 0 和 QoS 1
- 入站 QoS 2 仍不支持
- Topic Name 非法或 QoS 超出当前实现范围时，仍按异常发布路径断开连接

### 成功路径

- MQTT 3.1.1：处理成功后返回 `PUBACK`
- MQTT 5：处理成功后返回 `PUBACK(Success)`

## 目标订阅者处理

### 在线订阅者

- 计算最终投递 QoS 为 `min(发布 QoS, 订阅授予 QoS)`
- 若最终投递 QoS 为 0，立即直接发送
- 若最终投递 QoS 为 1，先创建会话级 inflight 记录，再发送

### 离线持久会话

- 若最终投递 QoS 为 1，则入离线队列
- 若最终投递 QoS 为 0，则当前直接跳过，不做积压

### 离线非持久会话

- 当前不保留离线消息

## PUBACK 完成确认

- 订阅端收到 QoS 1 消息后，由 MQTT 客户端库自动回 `PUBACK`
- Broker 收到 `PUBACK` 后，从会话 inflight 集合中移除对应 packet id
- 当前阶段不做后台超时重试；若连接先关闭，则未确认 inflight 回退为离线队列

## 重连恢复

- 持久会话重连成功后，Broker 将离线队列按 FIFO 顺序恢复发送
- 恢复发送的消息进入 inflight 集合并重新分配 packet id
- 当前阶段不额外暴露“恢复批次”给客户端，恢复行为对客户端透明

## 当前阶段边界

当前已覆盖：

- 入站 QoS 1
- 在线 QoS 1 出站
- 离线 QoS 1 积压
- 重连恢复
- `PUBACK` 完成确认

当前仍未覆盖：

- QoS 1 定时重试
- DUP 优化策略
- Message Expiry Interval
- QoS 2
