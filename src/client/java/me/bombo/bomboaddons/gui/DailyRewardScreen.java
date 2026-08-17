package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.DailyRewardHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DailyRewardScreen extends Screen {
   private final DailyRewardHelper.RewardPageData data;

   public DailyRewardScreen(DailyRewardHelper.RewardPageData data) {
      super(Component.literal("Hypixel Daily Reward"));
      this.data = data;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.fill(0, 0, this.width, this.height, -804648432);
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      graphics.centeredText(this.font, "§6§lHYPIXEL DAILY REWARD", centerX, centerY - 120, -22016);
      graphics.centeredText(this.font, "§eClick 1 of the 3 cards below to claim your reward:", centerX, centerY - 104, -171);
      if (this.data != null && this.data.cards != null && !this.data.cards.isEmpty()) {
         int totalW = this.data.cards.size() * 110 + (this.data.cards.size() - 1) * 20;
         int startX = centerX - totalW / 2;

         for(int i = 0; i < this.data.cards.size(); ++i) {
            DailyRewardHelper.RewardCard card = (DailyRewardHelper.RewardCard)this.data.cards.get(i);
            int cardX = startX + i * 130;
            int cardY = centerY - 75;
            int cardW = 110;
            int cardH = 160;
            boolean isHovered = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH;
            int drawY = isHovered ? cardY - 4 : cardY;
            int borderCol = isHovered ? card.getRarityColor() : card.getCardBorderColor();
            int bgCol = isHovered ? -266198482 : -267119589;
            graphics.fill(cardX - 2, drawY - 2, cardX + cardW + 2, drawY + cardH + 2, borderCol);
            graphics.fill(cardX, drawY, cardX + cardW, drawY + cardH, bgCol);
            String rarityText = "§l" + card.rarity.toUpperCase();
            graphics.centeredText(this.font, rarityText, cardX + cardW / 2, drawY + 10, card.getRarityColor());
            graphics.fill(cardX + 15, drawY + 23, cardX + cardW - 15, drawY + 24, card.getCardBorderColor());
            ItemStack iconStack = this.getIconForCard(card);
            graphics.pose().pushMatrix();
            graphics.pose().translate((float)(cardX + cardW / 2 - 8), (float)(drawY + 36));
            graphics.pose().scale(1.5F, 1.5F);
            graphics.item(iconStack, 0, 0);
            graphics.pose().popMatrix();
            graphics.centeredText(this.font, "§f" + card.title, cardX + cardW / 2, drawY + 82, -1);
            if (card.amount != null && !card.amount.isEmpty()) {
               graphics.centeredText(this.font, "§b" + card.amount, cardX + cardW / 2, drawY + 98, -11141121);
            }

            int btnBg = isHovered ? -16733696 : -16751104;
            graphics.fill(cardX + 10, drawY + 125, cardX + cardW - 10, drawY + 148, btnBg);
            graphics.fill(cardX + 11, drawY + 126, cardX + cardW - 11, drawY + 147, isHovered ? -16711936 : -16733696);
            graphics.centeredText(this.font, "§f§lCLAIM", cardX + cardW / 2, drawY + 132, -1);
         }

         if (this.data.currentStreak > 0 || this.data.highScore > 0) {
            String streakText = "§fDaily Streak: §b" + this.data.currentStreak + " §7| §fHigh Score: §e" + this.data.highScore;
            graphics.centeredText(this.font, streakText, centerX, centerY + 100, -1);
         }

      } else {
         graphics.centeredText(this.font, "§cNo reward data available.", centerX, centerY, -43691);
      }
   }

   private ItemStack getIconForCard(DailyRewardHelper.RewardCard card) {
      if (card != null && card.title != null) {
         String lower = card.title.toLowerCase();
         if (lower.contains("token")) {
            return new ItemStack(Items.GOLD_NUGGET);
         } else if (lower.contains("dust")) {
            return new ItemStack(Items.GLOWSTONE_DUST);
         } else if (!lower.contains("experience") && !lower.contains("xp")) {
            if (lower.contains("soul")) {
               return new ItemStack(Items.GHAST_TEAR);
            } else if (lower.contains("coin")) {
               return new ItemStack(Items.SUNFLOWER);
            } else {
               return !lower.contains("cosmetic") && !lower.contains("mystery") ? new ItemStack(Items.NETHER_STAR) : new ItemStack(Items.CHEST);
            }
         } else {
            return new ItemStack(Items.EXPERIENCE_BOTTLE);
         }
      } else {
         return new ItemStack(Items.CHEST);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      if (event.button() == 0 && this.data != null && this.data.cards != null) {
         int centerX = this.width / 2;
         int centerY = this.height / 2;
         int totalW = this.data.cards.size() * 110 + (this.data.cards.size() - 1) * 20;
         int startX = centerX - totalW / 2;
         int mouseX = (int)event.x();
         int mouseY = (int)event.y();

         for(int i = 0; i < this.data.cards.size(); ++i) {
            DailyRewardHelper.RewardCard card = (DailyRewardHelper.RewardCard)this.data.cards.get(i);
            int cardX = startX + i * 130;
            int cardY = centerY - 75;
            int cardW = 110;
            int cardH = 160;
            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
               Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
               DailyRewardHelper.claimReward(this.data, card);
               Minecraft.getInstance().setScreen((Screen)null);
               return true;
            }
         }
      }

      return super.mouseClicked(event, handled);
   }
}
