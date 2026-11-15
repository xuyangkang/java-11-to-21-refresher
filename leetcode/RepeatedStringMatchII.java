// Problem: https://leetcode.com/problems/repeated-string-match-ii

class Solution {
    int buildRoute(char[] a, char[] b, int i) {
        int n = a.length;
        int m = b.length;

        boolean equal = true;
        for (int j = 0; j < m; j++) {
            if (b[j] != a[i]) {
                equal = false;
                break;
            }
            i++;
            if (i >= n) {
                i -= n;
            }
        }
        if (!equal) {
            return -1;
        }
        return i;
    }

    public boolean repeatedStringMatch(String strA, String strB, int x, int y) {
        char[] a = strA.toCharArray();
        char[] b = strB.toCharArray();
        int n = a.length;
        int m = b.length;

        int[][] tab = new int[40][n];

        for (int i = 0; i < n; i++) {
            tab[0][i] = buildRoute(a, b, i);
        }

        for (int j = 1; j < 40; j++) {
            for (int i = 0; i < n; i++) {
                int start = i;
                int mid = tab[j - 1][i];
                int end = mid == -1 ? -1 : tab[j - 1][mid];
                tab[j][i] = end;
            }
        }

        for (int i = 0; i < n; i++) {
            long bigA = n; bigA *= x;
            long bigB = m; bigB *= y;
            if (i + bigB > bigA) {
                break;
            }

            int ptr = i;
            for (int j = 0; (1 << j) <= y; j++) {
                if (((1 << j) & y) > 0) {
                    ptr = tab[j][ptr];
                    if (ptr == -1) {
                        break;
                    }
                }
            }
            if (ptr == -1) {
                continue;
            }
            return true;
        }
        return false;
    }
}
