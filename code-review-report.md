# LeetCode 近期提交 Code Review 报告（Java 21 最佳实践视角）

> **评审范围**：`leetcode/` 目录下近期归档的 **7 道题目**  
> **评审基线**：Java 21 LTS 现代惯用法 + 算法边界安全性（防溢出、防爆栈、时空复杂度优化）

---

## 一、 总体画像：优势与 5 个共性升级点

从这 7 道题能明显看出你的**算法功底非常扎实**：树形多状态 DP、倍增（Binary Lifting）、自动机记忆化、单调消除栈都能手到擒来，且已经自觉用上了 `ArrayDeque`（替代 `Stack`）和 `List.of(...)`（不可变小列表）。

如果从 **「Java 11 老兵升级到 Java 21 高手」** 的角度挑刺，你的代码里有 **5 个高频共性习惯** 值得集中刷新：

| # | 当前习惯（C++ / 旧 Java 痕迹） | 潜在风险或痛点 | Java 21 推荐实践 |
|---|---|---|---|
| **1** | **位运算直接写 `1 << j` 且用 `> 0` 判位** | `int` 左移在 `j == 31` 变负数、`j >= 32` 按 `& 31` 回绕死循环/越界！ | 涉及高位的倍增/状压统一用 **`1L << j`**，判位写 **`((y >>> j) & 1) != 0`** |
| **2** | **手写非静态内部类 / 多个平行数组传状态** | 内部类隐式持有 `Solution.this` 引用；`case1/case2/case3` 平行数组靠注释记含义 | 方法内直接写一行**局部 `record`**（隐式 `static`）或紧凑 `static class` |
| **3** | **记忆化搜索用 `0` 当未访问哨兵 + 深递归** | 合法答案为 `0` 时记忆化退化重算；链状图/长串在 $N = 10^5$ 下易触发 `StackOverflowError` | 记忆化必 `Arrays.fill(row, -1)`；线性单向依赖直接改成**倒序迭代 + `switch ->` 表达式** |
| **4** | **分段扫描维护 `null` 状态 + 循环后补刀** | 循环内外写两遍相同的收集逻辑，极易漏写末尾收尾，且多出多余对象分配 | 改用经典的**「分组循环」（外 `for` + 内 `while`）** + `substring(start, i)` |
| **5** | **参数加下划线 `_n, _s1`、C 风格 `long dp[][]`** | Java 22+ 单 `_` 已是保留关键字，前缀 `_` 违背 Java 命名直觉；`dp[][]` 属 C 遗留语法 | 参数直接叫 `n, s1`（字段冲突用 `this.n = n`）；数组统一写 `long[][] dp`；判空用 `.isEmpty()` |

---

## 二、 逐题深度 Review 与 Java 21 重构对照

### 1. `RepeatedStringMatchII.java` — 倍增表中的致命 32 位移位隐患

- **原文件**：[RepeatedStringMatchII.java](./leetcode/RepeatedStringMatchII.java)
- **问题清单**：
  1. 🔴 **[高危 Bug / 整数溢出]**（L31, L54–L55）：你特意开了 `new int[40][n]` 准备支持超过 30 位的倍增，但循环条件却写了 `for (int j = 0; (1 << j) <= y; j++)` 和 `((1 << j) & y) > 0`：
     - 在 Java 中 `1` 是 32 位 `int`：`j = 31` 时 `1 << 31` 溢出为 `Integer.MIN_VALUE`（仍 `<= y`！），且 `(1 << 31) & y` 为负数导致 `> 0` 恒为 `false`；
     - `j = 32` 时 Java 会将移位位数对 32 取模（`1 << 32 == 1`），一旦 $y \ge 2^{30}$（约 $1.07 \times 10^9$），循环永远停不下来，直奔 `j = 40` 抛 `ArrayIndexOutOfBoundsException`！
  2. 🟡 **[代码坏味道]**（L47–L48）：`long bigA = n; bigA *= x;` 不仅写成了两句旧式防溢出，还放在了 `for (int i = 0; i < n; i++)` 循环内部重复算；L39 `int start = i;` 是未使用的死变量。
  3. 🟢 **[控制流精简]**（L8–L22）：`buildRoute` 里的 `boolean equal` 标志位完全多余，失配时直接 `return -1` 即可。

