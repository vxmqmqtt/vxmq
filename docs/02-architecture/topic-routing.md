# Topic 路由设计

本文档定义 `M1` 阶段的 Topic 路由设计，目标是明确：

1. Topic Name 与 Topic Filter 的匹配规则。
2. 路由索引的数据结构起点。
3. 订阅注册、取消订阅、发布查找时的一致性策略。
4. 为 `M2+` 的共享订阅、离线消息和持久化预留扩展空间。

## 设计目标

`M1` 的路由模块需要满足以下要求：

- 普通订阅与通配符订阅匹配正确。
- 数据结构足够简单，便于快速实现和验证。
- 订阅更新与消息查找行为可预测。
- 不提前为 `M5` 的高级特性引入复杂度。

## 基本概念

### Topic Name

- 由发布报文携带。
- 用于表示消息实际发布到的主题。
- `M1` 中 Topic Name 不允许包含 `+` 或 `#`。

### Topic Filter

- 由订阅报文携带。
- 用于描述订阅者关心的主题集合。
- `M1` 支持精确订阅、单层通配符 `+`、多层通配符 `#`。

### Subscription

建议最小字段：

- `clientId`
- `topicFilter`
- `requestedQos`
- `subscriptionId`

说明：

- `subscriptionId` 在 `M1` 可以是内部标识，不等同于 MQTT 5 属性中的 Subscription Identifier。
- `requestedQos` 在 `M1` 仅保留为字段，实际只支持 QoS 0 投递闭环。

## 匹配规则

### 规则 1：精确匹配

- Topic Filter 与 Topic Name 各层级完全相同则匹配。

示例：

- `a/b/c` 匹配 `a/b/c`
- `a/b/c` 不匹配 `a/b`

### 规则 2：单层通配符 `+`

- `+` 匹配单个主题层级。
- `+` 必须独占整个层级。

示例：

- `a/+/c` 匹配 `a/b/c`
- `a/+/c` 不匹配 `a/b/d`
- `a/+` 不匹配 `a/b/c`

### 规则 3：多层通配符 `#`

- `#` 匹配当前位置及后续所有层级。
- `#` 只能出现在 Topic Filter 最后一个层级。
- `#` 必须独占整个层级。

示例：

- `a/#` 匹配 `a`
- `a/#` 匹配 `a/b`
- `a/#` 匹配 `a/b/c`
- `a/#/c` 非法

### 规则 4：Topic Name 不可带通配符

- 发布报文中的 Topic Name 包含 `+` 或 `#` 时，应视为非法。

### 规则 5：Shared Subscription 暂不支持

- 形如 `$share/group/topic` 的语法在 `M1` 直接拒绝，不进入普通匹配流程。

## 输入规范化

为降低匹配实现复杂度，建议在注册和查找前做统一规范化：

1. 按 `/` 分层。
2. 保留空层级，不擅自压缩。
3. 不做大小写转换。
4. 不做路径归一化。

原因：

- MQTT 主题语义大小写敏感。
- 过度“智能处理”会引入与协议不一致的行为。

## 数据结构选择

### M1 推荐方案

`M1` 采用“规范化 Topic Filter + 线性匹配”的简单实现：

- `SubscriptionRegistry`
  保存：
  - `Map<String, SessionSubscriptions>`
  - `Map<String, Set<Subscription>>`

解释：

- 第一份索引按 `clientId` 组织，服务于会话视角的增删改查。
- 第二份索引按原始 `topicFilter` 组织，服务于路由视角的快速遍历。

发布查找时：

1. 遍历当前注册的 Topic Filter。
2. 对每个 Filter 执行匹配函数。
3. 收集命中的订阅者集合。

选择该方案的原因：

- `M1` 实现简单。
- 行为容易测试和调试。
- 在初期订阅规模较小的情况下足够用。
- 避免过早引入 Topic Tree 导致实现和调试成本上升。

### M2+ 可演进方案

