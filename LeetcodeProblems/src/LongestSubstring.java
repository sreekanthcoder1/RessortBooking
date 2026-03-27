import java.util.*;

public class LongestSubstring {
        public static int lengthOfLongestSubstring(String s) {
            int left = 0;
            int maxLength = 0;

            Set<Character> set = new HashSet<>();

            for (int right = 0; right < s.length(); right++) {

                // If duplicate found, shrink window
                while (set.contains(s.charAt(right))) {
                    set.remove(s.charAt(left));
                    left++;
                }

                // Add current character
                set.add(s.charAt(right));

                // Update max length
                maxLength = Math.max(maxLength, right - left + 1);
            }

            return maxLength;
        }

        public static void main(String[] args) {
            String s = "abcabcbb";
            System.out.println(lengthOfLongestSubstring(s)); // Output: 3
        }
    }