- **Java 21 改进写法（核心片段）**：
  ```java
  int buildRoute(char[] a, char[] b, int idx) {
      int n = a.length;
      for (char ch : b) {
          if (ch != a[idx]) return -1;
          if (++idx == n) idx = 0;
      }
      return idx;
  }

  // 查询阶段：提炼常量到循环外 + 用 long 移位 / Integer.numberOfLeadingZeros
  long totalA = (long) n * x;
  long totalB = (long) m * y;
  for (int i = 0; i < n && i + totalB <= totalA; i++) {
      int ptr = i;
      for (int rem = y; rem > 0 && ptr != -1; rem &= rem - 1) { // lowbit 枚举集合位，零溢出风险
          int bit = Integer.numberOfTrailingZeros(rem);
          ptr = tab[bit][ptr];
      }
      if (ptr != -1) return true;
  }
  ```

---

### 2. `CountValidSubstringsII.java` — 记忆化哨兵隐患、爆栈风险与 `switch` 表达式改造

- **原文件**：[CountValidSubstringsII.java](./leetcode/CountValidSubstringsII.java)
- **问题清单**：
  1. 🟠 **[记忆化反模式]**（L16, L33）：用 `if (dp[i][seen] > 0)` 判断是否算过，但当 `s[i] == 'c' && seen == 2` 时合法返回值恰为 **`0`**！本题之所以没超时，纯粹因为返回 `0` 的分支恰好没有继续递归；换一道返回 `0` 仍需向下递归的 DP 题，复杂度会瞬间退化成指数级 $O(2^n)$。**铁律：当 `0` 是合法状态值时，必须初始化 `-1` 并判 `!= -1`。**
  2. 🟠 **[深递归爆栈隐患]**（L51–L53）：虽然外层倒序枚举了 `i`，但外层只预热了 `seen = 0` 和 `seen = 1`；如果输入是 `"abbbbb...b"`（长度 $10^5$），外层扫到 `i = 0` 时 `seen = 2` 从未被算过，会一次性向深处递归 $10^5$ 层。既然 `(i, seen)` 只依赖 `i + 1`，直接写成**纯迭代滚动变量**只需 $O(1)$ 空间且快一个数量级。
  3. 🟢 **[语法清理 & Java 21 升级]**（L5, L10, L21–L38）：
     - L5 `long dp[][];` 应写为 `long[][] dp;`；
     - L10 注释残留了不存在的 `seen = 3`；L31 `int nextSeen = 0;` 在 `if` 分支里是死赋值；
     - L21–L38 的三层字符状态机非常适合用 **Java 21 `switch` 表达式**改写。

- **Java 21 改进写法（$O(n)$ 时间、$O(1)$ 空间、零递归 + 箭头 `switch`）**：
  ```java
  public long countValidSubstrings(String str) {
      long[] dp = new long[3]; // dp[seen] 表示从 i+1 出发、当前状态为 seen 的合法延伸数
      long ans = 0;

      for (int i = str.length() - 1; i >= 0; i--) {
          char ch = str.charAt(i);
          long[] next = new long[3];
          for (int seen = 0; seen < 3; seen++) {
              int s = seen;
              next[seen] = switch (ch) {
                  case 'a' -> 1 + dp[Math.max(s, 1)];
                  case 'b' -> 1 + dp[(s == 1 || s == 2) ? 2 : 0];
                  case 'c' -> s == 2 ? 0 : 1 + dp[s];
                  default -> throw new IllegalArgumentException("Unexpected char: " + ch);
              };
          }
          dp = next;
          ans += dp[ch == 'a' ? 1 : 0];
      }
      return ans;
  }
  ```

---

### 3. `RemoveElementsByTypeAndColor.java` — 内部类改造与冗余栈操作消除

