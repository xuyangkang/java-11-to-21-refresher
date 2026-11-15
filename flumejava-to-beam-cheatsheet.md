# FlumeJava 老兵转开源 Apache Beam & 外部数据栈速查 CheatSheet

> 姊妹篇：[Apache Beam (Java SDK) 速查](./apache-beam-java-cheatsheet.md) · [Java 21 语法与算法篇](./java21-algo-cheatsheet.md) · [Java 21 OOP 与工程并发篇](./java21-oop-cheatsheet.md)

**定位：** 专为有 **Google FlumeJava / MillWheel** 背景的工程师准备的**「肌肉记忆防串台 & 外部 DE 面试黑话翻译手册」**——帮你把内部烂熟于心的原理，零损耗切换成开源 Apache Beam 代码与主流开源大数据栈（Spark / Flink / Kafka / Iceberg）表达。

---

## 1. FlumeJava → Apache Beam API 肌肉记忆对照表

手写代码时盯紧以下 **9 处签名差异**，防止顺手写出 `PTable` 或 `EmitFn` 让外部面试官困惑：

| 概念 | FlumeJava 肌肉记忆 | 开源 Apache Beam (Java SDK) 标准写法 |
|---|---|---|
| **键值集合类型** | 有独立的 **`PTable<K, V>`** | **彻底废除 `PTable`！** 统一只有 **`PCollection<KV<K, V>>`** |
| **键值对元素** | `Pair.of(k, v)`（`first()` / `second()`） | **`KV.of(k, v)`**（`getKey()` / `getValue()`） |
| **算子串联方式** | 集合对象上直接调方法：`col.parallelDo(...)` | **万物皆 `.apply()`**：`col.apply("StepName", ParDo.of(...))` |
| **类型与序列化** | 算子参数传 `PTypes.strings()` / `PTypes.protos(...)` | 解耦为 **`Coder`**（自动推断、`ProtoCoder.of(...)`、`@DefaultSchema`）；Lambda 擦除时补 `TypeDescriptors` |
| **`DoFn` 处理与发射** | 重写固定签名 `process(In x, EmitFn<Out> emit)`，调 **`emit.emit(y)`** | 注解反射注入 `@ProcessElement void proc(@Element In x, OutputReceiver<Out> out)`，调 **`out.output(y)`** |
| **`DoFn` 生命周期** | `initialize()` / `finish()` | 拆细为 Worker 级 **`@Setup` / `@Teardown`** 与微批级 **`@StartBundle` / `@FinishBundle`** |
| **多路分流（Side Output）** | 传 Tagged Output 句柄 | 必须用带 `{}` 的匿名类保留泛型：`new TupleTag<T>() {}` + `MultiOutputReceiver` |
| **计数器监控** | Flume `Counter` 静态注册累加 | **`Metrics.counter(MyDoFn.class, "bad_rows").inc()`** / `Metrics.distribution(...)` |
| **延迟执行与物化** | `materialize()` + `FlumeJava.done()` | 显式在 `Pipeline p = Pipeline.create(opts)` 上构图，末尾调 **`p.run()`** |

---

## 2. 核心代码 1:1 翻译对照（Before vs After）

### 2.1 WordCount / 聚合管道对比

```java
// ❌ 脑海里的 FlumeJava 影子（外部面试别这么写）：
// PCollection<String> lines = FlumeJava.readTextFile(...);
// PTable<String, Long> counts = lines
//     .parallelDo(new ExtractWordsFn(), PTypes.tableOf(PTypes.strings(), PTypes.longs()))
//     .combineValues(Combines.sumLongs());
// FlumeJava.done();

// ✅ 开源 Apache Beam 标准写法：
Pipeline p = Pipeline.create(options);

PCollection<KV<String, Long>> counts = p
    .apply("Read", TextIO.read().from(inputPath))
    .apply("Split", FlatMapElements.into(TypeDescriptors.strings())
        .via((String line) -> Arrays.asList(line.strip().split("\\s+"))))
    .apply("FilterEmpty", Filter.by((String w) -> !w.isEmpty()))
    .apply("CountWords", Count.perElement());        // 或 WithKeys + Combine.perKey(Sum.ofLongs())

p.run().waitUntilFinish();
```

### 2.2 带监控 Counter、Side Input 与连接池的 `DoFn`

```java
static class EnrichFn extends DoFn<KV<String, Order>, EnrichedOrder> {
    // Beam 内置指标（可在 Dataflow / Flink / Spark UI 直接查看）
    private final Counter missingDim = Metrics.counter(EnrichFn.class, "missing_user_dim");
    private final Distribution latencyMs = Metrics.distribution(EnrichFn.class, "rpc_latency_ms");

    private final PCollectionView<Map<String, UserProfile>> userSideView;
    private transient ExternalClient client;         // 不可序列化资源一律标 transient，在 @Setup 创建

    EnrichFn(PCollectionView<Map<String, UserProfile>> userSideView) {
        this.userSideView = userSideView;
    }

    @Setup
    public void initClient() {
        this.client = ExternalClient.create();
    }

    @ProcessElement
    public void process(@Element KV<String, Order> kv, ProcessContext ctx, OutputReceiver<EnrichedOrder> out) {
        Map<String, UserProfile> dim = ctx.sideInput(userSideView);
        UserProfile user = dim.get(kv.getKey());
        if (user == null) {
            missingDim.inc();
            return;
        }
        long t0 = System.currentTimeMillis();
        String tag = client.lookupTag(kv.getValue().itemId());
        latencyMs.update(System.currentTimeMillis() - t0);
        out.output(new EnrichedOrder(kv.getValue(), user, tag));
    }

    @Teardown
    public void closeClient() {
        if (client != null) client.close();
    }
}
```

