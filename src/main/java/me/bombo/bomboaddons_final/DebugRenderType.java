package me.bombo.bomboaddons_final;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

public class DebugRenderType {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.minecraft.client.renderer.rendertype.RenderSetup$RenderSetupBuilder");
        System.out.println("=== BUILDER CONSTRUCTORS ===");
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            System.out.println(c.toString());
        }
        System.out.println("=== BUILDER METHODS ===");
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println(m.toString());
        }
        System.out.println("=== BUILDER FIELDS ===");
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println(f.toString());
        }
    }
}
