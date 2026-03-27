/*import java.util.HashSet;
import java.util.Set;

public class LonsgestSubstring {
    public static void main(String[] args) {
        String s= "abcabcbb";
        LonsgestSubstring lonsgestSubstring = new LonsgestSubstring();
       int ans =  lonsgestSubstring.lengthOfLongestSubstring(s);
        System.out.println(ans);


    }
        public int lengthOfLongestSubstring(String s) {
            Set<String>values = new HashSet<>();
            int left =0;
            int maxLength = 0;
            for(int right =0;right<s.length();right++){
                while (values.contains(right)){
                    values.remove(left);
                }
            }
        }
        }*/