### 2.3 表 Join：想念 `PTable.join()` 时的两种写法

1. **标准底层写法**：`KeyedPCollectionTuple.of(t1, p1).and(t2, p2).apply(CoGroupByKey.create())`（手写编码轮零外部依赖，最稳妥）。
2. **还原 Flume 体验的扩展库**（`beam-sdks-java-extensions-join-library`）：
   ```java
   // import org.apache.beam.sdk.extensions.joinlibrary.Join;
   PCollection<KV<String, KV<Order, Payment>>> joined =
       Join.leftOuterJoin(ordersKv, paymentsKv, Payment.EMPTY_SENTINEL);
   ```

---

## 3. 把你的 FlumeJava / MillWheel 经验无缝翻译给 Spark / Flink 面试官

外部很多公司的 DE 栈并不是 Beam + Dataflow，而是 **Spark（批/微批）+ Flink（实时流）**。面试系统设计轮时，你熟悉的优化原理全部通用，只需**双向对齐术语**：

| 你熟悉的 FlumeJava / Dataflow 原理 | 开源 Beam 表达 | 对话 **Spark** 面试官时的同义词 | 对话 **Flink** 面试官时的同义词 |
|---|---|---|---|
| **MSCR 算子融合（Fusion Optimization）** | Stage Fusion（打破融合用 `Redistribute` / `Reshuffle`） | **Whole-Stage Codegen** & Pipelined Stage（打破用 `.repartition()`） | **Operator Chaining**（打破用 `.disableChaining()` / `.rebalance()`） |
| **Combiner Lifting（Shuffle 前局部预聚合）** | `Combine.perKey` 自动提升 | **`reduceByKey` / `aggregateByKey`**（对比万恶的 `groupByKey`） | Local-Global Two-Phase Aggregation（开启 MiniBatch + LocalGlobal） |
| **热 Key 加盐两阶段打散** | `.withHotKeyFanout(N)` | Salting Key（Key 拼随机前缀 `0..N-1` 两阶段聚合） | 两阶段 Key 加盐聚合 |
| **Liquid Sharding / 动态切分防长尾** | Dataflow Dynamic Work Rebalancing | **AQE（Adaptive Query Execution）** 自动合并小分区 & 自动拆倾斜分区 | Dynamic Rebalancing / FLIP-Elastic Scaling |
| **Side Input 小表广播关联** | `View.asMap()` + `.withSideInputs(...)` | **Broadcast Hash Join**（`broadcast(dimDf)`） | **Broadcast State Pattern**（广播流 `connect` 主流） |
| **MillWheel 按键持久化状态与低水位线** | `@StateId` + `@TimerId` + Watermark | Structured Streaming `flatMapGroupsWithState` + Watermark | `KeyedProcessFunction` + **RocksDB StateBackend** + Checkpoint |

---

## 4. Google 公开论文体系 ↔ 现代外部开源数据栈“脱敏对照表”

系统设计轮画架构图时，用右侧的外部工业界标准组件直接替换你脑海里的左侧模块：

| 数据栈层次 | Google 体系（公开论文组件） | 外部多云 / 开源 DE 标准栈 | 面试关键差异提醒 |
|---|---|---|---|
| **底层对象存储** | GFS / Colossus | **AWS S3 / GCS / ADLS** | 对象存储重命名非原子、列目录慢 → 催生了上层表格式 Iceberg/Delta |
| **文件存储格式** | RecordIO（行） / Capacitor（列） | **Avro**（行，适合 Kafka/CDC） / **Parquet & ORC**（列，适合数仓） | Parquet 自带 Row Group Min/Max 统计、字典编码与列裁剪 |
| **数据湖表格式（热门必考！）** | Mesa / Napa 表管理层 | **Apache Iceberg / Delta Lake / Hudi** | 给 S3 上的 Parquet 加上 **ACID 事务、快照隔离、Time Travel、Schema 演进与隐藏分区** |
| **消息总线 / 流接入** | Cloud Pub/Sub | **Apache Kafka** / AWS Kinesis / Pulsar | **重大区别**：Kafka 按 **Partition 严格保序**且支持按 Offset 回溯重放，但分区数限制消费者并发上限；Pub/Sub 是逐条 ACK、水平扩展无限但默认无严格顺序 |
| **流批计算引擎** | FlumeJava + MillWheel | **Apache Flink**（真流式首选） / **Apache Spark**（批与 ETL 霸主） / **Dataflow (Beam)** | 延迟毫秒~秒级复杂事件处理选 Flink；TB/PB 级离线宽表与交互式批处理选 Spark |
| **云原生数仓 / OLAP** | Dremel / Procella | **BigQuery / Snowflake / Databricks SQL**（云数仓）；**ClickHouse / StarRocks**（极速实时 OLAP）；**Trino**（联邦查询） | 存算分离是现代云数仓标配；高并发低延迟面向用户报表常在数仓后接一层 ClickHouse/StarRocks |
| **编排与建模层** | BorgCron / 内部 Workflow | **Apache Airflow / Dagster**（调度） + **dbt**（SQL 模块化建模、血缘与单测） | 外部现代数据团队极度推崇 **ELT + dbt**（先入湖仓，再用 SQL + Jinja 做分层 `bronze/silver/gold` 建模与数据质量断言） |