- **原文件**：[RemoveElementsByTypeAndColor.java](./leetcode/RemoveElementsByTypeAndColor.java)
- **亮点**：自觉使用了 `Deque<StackElement> s = new ArrayDeque<>()` 而非废弃的 `Stack`。
- **问题清单**：
  1. 🟡 **[OOP / Java 21 惯用法]**（L7–L11, L18–L21）：`class StackElement` 没加 `static`（每个节点多存一个外部 `Solution.this` 指针），也没有构造器，导致入栈要手写 4 行字段赋值。
  2. 🟡 **[冗余栈操作]**（L24–L32）：在 `else` 分支里把栈顶 `pop()` 出来自增 `size` 再 `push()` 回去，紧接着下一行（L29）又立刻把它 `pop()` 出来检查 `size < groupSize` 再 `push()` 回去——每轮最多做了 **4 次出入栈**，其实直接读/改 `s.peek()` 最多只需 1 次操作！

- **Java 21 改进写法**：
  - 若追求零多余分配，用紧凑构造的局部类（Java 16+ 方法内可直接定义局部 `record`；这里由于要原地 `size++` 省对象分配，用带构造器的 `static final class` 最贴切，或用局部 `record Group(int type, int color, int size) {}` 不可变替换栈顶）：
  ```java
  public boolean canRemoveAll(int[] types, int[] colors, int[] groupSize) {
      // Java 21 方法内局部 record（不可变值语义，干净利落）
      record Group(int type, int color, int size) {}

      Deque<Group> st = new ArrayDeque<>();
      for (int i = 0; i < types.length; i++) {
          int t = types[i], c = colors[i];
          int newSize = (!st.isEmpty() && st.peek().type() == t && st.peek().color() == c)
                  ? st.pop().size() + 1
                  : 1;
          if (newSize < groupSize[t]) {
              st.push(new Group(t, c, newSize));
          }
      }
      return st.isEmpty();
  }
  ```

---

### 4. `MinimumCostToMakeRedNodesConnected.java` — 用局部 `record` 消灭平行 DP 数组

- **原文件**：[MinimumCostToMakeRedNodesConnected.java](./leetcode/MinimumCostToMakeRedNodesConnected.java)
- **问题清单**：
  1. 🟡 **[状态建模可读性]**（L10–L17）：用了 `case1`、`case2`、`case3`、`treeSize` 四个平行实例数组来传递子树 DP 结果，读到 L63–L68 时必须不断往上翻注释才能分清 `case2` 和 `case3` 谁是谁。在 Java 21 中，让 `dfs` 直接返回一个**具名局部 `record`**，既省掉 4 个实例数组，字段名本身就是注释！
  2. 🟡 **[死代码与多余分配]**（L21–L38）：
     - 每个节点都 `new ArrayList<>()` 拷贝一份 `children` 没必要，遍历 `adj.get(now)` 时跳过 `parent` 即可；
     - L28–L38 的叶子节点特判（`if (children.size() == 0)`，顺带提醒集合判空优先写 `children.isEmpty()`）**与 `else` 分支算出的结果完全一致**，整段 11 行特判可以直接删掉！
     - L62–L69 的两个 `for` 循环完全可以合并为一个：`Math.min(case2[child], case3[child])`。
  3. 🟢 **[术语与命名小节]**（L11, L73）：无向树里叫连通块（Connected Component），`SCC`（强连通分量）是专指有向图的术语；参数 `_n, _colors` 去掉前导下划线。

