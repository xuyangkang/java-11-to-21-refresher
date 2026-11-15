// Problem: https://leetcode.com/problems/remove-elements-by-type-and-color

import java.util.*;

class Solution {

    class StackElement {
        int type;
        int color;
        int size;
    }

    public boolean canRemoveAll(int[] types, int[] colors, int[] groupSize) {
        Deque<StackElement> s = new ArrayDeque<>();
        int n = types.length;
        for (int i = 0; i < n; i++) {
            if (s.isEmpty() || s.peek().type != types[i] || s.peek().color != colors[i]) {
                StackElement e = new StackElement();
                e.type = types[i];
                e.color = colors[i];
                e.size = 1;
                s.push(e);
            } else {
                StackElement e = s.pop();
                e.size++;
                s.push(e);
            }

            StackElement e = s.pop();
            if (e.size < groupSize[e.type]) {
                s.push(e);
            }
        }
        return s.isEmpty();
    }
}
