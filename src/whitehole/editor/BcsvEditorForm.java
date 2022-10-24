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
package whitehole.editor;

import java.io.IOException;
import java.util.*;
import javax.swing.JOptionPane;
import javax.swing.table.*;
import whitehole.Whitehole;
import whitehole.io.FilesystemBase;
import whitehole.io.RarcFile;
import whitehole.smg.Bcsv;
import whitehole.util.TableColumnAdjuster;

public class BcsvEditorForm extends javax.swing.JFrame {
    private final DefaultTableModel tableModel;
    private final TableColumnAdjuster adjuster;
    private FilesystemBase archive = null;
    private Bcsv bcsv = null;
    
    public BcsvEditorForm() {
        initComponents();
        tableModel = (DefaultTableModel)tblBcsv.getModel();
        adjuster = new TableColumnAdjuster(tblBcsv);
        tblBcsv.setAutoCreateRowSorter(true);
    }
    
    private void toggleShortcutVisibility() {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);
        
        boolean isSmg1 = Whitehole.getCurrentGameType() == 1;
        boolean hasGLE = Whitehole.isGalaxyLevelEngine();
        
        sep1.setVisible(!isSmg1);
        sep2.setVisible(!isSmg1);
        sep3.setVisible(hasGLE);
        mnuUseResource.setVisible(!isSmg1);
        mnuAudio.setVisible(!isSmg1);
        mnuSystem.setVisible(!isSmg1);
        subGalaxyInfo.setVisible(!isSmg1);
        subProductMapObjData.setVisible(!isSmg1);
        subWorldMapCamera.setVisible(!isSmg1);
        subKinopioBank.setVisible(!isSmg1);
        subPeach.setVisible(!isSmg1);
        subTicoFatCoin.setVisible(!isSmg1);
        subTicoFatStarPiece.setVisible(!isSmg1);
        subTicoShopDice.setVisible(!isSmg1);
        mniObjectInfluenceTable.setVisible(!isSmg1);
        mniRushInfluenceTable.setVisible(!isSmg1);
        mniMorphConditionTable.setVisible(!isSmg1);
        subAstroNamePlateData.setVisible(isSmg1);
        subChangeSceneListInfo.setVisible(!isSmg1);
        subScenarioSettings.setVisible(hasGLE);
        subStageInfo.setVisible(hasGLE);
        subMarioFaceShipEventCastTable.setVisible(!hasGLE);
        subWorldMapHeapGalaxy.setVisible(!hasGLE);
        subWorldMapHeapResource.setVisible(!hasGLE);
        subGalaxyDataTable.setVisible(!hasGLE);
        subSavePointList.setVisible(hasGLE);
        subHubworldStarReturnDataTable.setVisible(hasGLE);
        subGameSettings.setVisible(hasGLE);
        subGameEventValueTable.setVisible(hasGLE);
        
