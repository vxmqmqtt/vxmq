# PUBLISH 流程

本文档描述 Broker 对入站 PUBLISH 的长期处理规则。当前完成度看 [`../01-status/mqtt5-feature-matrix.md`](../01-status/mqtt5-feature-matrix.md)。

## 目的与范围

- 接收并校验入站 PUBLISH
- 根据 Topic Name 查找命中订阅者
- 对在线订阅者执行消息投递

## 输入报文与关键字段

- `topicName`
- `qos`
- `retain`
- `dup`
- `payload`

## broker 处理流程

1. `transport` 将入站报文转换为 `PublishRequest`
2. `protocol` 校验 Topic Name 与当前支持的 QoS
3. `protocol` 通过 `routing` 解析命中订阅集合
4. `protocol` 返回逻辑投递目标
5. `transport` 重新按当前活跃连接索引查找在线 endpoint，并执行实际出站写回

## 成功路径

- Topic Name 合法
- QoS 在当前实现范围内
- 命中订阅者收到消息
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
- 差异主要体现在异常断连的 reason code 表达上

## 投递规则

- 只有当前仍处于活跃状态的连接会收到消息
- 若目标会话当前无在线连接，当前实现直接跳过，不做离线消息暂存
- 若发布者同时也是订阅者，当前允许收到自己发布的消息
- 同一客户端命中重叠订阅时，当前只投递一次

## 当前实现边界

- 当前仅支持入站 QoS 0
- 当前未实现 Retained Message
- 当前未实现离线消息积压与恢复
- 当前未实现 QoS 1 / QoS 2 的入站与出站状态机
