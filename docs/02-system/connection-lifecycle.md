# 连接生命周期

本文档定义 Broker 对连接建立、会话恢复、断连与关闭路径的长期协议行为。它回答“CONNECT 之后会发生什么、会话如何延续、哪些关闭路径会触发什么后果”。

边界：本文描述 Broker 对 MQTT 连接相关报文和生命周期语义的外部行为；内部模块职责和状态归属见 [`system-design.md`](system-design.md) 与 [`state-and-routing.md`](state-and-routing.md)。

## 目的与范围

- 统一 CONNECT、CONNACK、会话恢复和连接接管语义。
- 统一显式断连、异常关闭、Keep Alive 超时和连接接管的关闭语义。
- 明确 MQTT 3.1.1 与 MQTT 5 的关键差异。

## 输入字段

CONNECT 处理当前重点关注：

- 协议版本
- `clientId`
- MQTT 3.1.1 `Clean Session`
- MQTT 5 `Clean Start`
- MQTT 5 `Session Expiry Interval`
- Keep Alive
- 用户名 / 密码
- 基础 Will Message

## 连接建立主线

1. `transport` 接收 CONNECT 并转换为 `ConnectRequest`。
2. `protocol` 校验协议版本、连接参数和 `clientId` 规则。
3. `authn` 对连接执行认证链检查；没有启用认证资源时默认放行，配置 static username/password backend 时校验用户名与密码。
4. MQTT 5 CONNECT `User Property` 会随 `ConnectRequest` 提供给认证扩展点，供后续认证 backend 使用。
5. 若 CONNECT 携带 will，`authz` 在会话状态写入前检查该 will topic 的 publish 权限；当前默认 authorizer 放行。
6. `protocol` 解析当前请求是新建会话、恢复现有会话还是接管旧连接。
7. `session` 更新会话归属，`connectionRegistry` 更新活跃连接索引。
8. `transport` 返回 CONNACK，并在必要时关闭旧连接。

## 会话恢复语义

### MQTT 3.1.1

- `Clean Session=true`
  - 总是新建临时会话，不恢复旧的订阅和离线 QoS 1 状态。
- `Clean Session=false`
  - 若存在持久会话，则恢复该会话的订阅、离线队列与 inflight 状态。

### MQTT 5

- `Clean Start=true`
  - 放弃当前 `clientId` 旧连接上的在线关联，并按 `Session Expiry Interval` 决定新会话是否持久。
- `Clean Start=false`
  - 若存在未过期会话，则恢复该会话；否则新建会话。

### 当前实现结果

- 当前持久会话可恢复订阅，并可恢复离线 QoS 1 消息。
- 会话过期采用懒清理，不引入后台扫描器。
- 基础 Will Message 会在 CONNECT 时提取并保存。

## 连接接管

- 当新的连接使用已占用的 `clientId` 成功接入时，Broker 会接管旧连接。
- MQTT 5 对旧连接使用 `DISCONNECT(Session taken over)`。
- MQTT 3.1.1 直接关闭旧连接。
- 若旧连接带有仍然生效的 will，旧连接关闭时仍可能触发该连接快照中的 will 发布。

## 成功路径

- CONNECT 字段合法，且协议版本在当前支持范围内。
- `clientId` 按协议版本规则通过校验或自动分配。
- 会话被正确新建、恢复或接管。
- `transport` 返回正确版本的 CONNACK；MQTT 5 会声明当前 Broker 的 `Receive Maximum` 与 `Maximum Packet Size`。

## 失败路径

- 协议版本不支持：
  - MQTT 5 使用对应 reason code 断连。
  - MQTT 3.1.1 直接关闭连接。
- `clientId` 非法或字段违反当前支持边界：
  - MQTT 5 使用显式断连 reason code。
  - MQTT 3.1.1 直接关闭连接。
- 入站 MQTT 5 PUBLISH 超过 Broker `Maximum Packet Size`：
  - 返回 `DISCONNECT(PACKET_TOO_LARGE)`。
- 发生内部异常：
  - 记录协议告警并关闭当前连接。

## 关闭路径

### 显式 DISCONNECT

- 当前连接从活跃连接索引中移除。
- 会话按持久策略转为离线或被删除。
- 当前连接和会话上的 will 会被清除，不再发布。

### 网络异常关闭 / Keep Alive 超时 / 协议错误断连

- 当前连接从活跃连接索引中移除。
- 会话按持久策略决定保留或删除。
- 若会话或连接上存在生效中的 will，则会触发 will 发布。
- 若存在未确认的 QoS 1 inflight 消息，这些消息会回退为离线队列。

### 连接接管导致的旧连接关闭

- 旧连接从活跃索引中移除。
- 新连接成为当前 `clientId` 的活跃连接。
- 若旧连接上的 will 仍处于生效状态，旧连接关闭路径会按异常关闭语义触发 will。

## Keep Alive

- 当前 Keep Alive 超时探测依赖 `vertx-mqtt` 内置处理，不重复实现第二套定时逻辑。
- 超时后进入异常关闭路径，而不是显式断连路径。

## 当前实现边界

- 当前实现是单机、内存态连接生命周期模型。
- 当前已支持持久会话恢复、会话懒清理、连接接管和基础 Will Message 保存。
- 当前支持配置驱动的 static username/password 认证，以及 CONNECT Will 的鉴权链接入；当前鉴权规则默认放行。
- 当前不支持 TLS、外部认证 backend、实际 ACL 规则、`Will Delay Interval`、高级 MQTT 5 认证流程和跨重启会话恢复。
