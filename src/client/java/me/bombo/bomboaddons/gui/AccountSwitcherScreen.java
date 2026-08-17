package me.bombo.bomboaddons.gui;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import me.bombo.bomboaddons.auth.AccountManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

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
            String encoded = URLEncoder.encode(urlStr, "UTF-8");
            URL url = new URL("https://api.qrserver.com/v1/create-qr-code/?size=33x33&margin=0&data=" + encoded);
            BufferedImage img = ImageIO.read(url);
            if (img != null) {
               int size = img.getWidth();
               boolean[][] pixels = new boolean[size][size];

               for(int x = 0; x < size; ++x) {
                  for(int y = 0; y < size; ++y) {
                     pixels[x][y] = (img.getRGB(x, y) & 16777215) == 0;
                  }
               }

               Minecraft.getInstance().execute(() -> {
                  this.qrCodeSize = size;
                  this.qrCodePixels = pixels;
               });
            }
         } catch (Exception var9) {
         }

      });
   }

   public AccountSwitcherScreen(Screen parent) {
      super(Component.literal("Account Switcher"));
      this.parent = parent;
   }

   protected void init() {
      super.init();
      int yOffset = this.height / 4;
      this.addRenderableWidget(Button.builder(Component.literal("Add Account (Microsoft)"), (button) -> {
         if (!this.awaitingAuth) {
            this.awaitingAuth = true;
            this.deviceCodeMessage = "Requesting code...";
            if (this.openLinkBtn != null) {
               this.openLinkBtn.visible = false;
            }

            AccountManager.requestDeviceCode().thenAccept((obj) -> {
               if (obj != null && obj.has("user_code")) {
                  String userCode = obj.get("user_code").getAsString();
                  this.deviceCodeUrl = obj.get("verification_uri").getAsString();
                  this.deviceCodeMessage = "Go to " + this.deviceCodeUrl + " and enter code: " + userCode + " (Use Incognito to switch accounts)";
                  Minecraft.getInstance().keyboardHandler.setClipboard(userCode);
                  this.loadQrCode(this.deviceCodeUrl);
                  Minecraft.getInstance().execute(() -> {
                     if (this.openLinkBtn != null) {
                        this.openLinkBtn.visible = true;
                     }

                  });
                  AccountManager.pollForToken(obj.get("device_code").getAsString(), (acc) -> {
                     this.awaitingAuth = false;
                     this.deviceCodeMessage = "";
                     Minecraft.getInstance().execute(() -> this.init());
                  }, (err) -> {
                     this.awaitingAuth = false;
                     this.deviceCodeMessage = "Error: " + err;
                  });
               } else {
                  this.awaitingAuth = false;
                  if (obj != null && obj.has("error")) {
                     this.deviceCodeMessage = "Failed: " + obj.get("error_description").getAsString();
                  } else {
                     String var10001 = obj != null ? obj.toString() : "null";
                     this.deviceCodeMessage = "Failed to request device code. " + var10001;
                  }
               }

            }).exceptionally((ex) -> {
               this.awaitingAuth = false;
               this.deviceCodeMessage = "Exception: " + ex.getMessage();
               return null;
            });
         }
      }).bounds(this.width / 2 - 100, this.height - 55, 200, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Back"), (button) -> Minecraft.getInstance().setScreen(this.parent)).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
      int count = 0;

      for(AccountManager.Account acc : AccountManager.accounts) {
         int btnY = yOffset + count * 25;
         Component label = Component.literal(acc.username + (AccountManager.currentAccount == acc ? " (Active)" : ""));
         this.addRenderableWidget(Button.builder(label, (button) -> {
            button.active = false;
            button.setMessage(Component.literal("Checking..."));
            AccountManager.refreshAccount(acc).thenAccept((refreshed) -> Minecraft.getInstance().execute(() -> {
                  if (refreshed != null) {
                     AccountManager.setSession(refreshed);
                     this.init();
                  } else {
                     button.active = true;
                     button.setMessage(Component.literal("§c" + acc.username + " §7(Refresh failed!)"));
                  }

               }));
         }).bounds(this.width / 2 - 100, btnY, 170, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("X"), (button) -> {
            AccountManager.accounts.remove(acc);
            AccountManager.saveAccounts();
            if (AccountManager.currentAccount == acc) {
               AccountManager.currentAccount = null;
            }

            this.init();
         }).bounds(this.width / 2 + 75, btnY, 25, 20).build());
         ++count;
      }

      this.openLinkBtn = Button.builder(Component.literal("Open Link in Browser"), (button) -> {
         if (this.deviceCodeUrl != null && !this.deviceCodeUrl.isEmpty()) {
            try {
               Util.getPlatform().openUri(new URI(this.deviceCodeUrl));
            } catch (Exception var3) {
            }
         }

      }).bounds(this.width / 2 - 100, this.height - 80, 200, 20).build();
      this.openLinkBtn.visible = this.awaitingAuth && this.deviceCodeUrl != null && !this.deviceCodeUrl.isEmpty();
      this.addRenderableWidget(this.openLinkBtn);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.fill(0, 0, this.width, this.height, Integer.MIN_VALUE);
      graphics.centeredText(this.font, "Account Switcher", this.width / 2, 20, -1);
      if (this.awaitingAuth) {
         graphics.centeredText(this.font, this.deviceCodeMessage, this.width / 2, this.height - 110, -256);
         graphics.centeredText(this.font, "Code copied to clipboard!", this.width / 2, this.height - 95, -16711936);
         if (this.qrCodePixels != null) {
            int scale = 3;
            int qrW = this.qrCodeSize * scale;
            int startX = this.width / 2 - qrW / 2;
            int startY = this.height / 2 - qrW / 2 - 40;
            graphics.fill(startX - 5, startY - 5, startX + qrW + 5, startY + qrW + 5, -1);

            for(int x = 0; x < this.qrCodeSize; ++x) {
               for(int y = 0; y < this.qrCodeSize; ++y) {
                  if (this.qrCodePixels[x][y]) {
                     graphics.fill(startX + x * scale, startY + y * scale, startX + (x + 1) * scale, startY + (y + 1) * scale, -16777216);
                  }
               }
            }
         }
      } else if (!this.deviceCodeMessage.isEmpty()) {
         graphics.centeredText(this.font, this.deviceCodeMessage, this.width / 2, this.height - 110, -65536);
      }

   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      return super.mouseClicked(event, handled);
   }
}
