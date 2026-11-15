// Problem: https://leetcode.com/problems/count-valid-substrings-ii

class Solution {

    long dp[][];

    // seen = 0: b & c
    // seen = 1: axxxx
    // seen = 2: axxbxxx
    // seen = 3: axxbxxcxx
    long dfs(char[] s, int n, int i, int seen) {
        if (i == n) {
            return 0;
        }

        if (dp[i][seen] > 0) {
            return dp[i][seen];
        }
        long ret = 0;

        if (s[i] == 'a') {
            int nextSeen = seen > 1 ? seen : 1;
            ret = 1 + dfs(s, n, i + 1, nextSeen);
        } else if (s[i] == 'b') {
            int nextSeen = 0;
            if (seen == 1 || seen == 2) {
                nextSeen = 2;
            }
            ret = 1 + dfs(s, n, i + 1, nextSeen);
        } else if (s[i] == 'c') {
            int nextSeen = 0;
            if (seen == 2) {
                ret = 0;
            } else {
                nextSeen = seen;
                ret = 1 + dfs(s, n, i + 1, nextSeen);
            }
        }

        dp[i][seen] = ret;
        return ret;
    }

    public long countValidSubstrings(String str) {
        char[] s = str.toCharArray();
        int n = s.length;

        dp = new long[n][3];

        long ans = 0;
        for (int i = n - 1; i >= 0; i--) {
            ans += dfs(s, n, i, s[i] == 'a' ? 1 : 0);
        }
        return ans;
    }
}
