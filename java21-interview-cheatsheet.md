# Java 21 实战速查 CheatSheet（刷题 · OOP · 工程复健）

**基线：** 从 Java 11 升级到 **Java 21 LTS**（LeetCode = OpenJDK 25，HackerRank = Java 21，主流生产环境 = Java 21/25）。
**Java 12→21 核心增量：** `record`（局部定义 / 紧凑构造 / 解构模式）、`sealed`、新 `switch`（返回值 / 模式匹配 / `when` / `case null`）、`instanceof` 流式收窄、文本块 `"""`、`stream().toList()`、`SequencedCollection / SequencedMap`、**虚拟线程（Virtual Threads）**。
**跨平台提醒：** `_` 未命名变量（Java 22）和 `void main()`（Java 25）在纯 Java 21 环境（如 HackerRank）未开 preview 编不过，但在 LeetCode（JDK 25）与新版工程中已转正。

---

## 1. 代码骨架

**LeetCode：只填方法，不改签名，返回而不是打印**

```java
import java.util.*;

class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            Integer j = seen.get(target - nums[i]);
            if (j != null) return new int[]{j, i};
            seen.put(nums[i], i);
        }
        return new int[0];
    }
}
```

**HackerRank / ACM：自己写 `main`（类名按平台：`Main` 或 `Solution`）**

```java
import java.io.*;
import java.util.*;

public class Main {
    static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    static StringTokenizer st;

    static String next() throws IOException {
        while (st == null || !st.hasMoreTokens()) st = new StringTokenizer(br.readLine());
        return st.nextToken();
    }

    public static void main(String[] args) throws IOException {
        int n = Integer.parseInt(next());
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = Integer.parseInt(next());

        StringBuilder out = new StringBuilder();
        out.append(solve(a)).append('\n');
        System.out.print(out);
    }

    static long solve(int[] a) { return 0; }
}
```

<details>
<summary><b>Scanner 版（数据 &lt; 10^5）</b></summary>

```java
Scanner sc = new Scanner(System.in);
int n = sc.nextInt();
sc.nextLine();                 // nextInt() 后要读整行，先吞掉换行
String line = sc.nextLine();
int[] a = new int[n];
for (int i = 0; i < n; i++) a[i] = sc.nextInt();
```

HackerRank 给了 `Result` 类和输入解析时，只填方法，不改成读 stdin。

</details>

---

## 2. 基础语法易错点

| 要做的事 | 写法 | 易错 |
|---|---|---|
| 长度 | 数组 `a.length`，字符串 `s.length()`，集合 `list.size()` | 三个别混 |
| 取字符 | `s.charAt(i)` / `char[] cs = s.toCharArray()` | 不能 `s[i]` |
| 子串 | `s.substring(l, r)` | 左闭右开 |
| 字符转数字 | `c - '0'`，字母 `c - 'a'` | `(int) c` 是 ASCII |
| 数字转字符 | `(char) ('a' + i)` | `char + int` 得 `int` |
| 字符串比较 | `s.equals(t)` / `Objects.equals(s, t)` | 不用 `==` |
| 循环拼接 | `StringBuilder` | `s += c` 是 $O(n^2)$ |
| 乘法溢出 | `long x = (long) a * b;` | `long x = a * b` 先按 int 算 |
| 除法 | `int` 截断；`(double) a / b` | `-7 / 2 == -3`，`(-1) % 3 == -1` |
| 非负取模 | `Math.floorMod(x, m)` | `(x % m + m) % m` 在大模数下可能 `int` 溢出 |
| 包装类比较 | `Objects.equals(a, b)` / `a.equals(b)` | 自动装箱默认仅缓存 $-128 \sim 127$，超出范围 `==` 比较引用为 false；遇 `null` 拆箱抛 NPE |
| 泛型 | `List<Integer>` | 不能 `List<int>` |
| 返回数组 | `return new int[]{a, b};` | `{a, b}` 只在声明时合法 |
| 值传递 | `nums[0] = 99` 调用方可见；`nums = new int[]{}` 不可见 | |
| 局部变量 | 必须初始化 | 数组/字段有默认值 |

