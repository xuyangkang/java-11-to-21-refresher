# Java 21 OOP · OOD 与工程实战 CheatSheet

> 姊妹篇：[Java 21 语法与算法速查 CheatSheet](./java21-algo-cheatsheet.md)

**定位：** 面向 OOD 面向对象设计轮、工程/系统 Coding 轮与日常 Vibe Coding 架构建模（Java 11 → **Java 21 LTS** 升级视角）。

---

## 1. OOP 关键字与内存/封装直觉

| 语法 | 核心直觉与边界 |
|---|---|
| `private` + 构造器 | 隐藏内部状态，构造时一次性校验不变量（Invariant）；`Objects.requireNonNull(x)` |
| `final` 字段 | **仅保证引用不重绑**，不保证集合/数组内容不可变；入参/返回值防御性拷贝用 `List.copyOf` / `Map.copyOf` |
| `static` 内部类 | **不持有外部类实例引用**；只要内部类不需要访问外部实例字段，一律加 `static` 防内存泄漏 |
| `interface` / `implements` | 可替换的行为契约；实现方法必须显式标 `public`；可含 `default` / `static` / `private` 辅助方法 |
| `abstract class` / `extends` | 共享状态 + 模板方法骨架（Template Method）；单继承，子类构造器先走 `super(...)`（Java 21） |
| `record` | 不可变透明数据载体（隐式 `final`，不可继承其他类，可 `implements` 接口），自动生成 `x()`、`equals`、`hashCode`、`toString` |
| `sealed` ... `permits` | 封闭继承层级，配合 `switch` 模式匹配让编译器做**穷尽检查**（免写 `default`） |
| `@Override` | 重写/实现接口方法必加，让编译器帮你拦住把 `equals(Object)` 误写成重载的低级 Bug |

---

## 2. 现代领域建模三板斧（Java 11 → 21）

### 2.1 `record` 深度用法（替代 80% 的传统 POJO / DTO / Value Object）

```java
// 紧凑构造器（Compact Constructor）：省去形参列表与 this.x = x，专心做校验与归一化
public record Order(String id, List<String> items, long cents) implements Comparable<Order> {
    public Order {
        Objects.requireNonNull(id, "id cannot be null");
        if (cents < 0) throw new IllegalArgumentException("negative cents: " + cents);
        items = List.copyOf(items);              // 防御性拷贝，末尾自动赋给 this.items，实现深度不可变
    }

    // 可定义实例方法、静态工厂方法，但不能声明额外的非 static 实例字段
    public boolean isFree() { return cents == 0; }

    @Override
    public int compareTo(Order o) { return Long.compare(this.cents, o.cents); }
}
```
> **何时不用 `record` 退回普通 `class`**：① 对象生命周期内需要原地修改状态（如实体状态流转、计数器、双向链表节点）；② 需要继承抽象基类；③ 需要隐藏内部表示不对外暴露同名访问器。

### 2.2 代数数据类型（ADT）：`sealed interface` + `record` + 穷尽 `switch`

告别“一个胖类塞满可空字段 + `status` 枚举猜哪个字段非空”的脆弱建模，非法状态在编译期直接无法构造：

```java
// 表达业务结果 / 支付状态机 / AST 节点 / 领域事件
public sealed interface PaymentState
        permits PaymentState.Pending, PaymentState.Paid, PaymentState.Failed {
    record Pending(Instant expireAt) implements PaymentState {}
    record Paid(String txId, long paidCents) implements PaymentState {}
    record Failed(int errCode, String reason, boolean retryable) implements PaymentState {}
}

String nextAction(PaymentState state, Instant now) {
    return switch (state) {                      // 编译器穷尽检查，故意不写 default 以防未来漏新状态
        case PaymentState.Pending(var exp) when now.isAfter(exp) -> "close_expired";
        case PaymentState.Pending(var exp) -> "wait_until_" + exp;
        case PaymentState.Paid(var txId, long cents) -> "ship:" + txId; // Java 22+ 不用字段可直接写 _
        case PaymentState.Failed(int code, var msg, boolean retry) -> retry ? "retry" : "abort:" + msg;
    };
}
```

### 2.3 传统可变 `class` 的标准契约（`equals` / `hashCode` / `Comparable`）

