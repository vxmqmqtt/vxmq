# Topic 路由设计

本文档描述 Broker 当前与未来都适用的 Topic 路由规则。阶段状态与完成度统一查看 [`../01-status/current-status.md`](../01-status/current-status.md)。

## 设计目标

- 普通订阅与通配符订阅匹配正确
- 订阅更新与发布查找行为可预测
- 数据结构足够简单，便于验证与演进
- 不提前为远期特性引入过度复杂度

## 基本概念

### Topic Name

- 由 PUBLISH 报文携带
- 表示消息实际发布到的主题
- 不能包含 `+` 或 `#`

### Topic Filter

- 由 SUBSCRIBE / UNSUBSCRIBE 报文携带
- 表示订阅者关心的主题集合
- 当前支持精确匹配、`+` 和 `#`

### Subscription

当前最小订阅视图包括：

- `clientId`
- `topicFilter`
- `requestedQos`

## 匹配规则

### 精确匹配

Topic Filter 与 Topic Name 各层级完全相同则匹配。

### 单层通配符 `+`

- `+` 匹配单个主题层级
- `+` 必须独占整个层级

### 多层通配符 `#`

- `#` 匹配当前位置及后续所有层级
- `#` 只能出现在最后一个层级
- `#` 必须独占整个层级

### 非法输入

- Topic Name 包含通配符时为非法
- 当前不支持 Shared Subscription，形如 `$share/group/topic` 的过滤器直接拒绝

## 当前数据结构

当前实现采用内存态订阅索引，优先保证语义正确与代码清晰。

- `session` 视图回答“某个 clientId 订阅了什么”
- `routing` 视图回答“某个 Topic Name 命中了谁”

这种双视图设计允许订阅更新和消息查找分别按最自然的方向组织。

## 更新与查询规则

### 订阅注册

1. 校验 Topic Filter
2. 先更新 `session`
3. 再更新 `routing`
4. 若路由更新失败，回滚会话更新

### 取消订阅

1. 校验 Topic Filter
2. 同步清理 `session` 与 `routing`
3. 若两侧都不存在，按协议版本返回对应结果

### 发布查找

1. 校验 Topic Name
2. 获取命中的订阅绑定
3. 按 `clientId` 去重
4. 由 `transport` 基于当前活跃连接视图执行实际投递

## 当前实现说明

- 当前实现采用内存态订阅索引，优先保证语义正确与代码清晰。
- 同一客户端的重叠订阅在当前实现中只投递一次，相关决策见 [`../07-project/decisions/0003-m1-overlapping-subscription-delivery.md`](../07-project/decisions/0003-m1-overlapping-subscription-delivery.md)。
- Shared Subscription 仍未进入当前实现范围，因此路由结果当前不携带共享组语义。

## 演进方向

- 若后续 Topic Filter 数量增长，索引结构可从当前实现演进为更高效的树形结构。
- 若后续引入 Shared Subscription，路由结果需要显式携带共享组信息。
- 若后续引入 Retained Message，路由层仍只负责匹配，不直接负责保留消息存储。
