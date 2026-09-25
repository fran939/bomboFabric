package me.bombo.bomboaddons.mixin;

import com.mojang.brigadier.CommandDispatcher;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.GardenMacroDetector;
import me.bombo.bomboaddons.IRCClient;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public class ClientPacketListenerMixin {
   private static final ThreadLocal<Boolean> IS_HANDLING_SEND_CHAT = ThreadLocal.withInitial(() -> false);
   private static final ThreadLocal<Boolean> IS_HANDLING_SEND_COMMAND = ThreadLocal.withInitial(() -> false);

   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendChat(String message, CallbackInfo ci) {
      if (IS_HANDLING_SEND_CHAT.get()) return;
      if (message != null) {
         if (me.bombo.bomboaddons.features.chat.ChatHistoryTracker.OUTGOING_TRIGGER.get() == null
               && Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
            me.bombo.bomboaddons.features.chat.ChatHistoryTracker.OUTGOING_PLAYER_INPUT.set(true);
         }
         me.bombo.bomboaddons.features.chat.ChatHistoryTracker.recordOutgoing(message, false);
      }
      if (message != null && message.contains("$imgur")) {
         ci.cancel();
         ClientPacketListener listener = (ClientPacketListener)(Object)this;
         me.bombo.bomboaddons.util.ClipboardImageUploader.processOutgoingMessage(message, resolvedMsg -> {
            IS_HANDLING_SEND_CHAT.set(true);
            try {
               listener.sendChat(resolvedMsg);
            } finally {
               IS_HANDLING_SEND_CHAT.set(false);
            }
         });
         return;
      }
      String replaced = SkyblockUtils.replaceCoordPlaceholders(message);
      if (BomboConfig.get().ircChatEnabled && BomboConfig.get().ircDefaultChat) {
         if (!replaced.startsWith("/")) {
            ci.cancel();
            IRCClient.sendMessage(replaced);
            return;
         }
      }
      if (!replaced.equals(message)) {
         ci.cancel();
         ClientPacketListener listener = (ClientPacketListener)(Object)this;
         IS_HANDLING_SEND_CHAT.set(true);
         try {
            listener.sendChat(replaced);
         } finally {
            IS_HANDLING_SEND_CHAT.set(false);
         }
      }
   }

   @Inject(
      method = {"sendCommand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendCommand(String command, CallbackInfo ci) {
      if (IS_HANDLING_SEND_COMMAND.get()) {
         return;
      }
      if (command != null) {
         if (me.bombo.bomboaddons.features.chat.ChatHistoryTracker.OUTGOING_TRIGGER.get() == null
               && Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
            me.bombo.bomboaddons.features.chat.ChatHistoryTracker.OUTGOING_PLAYER_INPUT.set(true);
         }
         me.bombo.bomboaddons.features.chat.ChatHistoryTracker.recordOutgoing("/" + command, true);
      }
      if (command != null && command.contains("$imgur")) {
         ci.cancel();
         ClientPacketListener listener = (ClientPacketListener)(Object)this;
         me.bombo.bomboaddons.util.ClipboardImageUploader.processOutgoingMessage(command, resolvedCmd -> {
            IS_HANDLING_SEND_COMMAND.set(true);
            try {
               listener.sendCommand(resolvedCmd);
            } finally {
               IS_HANDLING_SEND_COMMAND.set(false);
            }
         });
         return;
      }
      if (command != null) {
         String replaced = SkyblockUtils.replaceCoordPlaceholders(command);
         if (!replaced.equals(command)) {
            ci.cancel();
            IS_HANDLING_SEND_COMMAND.set(true);
            try {
               ClientPacketListener listener = (ClientPacketListener)(Object)this;
               listener.sendCommand(replaced);
            } finally {
               IS_HANDLING_SEND_COMMAND.set(false);
            }
            return;
         } else {
            String clean = command.trim();
            if (clean.startsWith("/")) {
               clean = clean.substring(1).trim();
            }

            if (clean.toLowerCase().startsWith("cc ")) {
               clean = clean.substring(3).trim();
               if (clean.startsWith("/")) {
                  clean = clean.substring(1).trim();
               }
            } else if (clean.toLowerCase().startsWith("copychat ")) {
               clean = clean.substring(9).trim();
               if (clean.startsWith("/")) {
                  clean = clean.substring(1).trim();
               }
            }

            String lower = clean.toLowerCase();
            if (!lower.equals("b last") && !lower.equals("bombo last") && !lower.startsWith("b last ") && !lower.startsWith("bombo last ") && !lower.equals("locraw") && !lower.startsWith("locraw ")) {
               SkyblockUtils.lastExecutedCommand = clean;
            }

            if (lower.startsWith("chat ")) {
               String channelArg = lower.substring(5).trim();
               if (!channelArg.equals("b")) {
                  String normalizedTarget = BomboaddonsClient.normalizeChannel(channelArg);
                  String normalizedCurrent = BomboaddonsClient.normalizeChannel(BomboaddonsClient.currentHypixelChannel);
                  boolean wasIrc = BomboConfig.get().ircDefaultChat;
                  if (wasIrc) {
                     BomboConfig.get().ircDefaultChat = false;
                     BomboConfig.save();
                     Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
                  }

                  if (normalizedTarget.equals(normalizedCurrent)) {
                     ci.cancel();
                     return;
                  }
               }
            } else if (lower.equals("chat")) {
               if (BomboConfig.get().ircDefaultChat) {
                  BomboConfig.get().ircDefaultChat = false;
                  BomboConfig.save();
                  Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
               }
            } else if ((lower.startsWith("tell ") || lower.startsWith("w ") || lower.startsWith("msg ") || lower.startsWith("r ") || lower.startsWith("reply ") || lower.startsWith("whisper ") || lower.startsWith("message ")) && BomboConfig.get().ircDefaultChat) {
               BomboConfig.get().ircDefaultChat = false;
               BomboConfig.save();
               Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Default chat set to §ePublic§7."));
            }

         }
      }
   }

   @Inject(
      method = {"handleCommands"},
      at = {@At("TAIL")}
   )
   private void onHandleCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
      try {
         ClientPacketListener listener = (ClientPacketListener)(Object)this;
         CommandDispatcher<ClientSuggestionProvider> dispatcher = listener.getCommands();
         if (dispatcher != null) {
            BomboaddonsClient.registerMsgCommandsToDispatcher(dispatcher);
            BomboaddonsClient.registerBCommandsToDispatcher(dispatcher);

            for(String alias : BomboConfig.get().commandAliases.keySet()) {
               BomboaddonsClient.registerAliasToDispatcher(dispatcher, alias);
            }
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("HEAD")}
   )
   private void onHandleMovePlayerHead(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      try {
         GardenMacroDetector.onMovePlayerPacketHead();
      } catch (Throwable var4) {
      }

   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("RETURN")}
   )
   private void onHandleMovePlayerReturn(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      try {
         GardenMacroDetector.onMovePlayerPacketTail();
      } catch (Throwable var4) {
      }

   }

   @Inject(
      method = {"handleSetEntityPassengersPacket"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandlePassengers(ClientboundSetPassengersPacket packet, CallbackInfo ci) {
      if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(packet.getVehicle()) == null) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleSoundEvent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandleSoundEvent(net.minecraft.network.protocol.game.ClientboundSoundPacket packet, CallbackInfo ci) {
      try {
         String loc = packet.getSound().unwrapKey().map(k -> k.identifier().toString()).orElseGet(() -> packet.getSound().value().location().toString());
         String repl = me.bombo.bomboaddons.features.sounds.CustomSoundManager.getReplacement(loc);
         if (repl != null && !repl.trim().isEmpty()) {
            ci.cancel();
            float vol = packet.getVolume();
            float pitch = packet.getPitch();
            if (BomboConfig.get().debugSounds) {
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null) {
                  mc.player.sendSystemMessage(Component.literal("§8[§bSound Packet Debug§8] §cBlocked: §e" + loc + " ➔ §aReplacing with: §e" + repl));
               }
            }
            me.bombo.bomboaddons.features.sounds.CustomSoundManager.playCustomOrVanillaSound(repl, vol, pitch);
         } else if (BomboConfig.get().debugSounds) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               mc.player.sendSystemMessage(Component.literal("§8[§bSound Packet Debug§8] §7[Packet] §e" + loc + " §7(v:" + packet.getVolume() + " p:" + packet.getPitch() + ")"));
            }
         }
      } catch (Throwable ignored) {}
   }

   @Inject(
      method = {"handleSoundEntityEvent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandleSoundEntityEvent(net.minecraft.network.protocol.game.ClientboundSoundEntityPacket packet, CallbackInfo ci) {
      try {
         String loc = packet.getSound().unwrapKey().map(k -> k.identifier().toString()).orElseGet(() -> packet.getSound().value().location().toString());
         String repl = me.bombo.bomboaddons.features.sounds.CustomSoundManager.getReplacement(loc);
         if (repl != null && !repl.trim().isEmpty()) {
            ci.cancel();
            float vol = packet.getVolume();
            float pitch = packet.getPitch();
            if (BomboConfig.get().debugSounds) {
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null) {
                  mc.player.sendSystemMessage(Component.literal("§8[§bSound Packet Debug§8] §cBlocked Entity Sound: §e" + loc + " ➔ §aReplacing with: §e" + repl));
               }
            }
            me.bombo.bomboaddons.features.sounds.CustomSoundManager.playCustomOrVanillaSound(repl, vol, pitch);
         } else if (BomboConfig.get().debugSounds) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               mc.player.sendSystemMessage(Component.literal("§8[§bSound Packet Debug§8] §7[Entity Packet] §e" + loc + " §7(v:" + packet.getVolume() + " p:" + packet.getPitch() + ")"));
            }
         }
      } catch (Throwable ignored) {}
   }

   @Inject(
      method = {"handleLogin"},
      at = {@At("RETURN")}
   )
   private void onHandleLogin(net.minecraft.network.protocol.game.ClientboundLoginPacket packet, CallbackInfo ci) {
      BomboaddonsClient.lobbyJoinTime = System.currentTimeMillis();
   }

   @Inject(
      method = {"handleBlockUpdate"},
      at = {@At("HEAD")}
   )
   private void onHandleBlockUpdate(net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
      if (packet.getBlockState().isAir()) {
         me.bombo.bomboaddons.GardenMovement.notifyBlockBroken();
      }
   }

   @Inject(
      method = {"handleSetTime"},
      at = {@At("HEAD")}
   )
   private void onHandleSetTime(net.minecraft.network.protocol.game.ClientboundSetTimePacket packet, CallbackInfo ci) {
      try {
         for (java.lang.reflect.Field f : packet.getClass().getDeclaredFields()) {
            if (f.getType() == long.class) {
               f.setAccessible(true);
               long val = f.getLong(packet);
               if (BomboaddonsClient.serverRawGameTime == 0) {
                  BomboaddonsClient.serverRawGameTime = val;
               } else {
                  BomboaddonsClient.serverRawDayTime = val;
                  break;
               }
            }
         }
      } catch (Throwable ignored) {}
   }

   @Inject(
      method = {"handleSetEntityData"},
      at = {@At("TAIL")}
   )
   private void onHandleSetEntityData(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
      try {
         BomboConfig.Settings s = BomboConfig.get();
         if (s != null && s.showDamageDebug) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
               net.minecraft.world.entity.Entity entity = mc.level.getEntity(packet.id());
               if (entity != null && entity.hasCustomName()) {
                  Component customName = entity.getCustomName();
                  String nameStr = customName != null ? customName.getString() : "";
                  if (!nameStr.isEmpty()) {
                     String clean = net.minecraft.ChatFormatting.stripFormatting(nameStr).trim();
                     // Check for actual damage splash format: e.g. ✧214✧, 214k, or pure damage number popups
                     // Filter out holograms, leaderboards, player names, and mob health tags
                     boolean hasInvalidChars = clean.contains("-") || clean.contains("[") || clean.contains("]")
                             || clean.contains(":") || clean.contains("/") || clean.contains("Lv") || clean.contains("lv");
                     String numOnly = clean.replace("✧", "").replace("✦", "").replace("✥", "").replace("❤", "").replace("☣", "").replace(" ", "").trim();
                     boolean isDamageNumber = !hasInvalidChars && numOnly.matches("^[\\d,.]+[kKmMbB]?$") && numOnly.matches(".*\\d+.*") && numOnly.length() <= 15;
                     boolean isCrit = clean.contains("✧");
                     if (isDamageNumber) {
                        if (!s.damageDebugCritsOnly || isCrit) {
                           mc.player.sendSystemMessage(Component.literal("§8[§cDamage Debug§8] ").append(customName));
                        }
                     }
                  }
               }
            }
         }
      } catch (Throwable ignored) {}
   }

   @Inject(
      method = {"handleParticleEvent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandleParticleEvent(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
      try {
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null || packet == null || packet.getParticle() == null) return;
         String typeName = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString().toLowerCase();
         if (s.noRenderExplosions && (typeName.contains("explosion") || typeName.contains("gust"))) {
            ci.cancel();
            return;
         }
         if (s.noRenderDeadPoof && typeName.contains("poof")) {
            ci.cancel();
            return;
         }
         if (s.noRenderBreakParticles && (typeName.contains("block") || typeName.contains("dust_pillar") || typeName.contains("falling_dust"))) {
            ci.cancel();
            return;
         }
      } catch (Throwable ignored) {}
   }

   @Inject(
      method = {"handleExplosion"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onHandleExplosion(net.minecraft.network.protocol.game.ClientboundExplodePacket packet, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderExplosions) {
         ci.cancel();
      }
   }
}