```java
// String & Math（含 Java 15-21 新增小工具）
s.indexOf(x)  s.indexOf(x, from, to)  s.strip()  s.toLowerCase()  s.startsWith(p)
String[] words = s.isBlank() ? new String[0] : s.strip().split("\\s+"); // 防前导空白/空串产生 ""
String.join(",", list)  "%s %.2f".formatted(name, price)  "ab".repeat(3)
Character.isDigit(c)  Character.isLetterOrDigit(c)  Character.toLowerCase(c)
Integer.bitCount(7)  Math.clamp(val, min, max)  Integer.MAX_VALUE  Long.MAX_VALUE

// StringBuilder（勿在循环中用 sb.insert(0, x)，每次搬移 O(n)，统一 append 后 reverse）
sb.append(x)  sb.repeat("ab", 3)  sb.deleteCharAt(sb.length() - 1)  sb.reverse()
sb.setLength(len)   // 回溯恢复现场
sb.toString()

// 字母计数
int[] cnt = new int[26];
for (char c : s.toCharArray()) cnt[c - 'a']++;
```

---

## 3. 集合选型、Stream 与 API

| 需求 | 声明 | 必背方法 |
|---|---|---|
| 动态数组 | `List<Integer> l = new ArrayList<>();` | `add` `get` `set` `size` `remove(int idx)` `removeLast()` |
| 哈希表 | `Map<K,V> m = new HashMap<>();` | `getOrDefault` `merge` `computeIfAbsent` `putIfAbsent` `entrySet` |
| 哈希集合 | `Set<T> s = new HashSet<>();` | `add`（返回 boolean 可判重）`contains` `remove` |
| 栈 | `Deque<Integer> st = new ArrayDeque<>();` | `push` `pop` `peek`（操作队头，遍历 `st` 是栈顶→栈底） |
| 队列 | `Queue<Integer> q = new ArrayDeque<>();` | `offer` `poll` `peek` `isEmpty` |
| 双端队列 | `Deque<Integer> dq = new ArrayDeque<>();` | `offerFirst/Last` `pollFirst/Last` `peekFirst/Last` |
| 堆 | `PriorityQueue<Integer> pq = new PriorityQueue<>();` | `offer` `poll` `peek`，**默认小顶堆** |
| 保序哈希 / LRU | `SequencedMap<K,V> m = new LinkedHashMap<>();` | `putLast` `pollFirstEntry` `firstEntry`（Java 21）；`LinkedHashSet` 有 `addLast/removeFirst` |
| 有序表 | `TreeMap` / `TreeSet` | `floorKey` `ceilingKey` `firstKey` `pollFirstEntry`；`floor` `ceiling` `lower` `higher` |

不用 `Stack`、`Vector`；队列/栈统一 `ArrayDeque`（例外：需入队 `null` 节点如二叉树序列化时用 `LinkedList`）。

