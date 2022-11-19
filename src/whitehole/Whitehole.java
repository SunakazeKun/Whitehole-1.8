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

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import javax.swing.*;
import whitehole.db.FieldHashes;
import whitehole.db.GalaxyNames;
import whitehole.db.ModelSubstitutions;
import whitehole.db.ObjectDB;
import whitehole.io.FilesystemBase;
import whitehole.smg.GameArchive;

public class Whitehole {
    public static final String NAME = "Whitehole Despaghettification";
    public static final String WEB_URL = "https://discord.gg/k7ZKzSDsVq";
    public static final Image ICON = Toolkit.getDefaultToolkit().createImage(Whitehole.class.getResource("/res/icon.png"));
    
    private static MainFrame MAIN_FRAME;
    private static FlatDarkLaf DARK_THEME;
    private static FlatLightLaf LIGHT_THEME;
    
    public static void main(String[] args) throws IOException {
        // Setup look and feel and set if applicable
        FlatDarkLaf.setup();
        FlatLightLaf.setup();
        DARK_THEME = new FlatDarkLaf();
        LIGHT_THEME = new FlatLightLaf();
        
        try {
            UIManager.setLookAndFeel(Settings.getUseDarkMode() ? DARK_THEME : LIGHT_THEME);
        }
        catch (Exception ex) {
            System.err.println(ex);
        }
        
        // Check if system supports SJIS or not
        if (!Settings.getSJISNotSupported() && !Charset.isSupported("SJIS")) {
            Settings.setSJISNotSupported(true);
            
            JOptionPane.showMessageDialog(null, "Shift-JIS encoding isn't supported.\n"
                    + "Whitehole will default to LATIN1, which may cause certain strings to look corrupted.\n\n"
                    + "This message appears only once.", Whitehole.NAME, JOptionPane.WARNING_MESSAGE
            );
        }
        
        // Initialize data
        FieldHashes.init();
        GalaxyNames.init();
        ObjectDB.init(true);
        ModelSubstitutions.init();
        
        MAIN_FRAME = new MainFrame();
        MAIN_FRAME.setVisible(true);
    }
    
    public static void requestUpdateLAF() {
        LookAndFeel next = null;
        
        if (Settings.getUseDarkMode()) {
            if (UIManager.getLookAndFeel() != DARK_THEME) {
                next = DARK_THEME;
            }
        }
        else {
            if (UIManager.getLookAndFeel() != LIGHT_THEME) {
                next = LIGHT_THEME;
            }
        }
        
        if (next != null) {
            try {
                UIManager.setLookAndFeel(next);
            }
            catch(Exception ex) {
                System.err.println(ex);
            }
            
            MAIN_FRAME.requestUpdateLAF();
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Game Util
    
    public static GameArchive GAME;
    
    public static int getCurrentGameType() {
        return GAME != null ? GAME.getGameType() : 0;
    }
    
    public static boolean isGalaxyLevelEngine() {
        return GAME != null ? GAME.existsGLE() : false;
    }
    
    public static boolean doesArchiveExist(String archivePath) {
        return GAME != null ? GAME.existsArchive(archivePath) : false;
    }
    
    public static FilesystemBase getCurrentGameFileSystem() {
        return GAME != null ? GAME.getFileSystem() : null;
    }
    
    public static List<String> getGalaxyList() {
        return GAME != null ? GAME.getGalaxyList() : null;
    }
    
    public static List<String> getWaterPlanetList() {
        return GAME != null ? GAME.getWaterPlanetList() : null;
    }
    
    public static boolean isExistObjectDataArc(String file) {
        return GAME != null ? GAME.existsResourceArcPath(file) : false;
    }
    
    public static String createResourceArcPath(String objModelName) {
        return GAME != null ? GAME.createResourceArcPath(objModelName) : null;
    }
}
