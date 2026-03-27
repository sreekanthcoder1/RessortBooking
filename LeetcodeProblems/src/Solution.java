public class Solution {
    public boolean validPalindrome(String s) {
        int left = 0;
        int right = s.length() - 1;

        while (left < right) {
            if (s.charAt(left) == s.charAt(right)) {
                left++;
                right--;
            } else {
                isValidPalindrome(s, left + 1, right);
                isValidPalindrome(s, left, right - 1);
            }
        }
        return true;
    }

    private boolean isValidPalindrome(String s, int left, int right) {
        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }
        public static void main(String[]args){
            String s = "aba";
            Solution solution = new Solution();
            boolean ans = solution.validPalindrome(s);
            System.out.println(ans);
        }
    }