```java
// Map 默认值四件套（对标 C++ mp[k] / Python defaultdict）
int c = cnt.getOrDefault(x, 0);                                       // 1. 只读兜底（不写入 map）
for (int v : graph.getOrDefault(u, List.of())) { /* 孤立点安全跳过 */ }
cnt.merge(x, 1, Integer::sum);                                        // 2. 累加 / 聚合（单次查表）
dp.merge(state, score, Integer::max);                                 //    稀疏 DP 取 max/min，免填 INF
cnt.merge(out, -1, (oldV, d) -> oldV + d == 0 ? null : oldV + d);     //    滑窗减 1 且归零自动删 key
graph.computeIfAbsent(u, k -> new ArrayList<>()).add(v);              // 3. 容器懒初始化（Java 22+ 可写 _ ->）
firstIdx.putIfAbsent(prefixSum, i);                                   // 4. 仅记录首次出现位置
for (var e : cnt.entrySet()) { e.getKey(); e.getValue(); }

// 堆
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
record Node(int id, long dist) {}
PriorityQueue<Node> pq2 = new PriorityQueue<>(Comparator.comparingLong(Node::dist));

// 排序
Arrays.sort(a);                                                   // int[] 只能升序
Arrays.sort(intervals, (x, y) -> Integer.compare(x[0], y[0]));   // int[][] 可传比较器
Arrays.sort(intervals, (x, y) -> x[0] != y[0] ? Integer.compare(x[0], y[0]) : Integer.compare(y[1], x[1]));
Integer[] boxed = {3, 1, 2};
Arrays.sort(boxed, Collections.reverseOrder());                   // 降序要包装类型
list.sort(Comparator.comparingInt(Person::age).thenComparing(Person::name, Comparator.reverseOrder()));
list.sort(Comparator.comparingInt(String::length).reversed());
Arrays.sort(intervals, Comparator.comparingInt((int[] x) -> x[0]).reversed()); // 链 .reversed() 时 Lambda 须显式标类型

// 数组工具
Arrays.fill(a, -1);
for (int[] row : dp) Arrays.fill(row, -1);                        // 二维逐行填
Arrays.copyOf(a, n);  Arrays.copyOfRange(a, l, r);                // [l, r)
Arrays.toString(a);  Arrays.deepToString(dp);
Arrays.stream(a).asLongStream().sum();                            // 直接 sum() 返回 int 易溢出
Arrays.stream(a).max().orElseThrow();                             // 空数组时抛 NoSuchElementException

// 互转
int[] arr = list.stream().mapToInt(Integer::intValue).toArray();
List<Integer> l2 = new ArrayList<>(Arrays.stream(arr).boxed().toList());
String[] sa = strList.toArray(new String[0]);

// 不可变小集合（不许 null）
List<Integer> xs = List.of(1, 2, 3);
Set<Character> vowels = Set.of('a', 'e', 'i', 'o', 'u');
Map<String, Integer> mm = Map.of("I", 1, "V", 5);

// 辅助结构
record Pair(int x, int y) {}      // 自动 x() y() equals hashCode toString
```

**集合的坑：**
- `List.of()`、`stream().toList()` 完全不可变（`set` 也报错，且 `List.of` 禁 `null`）；`Arrays.asList()` 不能增删但可 `set`（联动改原数组，允许 `null`）。要增删统一 `new ArrayList<>(...)`
- `list.remove(1)` 删下标；`list.remove(Integer.valueOf(1))` 删值
- `ArrayDeque` 不能放 `null`；`poll/peek` 空时返回 `null`，拆箱成 `int` 会 NPE；`pop` 空栈抛异常
- `Arrays.asList(new int[]{1,2,3})` 得到只有一个元素的列表
- for-each 里增删 → `ConcurrentModificationException`，用 `removeIf` 或 `Iterator.remove()`
- 勿写 `map.getOrDefault(u, new ArrayList<>()).add(v)`（缺 key 时加在临时列表上未入表），建图必用 `computeIfAbsent`
- 勿用 `map.computeIfAbsent(state, s -> dfs(s))` 写递归记忆化（Java 9+ 内层递归改同一 `HashMap` 直接抛 `ConcurrentModificationException`；记忆化老实用 `get` 判 `null` + `put`）
- `HashMap/HashSet` 无序；遍历 `PriorityQueue` 不等于有序
- `new ArrayList<>(old)` 是浅拷贝；二维数组 `clone()` 只拷外层

### Stream 与 `Collectors` 实战管道

