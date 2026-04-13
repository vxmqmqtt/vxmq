# PUBLISH 流程

本文档描述 Broker 对入站 PUBLISH 的长期处理规则。当前完成度看 [`../01-status/mqtt5-feature-matrix.md`](../01-status/mqtt5-feature-matrix.md)。

## 目的与范围

- 接收并校验入站 PUBLISH
- 处理 Retained Message 的写入、覆盖与清除
- 根据 Topic Name 查找命中订阅者
- 对在线订阅者执行消息投递
- 对离线持久会话执行 QoS 1 离线积压与重连恢复

## 输入报文与关键字段

- `topicName`
- `qos`
- `packetId`
- `retain`
- `dup`
- `payload`

## broker 处理流程

1. `transport` 将入站报文转换为 `PublishRequest`
2. `protocol` 校验 Topic Name 与当前支持的 QoS
3. `protocol` 通过 `routing` 解析命中订阅集合
4. `protocol` 决定每个目标是“在线立即投递 / 离线入队 / 跳过”
5. `transport` 重新按当前活跃连接索引查找在线 endpoint，并执行实际出站写回
6. QoS 1 场景下，`transport` 负责回 `PUBACK` 并接收订阅端 `PUBACK`

## 成功路径

- Topic Name 合法
- QoS 在当前实现范围内
- `retain=true` 时，retained store 按规则完成写入、覆盖或清除
- 命中订阅者收到消息，或对离线持久会话完成入队
- 单个目标写失败不会回滚整个发布结果

## 失败路径

- Topic Name 非法：
  - MQTT 5 使用 `DISCONNECT(TOPIC_NAME_INVALID)`
  - MQTT 3.1.1 直接关闭连接
- QoS 不支持：
  - MQTT 5 使用 `DISCONNECT(QOS_NOT_SUPPORTED)`
  - MQTT 3.1.1 直接关闭连接
- 路由或出站过程中出现内部异常：记录协议告警

## 协议版本差异

- 当前 MQTT 3.1.1 与 MQTT 5 的入站 PUBLISH 主链路基本一致
- MQTT 5 在当前实现中会返回 `PUBACK(Success)`；旧版本返回基础 `PUBACK`
- 其他差异主要体现在异常断连的 reason code 表达上

## 投递规则

- 只有当前仍处于活跃状态的连接会收到消息
- 若目标会话当前无在线连接且为持久会话，最终投递 QoS 为 1 的消息会进入离线队列
- 若目标会话当前无在线连接且为非持久会话，当前直接跳过
- 若发布者同时也是订阅者，当前允许收到自己发布的消息
- 同一客户端命中重叠订阅时，当前只投递一次

## 当前实现边界

- 当前支持入站 QoS 0 / QoS 1
- 当前支持出站 QoS 0 / QoS 1
- 当前已实现 Retained Message 的写入、清除与订阅后下发
- 当前已实现离线 QoS 1 积压与重连恢复
- 当前仍未实现 QoS 2 的入站与出站状态机
