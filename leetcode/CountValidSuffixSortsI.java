// Problem: https://leetcode.com/problems/count-valid-suffix-sorts-i

class Solution {
    public int countSuffix(int[] nums, int[] target) {
        int n = nums.length;
        int[] elementDiff = new int[100000 + 1];
        for (int i = 0; i < n; i++) {
            elementDiff[nums[i]]++;
            elementDiff[target[i]]--;
        }

        int totalDiff = 0;
        for (int d : elementDiff) {
            totalDiff += Math.abs(d);
        }

        if (totalDiff != 0) {
            return 0;
        }

        boolean[] equalPrefix = new boolean[n];
        equalPrefix[0] = nums[0] == target[0];
        for (int i = 1; i < n; i++) {
            equalPrefix[i] = equalPrefix[i - 1] && nums[i] == target[i];
        }

        boolean[] nonIncreasingSuffix = new boolean[n];
        nonIncreasingSuffix[n - 1] = true;
        for (int i = n - 2; i >= 0; i--) {
            nonIncreasingSuffix[i] = nonIncreasingSuffix[i + 1] && target[i] >= target[i + 1];
        }

        int ret = 0;
        for (int i = 0; i < n; i++) {
            if (nonIncreasingSuffix[i] && (i == 0 || equalPrefix[i - 1])) {
                ret++;
            }
        }
        return ret;
    }
}
