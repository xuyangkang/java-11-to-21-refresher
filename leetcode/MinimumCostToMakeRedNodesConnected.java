// Problem: https://leetcode.com/problems/minimum-cost-to-make-red-nodes-connected

import java.util.*;

class Solution {

    List<List<Integer>> adj;
    char[] color;
    // case 1: color everything to blue
    long[] case1;
    // case 2: build an SCC with subtree node i is red
    long[] case2;
    // case 3: has a red scc somewhere, while subtree node i is blue
    long[] case3;

    int n;
    int[] treeSize;

    void dfs(int now, int parent) {
        treeSize[now] = 1;
        List<Integer> children = new ArrayList<>();
        for (int i : adj.get(now)) {
            if (i != parent) {
                children.add(i);
            }
        }

        if (children.size() == 0) {
            if (color[now] == 'R') {
                case1[now] = 1;
                case2[now] = 0;
                case3[now] = 1;
            } else {
                case1[now] = 0;
                case2[now] = 1;
                case3[now] = 0;
            }
        } else {
            for (int child : children) {
                dfs(child, now);
                treeSize[now] += treeSize[child];
            }

            // case 1: color everything to blue
            case1[now] = color[now] == 'R' ? treeSize[now] : 0;
            for (int child : children) {
                case1[now] += case1[child];
            }
            // case 2: build an SCC with subtree node i is red
            case2[now] = color[now] == 'R' ? 0 : treeSize[now];
            for (int child : children) {
                case2[now] += Math.min(case1[child], case2[child]);
            }

            // case 3: has a red scc somewhere, while subtree node i is blue
            case3[now] = case1[now];

            long helper = 0;
            for (int child : children) {
                helper += case1[child];
            }
            for (int child : children) {
                long val = helper - case1[child] + case3[child] + (color[now] == 'R' ? treeSize[now] : 0);
                case3[now] = Math.min(case3[now], val);
            }
            for (int child : children) {
                long val = helper - case1[child] + case2[child] + (color[now] == 'R' ? treeSize[now] : 0);
                case3[now] = Math.min(case3[now], val);
            }
        }
    }

    public long minCost(int _n, String _colors, int[][] edges) {
        n = _n;
        case1 = new long[n];
        case2 = new long[n];
        case3 = new long[n];
        treeSize = new int[n];
        color = _colors.toCharArray();
        adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 0; i < n - 1; i++) {
            int f = edges[i][0];
            int t = edges[i][1];
            adj.get(f).add(t);
            adj.get(t).add(f);
        }
        dfs(0, -1);
        return Math.min(case2[0], case3[0]);
    }
}