- **Java 21 改进写法（局部 `record` 自解释树形 DP）**：
  ```java
  public long minCost(int n, String colors, int[][] edges) {
      List<List<Integer>> adj = new ArrayList<>(n);
      for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
      for (int[] e : edges) {
          adj.get(e[0]).add(e[1]);
          adj.get(e[1]).add(e[0]);
      }

      // 用具名 record 替代 case1 / case2 / case3 / treeSize 四个平行数组
      record Subtree(int size, long allBlue, long redRoot, long doneBelow) {}

      class Solver {
          Subtree dfs(int u, int parent) {
              int size = 1;
              List<Subtree> subs = new ArrayList<>();
              for (int v : adj.get(u)) {
                  if (v != parent) {
                      Subtree sub = dfs(v, u);
                      size += sub.size();
                      subs.add(sub);
                  }
              }

              long flipSelfToBlue = colors.charAt(u) == 'R' ? size : 0;
              long flipSelfToRed = colors.charAt(u) == 'R' ? 0 : size;

              long sumAllBlue = 0;
              long redRoot = flipSelfToRed;
              for (Subtree s : subs) {
                  sumAllBlue += s.allBlue();
                  redRoot += Math.min(s.allBlue(), s.redRoot());
              }

              long allBlue = flipSelfToBlue + sumAllBlue;
              long doneBelow = allBlue;
              for (Subtree s : subs) {
                  long candidate = flipSelfToBlue + sumAllBlue - s.allBlue()
                          + Math.min(s.redRoot(), s.doneBelow());
                  doneBelow = Math.min(doneBelow, candidate);
              }
              return new Subtree(size, allBlue, redRoot, doneBelow);
          }
      }

      Subtree root = new Solver().dfs(0, -1);
      return Math.min(root.redRoot(), root.doneBelow());
  }
  ```

---

### 5. `CountValidSubstringsI.java` — 用 `startsWith` + 倒序单指针省掉整张下标表

- **原文件**：[CountValidSubstringsI.java](./leetcode/CountValidSubstringsI.java)
- **问题清单**：
  1. 🟡 **[多余装箱与空间开销]**（L7, L17）：把所有 `"abc"` 下标装箱存进 `List<Integer> abcIndex`，再用 `ptr` 从左到右扫；另外 `abcIndex.get(ptr) == i` 之所以没出错全靠右侧 `i` 是基本类型 `int` 触发了拆箱（若两边都是 `Integer` 在下标 `>= 128` 时就会踩引用比较大坑）。
  2. 🟢 **[API 与分支合一]**（L9, L17–L27）：
     - `s.charAt(i) == 'a' && s.charAt(i+1) == 'b' && s.charAt(i + 2) == 'c'` 可以直接用标准库 **`s.startsWith("abc", i)`**；
     - 仔细观察你的 `case #1`（`ans += 2`）和 `case #2`（`ans += (abcIndex + 2) - i`）：当 `abcIndex == i` 时，`(i + 2) - i` **本来就等于 `2`**！两个分支其实是同一个公式。

- **Java 21 改进写法（7 行，$O(1)$ 空间）**：
  ```java
  public long countValidSubstrings(String s) {
      int n = s.length();
      int nextAbc = n; // 右侧最近的 "abc" 起点
      long ans = 0;
      for (int i = n - 1; i >= 0; i--) {
          if (s.startsWith("abc", i)) nextAbc = i; // startsWith 自带越界保护，无需手判 i + 2 < n
          ans += Math.min(n, nextAbc + 2) - i;
      }
      return ans;
  }
  ```

---

### 6. `FindMismatchedSegmentsBetweenTwoStrings.java` — 用「分组循环」消灭 `null` 状态与循环外补刀

- **原文件**：[FindMismatchedSegmentsBetweenTwoStrings.java](./leetcode/FindMismatchedSegmentsBetweenTwoStrings.java)
- **亮点**：已经用上了简洁的 `List.of(sb1.toString(), sb2.toString())`。
- **问题清单**：
  1. 🟡 **[分组循环范式缺失]**（L11–L31）：维护 `sb1 == null` 状态导致循环里和循环结束后（L29–L31）必须写两遍一模一样的 `ret.add(List.of(...))`，面试手写时漏掉循环外补刀是最高频 Bug 之一。
  2. 🟢 **[无谓拷贝]**（L7–L8, L16–L20）：先把两个字符串 `toCharArray()` 全量拷一遍，每段又新建两个 `StringBuilder` 逐字符塞进去再 `toString()`。直接双指针记起点 `start` 后调一次 `substring(start, i)` 既零中间对象又最清晰。

