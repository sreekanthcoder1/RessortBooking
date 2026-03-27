public class ValindPlaindrome2 {
    public static void main(String[] args) {
        String s = "aba";
        ValindPlaindrome2 valindPlaindrome2 = new ValindPlaindrome2();
        boolean ans = valindPlaindrome2.validPalindrome(s);
        System.out.println(ans);


    }

    public boolean validPalindrome(String s) {
        int left =0;
        int right = s.length()-1;
        while (left<right){
            if(s.charAt(left) == s.charAt(right)){
                left++;
                right--;
            }
            else {
                isPalindrome(s,left+1,right);
                isPalindrome(s,left,right-1);
            }
        }
        return  true;

    }

    private boolean isPalindrome(String s, int left, int right) {
        while (left<right){
            if(s.charAt(left) != s.charAt(right)){
                return false;
            }
            left++;
            right--;
        }
        return  true;
    }
}