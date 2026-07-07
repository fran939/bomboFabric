import net.minecraft.client.gui.screens.Screen;
public class Test {
    public static void main(String[] args) {
        for (java.lang.reflect.Method m : Screen.class.getMethods()) {
            if (m.getName().toLowerCase().contains("alt") || m.getName().toLowerCase().contains("control") || m.getName().toLowerCase().contains("shift")) {
                System.out.println("Screen: " + m.getName());
            }
        }
        for (java.lang.reflect.Method m : com.mojang.blaze3d.platform.Window.class.getMethods()) {
            if (m.getName().toLowerCase().contains("window") || m.getName().toLowerCase().contains("handle")) {
                System.out.println("Window: " + m.getName() + " -> " + m.getReturnType().getName());
            }
        }
    }
}
