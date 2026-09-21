package me.bombo.bomboaddons.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ClientItemInfoLoader.class)
public class ClientItemInfoLoaderMixin {

    @ModifyReturnValue(
        method = "scheduleLoad",
        at = @At("RETURN")
    )
    private static CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> bombo$injectCustomSkyblockModels(
        CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> original,
        @Local(argsOnly = true) ResourceManager resourceManager,
        @Local(argsOnly = true) Executor executor
    ) {
        return original.thenCompose(oldModels -> CompletableFuture.supplyAsync(() -> supplyExtraModels(resourceManager, oldModels), executor));
    }

    @Unique
    private static ClientItemInfoLoader.LoadedClientInfos supplyExtraModels(ResourceManager resourceManager, ClientItemInfoLoader.LoadedClientInfos oldModels) {
        try {
            me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.clear();

            Map<Identifier, ClientItem> newModels = new HashMap<>(oldModels.contents());

            // Index existing pack models from oldModels into SIMPLE_MODELS
            for (Identifier id : oldModels.contents().keySet()) {
                if (!id.getNamespace().equals("minecraft") && !id.getNamespace().equals("realms")) {
                    String path = id.getPath();
                    String simpleName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                    if (!simpleName.equalsIgnoreCase("pet")) {
                        me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.registerSimpleModel(simpleName, id);
                        me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.registerSimpleModel(path, id);
                    }
                }
            }

            Map<Identifier, Resource> resources = resourceManager.listResources(
                "models/item",
                id -> !id.getNamespace().equals("minecraft") && !id.getNamespace().equals("realms")
                          && id.getPath().endsWith(".json")
            );

            for (Map.Entry<Identifier, Resource> model : resources.entrySet()) {
                Identifier key = model.getKey();
                // strip "models/item/" prefix and ".json" suffix to get item model id
                String path = key.getPath();
                if (path.startsWith("models/item/") && path.endsWith(".json")) {
                    String subPath = path.substring("models/item/".length(), path.length() - ".json".length());
                    Identifier itemModelId = Identifier.fromNamespaceAndPath(key.getNamespace(), subPath);
                    Identifier genericModelId = Identifier.fromNamespaceAndPath(key.getNamespace(), "item/" + subPath);

                    ItemModel.Unbaked unbakedModel = new CuboidItemModelWrapper.Unbaked(genericModelId, Optional.empty(), List.of());
                    ClientItem clientItem = new ClientItem(
                        unbakedModel,
                        new ClientItem.Properties(true, true, 1.0F)
                    );
                    newModels.putIfAbsent(itemModelId, clientItem);
                    newModels.putIfAbsent(genericModelId, clientItem);

                    String simpleName = subPath.contains("/") ? subPath.substring(subPath.lastIndexOf('/') + 1) : subPath;
                    if (!simpleName.equalsIgnoreCase("pet")) {
                        me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.registerSimpleModel(simpleName, itemModelId);
                        me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.registerSimpleModel(simpleName, genericModelId);
                        me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.registerSimpleModel(subPath, itemModelId);
                    }
                    newModels.putIfAbsent(Identifier.fromNamespaceAndPath(key.getNamespace(), simpleName), clientItem);
                }
            }

            me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.loadOverrides(resourceManager);

            return new ClientItemInfoLoader.LoadedClientInfos(newModels);
        } catch (Throwable t) {
            System.err.println("[BomboAddons] Failed to supply extra custom models: " + t.getMessage());
            return oldModels;
        }
    }
}
