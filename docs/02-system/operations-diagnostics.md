# 运行诊断

本文档说明当前 Broker 的基础可观测入口。健康检查和 metrics 用于判断系统状态，诊断日志用于定位单个连接、协议操作或投递失败。

## 健康检查与指标

- `/q/health/live`：表示 Broker runtime 未进入 failed 状态。
- `/q/health/ready`：表示 MQTT transport 已成功监听并可接收流量。
- `/q/metrics`：暴露低基数 Prometheus 指标，包含连接数、会话数、transport state、订阅变更、消息路由、协议告警和 transport start/stop 计数。

应用不维护消息速率滑动窗口。消息速率应在 Prometheus 查询层通过 counter 计算，例如：

```promql
rate(vxmq_messages_routed_total[1m])
```

## 诊断日志格式

诊断日志使用稳定的 `key=value` 字段，缺失字段会被省略。字段顺序固定，便于 grep、日志采集和告警规则复用。

当前字段包括：

- `event`：事件类型，例如 `connect_rejected`、`publish_rejected`、`subscribe_item_rejected`、`connection_disconnect`、`connection_closed`、`delivery_failed`。
- `severity`：`INFO`、`WARN` 或 `ERROR`。
- `operation`：协议或 transport 操作，例如 `CONNECT`、`PUBLISH`、`SUBSCRIBE`、`UNSUBSCRIBE`、`DISCONNECT`、`CLOSE`。
- `reason`：低基数原因码，例如 `TOPIC_NAME_INVALID`、`NOT_AUTHORIZED`、`PACKET_TOO_LARGE`、`SESSION_TAKEN_OVER`。
- `connectionId`、`clientId`、`requestedClientId`、`remote`、`protocolVersion`：连接定位字段。
- `mqttReasonCode`、`mqttReturnCode`：对外返回的 MQTT 5 reason code 或 CONNECT return code。
- `topic`、`topicFilter`、`packetId`、`qos`：协议操作上下文。
- `transportAction`：transport 实际动作，例如 `mqtt5_disconnect` 或 `socket_close`。
- `sessionPresent`、`willPublished`、`sessionRemoved`、`matchedClients`：会话和投递结果摘要。

诊断事件不建模密码、payload、correlation data 或 user properties，避免日志输出敏感或高基数内容。

## 典型排障入口

CONNECT 被拒绝：

```text
event=connect_rejected severity=WARN operation=CONNECT reason=NOT_AUTHORIZED connectionId=... clientId=... mqttReturnCode=CONNECTION_REFUSED_NOT_AUTHORIZED_5
```

PUBLISH 被协议拒绝并断开：

```text
event=publish_rejected severity=WARN operation=PUBLISH reason=TOPIC_NAME_INVALID connectionId=... clientId=... mqttReasonCode=TOPIC_NAME_INVALID topic=sensors/+/temperature packetId=1 qos=1
event=connection_disconnect severity=WARN operation=DISCONNECT reason=TOPIC_NAME_INVALID connectionId=... transportAction=mqtt5_disconnect
```

连接关闭与 Will 发布：

```text
event=connection_closed severity=INFO operation=CLOSE reason=SOCKET_CLOSED connectionId=... clientId=... willPublished=true matchedClients=1
```

投递失败：

```text
event=delivery_failed severity=ERROR operation=PUBLISH reason=ENDPOINT_NOT_CONNECTED clientId=subscriber connectionId=... topic=sensors/room-1 packetId=7 qos=1
```

## 指标联动

`vxmq_protocol_warnings_total` 统计 `WARN` 和 `ERROR` 级别诊断事件，以及仍通过兼容入口上报的协议告警。`INFO` 级别的连接关闭和客户端正常断连不会增加该 counter。