- **面向接口声明，组合优于继承**：字段与方法签名统一写 `List<T>` / `Map<K, V>` / `Deque<T>`。
- **`List.copyOf(src)` vs `Collections.unmodifiableList(src)`**：
  - `List.copyOf` 做**不可变快照**（原 `src` 后续增删不影响返回值；禁 `null`；若 `src` 已是 `List.of` 系则 $O(1)$ 零拷贝直接复用！）；
  - `unmodifiableList` 只是**只读外壳视图**（持有 `src` 引用的人继续改 `src` 时，外部看到的也会跟着变！）。

```java
public final class Endpoint implements Comparable<Endpoint> {
    private static final Comparator<Endpoint> CMP =
            Comparator.comparing(Endpoint::host).thenComparingInt(Endpoint::port);

    private final String host;
    private final int port;

    public Endpoint(String host, int port) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
    }
    public String host() { return host; }
    public int port() { return port; }

    @Override
    public boolean equals(Object o) {            // Java 16+ instanceof 模式匹配，一行写完
        return o instanceof Endpoint e && port == e.port && host.equals(e.host);
    }

    @Override
    public int hashCode() {                      // 与 equals 使用完全相同的字段集
        return Objects.hash(host, port);
    }

    @Override
    public int compareTo(Endpoint other) {       // 复用静态 Comparator，告别手写多层 if 比较
        return CMP.compare(this, other);
    }
}
```

---

## 3. 高频设计模式的 Java 21 去样板化写法

OOD 面试常考扩展性（如停车场计费、折扣规则、通知路由、请求构造），现代 Java 无需建一堆琐碎空壳类：

```java
// 1. Strategy 策略模式：单方法契约直接用 @FunctionalInterface / 函数式接口注入，或用带行为的 enum
public enum PricingTier {
    NORMAL(cents -> cents),
    VIP(cents -> cents * 8 / 10),
    MINUS_500(cents -> Math.max(0, cents - 500));

    private final LongUnaryOperator calc;
    PricingTier(LongUnaryOperator calc) { this.calc = calc; }
    public long apply(long cents) { return calc.applyAsLong(cents); }
}

// 2. Builder 模式（无 Lombok 环境手写可选参数对象，构造完不可变）
public final class HttpReq {
    private final String url;
    private final int timeoutMs;
    private final Map<String, String> headers;

    private HttpReq(Builder b) {
        this.url = Objects.requireNonNull(b.url);
        this.timeoutMs = b.timeoutMs;
        this.headers = Map.copyOf(b.headers);
    }

    public static final class Builder {
        private final String url;
        private int timeoutMs = 3_000;
        private final Map<String, String> headers = new HashMap<>();

        public Builder(String url) { this.url = url; }
        public Builder timeoutMs(int ms) { this.timeoutMs = ms; return this; }
        public Builder header(String k, String v) { this.headers.put(k, v); return this; }
        public HttpReq build() { return new HttpReq(this); }
    }
}
```

---

## 4. 工程并发与资源管理（系统 Coding 轮高频）

### 4.1 虚拟线程并发扇出与限流（Java 21 JEP 444）

工程面试写并发抓取/批量 RPC 聚合，不再写 `CompletableFuture` 回调链，回归**直观同步代码 + `try-with-resources` 结构化收尾**：

```java
// 并发扇出 + Semaphore 控制下游并发度（Java 19+ ExecutorService 与 Java 21 HttpClient 均支持 AutoCloseable）
List<Result> batchFetch(List<String> urls, int maxConcurrency) throws Exception {
    Semaphore sem = new Semaphore(maxConcurrency);
    try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<Result>> futures = urls.stream()
                .map(url -> exec.submit(() -> {
                    sem.acquire();
                    try { return callRemote(url); }
                    finally { sem.release(); }
                }))
                .toList();

        List<Result> out = new ArrayList<>(futures.size());
        for (var f : futures) out.add(f.get());  // 阻塞虚拟线程极轻量，自动卸载底层载体线程
        return out;
    } // 出 try 自动等待所有任务结束并关闭执行器
}
```

- **铁律 1（绝不池化虚拟线程）**：虚拟线程是廉价一次性对象，不要建固定大小虚拟线程池；**限流靠 `Semaphore`**。
- **铁律 2（防 `synchronized` Pinning）**：在 **Java 21** 中，阻塞 IO 若包在 `synchronized` 内会钉死底层 OS 线程，锁内含 IO 时一律改用 **`ReentrantLock`**（Java 24 JEP 491 已彻底解除该限制）。
- **默认 UTF-8**：Java 18 起标准文件/流 API 全平台默认 UTF-8；小文件直接 `Files.readString(Path.of("a.txt"))`。