        if (isSmg1) {
            tbArchiveName.setText("/StageData/CocoonExGalaxy/CocoonExGalaxyScenario.arc");
            tbFileName.setText("/CocoonExGalaxyScenario/ScenarioData.bcsv");
        }
        else {
            tbArchiveName.setText("/StageData/RedBlueExGalaxy/RedBlueExGalaxyScenario.arc");
            tbFileName.setText("/RedBlueExGalaxyScenario/ScenarioData.bcsv");
        }
        if (hasGLE) {
            subMarioFaceShipEventDataTable.setText("HubworldEventDataTable");
            subGalaxyWorldOrderList.setText("GalaxyOrderList");
        } else {
            subMarioFaceShipEventDataTable.setText("MarioFaceShipEventDataTable");
            subGalaxyWorldOrderList.setText("GalaxyWorldOrderList");
        }
    }
    
    @Override
    public void setVisible(boolean state) {
        super.setVisible(state);
        toggleShortcutVisibility();
    }
    
    private String getTextInput(String text) {
        return (String)JOptionPane.showInputDialog(this, text, Whitehole.NAME, JOptionPane.PLAIN_MESSAGE, null, null, null);
    }
    
    private void handleShortcut(String archiveName, String bcsvName) {
        tbArchiveName.setText(archiveName);
        tbFileName.setText(bcsvName);
        populateBcsvData();
    }
    
    private void handleUseResourceShortcut(String bcsvName) {
        String stage = getTextInput("Enter the stage's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName = String.format("/StageData/%s/%sUseResource.arc", stage, stage);
            bcsvName = String.format("/Stage/csv/%s", bcsvName);
            handleShortcut(archiveName, bcsvName);
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Reading & writing
    
    private void populateBcsvData() {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);
        closeIO();
        
        try {
            archive = new RarcFile(Whitehole.getCurrentGameFileSystem().openFile(tbArchiveName.getText()));
            bcsv = new Bcsv(archive.openFile(tbFileName.getText()));
            
            // Add columns
            for (Bcsv.Field field : bcsv.fields.values()) {
                tableModel.addColumn(field.name);
            }
            
            // Add rows
            for (Bcsv.Entry entry : bcsv.entries) {
                ArrayList<Object> row = new ArrayList(bcsv.fields.size());
                
                for (Bcsv.Field field : bcsv.fields.values()) {
                    row.add(entry.get(field.hash));
                }
                
                tableModel.addRow(row.toArray());
            }
        }
        catch(IOException ex) {
            String errmsg = String.format("Can't open BCSV file: %s", ex.getMessage());
            JOptionPane.showMessageDialog(this, errmsg, Whitehole.NAME, JOptionPane.ERROR_MESSAGE);
            closeIO();
        }
        
        adjuster.adjustColumns();
    }
    
    private void storeBcsv() {
        bcsv.entries.clear();
        
        for (int r = 0; r < tblBcsv.getRowCount(); r++) {
            Bcsv.Entry entry = new Bcsv.Entry();
            
            int c = 0;
            for (Bcsv.Field field : bcsv.fields.values()) {
                Object valobj = tblBcsv.getValueAt(r, c);
                String val = (valobj == null) ? "" : valobj.toString();
                
                try {
                    switch(field.type) {
                        case 0:
                        case 3: entry.put(field.hash, Integer.parseInt(val)); break;
                        case 1:
                        case 6: entry.put(field.hash, val); break;
                        case 2: entry.put(field.hash, Float.parseFloat(val)); break;
                        case 4: entry.put(field.hash, Short.parseShort(val)); break;
                        case 5: entry.put(field.hash, Byte.parseByte(val)); break;
                    }
                }
                catch(NumberFormatException ex) {
                    switch (field.type) {
                        case 0:
                        case 3: entry.put(field.hash, -1); break;
                        case 1:
                        case 6: entry.put(field.hash, ""); break;
                        case 2: entry.put(field.hash, 0f); break;
                        case 4: entry.put(field.hash, (short)-1); break;
                        case 5: entry.put(field.hash, (byte)-1); break;
                    }
                }
                c++;
            }
            bcsv.entries.add(entry);
        }
        try { 
            bcsv.save();
            archive.save();
        }
        catch (IOException ex) {
            System.out.println(ex);
        }
    }
    
    private void closeIO() {
        try {
            if (bcsv != null) {
                bcsv.close();
            }
            if (archive != null) {
                archive.close();
            }

            archive = null;
            bcsv = null;
        }
        catch(IOException ex) {
            System.err.println(ex);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content
     * of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        toolbarPaths = new javax.swing.JToolBar();
        lblArchive = new javax.swing.JLabel();
        tbArchiveName = new javax.swing.JTextField();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(30, 0), new java.awt.Dimension(30, 0), new java.awt.Dimension(30, 0));
        lblFile = new javax.swing.JLabel();
        tbFileName = new javax.swing.JTextField();
        toolbarButtons = new javax.swing.JToolBar();
        btnOpen = new javax.swing.JButton();
        spr2 = new javax.swing.JToolBar.Separator();
        btnSave = new javax.swing.JButton();
        spr3 = new javax.swing.JToolBar.Separator();
        btnAddRow = new javax.swing.JButton();
        spr5 = new javax.swing.JToolBar.Separator();
        btnDuplicateRow = new javax.swing.JButton();
        spr6 = new javax.swing.JToolBar.Separator();
        btnDeleteRow = new javax.swing.JButton();
        spr7 = new javax.swing.JToolBar.Separator();
        btnClear = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblBcsv = new javax.swing.JTable();
        menubar = new javax.swing.JMenuBar();
        mnuFile = new javax.swing.JMenu();
        subOpen = new javax.swing.JMenuItem();
        subSave = new javax.swing.JMenuItem();
        subClose = new javax.swing.JMenuItem();
        mnuOpen = new javax.swing.JMenu();
        mnuStages = new javax.swing.JMenu();
        subScenarioData = new javax.swing.JMenuItem();
        subZoneList = new javax.swing.JMenuItem();
        subGalaxyInfo = new javax.swing.JMenuItem();
        sep1 = new javax.swing.JPopupMenu.Separator();
        subCameraParam = new javax.swing.JMenuItem();
        subLightDataZone = new javax.swing.JMenuItem();
        subLightData = new javax.swing.JMenuItem();
        subChangeSceneListInfo = new javax.swing.JMenuItem();
        sep2 = new javax.swing.JPopupMenu.Separator();
        mnuUseResource = new javax.swing.JMenu();
        subURcommon = new javax.swing.JMenuItem();
        subUR1 = new javax.swing.JMenuItem();
        subUR2 = new javax.swing.JMenuItem();
        subUR3 = new javax.swing.JMenuItem();
        subURarcCommon = new javax.swing.JMenuItem();
        subURarc1 = new javax.swing.JMenuItem();
        subURarc2 = new javax.swing.JMenuItem();
        subURarc3 = new javax.swing.JMenuItem();
        subURsoundCommon = new javax.swing.JMenuItem();
        subURsound1 = new javax.swing.JMenuItem();
        subURsound2 = new javax.swing.JMenuItem();
        subURsound3 = new javax.swing.JMenuItem();
        sep3 = new javax.swing.JPopupMenu.Separator();
        subScenarioSettings = new javax.swing.JMenuItem();
        subStageInfo = new javax.swing.JMenuItem();
        mnuObjects = new javax.swing.JMenu();
        subPlanetMapData = new javax.swing.JMenuItem();
        subProductMapObjData = new javax.swing.JMenuItem();
        subObjName = new javax.swing.JMenuItem();
        mnuNPCData = new javax.swing.JMenu();
        subCaretaker = new javax.swing.JMenuItem();
        subHoneyBee = new javax.swing.JMenuItem();
        subKinopio = new javax.swing.JMenuItem();
        subKinopioBank = new javax.swing.JMenuItem();
        subPeach = new javax.swing.JMenuItem();
        subPenguinRacer = new javax.swing.JMenuItem();
        subPenguinRacerLeader = new javax.swing.JMenuItem();
        subTicoComet = new javax.swing.JMenuItem();
        subTicoGalaxy = new javax.swing.JMenuItem();
        subTicoFat = new javax.swing.JMenuItem();
        subTicoFatStarPiece = new javax.swing.JMenuItem();
        subTicoFatCoin = new javax.swing.JMenuItem();
        subTicoShop = new javax.swing.JMenuItem();
        subTicoShopDice = new javax.swing.JMenuItem();
        sep4 = new javax.swing.JPopupMenu.Separator();
        mniObjectInfluenceTable = new javax.swing.JMenuItem();
        mniRushInfluenceTable = new javax.swing.JMenuItem();
        mniMorphConditionTable = new javax.swing.JMenuItem();
        subAstroNamePlateData = new javax.swing.JMenuItem();
        subWorldMapCamera = new javax.swing.JMenuItem();
        mnuSystem = new javax.swing.JMenu();
        subHeapSizeExcept = new javax.swing.JMenuItem();
        subMarioFaceShipEventDataTable = new javax.swing.JMenuItem();
        subGalaxyDataTable = new javax.swing.JMenuItem();
        subGalaxyWorldOrderList = new javax.swing.JMenuItem();
        subMarioFaceShipEventCastTable = new javax.swing.JMenuItem();
        subWorldMapHeapGalaxy = new javax.swing.JMenuItem();
        subWorldMapHeapResource = new javax.swing.JMenuItem();
        subGameEventValueTable = new javax.swing.JMenuItem();
        subGameSettings = new javax.swing.JMenuItem();
        subHubworldStarReturnDataTable = new javax.swing.JMenuItem();
        subSavePointList = new javax.swing.JMenuItem();
        mnuAudio = new javax.swing.JMenu();
        subStageBgmInfo = new javax.swing.JMenuItem();
        subScenarioBgmInfo = new javax.swing.JMenuItem();
        subMultiBgmInfo = new javax.swing.JMenuItem();
        subBgmParam = new javax.swing.JMenuItem();
        subActionSound = new javax.swing.JMenuItem();
        subSoundIdToInstList = new javax.swing.JMenuItem();
        mnuEffects = new javax.swing.JMenu();
        subParticleNames = new javax.swing.JMenuItem();
        subAutoEffectList = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(String.format("%s -- BCSV Editor", Whitehole.NAME));
        setIconImage(Whitehole.ICON);
        setSize(new java.awt.Dimension(0, 0));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        toolbarPaths.setBorder(null);
        toolbarPaths.setFloatable(false);
        toolbarPaths.setRollover(true);

        lblArchive.setText(" Archive: ");
        toolbarPaths.add(lblArchive);

        tbArchiveName.setToolTipText("");
        toolbarPaths.add(tbArchiveName);
        toolbarPaths.add(filler1);

        lblFile.setText("File: ");
        toolbarPaths.add(lblFile);
        toolbarPaths.add(tbFileName);

        toolbarButtons.setFloatable(false);
        toolbarButtons.setRollover(true);
        toolbarButtons.setAlignmentY(0.5F);
        toolbarButtons.setInheritsPopupMenu(true);

        btnOpen.setText("Open");
        btnOpen.setFocusable(false);
        btnOpen.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnOpen.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOpenActionPerformed(evt);
            }
        });
        toolbarButtons.add(btnOpen);
        toolbarButtons.add(spr2);

        btnSave.setText("Save");
        btnSave.setFocusable(false);
        btnSave.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnSave.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });
        toolbarButtons.add(btnSave);
        toolbarButtons.add(spr3);

        btnAddRow.setText("Add Row");
        btnAddRow.setFocusable(false);
        btnAddRow.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnAddRow.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnAddRow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddRowActionPerformed(evt);
            }
        });
        toolbarButtons.add(btnAddRow);
        toolbarButtons.add(spr5);

        btnDuplicateRow.setText("Duplicate row(s)");
        btnDuplicateRow.setToolTipText("");
        btnDuplicateRow.setFocusable(false);
        btnDuplicateRow.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnDuplicateRow.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnDuplicateRow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDuplicateRowActionPerformed(evt);
            }
        });
        toolbarButtons.add(btnDuplicateRow);
        toolbarButtons.add(spr6);

        btnDeleteRow.setText("Delete row(s)");
        btnDeleteRow.setFocusable(false);
        btnDeleteRow.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnDeleteRow.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnDeleteRow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteRowActionPerformed(evt);
            }
        });
        toolbarButtons.add(btnDeleteRow);
        toolbarButtons.add(spr7);

        btnClear.setText("Clear all");
        btnClear.setFocusable(false);
        btnClear.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnClear.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnClear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnClearActionPerformed(evt);
            }
        });
        toolbarButtons.add(btnClear);

        tblBcsv.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        tblBcsv.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        jScrollPane1.setViewportView(tblBcsv);

        menubar.setBorder(null);

        mnuFile.setText("File");

        subOpen.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        subOpen.setText("Open");
        subOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subOpenActionPerformed(evt);
            }
        });
        mnuFile.add(subOpen);

        subSave.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        subSave.setText("Save");
        subSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subSaveActionPerformed(evt);
            }
        });
        mnuFile.add(subSave);

        subClose.setText("Close");
        subClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subCloseActionPerformed(evt);
            }
        });
        mnuFile.add(subClose);

        menubar.add(mnuFile);

        mnuOpen.setBorder(null);
        mnuOpen.setText("Open");

        mnuStages.setText("Stages");
        mnuStages.setToolTipText("");

        subScenarioData.setText("ScenarioData");
        subScenarioData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subScenarioDataActionPerformed(evt);
            }
        });
        mnuStages.add(subScenarioData);

        subZoneList.setText("ZoneList");
        subZoneList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subZoneListActionPerformed(evt);
            }
        });
        mnuStages.add(subZoneList);

        subGalaxyInfo.setText("GalaxyInfo");
        subGalaxyInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subGalaxyInfoActionPerformed(evt);
            }
        });
        mnuStages.add(subGalaxyInfo);
        mnuStages.add(sep1);

        subCameraParam.setText("Camera");
        subCameraParam.setToolTipText("");
        subCameraParam.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subCameraParamActionPerformed(evt);
            }
        });
        mnuStages.add(subCameraParam);

        subLightDataZone.setText("Light");
        subLightDataZone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subLightDataZoneActionPerformed(evt);
            }
        });
        mnuStages.add(subLightDataZone);

        subLightData.setText("LightData");
        subLightData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subLightDataActionPerformed(evt);
            }
        });
        mnuStages.add(subLightData);

        subChangeSceneListInfo.setText("ChangeSceneListInfo");
        subChangeSceneListInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subChangeSceneListInfoActionPerformed(evt);
            }
        });
        mnuStages.add(subChangeSceneListInfo);
        mnuStages.add(sep2);

        mnuUseResource.setText("UseResource");

        subURcommon.setText("common");
        subURcommon.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURcommonActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURcommon);

        subUR1.setText("scenario_1");
        subUR1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subUR1ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subUR1);

        subUR2.setText("scenario_2");
        subUR2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subUR2ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subUR2);

        subUR3.setText("scenario_3");
        subUR3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subUR3ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subUR3);

        subURarcCommon.setText("wave_arc_common");
        subURarcCommon.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURarcCommonActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURarcCommon);

        subURarc1.setText("wave_arc_scenario_1");
        subURarc1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURarc1ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURarc1);

        subURarc2.setText("wave_arc_scenario_2");
        subURarc2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURarc2ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURarc2);

        subURarc3.setText("wave_arc_scenario_3");
        subURarc3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURarc3ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURarc3);

        subURsoundCommon.setText("sound_common");
        subURsoundCommon.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURsoundCommonActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURsoundCommon);

        subURsound1.setText("sound_scenario_1");
        subURsound1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURsound1ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURsound1);

        subURsound2.setText("sound_scenario_2");
        subURsound2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURsound2ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURsound2);

        subURsound3.setText("sound_scenario_3");
        subURsound3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subURsound3ActionPerformed(evt);
            }
        });
        mnuUseResource.add(subURsound3);

        mnuStages.add(mnuUseResource);
        mnuStages.add(sep3);

        subScenarioSettings.setText("ScenarioSettings");
        subScenarioSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subScenarioSettingsActionPerformed(evt);
            }
        });
        mnuStages.add(subScenarioSettings);

        subStageInfo.setText("StageInfo");
        subStageInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subStageInfoActionPerformed(evt);
            }
        });
        mnuStages.add(subStageInfo);

        mnuOpen.add(mnuStages);

        mnuObjects.setText("Objects");

        subPlanetMapData.setText("PlanetMapDataTable");
        subPlanetMapData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subPlanetMapDataActionPerformed(evt);
            }
        });
        mnuObjects.add(subPlanetMapData);

        subProductMapObjData.setText("ProductMapObjDataTable");
        subProductMapObjData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subProductMapObjDataActionPerformed(evt);
            }
        });
        mnuObjects.add(subProductMapObjData);

        subObjName.setText("ObjNameTable");
        subObjName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subObjNameActionPerformed(evt);
            }
        });
        mnuObjects.add(subObjName);

        mnuNPCData.setText("NPCData");

        subCaretaker.setText("CaretakerItem");
        subCaretaker.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subCaretakerActionPerformed(evt);
            }
        });
        mnuNPCData.add(subCaretaker);

        subHoneyBee.setText("HoneyBeeItem");
        subHoneyBee.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subHoneyBeeActionPerformed(evt);
            }
        });
        mnuNPCData.add(subHoneyBee);

        subKinopio.setText("KinopioItem");
        subKinopio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subKinopioActionPerformed(evt);
            }
        });
        mnuNPCData.add(subKinopio);

        subKinopioBank.setText("KinopioBankItem");
        subKinopioBank.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subKinopioBankActionPerformed(evt);
            }
        });
        mnuNPCData.add(subKinopioBank);

        subPeach.setText("PeachItem");
        subPeach.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subPeachActionPerformed(evt);
            }
        });
        mnuNPCData.add(subPeach);

        subPenguinRacer.setText("PenguinRacerItem");
        subPenguinRacer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subPenguinRacerActionPerformed(evt);
            }
        });
        mnuNPCData.add(subPenguinRacer);

        subPenguinRacerLeader.setText("PenguinRacerLeaderItem");
        subPenguinRacerLeader.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subPenguinRacerLeaderActionPerformed(evt);
            }
        });
        mnuNPCData.add(subPenguinRacerLeader);

        subTicoComet.setText("TicoCometItem");
        subTicoComet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subTicoCometActionPerformed(evt);
            }
        });
        mnuNPCData.add(subTicoComet);

        subTicoGalaxy.setText("TicoGalaxyItem");
        subTicoGalaxy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subTicoGalaxyActionPerformed(evt);
            }
        });
        mnuNPCData.add(subTicoGalaxy);

        subTicoFat.setText("TicoFatItem");
        subTicoFat.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subTicoFatActionPerformed(evt);
            }
        });
        mnuNPCData.add(subTicoFat);

        subTicoFatStarPiece.setText("TicoFatStarPiece");
        subTicoFatStarPiece.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subTicoFatStarPieceActionPerformed(evt);
            }
        });
        mnuNPCData.add(subTicoFatStarPiece);

        subTicoFatCoin.setText("TicoFatCoin");
        subTicoFatCoin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subTicoFatCoinActionPerformed(evt);
            }
        });
        mnuNPCData.add(subTicoFatCoin);

        subTicoShop.setText("TicoShopItem");
        subTicoShop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subTicoShopActionPerformed(evt);
            }
        });
        mnuNPCData.add(subTicoShop);

        subTicoShopDice.setText("TicoShopDiceItem");
        subTicoShopDice.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subTicoShopDiceActionPerformed(evt);
            }
        });
        mnuNPCData.add(subTicoShopDice);

        mnuObjects.add(mnuNPCData);
        mnuObjects.add(sep4);

        mniObjectInfluenceTable.setText("ObjectInfluenceTable");
        mniObjectInfluenceTable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mniObjectInfluenceTableActionPerformed(evt);
            }
        });
        mnuObjects.add(mniObjectInfluenceTable);

        mniRushInfluenceTable.setText("RushInfluenceTable");
        mniRushInfluenceTable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mniRushInfluenceTableActionPerformed(evt);
            }
        });
        mnuObjects.add(mniRushInfluenceTable);

        mniMorphConditionTable.setText("MorphConditionTable");
        mniMorphConditionTable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mniMorphConditionTableActionPerformed(evt);
            }
        });
        mnuObjects.add(mniMorphConditionTable);

        subAstroNamePlateData.setText("AstroNamePlateData");
        subAstroNamePlateData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subAstroNamePlateDataActionPerformed(evt);
            }
        });
        mnuObjects.add(subAstroNamePlateData);

        subWorldMapCamera.setText("WorldMapCamera");
        subWorldMapCamera.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subWorldMapCameraActionPerformed(evt);
            }
        });
        mnuObjects.add(subWorldMapCamera);

        mnuOpen.add(mnuObjects);

        mnuSystem.setText("System");

        subHeapSizeExcept.setText("HeapSizeExcept");
        subHeapSizeExcept.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subHeapSizeExceptActionPerformed(evt);
            }
        });
        mnuSystem.add(subHeapSizeExcept);

        subMarioFaceShipEventDataTable.setText("MarioFaceShipEventDataTable");
        subMarioFaceShipEventDataTable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subMarioFaceShipEventDataTableActionPerformed(evt);
            }
        });
        mnuSystem.add(subMarioFaceShipEventDataTable);

        subGalaxyDataTable.setText("GalaxyDataTable");
        subGalaxyDataTable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subGalaxyDataTableActionPerformed(evt);
            }
        });
        mnuSystem.add(subGalaxyDataTable);

        subGalaxyWorldOrderList.setText("GalaxyWorldOrderList");
        subGalaxyWorldOrderList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subGalaxyWorldOrderListActionPerformed(evt);
            }
        });
        mnuSystem.add(subGalaxyWorldOrderList);

        subMarioFaceShipEventCastTable.setText("MarioFaceShipEventCastTable");
        subMarioFaceShipEventCastTable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subMarioFaceShipEventCastTableActionPerformed(evt);
            }
        });
        mnuSystem.add(subMarioFaceShipEventCastTable);

        subWorldMapHeapGalaxy.setText("WorldMapHeapGalaxy");
        subWorldMapHeapGalaxy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subWorldMapHeapGalaxyActionPerformed(evt);
            }
        });
        mnuSystem.add(subWorldMapHeapGalaxy);

        subWorldMapHeapResource.setText("WorldMapHeapResource");
        subWorldMapHeapResource.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subWorldMapHeapResourceActionPerformed(evt);
            }
        });
        mnuSystem.add(subWorldMapHeapResource);

        subGameEventValueTable.setText("GameEventValueTable");
        subGameEventValueTable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subGameEventValueTableActionPerformed(evt);
            }
        });
        mnuSystem.add(subGameEventValueTable);

        subGameSettings.setText("GameSettings");
        subGameSettings.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subGameSettingsActionPerformed(evt);
            }
        });
        mnuSystem.add(subGameSettings);

        subHubworldStarReturnDataTable.setText("HubworldStarReturnDataTable");
        subHubworldStarReturnDataTable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subHubworldStarReturnDataTableActionPerformed(evt);
            }
        });
        mnuSystem.add(subHubworldStarReturnDataTable);

        subSavePointList.setText("SavePointList");
        subSavePointList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subSavePointListActionPerformed(evt);
            }
        });
        mnuSystem.add(subSavePointList);

        mnuOpen.add(mnuSystem);

        mnuAudio.setText("Audio");

        subStageBgmInfo.setText("StageBgmInfo");
        subStageBgmInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subStageBgmInfoActionPerformed(evt);
            }
        });
        mnuAudio.add(subStageBgmInfo);

        subScenarioBgmInfo.setText("ScenarioBgmInfo");
        subScenarioBgmInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subScenarioBgmInfoActionPerformed(evt);
            }
        });
        mnuAudio.add(subScenarioBgmInfo);

        subMultiBgmInfo.setText("MultiBgmInfo");
        subMultiBgmInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subMultiBgmInfoActionPerformed(evt);
            }
        });
        mnuAudio.add(subMultiBgmInfo);

        subBgmParam.setText("BgmParam");
        subBgmParam.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subBgmParamActionPerformed(evt);
            }
        });
        mnuAudio.add(subBgmParam);

        subActionSound.setText("ActionSound");
        subActionSound.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subActionSoundActionPerformed(evt);
            }
        });
        mnuAudio.add(subActionSound);

        subSoundIdToInstList.setText("SoundIdToInstList");
        subSoundIdToInstList.setToolTipText("");
        subSoundIdToInstList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subSoundIdToInstListActionPerformed(evt);
            }
        });
        mnuAudio.add(subSoundIdToInstList);

        mnuOpen.add(mnuAudio);

        mnuEffects.setText("Effects");

        subParticleNames.setText("ParticleNames");
        subParticleNames.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subParticleNamesActionPerformed(evt);
            }
        });
        mnuEffects.add(subParticleNames);

        subAutoEffectList.setText("AutoEffectList");
        subAutoEffectList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subAutoEffectListActionPerformed(evt);
            }
        });
        mnuEffects.add(subAutoEffectList);

        mnuOpen.add(mnuEffects);

        menubar.add(mnuOpen);

        setJMenuBar(menubar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(toolbarPaths, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .addComponent(toolbarButtons, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 900, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(toolbarPaths, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(toolbarButtons, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 586, Short.MAX_VALUE))
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        closeIO();
    }//GEN-LAST:event_formWindowClosing

    private void btnOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpenActionPerformed
        populateBcsvData();
    }//GEN-LAST:event_btnOpenActionPerformed

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        storeBcsv();
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnAddRowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddRowActionPerformed
        DefaultTableModel table = (DefaultTableModel)tblBcsv.getModel();
        
        Object[] row = new Object[bcsv.fields.size()];
        int i = 0;
        
        for (Bcsv.Field field : bcsv.fields.values()) {
            switch(field.type) {
                case 0:
                case 3: row[i] = -1; break;
                case 1:
                case 6: row[i] = ""; break;
                case 2: row[i] = 0.0f; break;
                case 4: row[i] = (short)-1; break;
                case 5: row[i] = (byte)-1; break;
            }
            
            i++;
        }
        
        table.addRow(row);
    }//GEN-LAST:event_btnAddRowActionPerformed

    private void btnDuplicateRowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDuplicateRowActionPerformed
        int[] selectedRows = tblBcsv.getSelectedRows();
        
        if (selectedRows.length > 0) {
            Vector<Vector> rows = tableModel.getDataVector();

            for (int selectedRow = 0; selectedRow < selectedRows.length; selectedRow++) {
                Vector duplicate = (Vector)rows.elementAt(selectedRows[selectedRow]).clone();
                tableModel.addRow(duplicate);
            }
        }
    }//GEN-LAST:event_btnDuplicateRowActionPerformed

    private void btnDeleteRowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteRowActionPerformed
        int[] selectedRows = tblBcsv.getSelectedRows();
        
        if (selectedRows.length > 0) {
            for (int selectedRow = selectedRows.length - 1 ; selectedRow >= 0 ; selectedRow--) {
                tableModel.removeRow(selectedRows[selectedRow]);
            }
        }
    }//GEN-LAST:event_btnDeleteRowActionPerformed

    private void btnClearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClearActionPerformed
        tableModel.setRowCount(0);
    }//GEN-LAST:event_btnClearActionPerformed

    private void subOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subOpenActionPerformed
        populateBcsvData();
    }//GEN-LAST:event_subOpenActionPerformed

    private void subSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subSaveActionPerformed
        storeBcsv();
    }//GEN-LAST:event_subSaveActionPerformed

    private void subCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subCloseActionPerformed
        dispose();
    }//GEN-LAST:event_subCloseActionPerformed

    private void subPlanetMapDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subPlanetMapDataActionPerformed
        handleShortcut("/ObjectData/PlanetMapDataTable.arc", "/PlanetMapDataTable/PlanetMapDataTable.bcsv");
    }//GEN-LAST:event_subPlanetMapDataActionPerformed

    private void subProductMapObjDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subProductMapObjDataActionPerformed
        handleShortcut("/ObjectData/ProductMapObjDataTable.arc", "/ProductMapObjDataTable/ProductMapObjDataTable.bcsv");
    }//GEN-LAST:event_subProductMapObjDataActionPerformed

    private void subObjNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subObjNameActionPerformed
        String archiveName;
        
        if (Whitehole.getCurrentGameType() == 1) {
            archiveName = "/StageData/ObjNameTable.arc";
        }
        else {
            archiveName = "/SystemData/ObjNameTable.arc";
        }
        
        handleShortcut(archiveName, "/ObjNameTable/ObjNameTable.tbl");
    }//GEN-LAST:event_subObjNameActionPerformed

    private void subAstroNamePlateDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subAstroNamePlateDataActionPerformed
        handleShortcut("/ObjectData/AstroNamePlateData.arc", "/AstroNamePlateData/AstroNamePlateData.bcsv");
    }//GEN-LAST:event_subAstroNamePlateDataActionPerformed

    private void subCaretakerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subCaretakerActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/CaretakerItem.bcsv");
    }//GEN-LAST:event_subCaretakerActionPerformed

    private void subHoneyBeeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subHoneyBeeActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/HoneyBeeItem.bcsv");
    }//GEN-LAST:event_subHoneyBeeActionPerformed

    private void subKinopioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subKinopioActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/KinopioItem.bcsv");
    }//GEN-LAST:event_subKinopioActionPerformed

    private void subKinopioBankActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subKinopioBankActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/KinopioBankItem.bcsv");
    }//GEN-LAST:event_subKinopioBankActionPerformed

    private void subPeachActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subPeachActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/PeachItem.bcsv");
    }//GEN-LAST:event_subPeachActionPerformed

    private void subPenguinRacerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subPenguinRacerActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/PenguinRacerItem.bcsv");
    }//GEN-LAST:event_subPenguinRacerActionPerformed

    private void subPenguinRacerLeaderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subPenguinRacerLeaderActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/PenguinRacerLeaderItem.bcsv");
    }//GEN-LAST:event_subPenguinRacerLeaderActionPerformed

    private void subTicoCometActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subTicoCometActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/TicoCometItem.bcsv");
    }//GEN-LAST:event_subTicoCometActionPerformed

    private void subTicoGalaxyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subTicoGalaxyActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/TicoGalaxyItem.bcsv");
    }//GEN-LAST:event_subTicoGalaxyActionPerformed

    private void subTicoFatActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subTicoFatActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/TicoFatItem.bcsv");
    }//GEN-LAST:event_subTicoFatActionPerformed

    private void subTicoFatStarPieceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subTicoFatStarPieceActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/TicoFatStarPieceItem.bcsv");
    }//GEN-LAST:event_subTicoFatStarPieceActionPerformed

    private void subTicoFatCoinActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subTicoFatCoinActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/TicoFatCoinItem.bcsv");
    }//GEN-LAST:event_subTicoFatCoinActionPerformed

    private void subTicoShopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subTicoShopActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/TicoShopItem.bcsv");
    }//GEN-LAST:event_subTicoShopActionPerformed

    private void subTicoShopDiceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subTicoShopDiceActionPerformed
        handleShortcut("/ObjectData/NPCData.arc", "/NPCData/TicoShopDiceItem.bcsv");
    }//GEN-LAST:event_subTicoShopDiceActionPerformed

    private void subStageBgmInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subStageBgmInfoActionPerformed
        handleShortcut("/AudioRes/Info/StageBgmInfo.arc", "/StageBgmInfo/StageBgmInfo.bcsv");
    }//GEN-LAST:event_subStageBgmInfoActionPerformed

    private void subScenarioBgmInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subScenarioBgmInfoActionPerformed
        handleShortcut("/AudioRes/Info/StageBgmInfo.arc", "/StageBgmInfo/ScenarioBgmInfo.bcsv");
    }//GEN-LAST:event_subScenarioBgmInfoActionPerformed

    private void subMultiBgmInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subMultiBgmInfoActionPerformed
        handleShortcut("/AudioRes/Info/MultiBgmInfo.arc", "/MultiBgmInfo/MultiBgmInfo.bcsv");
    }//GEN-LAST:event_subMultiBgmInfoActionPerformed

    private void subBgmParamActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subBgmParamActionPerformed
        handleShortcut("/AudioRes/Info/BgmParam.arc", "/BgmParam/BgmParam.bcsv");
    }//GEN-LAST:event_subBgmParamActionPerformed

    private void subActionSoundActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subActionSoundActionPerformed
        handleShortcut("/AudioRes/Info/ActionSound.arc", "/ActionSound/ActionSound.bcsv");
    }//GEN-LAST:event_subActionSoundActionPerformed

    private void subSoundIdToInstListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subSoundIdToInstListActionPerformed
        handleShortcut("/AudioRes/Info/SoundIdToInstList.arc", "/SoundIdToInstList/SoundIdToInstList.bcsv");
    }//GEN-LAST:event_subSoundIdToInstListActionPerformed

    private void subParticleNamesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subParticleNamesActionPerformed
        handleShortcut("/ParticleData/Effect.arc", "/Effect/ParticleNames.bcsv");
    }//GEN-LAST:event_subParticleNamesActionPerformed

    private void subAutoEffectListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subAutoEffectListActionPerformed
        handleShortcut("/ParticleData/Effect.arc", "/Effect/AutoEffectList.bcsv");
    }//GEN-LAST:event_subAutoEffectListActionPerformed

    private void subGalaxyDataTableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subGalaxyDataTableActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/GalaxyDataTable.bcsv");
    }//GEN-LAST:event_subGalaxyDataTableActionPerformed

    private void subGalaxyWorldOrderListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subGalaxyWorldOrderListActionPerformed
        if (Whitehole.isGalaxyLevelEngine()) {
            handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/GalaxyOrderList.bcsv");
        } else {
            handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/GalaxyWorldOrderList.bcsv");
        }
    }//GEN-LAST:event_subGalaxyWorldOrderListActionPerformed

    private void subMarioFaceShipEventDataTableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subMarioFaceShipEventDataTableActionPerformed
        if (Whitehole.isGalaxyLevelEngine()) {
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/HubworldEventDataTable.bcsv");
        } else {
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/MarioFaceShipEventDataTable.bcsv");
        }
    }//GEN-LAST:event_subMarioFaceShipEventDataTableActionPerformed

    private void subMarioFaceShipEventCastTableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subMarioFaceShipEventCastTableActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/MarioFaceShipEventCastTable.bcsv");
    }//GEN-LAST:event_subMarioFaceShipEventCastTableActionPerformed

    private void subHeapSizeExceptActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subHeapSizeExceptActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/HeapSizeExcept.bcsv");
    }//GEN-LAST:event_subHeapSizeExceptActionPerformed

    private void subWorldMapHeapGalaxyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subWorldMapHeapGalaxyActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/WorldMapHeapGalaxy.bcsv");
    }//GEN-LAST:event_subWorldMapHeapGalaxyActionPerformed

    private void subWorldMapHeapResourceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subWorldMapHeapResourceActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/WorldMapHeapResource.bcsv");
    }//GEN-LAST:event_subWorldMapHeapResourceActionPerformed

    private void subLightDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subLightDataActionPerformed
        String archiveName;
        
        if (Whitehole.getCurrentGameType() == 1) {
            archiveName = "/ObjectData/LightData.arc";
        }
        else {
            archiveName = "/LightData/LightData.arc";
        }
        
        handleShortcut(archiveName, "/LightData/LightData.bcsv");
    }//GEN-LAST:event_subLightDataActionPerformed

    private void subWorldMapCameraActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subWorldMapCameraActionPerformed
        handleShortcut("/ObjectData/WorldMapCamera.arc", "/WorldMapCamera/ActorInfo/CameraParam.bcam");
    }//GEN-LAST:event_subWorldMapCameraActionPerformed

    private void subScenarioDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subScenarioDataActionPerformed
        String stage = getTextInput("Enter the stage's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName = String.format("/StageData/%s/%sScenario.arc", stage, stage);
            String bcsvName = String.format("/%sScenario/ScenarioData.bcsv", stage);
            handleShortcut(archiveName, bcsvName);
        }
    }//GEN-LAST:event_subScenarioDataActionPerformed

    private void subGalaxyInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subGalaxyInfoActionPerformed
        String stage = getTextInput("Enter the stage's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName = String.format("/StageData/%s/%sScenario.arc", stage, stage);
            String bcsvName = String.format("/%sScenario/GalaxyInfo.bcsv", stage);
            handleShortcut(archiveName, bcsvName);
        }
    }//GEN-LAST:event_subGalaxyInfoActionPerformed

    private void subZoneListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subZoneListActionPerformed
        String stage = getTextInput("Enter the stage's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName = String.format("/StageData/%s/%sScenario.arc", stage, stage);
            String bcsvName = String.format("/%sScenario/ZoneList.bcsv", stage);
            handleShortcut(archiveName, bcsvName);
        }
    }//GEN-LAST:event_subZoneListActionPerformed

    private void subCameraParamActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subCameraParamActionPerformed
        String stage = getTextInput("Enter the stage's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName;
            
            if (Whitehole.getCurrentGameType() == 1) {
                archiveName = String.format("/StageData/%s.arc", stage, stage);
            }
            else {
                archiveName = String.format("/StageData/%s/%sMap.arc", stage, stage);
            }
            
            handleShortcut(archiveName, "/Stage/camera/CameraParam.bcam");
        }
    }//GEN-LAST:event_subCameraParamActionPerformed

    private void subLightDataZoneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subLightDataZoneActionPerformed
        String stage = getTextInput("Enter the stage's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName;
            String bcsvName;
            
            if (Whitehole.getCurrentGameType() == 1) {
                archiveName = "/ObjectData/LightData.arc";
                bcsvName = String.format("/LightData/Light%s.bcsv", stage);
            }
            else {
                archiveName = String.format("/StageData/%s/%sLight.arc", stage, stage);
                bcsvName = String.format("/Stage/csv/%sLight.bcsv", stage);
            }
            
            handleShortcut(archiveName, bcsvName);
        }
    }//GEN-LAST:event_subLightDataZoneActionPerformed

    private void subURcommonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURcommonActionPerformed
        handleUseResourceShortcut("common.bcsv");
    }//GEN-LAST:event_subURcommonActionPerformed

    private void subUR1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subUR1ActionPerformed
        handleUseResourceShortcut("scenario_1.bcsv");
    }//GEN-LAST:event_subUR1ActionPerformed

    private void subUR2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subUR2ActionPerformed
        handleUseResourceShortcut("scenario_2.bcsv");
    }//GEN-LAST:event_subUR2ActionPerformed

    private void subUR3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subUR3ActionPerformed
        handleUseResourceShortcut("scenario_3.bcsv");
    }//GEN-LAST:event_subUR3ActionPerformed

    private void subURarcCommonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURarcCommonActionPerformed
        handleUseResourceShortcut("wave_arc_common.bcsv");
    }//GEN-LAST:event_subURarcCommonActionPerformed

    private void subURarc1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURarc1ActionPerformed
        handleUseResourceShortcut("wave_arc_scenario_1.bcsv");
    }//GEN-LAST:event_subURarc1ActionPerformed

    private void subURarc2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURarc2ActionPerformed
        handleUseResourceShortcut("wave_arc_scenario_2.bcsv");
    }//GEN-LAST:event_subURarc2ActionPerformed

    private void subURarc3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURarc3ActionPerformed
        handleUseResourceShortcut("wave_arc_scenario_3.bcsv");
    }//GEN-LAST:event_subURarc3ActionPerformed

    private void subURsoundCommonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURsoundCommonActionPerformed
        handleUseResourceShortcut("sound_common.bcsv");
    }//GEN-LAST:event_subURsoundCommonActionPerformed

    private void subURsound1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURsound1ActionPerformed
        handleUseResourceShortcut("sound_scenario_1.bcsv");
    }//GEN-LAST:event_subURsound1ActionPerformed

    private void subURsound2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURsound2ActionPerformed
        handleUseResourceShortcut("sound_scenario_2.bcsv");
    }//GEN-LAST:event_subURsound2ActionPerformed

    private void subURsound3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subURsound3ActionPerformed
        handleUseResourceShortcut("sound_scenario_3.bcsv");
    }//GEN-LAST:event_subURsound3ActionPerformed

    private void mniObjectInfluenceTableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mniObjectInfluenceTableActionPerformed
        handleShortcut("/ObjectData/MarioConst.arc", "/MarioConst/ActorInfo/ObjectInfluenceTable.bcsv");
    }//GEN-LAST:event_mniObjectInfluenceTableActionPerformed

    private void mniRushInfluenceTableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mniRushInfluenceTableActionPerformed
        handleShortcut("/ObjectData/MarioConst.arc", "/MarioConst/ActorInfo/RushInfluenceTable.bcsv");
    }//GEN-LAST:event_mniRushInfluenceTableActionPerformed

    private void mniMorphConditionTableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mniMorphConditionTableActionPerformed
        handleShortcut("/ObjectData/MarioConst.arc", "/MarioConst/ActorInfo/MorphConditionTable.bcsv");
    }//GEN-LAST:event_mniMorphConditionTableActionPerformed

    private void subChangeSceneListInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subChangeSceneListInfoActionPerformed
        String stage = getTextInput("Enter the zone's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName = String.format("/StageData/%s/%sMap.arc", stage, stage);
            String bcsvName = String.format("/Stage/jmp/List/ChangeSceneListInfo", stage);
            handleShortcut(archiveName, bcsvName);
        }
    }//GEN-LAST:event_subChangeSceneListInfoActionPerformed

    private void subScenarioSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subScenarioSettingsActionPerformed
        String stage = getTextInput("Enter the galaxy's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName = String.format("/StageData/%s/%sMap.arc", stage, stage);
            String bcsvName = String.format("/Stage/jmp/List/ScenarioSettings", stage);
            handleShortcut(archiveName, bcsvName);
        }
    }//GEN-LAST:event_subScenarioSettingsActionPerformed

    private void subStageInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subStageInfoActionPerformed
        String stage = getTextInput("Enter the galaxy's file name: ");
        
        if (stage != null && !stage.isEmpty()) {
            String archiveName = String.format("/StageData/%s/%sMap.arc", stage, stage);
            String bcsvName = String.format("/Stage/jmp/List/StageInfo", stage);
            handleShortcut(archiveName, bcsvName);
        }
    }//GEN-LAST:event_subStageInfoActionPerformed

    private void subGameEventValueTableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subGameEventValueTableActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/GameEventValueTable.bcsv");
    }//GEN-LAST:event_subGameEventValueTableActionPerformed

    private void subGameSettingsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subGameSettingsActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/GameSettings.bcsv");
    }//GEN-LAST:event_subGameSettingsActionPerformed

    private void subSavePointListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subSavePointListActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/SavePointList.bcsv");
    }//GEN-LAST:event_subSavePointListActionPerformed

    private void subHubworldStarReturnDataTableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subHubworldStarReturnDataTableActionPerformed
        handleShortcut("/ObjectData/SystemDataTable.arc", "/SystemDataTable/HubworldStarReturnDataTable.bcsv");
    }//GEN-LAST:event_subHubworldStarReturnDataTableActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JButton btnAddRow;
    private javax.swing.JButton btnClear;
    private javax.swing.JButton btnDeleteRow;
    private javax.swing.JButton btnDuplicateRow;
    private javax.swing.JButton btnOpen;
    private javax.swing.JButton btnSave;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblArchive;
    private javax.swing.JLabel lblFile;
    private javax.swing.JMenuBar menubar;
    private javax.swing.JMenuItem mniMorphConditionTable;
    private javax.swing.JMenuItem mniObjectInfluenceTable;
    private javax.swing.JMenuItem mniRushInfluenceTable;
    private javax.swing.JMenu mnuAudio;
    private javax.swing.JMenu mnuEffects;
    private javax.swing.JMenu mnuFile;
    private javax.swing.JMenu mnuNPCData;
    private javax.swing.JMenu mnuObjects;
    private javax.swing.JMenu mnuOpen;
    private javax.swing.JMenu mnuStages;
    private javax.swing.JMenu mnuSystem;
    private javax.swing.JMenu mnuUseResource;
    private javax.swing.JPopupMenu.Separator sep1;
    private javax.swing.JPopupMenu.Separator sep2;
    private javax.swing.JPopupMenu.Separator sep3;
    private javax.swing.JPopupMenu.Separator sep4;
    private javax.swing.JToolBar.Separator spr2;
    private javax.swing.JToolBar.Separator spr3;
    private javax.swing.JToolBar.Separator spr5;
    private javax.swing.JToolBar.Separator spr6;
    private javax.swing.JToolBar.Separator spr7;
    private javax.swing.JMenuItem subActionSound;
    private javax.swing.JMenuItem subAstroNamePlateData;
    private javax.swing.JMenuItem subAutoEffectList;
    private javax.swing.JMenuItem subBgmParam;
    private javax.swing.JMenuItem subCameraParam;
    private javax.swing.JMenuItem subCaretaker;
    private javax.swing.JMenuItem subChangeSceneListInfo;
    private javax.swing.JMenuItem subClose;
    private javax.swing.JMenuItem subGalaxyDataTable;
    private javax.swing.JMenuItem subGalaxyInfo;
    private javax.swing.JMenuItem subGalaxyWorldOrderList;
    private javax.swing.JMenuItem subGameEventValueTable;
    private javax.swing.JMenuItem subGameSettings;
    private javax.swing.JMenuItem subHeapSizeExcept;
    private javax.swing.JMenuItem subHoneyBee;
    private javax.swing.JMenuItem subHubworldStarReturnDataTable;
    private javax.swing.JMenuItem subKinopio;
    private javax.swing.JMenuItem subKinopioBank;
    private javax.swing.JMenuItem subLightData;
    private javax.swing.JMenuItem subLightDataZone;
    private javax.swing.JMenuItem subMarioFaceShipEventCastTable;
    private javax.swing.JMenuItem subMarioFaceShipEventDataTable;
    private javax.swing.JMenuItem subMultiBgmInfo;
    private javax.swing.JMenuItem subObjName;
    private javax.swing.JMenuItem subOpen;
    private javax.swing.JMenuItem subParticleNames;
    private javax.swing.JMenuItem subPeach;
    private javax.swing.JMenuItem subPenguinRacer;
    private javax.swing.JMenuItem subPenguinRacerLeader;
    private javax.swing.JMenuItem subPlanetMapData;
    private javax.swing.JMenuItem subProductMapObjData;
    private javax.swing.JMenuItem subSave;
    private javax.swing.JMenuItem subSavePointList;
    private javax.swing.JMenuItem subScenarioBgmInfo;
    private javax.swing.JMenuItem subScenarioData;
    private javax.swing.JMenuItem subScenarioSettings;
    private javax.swing.JMenuItem subSoundIdToInstList;
    private javax.swing.JMenuItem subStageBgmInfo;
    private javax.swing.JMenuItem subStageInfo;
    private javax.swing.JMenuItem subTicoComet;
    private javax.swing.JMenuItem subTicoFat;
    private javax.swing.JMenuItem subTicoFatCoin;
    private javax.swing.JMenuItem subTicoFatStarPiece;
    private javax.swing.JMenuItem subTicoGalaxy;
    private javax.swing.JMenuItem subTicoShop;
    private javax.swing.JMenuItem subTicoShopDice;
    private javax.swing.JMenuItem subUR1;
    private javax.swing.JMenuItem subUR2;
    private javax.swing.JMenuItem subUR3;
    private javax.swing.JMenuItem subURarc1;
    private javax.swing.JMenuItem subURarc2;
    private javax.swing.JMenuItem subURarc3;
    private javax.swing.JMenuItem subURarcCommon;
    private javax.swing.JMenuItem subURcommon;
    private javax.swing.JMenuItem subURsound1;
    private javax.swing.JMenuItem subURsound2;
    private javax.swing.JMenuItem subURsound3;
    private javax.swing.JMenuItem subURsoundCommon;
    private javax.swing.JMenuItem subWorldMapCamera;
    private javax.swing.JMenuItem subWorldMapHeapGalaxy;
    private javax.swing.JMenuItem subWorldMapHeapResource;
    private javax.swing.JMenuItem subZoneList;
    public javax.swing.JTextField tbArchiveName;
    public javax.swing.JTextField tbFileName;
    public javax.swing.JTable tblBcsv;
    private javax.swing.JToolBar toolbarButtons;
    private javax.swing.JToolBar toolbarPaths;
    // End of variables declaration//GEN-END:variables
}