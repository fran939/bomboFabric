import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.minecraft.client.multiplayer.PlayerInfo;

public class TestReflection {
    public static void main(String[] args) {
        System.out.println("PlayerInfo methods:");
        for (Method m : PlayerInfo.class.getDeclaredMethods()) {
            System.out.println(m.getName() + " -> " + m.getReturnType().getName());
        }
    }
}
