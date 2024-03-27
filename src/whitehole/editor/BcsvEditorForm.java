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

import java.awt.Component;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
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
        
        try(FileReader reader = new FileReader("data/shortcuts.json", StandardCharsets.UTF_8)) {
            JSONObject customShortcutsList = new JSONObject(new JSONTokener(reader));
            addNewCustomShortcuts(mnuOpen, customShortcutsList);
        }
        catch (IOException ex) {
            System.out.println("Failed to load shortcuts.json");
            System.out.println(ex);
        }
        
        tableModel = (DefaultTableModel)tblBcsv.getModel();
        adjuster = new TableColumnAdjuster(tblBcsv);
        tblBcsv.setAutoCreateRowSorter(true);
        
        tblBcsv.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            lblColAndRow.setText(getSelectedColRowFormatted());
        });
    }
    
    private void toggleShortcutVisibility() {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);
        
        try(FileReader reader = new FileReader("data/shortcuts.json", StandardCharsets.UTF_8)) {
            JSONObject customShortcutsList = new JSONObject(new JSONTokener(reader));
            changeCustomShortcutVisibility(mnuOpen, customShortcutsList);
            changeCustomShortcutPaths(mnuOpen, customShortcutsList);
            changeCustomShortcutAttribute(mnuOpen, customShortcutsList, "Name");
            changeCustomShortcutAttribute(mnuOpen, customShortcutsList, "Tooltip");
        }
        catch (IOException ex) {
            System.out.println("Failed to load shortcuts.json");
            System.out.println(ex);
        }
        
        if (Whitehole.getCurrentGameType()==1) {
            tbArchiveName.setText("/StageData/CocoonExGalaxy/CocoonExGalaxyScenario.arc");
            tbFileName.setText("/CocoonExGalaxyScenario/ScenarioData.bcsv");
        }
        else {
            tbArchiveName.setText("/StageData/RedBlueExGalaxy/RedBlueExGalaxyScenario.arc");
            tbFileName.setText("/RedBlueExGalaxyScenario/ScenarioData.bcsv");
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
    
    // Grabs the variables that need to be replaced in and then handles the shortcut.
    private void handleShortcutGrabVariables(String archivePath, String bcsvPath, JSONArray variables) {
        // Declaration and also grabbing data.
        ArrayList<String> variableStringOrderList = new ArrayList<>(0); // The strings that are used.
        ArrayList<String> variableStringList = new ArrayList<>(0); // The string list for each unique variable.
        ArrayList<String> variableNameList = new ArrayList<>(0); // The names of all variables.
        
        // Get variable values for each variable listed. Accounts for duplicates.
        for (int i = 0; i < variables.length(); i++) {
            
            // Grab needed data.
            JSONObject currentVariable = variables.getJSONObject(i);
            String variableGetMessage = currentVariable.optString("VariableGetMessage");
            String variableName = currentVariable.optString("VariableName");
            String variableRequiredFile = currentVariable.optString("RequiredFile");
            
            // Don't bother adding the variable's string if the required file doesn't exist.
            if (doesRequiredFileExist(variableRequiredFile)) {
                if (!variableGetMessage.isBlank()) {
                    String variableInput = getTextInput(variableGetMessage);
                    
                    // If they do not enter anything or click cancel it will not handle the shortcut.
                    if (variableInput==null || variableInput.isEmpty()) {
                        return;
                    }
                    
                    // Add info to each list.
                    variableStringList.add(variableInput);
                    variableStringOrderList.add(variableInput);
                    variableNameList.add(variableName);
                } else {
                    // If they didn't add a message, it will look for any existing variables under the title. This lets you repeat variables without having to ask a lot of times.
                    int nameListIndex = variableNameList.indexOf(variableName);
                    if (nameListIndex!=-1) {
                        variableStringOrderList.add(variableStringList.get(variableNameList.indexOf(variableName)));
                    }
                }
            }
        }
        
        // Set bcsvPath and archivePath.
        int offset = archivePath.split("%s", -1).length - 1;
        archivePath = formatPathWithList(archivePath, variableStringOrderList, 0);
        bcsvPath = formatPathWithList(bcsvPath, variableStringOrderList, offset);
        
        handleShortcut(archivePath, bcsvPath);
    }
        
    // Gets the topmost attribute entered that has the required file.
    private static String getTopPriorityAttribute (JSONArray attributeList, String attributeName, String defaultName) {
        boolean changeAttribute;
        if (attributeList!=null) {
            for (int a = attributeList.length(); a > 0; a--) {
                // Grab element attribute info
                JSONObject attributeListInfo = attributeList.getJSONObject(a-1);
                String elementAttribute = attributeListInfo.optString(attributeName);
                changeAttribute = isChangeAttributeWithInfo(attributeListInfo);
            
                if (changeAttribute) {
                    defaultName = elementAttribute;
                }
            }
        }
        return defaultName;
    }
    
    // Checks if the required file exists based on a JSONObject given. It also checks if an ! is at the beginning so it can invert the boolean.
    private static boolean isChangeAttributeWithInfo (JSONObject attributeInfo) {
        String elementAttributeRequiredFile = attributeInfo.optString("RequiredFile");
        Boolean invert;
        Boolean isChangeAttribute;
        
        // Check whether to invert and change string to not include '!'
        if (!(elementAttributeRequiredFile.isBlank()) && elementAttributeRequiredFile.charAt(0) == '!') {
            invert = true;
            elementAttributeRequiredFile = elementAttributeRequiredFile.substring(1);
        } else {
            invert = false;
        }
            
        // Check if the shortcut will be changed and invert if needed
        isChangeAttribute = doesRequiredFileExist(elementAttributeRequiredFile);
        if (invert)
            isChangeAttribute ^= true;
        return isChangeAttribute;
    }
    
    // Checks if an archive exists or, if it matches a preset, checks if that preset's archive exists.
    public static boolean doesRequiredFileExist(String requiredFile) {
        boolean isSmg1 = Whitehole.getCurrentGameType() == 1;
        boolean hasGLE = Whitehole.isGalaxyLevelEngine();
        switch (requiredFile) {
            case "SMG2":
                return !isSmg1;
            case "SMG1":
                return isSmg1;
            case "GLE":
                return hasGLE;
            case "":
                return true;
            default:
                return Whitehole.doesArchiveExist(requiredFile);
        }
    }
        
    // Formats a path with an ArrayList of strings. An offset may also be used to specify what variable to start with.
    private String formatPathWithList(String path, ArrayList<String> variableOrderList, int offset) {
        ArrayList<String> limitedVariableOrderList = new ArrayList<>(0);
            
        // Checks how many variables are set. 
        int varUseNum = path.split("%s", -1).length-1;   
        
        if (varUseNum !=0) {
            // Make a new list containing the variables that need to be used.
            for (int i = 0; i < varUseNum; i++) {
                limitedVariableOrderList.add(variableOrderList.get(i+offset));
            }
            
            return String.format(path, limitedVariableOrderList.toArray());
        }
        return path;
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Attribute setting functions
    
    // Sets attribute chosen for all menu items. Only "Tooltip" and "Name" are valid.
    private void changeCustomShortcutAttribute(JMenu mnuParent, JSONObject objectWithArray, String attribute) {
        JSONArray containedObjectsList = objectWithArray.optJSONArray("MenuElements");
        for (int i = 0; i < containedObjectsList.length(); i++) {
            
            // Grab element data
            JSONObject elementInfo = containedObjectsList.getJSONObject(i);
            String elementDefaultAttribute = elementInfo.optString("Default"+attribute); 
            JSONArray elementMembers = elementInfo.optJSONArray("MenuElements");
            JSONArray elementOtherAttributes = elementInfo.optJSONArray("Other"+attribute+"s");
            Component mnuComponent = (mnuParent.getMenuComponent(i));
            
            // Go through each attribute chosen in the element
            elementDefaultAttribute = getTopPriorityAttribute(elementOtherAttributes, attribute, elementDefaultAttribute);
            
            // Set attribute based on what type of Component and attribute it is.
            if (mnuComponent instanceof javax.swing.JMenu) {
                JMenu customMnu = ((JMenu) mnuComponent);
                if (attribute.equals("Name")) {
                    customMnu.setText(elementDefaultAttribute);
                } else if (attribute.equals("Tooltip")) {
                    customMnu.setToolTipText(elementDefaultAttribute);
                }
            } else if (mnuComponent instanceof javax.swing.JMenuItem) {
                JMenuItem customMnuItem = ((JMenuItem) mnuComponent);
                if (attribute.equals("Name")) {
                    customMnuItem.setText(elementDefaultAttribute);
                } else if (attribute.equals("Tooltip")) {
                    customMnuItem.setToolTipText(elementDefaultAttribute);
                }
            }
            
            // Check for members and set them.
            if (elementMembers != null) {
                if (mnuComponent instanceof javax.swing.JMenu) {
                    changeCustomShortcutAttribute((JMenu) mnuComponent, elementInfo, attribute);
                }
            }
        }
    }
    
    // Sets the ArchivePath and BcsvPath according to the top path that returns true for RequiredFile. Uses default path if none return true.
    public void changeCustomShortcutPaths(JMenu mnuParent, JSONObject objectWithArray) {
        JSONArray containedObjectsList = objectWithArray.optJSONArray("MenuElements");
        for (int i = 0; i < containedObjectsList.length(); i++) {
            
            // Grab element data
            JSONObject elementInfo = containedObjectsList.getJSONObject(i);
            JSONArray elementMembers = elementInfo.optJSONArray("MenuElements");
            JSONArray elementOtherArchivePaths = elementInfo.optJSONArray("OtherArchivePaths");
            JSONArray elementOtherBcsvPaths = elementInfo.optJSONArray("OtherBcsvPaths");
            JSONArray elementVariables = elementInfo.optJSONArray("Variables");
            String archivePath = elementInfo.optString("DefaultArchivePath");
            String bcsvPath = elementInfo.optString("DefaultBcsvPath");
            
            // Get the topmost archivePath and bcsvPath that returns true.
            if (elementOtherArchivePaths != null) {
                archivePath = getTopPriorityAttribute(elementOtherArchivePaths, "ArchivePath", archivePath);
            }
            if (elementOtherBcsvPaths != null) {
                bcsvPath = getTopPriorityAttribute(elementOtherBcsvPaths, "BcsvPath", bcsvPath);
            }
            
            // Adds a listener depending upon what menu type the current element is.
            final String chosenArchivePath = archivePath;
            final String chosenBcsvPath = bcsvPath;
            if (!(archivePath.isBlank() || bcsvPath.isBlank())) { 
                if (mnuParent.getMenuComponent(i) instanceof javax.swing.JMenu) 
                {
                    JMenu customMnu = ((JMenu) (mnuParent.getMenuComponent(i)));
                    
                    // Remove any existing listeners.
                    if (customMnu.getMouseListeners().length!=0)
                        customMnu.removeMouseListener((customMnu.getMouseListeners())[0]);
                    
                    // Add new listener.
                    if (elementVariables!=null) {
                    customMnu.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent evt) {
                            handleShortcutGrabVariables(chosenArchivePath, chosenBcsvPath, elementVariables);
                        }
                    });
                    } else {
                    customMnu.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent evt) {
                            handleShortcut(chosenArchivePath, chosenBcsvPath);
                        }
                    });
                    }
                    
                } else if (mnuParent.getMenuComponent(i) instanceof javax.swing.JMenuItem) {
                    JMenuItem customMnuItem = ((JMenuItem) (mnuParent.getMenuComponent(i)));
                    
                    // Remove any existing listeners.
                    if (customMnuItem.getActionListeners().length!=0)
                        customMnuItem.removeActionListener(customMnuItem.getActionListeners()[0]);
                    
                    // Add new listener.
                    if (elementVariables!=null) {
                    customMnuItem.addActionListener((java.awt.event.ActionEvent evt) -> {
                        handleShortcutGrabVariables(chosenArchivePath, chosenBcsvPath, elementVariables);
                    });
                    } else {
                    customMnuItem.addActionListener((java.awt.event.ActionEvent evt) -> {
                        handleShortcut(chosenArchivePath, chosenBcsvPath);
                    });
                    }
                }
            }
            
            // Sets any members if the current element is a menu and has members.
            if (elementMembers != null) {
                if (mnuParent.getMenuComponent(i) instanceof javax.swing.JMenu) {
                    changeCustomShortcutPaths((JMenu) (mnuParent.getMenuComponent(i)), elementInfo);
                }
            }
        }
    }
        
    // Sets whether component is visible using RequiredFile.
    private void changeCustomShortcutVisibility(JMenu mnuParent, JSONObject objectWithArray) {
        JSONArray containedObjectsList = objectWithArray.optJSONArray("MenuElements");
        for (int i = 0; i < containedObjectsList.length(); i++) {
            JSONObject elementInfo = containedObjectsList.getJSONObject(i);
            // Grab element data
            JSONArray elementMembers = elementInfo.optJSONArray("MenuElements");
            Component mnuComponent = (mnuParent.getMenuComponent(i));
            
            boolean isShown = isChangeAttributeWithInfo(elementInfo);
            
            mnuComponent.setVisible(isShown);
            
            if (elementMembers != null) {
                if (mnuParent.getMenuComponent(i) instanceof javax.swing.JMenu) {
                    changeCustomShortcutVisibility((JMenu) (mnuParent.getMenuComponent(i)), elementInfo);
                }
            }
        }
    }
    
    private void addNewCustomShortcuts(JMenuItem mnuParent, JSONObject objectWithArray) {
        JSONArray containedObjectsList = objectWithArray.optJSONArray("MenuElements");

        for (int i = 0; i < containedObjectsList.length(); i++) {
            JSONObject elementInfo = containedObjectsList.getJSONObject(i);
            // Grab element data
            String elementType = elementInfo.optString("Type");
            String elementArchivePath = elementInfo.optString("DefaultArchivePath");
            String elementBcsvPath = elementInfo.optString("DefaultBcsvPath");
            JSONArray elementMembers = objectWithArray.optJSONArray("MenuElements");

            switch (elementType) {
                case "Menu":
                    // Create menu and set it up
                    JMenu customMnu = new javax.swing.JMenu();
                    mnuParent.add(customMnu);
                    
                    // Add a shortcut listener (optional)
                    // Did they make the menu have an archive path?
                    if (!(elementArchivePath.isBlank() || elementBcsvPath.isBlank()) 
                    // Is there another path we will have to worry about?
                    && elementInfo.optJSONArray("OtherArchivePaths").isEmpty() && elementInfo.optJSONArray("OtherBcsvPaths").isEmpty()) { 
                        customMnu.addMouseListener(new java.awt.event.MouseAdapter() {
                            @Override
                            public void mouseClicked(java.awt.event.MouseEvent evt) {
                                handleShortcut(elementArchivePath, elementBcsvPath);
                            }
                        });
                    }
                    
                    // Check for members and set them up
                    if (elementMembers != null) {
                        addNewCustomShortcuts(customMnu, elementInfo);
                    }
                break;
                case "Shortcut":
                    // Create shortcut and set it up
                    JMenuItem customMnuItem = new javax.swing.JMenuItem();
                    mnuParent.add(customMnuItem);
                    
                    // Setting up the action listener
                    if (!(elementArchivePath.isBlank() || elementBcsvPath.isBlank())) {
                        // Is there another path we will have to worry about?
                        if ((elementInfo.optJSONArray("OtherArchivePaths"))==null && 
                                (elementInfo.optJSONArray("OtherBcsvPaths"))==null){
                            customMnuItem.addActionListener((java.awt.event.ActionEvent evt) -> {
                                handleShortcut(elementArchivePath, elementBcsvPath);
                            });
                        }
                    }
                break;

                case "Separator":
                    JSeparator customSep = new javax.swing.JSeparator();
                    mnuParent.add(customSep);
                break;
                default:
                    System.out.println(String.format("Invalid Menu Element Type %s", elementType));
                break;
            }
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
                        case 3: entry.put(field.hash, Integer.parseInt(val.replaceAll(",", ""))); break;
                        case 1:
                        case 6: entry.put(field.hash, val); break;
                        case 2: entry.put(field.hash, Float.parseFloat(val.replaceAll(",", ""))); break;
                        case 4: entry.put(field.hash, Short.parseShort(val.replaceAll(",", ""))); break;
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

    // Gets the string that will be put in the top right.
    // If the column or row is invalid, it will return an empty string.
    private String getSelectedColRowFormatted() {
        int col = tblBcsv.getSelectedColumn();
        int row = tblBcsv.getSelectedRow();
        
        if (col!=-1 && row!=-1)
            return "["+col+", "+row+"]";
        return "";
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
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        lblColAndRow = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblBcsv = new javax.swing.JTable();
        menubar = new javax.swing.JMenuBar();
        mnuFile = new javax.swing.JMenu();
        subOpen = new javax.swing.JMenuItem();
        subSave = new javax.swing.JMenuItem();
        subClose = new javax.swing.JMenuItem();
        mnuOpen = new javax.swing.JMenu();

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
        toolbarButtons.add(filler2);

        lblColAndRow.setToolTipText("Column, Row");
        toolbarButtons.add(lblColAndRow);
        lblColAndRow.getAccessibleContext().setAccessibleName("");

        tblBcsv.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        tblBcsv.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        tblBcsv.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tblBcsvMouseClicked(evt);
            }
        });
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
        mnuOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuOpenActionPerformed(evt);
            }
        });
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

    private void mnuOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuOpenActionPerformed

    }//GEN-LAST:event_mnuOpenActionPerformed

    private void tblBcsvMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tblBcsvMouseClicked
        lblColAndRow.setText(getSelectedColRowFormatted());
    }//GEN-LAST:event_tblBcsvMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JButton btnAddRow;
    private javax.swing.JButton btnClear;
    private javax.swing.JButton btnDeleteRow;
    private javax.swing.JButton btnDuplicateRow;
    private javax.swing.JButton btnOpen;
    private javax.swing.JButton btnSave;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblArchive;
    private javax.swing.JLabel lblColAndRow;
    private javax.swing.JLabel lblFile;
    private javax.swing.JMenuBar menubar;
    private javax.swing.JMenu mnuFile;
    private javax.swing.JMenu mnuOpen;
    private javax.swing.JToolBar.Separator spr2;
    private javax.swing.JToolBar.Separator spr3;
    private javax.swing.JToolBar.Separator spr5;
    private javax.swing.JToolBar.Separator spr6;
    private javax.swing.JToolBar.Separator spr7;
    private javax.swing.JMenuItem subClose;
    private javax.swing.JMenuItem subOpen;
    private javax.swing.JMenuItem subSave;
    public javax.swing.JTextField tbArchiveName;
    public javax.swing.JTextField tbFileName;
    public javax.swing.JTable tblBcsv;
    private javax.swing.JToolBar toolbarButtons;
    private javax.swing.JToolBar toolbarPaths;
    // End of variables declaration//GEN-END:variables
}