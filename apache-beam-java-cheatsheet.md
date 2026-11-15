# Apache Beam (Java SDK) 实战与面试速查 CheatSheet

> 姊妹篇：[Java 21 语法与算法篇](./java21-algo-cheatsheet.md) · [Java 21 OOP 与工程并发篇](./java21-oop-cheatsheet.md)

**定位：** 面向 **Data Engineer（数据工程）** 编码轮与流批一体系统设计轮（覆盖 Beam 统一编程模型、Batch/Streaming 核心 API、Window/Watermark/Trigger、Stateful & Timely `DoFn`、Join 调优与生产避坑）。

---

## 1. 统一四问模型与 Java 手写三大防坑

### 1.1 Beam 四问模型（系统设计口述主线）

| 维度 | 回答的问题 | Beam 核心 API |
|---|---|---|
| **What** | 算什么逻辑？ | `ParDo`、`MapElements`、`FlatMapElements`、`Filter`、`Combine`、`GroupByKey` |
| **Where** | 按事件时间切到哪个窗口？ | `Window.into(...)`：`FixedWindows`、`SlidingWindows`、`Sessions`、`GlobalWindows` |
| **When** | 处理时间里何时触发输出？ | **Watermark** + `Trigger`（`AfterWatermark`、`AfterProcessingTime`、`AfterPane`）+ `AllowedLateness` |
| **How** | 多次触发的结果如何修正？ | `accumulatingFiredPanes()`（发全量最新值）vs `discardingFiredPanes()`（只发增量差值） |

### 1.2 Java 21 写 Beam 最易踩的 3 个编译/运行坑

1. **时间类全部是 `org.joda.time`，不是 `java.time`！**
   - 窗口时长写 `org.joda.time.Duration.standardMinutes(5)`（不是 `Duration.ofMinutes(5)`），事件时间戳是 `org.joda.time.Instant`。
2. **Lambda 泛型擦除必须传 `TypeDescriptors`**：
   - `MapElements.into(TypeDescriptors.strings()).via(x -> ...)`；忘了指定返回类型会导致 Beam 无法推断 `Coder` 直接在构图阶段报错。
3. **`TupleTag` 必须带空花括号 `{}` 保留泛型**：
   - 写 `new TupleTag<String>() {}`（匿名内部类保留运行期泛型给 `Coder`），漏写 `{}` 会报 `Unable to return a default Coder`。

---

## 2. Pipeline 骨架、配置与常用 IO

```java
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.*;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.*;

public class EtlJob {
    // 1. 强类型命令行参数接口（支持 --inputPath=... --outputPath=... 自动解析）
    public interface JobOptions extends PipelineOptions {
        @Description("Input path")
        @Validation.Required
        String getInputPath();
        void setInputPath(String value);

        @Default.String("gs://my-bucket/out/prefix")
        String getOutputPath();
        void setOutputPath(String value);
    }

    public static void main(String[] args) {
        JobOptions opts = PipelineOptionsFactory.fromArgs(args).withValidation().as(JobOptions.class);
        Pipeline p = Pipeline.create(opts);

        PCollection<String> lines = p.apply("ReadRaw", TextIO.read().from(opts.getInputPath()));

        lines
            .apply("FilterBlank", Filter.by((String s) -> !s.isBlank()))
            .apply("ToLower", MapElements.into(TypeDescriptors.strings()).via(String::strip))
            .apply("WriteOut", TextIO.write().to(opts.getOutputPath()).withSuffix(".txt").withNumShards(10));

        p.run().waitUntilFinish(); // 流任务在 Dataflow/Flink 提交时通常只调 p.run()
    }
}
```

**常见生产 IO 速选：**
- **对象存储文件**：`TextIO`、`AvroIO`、`ParquetIO.sink(schema)` + `FileIO.write().via(...)`（流式落湖必设 `.withNumShards(N)` 防海量小文件！）
- **消息流**：`KafkaIO.<String, byte[]>read().withBootstrapServers(...).withTopic(...)`、`PubsubIO.readStrings().fromSubscription(...)`
- **数仓与数据库**：`BigQueryIO.writeTableRows().withMethod(Method.STORAGE_WRITE_API)`（高吞吐精确一次流写首选）、`JdbcIO`

