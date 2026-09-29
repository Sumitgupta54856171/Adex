import rikka.shizuku.Shizuku;
import java.lang.reflect.Method;
public class test3 {
    public static void main(String[] args) {
        for(Method m : Shizuku.class.getMethods()) {
            if(m.getName().toLowerCase().contains("process") || m.getName().toLowerCase().contains("exec") || m.getName().toLowerCase().contains("shell")) {
                System.out.println(m);
            }
        }
    }
}
