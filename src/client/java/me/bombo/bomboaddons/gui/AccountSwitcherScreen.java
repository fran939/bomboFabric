package me.bombo.bomboaddons.gui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import me.bombo.bomboaddons.auth.AccountManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.concurrent.CompletableFuture;

public class AccountSwitcherScreen extends Screen {
    private final Screen parent;
    private String deviceCodeMessage = "";
    private String deviceCodeUrl = "";
    private boolean awaitingAuth = false;
    private boolean[][] qrCodePixels = null;
    private int qrCodeSize = 0;
    private Button openLinkBtn;

    private void loadQrCode(String urlStr) {
        CompletableFuture.runAsync(() -> {
            try {
                String encoded = java.net.URLEncoder.encode(urlStr, "UTF-8");
                URL url = new URL("https://api.qrserver.com/v1/create-qr-code/?size=33x33&margin=0&data=" + encoded);
                BufferedImage img = ImageIO.read(url);
                if (img != null) {
                    int size = img.getWidth();
                    boolean[][] pixels = new boolean[size][size];
                    for (int x = 0; x < size; x++) {
                        for (int y = 0; y < size; y++) {
                            pixels[x][y] = (img.getRGB(x, y) & 0xFFFFFF) == 0;
                        }
                    }
                    Minecraft.getInstance().execute(() -> {
                        qrCodeSize = size;
                        qrCodePixels = pixels;
                    });
                }
            } catch (Exception e) {}
        });
    }

    public AccountSwitcherScreen(Screen parent) {
        super(Component.literal("Account Switcher"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int yOffset = height / 4;

        this.addRenderableWidget(Button.builder(Component.literal("Add Account (Microsoft)"), button -> {
            if (awaitingAuth) return;
            awaitingAuth = true;
            deviceCodeMessage = "Requesting code...";
            if (openLinkBtn != null) openLinkBtn.visible = false;
            
            AccountManager.requestDeviceCode().thenAccept(obj -> {
                if (obj != null && obj.has("user_code")) {
                    String userCode = obj.get("user_code").getAsString();
                    deviceCodeUrl = obj.get("verification_uri").getAsString();
                    deviceCodeMessage = "Go to " + deviceCodeUrl + " and enter code: " + userCode + " (Use Incognito to switch accounts)";
                    
                    // Copy to clipboard
                    Minecraft.getInstance().keyboardHandler.setClipboard(userCode);
                    this.loadQrCode(deviceCodeUrl);
                    
                    Minecraft.getInstance().execute(() -> {
                        if (openLinkBtn != null) openLinkBtn.visible = true;
                    });

                    AccountManager.pollForToken(obj.get("device_code").getAsString(), acc -> {
                        awaitingAuth = false;
                        deviceCodeMessage = "";
                        Minecraft.getInstance().execute(() -> {
                            this.init(); // Refresh UI
                        });
                    }, err -> {
                        awaitingAuth = false;
                        deviceCodeMessage = "Error: " + err;
                    });
                } else {
                    awaitingAuth = false;
                    if (obj != null && obj.has("error")) {
                        deviceCodeMessage = "Failed: " + obj.get("error_description").getAsString();
                    } else {
                        deviceCodeMessage = "Failed to request device code. " + (obj != null ? obj.toString() : "null");
                    }
                }
            }).exceptionally(ex -> {
                awaitingAuth = false;
                deviceCodeMessage = "Exception: " + ex.getMessage();
                return null;
            });
        }).bounds(width / 2 - 100, height - 55, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(width / 2 - 100, height - 30, 200, 20).build());

        int count = 0;
        for (AccountManager.Account acc : AccountManager.accounts) {
            final AccountManager.Account currentAcc = acc;
            int btnY = yOffset + (count * 25);
            Component label = Component.literal(acc.username + (AccountManager.currentAccount == acc ? " (Active)" : ""));
            
            this.addRenderableWidget(Button.builder(label, button -> {
                button.active = false;
                button.setMessage(Component.literal("Refreshing..."));
                AccountManager.refreshAccount(currentAcc).thenAccept(refreshed -> {
                    Minecraft.getInstance().execute(() -> {
                        if (refreshed != null) {
                            AccountManager.setSession(refreshed);
                            this.init();
                        } else {
                            button.active = true;
                            button.setMessage(Component.literal("\u00a7c" + currentAcc.username + " \u00a77(Refresh failed!)"));
                        }
                    });
                });
            }).bounds(width / 2 - 100, btnY, 170, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("X"), button -> {
                AccountManager.accounts.remove(currentAcc);
                AccountManager.saveAccounts();
                if (AccountManager.currentAccount == currentAcc) AccountManager.currentAccount = null;
                this.init();
            }).bounds(width / 2 + 75, btnY, 25, 20).build());

            count++;
        }
        
        openLinkBtn = Button.builder(Component.literal("Open Link in Browser"), button -> {
            if (deviceCodeUrl != null && !deviceCodeUrl.isEmpty()) {
                try {
                    net.minecraft.util.Util.getPlatform().openUri(new URI(deviceCodeUrl));
                } catch (Exception e) {}
            }
        }).bounds(width / 2 - 100, height - 80, 200, 20).build();
        openLinkBtn.visible = awaitingAuth && deviceCodeUrl != null && !deviceCodeUrl.isEmpty();
        this.addRenderableWidget(openLinkBtn);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        graphics.centeredText(this.font, "Account Switcher", this.width / 2, 20, 0xFFFFFFFF);

        if (awaitingAuth) {
            graphics.centeredText(this.font, deviceCodeMessage, this.width / 2, this.height - 110, 0xFFFFFF00);
            graphics.centeredText(this.font, "Code copied to clipboard!", this.width / 2, this.height - 95, 0xFF00FF00);
            
            if (qrCodePixels != null) {
                int scale = 3;
                int qrW = qrCodeSize * scale;
                int startX = this.width / 2 - qrW / 2;
                int startY = this.height / 2 - qrW / 2 - 40;

                graphics.fill(startX - 5, startY - 5, startX + qrW + 5, startY + qrW + 5, 0xFFFFFFFF);

                for (int x = 0; x < qrCodeSize; x++) {
                    for (int y = 0; y < qrCodeSize; y++) {
                        if (qrCodePixels[x][y]) {
                            graphics.fill(startX + x * scale, startY + y * scale, startX + (x + 1) * scale, startY + (y + 1) * scale, 0xFF000000);
                        }
                    }
                }
            }
            
        } else if (!deviceCodeMessage.isEmpty()) {
            graphics.centeredText(this.font, deviceCodeMessage, this.width / 2, this.height - 110, 0xFFFF0000);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        return super.mouseClicked(event, handled);
    }
}
