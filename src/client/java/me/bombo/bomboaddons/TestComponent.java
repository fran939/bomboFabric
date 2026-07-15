package me.bombo.bomboaddons;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class TestComponent {
    public static Component test(Component comp) {
        MutableComponent m = comp.copy();
        m.getSiblings().clear();
        for (Component child : comp.getSiblings()) {
            m.append(test(child));
        }
        return m;
    }
}
