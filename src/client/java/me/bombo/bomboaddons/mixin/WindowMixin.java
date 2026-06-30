package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Shadow private boolean fullscreen;
    @Shadow private long handle;

    @Inject(method = "updateFullscreen", at = @At("HEAD"), cancellable = true)
    private void onUpdateFullscreen(boolean vsync, CallbackInfo ci) {
        if (me.bombo.bomboaddons.BomboConfig.get().borderlessFullscreen) {
            if (this.fullscreen) {
                // 1. Set window undecorated (borderless)
                GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                
                // 2. Find primary monitor
                long monitor = GLFW.glfwGetPrimaryMonitor();
                if (monitor != 0) {
                    GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
                    if (vidMode != null) {
                        int[] xpos = new int[1];
                        int[] ypos = new int[1];
                        GLFW.glfwGetMonitorPos(monitor, xpos, ypos);
                        
                        // 3. Move and resize window to fit the monitor borderlessly
                        GLFW.glfwSetWindowMonitor(
                            this.handle,
                            0, // 0 = windowed mode
                            xpos[0],
                            ypos[0],
                            vidMode.width(),
                            vidMode.height(),
                            vidMode.refreshRate()
                        );
                    }
                }
                
                ci.cancel();
            } else {
                // Restore decoration for windowed mode
                GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
            }
        } else {
            // Ensure window decoration is restored when borderless is disabled
            GLFW.glfwSetWindowAttrib(this.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        }
    }
}
