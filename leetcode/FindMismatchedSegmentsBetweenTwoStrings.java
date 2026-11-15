// Problem: https://leetcode.com/problems/find-mismatched-segments-between-two-strings

import java.util.*;

class Solution {
    public List<List<String>> findMismatchedSegments(String _s1, String _s2) {
        char[] s1 = _s1.toCharArray();
        char[] s2 = _s2.toCharArray();
        final int n = s1.length;
        List<List<String>> ret = new ArrayList<>();
        StringBuilder sb1 = null;
        StringBuilder sb2 = null;
        for (int i = 0; i < n; i++) {
            if (s1[i] != s2[i]) {
                if (sb1 == null) {
                    sb1 = new StringBuilder();
                    sb2 = new StringBuilder();
                }
                sb1.append(s1[i]);
                sb2.append(s2[i]);
            } else {
                if (sb1 != null) {
                    ret.add(List.of(sb1.toString(), sb2.toString()));
                    sb1 = null;
                    sb2 = null;
                }
            }
        }
        if (sb1 != null) {
            ret.add(List.of(sb1.toString(), sb2.toString()));
        }
        return ret;
    }
}
