package me.bombo;

import net.minecraft.client.User;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class TestUser {
    public static void main(String[] args) {
        System.out.println("Constructors:");
        for (Constructor<?> c : User.class.getDeclaredConstructors()) {
            System.out.println(c);
        }
        System.out.println("Fields in Minecraft:");
        for (Field f : Minecraft.class.getDeclaredFields()) {
            if (f.getType() == User.class) {
                System.out.println(f);
            }
        }
    }
}
