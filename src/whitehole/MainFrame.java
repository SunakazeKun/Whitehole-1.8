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

import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import whitehole.db.GalaxyNames;
import whitehole.db.ObjectDB;
import whitehole.editor.BcsvEditorForm;
import whitehole.editor.GalaxyEditorForm;
import whitehole.editor.ObjectSelectForm;
import whitehole.io.ExternalFilesystem;
import whitehole.rendering.RendererCache;
import whitehole.rendering.ShaderCache;
import whitehole.rendering.TextureCache;
import whitehole.smg.GameArchive;

public final class MainFrame extends javax.swing.JFrame {
    private static class GalaxyListItem {
        final String identifier;
        
        GalaxyListItem(String id) {
            identifier = id;
        }
        
        @Override
        public String toString() {
            return GalaxyNames.getSimplifiedStageName(identifier);
        }
    }
    
    private static final Comparator<GalaxyListItem> ITEM_COMPARATOR = (i, j) -> i.toString().compareTo(j.toString());
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    private final DefaultListModel<GalaxyListItem> galaxyItems;
    private String currentGalaxy = null;
    private GalaxyEditorForm galaxyEditor = null;
    private final BcsvEditorForm bcsvEditor;
    private final AboutForm aboutDialog;
    private final SettingsForm settingsDialog;
    
    public MainFrame() {
        initComponents();
        galaxyItems = (DefaultListModel)(listGalaxy.getModel());
        
        bcsvEditor = new BcsvEditorForm();
        aboutDialog = new AboutForm(this);
        settingsDialog = new SettingsForm(this);
    }
    
    private void openGameDir(String gameDir) {
        btnOpenGalaxy.setEnabled(false);
        btnBcsvEditor.setEnabled(false);
        
        // Reload databases if previous selected game overwrote them
        if (Whitehole.GAME != null) {
            if (Whitehole.GAME.hasOverwriteObjectDatabase()) {
                ObjectDB.init(false);
            }
            
            if (Whitehole.GAME.hasOverwriteGalaxyNames()) {
                GalaxyNames.clearProjectDatabase();
            }
        }
        
        // Load game system and store last selected game directory
        try {
            Whitehole.GAME = new GameArchive(new ExternalFilesystem(gameDir));
        }
        catch (IOException ex) {
            System.err.println("Failed to open game directory!");
            System.err.println(ex);
            lbStatusBar.setText("Failed to open saved game directory. See console output for details.");
            return;
        }
        
        Settings.setLastGameDir(gameDir);
        
        // Construct list of galaxy items
        galaxyItems.removeAllElements();
        
        if (Whitehole.getCurrentGameType() == 0) {
            lbStatusBar.setText("Selected directory isn't an SMG1/2 workspace.");
            return;
        }
        
        List<String> galaxies = Whitehole.GAME.getGalaxyList();
        List<GalaxyListItem> listItems = new ArrayList(galaxies.size());
        
        for (String galaxy : galaxies) {
            listItems.add(new GalaxyListItem(galaxy));
        }
        
        listItems.sort(ITEM_COMPARATOR);
        
        for (GalaxyListItem item : listItems) {
            galaxyItems.addElement(item);
        }
        
        btnBcsvEditor.setEnabled(true);
        lbStatusBar.setText("Successfully opened the game directory!");
    }
    
    public void openGalaxy() {
        GalaxyListItem galaxy = (GalaxyListItem)listGalaxy.getSelectedValue();
        
        // Don't open new editor if one is already active
        if (galaxyEditor != null && galaxyEditor.isVisible()) {
            if (galaxy.identifier.equals(currentGalaxy)) {
                galaxyEditor.toFront();
            }
            
            return;
        }
        
        // Prepare caches
        TextureCache.init();
        ShaderCache.init();
        RendererCache.init();
        
        currentGalaxy = galaxy.identifier;
        galaxyEditor = new GalaxyEditorForm(currentGalaxy);
        galaxyEditor.setVisible(true);
    }
    
