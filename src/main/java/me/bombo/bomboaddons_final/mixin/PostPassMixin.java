package me.bombo.bomboaddons_final.mixin;

import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(PostPass.class)
public abstract class PostPassMixin {

    // We search across all methods since the remapper can't find 'process' or 'render' reliably.
    // However, the shader is processed during rendering. 
    // We'll hook into a method that takes a float (partialTicks) as per standard PostPass logic.
    @Inject(method = {"process", "render"}, at = @At("HEAD"), remap = false, require = 0)
    private void onProcess(float partialTicks, CallbackInfo ci) {
        applyThickerGlow();
    }

    private void applyThickerGlow() {
        try {
            // Find the shader field by looking for a field whose type name contains "ShaderInstance" or "EffectInstance".
            // This is more robust against mapping changes.
            Field shaderField = null;
            for (Field f : PostPass.class.getDeclaredFields()) {
                String typeName = f.getType().getSimpleName();
                if (typeName.contains("ShaderInstance") || typeName.contains("EffectInstance") || typeName.contains("EffectProgram")) {
                    shaderField = f;
                    break;
                }
            }

            if (shaderField == null) return;
            shaderField.setAccessible(true);
            Object shader = shaderField.get(this);
            if (shader == null) return;

            // Find getUniform method
            Method getUniform = null;
            for (Method m : shader.getClass().getDeclaredMethods()) {
                if (m.getName().equals("getUniform") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                    getUniform = m;
                    break;
                }
            }

            if (getUniform == null) return;
            getUniform.setAccessible(true);
            Object uniform = getUniform.invoke(shader, "Radius");

            if (uniform != null) {
                // Find set method
                for (Method m : uniform.getClass().getDeclaredMethods()) {
                    if (m.getName().equals("set") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == float.class) {
                        m.setAccessible(true);
                        // Significant increase for a "thick" glow look
                        m.invoke(uniform, 4.0f);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Silently fail to ensure game stability
        }
    }
}
