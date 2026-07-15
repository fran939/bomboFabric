package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CustomSoundsScreen extends Screen {
    private final Screen parent;
    private EditBox searchBox;
    private double scrollAmount = 0;
    private List<String> filteredSounds = new ArrayList<>();
    private final List<String> allSounds = new ArrayList<>();
    private final List<VolumeSlider> sliders = new ArrayList<>();
    private static final int ITEM_HEIGHT = 24;
    private int listStartY = 40;

    public CustomSoundsScreen(Screen parent) {
        super(Component.literal("BomboAddons Sounds"));
        this.parent = parent;
        for (Identifier rl : BuiltInRegistries.SOUND_EVENT.keySet()) {
            allSounds.add(rl.toString());
        }
        allSounds.sort(String::compareToIgnoreCase);
        filteredSounds.addAll(allSounds);
    }

    @Override
    protected void init() {
        searchBox = new EditBox(font, width / 2 - 100, 10, 200, 20, Component.literal("Search..."));
        searchBox.setResponder(query -> {
            filteredSounds = allSounds.stream()
                .filter(s -> s.toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
            updateSliders();
        });
        addRenderableWidget(searchBox);
        updateSliders();
    }

    private void updateSliders() {
        for (VolumeSlider slider : sliders) {
            removeWidget(slider);
        }
        sliders.clear();
        BomboConfig.Settings s = BomboConfig.get();
        int columns = 3;
        int colWidth = width / columns;
        int idx = 0;
        for (String sound : filteredSounds) {
            float initialVol = s.customSoundVolumes.getOrDefault(sound, 1.0f);
            int col = idx % columns;
            int row = idx / columns;
            VolumeSlider slider = new VolumeSlider(
                    col * colWidth + 10, 
                    listStartY + (row * 36) + 12, 
                    colWidth - 20, 20, 
                    Component.literal(""), 
                    initialVol / 2.0, 
                    sound
            );
            sliders.add(slider);
            addRenderableWidget(slider);
            idx++;
        }
        updateScroll(0);
    }

    private void updateScroll(double delta) {
        int columns = 3;
        int rows = (int) Math.ceil((double) filteredSounds.size() / columns);
        double maxScroll = Math.max(0, rows * 36 - (height - listStartY - 20));
        scrollAmount -= delta;
        scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));
        
        int idx = 0;
        for (VolumeSlider slider : sliders) {
            int row = idx / columns;
            slider.setY(listStartY + (row * 36) + 12 - (int) scrollAmount);
            slider.visible = slider.getY() >= listStartY && slider.getY() <= height - 20;
            idx++;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        updateScroll(scrollY * 10);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        
        graphics.centeredText(font, "BomboAddons Sounds", width / 2, 30, 0xFFFFFFFF);
        
        int columns = 3;
        int colWidth = width / columns;
        int idx = 0;
        for (String sound : filteredSounds) {
            int col = idx % columns;
            int row = idx / columns;
            int y = listStartY + (row * 36) - (int) scrollAmount;
            if (y >= listStartY && y <= height - 20) {
                // Shorten string if it's too long
                String displayStr = sound;
                if (font.width(displayStr) > colWidth - 20) {
                    displayStr = font.plainSubstrByWidth(displayStr, colWidth - 30) + "...";
                }
                graphics.text(font, displayStr, col * colWidth + 10, y, 0xFFFFFFFF, true);
            }
            idx++;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    class VolumeSlider extends AbstractSliderButton {
        private final String soundId;

        public VolumeSlider(int x, int y, int width, int height, Component title, double value, String soundId) {
            super(x, y, width, height, title, value);
            this.soundId = soundId;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format("Vol: %d%%", (int)(this.value * 200))));
        }

        @Override
        protected void applyValue() {
            BomboConfig.Settings s = BomboConfig.get();
            float newVol = (float) (this.value * 2.0);
            if (Math.abs(newVol - 1.0f) < 0.01f) {
                s.customSoundVolumes.remove(soundId);
            } else {
                s.customSoundVolumes.put(soundId, newVol);
            }
            BomboConfig.save();
        }
    }
}
