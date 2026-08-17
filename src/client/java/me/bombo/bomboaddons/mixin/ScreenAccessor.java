package me.bombo.bomboaddons.mixin;

import java.util.List;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Screen.class})
public interface ScreenAccessor {
   @Accessor("renderables")
   List<Renderable> getRenderables();

   @Invoker("addRenderableWidget")
   <T extends GuiEventListener & Renderable & NarratableEntry> T invokeAddRenderableWidget(T var1);
}
