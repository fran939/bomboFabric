import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;

public class TestReflection {
    public static void main(String[] args) {
        System.out.println("KeyBindsScreen fields:");
        for (Field f : KeyBindsScreen.class.getDeclaredFields()) {
            System.out.println(f.getName() + " " + f.getType().getName());
        }
        System.out.println("KeyBindsList methods:");
        for (Method m : KeyBindsList.class.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
        System.out.println("KeyBindsList fields:");
        for (Field f : KeyBindsList.class.getDeclaredFields()) {
            System.out.println(f.getName() + " " + f.getType().getName());
        }
    }
}
