package me.bombo.bomboaddons_final;

import java.lang.reflect.Method;
import net.minecraft.client.renderer.rendertype.RenderType;

public class DebugMethods {
    public static void listRenderTypeMethods() {
        System.out.println("DEBUG: Listing RenderType methods:");
        for (Method m : RenderType.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 0) {
                System.out.println("DEBUG: Method: " + m.getName());
            }
        }
    }
}
