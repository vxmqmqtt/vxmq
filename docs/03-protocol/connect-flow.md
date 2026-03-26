# CONNECT 流程

本文档描述 Broker 对 CONNECT 的长期处理规则。当前完成度看 [`../01-status/mqtt5-feature-matrix.md`](../01-status/mqtt5-feature-matrix.md)。

## 目的与范围

- 接收并校验 CONNECT
- 建立 `clientId` 与连接的关系
- 打开、恢复或替换会话
- 处理重复 `clientId` 的连接接管
- 返回 CONNACK 或拒绝连接

## 输入报文与关键字段

- `protocolName`
- `protocolVersion`
- `clientId`
- `cleanSession` / `cleanStart`
- `Session Expiry Interval`
- 用户名密码
- Keep Alive

## broker 处理流程

1. `transport` 从 `MqttEndpoint` 提取 CONNECT 字段并构造 `ConnectRequest`
2. `protocol` 校验协议名和协议版本
3. `protocol` 调用鉴权扩展点
4. `protocol` 解析有效 `clientId`
5. `protocol` 根据 MQTT 版本和连接参数决定“新建会话 / 恢复会话 / 删除旧会话后重建”
6. `protocol` 更新连接状态、绑定 `clientId`、计算是否存在被接管的旧连接
7. `transport` 根据协议结果返回 CONNACK，必要时关闭旧连接

## 成功路径

- 连接被接受
- 连接进入 `CONNECTED`
- `clientId` 绑定到当前活跃连接
- 当前会话被绑定到新连接
- `sessionPresent` 按“是否恢复既有会话”返回真实值
- MQTT 5 且 broker 自动分配 `clientId` 时，在 CONNACK 中返回 `Assigned Client Identifier`

## 失败路径

- 协议名或协议版本不支持：拒绝连接
- `clientId` 不满足当前版本约束：拒绝连接
- 鉴权失败：拒绝连接
- 内部异常：关闭连接并记录告警

## 协议版本差异

### MQTT 3.1.1

- 空 `clientId` 仅在 `cleanSession=true` 时可被 broker 自动分配
- `cleanSession=true` 时，每次连接都丢弃旧会话并新建非持久会话，`sessionPresent=false`
- `cleanSession=false` 时，优先恢复旧会话；只有恢复成功时 `sessionPresent=true`
- 重复 `clientId` 接管时，旧连接直接关闭

### MQTT 5

- 空 `clientId` 可由 broker 自动分配
- broker 自动分配 `clientId` 时，在 CONNACK 中返回 `Assigned Client Identifier`
- `cleanStart=true` 时，连接成功前先删除旧会话，再按本次 `Session Expiry Interval` 新建会话，`sessionPresent=false`
- `cleanStart=false` 时，优先恢复旧会话；只有恢复成功时 `sessionPresent=true`
- `Session Expiry Interval` 缺省值按 `0` 处理
- 重复 `clientId` 接管时，旧连接使用 `DISCONNECT(Session taken over)` 后关闭

## Keep Alive

- Keep Alive 数值从 CONNECT 中读取
- PINGREQ / PINGRESP 与空闲超时关闭由 `vertx-mqtt` 内置机制处理
- Broker 不重复实现第二套 Keep Alive 定时逻辑

## 当前实现边界

- 当前已实现 MQTT 3.1.1 `Clean Session` 与 MQTT 5 `Clean Start / Session Expiry` 的会话打开、恢复和懒清理语义
- 当前持久会话可恢复订阅，但不恢复离线消息
- 当前未实现 MQTT 5 更多 CONNECT / CONNACK 属性
- 当前会话绑定仍是内存态单机实现
