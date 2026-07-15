package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.auth.AccountManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public class AccountSwitcherWidget extends AbstractWidget {

    private boolean expanded = false;
    private final Screen parent;
    private final int ITEM_HEIGHT = 22;

    public AccountSwitcherWidget(int x, int y, Screen parent) {
        super(x, y, 140, 24, Component.empty());
        this.parent = parent;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width
                && mouseY < this.getY() + this.height;

        List<AccountManager.Account> accounts = new java.util.ArrayList<>(AccountManager.accounts);
        accounts.removeIf(a -> a == AccountManager.currentAccount);

        int totalHeight = this.height;
        if (expanded) {
            totalHeight += accounts.size() * ITEM_HEIGHT + ITEM_HEIGHT;
        }

        if (expanded) {
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + totalHeight, 0xD0000000);
        } else {
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
                    hovered ? 0x90000000 : 0x70000000);
        }

        AccountManager.Account current = AccountManager.currentAccount;
        String name = current != null ? current.username : mc.getUser().getName();

        if (current != null) {
            drawHead(g, current, this.getX() + 4, this.getY() + 4);
        }
        g.text(mc.font, name, this.getX() + 24, this.getY() + (this.height - mc.font.lineHeight) / 2, 0xFFFFFFFF, true);

        // draw down arrow
        g.text(mc.font, expanded ? "▲" : "▼", this.getX() + this.width - 12,
                this.getY() + (this.height - mc.font.lineHeight) / 2, 0xFFFFFFFF, true);

        if (expanded) {
            int curY = this.getY() + this.height;
            for (AccountManager.Account acc : accounts) {
                boolean itemHovered = mouseX >= this.getX() && mouseY >= curY && mouseX < this.getX() + this.width
                        && mouseY < curY + ITEM_HEIGHT;
                if (itemHovered) {
                    g.fill(this.getX(), curY, this.getX() + this.width, curY + ITEM_HEIGHT, 0x50FFFFFF);
                }
                drawHead(g, acc, this.getX() + 4, curY + 3);
                g.text(mc.font, acc.username, this.getX() + 24, curY + (ITEM_HEIGHT - mc.font.lineHeight) / 2,
                        0xFFFFFFFF, true);
                curY += ITEM_HEIGHT;
            }

            boolean addHovered = mouseX >= this.getX() && mouseY >= curY && mouseX < this.getX() + this.width
                    && mouseY < curY + ITEM_HEIGHT;
            if (addHovered) {
                g.fill(this.getX(), curY, this.getX() + this.width, curY + ITEM_HEIGHT, 0x50FFFFFF);
            }
            g.text(mc.font, "§a+ Add Account", this.getX() + 24, curY + (ITEM_HEIGHT - mc.font.lineHeight) / 2,
                    0xFFFFFFFF, true);
        }
    }

    private void drawHead(GuiGraphicsExtractor g, AccountManager.Account acc, int x, int y) {
        // Draw generic head background for now to simulate head
        g.fill(x, y, x + 16, y + 16, 0xFF555555);
        g.fill(x + 2, y + 2, x + 14, y + 14, 0xFF888888);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        boolean hoveredMain = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width
                && mouseY < this.getY() + this.height;
        if (hoveredMain) {
            expanded = !expanded;
            return true;
        }

        if (expanded) {
            List<AccountManager.Account> accounts = new java.util.ArrayList<>(AccountManager.accounts);
            accounts.removeIf(a -> a == AccountManager.currentAccount);

            int curY = this.getY() + this.height;
            for (AccountManager.Account acc : accounts) {
                if (mouseX >= this.getX() && mouseY >= curY && mouseX < this.getX() + this.width
                        && mouseY < curY + ITEM_HEIGHT) {
                    expanded = false;
                    AccountManager.refreshAccount(acc).thenAccept(refreshed -> {
                        Minecraft.getInstance().execute(() -> {
                            if (refreshed != null) {
                                AccountManager.setSession(refreshed);
                            } else {
                                // Failed to refresh
                            }
                        });
                    });
                    return true;
                }
                curY += ITEM_HEIGHT;
            }

            if (mouseX >= this.getX() && mouseY >= curY && mouseX < this.getX() + this.width
                    && mouseY < curY + ITEM_HEIGHT) {
                Minecraft.getInstance().setScreen(new AccountSwitcherScreen(parent));
                return true;
            }
        }

        expanded = false;
        return super.mouseClicked(event, handled);
    }

    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int totalHeight = this.height;
        if (expanded) {
            int count = AccountManager.accounts.size();
            if (AccountManager.originalAccount != null)
                count++;
            totalHeight += count * ITEM_HEIGHT + ITEM_HEIGHT;
        }
        return mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width
                && mouseY < this.getY() + totalHeight;
    }
}
