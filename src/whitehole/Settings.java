/*
 * Copyright (C) 2022 Whitehole Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package whitehole;

import java.awt.event.KeyEvent;
import java.util.prefs.Preferences;

public final class Settings {
    private Settings() {}
    
    private static final Preferences PREFERENCES = Preferences.userRoot();
    
    // General 
    public static String getLastGameDir() { return PREFERENCES.get("whitehole_lastGameDir", null); }
    public static boolean getSJISNotSupported() { return PREFERENCES.getBoolean("whitehole_sjisNotSupported", false); }
    public static boolean getUseDarkMode() { return PREFERENCES.getBoolean("whitehole_useDarkMode", true); }
    public static boolean getUseGalaxyFileNames() { return PREFERENCES.getBoolean("whitehole_useGalaxyFileNames", false);}
    
    public static void setLastGameDir(String val) { PREFERENCES.put("whitehole_lastGameDir", val); }
    public static void setSJISNotSupported(boolean val) { PREFERENCES.putBoolean("whitehole_sjisNotSupported", val); }
    public static void setUseDarkMode(boolean val) { PREFERENCES.putBoolean("whitehole_useDarkMode", val); }
    public static void setUseGalaxyFileNames(boolean val) { PREFERENCES.putBoolean("whitehole_useGalaxyFileNames", val); }
    
    // Rendering
    public static boolean getShowAxis() { return PREFERENCES.getBoolean("whitehole_showAxis", true); }
    public static boolean getShowAreas() { return PREFERENCES.getBoolean("whitehole_showAreas", true); }
    public static boolean getShowCameras() { return PREFERENCES.getBoolean("whitehole_showCameras", true); }
    public static boolean getShowGravity() { return PREFERENCES.getBoolean("whitehole_showGravity", true); }
    public static boolean getShowPaths() { return PREFERENCES.getBoolean("whitehole_showPaths", true); }
    public static boolean getDebugFakeColor() { return PREFERENCES.getBoolean("whitehole_debugFakeColor", false); }
    public static boolean getDebugFastDrag() { return PREFERENCES.getBoolean("whitehole_debugFastDrag", false); }
    
    public static void setShowAxis(boolean val) { PREFERENCES.putBoolean("whitehole_showAxis", val); }
    public static void setShowAreas(boolean val) { PREFERENCES.putBoolean("whitehole_showAreas", val); }
    public static void setShowCameras(boolean val) { PREFERENCES.putBoolean("whitehole_showCameras", val); }
    public static void setShowGravity(boolean val) { PREFERENCES.putBoolean("whitehole_showGravity", val); }
    public static void setShowPaths(boolean val) { PREFERENCES.putBoolean("whitehole_showPaths", val); }
    public static void setDebugFakeColor(boolean val) { PREFERENCES.putBoolean("whitehole_debugFakeColor", val); }
    public static void setDebugFastDrag(boolean val) { PREFERENCES.putBoolean("whitehole_debugFastDrag", val); }
    
    // Controls
    public static boolean getUseReverseRot() { return PREFERENCES.getBoolean("whitehole_useReverseRot", false); }
    public static boolean getUseWASD() { return PREFERENCES.getBoolean("whitehole_useWASD", false); }
    public static int getKeyPosition() { return PREFERENCES.getInt("whitehole_keyPosition", KeyEvent.VK_G); }
    public static int getKeyRotation() { return PREFERENCES.getInt("whitehole_keyRotation", KeyEvent.VK_R); }
    public static int getKeyScale() { return PREFERENCES.getInt("whitehole_keyScale", KeyEvent.VK_S); }
    
    public static void setUseReverseRot(boolean val) { PREFERENCES.putBoolean("whitehole_useReverseRot", val); }
    public static void setUseWASD(boolean val) { PREFERENCES.putBoolean("whitehole_useWASD", val); }
    public static void setKeyPosition(int val) { PREFERENCES.putInt("whitehole_keyPosition", val); }
    public static void setKeyRotation(int val) { PREFERENCES.putInt("whitehole_keyRotation", val); }
    public static void setKeyScale(int val) { PREFERENCES.putInt("whitehole_keyScale", val); }
}
