package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An in-game command terminal ({@code /b cmd}).
 *
 * <p>Real shell execution on the machine running the client, with the output rendered inside
 * Minecraft instead of an OS window. Commands run one per line, stream their output live, and
 * the buffer keeps scrollback plus a command history (arrow keys).
 *
 * <p><b>The session outlives the screen.</b> Buffer, history and any running command are held in
 * static state: closing the terminal ({@code Esc}) does not kill a running command or wipe the
 * scrollback - reopening with {@code /b cmd} shows everything exactly as it was, streaming
 * included. {@code Ctrl+L} or the {@code clear} built-in wipes the buffer on demand.
 *
 * <p>Built-ins ({@code help}, {@code clear}, {@code close}, {@code ver}, {@code flavor}, {@code echo})
 * are handled locally; <b>everything else is handed to the platform shell</b> - {@code ping},
 * {@code ipconfig}, {@code curl}, {@code ssh}, {@code java -version} and so on all work exactly as
 * they would in a normal terminal, relative to the Minecraft directory.
 *
 * <p>Two honest limits: there is no pseudo-terminal, so interactive full-screen programs
 * ({@code ssh} asking for a password, {@code vim}, {@code top}) will not behave interactively; and
 * nothing is sandboxed - this is your own shell with your own permissions.
 */
public class CmdScreen extends Screen {

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*[ -/]*[@-~]");
    private static final int MAX_LINES = 2000;

    // ------------------------------------------------------------------
    // Session state (static: survives closing the screen)
    // ------------------------------------------------------------------
    private static final List<String> SESSION_LINES = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> SESSION_HISTORY = new ArrayList<>();
    private static volatile int sessionVersion = 0;
    private static volatile boolean sessionRunning = false;
    private static String sessionInput = "";
    private static int sessionCursor = 0;
    private static int sessionHistoryIndex = -1;

    private final Screen parent;

    // Per-instance view state
    private int renderedVersion = -1;
    private List<String> snapshot = List.of();
    private boolean snapToBottom = true;
    private int scrollOffset = 0;

    // Live input line (mirrored to the session on close via removed())
    private String input = "";
    private int cursor = 0;

    private int winX, winY, winW, winH;
    private final int rowH = 10;
    private final int padX = 8;

    public CmdScreen(Screen parent, String initialCommand) {
        super(Component.literal("Bombo Terminal"));
        this.parent = parent;
        boolean firstOpen = SESSION_LINES.isEmpty();
        if (firstOpen) {
            print("§7BomboAddons terminal §8- §f" + me.bombo.bomboaddons.Constants.identityLine()
                    + " §8| §7type §fhelp§7 for built-ins");
            print("§8Runs real shell commands on this machine. Esc closes (session keeps running), ↑/↓ recall history.");
        } else {
            print("§8[session resumed - " + SESSION_LINES.size() + " lines"
                    + (sessionRunning ? ", §ecommand still running§8" : "") + "]");
        }
        if (initialCommand != null && !initialCommand.trim().isEmpty()) {
            run(initialCommand.trim());
        }
        // Adopt the persisted input line so reopening continues mid-typed command.
        input = sessionInput;
        cursor = Math.min(sessionCursor, input.length());
    }

