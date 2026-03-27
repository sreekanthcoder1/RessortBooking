public class ReverseInteger {
    public int reverse(int x) {
        int left =x;
        for (int i = 1; i < left; i++) {
            int temp = i;
            i = left;
            i++;
            left--;
        }
        return x;
    }
    public static void main(String[]args){
        int x =123;
        ReverseInteger solution = new ReverseInteger();
        int ans = solution.reverse(x);
        System.out.println(ans);
    }
}