若后续订阅规模或匹配性能成为瓶颈，再演进到 Topic Tree / Trie 结构。

演进前提：

- 先有可靠的匹配测试基线。
- 先有性能基线数据。

没有这两个前提，不建议过早优化数据结构。

## 核心接口建议

### TopicFilterMatcher

职责：

- 校验 Topic Filter 合法性。
- 判断单个 Topic Filter 是否匹配某个 Topic Name。

建议接口：

```text
boolean isValidFilter(String topicFilter)
boolean isValidTopicName(String topicName)
boolean matches(String topicFilter, String topicName)
```

### SubscriptionRegistry

职责：

- 注册订阅。
- 取消订阅。
- 查询匹配订阅者。

建议接口：

```text
void addSubscription(Session session, Subscription subscription)
void removeSubscription(Session session, String topicFilter)
Collection<Subscription> match(String topicName)
Collection<Subscription> listByClient(String clientId)
```

## 注册流程

订阅注册必须同时更新两处状态：

1. `session.subscriptions`
2. `routing` 内部索引

推荐顺序：

1. 校验 Topic Filter。
2. 构造 `Subscription`。
3. 更新 `session.subscriptions`。
4. 更新 `routing` 索引。
5. 成功后返回 SUBACK。

一致性要求：

- 若第 3 步成功、第 4 步失败，必须回滚 `session.subscriptions`。
- 不允许返回成功但内部路由索引缺失。

## 取消订阅流程

推荐顺序：

1. 从 `session` 找出目标订阅。
2. 从 `routing` 索引删除。
3. 从 `session.subscriptions` 删除。
4. 返回 UNSUBACK。

要求：

- 取消不存在的订阅不应抛出内部错误。
- 重复取消应保持幂等。

## 发布查找流程

处理顺序：

1. 校验 Topic Name。
2. 获取当前所有 Topic Filter。
3. 对每个 Filter 调用 `matches`。
4. 将命中结果按 `clientId` 去重。
5. 仅保留当前在线会话。
6. 返回 `RouteResult`。

去重原因：

- 同一客户端可能因多条订阅命中同一消息。
- `M1` 需要先明确投递策略，避免实现时隐含重复投递。

`M1` 建议策略：

- 同一客户端若有多条订阅命中同一 Topic Name，只投递一次。

原因：

- 可先降低投递复杂度。
- 便于后续在明确 MQTT 5 细节后再收紧行为。

注意：

- 该策略已由 `ADR-0003` 固化；若后续需要改为多副本投递，必须记录为新的决策变更。

## 顺序与并发

`M1` 建议：

- 订阅更新与路由查询都通过单线程事件循环或等效串行化机制执行。
- 先保证行为确定性，再考虑并发优化。

原因：

- 路由模块本身就是共享状态中心。
- 过早引入细粒度锁会显著提升复杂度和调试成本。

## 异常处理

### 非法 Topic Filter

- 拒绝注册，不写入任何状态。

### 非法 Topic Name

- 拒绝发布，不执行匹配。

### 索引更新失败

- 回滚当前操作。
- 记录错误日志。

### 匹配过程异常

- 记录错误。
- 终止本次发布处理，交由上层协议错误路径处理。

## 可观测性

建议至少暴露以下统计项：

- 当前订阅总数。
- 当前唯一 Topic Filter 数量。
- 每次发布命中的订阅者数量。
- 订阅注册失败次数。
- 路由匹配失败次数。

## M1 验收点

- 精确 Topic Filter 匹配正确。
- `+` 匹配单层语义正确。
- `#` 匹配尾部多层语义正确。
- 非法 Filter 被拒绝且不污染索引。
- 取消订阅后索引及时清理。
- 同一客户端多条命中订阅的去重行为符合当前设计。

## 待确认项

- 是否需要在 `M1` 就区分系统主题前缀。
- 何时从线性匹配升级到树结构，应以什么性能阈值触发。