    @Override
    protected void init() {
        this.winW = Math.min(this.width - 24, 900);
        this.winH = Math.min(this.height - 24, 560);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    /** Persist the live input line back to the session so it survives closing the screen. */
    @Override
    public void removed() {
        sessionInput = input;
        sessionCursor = cursor;
        super.removed();
    }

    // ------------------------------------------------------------------
    // Output plumbing
    // ------------------------------------------------------------------

    private void print(String line) {
        SESSION_LINES.add(line == null ? "" : line);
        while (SESSION_LINES.size() > MAX_LINES) {
            SESSION_LINES.remove(0);
        }
        sessionVersion++;
    }

    private void printRaw(String line) {
        print(stripAnsi(line));
    }

    private static String stripAnsi(String line) {
        return line == null ? "" : ANSI.matcher(line).replaceAll("");
    }

    private List<String> visibleLines() {
        if (renderedVersion != sessionVersion) {
            synchronized (SESSION_LINES) {
                snapshot = List.copyOf(SESSION_LINES);
            }
            renderedVersion = sessionVersion;
        }
        return snapshot;
    }

    private static void clearSessionLines() {
        synchronized (SESSION_LINES) {
            SESSION_LINES.clear();
        }
        sessionVersion++;
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    public void run(String raw) {
        String cmd = raw.trim();
        if (cmd.isEmpty()) return;

        synchronized (SESSION_HISTORY) {
            if (SESSION_HISTORY.isEmpty() || !SESSION_HISTORY.get(SESSION_HISTORY.size() - 1).equals(cmd)) {
                SESSION_HISTORY.add(cmd);
            }
            sessionHistoryIndex = -1;
        }
        print("§a> §f" + cmd);

        String lower = cmd.toLowerCase(Locale.ROOT);
        if (handleBuiltIn(lower, cmd)) {
            snapToBottom = true;
            return;
        }

        if (sessionRunning) {
            print("§cA command is still running. Wait for it to finish.");
            return;
        }

        final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        final List<String> argv = windows
                ? List.of("cmd.exe", "/c", cmd)
                : List.of("/bin/sh", "-c", cmd);

        sessionRunning = true;
        Thread worker = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(argv);
                pb.redirectErrorStream(true);
                pb.directory(new File(Minecraft.getInstance().gameDirectory.getAbsolutePath()));

                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        printRaw(line);
                    }
                }
                int exit = process.waitFor();
                if (exit != 0) {
                    print("§8[exit code " + exit + "]");
                }
            } catch (Throwable t) {
                print("§c[failed] " + t.getClass().getSimpleName() + ": " + t.getMessage());
            } finally {
                sessionRunning = false;
                // Adding to the list off-thread is safe; nudging the view is not.
                Minecraft.getInstance().execute(() -> snapToBottom = true);
            }
        }, "bombo-cmd");
        worker.setDaemon(true);
        worker.start();
    }

    /** @return true when the command was fully handled and must not reach the shell. */
    private boolean handleBuiltIn(String lower, String original) {
        switch (lower) {
            case "help", "?" -> {
                print("§7Built-ins: §fhelp§7, §fclear§7, §fclose§7, §fver§7, §fflavor§7, §fecho <text>§7, §fnb on|off");
                print("§7Everything else runs in your shell: e.g. §fping 1.1.1.1§7, §fipconfig§7, §fcurl ifconfig.me§7, §ftasklist§7.");
                print("§7Up/Down recall history. Ctrl+V paste. Ctrl+L clears the session. PageUp/PageDown scroll.");
                return true;
            }
            case "clear", "cls" -> {
                clearSessionLines();
                return true;
            }
            case "close", "exit", "quit" -> {
                Minecraft.getInstance().setScreenAndShow(parent);
                return true;
            }
            case "ver", "version" -> {
                print("§7" + me.bombo.bomboaddons.Constants.identityLine()
                        + " §8| §f" + me.bombo.bomboaddons.BomboaddonsClient.getModVersion()
                        + " §8| §fminecraft §f" + me.bombo.bomboaddons.Constants.artifactPrefix());
                print("§7Java §f" + System.getProperty("java.version")
                        + " §7on §f" + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
                return true;
            }
            case "flavor" -> {
                print("§7Flavor: §f" + me.bombo.bomboaddons.Constants.FLAVOR
                        + " §8| §7mod id: §f" + me.bombo.bomboaddons.Constants.MOD_ID
                        + " §8| §7sequence runtime: §f"
                        + (me.bombo.bomboaddons.features.auto.AutoSequenceManager.hasRuntime() ? "yes" : "no"));
                return true;
            }
            case "nb on" -> {
                me.bombo.bomboaddons.BomboConfig.Settings s = me.bombo.bomboaddons.BomboConfig.get();
                s.noObfuscate = true;
                me.bombo.bomboaddons.BomboConfig.save();
                print("§aNo Obfuscate is now ON.");
                return true;
            }
            case "nb off" -> {
                me.bombo.bomboaddons.BomboConfig.Settings s = me.bombo.bomboaddons.BomboConfig.get();
                s.noObfuscate = false;
                me.bombo.bomboaddons.BomboConfig.save();
                print("§7No Obfuscate is now OFF.");
                return true;
            }
            default -> {
                if (lower.startsWith("echo ")) {
                    print("§f" + original.substring(5).trim());
                    return true;
                }
                return false;
            }
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xD0080A0E);

        int mainBg = ConfigUITheme.getMainWindowBg();
        int headerBg = ConfigUITheme.getHeaderBg();
        int borderCol = ConfigUITheme.getBorderColor();
        Font font = this.font;

        g.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x33000000);
        g.fill(winX, winY, winX + winW, winY + winH, mainBg);
        g.outline(winX, winY, winW, winH, borderCol);

        int headerH = 20;
        g.fill(winX, winY, winX + winW, winY + headerH, headerBg);
        g.text(font, "§a§l>_ §fBOMBO TERMINAL §8| §7/b cmd", winX + padX + 4, winY + 6, 0xFFFFFFFF, false);
        g.text(font, runningStatus(), winX + winW - 70, winY + 6, 0xFFFFFFFF, false);

        int inputH = 20;
        int listTop = winY + headerH + 2;
        int listBottom = winY + winH - inputH - 2;
        int rows = Math.max(1, (listBottom - listTop) / rowH);

        List<String> view = visibleLines();
        int maxScroll = Math.max(0, view.size() - rows);
        if (snapToBottom) {
            scrollOffset = maxScroll;
            snapToBottom = false;
        }
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        for (int i = 0; i < rows; i++) {
            int index = scrollOffset + i;
            if (index >= view.size()) break;
            g.text(font, font.plainSubstrByWidth(view.get(index), winW - padX * 2 - 8),
                    winX + padX, listTop + i * rowH, 0xFFE2E8F0, false);
        }

        // Scrollbar
        if (view.size() > rows) {
            int sbX = winX + winW - 6;
            int sbH = rows * rowH;
            g.fill(sbX, listTop, sbX + 3, listTop + sbH, 0x44000000);
            float ratio = (float) rows / view.size();
            int thumbH = Math.max(12, (int) (sbH * ratio));
            int thumbY = maxScroll == 0 ? listTop : listTop + (int) ((float) scrollOffset / maxScroll * (sbH - thumbH));
            g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF38BDF8);
        }

        // Input line
        int inputY = winY + winH - inputH;
        g.fill(winX, inputY, winX + winW, winY + winH, 0xFF0B1220);
        g.fill(winX, inputY, winX + winW, inputY + 1, ConfigUITheme.getDividerColor());
        g.text(font, "§a$", winX + padX, inputY + 6, 0xFF10B981, false);

        int textX = winX + padX + 12;
        String shown = input + ((System.currentTimeMillis() / 400 % 2 == 0) ? "_" : "");
        g.text(font, shown, textX, inputY + 6, 0xFFFFFFFF, false);

        // Cursor caret over the character boundary
        if (cursor >= 0 && cursor <= input.length()) {
            String before = input.substring(0, cursor);
            int caretX = textX + font.width(before);
            g.fill(caretX, inputY + 5, caretX + 1, inputY + 15, 0xFF38BDF8);
        }

        g.text(font, "§8" + SESSION_LINES.size() + " lines | " + runningStatus(),
                winX + winW - 150, winY + winH - inputH + 6, 0xFF64748B, false);
    }

    private static String runningStatus() {
        return sessionRunning ? "§e● running" : "§a● idle";
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (c >= ' ' && c != 127) {
            insert(String.valueOf(c));
            return true;
        }
        return super.charTyped(event);
    }

    private void insert(String text) {
        cursor = Math.max(0, Math.min(input.length(), cursor));
        input = input.substring(0, cursor) + text + input.substring(cursor);
        cursor += text.length();
        snapToBottom = true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (event.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                String cmd = input;
                input = "";
                cursor = 0;
                run(cmd);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (ctrl) {
                    int cut = wordStart(input, cursor);
                    input = input.substring(0, cut) + input.substring(cursor);
                    cursor = cut;
                } else if (cursor > 0) {
                    input = input.substring(0, cursor - 1) + input.substring(cursor);
                    cursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < input.length()) {
                    input = input.substring(0, cursor) + input.substring(cursor + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = ctrl ? wordStart(input, cursor) : Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = ctrl ? wordEnd(input, cursor) : Math.min(input.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = input.length();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                recallHistory(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                recallHistory(1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                snapToBottom = false;
                scrollOffset -= Math.max(1, (winH - 60) / rowH);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scrollOffset += Math.max(1, (winH - 60) / rowH);
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    input = "";
                    cursor = 0;
                    return true;
                }
                return super.keyPressed(event);
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null) {
                        insert(clip.replace("\n", " ").replace("\r", ""));
                    }
                    return true;
                }
                return super.keyPressed(event);
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(input);
                    print("§8[copied input line]");
                    return true;
                }
                return super.keyPressed(event);
            }
            case GLFW.GLFW_KEY_L -> {
                if (ctrl) {
                    // Ctrl+L clears the session buffer, exactly like a real terminal.
                    print("§8[cleared]");
                    clearSessionLines();
                    return true;
                }
                return super.keyPressed(event);
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                // Session state is static: the scrollback and any running command survive.
                Minecraft.getInstance().setScreenAndShow(parent);
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    private void recallHistory(int direction) {
        synchronized (SESSION_HISTORY) {
            if (SESSION_HISTORY.isEmpty()) return;
            if (sessionHistoryIndex == -1) {
                sessionHistoryIndex = SESSION_HISTORY.size();
            }
            sessionHistoryIndex = Math.max(0, Math.min(SESSION_HISTORY.size(), sessionHistoryIndex + direction));
            input = sessionHistoryIndex >= SESSION_HISTORY.size() ? "" : SESSION_HISTORY.get(sessionHistoryIndex);
            cursor = input.length();
        }
    }

    private static int wordStart(String text, int from) {
        int i = Math.max(0, Math.min(text.length(), from));
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) i--;
        return i;
    }

    private static int wordEnd(String text, int from) {
        int i = Math.max(0, Math.min(text.length(), from));
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            snapToBottom = false;
            scrollOffset -= (int) (verticalAmount * 3);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        // Clicking inside the buffer copies the line under the cursor - handy for long output.
        int listTop = winY + 22;
        int index = scrollOffset + (int) ((event.y() - listTop) / rowH);
        List<String> view = visibleLines();
        if (event.y() >= listTop && index >= 0 && index < view.size()
                && event.x() >= winX && event.x() <= winX + winW - 8) {
            Minecraft.getInstance().keyboardHandler.setClipboard(stripAnsi(view.get(index)));
            print("§8[copied line to clipboard]");
            return true;
        }
        return super.mouseClicked(event, handled);
    }
}