### 4.2 线程安全组件速选（OOD 并发追问必背）

| 场景 | 选型 | 关键惯用法与注意点 |
|---|---|---|
| 并发键值缓存 / 路由表 | `ConcurrentHashMap<K, V>` | 用 `compute` / `merge` 做原子更新；**禁 `null` 键值**；勿把 `get` + `put` 分两步写（非原子） |
| 高并发计数器 / 指标 | `LongAdder` / `AtomicLong` | 纯累加统计用 `LongAdder`（分段减少 CAS 争用）；需精确 CAS 条件更新（如限流器令牌）用 `AtomicLong` |
| 读多写少复杂结构 | `ReentrantReadWriteLock` | `lock()` 必须在 `try` 外紧邻处调，`unlock()` 铁定放 `finally` |
| 生产者-消费者解耦 | `ArrayBlockingQueue<T>` | 有界防 OOM；阻塞用 `put(x)` / `take()`，带超时用 `offer(x, t, unit)` / `poll(t, unit)` |

---

## 5. OOD 面试手写高频模板

### 5.1 LRU Cache（Java 21 `SequencedMap` 组合版 vs 纯手写双向链表版）

```java
// 写法 A：工程与允许用标准库的 OOD 轮 —— Java 21 LinkedHashMap 原生支持 putLast / pollFirstEntry
class LRUCache {
    private final int cap;
    private final LinkedHashMap<Integer, Integer> map = new LinkedHashMap<>();

    public LRUCache(int capacity) { this.cap = capacity; }

    public int get(int key) {
        Integer val = map.remove(key);
        if (val == null) return -1;
        map.putLast(key, val);                           // 移到队尾（最新访问）
        return val;
    }

    public void put(int key, int value) {
        map.putLast(key, value);                         // 插入或更新值，并自动移到队尾
        if (map.size() > cap) map.pollFirstEntry();      // O(1) 淘汰队头（最久未用）
    }
}
```

<details>
<summary><b>写法 B：面试官禁 LinkedHashMap 时的纯手写模板（哨兵双向链表 + HashMap）</b></summary>

```java
class LRUCache {
    private static final class Node {
        int key, val;
        Node prev, next;
        Node(int key, int val) { this.key = key; this.val = val; }
    }

    private final int cap;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0), tail = new Node(0, 0); // 哑头尾哨兵，彻底消灭 null 特判！

    public LRUCache(int capacity) {
        this.cap = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        Node node = map.get(key);
        if (node == null) return -1;
        moveToTail(node);
        return node.val;
    }

    public void put(int key, int value) {
        Node node = map.get(key);
        if (node != null) {
            node.val = value;
            moveToTail(node);
            return;
        }
        if (map.size() == cap) {
            Node lru = head.next;
            unlink(lru);
            map.remove(lru.key);
        }
        Node fresh = new Node(key, value);
        appendTail(fresh);
        map.put(key, fresh);
    }

    private void moveToTail(Node x) { unlink(x); appendTail(x); }
    private void unlink(Node x) { x.prev.next = x.next; x.next.prev = x.prev; }
    private void appendTail(Node x) {
        x.prev = tail.prev; x.next = tail;
        tail.prev.next = x; tail.prev = x;
    }
}
```

</details>

### 5.2 自定义 `Iterator<T>` 万能「预取 `advance()`」骨架

手写过滤迭代器、二维展开迭代器、树迭代器时，**永远在构造器和每次 `next()` 末尾预取好下一个元素**：这样 `hasNext()` 只是连续调多少次都安全的 $O(1)$ 判非空，`next()` 也绝不会漏抛 `NoSuchElementException`。

```java
public final class FilteringIterator<T> implements Iterator<T> {
    private final Iterator<T> src;
    private final Predicate<T> pred;
    private T nextItem;
    private boolean hasNextItem;

    public FilteringIterator(Iterator<T> src, Predicate<T> pred) {
        this.src = Objects.requireNonNull(src);
        this.pred = Objects.requireNonNull(pred);
        advance();
    }

    private void advance() {
        hasNextItem = false;
        nextItem = null;
        while (src.hasNext()) {
            T cand = src.next();
            if (pred.test(cand)) {
                nextItem = cand;
                hasNextItem = true;
                return;
            }
        }
    }

    @Override
    public boolean hasNext() { return hasNextItem; }

    @Override
    public T next() {
        if (!hasNextItem) throw new NoSuchElementException();
        T res = nextItem;
        advance();
        return res;
    }
}
```
