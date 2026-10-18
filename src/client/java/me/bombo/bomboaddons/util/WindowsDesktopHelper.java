package me.bombo.bomboaddons.util;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import me.bombo.bomboaddons.Bomboaddons;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.Locale;

public class WindowsDesktopHelper {
    public interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class, W32APIOptions.DEFAULT_OPTIONS);
        HANDLE OpenDesktop(String lpszDesktop, int dwFlags, boolean fInherit, int dwDesiredAccess);
        boolean SetThreadDesktop(HANDLE hDesktop);
        boolean CloseDesktop(HANDLE hDesktop);
    }

    private static boolean registered = false;

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static void ensureDefaultDesktop() {
        if (!isWindows()) return;
        try {
            HANDLE hDesk = User32Ext.INSTANCE.OpenDesktop("Default", 0, false, 0x01FF);
            if (hDesk != null) {
                boolean ok = User32Ext.INSTANCE.SetThreadDesktop(hDesk);
                Bomboaddons.LOGGER.info("[BomboAddons] Desktop attachment to 'Default': {}", ok);
            }
        } catch (Throwable t) {
            Bomboaddons.LOGGER.debug("[BomboAddons] ensureDefaultDesktop skipped: {}", t.getMessage());
        }
    }

    public static void registerWindow(long glfwHandle) {
        if (!isWindows() || glfwHandle == 0L) return;
        try {
            long win32Hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
            if (win32Hwnd == 0L) return;

            HWND hwndObj = new HWND(new Pointer(win32Hwnd));
            User32 u32 = User32.INSTANCE;

            // Ensure WS_EX_APPWINDOW is set and WS_EX_TOOLWINDOW is cleared
            int exStyle = u32.GetWindowLong(hwndObj, -20); // GWL_EXSTYLE
            int newExStyle = (exStyle | 0x00040000) & ~0x00000080;
            if (newExStyle != exStyle) {
                u32.SetWindowLong(hwndObj, -20, newExStyle);
                u32.SetWindowPos(hwndObj, null, 0, 0, 0, 0, 0x0027); // SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER
            }

            // Force window visibility and bring to top
            u32.ShowWindow(hwndObj, 9); // SW_RESTORE
            u32.ShowWindow(hwndObj, 5); // SW_SHOW
            u32.SetForegroundWindow(hwndObj);

            // Also call GLFW show and focus
            GLFW.glfwShowWindow(glfwHandle);
            GLFW.glfwFocusWindow(glfwHandle);

            if (!registered) {
                registered = true;
                Bomboaddons.LOGGER.info("[BomboAddons] Successfully registered Minecraft window (0x{}) on Default desktop & taskbar", Long.toHexString(win32Hwnd));
            }
        } catch (Throwable t) {
            Bomboaddons.LOGGER.debug("[BomboAddons] registerWindow skipped: {}", t.getMessage());
        }
    }
}