```java
// 注：非 LeetCode 环境需显式 import java.util.stream.*;

// 1. 常用流源构造（告别手写下标循环）
IntStream.range(0, n);                                              // [0, n) 下标流；rangeClosed(1, n) 为 [1, n]
Arrays.stream(a, l, r);                                             // 原生支持数组子区间 [l, r) 切片流，零拷贝
Stream.iterate(head, Objects::nonNull, p -> p.next);                // Java 9+ 有界迭代（如一行把链表变 Stream）
Stream.ofNullable(maybeNull);                                       // Java 9+：null 变空流，配合 flatMap 自动滤空

// 2. 链式转换精华（含 Java 9-16 增量）
List<Integer> topK = nestedLists.stream()
    .flatMap(Collection::stream)                                    // 展平 Stream<List<T>> -> Stream<T>
    .filter(x -> x > 0)
    .distinct()
    .sorted(Comparator.reverseOrder())
    .takeWhile(x -> x >= 10)                                        // Java 9+：有序流不满足条件即短路停止（另有 dropWhile）
    .limit(k)
    .toList();                                                      // Java 16+ 不可变 List；要可变用 .collect(Collectors.toCollection(ArrayList::new))

// Java 16+ mapMulti：1 对 0/1/多转换，省去 flatMap 每次创建临时子 Stream 的开销
List<Integer> parsed = rawStrings.stream()
    .<Integer>mapMulti((s, sink) -> { if (!s.isBlank()) sink.accept(Integer.parseInt(s.strip())); })
    .toList();

// 3. Collectors 工程与刷题王牌组合
// (a) 分组与多级下游聚合 groupingBy
Map<String, List<User>> byDept = users.stream()
    .collect(Collectors.groupingBy(User::dept));

Map<Character, Long> charFreq = s.chars().mapToObj(c -> (char) c)
    .collect(Collectors.groupingBy(c -> c, Collectors.counting())); // 一行统计字符频率

Map<String, List<Long>> idsByDept = users.stream()
    .collect(Collectors.groupingBy(User::dept,
        Collectors.mapping(User::id, Collectors.toList())));        // 分组后只提取特定字段（另有 summingLong / maxBy）

// (b) 布尔二分桶 partitioningBy（单次遍历拆成 true / false 两组）
Map<Boolean, List<User>> passOrFail = users.stream()
    .collect(Collectors.partitioningBy(u -> u.score() >= 60));

// (c) 转字典 toMap（第 3 参数处理重复 key，第 4 参数可传 LinkedHashMap::new 保序）
Map<Long, User> userMap = users.stream()
    .collect(Collectors.toMap(User::id, u -> u, (oldV, newV) -> newV));

// (d) 字符串拼接 joining（带分隔符、前缀、后缀）
String csv = nums.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));

// (e) 双路同时聚合 teeing（Java 12+：单次流遍历同时跑两个收集器再合并结果）
record MinMax(int min, int max) {}
MinMax mm = nums.stream().collect(Collectors.teeing(
    Collectors.minBy(Integer::compare),
    Collectors.maxBy(Integer::compare),
    (minOpt, maxOpt) -> new MinMax(minOpt.orElseThrow(), maxOpt.orElseThrow())
));

// 4. 短路判定与数值汇总
boolean hasNeg = Arrays.stream(a).anyMatch(x -> x < 0);             // 另有 allMatch（注意：空流返回 true！）/ noneMatch
IntSummaryStatistics stats = nums.stream().mapToInt(Integer::intValue).summaryStatistics(); // 一次拿 count/sum/min/max/avg
```

**Stream 避坑边界：**
- **`Collectors.toMap` 两大线上故障点**：① 双参数版遇重复 key 直接抛 `IllegalStateException`（不确定唯一必写第 3 参数合并函数）；② **value 为 `null` 时必抛 `NullPointerException`**（底层调 `Map.merge` 禁 `null`）。
- **防自动装箱**：大量数值聚合优先走 `IntStream` / `LongStream`（`mapToInt` / `mapToLong`），避免 `Stream<Integer>.reduce` 反复拆装箱。
- **何时果断写回普通 `for`**：需要原地改多个外部局部变量（Lambda 要求捕获变量 effectively final）、双指针/回溯复杂跳出、或内部需抛受检异常（`IOException`）时，别硬套 Stream。

---

## 4. 算法模板

**二分：左闭右开 lowerBound**

```java
int lowerBound(int[] a, int target) {          // 第一个 >= target 的下标；全小于则返回 a.length
    int lo = 0, hi = a.length;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (a[mid] < target) lo = mid + 1;
        else hi = mid;
    }
    return lo;
}
// 判存在：idx < a.length && a[idx] == target
// 对答案二分：把 a[mid] < target 换成 !check(mid)；若值域含负数或跨度大，lo/hi/mid 改用 long 防 hi - lo 溢出
```

**二分：闭区间找目标**

```java
int lo = 0, hi = n - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    if (a[mid] == target) return mid;
    if (a[mid] < target) lo = mid + 1;
    else hi = mid - 1;
}
return -1;
```

**网格 BFS（分层，求最短步数）**