- **Java 21 改进写法（分组循环模板）**：
  ```java
  public List<List<String>> findMismatchedSegments(String s1, String s2) {
      int n = s1.length();
      List<List<String>> res = new ArrayList<>();
      for (int i = 0; i < n; ) {
          if (s1.charAt(i) == s2.charAt(i)) {
              i++;
              continue;
          }
          int start = i;
          while (i < n && s1.charAt(i) != s2.charAt(i)) {
              i++;
          }
          res.add(List.of(s1.substring(start, i), s2.substring(start, i)));
      }
      return res;
  }
  ```

---

### 7. `CountValidSuffixSortsI.java` — 魔术常量可读性与提前短路

- **原文件**：[CountValidSuffixSortsI.java](./leetcode/CountValidSuffixSortsI.java)
- **亮点**：将条件拆成 `equalPrefix` 与 `nonIncreasingSuffix` 两个布尔数组，逻辑非常直观、不易写挂，面试中这种清晰的拆解很稳健。
- **问题清单**：
  1. 🟢 **[数字字面量可读性 & 空间分配]**（L6）：`new int[100000 + 1]` 手数 `0` 的个数极易在紧张时少写/多写一个 `0`，Java 支持数字下划线分隔符 **`100_001`**；更稳妥的做法是先扫一遍取输入最大值 `maxVal` 开 `new int[maxVal + 1]`，避免小用例每次也分配并遍历十万长度数组。
  2. 🟢 **[提前返回 & 单调性化简]**（L12–L19, L21–L38）：
     - 校验词频一致无需算 `Math.abs(d)` 累加，遇到 `if (d != 0) return 0;` 直接短路返回即可；
     - 进阶空间优化：`equalPrefix` 是前缀全真后全假，`nonIncreasingSuffix` 是前缀全假后全真，其实只需记录**首个不等位置 `firstDiff`** 和**最长非递增后缀起点 `suffixStart`**，合法区间长度就是 `Math.max(0, firstDiff - suffixStart + 1)`，$O(1)$ 额外空间即可拿下。

- **Java 21 改进写法**：
  ```java
  public int countSuffix(int[] nums, int[] target) {
      int n = nums.length;
      int maxVal = 0;
      for (int i = 0; i < n; i++) {
          maxVal = Math.max(maxVal, Math.max(nums[i], target[i]));
      }

      int[] diff = new int[maxVal + 1];
      for (int i = 0; i < n; i++) {
          diff[nums[i]]++;
          diff[target[i]]--;
      }
      for (int d : diff) {
          if (d != 0) return 0;
      }

      int firstDiff = 0;
      while (firstDiff < n && nums[firstDiff] == target[firstDiff]) {
          firstDiff++;
      }

      int suffixStart = n - 1;
      while (suffixStart > 0 && target[suffixStart - 1] >= target[suffixStart]) {
          suffixStart--;
      }

      // 合法的后缀起点 i 需满足：i >= suffixStart 且 i <= Math.min(n - 1, firstDiff)
      return Math.max(0, Math.min(n - 1, firstDiff) - suffixStart + 1);
  }
  ```

---

## 三、 下次刷题前 10 秒自检清单

1. **有移位吗？** 只要指数可能 `>= 30`，立刻把 `1 << j` 改成 **`1L << j`**，判位不用 `> 0` 改用 **`!= 0`**。
2. **有自定义元组 / 栈元素 / 树 DP 多返回值吗？** 直接在方法里写一行 **`record Foo(...) {}`**。
3. **有记忆化 DFS 吗？** `dp` 数组有没 `Arrays.fill(-1)`？如果只是从右往左递推，能不能顺手写成倒序 `for` + `switch ->`？
4. **是按段切分数组/字符串吗？** 直接套 `for (int i = 0; i < n; ) { int start = i; while (...) i++; }` 分组循环，拒绝循环外补刀。