    public void requestUpdateLAF() {
        SwingUtilities.updateComponentTreeUI(this);
        
        if (galaxyEditor != null) {
            galaxyEditor.requestUpdateLAF();
        }
        
        SwingUtilities.updateComponentTreeUI(bcsvEditor);
        SwingUtilities.updateComponentTreeUI(aboutDialog);
        SwingUtilities.updateComponentTreeUI(settingsDialog);
        
        ObjectSelectForm.requestUpdateLAF();
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        toolbar = new javax.swing.JToolBar();
        btnOpenGame = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        btnOpenGalaxy = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JToolBar.Separator();
        btnBcsvEditor = new javax.swing.JButton();
        jSeparator3 = new javax.swing.JToolBar.Separator();
        btnSettings = new javax.swing.JButton();
        jSeparator6 = new javax.swing.JToolBar.Separator();
        btnAbout = new javax.swing.JButton();
        lbStatusBar = new javax.swing.JLabel();
        scrGalaxy = new javax.swing.JScrollPane();
        listGalaxy = new javax.swing.JList();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle(Whitehole.NAME);
        setIconImage(Whitehole.ICON);
        setMinimumSize(new java.awt.Dimension(636, 544));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        toolbar.setFloatable(false);
        toolbar.setRollover(true);

        btnOpenGame.setText("Select Game Folder");
        btnOpenGame.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnOpenGame.setFocusable(false);
        btnOpenGame.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnOpenGame.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnOpenGame.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOpenGameActionPerformed(evt);
            }
        });
        toolbar.add(btnOpenGame);
        toolbar.add(jSeparator1);

        btnOpenGalaxy.setText("Open Galaxy");
        btnOpenGalaxy.setEnabled(false);
        btnOpenGalaxy.setFocusable(false);
        btnOpenGalaxy.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnOpenGalaxy.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnOpenGalaxy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOpenGalaxyActionPerformed(evt);
            }
        });
        toolbar.add(btnOpenGalaxy);
        toolbar.add(jSeparator2);

        btnBcsvEditor.setText("BCSV Editor");
        btnBcsvEditor.setEnabled(false);
        btnBcsvEditor.setFocusable(false);
        btnBcsvEditor.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnBcsvEditor.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnBcsvEditor.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBcsvEditorActionPerformed(evt);
            }
        });
        toolbar.add(btnBcsvEditor);
        toolbar.add(jSeparator3);

        btnSettings.setText("Settings");
        btnSettings.setFocusable(false);
        btnSettings.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnSettings.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSettingsActionPerformed(evt);
            }
        });
        toolbar.add(btnSettings);
        toolbar.add(jSeparator6);

        btnAbout.setText("About");
        btnAbout.setFocusable(false);
        btnAbout.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnAbout.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAboutActionPerformed(evt);
            }
        });
        toolbar.add(btnAbout);

        lbStatusBar.setText("Ready!");
        lbStatusBar.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        listGalaxy.setModel(new DefaultListModel());
        listGalaxy.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listGalaxyMouseClicked(evt);
            }
        });
        listGalaxy.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                listGalaxyKeyReleased(evt);
            }
        });
        listGalaxy.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                listGalaxyValueChanged(evt);
            }
        });
        scrGalaxy.setViewportView(listGalaxy);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(toolbar, javax.swing.GroupLayout.DEFAULT_SIZE, 600, Short.MAX_VALUE)
            .addComponent(lbStatusBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(scrGalaxy)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(toolbar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrGalaxy, javax.swing.GroupLayout.DEFAULT_SIZE, 470, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbStatusBar, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents
    
    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        String lastGameDir = Settings.getLastGameDir();
        
        if (lastGameDir != null) {
            openGameDir(lastGameDir);
        }
        
        lbStatusBar.setText("Started!");
    }//GEN-LAST:event_formWindowOpened
	
    private void btnOpenGameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpenGameActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open a SMG1/2 Directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        String lastGameDir = Settings.getLastGameDir();
        if (lastGameDir != null) {
            fc.setSelectedFile(new File(lastGameDir));
        }
        
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openGameDir(fc.getSelectedFile().getPath());
        }
    }//GEN-LAST:event_btnOpenGameActionPerformed

    private void btnOpenGalaxyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpenGalaxyActionPerformed
        openGalaxy();
    }//GEN-LAST:event_btnOpenGalaxyActionPerformed

    private void btnBcsvEditorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBcsvEditorActionPerformed
        if (!bcsvEditor.isVisible()) {
            bcsvEditor.setVisible(true);
        }
    }//GEN-LAST:event_btnBcsvEditorActionPerformed

    private void btnSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSettingsActionPerformed
        if (!settingsDialog.isVisible()) {
            settingsDialog.setVisible(true);
        }
    }//GEN-LAST:event_btnSettingsActionPerformed

    private void btnAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAboutActionPerformed
        if (!aboutDialog.isVisible()) {
            aboutDialog.setVisible(true);
        }
    }//GEN-LAST:event_btnAboutActionPerformed

    private void listGalaxyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listGalaxyMouseClicked
        if (evt.getClickCount() > 1) {
            openGalaxy();
        }
    }//GEN-LAST:event_listGalaxyMouseClicked

    private void listGalaxyValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_listGalaxyValueChanged
        btnOpenGalaxy.setEnabled(listGalaxy.getSelectedIndex() >= 0);
    }//GEN-LAST:event_listGalaxyValueChanged

    private void listGalaxyKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_listGalaxyKeyReleased
        if (listGalaxy.getSelectedIndex() == -1) {
            return;
        }
        
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            openGalaxy();
        }
    }//GEN-LAST:event_listGalaxyKeyReleased
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAbout;
    private javax.swing.JButton btnBcsvEditor;
    private javax.swing.JButton btnOpenGalaxy;
    private javax.swing.JButton btnOpenGame;
    private javax.swing.JButton btnSettings;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JToolBar.Separator jSeparator2;
    private javax.swing.JToolBar.Separator jSeparator3;
    private javax.swing.JToolBar.Separator jSeparator6;
    private javax.swing.JLabel lbStatusBar;
    private javax.swing.JList listGalaxy;
    private javax.swing.JScrollPane scrGalaxy;
    private javax.swing.JToolBar toolbar;
    // End of variables declaration//GEN-END:variables
}
