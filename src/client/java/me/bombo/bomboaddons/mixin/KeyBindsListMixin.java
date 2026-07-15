package me.bombo.bomboaddons.mixin;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.bombo.bomboaddons.IBomboKeyBindsList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin extends ContainerObjectSelectionList<KeyBindsList.Entry> implements IBomboKeyBindsList {

    public KeyBindsListMixin(net.minecraft.client.Minecraft minecraft, int i, int j, int k, int l, int m) {
        super(minecraft, i, j, k, l);
    }

    @Unique
    private List<KeyBindsList.Entry> bombo$allEntries = null;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        bombo$allEntries = new ArrayList<>(this.children());
    }

    @Override
    @Unique
    public void bombo$filter(String query) {
        if (bombo$allEntries == null) return;
        this.clearEntries();
        query = query.toLowerCase(Locale.ROOT);
        
        if (query.isEmpty()) {
            for (KeyBindsList.Entry e : bombo$allEntries) {
                this.addEntry(e);
            }
            return;
        }

        KeyBindsList.Entry currentCategory = null;
        boolean categoryMatched = false;

        for (KeyBindsList.Entry e : bombo$allEntries) {
            String className = e.getClass().getSimpleName();
            if (className.equals("CategoryEntry")) {
                currentCategory = e;
                categoryMatched = false;
                try {
                    Field nameField = null;
                    for (Field f : e.getClass().getDeclaredFields()) {
                        if (Component.class.isAssignableFrom(f.getType())) {
                            nameField = f;
                            break;
                        }
                    }
                    if (nameField != null) {
                        nameField.setAccessible(true);
                        Component nameComp = (Component) nameField.get(e);
                        if (nameComp.getString().toLowerCase(Locale.ROOT).contains(query)) {
                            categoryMatched = true;
                            this.addEntry(e);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else if (className.equals("KeyEntry")) {
                try {
                    Field keyField = null;
                    for (Field f : e.getClass().getDeclaredFields()) {
                        if (KeyMapping.class.isAssignableFrom(f.getType())) {
                            keyField = f;
                            break;
                        }
                    }
                    if (keyField != null) {
                        keyField.setAccessible(true);
                        KeyMapping mapping = (KeyMapping) keyField.get(e);
                        
                        String keyName = Component.translatable(mapping.getName()).getString().toLowerCase(Locale.ROOT);
                        String boundKey = mapping.getTranslatedKeyMessage().getString().toLowerCase(Locale.ROOT);
                        String catName = mapping.getCategory() == null ? "" : mapping.getCategory().toString().toLowerCase(Locale.ROOT);

                        boolean exactMatch = false;
                        String cleanQuery = query;
                        if (cleanQuery.startsWith("\"") && cleanQuery.endsWith("\"") && cleanQuery.length() >= 2) {
                            exactMatch = true;
                            cleanQuery = cleanQuery.substring(1, cleanQuery.length() - 1);
                        } else if (cleanQuery.startsWith("\"")) {
                            exactMatch = true;
                            cleanQuery = cleanQuery.substring(1);
                        }

                        boolean boundMatch;
                        boolean nameMatch;
                        boolean catMatch;

                        if (exactMatch || cleanQuery.length() == 1) {
                            // Single letters also default to exact boundKey match to reduce noise
                            boundMatch = boundKey.equals(cleanQuery);
                            nameMatch = exactMatch ? keyName.equals(cleanQuery) : keyName.contains(cleanQuery);
                            catMatch = exactMatch ? catName.equals(cleanQuery) : false;
                        } else {
                            boundMatch = boundKey.contains(cleanQuery);
                            nameMatch = keyName.contains(cleanQuery);
                            catMatch = catName.contains(cleanQuery);
                        }

                        if (categoryMatched || boundMatch || nameMatch || catMatch) {
                            if (!categoryMatched && currentCategory != null) {
                                this.addEntry(currentCategory);
                                currentCategory = null;
                            }
                            this.addEntry(e);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        this.setScrollAmount(0);
    }
}
