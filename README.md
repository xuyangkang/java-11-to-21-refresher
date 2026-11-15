# Java 11 → 21 Refresher

Java 11 老兵回归 & Vibe Coding 实战复健笔记，覆盖基本语法与算法刷题、现代 OOP/OOD 领域建模与工程并发（Java 21 LTS / JDK 25）。

## 内容索引

- **[Java 21 语法与算法速查 CheatSheet](./java21-algo-cheatsheet.md)**
  - 刷题代码骨架（LeetCode / HackerRank / ACM）
  - 基础语法易错点（含整数/位移溢出、字符串与 `StringBuilder` 陷阱）
  - 集合选型、`Map` 默认值四件套与 **Stream / `Collectors` 实战管道**
  - Java 11 → 21 语法增量（模式匹配与新 `switch`、`SequencedCollection / SequencedMap`、文本块、Java 22–25 速览）
  - 高频算法模板（二分、BFS、回溯、滑动窗口与分组循环、DSU、拓扑排序、Dijkstra、树/链表/单调栈/DP/Trie）
- **[Java 21 OOP · OOD 与工程实战 CheatSheet](./java21-oop-cheatsheet.md)**
  - OOP 关键字与内存/封装直觉
  - 现代领域建模三板斧（`record` 紧凑构造器、`sealed + record` 代数数据类型 ADT、可变类 `equals / hashCode / Comparable` 契约）
  - 高频设计模式去样板化写法（函数式/枚举 `Strategy`、手写不可变 `Builder`）
  - 工程并发与资源管理（Java 21 虚拟线程并发扇出与 `Semaphore` 限流模板、并发容器速选表）
  - OOD 面试手写高频模板（`SequencedMap` 与哨兵双向链表双版本 `LRUCache`、万能预取式 `Iterator<T>`）
- **[Apache Beam (Java SDK) 实战与面试速查 CheatSheet](./apache-beam-java-cheatsheet.md)**
  - Beam 四问模型（What / Where / When / How）与 Java 泛型/时间类三大防坑
  - `PipelineOptions` 骨架、`DoFn` 生命周期与多路输出死信队列（DLQ）模板
  - `Combine` 热 Key 扇出、`Side Input` 广播关联 vs `CoGroupByKey` 分布式 Join
  - 流式窗口、Watermark、三维 Trigger 与有状态定时 `Stateful & Timely DoFn`（含 TTL 去重模板）
  - Schema / Beam SQL、`TestStream` 确定性流单测与五大生产死穴（Fusion 打断、Coder 选型、幂等性）
- **[LeetCode 近期提交 Code Review 报告（Java 21 视角）](./code-review-report.md)**
  - 7 道近期实战题的溢出隐患、记忆化反模式与 Java 21 现代语法重构对照
- **[`leetcode/`](./leetcode/)** — 近期归档题解源码
