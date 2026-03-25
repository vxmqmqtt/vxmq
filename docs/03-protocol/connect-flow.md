# CONNECT 流程

本文档描述 Broker 对 CONNECT 的长期处理规则。当前完成度看 [`../01-status/mqtt5-feature-matrix.md`](../01-status/mqtt5-feature-matrix.md)。

## 目的与范围

- 接收并校验 CONNECT
- 建立 `clientId` 与连接的关系
- 处理重复 `clientId` 的连接接管
- 返回 CONNACK 或拒绝连接

## 输入报文与关键字段

- `protocolName`
- `protocolVersion`
- `clientId`
- `cleanSession` / `cleanStart`
- 用户名密码
- Keep Alive

## broker 处理流程

1. `transport` 从 `MqttEndpoint` 提取 CONNECT 字段并构造 `ConnectRequest`
2. `protocol` 校验协议名和协议版本
3. `protocol` 调用鉴权扩展点
4. `protocol` 解析有效 `clientId`
5. `protocol` 更新连接状态、绑定 `clientId`、计算是否存在被接管的旧连接
6. `transport` 根据协议结果返回 CONNACK，必要时关闭旧连接

## 成功路径

- 连接被接受
- 连接进入 `CONNECTED`
- `clientId` 绑定到当前活跃连接
- 当前会话视图绑定到新连接
- MQTT 5 且 broker 自动分配 `clientId` 时，在 CONNACK 中返回 `Assigned Client Identifier`

## 失败路径

- 协议名或协议版本不支持：拒绝连接
- `clientId` 不满足当前版本约束：拒绝连接
- 鉴权失败：拒绝连接
- 内部异常：关闭连接并记录告警

## 协议版本差异

### MQTT 3.1.1

- 空 `clientId` 仅在 `cleanSession=true` 时可被 broker 自动分配
- 重复 `clientId` 接管时，旧连接直接关闭

### MQTT 5

- 空 `clientId` 可由 broker 自动分配
- broker 自动分配 `clientId` 时，在 CONNACK 中返回 `Assigned Client Identifier`
- 重复 `clientId` 接管时，旧连接使用 `DISCONNECT(Session taken over)` 后关闭

## Keep Alive

- Keep Alive 数值从 CONNECT 中读取
- PINGREQ / PINGRESP 与空闲超时关闭由 `vertx-mqtt` 内置机制处理
- Broker 不重复实现第二套 Keep Alive 定时逻辑

## 当前实现边界

- 当前未实现 Clean Start / Session Expiry 的完整语义
- 当前未实现 MQTT 5 更多 CONNECT / CONNACK 属性
- 当前会话绑定仍是内存态单机实现
