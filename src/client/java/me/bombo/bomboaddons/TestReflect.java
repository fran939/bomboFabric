package me.bombo.bomboaddons;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

public class TestReflect {
   public static void main(String[] args) {
      for(Method m : Minecraft.class.getMethods()) {
         if (m.getReturnType().getName().contains("SessionService")) {
            System.out.println("FOUND: " + m.getName());
         }
      }

   }
}