---

## 3. 核心转换：`Map`、`ParDo` 生命周期与多路死信队列（DLQ）

### 3.1 轻量转换（优先于手写 `DoFn`）

```java
PCollection<KV<String, Long>> wordOnes = lines
    .apply(FlatMapElements.into(TypeDescriptors.strings())
        .via((String line) -> Arrays.asList(line.strip().split("\\s+"))))
    .apply(Filter.by((String w) -> !w.isEmpty()))
    .apply(MapElements.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.longs()))
        .via(w -> KV.of(w, 1L)));
// 提取键：WithKeys.of((Event e) -> e.userId()).withKeyType(TypeDescriptors.strings())
```

### 3.2 `DoFn` 完整生命周期 + 死信队列（DE 编码面第一高频模板！）

> **面试必考点**：外部客户端（DB 连接池 / HTTP / RPC Stub）**严禁**在 `@ProcessElement` 里每条新建，必须在 **`@Setup`** 初始化；批量写出在 **`@StartBundle` / `@FinishBundle`** 攒批提交。

```java
static final TupleTag<Event> VALID_TAG = new TupleTag<>() {};
static final TupleTag<String> DLQ_TAG = new TupleTag<>() {};

static class ParseAndEnrichFn extends DoFn<String, Event> {
    private transient RpcClient rpcClient;               // transient：不可序列化的重客户端在 Worker 端懒初始化
    private transient List<Event> buffer;

    @Setup
    public void setup() {                                // 每个 Worker 实例启动调一次：建连接池/客户端
        this.rpcClient = RpcClient.connect();
    }

    @StartBundle
    public void startBundle() {                          // 每个微批 Bundle 开始
        this.buffer = new ArrayList<>(256);
    }

    @ProcessElement
    public void process(@Element String raw, @Timestamp org.joda.time.Instant ts, MultiOutputReceiver out) {
        try {
            Event evt = Event.parseJson(raw, ts.getMillis());
            out.get(VALID_TAG).output(evt);
        } catch (Exception ex) {                         // 脏数据绝不打崩整条流，路由到死信分支！
            out.get(DLQ_TAG).output("err=" + ex.getMessage() + ", raw=" + raw);
        }
    }

    @FinishBundle
    public void finishBundle(FinishBundleContext ctx) {  // 若有外部攒批写可在此统一 flush
        buffer.clear();
    }

    @Teardown
    public void teardown() {                             // Worker 销毁时优雅关连接
        if (rpcClient != null) rpcClient.close();
    }
}

// 组装多路输出
PCollectionTuple parsed = rawLines.apply("ParseWithDLQ",
    ParDo.of(new ParseAndEnrichFn()).withOutputTags(VALID_TAG, TupleTagList.of(DLQ_TAG)));

PCollection<Event> validEvents = parsed.get(VALID_TAG);
PCollection<String> badRecords = parsed.get(DLQ_TAG);    // 单独落 GCS/Kafka 供排查重放
```

---

## 4. 聚合与多流 Join（选型铁律与倾斜调优）

### 4.1 聚合：`Combine` 永远优先于 `GroupByKey`

- **`GroupByKey`**：把同一 Key 的所有元素原封不动通过网络 Shuffle 到一个节点，数据量大或 Key 倾斜时直接 OOM。
- **`Combine.perKey(...)`**：自动开启 **Combiner Lifting（Shuffle 前本地预聚合）**，网络只传中间累加器；遇到超级大户热 Key 还可用 **`.withHotKeyFanout(N)`** 自动加盐拆成两阶段聚合！