```java
static final int[][] DIRS = {{1,0},{-1,0},{0,1},{0,-1}};

int bfs(char[][] g, int sr, int sc) {
    int m = g.length, n = g[0].length;
    boolean[][] seen = new boolean[m][n];
    Queue<int[]> q = new ArrayDeque<>();
    q.offer(new int[]{sr, sc});
    seen[sr][sc] = true;
    for (int step = 0; !q.isEmpty(); step++) {
        for (int k = q.size(); k > 0; k--) {
            int[] cur = q.poll();
            if (g[cur[0]][cur[1]] == 'E') return step;
            for (int[] d : DIRS) {
                int r = cur[0] + d[0], c = cur[1] + d[1];
                if (r < 0 || r >= m || c < 0 || c >= n || seen[r][c] || g[r][c] == '#') continue;
                seen[r][c] = true;                   // 入队时标记
                q.offer(new int[]{r, c});
            }
        }
    }
    return -1;
}
```

**图 BFS（无权最短距离）**

```java
int[] dist = new int[n];
Arrays.fill(dist, -1);
Queue<Integer> q = new ArrayDeque<>();
dist[start] = 0; q.offer(start);
while (!q.isEmpty()) {
    int u = q.poll();
    for (int v : g.get(u)) {
        if (dist[v] != -1) continue;
        dist[v] = dist[u] + 1;
        q.offer(v);
    }
}
```

**回溯（子集）**

```java
void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> res) {
    res.add(new ArrayList<>(path));                  // 必须拷贝
    for (int i = start; i < nums.length; i++) {
        path.add(nums[i]);
        backtrack(nums, i + 1, path, res);
        path.remove(path.size() - 1);                // 或 path.removeLast()
    }
}
```

**滑动窗口（无重复最长子串）**

```java
int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> last = new HashMap<>();
    int best = 0;
    for (int l = 0, r = 0; r < s.length(); r++) {
        char c = s.charAt(r);
        if (last.containsKey(c)) l = Math.max(l, last.get(c) + 1);
        last.put(c, r);
        best = Math.max(best, r - l + 1);
    }
    return best;
}
// 通用：for r { 加入 s[r]; while (不合法) { 移除 s[l]; l++; } 更新 ans; }
// 有负数时滑窗可能失效，改前缀和 + HashMap
```

<details>
<summary><b>并查集、邻接表、拓扑排序、Dijkstra</b></summary>

```java
class DSU {
    int[] p, sz;
    DSU(int n) { p = new int[n]; sz = new int[n]; for (int i = 0; i < n; i++) { p[i] = i; sz[i] = 1; } }
    int find(int x) { return p[x] == x ? x : (p[x] = find(p[x])); }
    boolean union(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra == rb) return false;
        if (sz[ra] < sz[rb]) { int t = ra; ra = rb; rb = t; }
        p[rb] = ra; sz[ra] += sz[rb];
        return true;
    }
}

// 邻接表
List<List<Integer>> g = new ArrayList<>();
for (int i = 0; i < n; i++) g.add(new ArrayList<>());
g.get(u).add(v);

// Kahn 拓扑排序（课程表）
int[] indeg = new int[n];
for (int[] p : pre) { g.get(p[1]).add(p[0]); indeg[p[0]]++; }
Queue<Integer> q = new ArrayDeque<>();
for (int i = 0; i < n; i++) if (indeg[i] == 0) q.offer(i);
int done = 0;
while (!q.isEmpty()) {
    int u = q.poll(); done++;
    for (int v : g.get(u)) if (--indeg[v] == 0) q.offer(v);
}
boolean ok = done == n;

// Dijkstra，g.get(u) 存 {v, w}
record State(int node, long dist) {}
long[] dijkstra(List<List<int[]>> g, int src) {
    long[] dist = new long[g.size()];
    Arrays.fill(dist, Long.MAX_VALUE);
    dist[src] = 0;
    PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparingLong(State::dist));
    pq.offer(new State(src, 0));
    while (!pq.isEmpty()) {
        State cur = pq.poll();
        if (cur.dist() > dist[cur.node()]) continue;          // 过期条目跳过
        for (int[] e : g.get(cur.node())) {
            long nd = cur.dist() + e[1];
            if (nd < dist[e[0]]) { dist[e[0]] = nd; pq.offer(new State(e[0], nd)); }
        }
    }
    return dist;
}
```

