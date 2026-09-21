package me.bombo.bomboaddons.features.sounds;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CustomSoundsScreen extends Screen {
    private final Screen parent;
    private EditBox originalSoundBox;
    private EditBox replacementSoundBox;
    private double scrollAmount = 0.0;
    private String editingOriginalKey = null;
    private final List<String> suggestions = new ArrayList<>();
    private int selectedSuggestionIdx = 0;
    private EditBox activeBoxForSuggestions = null;

    public CustomSoundsScreen(Screen parent) {
        super(Component.literal("§6§lCustom Sounds & Replacements"));
        this.parent = parent;
    }

    private void updateSuggestions(EditBox box, String input) {
        suggestions.clear();
        selectedSuggestionIdx = 0;
        activeBoxForSuggestions = box;
        String lower = input != null ? input.trim().toLowerCase() : "";

        if (box == this.replacementSoundBox) {
            // Suggest custom files first
            for (File f : CustomSoundManager.getCustomSoundFiles()) {
                if (lower.isEmpty() || f.getName().toLowerCase().contains(lower)) {
                    suggestions.add(f.getName());
                    if (suggestions.size() >= 12)
                        break;
                }
            }
        }

        // Suggest vanilla sound events
        for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                .keySet()) {
            String path = id.getPath();
            String full = id.toString();
            if (lower.isEmpty() || path.contains(lower) || full.contains(lower)) {
                if (!suggestions.contains(path) && !suggestions.contains(full)) {
                    suggestions.add(path);
                    if (suggestions.size() >= 12)
                        break;
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int topY = 40;
        int leftX = 20;

        // Top input boxes
        this.originalSoundBox = new EditBox(this.font, leftX, topY + 16, 210, 18, Component.literal("Original Sound"));
        this.originalSoundBox.setHint(Component.literal("§8Original Sound (e.g. entity.ender_dragon.hurt)"));
        if (editingOriginalKey != null) {
            this.originalSoundBox.setValue(editingOriginalKey);
        }
        this.originalSoundBox.setResponder(val -> updateSuggestions(this.originalSoundBox, val));
        this.addRenderableWidget(this.originalSoundBox);

        this.replacementSoundBox = new EditBox(this.font, leftX + 220, topY + 16, 210, 18,
                Component.literal("Replacement Sound / File"));
        this.replacementSoundBox.setHint(Component.literal("§8Replacement (e.g. sound.mp3, entity.cat.purreow)"));
        if (editingOriginalKey != null && BomboConfig.get().customSoundReplacements.containsKey(editingOriginalKey)) {
            this.replacementSoundBox.setValue(BomboConfig.get().customSoundReplacements.get(editingOriginalKey));
        }
        this.replacementSoundBox.setResponder(val -> updateSuggestions(this.replacementSoundBox, val));
        this.addRenderableWidget(this.replacementSoundBox);

        // Add / Save Button
        this.addRenderableWidget(Button.builder(Component.literal("§a+ Save / Replace"), btn -> {
            String orig = this.originalSoundBox.getValue().trim();
            String repl = this.replacementSoundBox.getValue().trim();
            if (!orig.isEmpty() && !repl.isEmpty()) {
                if (this.editingOriginalKey != null && !this.editingOriginalKey.equals(orig)) {
                    BomboConfig.get().customSoundReplacements.remove(this.editingOriginalKey);
                }
                BomboConfig.get().customSoundReplacements.put(orig, repl);
                BomboConfig.save();
                this.editingOriginalKey = null;
                this.originalSoundBox.setValue("");
                this.replacementSoundBox.setValue("");
                this.init();
            }
        }).bounds(leftX + 440, topY + 15, 120, 20).build());

        // Test Sound Button
        this.addRenderableWidget(Button.builder(Component.literal("§e▶ Test"), btn -> {
            String repl = this.replacementSoundBox.getValue().trim();
            if (!repl.isEmpty()) {
                CustomSoundManager.playCustomOrVanillaSound(repl, 1.0f, 1.0f);
            }
        }).bounds(leftX + 565, topY + 15, 60, 20).build());

        // Open Folder Button
        this.addRenderableWidget(Button.builder(Component.literal("§b📁 Open Folder"), btn -> {
            try {
                File dir = CustomSoundManager.getSoundsDirectory();
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", dir.getAbsolutePath()).start();
                } else {
                    new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).bounds(this.width - 130, 10, 110, 20).build());

        // Back Button
        this.addRenderableWidget(Button.builder(Component.literal("§c← Back"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.parent);
            }
        }).bounds(20, 10, 70, 20).build());
    }

    public void handleFilesDropped(List<Path> paths) {
        int imported = 0;
        for (Path p : paths) {
            if (CustomSoundManager.importSoundFile(p)) {
                imported++;
            }
        }
        if (imported > 0) {
            Bomboaddons.sendMessage("§8[§6Sounds§8] §aSuccessfully imported " + imported
                    + " sound file(s) into config/bomboaddons/sounds!");
            this.init();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Background overlay
        g.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);

        // Header Title
        g.text(this.font, "§6§lCustom Sounds & Sound Replacements", this.width / 2 - 120, 15, -1, true);
        g.text(this.font, "§7Drag & drop .mp3, .ogg, .wav, .mp4, .aiff files anywhere to import them!", 20, 80, -1,
                false);

        int listTop = 100;
        int listBottom = this.height - 30;

        // Container Box
        g.fill(16, listTop - 4, this.width - 16, listBottom + 4, 0x55000000);
        g.fill(16, listTop - 4, this.width - 16, listTop - 3, 0x77FFFFFF);

        List<Map.Entry<String, String>> list = new ArrayList<>(BomboConfig.get().customSoundReplacements.entrySet());
        int y = listTop + 4 - (int) this.scrollAmount;

        g.enableScissor(16, listTop, this.width - 16, listBottom);

        for (int i = 0; i < list.size(); i++) {
            Map.Entry<String, String> entry = list.get(i);
            String orig = entry.getKey();
            String repl = entry.getValue();
            boolean isEnabled = BomboConfig.get().disabledCustomSoundReplacements == null
                    || !BomboConfig.get().disabledCustomSoundReplacements.contains(orig);

            if (y + 24 >= listTop && y <= listBottom) {
                // Background row
                g.fill(20, y, this.width - 20, y + 22, (i % 2 == 0) ? 0x33FFFFFF : 0x22FFFFFF);

                // Text
                String text = (isEnabled ? "§e" : "§8[OFF] §7") + orig + " §7➔ " + (isEnabled ? "§a" : "§8") + repl;
                g.text(this.font, text, 26, y + 6, -1, false);

                // Right buttons: [Toggle ON/OFF] [Test] [Edit] [Del]
                int btnX = this.width - 190;

                // Toggle Button
                if (isEnabled) {
                    g.fill(btnX, y + 2, btnX + 32, y + 20, 0xFF15803D);
                    g.text(this.font, "§aON", btnX + 8, y + 6, -1, false);
                } else {
                    g.fill(btnX, y + 2, btnX + 32, y + 20, 0xFF475569);
                    g.text(this.font, "§7OFF", btnX + 6, y + 6, -1, false);
                }

                // Test / Play
                g.fill(btnX + 36, y + 2, btnX + 72, y + 20, 0xFF225588);
                g.text(this.font, "§bTest", btnX + 42, y + 6, -1, false);

                // Edit
                g.fill(btnX + 76, y + 2, btnX + 112, y + 20, 0xFF333344);
                g.text(this.font, "§eEdit", btnX + 82, y + 6, -1, false);

                // Del
                g.fill(btnX + 116, y + 2, btnX + 152, y + 20, 0xFF552222);
                g.text(this.font, "§cDel", btnX + 125, y + 6, -1, false);
            }
            y += 26;
        }

        // Custom files list at bottom
        List<File> files = CustomSoundManager.getCustomSoundFiles();
        if (!files.isEmpty()) {
            if (y + 20 >= listTop && y <= listBottom) {
                g.text(this.font, "§b§lImported Custom Audio Files (" + files.size() + "):", 26, y + 4, -1, true);
            }
            y += 18;
            for (File f : files) {
                if (y + 20 >= listTop && y <= listBottom) {
                    g.text(this.font, "§f• " + f.getName(), 34, y + 4, -1, false);
                    int playBtnX = this.width - 70;
                    g.fill(playBtnX, y, playBtnX + 45, y + 16, 0xFF225588);
                    g.text(this.font, "§bPlay", playBtnX + 10, y + 4, -1, false);
                }
                y += 20;
            }
        }

        g.disableScissor();

        // Render suggestion overlay dropdown
        if (activeBoxForSuggestions != null && activeBoxForSuggestions.isFocused() && !suggestions.isEmpty()) {
            int sx = activeBoxForSuggestions.getX();
            int sy = activeBoxForSuggestions.getY() + activeBoxForSuggestions.getHeight() + 2;
            int sw = activeBoxForSuggestions.getWidth();
            int sh = suggestions.size() * 14 + 4;

            g.fill(sx - 1, sy - 1, sx + sw + 1, sy + sh + 1, 0xF010141C);
            g.outline(sx - 1, sy - 1, sw + 2, sh + 2, 0xFF3C4452);

            for (int i = 0; i < suggestions.size(); i++) {
                int itemY = sy + 2 + i * 14;
                boolean isSel = (i == selectedSuggestionIdx);
                if (isSel) {
                    g.fill(sx, itemY, sx + sw, itemY + 14, 0x5538BDF8);
                }
                g.text(this.font, (isSel ? "§b▶ §f" : "§7  ") + suggestions.get(i), sx + 4, itemY + 3, -1, false);
            }
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        if (activeBoxForSuggestions != null && activeBoxForSuggestions.isFocused()) {
            if (keyCode == 258) { // TAB
                if (suggestions.isEmpty()) {
                    updateSuggestions(activeBoxForSuggestions, activeBoxForSuggestions.getValue());
                }
                if (!suggestions.isEmpty()) {
                    selectedSuggestionIdx = (selectedSuggestionIdx + 1) % suggestions.size();
                    String sel = suggestions.get(selectedSuggestionIdx);
                    activeBoxForSuggestions.setValue(sel);
                    return true;
                }
            } else if (!suggestions.isEmpty()) {
                if (keyCode == 264) { // DOWN
                    selectedSuggestionIdx = (selectedSuggestionIdx + 1) % suggestions.size();
                    String sel = suggestions.get(selectedSuggestionIdx);
                    activeBoxForSuggestions.setValue(sel);
                    return true;
                } else if (keyCode == 265) { // UP
                    selectedSuggestionIdx = (selectedSuggestionIdx - 1 + suggestions.size()) % suggestions.size();
                    String sel = suggestions.get(selectedSuggestionIdx);
                    activeBoxForSuggestions.setValue(sel);
                    return true;
                } else if (keyCode == 257 || keyCode == 335) { // ENTER
                    String sel = suggestions.get(selectedSuggestionIdx);
                    activeBoxForSuggestions.setValue(sel);
                    suggestions.clear();
                    return true;
                } else if (keyCode == 256) { // ESCAPE
                    suggestions.clear();
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Check if clicked inside dropdown
        if (activeBoxForSuggestions != null && activeBoxForSuggestions.isFocused() && !suggestions.isEmpty()) {
            int sx = activeBoxForSuggestions.getX();
            int sy = activeBoxForSuggestions.getY() + activeBoxForSuggestions.getHeight() + 2;
            int sw = activeBoxForSuggestions.getWidth();
            int sh = suggestions.size() * 14 + 4;
            if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
                int clickedIdx = (int) ((mouseY - sy - 2) / 14);
                if (clickedIdx >= 0 && clickedIdx < suggestions.size()) {
                    activeBoxForSuggestions.setValue(suggestions.get(clickedIdx));
                    suggestions.clear();
                    return true;
                }
            }
        }

        int listTop = 100;
        int listBottom = this.height - 30;

        if (mouseX >= 20 && mouseX <= this.width - 20 && mouseY >= listTop && mouseY <= listBottom) {
            List<Map.Entry<String, String>> list = new ArrayList<>(
                    BomboConfig.get().customSoundReplacements.entrySet());
            int y = listTop + 4 - (int) this.scrollAmount;

            for (int i = 0; i < list.size(); i++) {
                if (mouseY >= y && mouseY <= y + 22) {
                    Map.Entry<String, String> entry = list.get(i);
                    int btnX = this.width - 190;
                    if (mouseX >= btnX && mouseX <= btnX + 32) { // Toggle ON/OFF
                        if (BomboConfig.get().disabledCustomSoundReplacements == null) {
                            BomboConfig.get().disabledCustomSoundReplacements = new java.util.HashSet<>();
                        }
                        if (BomboConfig.get().disabledCustomSoundReplacements.contains(entry.getKey())) {
                            BomboConfig.get().disabledCustomSoundReplacements.remove(entry.getKey());
                        } else {
                            BomboConfig.get().disabledCustomSoundReplacements.add(entry.getKey());
                        }
                        BomboConfig.save();
                        return true;
                    } else if (mouseX >= btnX + 36 && mouseX <= btnX + 72) { // Test
                        CustomSoundManager.playCustomOrVanillaSound(entry.getValue(), 1.0f, 1.0f);
                        return true;
                    } else if (mouseX >= btnX + 76 && mouseX <= btnX + 112) { // Edit
                        this.editingOriginalKey = entry.getKey();
                        this.originalSoundBox.setValue(entry.getKey());
                        this.replacementSoundBox.setValue(entry.getValue());
                        this.init();
                        return true;
                    } else if (mouseX >= btnX + 116 && mouseX <= btnX + 152) { // Del
                        BomboConfig.get().customSoundReplacements.remove(entry.getKey());
                        if (BomboConfig.get().disabledCustomSoundReplacements != null) {
                            BomboConfig.get().disabledCustomSoundReplacements.remove(entry.getKey());
                        }
                        BomboConfig.save();
                        this.init();
                        return true;
                    }
                }
                y += 26;
            }

            List<File> files = CustomSoundManager.getCustomSoundFiles();
            if (!files.isEmpty()) {
                y += 18;
                for (File f : files) {
                    if (mouseY >= y && mouseY <= y + 18) {
                        int playBtnX = this.width - 70;
                        if (mouseX >= playBtnX && mouseX <= playBtnX + 45) {
                            CustomSoundManager.playCustomOrVanillaSound(f.getName(), 1.0f, 1.0f);
                            return true;
                        }
                    }
                    y += 20;
                }
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int totalItems = BomboConfig.get().customSoundReplacements.size() * 26
                + CustomSoundManager.getCustomSoundFiles().size() * 20 + 40;
        double maxScroll = Math.max(0, totalItems - (this.height - 130));
        this.scrollAmount = Math.max(0.0, Math.min(maxScroll, this.scrollAmount - vertical * 20.0));
        return true;
    }
}