```java
// 1. 内置聚合器：Sum.longsPerKey() / Count.perKey() / Mean.perKey() / Top.largestPerKey(k)
PCollection<KV<String, Long>> totals = wordOnes.apply(
    Combine.<String, Long, Long>perKey(Sum.ofLongs()).withHotKeyFanout(16)); // 热 Key 两阶段扇出防倾斜

// 2. 自定义 CombineFn<InputT, AccumT, OutputT>（如算去重 UV / 加权均值）
static class AvgFn extends Combine.CombineFn<Long, long[], Double> {
    @Override public long[] createAccumulator() { return new long[2]; } // [sum, count]
    @Override public long[] addInput(long[] acc, Long v) { acc[0] += v; acc[1]++; return acc; }
    @Override public long[] mergeAccumulators(Iterable<long[]> accs) {
        long[] res = new long[2];
        for (long[] a : accs) { res[0] += a[0]; res[1] += a[1]; }
        return res;
    }
    @Override public Double extractOutput(long[] acc) {
        return acc[1] == 0 ? 0.0 : (double) acc[0] / acc[1];
    }
}
```

### 4.2 多流关联：Side Input（广播小表）vs `CoGroupByKey`（Shuffle 大表）

| 关联方式 | 适用规模 | 原理与注意点 |
|---|---|---|
| **Side Input** (`View.asMap`) | 维表较小（百 MB 级，放得进 Worker 内存） | 广播到每个 Worker，**主流免 Shuffle**；流式维表可用全局窗口 + 周期触发实现热更新 |
| **`CoGroupByKey`** | 双大表 / 双高吞吐事件流 | 按相同 Key + **对齐的窗口**做分布式 Shuffle Join（支持 Inner / Left / Full Outer 语义自写） |

```java
// 方式 A：Side Input 广播小维表 Join（零 Shuffle）
PCollectionView<Map<String, String>> userDimView = userDimKVs.apply(View.asMap());

PCollection<String> enriched = clickEvents.apply(ParDo.of(new DoFn<KV<String, Event>, String>() {
    @ProcessElement
    public void process(@Element KV<String, Event> elem, ProcessContext c) {
        Map<String, String> dim = c.sideInput(userDimView);
        String tier = dim.getOrDefault(elem.getKey(), "FREE");
        c.output(elem.getKey() + ":" + tier);
    }
}).withSideInputs(userDimView));

// 方式 B：CoGroupByKey 分布式大表 Left Join
final TupleTag<Order> orderTag = new TupleTag<>() {};
final TupleTag<Payment> payTag = new TupleTag<>() {};

PCollection<KV<String, CoGbkResult>> joined = KeyedPCollectionTuple
    .of(orderTag, ordersByOrderId)                       // PCollection<KV<String, Order>>
    .and(payTag, paymentsByOrderId)                      // PCollection<KV<String, Payment>>
    .apply("ShuffleJoin", CoGroupByKey.create());

joined.apply(ParDo.of(new DoFn<KV<String, CoGbkResult>, String>() {
    @ProcessElement
    public void process(@Element KV<String, CoGbkResult> kv, OutputReceiver<String> out) {
        Iterable<Order> orders = kv.getValue().getAll(orderTag);
        Iterable<Payment> pays = kv.getValue().getAll(payTag);
        for (Order o : orders) {
            boolean paid = pays.iterator().hasNext();    // Left Join 逻辑自由控制
            out.output(o.id() + " paid=" + paid);
        }
    }
}));
```

---

## 5. 流式核心：Window · Watermark · Trigger · Lateness