</details>

<details>
<summary><b>树、链表、单调栈、DP、Trie</b></summary>

```java
// 树递归
int maxDepth(TreeNode root) {
    if (root == null) return 0;
    return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
}

// 迭代中序
List<Integer> res = new ArrayList<>();
Deque<TreeNode> st = new ArrayDeque<>();
for (TreeNode cur = root; cur != null || !st.isEmpty(); ) {
    while (cur != null) { st.push(cur); cur = cur.left; }
    cur = st.pop(); res.add(cur.val); cur = cur.right;
}

// 反转链表
ListNode prev = null;
while (head != null) { ListNode nx = head.next; head.next = prev; prev = head; head = nx; }
return prev;
// 删/插节点先建 dummy：ListNode dummy = new ListNode(0, head);

// 单调栈：下一个更大元素
int[] res = new int[a.length]; Arrays.fill(res, -1);
Deque<Integer> stk = new ArrayDeque<>();                     // 存下标
for (int i = 0; i < a.length; i++) {
    while (!stk.isEmpty() && a[stk.peek()] < a[i]) res[stk.pop()] = a[i];
    stk.push(i);
}

// 零钱兑换
int[] dp = new int[amount + 1];
Arrays.fill(dp, amount + 1); dp[0] = 0;
for (int i = 1; i <= amount; i++)
    for (int c : coins) if (c <= i) dp[i] = Math.min(dp[i], dp[i - c] + 1);
return dp[amount] > amount ? -1 : dp[amount];

// Trie
class Trie {
    Trie[] next = new Trie[26]; boolean end;
    void insert(String w) {
        Trie cur = this;
        for (char c : w.toCharArray()) {
            if (cur.next[c - 'a'] == null) cur.next[c - 'a'] = new Trie();
            cur = cur.next[c - 'a'];
        }
        cur.end = true;
    }
    boolean search(String w) {
        Trie cur = this;
        for (char c : w.toCharArray()) {
            cur = cur.next[c - 'a'];
            if (cur == null) return false;
        }
        return cur.end;
    }
}
```

</details>

**题型 → 数据结构**

| 信号 | 用 |
|---|---|
| 查重、计数、补数 | `HashSet` / `HashMap` |
| 有序数组、单调判定、对答案二分 | 二分 |
| 连续子数组 / 子串 | 滑动窗口、前缀和 |
| 最近更大 / 更小元素 | 单调栈 |
| Top-K、合并有序 | 堆 |
| 连通性、最短路 | BFS / DFS |
| 所有组合、排列 | 回溯 |
| 重叠子问题、最优解 | DP |

**复杂度口述：** 排序 $O(n \log n)$，二分 $O(\log n)$，BFS/DFS $O(V+E)$，堆 Top-K $O(n \log k)$，子集 $O(n \cdot 2^n)$。

---

## 5. Java 11 → 21 核心新特性（复健重点）

### 5.1 模式匹配与新 `switch`（干掉强转、`break` 与外层判空）

```java
// 1. instanceof 流式收窄（Java 16）：不仅 if 内能用，卫语句提前 return 后下方也直接生效
if (!(obj instanceof String s)) return;
System.out.println(s.strip());                   // s 在此处已是 String，无需强转

// 2. 新 switch 当语句用（不返回值，末尾无分号）：天然无穿透，告别 break；箭头后可接单句、{} 块或 throw
switch (cmd) {
    case 'U' -> y++;
    case 'D' -> y--;
    case 'L', 'R' -> { log(cmd); moveX(cmd); }
    default -> throw new IllegalArgumentException("bad cmd: " + cmd);
}

// 3. 新 switch 当表达式用（必须穷尽，右花括号后必须带分号 };）：多行块用 yield 产值
int days = switch (month) {
    case 1, 3, 5, 7, 8, 10, 12 -> 31;
    case 4, 6, 9, 11 -> 30;
    case 2 -> 28;
    default -> { log("bad: " + month); yield 0; }
};

// 4. switch 类型匹配 + case null + when 守卫 + Record 嵌套解构（Java 21）
record Point(int x, int y) {}
record Line(Point from, Point to) {}

String describe(Object obj) {
    return switch (obj) {
        case String s when s.isBlank() -> "blank str";            // 更具体的守卫/子类必须写在宽泛分支前面！
        case String s -> "str: " + s;
        case Line(Point(int x1, int y1), Point(int x2, int y2))   // 嵌套解构直接拿到内部字段
            -> "(%d,%d)->(%d,%d)".formatted(x1, y1, x2, y2);
        case null, default -> "other or null";                    // 合并接住 null 与默认分支
    };
}
```

