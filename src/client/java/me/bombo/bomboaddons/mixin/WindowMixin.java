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

import com.mojang.blaze3d.platform.WindowEventHandler;

@Mixin({Window.class})
public abstract class WindowMixin {
   @Shadow
   private boolean fullscreen;
   @Shadow
   private boolean actuallyFullscreen;
   @Shadow
   private long handle;
   @Shadow
   private int windowedX;
   @Shadow
   private int windowedY;
   @Shadow
   private int windowedWidth;
   @Shadow
   private int windowedHeight;
   @Shadow
   private int x;
   @Shadow
   private int y;
   @Shadow
   private int width;
   @Shadow
   private int height;
   @Shadow
   private WindowEventHandler eventHandler;

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
         this.actuallyFullscreen = this.fullscreen;
         if (this.fullscreen) {
            this.windowedX = this.x;
            this.windowedY = this.y;
            this.windowedWidth = this.width > 0 ? this.width : 854;
            this.windowedHeight = this.height > 0 ? this.height : 480;

            GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor != 0L) {
               GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
               if (vidMode != null) {
                  int[] xpos = new int[1];
                  int[] ypos = new int[1];
                  GLFW.glfwGetMonitorPos(monitor, xpos, ypos);
                  this.x = xpos[0];
                  this.y = ypos[0];
                  this.width = vidMode.width();
                  this.height = vidMode.height();
                  GLFW.glfwSetWindowMonitor(this.handle, 0L, this.x, this.y, this.width, this.height, vidMode.refreshRate());
               }
            }
         } else {
            GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
            this.x = this.windowedX;
            this.y = this.windowedY;
            this.width = this.windowedWidth > 0 ? this.windowedWidth : 854;
            this.height = this.windowedHeight > 0 ? this.windowedHeight : 480;
            GLFW.glfwSetWindowMonitor(this.handle, 0L, this.x, this.y, this.width, this.height, -1);
         }
         this.eventHandler.framebufferSizeChanged();
         ci.cancel();
      }
   }
}
