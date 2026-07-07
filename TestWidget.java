
import net.minecraft.client.gui.components.AbstractWidget;
import java.lang.reflect.Method;
public class TestWidget {
    public static void main(String[] args) {
        for (Method m : AbstractWidget.class.getDeclaredMethods()) {
            System.out.println(m);
        }
    }
}
