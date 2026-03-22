# CONNECT 流程

本文档定义 `M1` 阶段客户端建立连接的处理流程，覆盖：

- CONNECT 接入与校验。
- CONNACK 返回。
- 重复 `clientId` 处理。
- Keep Alive 初始化。
- 拒绝路径与异常断开。

## 目标

`M1` 的连接流程要保证两件事：

1. 协议行为正确，能稳定完成 MQTT 客户端接入。
2. 流程边界清晰，后续能自然扩展到 `M2` 的会话恢复和 `M3` 的鉴权增强。

## 输入与输出

输入：

- TCP 连接建立事件。
- 客户端发送的 CONNECT 报文。

输出：

- 成功时返回 CONNACK，并将连接置为 `CONNECTED`。
- 失败时返回适当错误响应或直接关闭连接。

## 前置约束

- 一个网络连接在收到合法 CONNECT 前，不应接收其他 MQTT 控制报文。
- 同一连接只允许一次成功 CONNECT。
- `M1` 默认不恢复持久会话，但必须为后续恢复流程预留位置。

## 流程总览

```text
TCP connected
  -> read CONNECT
  -> decode and basic validation
  -> protocol validation
  -> auth hook
  -> clientId resolution
  -> duplicate client handling
  -> create/bind session
  -> init keep alive
  -> send CONNACK
  -> enter CONNECTED state
```

## 详细步骤

### 1. 连接建立

- `transport` 创建 `ClientConnection`。
- 初始状态设为 `NEW`。
- 连接进入“等待 CONNECT 报文”阶段。

### 2. 接收并解码 CONNECT

- `codec` 将字节流解码为 CONNECT 报文对象。
- 若解码失败，直接关闭连接。

`M1` 规则：

- 报文格式非法时，不尝试继续读取后续报文。
- 若首个有效报文不是 CONNECT，则关闭连接。

### 3. CONNECT 基础校验

至少校验以下内容：

- 协议名称是否为 MQTT。
- 协议级别是否为 MQTT 3.1.1 或 MQTT 5。
- 保留标志位是否合法。
- Client Identifier 是否满足基本格式约束。
- Keep Alive 数值是否合法。

`M1` 说明：

- 鉴权可先走默认放行实现。
- 与 `M2` 相关的 Session Expiry 可先仅解析或明确忽略，但行为要文档化。

### 4. 鉴权扩展点

- `protocol` 调用 `auth` 接口。
- `M1` 默认实现允许通过。

未来扩展：

- 用户名密码校验。
- ACL / 授权。
- 扩展认证机制。

### 5. Client Identifier 解析

处理原则见 `ADR-0002`，按协议版本区分：

- MQTT 3.1.1
  - 非空 `clientId`：正常处理。
  - 空 `clientId` + `Clean Session = 1`：接受并生成唯一 `clientId`。
  - 空 `clientId` + `Clean Session = 0`：返回 `0x02 Identifier rejected` 后关闭连接。
- MQTT 5
  - 非空 `clientId`：正常处理。
  - 空 `clientId`：接受并生成唯一 `clientId`，在 CONNACK 中返回 `Assigned Client Identifier`。

实现要求：

- 服务端生成的 `clientId` 必须在当前 Broker 中唯一。
- 生成后的 `clientId` 要进入后续会话绑定、重复连接判定和日志链路。

### 6. 重复 clientId 处理

当已有在线连接占用相同 `clientId` 时：

1. 标记旧连接进入 `DISCONNECTING`。
2. 关闭旧连接。
3. 将新连接绑定到该 `clientId`。

`M1` 约束：

- 必须确保任一时刻最多只有一个在线连接持有同一 `clientId`。
- 旧连接关闭与新连接绑定之间要有原子性保证，避免短暂双活。

## 会话绑定

`M1` 处理原则：

- 若该 `clientId` 对应会话不存在，则创建新会话。
- 若存在会话对象，则复用会话对象，但只保留内存态，不承诺跨重启恢复。

会话最小绑定内容：

- `clientId`
- `connectionId`
- `connected=true`

## Keep Alive 初始化

- 从 CONNECT 中读取 Keep Alive 值。
- 若值大于 0，则依赖 `vertx-mqtt` 内置的空闲检测。
- 超时阈值按 `Keep Alive * 1.5` 计算。
- 超时后按异常断连处理。

`M1` 当前规则：

- `vertx-mqtt` 在 CONNECT 完成后自动安装 Keep Alive 超时检测。
- `PINGREQ -> PINGRESP` 默认由 `vertx-mqtt` 自动处理。
- Keep Alive 超时路径按连接关闭处理，不在 broker 中重复实现第二套计时器。

## 成功返回

满足条件后：

1. 构造 CONNACK。
2. 写回客户端。
3. 将连接状态更新为 `CONNECTED`。
4. 记录连接成功事件。

`M1` 建议：

- CONNACK 中只返回当前阶段已支持的属性。
- 对未支持但已解析的属性，应避免伪装成“完整支持”。

## 失败路径

### 报文格式非法

- 直接关闭连接。

### 协议级别不支持

- 返回合适拒绝结果后关闭连接，或按实现约束直接关闭。

### Client Identifier 非法

- 返回拒绝结果并关闭连接。

### 鉴权失败

- 返回拒绝结果并关闭连接。

### 内部异常

- 记录日志。
- 关闭连接。

## 状态机

```text
NEW
 -> CONNECTING
 -> CONNECTED
 -> DISCONNECTING
 -> CLOSED
```

状态转换说明：

- TCP 建立后进入 `CONNECTING`。
- CONNECT + CONNACK 成功完成后进入 `CONNECTED`。
- 主动断开、重复 `clientId` 替换、Keep Alive 超时、写失败都进入 `DISCONNECTING`。
- 资源释放完成后进入 `CLOSED`。

## M1 验收点

- 合法客户端可成功连接并收到 CONNACK。
- 首报文不是 CONNECT 时连接被拒绝。
- 非法协议级别被拒绝。
- 重复 `clientId` 连接时旧连接被替换。
- Keep Alive 超时可触发关闭。
- 集成测试能覆盖成功、拒绝、替换、超时四类路径。

## 待确认项

- CONNECT 失败时具体返回码策略是否在 `M1` 完整实现。