```java
import org.apache.beam.sdk.transforms.windowing.*;
import org.joda.time.Duration;
import org.joda.time.Instant;

// 1. 提取事件时间（生产中优先在 KafkaIO/PubsubIO 读取层直接绑事件时间，以便精确推进底层 Watermark）
PCollection<Event> timed = events.apply(
    WithTimestamps.of((Event e) -> Instant.ofEpochMilli(e.eventTimeMs()))
        .withAllowedTimestampSkew(Duration.standardMinutes(5)));

// 2. 常见窗口类型
// - 滚动窗口：FixedWindows.of(Duration.standardMinutes(10))
// - 滑动窗口：SlidingWindows.of(Duration.standardMinutes(10)).every(Duration.standardMinutes(1))
// - 会话窗口（按每用户动态超时自动合并）：Sessions.withGapDuration(Duration.standardMinutes(30))

// 3. 经典三维流式窗口模板：分钟级早发预览 + 水位线过窗准点产出 + 1 小时内迟到逐条修正
PCollection<KV<String, Long>> windowed = keyedEvents.apply("Window10m",
    Window.<KV<String, Long>>into(FixedWindows.of(Duration.standardMinutes(10)))
        .triggering(
            AfterWatermark.pastEndOfWindow()
                .withEarlyFirings(AfterProcessingTime.pastFirstElementInPane()
                    .plusDelayOf(Duration.standardMinutes(1)))   // 窗口未关前每分钟出一版推测值
                .withLateFirings(AfterPane.elementCountAtLeast(1))) // 水位线过后迟到数据每来 1 条立刻修正
        .withAllowedLateness(Duration.standardHours(1), Window.ClosingBehavior.FIRE_IF_NON_EMPTY)
        .accumulatingFiredPanes()
);
```

> **面试必答：`accumulatingFiredPanes()` vs `discardingFiredPanes()` 怎么选？**
> - 下游是 **KV 库 / 数据库 Upsert（按 `window + key` 覆盖写）** 👉 选 **`accumulatingFiredPanes()`**（每次发出窗口截至目前的完整累计总值，直接覆盖旧值即可，不怕乱序）。
> - 下游是 **消息队列 / 只追加日志且下游自己还会做一次 Sum** 👉 选 **`discardingFiredPanes()`**（每次只发出距离上一版 Pane 的增量，避免重复累加）。

---

## 6. 有状态与定时处理（Stateful & Timely `DoFn`）

当标准窗口满足不了复杂精细状态（如**流式去重、精准攒够 N 条或超时 T 秒立刻发往下游、风控状态机**）时使用。
> **硬性前提**：输入必须是 **`PCollection<KV<K, V>>`**——所有 `@StateId` 和 `@TimerId` 都严格按 **`Key + Window`** 隔离存储与串行化加锁（小心空 Key `""` 把全流锁成单线程！）。

```java
import org.apache.beam.sdk.state.*;

// 实战模板：流式按 eventId 1 小时内精准去重（利用 ValueState + 事件时间定时器自动清理防状态无限膨胀！）
static class DeduplicateFn extends DoFn<KV<String, Event>, Event> {
    @StateId("seen")
    private final StateSpec<ValueState<Boolean>> seenSpec = StateSpecs.value();

    @TimerId("ttl")
    private final TimerSpec ttlSpec = TimerSpecs.timer(TimeDomain.EVENT_TIME);

    @ProcessElement
    public void process(
            @Element KV<String, Event> kv,
            @Timestamp org.joda.time.Instant ts,
            @StateId("seen") ValueState<Boolean> seen,
            @TimerId("ttl") Timer ttlTimer,
            OutputReceiver<Event> out) {
        if (Boolean.TRUE.equals(seen.read())) {
            return;                                      // 重复事件直接丢弃
        }
        seen.write(true);
        ttlTimer.set(ts.plus(Duration.standardHours(1))); // 1 小时事件时间后触发状态回收
        out.output(kv.getValue());
    }

    @OnTimer("ttl")
    public void onExpire(@StateId("seen") ValueState<Boolean> seen) {
        seen.clear();                                    // 必须清理过期状态，否则 State 后端迟早爆满！
    }
}
// 注：还有 BagState<T>（攒列表）、CombiningState<In, Accum, Out>、MapState<K, V> 及 TimeDomain.PROCESSING_TIME
```

---

## 7. Schema、Beam SQL、单元测试与生产五大死穴

<details>
<summary><b>Schema 化管道与 Beam SQL（少写样板代码 + 免手配 Coder）</b></summary>