**新 `switch` 必知防坑：**
- **`default` 不会自动接 `null`**：未显式写 `case null`（或 `case null, default ->`）时，传入 `null` 照样抛 `NullPointerException`。
- **分支支配顺序（Dominance）**：带 `when` 的分支必须排在同类型无守卫分支前面，子类必须排在父类前面，否则编译报错。
- **`enum` / `sealed` 优先省去 `default`**：覆盖全部分支即算穷尽，不写 `default` 能让编译器在未来新增枚举值时自动抓漏。
- **选择器类型边界**：Java 21 基本类型仍只支持 `byte / short / char / int`（不能直接 `switch (long / double / boolean)`）。

### 5.2 `record` 进阶（Java 16：局部定义 + 紧凑构造器）

```java
// 紧凑构造器（Compact Constructor）：省略形参列表和末尾赋值，专心做校验与防御性拷贝
public record Order(String id, List<String> items, int amount) {
    public Order {
        if (amount < 0) throw new IllegalArgumentException("negative amount");
        items = List.copyOf(items);              // 归一化后自动赋给 this.items，实现深度不可变
    }
}
// 方法内也可随时写局部 record（如 record Pair(int r, int c) {}），隐式 static，刷题零样板
```

### 5.3 集合、Stream 与文本块增量（Java 15–21）

```java
// Stream 直接转不可变 List（Java 16，告别 .collect(Collectors.toList())）
List<String> names = users.stream().map(User::name).toList();     // 允许 null，不可增删/set

// SequencedCollection / SequencedSet / SequencedMap（Java 21 JEP 431）
list.getFirst();  list.getLast();  list.removeLast();             // 空时抛 NoSuchElementException
list.reversed();                                                  // O(1) 逆序视图，不改原列表

var lruSet = new LinkedHashSet<String>();
lruSet.addLast("a");                                              // 若已存在，自动移到队尾！

var seqMap = new LinkedHashMap<String, Integer>();
seqMap.putLast("k", 1);                                           // 插入或更新值，并移到队尾
var oldest = seqMap.pollFirstEntry();                             // O(1) 弹出队头 Map.Entry

// 文本块（Java 15）：结束 """ 的列位置决定公共缩进剥离基准；行尾加 \ 可折行但不插入换行符
String json = """
        {
          "user": "%s",
          "role": "admin"
        }
        """.formatted(username);
```

### 5.4 工程并发与 IO：虚拟线程（Java 21 JEP 444）

工程题不再手搓复杂线程池或写 `CompletableFuture` 回调链，回归**直观同步阻塞写法**：

```java
// 并发扇出聚合（ExecutorService 在 Java 19+、HttpClient 在 Java 21 均已实现 AutoCloseable）
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<User> userF = exec.submit(() -> fetchUser(uid));
    Future<List<Order>> ordersF = exec.submit(() -> fetchOrders(uid));
    return new Profile(userF.get(), ordersF.get()); // 阻塞虚拟线程极轻量，自动挂载/卸载载体线程
} // 出 try 块自动等待所有子任务完成并关闭

// 单任务快速启动
Thread vt = Thread.ofVirtual().name("job-", 0).start(() -> handle(req));
```

- **工程铁律 1（不池化）**：虚拟线程用完即弃，**绝不建固定大小虚拟线程池**；下游限流用 `Semaphore`。
- **工程铁律 2（防 Pinning）**：在 **Java 21** 中，若阻塞 IO 包在 `synchronized` 内会钉死底层 OS 线程，高并发锁内有 IO 时改用 **`ReentrantLock`**（注：Java 24 JEP 491 已彻底消除该限制）。
- **文件与编码**：Java 18 起全平台默认字符集统一为 **UTF-8**；读写小文件直接 `Files.readString(Path.of("a.txt"))` / `Files.writeString(...)`。

