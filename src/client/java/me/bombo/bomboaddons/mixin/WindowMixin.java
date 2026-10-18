package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.util.WindowsDesktopHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Window.class})
public abstract class WindowMixin {
   @Shadow
   private boolean fullscreen;
   @Shadow
   private boolean actuallyFullscreen;
   @Shadow
   private long handle;

   @Inject(
      method = "createGlfwWindow",
      at = @At("HEAD")
   )
   private static void onBeforeCreateGlfwWindow(int width, int height, String title, long monitor, GpuBackend backend, CallbackInfoReturnable<Long> cir) {
      WindowsDesktopHelper.ensureDefaultDesktop();
   }

   @Inject(
      method = "createGlfwWindow",
      at = @At("RETURN")
   )
   private static void onAfterCreateGlfwWindow(int width, int height, String title, long monitor, GpuBackend backend, CallbackInfoReturnable<Long> cir) {
      long handle = cir.getReturnValue();
      if (handle != 0L) {
         WindowsDesktopHelper.registerWindow(handle);
      }
   }

   @Inject(
      method = {"updateFullscreenIfChanged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onUpdateFullscreen(CallbackInfo ci) {
      if (this.fullscreen == this.actuallyFullscreen) {
         return;
      }
      if (BomboConfig.get().borderlessFullscreen) {
         if (this.fullscreen) {
            this.actuallyFullscreen = true;
            GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
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
            GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
         }
      }
   }
}
