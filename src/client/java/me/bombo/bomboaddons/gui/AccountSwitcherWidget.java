package me.bombo.bomboaddons.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.bombo.bomboaddons.auth.AccountManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class AccountSwitcherWidget extends AbstractWidget {
   private boolean expanded = false;
   private final Screen parent;
   private final int ITEM_HEIGHT = 22;

   public AccountSwitcherWidget(int x, int y, Screen parent) {
      super(x, y, 140, 24, Component.empty());
      this.parent = parent;
   }

   public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
      Minecraft mc = Minecraft.getInstance();
      boolean hovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
      List<AccountManager.Account> accounts = new ArrayList(AccountManager.accounts);
      accounts.removeIf((a) -> a == AccountManager.currentAccount);
      int totalHeight = this.height;
      if (this.expanded) {
         totalHeight += accounts.size() * 22 + 22;
      }

      if (this.expanded) {
         g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + totalHeight, -805306368);
      } else {
         g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, hovered ? -1879048192 : 1879048192);
      }

      AccountManager.Account current = AccountManager.currentAccount;
      String name = current != null ? current.username : mc.getUser().getName();
      if (current != null) {
         this.drawHead(g, current, this.getX() + 4, this.getY() + 4);
      }

      Font var10001 = mc.font;
      int var10003 = this.getX() + 24;
      int var10004 = this.getY();
      int var10005 = this.height;
      Objects.requireNonNull(mc.font);
      g.text(var10001, name, var10003, var10004 + (var10005 - 9) / 2, -1, true);
      var10001 = mc.font;
      String var10002 = this.expanded ? "▲" : "▼";
      var10003 = this.getX() + this.width - 12;
      var10004 = this.getY();
      var10005 = this.height;
      Objects.requireNonNull(mc.font);
      g.text(var10001, var10002, var10003, var10004 + (var10005 - 9) / 2, -1, true);
      if (this.expanded) {
         int curY = this.getY() + this.height;

         for(AccountManager.Account acc : accounts) {
            boolean itemHovered = mouseX >= this.getX() && mouseY >= curY && mouseX < this.getX() + this.width && mouseY < curY + 22;
            if (itemHovered) {
               g.fill(this.getX(), curY, this.getX() + this.width, curY + 22, 1358954495);
            }

            this.drawHead(g, acc, this.getX() + 4, curY + 3);
            var10001 = mc.font;
            var10002 = acc.username;
            var10003 = this.getX() + 24;
            Objects.requireNonNull(mc.font);
            g.text(var10001, var10002, var10003, curY + (22 - 9) / 2, -1, true);
            curY += 22;
         }

         boolean addHovered = mouseX >= this.getX() && mouseY >= curY && mouseX < this.getX() + this.width && mouseY < curY + 22;
         if (addHovered) {
            g.fill(this.getX(), curY, this.getX() + this.width, curY + 22, 1358954495);
         }

         var10001 = mc.font;
         var10003 = this.getX() + 24;
         Objects.requireNonNull(mc.font);
         g.text(var10001, "§a+ Add Account", var10003, curY + (22 - 9) / 2, -1, true);
      }

   }

   private void drawHead(GuiGraphicsExtractor g, AccountManager.Account acc, int x, int y) {
      g.fill(x, y, x + 16, y + 16, -11184811);
      g.fill(x + 2, y + 2, x + 14, y + 14, -7829368);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      double mouseX = event.x();
      double mouseY = event.y();
      boolean hoveredMain = mouseX >= (double)this.getX() && mouseY >= (double)this.getY() && mouseX < (double)(this.getX() + this.width) && mouseY < (double)(this.getY() + this.height);
      if (hoveredMain) {
         this.expanded = !this.expanded;
         return true;
      } else {
         if (this.expanded) {
            List<AccountManager.Account> accounts = new ArrayList(AccountManager.accounts);
            accounts.removeIf((a) -> a == AccountManager.currentAccount);
            int curY = this.getY() + this.height;

            for(AccountManager.Account acc : accounts) {
               if (mouseX >= (double)this.getX() && mouseY >= (double)curY && mouseX < (double)(this.getX() + this.width) && mouseY < (double)(curY + 22)) {
                  this.expanded = false;
                  AccountManager.refreshAccount(acc).thenAccept((refreshed) -> Minecraft.getInstance().execute(() -> {
                        if (refreshed != null) {
                           AccountManager.setSession(refreshed);
                        }

                     }));
                  return true;
               }

               curY += 22;
            }

            if (mouseX >= (double)this.getX() && mouseY >= (double)curY && mouseX < (double)(this.getX() + this.width) && mouseY < (double)(curY + 22)) {
               Minecraft.getInstance().setScreen(new AccountSwitcherScreen(this.parent));
               return true;
            }
         }

         this.expanded = false;
         return super.mouseClicked(event, handled);
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      int totalHeight = this.height;
      if (this.expanded) {
         int count = AccountManager.accounts.size();
         if (AccountManager.originalAccount != null) {
            ++count;
         }

         totalHeight += count * 22 + 22;
      }

      return mouseX >= (double)this.getX() && mouseY >= (double)this.getY() && mouseX < (double)(this.getX() + this.width) && mouseY < (double)(this.getY() + totalHeight);
   }
}