```java
import org.apache.beam.sdk.schemas.JavaFieldSchema;
import org.apache.beam.sdk.schemas.annotations.DefaultSchema;
import org.apache.beam.sdk.schemas.transforms.Group;
import org.apache.beam.sdk.schemas.transforms.Select;
import org.apache.beam.sdk.extensions.sql.SqlTransform;

@DefaultSchema(JavaFieldSchema.class)                    // 自动注册 SchemaCoder，远快于 SerializableCoder！
public class ClickRow {
    public String userId;
    public long costCents;
    public ClickRow() {}                                 // JavaFieldSchema 要求无参构造器（或用 @SchemaCreate）
}

// 直接用字段名聚合或跑 SQL
PCollection<Row> agg = clicks.apply(
    Group.<ClickRow>byFieldNames("userId")
         .aggregateField("costCents", Sum.ofLongs(), "totalCents"));

PCollection<Row> sqlRes = clicks.apply(
    SqlTransform.query("SELECT userId, SUM(costCents) AS total FROM PCOLLECTION GROUP BY userId"));
```

</details>

<details>
<summary><b>单元测试模板：TestPipeline + PAssert + TestStream（编码面加分王炸）</b></summary>

```java
// 1. 批处理断言测试
@Rule public final transient TestPipeline p = TestPipeline.create();

@Test
public void testTransform() {
    PCollection<String> out = p.apply(Create.of("a", "b", "a"))
        .apply(Count.perElement())
        .apply(MapElements.into(TypeDescriptors.strings()).via(kv -> kv.getKey() + ":" + kv.getValue()));

    PAssert.that(out).containsInAnyOrder("a:2", "b:1");
    p.run();
}

// 2. 流式水位线与迟到数据确定性测试（TestStream 精确拨动虚拟时钟！）
TestStream<String> stream = TestStream.create(StringUtf8Coder.of())
    .advanceWatermarkTo(Instant.EPOCH)
    .addElements(TimestampedValue.of("on_time", Instant.EPOCH.plus(Duration.standardSeconds(10))))
    .advanceWatermarkTo(Instant.EPOCH.plus(Duration.standardMinutes(15))) // 越过 10 分钟窗口尾
    .addElements(TimestampedValue.of("late_elem", Instant.EPOCH.plus(Duration.standardSeconds(20))))
    .advanceWatermarkToInfinity();
```

</details>

### DE 面试官最爱追问的 5 个 Beam 生产死穴

1. **严禁原地修改输入元素或已 `output` 的对象**：分布式 Runner 会做**算子融合（Fusion Optimization）**，上下游 `DoFn` 在同一线程直接传对象引用，原地改字段会导致诡异脏数据（`DirectRunner` 单测会直接抛 `IllegalMutationException` 抓你）。
2. **算子融合导致高扇出卡死（High-Fanout Fusion Trap）**：上游 1 条配置/文件路径在 `FlatMap` 拆成 10 万条子记录，若紧接着重计算 `DoFn`，Beam 会把它们融合在**同一个单线程**串行跑！👉 中间插一步 **`Redistribute.arbitrarily()` / `Reshuffle.viaRandomKey()`** 强制打断融合并均匀分发到所有 Worker。
3. **`SerializableCoder` 性能陷阱**：Java `Serializable` 编码体积大且 CPU 极重，千万别在百 GB/TB Shuffle 流里用默认 `SerializableCoder`；优先用 `@DefaultSchema`、`AvroCoder`、`ProtoCoder` 或组合内置 `KvCoder.of(StringUtf8Coder.of(), VarLongCoder.of())`。
4. **闭包捕获外部大对象 / `this`**：匿名内部类 `new DoFn<>() {}` 会隐式捕获外部类的 `this` 导致 `NotSerializableException`——`DoFn` 一律写成 **`static class`**。
5. **端到端 Exactly-Once 边界**：Beam 保证算子间 Shuffle 与状态精确一次，但外部 Sink 遇网络重试仍是至少一次（At-Least-Once）——落库靠**确定性业务幂等键 Upsert**，写 BigQuery 用 `STORAGE_WRITE_API` 精确一次模式，调非幂等外部 RPC 前先加一步 `Reshuffle` 固化上游随机结果防重算漂移。
