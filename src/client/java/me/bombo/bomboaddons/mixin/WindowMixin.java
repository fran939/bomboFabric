package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.platform.Window;
import me.bombo.bomboaddons.BomboConfig;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Window.class})
public abstract class WindowMixin {
   @Shadow
   private boolean fullscreen;
   @Shadow
   private long handle;

   @Inject(
      method = {"updateFullscreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onUpdateFullscreen(boolean vsync, CallbackInfo ci) {
      if (BomboConfig.get().borderlessFullscreen) {
         if (this.fullscreen) {
            GLFW.glfwSetWindowAttrib(this.handle, 131077, 0);
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor != 0L) {
               GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
               if (vidMode != null) {
                  int[] xpos = new int[1];
                  int[] ypos = new int[1];
                  GLFW.glfwGetMonitorPos(monitor, xpos, ypos);
                  GLFW.glfwSetWindowMonitor(this.handle, 0L, xpos[0], ypos[0], vidMode.width(), vidMode.height(), vidMode.refreshRate());
               }
            }

            ci.cancel();
         } else {
            GLFW.glfwSetWindowAttrib(this.handle, 131077, 1);
         }
      } else {
         GLFW.glfwSetWindowAttrib(this.handle, 131077, 1);
      }

   }
}
