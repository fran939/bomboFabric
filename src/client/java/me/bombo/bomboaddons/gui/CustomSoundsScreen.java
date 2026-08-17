package me.bombo.bomboaddons.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class CustomSoundsScreen extends Screen {
   private final Screen parent;
   private EditBox searchBox;
   private double scrollAmount = (double)0.0F;
   private List<String> filteredSounds = new ArrayList();
   private final List<String> allSounds = new ArrayList();
   private final List<VolumeSlider> sliders = new ArrayList();
   private static final int ITEM_HEIGHT = 24;
   private int listStartY = 40;

   public CustomSoundsScreen(Screen parent) {
      super(Component.literal("BomboAddons Sounds"));
      this.parent = parent;

      for(Identifier rl : BuiltInRegistries.SOUND_EVENT.keySet()) {
         this.allSounds.add(rl.toString());
      }

      this.allSounds.sort(String::compareToIgnoreCase);
      this.filteredSounds.addAll(this.allSounds);
   }

   protected void init() {
      this.searchBox = new EditBox(this.font, this.width / 2 - 100, 10, 200, 20, Component.literal("Search..."));
      this.searchBox.setResponder((query) -> {
         this.filteredSounds = (List)this.allSounds.stream().filter((s) -> s.toLowerCase().contains(query.toLowerCase())).collect(Collectors.toList());
         this.updateSliders();
      });
      this.addRenderableWidget(this.searchBox);
      this.updateSliders();
   }

   private void updateSliders() {
      for(VolumeSlider slider : this.sliders) {
         this.removeWidget(slider);
      }

      this.sliders.clear();
      BomboConfig.Settings s = BomboConfig.get();
      int columns = 3;
      int colWidth = this.width / columns;
      int idx = 0;

      for(String sound : this.filteredSounds) {
         float initialVol = (Float)s.customSoundVolumes.getOrDefault(sound, 1.0F);
         int col = idx % columns;
         int row = idx / columns;
         VolumeSlider slider = new VolumeSlider(col * colWidth + 10, this.listStartY + row * 36 + 12, colWidth - 20, 20, Component.literal(""), (double)initialVol / (double)2.0F, sound);
         this.sliders.add(slider);
         this.addRenderableWidget(slider);
         ++idx;
      }

      this.updateScroll((double)0.0F);
   }

   private void updateScroll(double delta) {
      int columns = 3;
      int rows = (int)Math.ceil((double)this.filteredSounds.size() / (double)columns);
      double maxScroll = (double)Math.max(0, rows * 36 - (this.height - this.listStartY - 20));
      this.scrollAmount -= delta;
      this.scrollAmount = Math.max((double)0.0F, Math.min(this.scrollAmount, maxScroll));
      int idx = 0;

      for(VolumeSlider slider : this.sliders) {
         int row = idx / columns;
         slider.setY(this.listStartY + row * 36 + 12 - (int)this.scrollAmount);
         slider.visible = slider.getY() >= this.listStartY && slider.getY() <= this.height - 20;
         ++idx;
      }

   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      this.updateScroll(scrollY * (double)10.0F);
      return true;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
      graphics.centeredText(this.font, "BomboAddons Sounds", this.width / 2, 30, -1);
      int columns = 3;
      int colWidth = this.width / columns;
      int idx = 0;

      for(String sound : this.filteredSounds) {
         int col = idx % columns;
         int row = idx / columns;
         int y = this.listStartY + row * 36 - (int)this.scrollAmount;
         if (y >= this.listStartY && y <= this.height - 20) {
            String displayStr = sound;
            if (this.font.width(sound) > colWidth - 20) {
               String var10000 = this.font.plainSubstrByWidth(sound, colWidth - 30);
               displayStr = var10000 + "...";
            }

            graphics.text(this.font, displayStr, col * colWidth + 10, y, -1, true);
         }

         ++idx;
      }

   }

   public void onClose() {
      Minecraft.getInstance().setScreen(this.parent);
   }

   class VolumeSlider extends AbstractSliderButton {
      private final String soundId;

      public VolumeSlider(int x, int y, int width, int height, Component title, double value, String soundId) {
         Objects.requireNonNull(CustomSoundsScreen.this);
         super(x, y, width, height, title, value);
         this.soundId = soundId;
         this.updateMessage();
      }

      protected void updateMessage() {
         this.setMessage(Component.literal(String.format("Vol: %d%%", (int)(this.value * (double)200.0F))));
      }

      protected void applyValue() {
         BomboConfig.Settings s = BomboConfig.get();
         float newVol = (float)(this.value * (double)2.0F);
         if (Math.abs(newVol - 1.0F) < 0.01F) {
            s.customSoundVolumes.remove(this.soundId);
         } else {
            s.customSoundVolumes.put(this.soundId, newVol);
         }

         BomboConfig.save();
      }
   }
}