<details>
<summary><b>Vibe Coding 防懵速览：AI 常生成的 Java 22 → 25 LTS 新语法</b></summary>

```java
// 1. 未命名变量与模式 _（Java 22 正式；HackerRank Java 21 勿用，LeetCode JDK 25 可用）
map.computeIfAbsent(k, _ -> new ArrayList<>()).add(v);
try { Integer.parseInt(s); } catch (NumberFormatException _) { return -1; }
if (obj instanceof Point(int x, _)) System.out.println(x);

// 2. Stream Gatherers（Java 24 正式）：官方内置滑动窗口、分块、前缀扫描
List<List<Integer>> windows = nums.stream().gather(Gatherers.windowSliding(3)).toList();
List<List<Integer>> batches = nums.stream().gather(Gatherers.windowFixed(100)).toList();

// 3. 灵活构造器体（Java 25 正式）：super(...) 前终于允许先写校验和计算逻辑
class PositiveBigInt extends BigInteger {
    PositiveBigInt(long val) {
        if (val <= 0) throw new IllegalArgumentException(); // Java 21 编译报错，Java 25 合法
        super(Long.toString(val));
    }
}

// 4. 紧凑源文件与实例 main（Java 25 正式）：单文件脚本可省 class 声明与 static String[] args
void main() { IO.println("Hello Java 25"); }
```

</details>

---

## 6. OOP 与工程建模

### 关键字直觉

| 语法 | 想到什么 |
|---|---|
| `private` + 构造器 | 隐藏状态，构造时保证初始状态合法；`this.x = x` |
| `final` 字段 | 引用只赋一次，不代表集合/数组内容不可变（对外返回用 `List.copyOf`） |
| `static` | 属于类不属于实例；静态内部类不持有外部类引用 |
| `interface` / `implements` | 可替换的行为契约；实现方法必须是 `public`；可含 `default` / `static` 方法 |
| `abstract class` / `extends` | 共享状态 + 模板骨架；单继承，子类构造器先走 `super(...)`（Java 21） |
| `record` | 不可变数据载体（隐式 `final`），自动生成构造器、`x()`、`equals` / `hashCode` / `toString` |
| `sealed` ... `permits` | 受限继承层级，配合 `switch` 模式匹配可免 `default` 做编译器穷尽检查 |
| `@Override` | 重写必加，防止把 `equals(Object)` 等签名误写成重载 |

### 手写类与领域建模要点

- **面向接口声明，组合优于继承**：字段和返回值优先写 `List<T>` / `Map<K, V>` 而非具体实现类。
- **自定义对象做 `HashMap` 键 / 放 `HashSet`**：无可变状态直接用 `record`；手写 `class` 必须**同时**重写 `equals` 和 `hashCode`。
- **用 `sealed + record` 替代脆弱的状态枚举/错误码**（现代 Java 最地道的代数数据类型 ADT）：

```java
// 1. Java 21 模式匹配手写 equals & hashCode
class Point {
    final int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Point p && x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}

// 2. 工程建模：sealed interface + record 表达 Result<T> / 状态机 / 表达式树
sealed interface Result<T> permits Result.Ok, Result.Err {
    record Ok<T>(T data) implements Result<T> {}
    record Err<T>(int code, String msg) implements Result<T> {}
}

String render(Result<String> r) {
    return switch (r) {                                  // 编译器穷尽检查，无需 default
        case Result.Ok(var data) -> "200: " + data;
        case Result.Err(int code, var msg) -> code + ": " + msg;
    };
}
```

<details>
<summary><b>高频 OOD 模板：LRU Cache（Java 21 SequencedMap 组合写法）</b></summary>

```java
// Java 21 LinkedHashMap 原生支持 putLast / pollFirstEntry，用组合即可，告别重写 removeEldestEntry
// （注：算法面试若面试官禁止直接用 LinkedHashMap，则手写 Node + 哨兵双向链表 + HashMap）
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

</details>
