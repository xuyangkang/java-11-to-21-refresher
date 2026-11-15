// Problem: https://leetcode.com/problems/count-valid-substrings-i

import java.util.*;

class Solution {
    public long countValidSubstrings(String s) {
        List<Integer> abcIndex = new ArrayList<>();
        for (int i = 0; i + 2 < s.length(); i++) {
            if (s.charAt(i) == 'a' && s.charAt(i+1) == 'b' && s.charAt(i + 2) == 'c') {
                abcIndex.add(i);
            }
        }
        int ptr = 0;
        long ans = 0;
        for (int i = 0; i < s.length(); i++) {
            // case #1: abcxxxxx will be all invalid
            if (ptr < abcIndex.size() && abcIndex.get(ptr) == i) {
                ans += 2;
                ptr++;
                continue;
            }
            // case #2: xxxxxabc should stop at xxxxxab
            int next = s.length();
            if (ptr < abcIndex.size()) {
                next = abcIndex.get(ptr) + 2;
            }
            ans += next - i;
        }
        return ans;
    }
}
