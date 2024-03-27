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

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import whitehole.Settings;
import whitehole.Whitehole;
import whitehole.db.GalaxyNames;
import whitehole.rendering.GLRenderer;
import whitehole.rendering.GLRenderer.RenderMode;
import whitehole.rendering.RendererCache;
import whitehole.rendering.RendererFactory;
import whitehole.smg.Bcsv;
import whitehole.smg.GalaxyArchive;
import whitehole.smg.StageArchive;
import whitehole.smg.object.*;
import whitehole.util.CheckBoxList;
import whitehole.math.Matrix4;
import whitehole.util.PropertyGrid;
import whitehole.math.RotationMatrix;
import whitehole.math.Vec2f;
import whitehole.math.Vec3f;

public class GalaxyEditorForm extends javax.swing.JFrame {
    private static final float SCALE_DOWN = 10000f;
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Variables
    
    // General
    private boolean isGalaxyMode = true;
    private String galaxyName;
    private GalaxyArchive galaxyArchive = null;
    private HashMap<String, StageArchive> zoneArchives;
    private int curScenarioID;
    private Bcsv.Entry curScenario;
    private String curZone;
    private StageArchive curZoneArc;
    
    private HashMap<String, GalaxyEditorForm> zoneEditors = new HashMap();
    private GalaxyEditorForm parentForm = null;
    private boolean unsavedChanges = false;
    private int zoneModeLayerBitmask;
    
    // Additional UI
    private CheckBoxList listLayerCheckboxes;
    private JPopupMenu popupAddItems;
    private PropertyGrid pnlObjectSettings;
    
    // Object holders
    private int maxUniqueID = 0;
    private final HashMap<Integer, AbstractObj> globalObjList = new HashMap();
    private final HashMap<Integer, PathObj> globalPathList = new HashMap();
    private final HashMap<Integer, PathPointObj> globalPathPointList = new HashMap();
    private final HashMap<Integer, AbstractObj> selectedObjs = new LinkedHashMap();
    private final HashMap<Integer, PathPointObj> displayedPaths = new LinkedHashMap();
    private final HashMap<String, StageObj> zonePlacements = new HashMap();
    private final HashMap<Integer, TreeNode> treeNodeList = new HashMap();
    
    // Object selection & settings
    private DefaultTreeModel objListModel;
    private final DefaultMutableTreeNode objListRootNode = new DefaultMutableTreeNode("dummy");
    private final HashMap<String, ObjListTreeNode> objListTreeNodes = new LinkedHashMap(11);
    private final ObjListTreeNode objListPathRootNode = new ObjListTreeNode("Paths");
    private String addingObject = "";
    private String addingObjectOnLayer = "";
    
    private static final Vec3f COPY_POSITION = new Vec3f(0f, 0f, 0f);
    private static final Vec3f COPY_ROTATION = new Vec3f(0f, 0f, 0f);
    private static final Vec3f COPY_SCALE = new Vec3f(1f, 1f, 1f);
    
    // Rendering
    private GalaxyRenderer renderer;
    private GLRenderer.RenderInfo renderInfo;
    private final HashMap<String, int[]> objDisplayLists = new HashMap();
    private final HashMap<Integer, int[]> zoneDisplayLists = new HashMap();
    private final Queue<String> rerenderTasks = new PriorityQueue();
    private GLCanvas glCanvas;
    private boolean initializedRenderer = false;
    
    // Camera & view
    private Matrix4 modelViewMatrix;
    private float camDistance = 1.0f;
    private final Vec2f camRotation = new Vec2f(0.0f, 0.0f);
    private final Vec3f camPosition = new Vec3f(0.0f, 0.0f, 0.0f);
    private Vec3f camTarget = new Vec3f(0.0f, 0.0f, 0.0f);
    private boolean isUpsideDown = false;
    
    // Controls
    private float pixelFactorX, pixelFactorY;
    private int mouseButton;
    private Point mousePos = new Point(-1, 1);
    private boolean isDragging = false;
    private boolean pickingCapture = false;
    private final IntBuffer pickingFrameBuffer = IntBuffer.allocate(9);
    private final FloatBuffer pickingDepthBuffer = FloatBuffer.allocate(1);
    private float pickingDepth = 1.0f;
    
    // Assorted
    private final Vec3f scratchVec = new Vec3f();
    
    private float g_move_x=0;
    private float g_move_y=0;
    private float g_move_z=0;
    private float g_move_step_x=0;
    private float g_move_step_y=0;
    private float g_move_step_z=0;
    private float g_move_step_a=0;
    private float g_center_x=0;
    private float g_center_y=0;
    private float g_center_z=0;
    private float g_angle_x=0;
    private float g_angle_y=0;
    private float g_angle_z=0;
    private float g_offset_x=0;
    private float g_offset_y=0;
    private float g_offset_z=0;
    
    private int underCursor = 0xFFFFFF;
    private float depthUnderCursor;
    private int selectionArg = 0;
    private boolean deletingObjects = false;
    
    public LinkedHashMap<Integer, AbstractObj> copyObj;
    public AbstractObj newobj; 
    public Point startingMousePos;
    
    public boolean keyScaling, keyTranslating, keyRotating, fullscreen = false;
    public String keyAxis = "all";
    
    public ArrayList<UndoEntry> undoList = new ArrayList();
    private int undoIndex = 0;
    private float lastDist;
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Constructors and GUI Setup
    
    public GalaxyEditorForm(String galaxy) {
        initComponents();
        
        galaxyName = galaxy;
        tabData.remove(1);
        
        try {
            // Preload all zones
            galaxyArchive = Whitehole.GAME.openGalaxy(galaxyName);
            zoneArchives = new HashMap(galaxyArchive.zoneList.size());
            
            for (String zone : galaxyArchive.zoneList) {
                loadZone(zone);
            }
            
            checkForInvalidSwitchesInGalaxy();
            
            // Collect zone placements
            StageArchive galaxyZone = zoneArchives.get(galaxyName);
            
            for (int i = 0 ; i < galaxyArchive.scenarioData.size() ; i++) {
                Bcsv.Entry scenarioEntry = galaxyArchive.scenarioData.get(i);
                int layerMask = scenarioEntry.getInt(galaxyName);
                
                if (galaxyZone.zones.containsKey("common")) {
                    for (StageObj zonePlacement : galaxyZone.zones.get("common")) {
                        String stageKey = String.format("%d/%s", i, zonePlacement.name);
                        
                        if (zonePlacements.containsKey(stageKey)) {
                            System.out.println("Warning! Skipping duplicate stage entry " + stageKey);
                            continue;
                        }
                        
                        zonePlacements.put(stageKey, zonePlacement);
                    }
                }
                
                for (int l = 0 ; l < 16 ; l++) {
                    if ((layerMask & (1 << l)) == 0) {
                        continue;
                    }
                    
                    String layerKey = "layer" + (char)('a' + l);
                    
                    if (!galaxyZone.zones.containsKey(layerKey)) {
                        continue;
                    }
                    
                    for (StageObj zonePlacement : galaxyZone.zones.get(layerKey)) {
                        String stageKey = String.format("%d/%s", i, zonePlacement.name);
                        
                        if (zonePlacements.containsKey(stageKey)) {
                            System.out.println("Warning! Skipping duplicate stage entry " + stageKey);
                            continue;
                        }
                        
                        zonePlacements.put(stageKey, zonePlacement);
                    }
                }
            }
            
            for (List<StageObj> placements : galaxyZone.zones.values()) {
                for (StageObj zonePlacement : placements) {
                    if (!zonePlacements.containsKey(zonePlacement.name)) {
                        zonePlacements.put(zonePlacement.name, zonePlacement);
                    }
                } 
            }
        }
        catch(IOException ex) {
            JOptionPane.showMessageDialog(null, "Failed to open the galaxy: " + ex.getMessage(), Whitehole.NAME, JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            dispose();
            return;
        }
        
        initObjectNodeTree();
        initGUI();
        
        // Load scenario information
        DefaultListModel scenlist = (DefaultListModel)listScenarios.getModel();

        for(Bcsv.Entry scen : galaxyArchive.scenarioData) {
            scenlist.addElement(String.format("[%1$d] %2$s", scen.getInt("ScenarioNo"), scen.getString("ScenarioName")));
        }
    }
    
    public GalaxyEditorForm(GalaxyEditorForm parent, StageArchive zoneArc) {
        initComponents();
        
        isGalaxyMode = false;
        parentForm = parent;
        galaxyName = zoneArc.stageName;
        
        zoneArchives = new HashMap(1);
        zoneArchives.put(galaxyName, zoneArc);
        loadZone(galaxyName);
        
        curZone = galaxyName;
        curZoneArc = zoneArchives.get(curZone);
        
        initObjectNodeTree();
        initGUI();
        
        populateObjectNodeTree(zoneModeLayerBitmask);
    }
    
    public void requestUpdateLAF() {
        SwingUtilities.updateComponentTreeUI(this);
        SwingUtilities.updateComponentTreeUI(pnlObjectSettings);
        SwingUtilities.updateComponentTreeUI(popupAddItems);
        
        // Does not affect the actual checkboxes yet... Investigate this.
        if (listLayerCheckboxes != null) {
            SwingUtilities.updateComponentTreeUI(listLayerCheckboxes);
        }
        
        for (GalaxyEditorForm subEditor : zoneEditors.values()) {
            subEditor.requestUpdateLAF();
        }
    }
    
    private void initGUI() {
        if (isGalaxyMode) {
            setTitle(GalaxyNames.getSimplifiedStageName(galaxyName) + " -- " + Whitehole.NAME);
        }
        else {
            setTitle(galaxyName + " -- " + Whitehole.NAME);
        }
        
        initAddObjectPopup();
        
        tgbShowAreas.setSelected(Settings.getShowAreas());
        tgbShowCameras.setSelected(Settings.getShowCameras());
        tgbShowGravity.setSelected(Settings.getShowGravity());
        tgbShowPaths.setSelected(Settings.getShowPaths());
        tgbShowAxis.setSelected(Settings.getShowAxis());
        
        // For now, hide these until their proper functions are added
        btnAddScenario.setVisible(false);
        btnEditScenario.setVisible(false);
        btnDeleteScenario.setVisible(false);
        btnAddZone.setVisible(false);
        btnDeleteZone.setVisible(false);
        sep3.setVisible(false);
        tgbShowGravity.setVisible(false);
        
        // Setup the actual preview canvas
        GLProfile prof = GLProfile.getMaxFixedFunc(true);
        GLCapabilities capabilities = new GLCapabilities(prof);
        capabilities.setSampleBuffers(true);
        capabilities.setNumSamples(8);
        capabilities.setHardwareAccelerated(true);
        capabilities.setDoubleBuffered(true);
        
        glCanvas = new GLCanvas(capabilities);
        
        if (RendererCache.refContext == null) {
            RendererCache.refContext = glCanvas.getContext();
        }
        else {
            glCanvas.setSharedContext(RendererCache.refContext);
        }
        
        renderer = new GalaxyEditorForm.GalaxyRenderer();
        glCanvas.addGLEventListener(renderer);
        glCanvas.addMouseListener(renderer);
        glCanvas.addMouseMotionListener(renderer);
        glCanvas.addMouseWheelListener(renderer);
        glCanvas.addKeyListener(renderer);
        
        pnlGLPanel.add(glCanvas, BorderLayout.CENTER);
        pnlGLPanel.validate();
        
        pnlObjectSettings = new PropertyGrid(this);
        scrObjSettings.setViewportView(pnlObjectSettings);
        scrObjSettings.getVerticalScrollBar().setUnitIncrement(16);
        pnlObjectSettings.setEventListener((String propname, Object value) -> {
            propertyPanelPropertyChanged(propname, value);
        });
        
        glCanvas.requestFocusInWindow();
        
        // Zone-only GUI stuff
        if (!isGalaxyMode) {
            tabData.remove(0);

            listLayerCheckboxes = new CheckBoxList();
            listLayerCheckboxes.setEventListener((index, status) -> layerSelectChange(index, status));
            scrLayers.setViewportView(listLayerCheckboxes);

            // Collect valid layer checkboxes
            zoneModeLayerBitmask = 0;
            JCheckBox[] layerCheckBoxes = new JCheckBox[curZoneArc.objects.keySet().size() - 1];
            int index = 0;

            for (int layerID = 0 ; layerID < 16 ; layerID++) {
                String layerName = "Layer" + (char)('A' + layerID);

                if (curZoneArc.objects.containsKey(layerName.toLowerCase())) {
                    layerCheckBoxes[index] = new JCheckBox(layerName);

                    if (index == 0) {
                        layerCheckBoxes[index].setSelected(true);
                        zoneModeLayerBitmask |= (1 << layerID);
                    }

                    index++;
                }
            }

            listLayerCheckboxes.setListData(layerCheckBoxes);
        }
    }
    
    private void initAddObjectPopup() {
        tlbObjects.validate();
        popupAddItems = new JPopupMenu();
        
        initAddObjectPopupItem("objinfo", "General");
        initAddObjectPopupItem("mappartsinfo", "MapPart");
        
        if (Whitehole.getCurrentGameType() == 1) {
            initAddObjectPopupItem("childobjinfo", "ChildObj");
        }
        
        initAddObjectPopupItem("planetobjinfo", "Gravity");
        initAddObjectPopupItem("areaobjinfo", "Area");
        initAddObjectPopupItem("cameracubeinfo", "Camera");
        
        if (Whitehole.getCurrentGameType() == 1) {
            initAddObjectPopupItem("soundinfo", "Sound");
        }
        
        initAddObjectPopupItem("startinfo", "Start");
        initAddObjectPopupItem("demoobjinfo", "Cutscene");
        initAddObjectPopupItem("generalposinfo", "Position");
        initAddObjectPopupItem("debugmoveinfo", "Debug");
        initAddObjectPopupItem("path", "Path");
        initAddObjectPopupItem("pathpoint", "Path Point");
        
        popupAddItems.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if(addingObject.isEmpty()) {
                    tgbAddObject.setSelected(false);
                }
                else {
                    setDefaultStatus();
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                if(addingObject.isEmpty()) {
                    tgbAddObject.setSelected(false);
                }
                else {
                    setDefaultStatus();
                }
            }
        });
    }
    
    private void initAddObjectPopupItem(String key, String desc) {
        JMenuItem menuItem = new JMenuItem(desc);
        menuItem.addActionListener((ActionEvent e) -> setObjectBeingAdded(key));
        popupAddItems.add(menuItem);
    }
    
    private void setDefaultStatus() {
        if (isGalaxyMode) {
            lblStatus.setText("Editing scenario " + listScenarios.getSelectedValue() + ", zone " + curZone + ".");
        }
        else {
            lblStatus.setText("Editing zone " + curZone + ".");
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Zone loading and saving
    
    private void openSelectedZone() {
        if (zoneEditors.containsKey(curZone)) {
            if (!zoneEditors.get(curZone).isVisible()) {
                zoneEditors.remove(curZone);
            }
            else {
                zoneEditors.get(curZone).toFront();
                return;
            }
        }
        
        GalaxyEditorForm form = new GalaxyEditorForm(this, curZoneArc);
        form.setVisible(true);
        zoneEditors.put(curZone, form);
    }
    
    private void loadZone(String zone) {
        // Load zone archive
        StageArchive arc;
        
        if(isGalaxyMode) {
            arc = galaxyArchive.openZone(zone);
            zoneArchives.put(zone, arc);
        }
        else {
            arc = zoneArchives.get(zone);
        }
        
        // Populate objects and assign their maxUniqueIDs
        for (List<AbstractObj> layers : arc.objects.values()) {
            if (isGalaxyMode) {
                for (AbstractObj obj : layers) {
                    obj.uniqueID = maxUniqueID;
                    globalObjList.put(maxUniqueID++, obj);
                }
            }
            else {
                for (AbstractObj obj : layers) {
                    globalObjList.put(obj.uniqueID, obj);
                }
            }
        }
        
        // Populate paths and assign their maxUniqueIDs
        for (PathObj pathObj : arc.paths) {
            pathObj.uniqueID = maxUniqueID;
            globalPathList.put(maxUniqueID++, pathObj);
            
            for (PathPointObj pointObj : pathObj.getPoints()) {
                globalObjList.put(maxUniqueID, pointObj);
                globalPathPointList.put(maxUniqueID, pointObj);
                pointObj.uniqueID = maxUniqueID;
                maxUniqueID++;
            }
        }
    }
    
    private void checkForInvalidSwitchesInGalaxy() {
        Map<String, Set<Integer>> invalidSwitchZoneMap = new HashMap<>();
        Set<Integer> invalidSwitchList = new HashSet<>();
        String[] switchFieldList = {"SW_APPEAR", "SW_DEAD", "SW_A", "SW_B", "SW_AWAKE", "SW_PARAM", "SW_SLEEP"};
        
        // Generate a list and map of invalid switch IDs
        for (StageArchive arc : zoneArchives.values()) {
            // Add zone to map
            invalidSwitchZoneMap.put(arc.stageName, new HashSet<>());
            
            // Go through each object's switch fields
            for (List<AbstractObj> layers : arc.objects.values()) {
                for (AbstractObj obj : layers) {
                    for (String field : switchFieldList) {
                        int switchId = obj.data.getInt(field, -1);
                        
                        // Only valid Switch IDs: -1 (No Switch); 0-127 (Zone Exclusive); 1000-1127 (Galaxy Exclusive)
                        if (switchId !=-1 && !(switchId >= 0 && switchId <=127) && !(switchId >=1000 && switchId <= 1127)) {
                            invalidSwitchZoneMap.get(arc.stageName).add(switchId);
                            invalidSwitchList.add(switchId);
                        }
                    }
                }
            }
        }
        
        // Go through each invalid switch and replace it
        for (int invalidSwitch : invalidSwitchList) {
            ArrayList<String> switchZoneAppearances = new ArrayList<>();
            String additionalInfo = "";

            // Generate a list of zones the switch appears in
            for (String zone : invalidSwitchZoneMap.keySet()) {
                if (invalidSwitchZoneMap.get(zone).contains(invalidSwitch))
                    switchZoneAppearances.add(zone);
            }

            // If the switch ID is used across multiple zones, inform the user
            if (switchZoneAppearances.size() >= 2)
                additionalInfo = "\n(!) This switch ID is used across multiple zones.";
            
            // Generate the UI
            String[] choices = {"Zone", "Galaxy", "Don't replace"};
            int choice = JOptionPane.showOptionDialog(null, "An invalid switch ID was found: "+invalidSwitch+additionalInfo+
                "\nGenerate new switch for:", "Invalid Switch ID found!",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, choices, null);
            
            if (choice==-1)
                choice = 2;
            
            // Generate and replace the switch based on their choice
            int replSwitchID;
            switch (choices[choice]) {
            case "Zone":
                for (String zoneString : switchZoneAppearances) {
                    replSwitchID = getValidSwitchInZone(zoneArchives.get(zoneString)); // Generate
                    
                    if (replSwitchID !=-1)
                        zoneArchives = replaceSwitchID(invalidSwitch, replSwitchID, zoneArchives, zoneString); // Replace
                }
                unsavedChanges = true;
                break;

            case "Galaxy":
                replSwitchID = getValidSwitchInGalaxy(zoneArchives); // Generate
                
                if (replSwitchID !=-1)
                    zoneArchives = replaceSwitchID(invalidSwitch, replSwitchID, zoneArchives); // Replace
                unsavedChanges = true;
                break;
            }
        }
    }

    private void saveChanges() {
        lblStatus.setText("Saving changes...");
        
        try {
            for (StageArchive stageArc : zoneArchives.values()) {
                stageArc.save();
            }
            
            // Update main editor from subzone
            if(!isGalaxyMode && parentForm != null) {
                parentForm.updateZone(galaxyName);
            }
            // Update subzone editors from main editor
            else {
                for (GalaxyEditorForm form : zoneEditors.values()) {
                    form.updateZone(form.galaxyName);
                }
            }
            
            unsavedChanges = false;
            lblStatus.setText("Saved changes!");
        }
        catch(IOException ex) {
            lblStatus.setText("Failed to save changes: " + ex.getMessage() + ".");
            System.err.println(ex);
        }
    }
    
    private void closeEditor() {
        if (isGalaxyMode) {
            for (GalaxyEditorForm form : zoneEditors.values()) {
                form.dispose();
            }
            
            // Save renderer preferences
            Settings.setShowAreas(tgbShowAreas.isSelected());
            Settings.setShowCameras(tgbShowCameras.isSelected());
            Settings.setShowGravity(tgbShowGravity.isSelected());
            Settings.setShowPaths(tgbShowPaths.isSelected());
            Settings.setShowAxis(tgbShowAxis.isSelected());
            
            if(unsavedChanges) {
                // Should we really scrap our changes?
                int res = JOptionPane.showConfirmDialog(
                        this, "Save your changes?", Whitehole.NAME,
                        JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE
                );

                switch (res) {
                    case JOptionPane.CANCEL_OPTION:
                        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                        return;
                    case JOptionPane.YES_OPTION:
                        if (res == JOptionPane.YES_OPTION) {
                            saveChanges();
                        }
                    default:
                        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        break;
                }
            }
            
            // Close stages
            for (StageArchive stageArc : zoneArchives.values()) {
                stageArc.close();
            }
        }
        else {
            if (unsavedChanges) {
                parentForm.unsavedChanges = true;
            }
        }
    }
    
    private AbstractObj getScenarioStart() {
        if (!isGalaxyMode) {
            return null;
        }
        
        AbstractObj ret = getScenarioStartInLayer(zoneArchives.get(galaxyName).objects.get("common"));
        
        if (ret != null) {
            return ret;
        }
        
        
        int layerMask = curScenario.getInt(galaxyName, NORMAL);
        
        for (int layerID = 0 ; layerID < 16 ; layerID++) {
            if ((layerMask & (1 << layerID)) == 0) {
                continue;
            }
            
            String layerKey = "layer" + (char)('a' + layerID);
            ret = getScenarioStartInLayer(zoneArchives.get(galaxyName).objects.get(layerKey));
            
            if (ret != null) {
                return ret;
            }
        }
        
        return null;
    }
    
    private AbstractObj getScenarioStartInLayer(List<AbstractObj> objects) {
        for (AbstractObj obj : objects) {
            if (!(obj instanceof StartObj)) {
                continue;
            }
            
            if (obj.data.getInt("MarioNo", -1) == 0) {
                return obj;
            }
        }
        
        return null;
    }
    
    private void updateZone(String zone) {
        rerenderTasks.add("zone:" + zone);
        glCanvas.repaint();
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Object nodes and layer presentation
    
    private void initObjectNodeTree() {
        objListModel = (DefaultTreeModel)treeObjects.getModel();
        objListModel.setRoot(objListRootNode);
        
        objListTreeNodes.put("objinfo", new ObjListTreeNode("General"));
        objListTreeNodes.put("mappartsinfo", new ObjListTreeNode("MapParts"));
        
        if (Whitehole.getCurrentGameType() == 1) {
            objListTreeNodes.put("childobjinfo", new ObjListTreeNode("ChildObjs"));
        }
        
        objListTreeNodes.put("planetobjinfo", new ObjListTreeNode("Gravities"));
        objListTreeNodes.put("areaobjinfo", new ObjListTreeNode("Areas"));
        objListTreeNodes.put("cameracubeinfo", new ObjListTreeNode("Cameras"));
        
        /*if (Whitehole.getCurrentGameType() == 2) {
            objListTreeNodes.put("design_areaobjinfo", new ObjListTreeNode("Light Areas"));
            objListTreeNodes.put("sound_areaobjinfo", new ObjListTreeNode("Sound Areas"));
            objListTreeNodes.put("sound_objinfo", new ObjListTreeNode("Sound Objects"));
        }*/
        
        if (Whitehole.getCurrentGameType() == 1) {
            objListTreeNodes.put("soundinfo", new ObjListTreeNode("Sounds"));
        }
        
        objListTreeNodes.put("startinfo", new ObjListTreeNode("Spawns"));
        objListTreeNodes.put("demoobjinfo", new ObjListTreeNode("Cutscenes"));
        objListTreeNodes.put("generalposinfo", new ObjListTreeNode("Positions"));
        objListTreeNodes.put("debugmoveinfo", new ObjListTreeNode("Debug"));
    }
    
    private void populateObjectNodeTree(int layerMask) {
        treeNodeList.clear();
        objListRootNode.setUserObject(curZone);
        objListRootNode.removeAllChildren();
        
        // Populate objects
        for (Map.Entry<String, ObjListTreeNode> entry : objListTreeNodes.entrySet()) {
            String key = entry.getKey();
            ObjListTreeNode node = entry.getValue();
            node.removeAllChildren();
            objListRootNode.add(node);
            
            for (List<AbstractObj> layer : curZoneArc.objects.values()) {
                for (AbstractObj obj : layer) {
                    if (!obj.getFileType().equals(key)) {
                        continue;
                    }
                    if (!obj.layerKey.equals("common")) {
                        int layerID = obj.layerKey.charAt(5) - 'a';

                        if ((layerMask & (1 << layerID)) == 0) {
                            continue;
                        }
                    }

                    ObjTreeNode objnode = node.addObject(obj);
                    treeNodeList.put(obj.uniqueID, objnode);
                }
            }
        }
        
        // Populate paths
        objListRootNode.add(objListPathRootNode);
        
        for (PathObj obj : curZoneArc.paths) {
            ObjListTreeNode node = (ObjListTreeNode)objListPathRootNode.addObject(obj);
            treeNodeList.put(obj.uniqueID, node);
            
            for (Map.Entry<Integer, ObjTreeNode> pathPointNode : node.children.entrySet()) {
                treeNodeList.put(pathPointNode.getKey(), pathPointNode.getValue());
            }
        }
        
        objListModel.reload();
        treeObjects.expandRow(0);
    }
    
    private void layerSelectChange(int index, boolean status) {
        String layerName = ((JCheckBox)listLayerCheckboxes.getModel().getElementAt(index)).getText();
        int layerMask = (1 << (layerName.charAt(5) - 'A'));
        
        if (status) {
            zoneModeLayerBitmask |= layerMask;
        }
        else {
            zoneModeLayerBitmask &= ~layerMask;
        }
        
        populateObjectNodeTree(zoneModeLayerBitmask);
        rerenderTasks.add("allobjects:");
        glCanvas.repaint();
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content
     * of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        split = new javax.swing.JSplitPane();
        pnlGLPanel = new javax.swing.JPanel();
        tlbOptions = new javax.swing.JToolBar();
        tgbDeselect = new javax.swing.JButton();
        sep1 = new javax.swing.JToolBar.Separator();
        tgbShowPaths = new javax.swing.JToggleButton();
        sep2 = new javax.swing.JToolBar.Separator();
        tgbShowAreas = new javax.swing.JToggleButton();
        sep3 = new javax.swing.JToolBar.Separator();
        tgbShowGravity = new javax.swing.JToggleButton();
        sep4 = new javax.swing.JToolBar.Separator();
        tgbShowCameras = new javax.swing.JToggleButton();
        sep5 = new javax.swing.JToolBar.Separator();
        tgbShowAxis = new javax.swing.JToggleButton();
        lblStatus = new javax.swing.JLabel();
        tabData = new javax.swing.JTabbedPane();
        pnlScenarioZone = new javax.swing.JSplitPane();
        pnlScenarios = new javax.swing.JPanel();
        tlbScenarios = new javax.swing.JToolBar();
        lblScenarios = new javax.swing.JLabel();
        btnAddScenario = new javax.swing.JButton();
        btnEditScenario = new javax.swing.JButton();
        btnDeleteScenario = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        listScenarios = new javax.swing.JList();
        pnlZones = new javax.swing.JPanel();
        tlbZones = new javax.swing.JToolBar();
        lblZones = new javax.swing.JLabel();
        btnAddZone = new javax.swing.JButton();
        btnDeleteZone = new javax.swing.JButton();
        sepZones = new javax.swing.JToolBar.Separator();
        btnEditZone = new javax.swing.JButton();
        scrZones = new javax.swing.JScrollPane();
        listZones = new javax.swing.JList();
        pnlLayers = new javax.swing.JPanel();
        tlbLayers = new javax.swing.JToolBar();
        jLabel1 = new javax.swing.JLabel();
        scrLayers = new javax.swing.JScrollPane();
        scrObjects = new javax.swing.JSplitPane();
        pnlObjects = new javax.swing.JPanel();
        tlbObjects = new javax.swing.JToolBar();
        tgbAddObject = new javax.swing.JToggleButton();
        jSeparator4 = new javax.swing.JToolBar.Separator();
        tgbDeleteObject = new javax.swing.JToggleButton();
        jSeparator5 = new javax.swing.JToolBar.Separator();
        tgbCopyObj = new javax.swing.JToggleButton();
        jSeparator10 = new javax.swing.JToolBar.Separator();
        tgbPasteObj = new javax.swing.JToggleButton();
        scrObjectTree = new javax.swing.JScrollPane();
        treeObjects = new javax.swing.JTree();
        scrObjSettings = new javax.swing.JScrollPane();
        menu = new javax.swing.JMenuBar();
        mnuFile = new javax.swing.JMenu();
        mniSave = new javax.swing.JMenuItem();
        mniClose = new javax.swing.JMenuItem();
        mnuEdit = new javax.swing.JMenu();
        mnuCopy = new javax.swing.JMenu();
        itmPositionCopy = new javax.swing.JMenuItem();
        itmRotationCopy = new javax.swing.JMenuItem();
        itmScaleCopy = new javax.swing.JMenuItem();
        mnuPaste = new javax.swing.JMenu();
        itmPositionPaste = new javax.swing.JMenuItem();
        itmRotationPaste = new javax.swing.JMenuItem();
        itmScalePaste = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(Whitehole.NAME);
        setIconImage(Whitehole.ICON);
        setMinimumSize(new java.awt.Dimension(960, 720));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        split.setDividerLocation(335);
        split.setFocusable(false);

        pnlGLPanel.setMinimumSize(new java.awt.Dimension(10, 30));
        pnlGLPanel.setLayout(new java.awt.BorderLayout());

        tlbOptions.setFloatable(false);
        tlbOptions.setRollover(true);

        tgbDeselect.setText("Deselect");
        tgbDeselect.setEnabled(false);
        tgbDeselect.setFocusable(false);
        tgbDeselect.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbDeselect.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbDeselect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbDeselectActionPerformed(evt);
            }
        });
        tlbOptions.add(tgbDeselect);
        tlbOptions.add(sep1);

        tgbShowPaths.setSelected(true);
        tgbShowPaths.setText("Show paths");
        tgbShowPaths.setFocusable(false);
        tgbShowPaths.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbShowPaths.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbShowPaths.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbShowPathsActionPerformed(evt);
            }
        });
        tlbOptions.add(tgbShowPaths);
        tlbOptions.add(sep2);

        tgbShowAreas.setSelected(true);
        tgbShowAreas.setText("Show areas");
        tgbShowAreas.setFocusable(false);
        tgbShowAreas.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbShowAreas.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbShowAreas.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbShowAreasActionPerformed(evt);
            }
        });
        tlbOptions.add(tgbShowAreas);
        tlbOptions.add(sep3);

        tgbShowGravity.setSelected(true);
        tgbShowGravity.setText("Show gravity");
        tgbShowGravity.setFocusable(false);
        tgbShowGravity.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbShowGravity.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbShowGravity.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbShowGravityActionPerformed(evt);
            }
        });
        tlbOptions.add(tgbShowGravity);
        tlbOptions.add(sep4);

        tgbShowCameras.setSelected(true);
        tgbShowCameras.setText("Show cameras");
        tgbShowCameras.setFocusable(false);
        tgbShowCameras.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbShowCameras.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbShowCameras.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbShowCamerasActionPerformed(evt);
            }
        });
        tlbOptions.add(tgbShowCameras);
        tlbOptions.add(sep5);

        tgbShowAxis.setSelected(true);
        tgbShowAxis.setText("Show axis");
        tgbShowAxis.setFocusable(false);
        tgbShowAxis.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbShowAxis.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbShowAxis.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbShowAxisActionPerformed(evt);
            }
        });
        tlbOptions.add(tgbShowAxis);

        pnlGLPanel.add(tlbOptions, java.awt.BorderLayout.NORTH);

        lblStatus.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        lblStatus.setText("Booting...");
        pnlGLPanel.add(lblStatus, java.awt.BorderLayout.PAGE_END);

        split.setRightComponent(pnlGLPanel);

        tabData.setMinimumSize(new java.awt.Dimension(100, 5));
        tabData.setName(""); // NOI18N

        pnlScenarioZone.setDividerLocation(200);
        pnlScenarioZone.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        pnlScenarioZone.setLastDividerLocation(200);

        pnlScenarios.setPreferredSize(new java.awt.Dimension(201, 200));
        pnlScenarios.setLayout(new java.awt.BorderLayout());

        tlbScenarios.setFloatable(false);
        tlbScenarios.setRollover(true);

        lblScenarios.setText("Scenarios:");
        tlbScenarios.add(lblScenarios);

        btnAddScenario.setText("Add");
        btnAddScenario.setFocusable(false);
        btnAddScenario.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnAddScenario.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnAddScenario.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddScenarioActionPerformed(evt);
            }
        });
        tlbScenarios.add(btnAddScenario);

        btnEditScenario.setText("Edit");
        btnEditScenario.setFocusable(false);
        btnEditScenario.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnEditScenario.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnEditScenario.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEditScenarioActionPerformed(evt);
            }
        });
        tlbScenarios.add(btnEditScenario);

        btnDeleteScenario.setText("Delete");
        btnDeleteScenario.setFocusable(false);
        btnDeleteScenario.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnDeleteScenario.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnDeleteScenario.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteScenarioActionPerformed(evt);
            }
        });
        tlbScenarios.add(btnDeleteScenario);

        pnlScenarios.add(tlbScenarios, java.awt.BorderLayout.PAGE_START);

        listScenarios.setModel(new DefaultListModel());
        listScenarios.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        listScenarios.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                listScenariosValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(listScenarios);

        pnlScenarios.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        pnlScenarioZone.setTopComponent(pnlScenarios);

        pnlZones.setLayout(new java.awt.BorderLayout());

        tlbZones.setBorder(null);
        tlbZones.setFloatable(false);
        tlbZones.setRollover(true);

        lblZones.setText(" Zones: ");
        tlbZones.add(lblZones);

        btnAddZone.setText("Add");
        btnAddZone.setFocusable(false);
        btnAddZone.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnAddZone.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnAddZone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddZoneActionPerformed(evt);
            }
        });
        tlbZones.add(btnAddZone);

        btnDeleteZone.setText("Delete");
        btnDeleteZone.setFocusable(false);
        btnDeleteZone.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnDeleteZone.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnDeleteZone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteZoneActionPerformed(evt);
            }
        });
        tlbZones.add(btnDeleteZone);
        tlbZones.add(sepZones);

        btnEditZone.setText("Edit individually");
        btnEditZone.setFocusable(false);
        btnEditZone.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnEditZone.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnEditZone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEditZoneActionPerformed(evt);
            }
        });
        tlbZones.add(btnEditZone);

        pnlZones.add(tlbZones, java.awt.BorderLayout.PAGE_START);

        listZones.setModel(new DefaultListModel());
        listZones.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        listZones.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listZonesMouseClicked(evt);
            }
        });
        listZones.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                listZonesValueChanged(evt);
            }
        });
        scrZones.setViewportView(listZones);

        pnlZones.add(scrZones, java.awt.BorderLayout.CENTER);

        pnlScenarioZone.setRightComponent(pnlZones);

        tabData.addTab("Scenario/Zone", pnlScenarioZone);

        pnlLayers.setLayout(new java.awt.BorderLayout());

        tlbLayers.setFloatable(false);
        tlbLayers.setRollover(true);

        jLabel1.setText("Layers:");
        tlbLayers.add(jLabel1);

        pnlLayers.add(tlbLayers, java.awt.BorderLayout.PAGE_START);
        pnlLayers.add(scrLayers, java.awt.BorderLayout.CENTER);

        tabData.addTab("Layers", pnlLayers);

        scrObjects.setDividerLocation(300);
        scrObjects.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        scrObjects.setResizeWeight(0.5);
        scrObjects.setFocusCycleRoot(true);
        scrObjects.setLastDividerLocation(300);

        pnlObjects.setPreferredSize(new java.awt.Dimension(149, 300));
        pnlObjects.setLayout(new java.awt.BorderLayout());

        tlbObjects.setFloatable(false);
        tlbObjects.setRollover(true);

        tgbAddObject.setText("Add object");
        tgbAddObject.setFocusable(false);
        tgbAddObject.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbAddObject.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbAddObject.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbAddObjectActionPerformed(evt);
            }
        });
        tlbObjects.add(tgbAddObject);
        tlbObjects.add(jSeparator4);

        tgbDeleteObject.setText("Delete object");
        tgbDeleteObject.setFocusable(false);
        tgbDeleteObject.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbDeleteObject.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbDeleteObject.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbDeleteObjectActionPerformed(evt);
            }
        });
        tlbObjects.add(tgbDeleteObject);
        tlbObjects.add(jSeparator5);

        tgbCopyObj.setText("Copy");
        tgbCopyObj.setFocusable(false);
        tgbCopyObj.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbCopyObj.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbCopyObj.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbCopyObjActionPerformed(evt);
            }
        });
        tlbObjects.add(tgbCopyObj);
        tlbObjects.add(jSeparator10);

        tgbPasteObj.setText("Paste");
        tgbPasteObj.setFocusable(false);
        tgbPasteObj.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tgbPasteObj.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tgbPasteObj.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tgbPasteObjActionPerformed(evt);
            }
        });
        tlbObjects.add(tgbPasteObj);

        pnlObjects.add(tlbObjects, java.awt.BorderLayout.PAGE_START);

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        treeObjects.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        treeObjects.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
            public void valueChanged(javax.swing.event.TreeSelectionEvent evt) {
                treeObjectsValueChanged(evt);
            }
        });
        scrObjectTree.setViewportView(treeObjects);

        pnlObjects.add(scrObjectTree, java.awt.BorderLayout.CENTER);

        scrObjects.setTopComponent(pnlObjects);
        scrObjects.setRightComponent(scrObjSettings);

        tabData.addTab("Objects", scrObjects);

        split.setTopComponent(tabData);
        tabData.getAccessibleContext().setAccessibleDescription("");

        getContentPane().add(split, java.awt.BorderLayout.CENTER);

        mnuFile.setText("File");

        mniSave.setAccelerator(Settings.getUseWASD() ? null : KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK)
        );
        mniSave.setText("Save");
        mniSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mniSaveActionPerformed(evt);
            }
        });
        mnuFile.add(mniSave);

        mniClose.setText("Close");
        mniClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mniCloseActionPerformed(evt);
            }
        });
        mnuFile.add(mniClose);

        menu.add(mnuFile);

        mnuEdit.setText("Edit");
        mnuEdit.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                mnuEditMenuSelected(evt);
            }
        });

        mnuCopy.setText("Copy");

        itmPositionCopy.setAccelerator(KeyStroke.getKeyStroke(Settings.getKeyPosition(), ActionEvent.ALT_MASK)
        );
        itmPositionCopy.setText("Position");
        itmPositionCopy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmPositionCopyActionPerformed(evt);
            }
        });
        mnuCopy.add(itmPositionCopy);

        itmRotationCopy.setAccelerator(KeyStroke.getKeyStroke(Settings.getKeyRotation(), ActionEvent.ALT_MASK));
        itmRotationCopy.setText("Rotation");
        itmRotationCopy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmRotationCopyActionPerformed(evt);
            }
        });
        mnuCopy.add(itmRotationCopy);

        itmScaleCopy.setAccelerator(KeyStroke.getKeyStroke(Settings.getKeyScale(), ActionEvent.ALT_MASK));
        itmScaleCopy.setText("Scale");
        itmScaleCopy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmScaleCopyActionPerformed(evt);
            }
        });
        mnuCopy.add(itmScaleCopy);

        mnuEdit.add(mnuCopy);

        mnuPaste.setText("Paste");

        itmPositionPaste.setAccelerator(KeyStroke.getKeyStroke(Settings.getKeyPosition(), ActionEvent.SHIFT_MASK));
        itmPositionPaste.setText("Position (0.0, 0.0, 0.0)");
        itmPositionPaste.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmPositionPasteActionPerformed(evt);
            }
        });
        mnuPaste.add(itmPositionPaste);

        itmRotationPaste.setAccelerator(KeyStroke.getKeyStroke(Settings.getKeyRotation(), ActionEvent.SHIFT_MASK));
        itmRotationPaste.setText("Rotation (0.0, 0.0, 0.0)");
        itmRotationPaste.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmRotationPasteActionPerformed(evt);
            }
        });
        mnuPaste.add(itmRotationPaste);

        itmScalePaste.setAccelerator(KeyStroke.getKeyStroke(Settings.getKeyScale(), ActionEvent.SHIFT_MASK));
        itmScalePaste.setText("Scale (1.0, 1.0, 1.0)");
        itmScalePaste.setToolTipText("");
        itmScalePaste.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmScalePasteActionPerformed(evt);
            }
        });
        mnuPaste.add(itmScalePaste);

        mnuEdit.add(mnuPaste);

        menu.add(mnuEdit);

        setJMenuBar(menu);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        if (isGalaxyMode) {
            listScenarios.setSelectedIndex(0);
        }
    }//GEN-LAST:event_formWindowOpened
    
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        closeEditor();
    }//GEN-LAST:event_formWindowClosing

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        renderAllObjects();
    }//GEN-LAST:event_formWindowActivated

    private void mniSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mniSaveActionPerformed
        saveChanges();
    }//GEN-LAST:event_mniSaveActionPerformed

    private void mniCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mniCloseActionPerformed
        closeEditor();
        dispose();
    }//GEN-LAST:event_mniCloseActionPerformed

    private void mnuEditMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_mnuEditMenuSelected
        itmPositionPaste.setText(String.format("Position (%s)", COPY_POSITION.toString()));
        itmRotationPaste.setText(String.format("Rotation (%s)", COPY_ROTATION.toString()));
        itmScalePaste.setText(String.format("Scale (%s)", COPY_SCALE.toString()));
    }//GEN-LAST:event_mnuEditMenuSelected

    private void itmPositionCopyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmPositionCopyActionPerformed
        if (selectedObjs.size() == 1) {
            AbstractObj obj = selectedObjs.values().iterator().next();
            COPY_POSITION.set(obj.position);
            
            String posString = COPY_POSITION.toString();
            itmPositionPaste.setText(String.format("Position (%s)", posString));
            lblStatus.setText(String.format("Copied position %s.", posString));
        }
    }//GEN-LAST:event_itmPositionCopyActionPerformed
    
    private void itmRotationCopyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmRotationCopyActionPerformed
        if (selectedObjs.size() == 1) {
            AbstractObj obj = selectedObjs.values().iterator().next();
            COPY_ROTATION.set(obj.rotation);
            
            String rotString = COPY_ROTATION.toString();
            itmRotationPaste.setText(String.format("Rotation (%s)", rotString));
            lblStatus.setText(String.format("Copied rotation %s.", rotString));
        }
    }//GEN-LAST:event_itmRotationCopyActionPerformed

    private void itmScaleCopyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmScaleCopyActionPerformed
        if (selectedObjs.size() == 1) {
            AbstractObj obj = selectedObjs.values().iterator().next();
            COPY_SCALE.set(obj.scale);
            
            String scaleString = COPY_SCALE.toString();
            itmScalePaste.setText(String.format("Scale (%s)", scaleString));
            lblStatus.setText(String.format("Copied scale %s.", scaleString));
        }
    }//GEN-LAST:event_itmScaleCopyActionPerformed

    private void itmPositionPasteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmPositionPasteActionPerformed
        Vec3f offset = new Vec3f();
        
        for (AbstractObj obj : selectedObjs.values()) {
            if (obj instanceof PathPointObj) {
                PathPointObj pointObj = (PathPointObj)obj;
                offset.subtract(COPY_POSITION, pointObj.position);
                
                pointObj.position.set(COPY_POSITION);
                pointObj.point1.add(offset);
                pointObj.point2.add(offset);
                
                pnlObjectSettings.setFieldValue("pnt0_x", pointObj.position.x);
                pnlObjectSettings.setFieldValue("pnt0_y", pointObj.position.y);
                pnlObjectSettings.setFieldValue("pnt0_z", pointObj.position.z);
                pnlObjectSettings.setFieldValue("pnt1_x", pointObj.point1.x);
                pnlObjectSettings.setFieldValue("pnt1_y", pointObj.point1.y);
                pnlObjectSettings.setFieldValue("pnt1_z", pointObj.point1.z);
                pnlObjectSettings.setFieldValue("pnt2_x", pointObj.point2.x);
                pnlObjectSettings.setFieldValue("pnt2_y", pointObj.point2.y);
                pnlObjectSettings.setFieldValue("pnt2_z", pointObj.point2.z);
                pnlObjectSettings.repaint();
                
                rerenderTasks.add("path:" + pointObj.path.uniqueID);
                rerenderTasks.add("zone:" + pointObj.stage.stageName);
            }
            else {
                addUndoEntry("changeObj", obj);
                
                obj.position.set(COPY_POSITION);
                pnlObjectSettings.setFieldValue("pos_x", obj.position.x);
                pnlObjectSettings.setFieldValue("pos_y", obj.position.y);
                pnlObjectSettings.setFieldValue("pos_z", obj.position.z);
                pnlObjectSettings.repaint();
                
                rerenderTasks.add("object:" + obj.uniqueID);
                rerenderTasks.add("zone:" + obj.stage.stageName);
            }
            
            lblStatus.setText(String.format("Pasted position %s.", COPY_POSITION.toString()));
            
            glCanvas.repaint();
            unsavedChanges = true;
        }
    }//GEN-LAST:event_itmPositionPasteActionPerformed

    private void itmRotationPasteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmRotationPasteActionPerformed
        for (AbstractObj obj : selectedObjs.values()) {
            if (obj instanceof PathPointObj) {
                return;
            }
            
            addUndoEntry("changeObj", obj);
            
            obj.rotation.set(COPY_ROTATION);
            pnlObjectSettings.setFieldValue("dir_x", obj.rotation.x);
            pnlObjectSettings.setFieldValue("dir_y", obj.rotation.y);
            pnlObjectSettings.setFieldValue("dir_z", obj.rotation.z);
            pnlObjectSettings.repaint();
            
            rerenderTasks.add("object:" + obj.uniqueID);
            rerenderTasks.add("zone:" + obj.stage.stageName);
            
            lblStatus.setText(String.format("Pasted rotation %s.", COPY_ROTATION.toString()));
            
            glCanvas.repaint();
            unsavedChanges = true;
        }
    }//GEN-LAST:event_itmRotationPasteActionPerformed

    private void itmScalePasteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmScalePasteActionPerformed
        for (AbstractObj obj : selectedObjs.values()) {
            if (obj instanceof PathPointObj || obj instanceof PositionObj || obj instanceof StageObj) {
                return;
            }
            
            addUndoEntry("changeObj", obj);
            
            obj.scale.set(COPY_SCALE);
            pnlObjectSettings.setFieldValue("scale_x", obj.scale.x);
            pnlObjectSettings.setFieldValue("scale_y", obj.scale.y);
            pnlObjectSettings.setFieldValue("scale_z", obj.scale.z);
            pnlObjectSettings.repaint();
            
            rerenderTasks.add("object:" + obj.uniqueID);
            rerenderTasks.add("zone:" + obj.stage.stageName);
            
            lblStatus.setText(String.format("Pasted scale %s.", COPY_SCALE.toString()));
            
            glCanvas.repaint();
            unsavedChanges = true;
        }
    }//GEN-LAST:event_itmScalePasteActionPerformed

    private void listScenariosValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_listScenariosValueChanged
        if (evt.getValueIsAdjusting() || listScenarios.getSelectedValue() == null) {
            return;
        }

        curScenarioID = listScenarios.getSelectedIndex();
        curScenario = galaxyArchive.scenarioData.get(curScenarioID);

        DefaultListModel zonelist = (DefaultListModel)listZones.getModel();
        zonelist.removeAllElements();
        
        for(String zone : galaxyArchive.zoneList) {
            int layermask = curScenario.getInt(zone);
            String layers = "Common+";
            
            for (int i = 0 ; i < 16 ; i++) {
                if ((layermask & (1 << i)) != 0) {
                    layers += (char)('A' + i);
                }
            }
            
            if (layers.equals("Common+")) {
                layers = "Common";
            }
            
            zonelist.addElement(zone + " [" + layers + "]");
        }
        
        listZones.setSelectedIndex(0);
    }//GEN-LAST:event_listScenariosValueChanged

    private void btnAddScenarioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddScenarioActionPerformed
        // TODO: what the peck is this?
    }//GEN-LAST:event_btnAddScenarioActionPerformed

    private void btnDeleteScenarioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteScenarioActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnDeleteScenarioActionPerformed

    private void btnEditScenarioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEditScenarioActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnEditScenarioActionPerformed

    private void listZonesValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_listZonesValueChanged
        if (evt.getValueIsAdjusting() || listZones.getSelectedValue() == null) {
            return;
        }
        
        btnEditZone.setEnabled(true);
        
        curZone = galaxyArchive.zoneList.get(listZones.getSelectedIndex());
        curZoneArc = zoneArchives.get(curZone);
        
        populateObjectNodeTree(curScenario.getInt(curZone));
        
        setDefaultStatus();
        glCanvas.repaint();
    }//GEN-LAST:event_listZonesValueChanged

    private void btnAddZoneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddZoneActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnAddZoneActionPerformed

    private void btnDeleteZoneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteZoneActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnDeleteZoneActionPerformed

    private void btnEditZoneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEditZoneActionPerformed
        openSelectedZone();
    }//GEN-LAST:event_btnEditZoneActionPerformed

    private void tgbDeselectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbDeselectActionPerformed
        for (AbstractObj obj : selectedObjs.values()) {
            addRerenderTask("zone:" + obj.stage.stageName);
        }
        
        selectedObjs.clear();
        selectionChanged();
        glCanvas.repaint();
    }//GEN-LAST:event_tgbDeselectActionPerformed

    private void tgbShowPathsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbShowPathsActionPerformed
        renderAllObjects();
    }//GEN-LAST:event_tgbShowPathsActionPerformed

    private void tgbShowAreasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbShowAreasActionPerformed
        renderAllObjects();
    }//GEN-LAST:event_tgbShowAreasActionPerformed

    private void tgbShowCamerasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbShowCamerasActionPerformed
        renderAllObjects();
    }//GEN-LAST:event_tgbShowCamerasActionPerformed

    private void tgbShowGravityActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbShowGravityActionPerformed
        renderAllObjects();
    }//GEN-LAST:event_tgbShowGravityActionPerformed

    private void tgbShowAxisActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbShowAxisActionPerformed
        glCanvas.repaint();
    }//GEN-LAST:event_tgbShowAxisActionPerformed

    private void treeObjectsValueChanged(javax.swing.event.TreeSelectionEvent evt) {//GEN-FIRST:event_treeObjectsValueChanged
        TreePath[] paths = evt.getPaths();
        for(TreePath path : paths) {
            TreeNode node =(TreeNode)path.getLastPathComponent();
            if(!(node instanceof ObjTreeNode))
                continue;
            
            ObjTreeNode tnode =(ObjTreeNode)node;
            if(!(tnode.object instanceof AbstractObj))
                continue;
            
            AbstractObj obj =(AbstractObj)tnode.object;
            if(evt.isAddedPath(path)) {
                selectedObjs.put(obj.uniqueID, obj);
                addRerenderTask("zone:"+obj.stage.stageName);
            } else {
                selectedObjs.remove(obj.uniqueID);
                addRerenderTask("zone:"+obj.stage.stageName);
            }
        }

        selectionArg = 0;
        selectionChanged();
        glCanvas.repaint();
    }//GEN-LAST:event_treeObjectsValueChanged

    private void tgbAddObjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbAddObjectActionPerformed
        if (tgbAddObject.isSelected()) {
            popupAddItems.show(tgbAddObject, tgbAddObject.getX(), tgbAddObject.getY() + tgbAddObject.getHeight());
        }
        else {
            popupAddItems.setVisible(false);
            setDefaultStatus();
        }
    }//GEN-LAST:event_tgbAddObjectActionPerformed

    private void tgbDeleteObjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbDeleteObjectActionPerformed
        if (selectedObjs.isEmpty()) {
            if (tgbDeleteObject.isSelected()) {
                deletingObjects = true;
                lblStatus.setText("Click the object you want to delete. Hold Shift to delete multiple objects. Right-click to abort.");
            }
            else {
                deletingObjects = false;
                setDefaultStatus();
            }
        }
        else {
            if (tgbDeleteObject.isSelected()) {
                List<AbstractObj> templist = new ArrayList(selectedObjs.values());
                
                for(AbstractObj selectedObj : templist) {
                    selectedObjs.remove(selectedObj.uniqueID);
                    deleteObject(selectedObj.uniqueID);
                }
                
                templist.clear();
                selectionChanged();
            }
            
            treeObjects.setSelectionRow(0);
            tgbDeleteObject.setSelected(false);
        }
    }//GEN-LAST:event_tgbDeleteObjectActionPerformed

    private void tgbCopyObjActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbCopyObjActionPerformed
        copyObj =(LinkedHashMap<Integer, AbstractObj>) selectedObjs.clone();
        tgbCopyObj.setSelected(false);
        lblStatus.setText("Copied objects.");
    }//GEN-LAST:event_tgbCopyObjActionPerformed

    private void tgbPasteObjActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tgbPasteObjActionPerformed
        tgbPasteObj.setSelected(false);
        if(copyObj != null) {
            for(AbstractObj currentTempObj : copyObj.values()) {
                addingObject = "objinfo|" + currentTempObj.name;
                addingObjectOnLayer = currentTempObj.layerKey;
                
                Vec3f temppos=new Vec3f();
                temppos.x=currentTempObj.position.x+g_offset_x;
                temppos.y=currentTempObj.position.y+g_offset_y;
                temppos.z=currentTempObj.position.z+g_offset_z;
                
                Vec3f temprot=new Vec3f();
                temprot.x=currentTempObj.rotation.x;
                temprot.y=currentTempObj.rotation.y;
                temprot.z=currentTempObj.rotation.z;
                
                addObject(temppos);
                
                
                
                addUndoEntry("addObj", newobj);
                newobj.rotation = temprot;
                addingObject = "";
            }
            lblStatus.setText("Pasted objects.");
            glCanvas.repaint();
        }
    }//GEN-LAST:event_tgbPasteObjActionPerformed

    private void listZonesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listZonesMouseClicked
        if (evt.getClickCount() > 1 && btnEditZone.isEnabled())
            openSelectedZone();
    }//GEN-LAST:event_listZonesMouseClicked
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Object adding and deleting
    
    private void setObjectBeingAdded(String key) {
        switch(key) {
            case "startinfo":
                addingObject = "startinfo|Mario";
                addingObjectOnLayer = "common";
                break;
            case "soundinfo":
                addingObject = "soundinfo|SoundEmitter";
                addingObjectOnLayer = "common";
                break;
            case "generalposinfo":
                addingObject = "generalposinfo|GeneralPos";
                addingObjectOnLayer = "common";
                break;
            case "debugmoveinfo":
                addingObject = "debugmoveinfo|DebugMovePos";
                addingObjectOnLayer = "common";
                break;
            case "path":
                addingObject = "path|null";
                break;
            case "pathpoint":
                addingObject = "pathpoint|null";
                break;
            case "cameracubeinfo":
                if (Whitehole.getCurrentGameType() == 2) {
                    addingObject = "cameracubeinfo|CameraArea";
                    addingObjectOnLayer = "common";
                    break;
                }
            default:
                if (ObjectSelectForm.openNewObjectDialog(key, curZoneArc.getLayerNames())) {
                    addingObject = key + "|" + ObjectSelectForm.getResultName();
                    addingObjectOnLayer = ObjectSelectForm.getResultLayer();
                }
                else {
                    tgbAddObject.setSelected(false);
                }
                break;
        }
        
        lblStatus.setText("Click the level view to place your object. Hold Shift to place multiple objects. Right-click to abort.");
    }
    
    private void addObject(Object point) {
        // Resolve position
        Vec3f position;
        if(point instanceof Point) {
            position = get3DCoords((Point)point, Math.min(pickingDepth, 1f));
        }
        else {
            position = (Vec3f)point;
        }
        
        // Apply zone placement
        if(isGalaxyMode) {
            String stageKey = String.format("%d/%s", curScenarioID, curZone);
            if (zonePlacements.containsKey(stageKey)) {
                StageObj zonePlacement = zonePlacements.get(stageKey);
                Vec3f.subtract(position, zonePlacement.position, position);
                applySubzoneRotation(position);
            }
        }
        
        // Get designated information
        String objtype = addingObject.substring(0, addingObject.indexOf('|'));
        String objname = addingObject.substring(addingObject.indexOf('|') + 1);
        addingObjectOnLayer = addingObjectOnLayer.toLowerCase();
        
        TreeNode newNode;
        
        // Add new path?
        if (objtype.equals("path")) {
            int newPathLinkID = 0;

            for (PathObj path : curZoneArc.paths) {
                if (path.pathID >= newPathLinkID) {
                    newPathLinkID = path.pathID + 1;
                }
            }

            PathObj thepath = new PathObj(curZoneArc, newPathLinkID);
            thepath.uniqueID = maxUniqueID++;
            globalPathList.put(thepath.uniqueID, thepath);
            curZoneArc.paths.add(thepath);

            PathPointObj thepoint = new PathPointObj(thepath, position);
            thepoint.uniqueID = maxUniqueID;
            maxUniqueID += 3;
            
            globalObjList.put(thepoint.uniqueID, thepoint);
            globalPathPointList.put(thepoint.uniqueID, thepoint);
            thepath.getPoints().add(thepoint);

            ObjListTreeNode newnode = (ObjListTreeNode)objListPathRootNode.addObject(thepath);
            treeNodeList.put(thepath.uniqueID, newnode);

            newNode = newnode.addObject(thepoint);
            treeNodeList.put(thepoint.uniqueID, newNode);

            rerenderTasks.add("path:" + thepath.uniqueID);
            rerenderTasks.add("zone:" + curZone);
        }
        // Add new path point?
        else if (objtype.equals("pathpoint")) {
            if(selectedObjs.size() > 1)
                return;

            PathObj thepath = null;
            for(AbstractObj obj : selectedObjs.values())
                thepath =((PathPointObj) obj).path;

            if(thepath == null)
                return;


            PathPointObj thepoint = new PathPointObj(thepath, position);
            thepoint.uniqueID = maxUniqueID;
            maxUniqueID += 3;
            globalObjList.put(thepoint.uniqueID, thepoint);
            globalPathPointList.put(thepoint.uniqueID, thepoint);
            thepath.getPoints().add(thepoint);

            ObjListTreeNode listnode = objListPathRootNode;
            listnode =(ObjListTreeNode) listnode.children.get(thepath.uniqueID);

            newNode = listnode.addObject(thepoint);
            treeNodeList.put(thepoint.uniqueID, newNode);
            
            rerenderTasks.add("path:" + thepath.uniqueID);
            rerenderTasks.add("zone:" + thepath.stage.stageName);
        }
        else {
            newobj = null;
            
            switch(objtype) {
                case "objinfo":
                case "sound_objinfo":
                    newobj = new LevelObj(curZoneArc, addingObjectOnLayer, objname, position); break;
                case "mappartsinfo": newobj = new MapPartObj(curZoneArc, addingObjectOnLayer, objname, position); break;
                case "childobjinfo": newobj = new ChildObj(curZoneArc, addingObjectOnLayer, objname, position); break;
                case "planetobjinfo": newobj = new GravityObj(curZoneArc, addingObjectOnLayer, objname, position); break;
                case "areaobjinfo":
                case "sound_areaobjinfo":
                case "design_areaobjinfo":
                    newobj = new AreaObj(curZoneArc, addingObjectOnLayer, objname, position); break;
                case "cameracubeinfo": newobj = new CameraObj(curZoneArc, addingObjectOnLayer, objname, position); break;
                case "soundinfo": newobj = new SoundObj(curZoneArc, addingObjectOnLayer, position); break;
                case "startinfo": newobj = new StartObj(curZoneArc, addingObjectOnLayer, position); break;
                case "demoobjinfo": newobj = new CutsceneObj(curZoneArc, addingObjectOnLayer, objname, position); break;
                case "generalposinfo": newobj = new PositionObj(curZoneArc, addingObjectOnLayer, position); break;
                case "debugmoveinfo": newobj = new DebugObj(curZoneArc, addingObjectOnLayer, position); break;
            }
            
            // Calculate UID
            int uniqueID = 0;
            
            while(globalObjList.containsKey(uniqueID)
                    || globalPathList.containsKey(uniqueID)
                    || globalPathPointList.containsKey(uniqueID)) {
                uniqueID++;
            }
            
            if(uniqueID > maxUniqueID) {
                maxUniqueID = uniqueID;
            }
            
            // Add entry and node
            newobj.uniqueID = uniqueID;
            globalObjList.put(uniqueID, newobj);
            curZoneArc.objects.get(addingObjectOnLayer).add(newobj);
            
            newNode = objListTreeNodes.get(objtype).addObject(newobj);
            treeNodeList.put(uniqueID, newNode);
            
            // Update rendering
            rerenderTasks.add(String.format("addobj:%1$d", uniqueID));
            rerenderTasks.add("zone:" + curZone);
        }
        
        // Update tree node model and scroll to new node
        objListModel.reload();
        TreePath path = new TreePath(objListModel.getPathToRoot(newNode));
        treeObjects.setSelectionPath(path);
        treeObjects.scrollPathToVisible(path);
        
        glCanvas.repaint();
        unsavedChanges = true;
    }
    
    private void deleteObject(int uniqueID) {
        if(globalObjList.containsKey(uniqueID)) {
            AbstractObj obj = globalObjList.get(uniqueID);
            
            addUndoEntry("deleteObj", obj);
            
            obj.stage.objects.get(obj.layerKey).remove(obj);
            rerenderTasks.add(String.format("delobj:%1$d", uniqueID));
            rerenderTasks.add("zone:" + obj.stage.stageName);

            if(treeNodeList.containsKey(uniqueID)) {
                DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                ObjTreeNode thenode =(ObjTreeNode)treeNodeList.get(uniqueID);
                objlist.removeNodeFromParent(thenode);
                treeNodeList.remove(uniqueID);
            }
        }
        
        if(globalPathPointList.containsKey(uniqueID)) {
            PathPointObj obj = globalPathPointList.get(uniqueID);
            obj.path.getPoints().remove(obj.getIndex());
            globalPathPointList.remove(uniqueID);
            if(obj.path.getPoints().isEmpty()) {
                obj.path.stage.paths.remove(obj.path);
                globalPathList.remove(obj.path.uniqueID);
                
                rerenderTasks.add("zone:"+obj.path.stage.stageName);

                if(treeNodeList.containsKey(obj.path.uniqueID)) {
                    DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                    ObjTreeNode thenode =(ObjTreeNode)treeNodeList.get(obj.path.uniqueID);
                    objlist.removeNodeFromParent(thenode);
                    treeNodeList.remove(obj.path.uniqueID);
                    treeNodeList.remove(uniqueID);
                }
            }
            else {
                rerenderTasks.add(String.format("path:%1$d", obj.path.uniqueID));
                rerenderTasks.add("zone:"+obj.path.stage.stageName);

                if(treeNodeList.containsKey(uniqueID)) {
                    DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                    ObjTreeNode thenode =(ObjTreeNode)treeNodeList.get(uniqueID);
                    objlist.removeNodeFromParent(thenode);
                    treeNodeList.remove(uniqueID);
                }
            }
        }
        
        glCanvas.repaint();
        unsavedChanges = true;
    }
    
    /**
     * Update rendering according to current selection. Called when the selection is changed.
     */
    public void selectionChanged() {
        displayedPaths.clear();
        pnlObjectSettings.clear();
        
        if(selectedObjs.isEmpty()) {
            lblStatus.setText("Object deselected.");
            tgbDeselect.setEnabled(false);
            pnlObjectSettings.doLayout();
            pnlObjectSettings.validate();
            pnlObjectSettings.repaint();

            glCanvas.requestFocusInWindow();
            return;
        }
        
        // Check if any are paths/path points linked to selected objs
        for(AbstractObj obj : selectedObjs.values()) {
            int pathid = -1;
            
            if(obj instanceof PathPointObj)
                pathid = ((PathPointObj) obj).path.pathID;
            else if(obj.data.containsKey("CommonPath_ID"))
                pathid = (short) obj.data.get("CommonPath_ID");
            
            if(pathid == -1)
                continue;
            
            // Display path if it is linked to object
            if(displayedPaths.get(pathid) == null)
                displayedPaths.put(pathid, (PathPointObj) globalPathPointList.get(pathid));
        }
        
        // Check if the selected objects' classes are the same
        Class cls = null; boolean allthesame = true;
        if(selectedObjs.size() > 1) {
            for(AbstractObj selectedObj : selectedObjs.values()) {
                if(cls != null && cls != selectedObj.getClass()) {
                    allthesame = false;
                    break;
                } else if(cls == null)
                    cls = selectedObj.getClass();
            }
        }
        
        // If all selected objects are the same type, add all properties
        if(allthesame) {
            for(AbstractObj selectedObj : selectedObjs.values()) {
                if(selectedObj instanceof PathPointObj) {
                    PathPointObj selectedPathPoint =(PathPointObj)selectedObj;
                    PathObj path = selectedPathPoint.path;
                    lblStatus.setText(String.format("Selected [%3$d] %1$s(%2$s), point %4$d",
                                path.data.get("name"), path.stage.stageName, path.pathID, selectedPathPoint.getIndex()) + ".");
                    tgbDeselect.setEnabled(true);
                    selectedPathPoint.getProperties(pnlObjectSettings);
                }
                else {
                    String layer = selectedObj.layerKey.equals("common") ? "Common" : selectedObj.getLayerName();
                    lblStatus.setText("Selected " + selectedObj.name + "(" + selectedObj.stage.stageName + ", " + layer + ").");
                    tgbDeselect.setEnabled(true);
                    
                    LinkedList layerlist = new LinkedList();
                    layerlist.add("Common");
                    for(int l = 0; l < 26; l++) {
                        String layerstring = String.format("Layer%1$c", (char) ('A' + l));
                        if(curZoneArc.objects.containsKey(layerstring.toLowerCase()))
                            layerlist.add(layerstring);
                    }
                    
                    if(selectedObj.getClass() != PathPointObj.class) {
                        pnlObjectSettings.addCategory("obj_general", "General");
                        if(selectedObj.getClass() != StartObj.class && selectedObj.getClass() != DebugObj.class)
                            pnlObjectSettings.addField("name", "Object", "objname", null, selectedObj.name, "Default");
                        if(isGalaxyMode)
                            pnlObjectSettings.addField("zone", "Zone", "list", galaxyArchive.zoneList, selectedObj.stage.stageName, "Default");
                        pnlObjectSettings.addField("layer", "Layer", "list", layerlist, layer, "Default");
                    }

                    selectedObj.getProperties(pnlObjectSettings);
                }
            }

            if(selectedObjs.size() > 1) {
                pnlObjectSettings.removeField("pos_x"); pnlObjectSettings.removeField("pos_y"); pnlObjectSettings.removeField("pos_z");
                pnlObjectSettings.removeField("pnt0_x"); pnlObjectSettings.removeField("pnt0_y"); pnlObjectSettings.removeField("pnt0_z");
                pnlObjectSettings.removeField("pnt1_x"); pnlObjectSettings.removeField("pnt1_y"); pnlObjectSettings.removeField("pnt1_z");
                pnlObjectSettings.removeField("pnt2_x"); pnlObjectSettings.removeField("pnt2_y"); pnlObjectSettings.removeField("pnt2_z");
                
                
                
                pnlObjectSettings.addCategory("Group_Move", "Group_Move");
                pnlObjectSettings.addField("group_move_x", "Step X Pos", "float", null,0.0f, "");//adding movement input fields
                pnlObjectSettings.addField("group_move_y", "Step Y Pos", "float", null,0.0f, "");
                pnlObjectSettings.addField("group_move_z", "Step Z Pos", "float", null,0.0f, "");
                pnlObjectSettings.addField("groupmove_x", "Move X", "float", null,0.0f, "");
                pnlObjectSettings.addField("groupmove_y", "Move Y", "float", null,0.0f, "");
                pnlObjectSettings.addField("groupmove_z", "Move Z", "float", null,0.0f, "");
                pnlObjectSettings.addField("groupmove_a", "Move all", "float", null,0.0f, "");
                
                pnlObjectSettings.addCategory("Group_Rotate", "Group_Rotate");
                pnlObjectSettings.addField("group_rotate_center_x", "Center X Pos", "float", null,0.0f, "");//adding rotations input fields
                pnlObjectSettings.addField("group_rotate_center_y", "Center Y Pos", "float", null,0.0f, "");
                pnlObjectSettings.addField("group_rotate_center_z", "Center Z Pos", "float", null,0.0f, "");
                pnlObjectSettings.addField("group_rotate_angle_x", "Rotate X", "float", null,0.0f, "");
                pnlObjectSettings.addField("group_rotate_angle_y", "Rotate Y", "float", null,0.0f, "");
                pnlObjectSettings.addField("group_rotate_angle_z", "Rotate Z", "float", null,0.0f, "");
                
                pnlObjectSettings.addCategory("Group_Copy", "Group_Copy");
                pnlObjectSettings.addField("group_copy_offset_x", "offest X", "float", null,0.0f, "");//adding copy input fields
                pnlObjectSettings.addField("group_copy_offset_y", "offest Y", "float", null,0.0f, "");
                pnlObjectSettings.addField("group_copy_offset_z", "offest Z", "float", null,0.0f, "");
            }
            	this.g_center_x = 0;//reset values if selected objects changed
            	this.g_center_y =0;
            	this.g_center_z =0;
            	this.g_angle_x =0;
            	this.g_angle_y =0;
            	this.g_angle_z =0;
            	this.g_move_step_a =0;
            	this.g_move_step_x =0;
            	this.g_move_step_y =0;
            	this.g_move_step_z =0;
            	this.g_move_x =0;
            	this.g_move_y =0;
            	this.g_move_z =0;
            	this.g_offset_x=0;
            	this.g_offset_y=0;
            	this.g_offset_z=0;
            	
        }
        
        if(selectedObjs.size() > 1) {
            lblStatus.setText("Multiple objects selected.(" + selectedObjs.size() + ").");
        }
        
        pnlObjectSettings.doLayout();
        pnlObjectSettings.validate();
        pnlObjectSettings.repaint();
        
        glCanvas.requestFocusInWindow();
    }
    
    public void pasteObject(AbstractObj obj) {
        addingObject = obj.getFileType() + "|" + obj.name;
        addingObjectOnLayer = obj.layerKey;
        addObject(mousePos);
        
        addingObject = "";
        
        newobj.rotation.x = obj.rotation.x; newobj.rotation.y = obj.rotation.y; newobj.rotation.z = obj.rotation.z;
        newobj.scale.x = obj.scale.x; newobj.scale.y = obj.scale.y; newobj.scale.z = obj.scale.z;
        addingObject="";
        newobj.data.put("dir_x", obj.rotation.x); newobj.data.put("dir_y", obj.rotation.y); newobj.data.put("dir_z", obj.rotation.z);
        newobj.data.put("scale_x", obj.scale.x); newobj.data.put("scale_y", obj.scale.y); newobj.data.put("scale_z", obj.scale.z);
        newobj.data.put("Obj_arg0", obj.data.get("Obj_arg0"));
        newobj.data.put("Obj_arg1", obj.data.get("Obj_arg1"));
        newobj.data.put("Obj_arg2", obj.data.get("Obj_arg2"));
        newobj.data.put("Obj_arg3", obj.data.get("Obj_arg3"));
        newobj.data.put("Obj_arg4", obj.data.get("Obj_arg4"));
        newobj.data.put("Obj_arg5", obj.data.get("Obj_arg5"));
        newobj.data.put("Obj_arg6", obj.data.get("Obj_arg6"));
        newobj.data.put("Obj_arg7", obj.data.get("Obj_arg7"));
        newobj.data.put("SW_APPEAR", obj.data.get("SW_APPEAR"));
        newobj.data.put("SW_DEAD", obj.data.get("SW_DEAD"));
        newobj.data.put("SW_A", obj.data.get("SW_A"));
        newobj.data.put("SW_B", obj.data.get("SW_B"));
        newobj.data.put("l_id", obj.data.get("l_id"));
        newobj.data.put("CameraSetId", obj.data.get("CameraSetId"));
        newobj.data.put("CastId", obj.data.get("CastId"));
        newobj.data.put("ViewGroupId", obj.data.get("ViewGroupId"));
        newobj.data.put("ShapeModelNo", obj.data.get("ShapeModelNo"));
        newobj.data.put("CommonPath_ID", obj.data.get("CommonPath_ID"));
        newobj.data.put("ClippingGroupId", obj.data.get("ClippingGroupId"));
        newobj.data.put("GroupId", obj.data.get("GroupId"));
        newobj.data.put("DemoGroupId", obj.data.get("DemoGroupId"));
        newobj.data.put("MapParts_ID", obj.data.get("MapParts_ID"));
        newobj.data.put("MessageId", obj.data.get("MessageId"));
        
        if(Whitehole.getCurrentGameType() == 1)
            newobj.data.put("SW_SLEEP", obj.data.get("SW_SLEEP"));
        if(Whitehole.getCurrentGameType() == 2) {
            newobj.data.put("SW_AWAKE", obj.data.get("SW_AWAKE"));
            newobj.data.put("SW_PARAM", obj.data.get("SW_PARAM"));
            newobj.data.put("ParamScale", obj.data.get("ParamScale"));
            newobj.data.put("Obj_ID", obj.data.get("Obj_ID"));
            newobj.data.put("GeneratorID", obj.data.get("GeneratorID"));
        }
        
        newobj.renderer = obj.renderer;
        
        addUndoEntry("addObj", newobj);
    }
    
    /**
     * Decrease {@code undoIndex} and undo if possible.
     */
    public void undo() {
        // Decrease undo index
        if(undoIndex >= 1)
            undoIndex--;
        else // Nothing to undo
            return;
        
        UndoEntry change = undoList.get(undoIndex);
        
        if(change.objType.equals("pathpoint")) {
            lblStatus.setText("Undoing path points is currently not supported, sorry!");
        } else {
            switch(change.type) {
                case "changeObj":
                    AbstractObj obj = globalObjList.get(change.id);
                    obj.data = (Bcsv.Entry) change.data.clone();
                    obj.position = (Vec3f) change.position.clone();
                    obj.rotation = (Vec3f) change.rotation.clone();
                    obj.scale = (Vec3f) change.scale.clone();
                    pnlObjectSettings.setFieldValue("pos_x", obj.position.x);
                    pnlObjectSettings.setFieldValue("pos_y", obj.position.y);
                    pnlObjectSettings.setFieldValue("pos_z", obj.position.z);
                    pnlObjectSettings.repaint();
                    addRerenderTask("zone:"+obj.stage.stageName);
                    break;
                case "deleteObj":
                    addingObject = change.objType + "|" + change.name;
                    addingObjectOnLayer = change.layer;

                    addObject(change.position);
                    addingObject = "";

                    newobj.data =(Bcsv.Entry) change.data.clone();
                    newobj.position =(Vec3f) change.position.clone();

                    if(change.rotation != null)
                        newobj.rotation =(Vec3f) change.rotation.clone();

                    if(change.scale != null)
                        newobj.scale =(Vec3f) change.scale.clone();

                    addRerenderTask("zone:" + newobj.stage.stageName);
                    break;
                case "addObj":
                    deleteObject(change.id);
            }
        }
        
        undoList.remove(change);
    }
    
    /**
     * Adds {@code obj} to the current undo list.
     * @param type the type of action
     * @param obj the object that the action was performed on
     */
    public void addUndoEntry(String type, AbstractObj obj) {
        if(obj instanceof PathPointObj)
            return; // we don't support path points
        
        if(undoIndex != undoList.size())
            undoList.subList(0, undoIndex);
        if(undoIndex > 0) {
            if(undoList.get(undoIndex - 1).type.equals(type) && undoList.get(undoIndex - 1).id == obj.uniqueID)
                return;
        }
        
        System.out.println("Added " + type + " with " + obj);

        undoList.add(new UndoEntry(type, obj));
        undoIndex++;
    }
    
    /**
     * Attempt to apply rotation/translation of the current zone to {@code delta}.
     * @param delta the position to change
     */
    public Vec3f applySubzoneRotation(Vec3f delta) {
        if(!isGalaxyMode)
            return new Vec3f();

        String stageKey = String.format("%d/%s", curScenarioID, curZone);
        if(zonePlacements.containsKey(stageKey)) {
            StageObj szdata = zonePlacements.get(stageKey);

            float xcos = (float)Math.cos(-(szdata.rotation.x * Math.PI) / 180f);
            float xsin = (float)Math.sin(-(szdata.rotation.x * Math.PI) / 180f);
            float ycos = (float)Math.cos(-(szdata.rotation.y * Math.PI) / 180f);
            float ysin = (float)Math.sin(-(szdata.rotation.y * Math.PI) / 180f);
            float zcos = (float)Math.cos(-(szdata.rotation.z * Math.PI) / 180f);
            float zsin = (float)Math.sin(-(szdata.rotation.z * Math.PI) / 180f);

            float x1 = (delta.x * zcos) - (delta.y * zsin);
            float y1 = (delta.x * zsin) + (delta.y * zcos);
            float x2 = (x1 * ycos) + (delta.z * ysin);
            float z2 = -(x1 * ysin) + (delta.z * ycos);
            float y3 = (y1 * xcos) - (z2 * xsin);
            float z3 = (y1 * xsin) + (z2 * xcos);

            delta.x = x2;
            delta.y = y3;
            delta.z = z3;
            
        } else {
            // zone not found??;
        }
        
        return delta;
    }
    
    private Vec3f get3DCoords(Point pt, float depth) {
        Vec3f ret = new Vec3f(
                camPosition.x * SCALE_DOWN,
                camPosition.y * SCALE_DOWN,
                camPosition.z * SCALE_DOWN);
        depth *= SCALE_DOWN;

        ret.x -=(depth *(float)Math.cos(camRotation.x) *(float)Math.cos(camRotation.y));
        ret.y -=(depth *(float)Math.sin(camRotation.y));
        ret.z -=(depth *(float)Math.sin(camRotation.x) *(float)Math.cos(camRotation.y));

        float x =(pt.x -(glCanvas.getWidth() / 2f)) * pixelFactorX * depth;
        float y = -(pt.y -(glCanvas.getHeight() / 2f)) * pixelFactorY * depth;

        ret.x +=(x *(float)Math.sin(camRotation.x)) -(y *(float)Math.sin(camRotation.y) *(float)Math.cos(camRotation.x));
        ret.y += y *(float)Math.cos(camRotation.y);
        ret.z += -(x *(float)Math.cos(camRotation.x)) -(y *(float)Math.sin(camRotation.y) *(float)Math.sin(camRotation.x));

        return ret;
    }
    
    /**
     * Moves the selection by {@code delta}.
     * @param delta the distance to move the selection by
     */
    private void offsetSelectionBy(Vec3f delta) {
        unsavedChanges = true;
        for(AbstractObj selectedObj : selectedObjs.values()) {
            if(selectedObj instanceof PathPointObj) {
                PathPointObj selectedPathPoint =(PathPointObj)selectedObj;
                
                switch(selectionArg) {
                    case 0:
                        selectedPathPoint.position.x += delta.x;
                        selectedPathPoint.position.y += delta.y;
                        selectedPathPoint.position.z += delta.z;
                        selectedPathPoint.point1.x += delta.x;
                        selectedPathPoint.point1.y += delta.y;
                        selectedPathPoint.point1.z += delta.z;
                        selectedPathPoint.point2.x += delta.x;
                        selectedPathPoint.point2.y += delta.y;
                        selectedPathPoint.point2.z += delta.z;
                        break;
                    case 1:
                        selectedPathPoint.point1.x += delta.x;
                        selectedPathPoint.point1.y += delta.y;
                        selectedPathPoint.point1.z += delta.z;
                        break;
                    case 2:
                        selectedPathPoint.point2.x += delta.x;
                        selectedPathPoint.point2.y += delta.y;
                        selectedPathPoint.point2.z += delta.z;
                        break;
                }

                pnlObjectSettings.setFieldValue("pnt0_x", selectedPathPoint.position.x);
                pnlObjectSettings.setFieldValue("pnt0_y", selectedPathPoint.position.y);
                pnlObjectSettings.setFieldValue("pnt0_z", selectedPathPoint.position.z);
                pnlObjectSettings.setFieldValue("pnt1_x", selectedPathPoint.point1.x);
                pnlObjectSettings.setFieldValue("pnt1_y", selectedPathPoint.point1.y);
                pnlObjectSettings.setFieldValue("pnt1_z", selectedPathPoint.point1.z);
                pnlObjectSettings.setFieldValue("pnt2_x", selectedPathPoint.point2.x);
                pnlObjectSettings.setFieldValue("pnt2_y", selectedPathPoint.point2.y);
                pnlObjectSettings.setFieldValue("pnt2_z", selectedPathPoint.point2.z);
                pnlObjectSettings.repaint();
                rerenderTasks.add(String.format("path:%1$d", selectedPathPoint.path.uniqueID));
                rerenderTasks.add("zone:"+selectedPathPoint.path.stage.stageName);
            } else {
                //if(selectedObj instanceof StageObj)
                //    return;
                
                addUndoEntry("changeObj", selectedObj);
                
                selectedObj.position.x += delta.x;
                selectedObj.position.y += delta.y;
                selectedObj.position.z += delta.z;
                pnlObjectSettings.setFieldValue("pos_x", selectedObj.position.x);
                pnlObjectSettings.setFieldValue("pos_y", selectedObj.position.y);
                pnlObjectSettings.setFieldValue("pos_z", selectedObj.position.z);
                pnlObjectSettings.repaint();
                addRerenderTask("zone:"+selectedObj.stage.stageName);
            }
            glCanvas.repaint();
        }
    }
    
    /**
     * Rotate the selection by {@code delta}.
     * @param delta the amount to rotate by
     */
    private void rotateSelectionBy(Vec3f delta) {
        unsavedChanges = true;
        for(AbstractObj selectedObj : selectedObjs.values()) {
            if(selectedObj instanceof StageObj || selectedObj instanceof PositionObj || selectedObj instanceof PathPointObj)
                return;
            
            addUndoEntry("changeObj", selectedObj);
            
            selectedObj.rotation.x += delta.x;
            selectedObj.rotation.y += delta.y;
            selectedObj.rotation.z += delta.z;
            pnlObjectSettings.setFieldValue("dir_x", selectedObj.rotation.x);
            pnlObjectSettings.setFieldValue("dir_y", selectedObj.rotation.y);
            pnlObjectSettings.setFieldValue("dir_z", selectedObj.rotation.z);
            pnlObjectSettings.repaint();
            
            addRerenderTask("zone:"+selectedObj.stage.stageName);
            addRerenderTask("object:"+selectedObj.uniqueID);
            glCanvas.repaint();
        }
    }
    
    /**
     * Scale the selection by {@code delta}.
     * @param delta the amount to scale by
     */
    private void scaleSelectionBy(Vec3f delta, float snap) {
        unsavedChanges = true;
        for(AbstractObj selectedObj : selectedObjs.values()) {
            if(selectedObj instanceof StageObj || selectedObj instanceof PositionObj || selectedObj instanceof PathPointObj)
                return;
            
            addUndoEntry("changeObj", selectedObj);
            
            selectedObj.scale.x += delta.x;
            selectedObj.scale.y += delta.y;
            selectedObj.scale.z += delta.z;
            
            if(snap != 0.0f)
            {
                selectedObj.scale.x -= selectedObj.scale.x % snap;
                selectedObj.scale.y -= selectedObj.scale.y % snap;
                selectedObj.scale.z -= selectedObj.scale.z % snap;
            }
            
            pnlObjectSettings.setFieldValue("scale_x", selectedObj.scale.x);
            pnlObjectSettings.setFieldValue("scale_y", selectedObj.scale.y);
            pnlObjectSettings.setFieldValue("scale_z", selectedObj.scale.z);
            pnlObjectSettings.repaint();

            addRerenderTask("zone:"+selectedObj.stage.stageName);
            addRerenderTask("object:"+selectedObj.uniqueID);
            glCanvas.repaint();
        }
    }
    
    private void scaleSelectionBy(Vec3f delta) {
        scaleSelectionBy(delta, 0.0f);
    }
    
    /**
     * Called when translating through the shortcut key G
     * @param shiftDown will move slower if shift is pressed
     * @param ctrlDown will snap to values if ctrl is pressed
     */
    public void keyTranslating(boolean shiftDown, boolean ctrlDown) {
        Vec3f delta = new Vec3f();
        float curDist = (float) Math.sqrt(Math.pow(startingMousePos.x - mousePos.x, 2) + Math.pow(startingMousePos.y - mousePos.y, 2));
        if(lastDist == 0)
            lastDist = curDist;
        float pol = 10f;

        if(shiftDown)
            pol *= 0.1;

        if(curDist < lastDist)
            pol *= -1;
        if(startingMousePos.y < mousePos.y)
            pol *= -1;

        if(keyAxis != null) switch(keyAxis) {
            case "all":
                float objz = depthUnderCursor;

                float xdelta = (startingMousePos.x - mousePos.x) * pixelFactorX * objz * SCALE_DOWN;
                float ydelta = (startingMousePos.y - mousePos.y) * -pixelFactorY * objz * SCALE_DOWN;

                delta = new Vec3f(
                       (xdelta *(float)Math.sin(camRotation.x)) -(ydelta *(float)Math.sin(camRotation.y) *(float)Math.cos(camRotation.x)),
                        ydelta *(float)Math.cos(camRotation.y),
                        -(xdelta *(float)Math.cos(camRotation.x)) -(ydelta *(float)Math.sin(camRotation.y) *(float)Math.sin(camRotation.x)));
                applySubzoneRotation(delta);
                System.out.println(delta);
                break;
            case "x":
                delta.x = pol * Math.abs(lastDist - curDist);
                break;
            case "y":
                delta.y += pol * Math.abs(lastDist - curDist);
                break;
            case "z":
                delta.z += pol * Math.abs(lastDist - curDist);
                break;
            default:
                break;
        }

        offsetSelectionBy(delta);

        lastDist = curDist;
        rerenderTasks.add("zone:" + curZone);
        pnlObjectSettings.repaint();
        glCanvas.repaint();
        unsavedChanges = true;
    }
    
    /**
     * Called when scaling through the shortcut key S
     * @param shiftDown will move slower if shift is pressed
     * @param ctrlDown will snap to increments if ctrl is pressed
     */
    public void keyScaling(boolean shiftDown, boolean ctrlDown) {
        // calculate dist from the position where we began scaling
        float curDist = (float) Math.sqrt(Math.pow(startingMousePos.x - mousePos.x, 2) + Math.pow(startingMousePos.y - mousePos.y, 2));
        if(lastDist == 0)
            lastDist = curDist;
        
        int polarity = 1;
        float speed = 0.01f;
        
        // raise sensitivity?
        if(ctrlDown)
            speed *= 5;
        
        if(shiftDown)
            speed *= 0.1f;

        
        if(curDist < lastDist) // we're moving closer to object center
            polarity *= -1;
        if(startingMousePos.y < mousePos.y)
            polarity *= -1;

        
        Vec3f delta = new Vec3f();
        if(keyAxis != null) switch(keyAxis) {
            case "all":
                delta.x += polarity * Math.abs(lastDist - curDist) * speed;
                delta.y += polarity * Math.abs(lastDist - curDist) * speed;
                delta.z += polarity * Math.abs(lastDist - curDist) * speed;
                break;
            case "x":
                delta.x += polarity * Math.abs(lastDist - curDist) * speed;
                break;
            case "y":
                delta.y += polarity * Math.abs(lastDist - curDist) * speed;
                break;
            case "z":
                delta.z += polarity * Math.abs(lastDist - curDist) * speed;
                break;
        }

        
        if(ctrlDown)
            scaleSelectionBy(delta, 0.10f * polarity);
        else
            scaleSelectionBy(delta);
        
        lastDist = curDist;
        rerenderTasks.add("zone:" + curZone);
        pnlObjectSettings.repaint();
        glCanvas.repaint();
        unsavedChanges = true;
    }
    
    /**
     * Called when rotating through the shortcut key R
     * @param shiftDown will move slower if shift is pressed
     * @param ctrlDown will snap if ctrl is pressed
     */
    public void keyRotating(boolean shiftDown, boolean ctrlDown) {
        Vec3f delta = new Vec3f();
        float curDist = (float) Math.sqrt(Math.pow(startingMousePos.x - mousePos.x, 2) + Math.pow(startingMousePos.y - mousePos.y, 2));
        if(lastDist == 0)
            lastDist = curDist;
        float pol = 0.1f;

        if(shiftDown)
            pol *= 0.1;

        if(curDist < lastDist)
            pol *= -1;
        if(startingMousePos.y < mousePos.y)
            pol *= -1;

        if(keyAxis != null) switch(keyAxis) {
            case "x":
                delta.x += pol * Math.abs(lastDist - curDist);
                break;
            case "y":
                delta.y += pol * Math.abs(lastDist - curDist);
                break;
            case "z":
                delta.z += pol * Math.abs(lastDist - curDist);
                break;
            default:
                break;
        }

        rotateSelectionBy(delta);

        lastDist = curDist;
        rerenderTasks.add("zone:" + curZone);
        pnlObjectSettings.repaint();
        glCanvas.repaint();
        unsavedChanges = true;
    }
    
    /*
     * The property changing events. These methods will update the data fields
     * for the selected objects.
     */
    
    public void propertyChanged(String propname, Object value, Bcsv.Entry data) {
        Object oldval = data.get(propname);
        if(oldval==null) {//the movement inputs have no value before
        	oldval=0.0f;
        }
        if(oldval.getClass() == String.class) data.put(propname, value);
        else if(oldval.getClass() == Integer.class) data.put(propname,(int)value);
        else if(oldval.getClass() == Short.class) data.put(propname,(short)(int)value);
        else if(oldval.getClass() == Byte.class) data.put(propname,(byte)(int)value);
        else if(oldval.getClass() == Float.class) data.put(propname,(float)value);
        else throw new UnsupportedOperationException("UNSUPPORTED PROP TYPE: " +oldval.getClass().getName());
    }
    
    public void propertyPanelPropertyChanged(String propname, Object value) {
    	String axis="";//rotation axis
    	float to_rotate_x=0;//angle to rotate because of the changed value
    	float to_rotate_y=0;
    	float to_rotate_z=0;
    	if(propname.startsWith("group_copy_offset_")) {
    		axis = propname.substring(propname.length()-1);
    		switch(axis){//setting the copy offset
				case "x": this.g_offset_x=(float)value;break;
				case "y": this.g_offset_y=(float)value;break;
				case "z": this.g_offset_z=(float)value;break;
				default:System.out.println("invalid axis");
			}
    	}
    	if(propname.startsWith("group_rotate_center_")){
    		axis = propname.substring(propname.length()-1);
    		switch(axis){//setting the rotation point/center
    			case "x": this.g_center_x=(float)value;break;
    			case "y": this.g_center_y=(float)value;break;
    			case "z": this.g_center_z=(float)value;break;
    			default:System.out.println("invalid axis");
    		}
    	}
    	if(propname.startsWith("group_rotate_angle_")) {
    		axis = propname.substring(propname.length()-1);
    		System.out.println(value.getClass().getName());
    		switch(axis){
    			case "x": to_rotate_x=(float)value-this.g_angle_x;this.g_angle_x=(float)value;break;
    			case "y": to_rotate_y=(float)value-this.g_angle_y;this.g_angle_y=(float)value;to_rotate_y=-to_rotate_y;break;//rotate counter clockwise
    			case "z": to_rotate_z=(float)value-this.g_angle_z;this.g_angle_z=(float)value;break;
    			default:System.out.println("invalid axis");
    		}
    	}
    	boolean moveup=false;
    	if(propname.startsWith("groupmove_")){
    		axis = propname.substring(propname.length()-1);
    		switch(axis){
    			case "x":if(this.g_move_step_x<((float)value)) {//the direction +/- in which the objects will be moved
    						moveup=true;
		    			}
    					this.g_move_step_x=(float)value;
    					break;
    			case "y":if(this.g_move_step_y<(float)value) {
							moveup=true;
		    			}
    					this.g_move_step_y=(float)value;
						break;
    			case "z":if(this.g_move_step_z<(float)value) {
							moveup=true;
		    			}
						this.g_move_step_z=(float)value;
						break;
    			case "a":if(this.g_move_step_a<(float)value) {
							moveup=true;
		    			}
						this.g_move_step_a=(float)value;
						break;
    			default:System.out.println("invalid axis");
    		}
    	}
        for(AbstractObj selectedObj : selectedObjs.values()) {
        	if(propname.startsWith("group_rotate_angle_")) {
        		double newangle;
        		double distance; //distance on the 2 other axis
    			addUndoEntry("changeObj", selectedObj);//required for undoing
        		switch(axis){//axis to rotate around
	    			case "x": if(to_rotate_x!=0) {//if angele = 0 then there is noting to do
			    				RotationMatrix a= new RotationMatrix();
			    				a=a.yawPitchRollToMatrix(selectedObj.rotation.x*Math.PI/180,selectedObj.rotation.y*Math.PI/180,selectedObj.rotation.z*Math.PI/180);//convert the angle of the object in a rotation matrix
			    				
			    				double angle=to_rotate_x/180*Math.PI;//° to radians
			    				RotationMatrix d=new RotationMatrix();//rotation matrix for rotation around x axis
			    				d.r11=Math.cos(angle);
			    				d.r12=-Math.sin(angle);
			    				d.r13=0;
			    				d.r21=Math.sin(angle);
			    				d.r22=Math.cos(angle);
			    				d.r23=0;
			    				d.r31=0;
			    				d.r32=0;
			    				d.r33=1;
			    				RotationMatrix c=a.multiplyMatrices(a, d);//rotate
			    				
			    				double[] angles=c.MatrixToYawPitchRoll(c);//get angles from matrix
			    				selectedObj.rotation.x=(float)(180/Math.PI*angles[0]);//radians to °
			    				selectedObj.rotation.y=(float)(180/Math.PI*angles[1]);
			    				selectedObj.rotation.z=(float)(180/Math.PI*angles[2]);
								if(selectedObj.position.z-this.g_center_z==0 ) {//tan has a period of pi/2; this gets the correct angle
									if(selectedObj.position.y-this.g_center_y==0) {
										newangle=0; //if the object is in the same place as the rotation point there is no change of it's position; this angle is only used for the position
									}else {
										newangle=to_rotate_x/180*Math.PI;
									}
								}else {
									if(selectedObj.position.y-this.g_center_y==0) {
										if(selectedObj.position.z-this.g_center_z>0) {
											newangle=to_rotate_x/180*Math.PI+Math.atan((double)((selectedObj.position.z-this.g_center_z)/(selectedObj.position.y-this.g_center_y)))+Math.PI;//newangle=oldangle+arctan([object zposition-zposition rotation point]/[object yposition-yposition rotation point])
										}else {
											if(selectedObj.position.z-this.g_center_z<0) {
												newangle=to_rotate_x/180*Math.PI+Math.atan((double)((selectedObj.position.z-this.g_center_z)/(selectedObj.position.y-this.g_center_y)));
											}else {
												newangle=0; //postion doesn't change
											}
										}
									}else {
										if(selectedObj.position.y-this.g_center_y>0) {
											newangle=to_rotate_x/180*Math.PI+Math.atan((double)((selectedObj.position.z-this.g_center_z)/(selectedObj.position.y-this.g_center_y)));
										}else {
											newangle=to_rotate_x/180*Math.PI+Math.atan((double)((selectedObj.position.z-this.g_center_z)/(selectedObj.position.y-this.g_center_y)))+Math.PI;
										}
									}
								}
								distance=Math.sqrt((double)((selectedObj.position.z-this.g_center_z)*(selectedObj.position.z-this.g_center_z)+(selectedObj.position.y-this.g_center_y)*(selectedObj.position.y-this.g_center_y)));
								selectedObj.position.z=(float)(Math.sin(newangle)*distance)+this.g_center_z;
								selectedObj.position.y=(float)(Math.cos(newangle)*distance)+this.g_center_y;
        					}
							break;
	    			case "y":if(to_rotate_y!=0) {
			    				RotationMatrix a= new RotationMatrix();
			    				a=a.yawPitchRollToMatrix(selectedObj.rotation.x*Math.PI/180,selectedObj.rotation.y*Math.PI/180,selectedObj.rotation.z*Math.PI/180);
			    				double angle=-(to_rotate_y/180*Math.PI);
			    				RotationMatrix d=new RotationMatrix();
			    				d.r11=Math.cos(angle);//rotation matrix for rotation around y axis
			    				d.r12=0;
			    				d.r13=Math.sin(angle);
			    				d.r21=0;
			    				d.r22=1;
			    				d.r23=0;
			    				d.r31=-Math.sin(angle);
			    				d.r32=0;
			    				d.r33=Math.cos(angle);
			    				RotationMatrix c=a.multiplyMatrices(a, d);
			    				double[] angles=c.MatrixToYawPitchRoll(c);
			    				selectedObj.rotation.x=(float)(180/Math.PI*angles[0]);
			    				selectedObj.rotation.y=(float)(180/Math.PI*angles[1]);
			    				selectedObj.rotation.z=(float)(180/Math.PI*angles[2]);
	    				
								if(selectedObj.position.z-this.g_center_z==0 ) {//tan has a period of pi/2; this gets the correct angle
									if(selectedObj.position.x-this.g_center_x==0) {
										newangle=0; //no position change
									}else {
										if(selectedObj.position.x-this.g_center_x>0) {
											newangle=to_rotate_y/180*Math.PI;
										}else {
											newangle=to_rotate_y/180*Math.PI-Math.PI;
										}
									}
								}else {
									if(selectedObj.position.x-this.g_center_x==0) {
										if(selectedObj.position.z-this.g_center_z>0) {
											newangle=to_rotate_y/180*Math.PI+Math.atan((double)((selectedObj.position.z-this.g_center_z)/(selectedObj.position.x-this.g_center_x)));
										}else {
											if(selectedObj.position.z-this.g_center_z<0) {
												newangle=to_rotate_y/180*Math.PI+Math.atan((double)((selectedObj.position.z-this.g_center_z)/(selectedObj.position.x-this.g_center_x)))+Math.PI;
											}else {
												newangle=0; //no position change
											}
										}
									}else {
										if(selectedObj.position.x-this.g_center_x>0) {
											newangle=to_rotate_y/180*Math.PI+Math.atan((double)((selectedObj.position.z-this.g_center_z)/(selectedObj.position.x-this.g_center_x)));
										}else {
											newangle=to_rotate_y/180*Math.PI+Math.atan((double)((selectedObj.position.z-this.g_center_z)/(selectedObj.position.x-this.g_center_x)))+Math.PI;
										}
									}
								}
								distance=Math.sqrt((double)((selectedObj.position.z-this.g_center_z)*(selectedObj.position.z-this.g_center_z)+(selectedObj.position.x-this.g_center_x)*(selectedObj.position.x-this.g_center_x)));
								selectedObj.position.z=(float)(Math.sin(newangle)*distance)+this.g_center_z;
								selectedObj.position.x=(float)(Math.cos(newangle)*distance)+this.g_center_x;
	    					}			
							break;
	    			case "z": if(to_rotate_z!=0) {
	    						selectedObj.rotation.z+=to_rotate_z; //rotate around z-axis; this always works because rotation aroud z axis is done first, then y then x
								if(selectedObj.rotation.z>360) {
									selectedObj.rotation.z-=360;
								}
								if(selectedObj.rotation.z<-360) {//keep angle between -360° and 360°
									selectedObj.rotation.z+=360;
								}
								if(selectedObj.position.x-this.g_center_x==0 ) {//tan has a period of pi/2; this gets the correct angle
									if(selectedObj.position.y-this.g_center_y==0) {
										if(selectedObj.position.x-this.g_center_x<0) {
											newangle=to_rotate_y/180*Math.PI;
										}else {
											newangle=0;//no position change
										}
									}else {
										if(selectedObj.position.x-this.g_center_x>0) {
											newangle=to_rotate_z/180*Math.PI;
										}else {
											newangle=to_rotate_z/180*Math.PI-Math.PI;
										}
									}
								}else {
									if(selectedObj.position.x-this.g_center_x==0) {
										if(selectedObj.position.z-this.g_center_z>0) {
											newangle=to_rotate_z/180*Math.PI+Math.atan((double)((selectedObj.position.y-this.g_center_y)/(selectedObj.position.x-this.g_center_x)))+Math.PI;//the same as with the x axis but with the other 2 axis
										}else {
											if(selectedObj.position.z-this.g_center_z<0) {
												newangle=to_rotate_z/180*Math.PI+Math.atan((double)((selectedObj.position.y-this.g_center_y)/(selectedObj.position.x-this.g_center_x)));
											}else {
												newangle=0;//no position change
											}
										}
									}else {
										if(selectedObj.position.x-this.g_center_x>0) {
											newangle=to_rotate_z/180*Math.PI+Math.atan((double)((selectedObj.position.y-this.g_center_y)/(selectedObj.position.x-this.g_center_x)));
										}else {
											newangle=to_rotate_z/180*Math.PI+Math.atan((double)((selectedObj.position.y-this.g_center_y)/(selectedObj.position.x-this.g_center_x)))+Math.PI;
										}
									}
								}
								distance=Math.sqrt((double)((selectedObj.position.y-this.g_center_y)*(selectedObj.position.y-this.g_center_y)+(selectedObj.position.x-this.g_center_x)*(selectedObj.position.x-this.g_center_x)));
								selectedObj.position.y=(float)(Math.sin(newangle)*distance)+this.g_center_y;
								selectedObj.position.x=(float)(Math.cos(newangle)*distance)+this.g_center_x;
	    					}
							break;
	    			default:System.out.println("Invalid axis");
	    		}
        		rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                glCanvas.repaint();//redraw the objects
        	}
        	if(propname.startsWith("group_move_")){
        		axis = propname.substring(propname.length()-1);
        		switch (axis) {
        			case "x":this.g_move_x=(float)value;break;
        			case "y":this.g_move_y=(float)value;break;
        			case "z":this.g_move_z=(float)value;break;
        			default: System.out.println("invalid axis");
        		}
        	}
        	if(propname.startsWith("groupmove")){
        		axis = propname.substring(propname.length()-1);
    			addUndoEntry("changeObj", selectedObj);//required for undoing
        		switch(axis){
        			case "x":if(moveup) {
		        				selectedObj.position.x+=this.g_move_x;//it only moves one step at a time
							}else {
								selectedObj.position.x-=this.g_move_x;
							}
        					break;
        			case "y":if(moveup) {
		    	        		selectedObj.position.y+=this.g_move_y;
							}else {
				        		selectedObj.position.y-=this.g_move_y;
							}
							break;
        			case "z":if(moveup) {
		    	        		selectedObj.position.z+=this.g_move_z;
							}else {
				        		selectedObj.position.z-=this.g_move_z;
							}
							break;
        			case "a":if(moveup) {
		        				selectedObj.position.x+=this.g_move_x;//moving in all direction at once
		    	        		selectedObj.position.y+=this.g_move_y;
		    	        		selectedObj.position.z+=this.g_move_z;
        					}else {
        						selectedObj.position.x-=this.g_move_x;
        		        		selectedObj.position.y-=this.g_move_y;
        		        		selectedObj.position.z-=this.g_move_z;
        					}
							break;
        			default:System.out.println("invalid axis");
        		}
        		rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                glCanvas.repaint();
        	}
            // Path point objects, as they work a bit differently
            if(selectedObj instanceof PathPointObj) {
                PathPointObj selectedPathPoint =(PathPointObj) selectedObj;
                
                // Path point coordinates
                if(propname.startsWith("pnt")) {
                    switch(propname) {
                        case "pnt0_x": selectedPathPoint.position.x =(float)value; break;
                        case "pnt0_y": selectedPathPoint.position.y =(float)value; break;
                        case "pnt0_z": selectedPathPoint.position.z =(float)value; break;
                        case "pnt1_x": selectedPathPoint.point1.x =(float)value; break;
                        case "pnt1_y": selectedPathPoint.point1.y =(float)value; break;
                        case "pnt1_z": selectedPathPoint.point1.z =(float)value; break;
                        case "pnt2_x": selectedPathPoint.point2.x =(float)value; break;
                        case "pnt2_y": selectedPathPoint.point2.y =(float)value; break;
                        case "pnt2_z": selectedPathPoint.point2.z =(float)value; break;
                    }
                    
                    rerenderTasks.add("path:" + selectedPathPoint.path.uniqueID);
                    rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                    glCanvas.repaint();
                }
                
                // Path properties
                else if(propname.startsWith("[P]")) {
                    String property = propname.substring(3);
                    switch(property) {
                        case "closed": {
                            selectedPathPoint.path.data.put(property,(boolean) value ? "CLOSE" : "OPEN");
                            rerenderTasks.add("path:" + selectedPathPoint.path.uniqueID);
                            glCanvas.repaint();
                            break;
                        }
                        case "name": {
                            selectedPathPoint.path.name =(String) value;
                            DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                            objlist.nodeChanged(treeNodeList.get(selectedPathPoint.path.uniqueID));
                            break;
                        }
                        case "l_id": {
                            selectedPathPoint.path.pathID =(int) value;
                            DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                            objlist.nodeChanged(treeNodeList.get(selectedPathPoint.path.uniqueID));
                        }
                        default:
                            propertyChanged(property, value, selectedPathPoint.path.data);
                            break;
                    }
                }
                
                else {
                    propertyChanged(propname, value, selectedPathPoint.data);
                }
            }
            
            // Any other object
            else {
                if(propname.equals("name")) {
                    selectedObj.name =(String)value;
                    selectedObj.loadDBInfo();

                    DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                    objlist.nodeChanged(treeNodeList.get(selectedObj.uniqueID));

                    rerenderTasks.add("object:"+Integer.toString(selectedObj.uniqueID));
                    rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                    glCanvas.repaint();
                }
                else if(propname.equals("zone")) {
                    String oldzone = selectedObj.stage.stageName;
                    String newzone =(String)value;
                    int uid = selectedObj.uniqueID;

                    selectedObj.stage = zoneArchives.get(newzone);
                    zoneArchives.get(oldzone).objects.get(selectedObj.layerKey).remove(selectedObj);
                    if(zoneArchives.get(newzone).objects.containsKey(selectedObj.layerKey))
                        zoneArchives.get(newzone).objects.get(selectedObj.layerKey).add(selectedObj);
                    else {
                        selectedObj.layerKey = "common";
                        zoneArchives.get(newzone).objects.get(selectedObj.layerKey).add(selectedObj);
                    }

                    for(int z = 0; z < galaxyArchive.zoneList.size(); z++) {
                        if(!galaxyArchive.zoneList.get(z).equals(newzone))
                            continue;
                        listZones.setSelectedIndex(z);
                        break;
                    }
                    if(treeNodeList.containsKey(uid)) {
                        TreeNode tn = treeNodeList.get(uid);
                        TreePath tp = new TreePath(((DefaultTreeModel)treeObjects.getModel()).getPathToRoot(tn));
                        treeObjects.setSelectionPath(tp);
                        treeObjects.scrollPathToVisible(tp);
                    }

                    selectionChanged();
                    rerenderTasks.add("zone:"+oldzone);
                    rerenderTasks.add("zone:"+newzone);
                    glCanvas.repaint();
                }
                else if(propname.equals("layer")) {
                    String oldlayer = selectedObj.layerKey;
                    String newlayer =((String)value).toLowerCase();

                    selectedObj.layerKey = newlayer;
                    curZoneArc.objects.get(oldlayer).remove(selectedObj);
                    curZoneArc.objects.get(newlayer).add(selectedObj);

                    DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                    objlist.nodeChanged(treeNodeList.get(selectedObj.uniqueID));

                    rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                    glCanvas.repaint();
                }
                else if(propname.startsWith("pos_") || propname.startsWith("dir_") || propname.startsWith("scale_")) {
                    switch(propname) {
                        case "pos_x": selectedObj.position.x =(float)value; break;
                        case "pos_y": selectedObj.position.y =(float)value; break;
                        case "pos_z": selectedObj.position.z =(float)value; break;
                        case "dir_x": selectedObj.rotation.x =(float)value; break;
                        case "dir_y": selectedObj.rotation.y =(float)value; break;
                        case "dir_z": selectedObj.rotation.z =(float)value; break;
                        case "scale_x": selectedObj.scale.x =(float)value; break;
                        case "scale_y": selectedObj.scale.y =(float)value; break;
                        case "scale_z": selectedObj.scale.z =(float)value; break;
                    }

                    if(propname.startsWith("scale_") && selectedObj.renderer.hasSpecialScaling())
                        rerenderTasks.add("object:"+Integer.toString(selectedObj.uniqueID));
                    
                    if (selectedObj instanceof StageObj) {
                        rerenderTasks.add("zone:"+selectedObj.name);
                    }
                    else {
                        rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                    }
                    glCanvas.repaint();
                }
                else {
                    propertyChanged(propname, value, selectedObj.data);
                    if(propname.startsWith("Obj_arg")) {
                        int argnum = Integer.parseInt(propname.substring(7));
                        if(selectedObj.renderer.boundToObjArg(argnum)) {
                            rerenderTasks.add("object:"+Integer.toString(selectedObj.uniqueID));
                            rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                            glCanvas.repaint();
                        }
                    }
                    else if (propname.equals("ShapeModelNo")) {
                        rerenderTasks.add("object:"+Integer.toString(selectedObj.uniqueID));
                        rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                        glCanvas.repaint();
                    }
                    else if(propname.equals("Range")) {
                        if(selectedObj.renderer.boundToProperty()) {
                            rerenderTasks.add("object:"+Integer.toString(selectedObj.uniqueID));
                            rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                            glCanvas.repaint();
                        }
                    }
                    else if(propname.equals("AreaShapeNo")) {
                        DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                        objlist.nodeChanged(treeNodeList.get(selectedObj.uniqueID));
                        if(selectedObj.getClass() == AreaObj.class || selectedObj.getClass() == CameraObj.class) {
                            rerenderTasks.add("object:"+Integer.toString(selectedObj.uniqueID));
                            rerenderTasks.add("zone:"+selectedObj.stage.stageName);
                            glCanvas.repaint();
                        }
                    }
                    else if(propname.equals("MarioNo") || propname.equals("PosName") || propname.equals("DemoName") || propname.equals("TimeSheetName")) {
                        DefaultTreeModel objlist =(DefaultTreeModel)treeObjects.getModel();
                        objlist.nodeChanged(treeNodeList.get(selectedObj.uniqueID));
                    }
                }
            }
        }
        
        unsavedChanges = true;
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Actual galaxy scene renderer
    
    /**
     * Rerenders all objects in all zones.
     */
    public void renderAllObjects() {
        for(String zone : zoneArchives.keySet())
            rerenderTasks.add("zone:" + zone);
        
        glCanvas.repaint();
    }
    
    /**
     * Adds {@code task} to the queue if it is not already in the rerender queue.
     * @param task the task to add
     */
    public void addRerenderTask(String task) {
        if(!rerenderTasks.contains(task))
            rerenderTasks.add(task);
    }
    
    private class GalaxyRenderer implements GLEventListener, MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
        private class AsyncPrerenderer implements Runnable {
            private final GL2 gl;
            
            public AsyncPrerenderer(GL2 gl) {
                this.gl = gl;
            }
            
            @Override
            public void run() {
                try {
                    gl.getContext().makeCurrent();
                    
                    if (isGalaxyMode) {
                        for (AbstractObj obj : globalObjList.values()) {
                            obj.initRenderer(renderInfo);
                            obj.oldName = obj.name;
                        }
                        
                        for (PathObj obj : globalPathList.values()) {
                            obj.prerender(renderInfo);
                        }
                    }
                    
                    renderInfo.renderMode = GLRenderer.RenderMode.PICKING; renderAllObjects(gl);
                    renderInfo.renderMode = GLRenderer.RenderMode.OPAQUE; renderAllObjects(gl);
                    renderInfo.renderMode = GLRenderer.RenderMode.TRANSLUCENT; renderAllObjects(gl);
                    
                    gl.getContext().release();
                    glCanvas.repaint();
                    setDefaultStatus();
                }
                catch(GLException ex) {
                    lblStatus.setForeground(Color.red);
                    lblStatus.setText("Failed to render level!" + ex.getMessage());
                    System.out.println(ex);
                }
            }
        }
        
        private static final float FOV = (float)((70.0 * Math.PI) / 180.0);
        private static final float Z_NEAR = 0.001f;
        private static final float Z_FAR = 1000f;
        
        public GalaxyRenderer() {
            super();
        }
        
        @Override
        public void init(GLAutoDrawable glad) {
            GL2 gl = glad.getGL().getGL2();
            
            RendererCache.setRefContext(glad.getContext());
            
            
            renderInfo = new GLRenderer.RenderInfo();
            renderInfo.drawable = glad;
            renderInfo.renderMode = GLRenderer.RenderMode.OPAQUE;
            
            // Place the camera behind the first entrance
            AbstractObj startObj = getScenarioStart();
            
            if(startObj != null) {
                camDistance = 0.125f;
                
                camTarget.x = startObj.position.x / SCALE_DOWN;
                camTarget.y = startObj.position.y / SCALE_DOWN;
                camTarget.z = startObj.position.z / SCALE_DOWN;
                
                camRotation.y =(float)Math.PI / 8f;
                camRotation.x =(-startObj.rotation.y - 90f) *(float)Math.PI / 180f;
            }
            
            updateCamera();
            
            for(int s = 0; s <(isGalaxyMode ? galaxyArchive.scenarioData.size() : 1); s++)
                zoneDisplayLists.put(s, new int[] {0,0,0});
            
            gl.glFrontFace(GL2.GL_CW);
            
            gl.glClearColor(0.118f, 0.118f, 0.784f, 1f);
            gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
            lblStatus.setText("Prerendering "+(isGalaxyMode?"galaxy":"zone")+", please wait...");
            
            SwingUtilities.invokeLater(new GalaxyRenderer.AsyncPrerenderer(gl));
            
            initializedRenderer = true;
        }
        
        private void renderSelectHighlight(GL2 gl, String zone)  {
            boolean gotany = false;
            for(AbstractObj obj : selectedObjs.values()) {
                if(obj.stage.stageName.equals(zone)) {
                    gotany = true;
                    break;
                }
            }
            if(!gotany) return;
            
            RenderMode oldmode = doHighLightSettings(gl);
            
            for(AbstractObj obj : selectedObjs.values()) {
                if(obj.stage.stageName.equals(zone) && !(obj instanceof PathPointObj))
                    obj.render(renderInfo);
            }
            
            gl.glDisable(GL2.GL_POLYGON_OFFSET_FILL);
            renderInfo.renderMode = oldmode;
        }
        
        private void renderAllObjects(GL2 gl) {
            int mode = -1;
            switch(renderInfo.renderMode) {
                case PICKING: mode = 0; break;
                case OPAQUE: mode = 1; break;
                case TRANSLUCENT: mode = 2; break;
            }
            
            if(isGalaxyMode) {
                for(String zone : galaxyArchive.zoneList)
                    prerenderZone(gl, zone);
                
                for(int s = 0; s < galaxyArchive.scenarioData.size(); s++) {

                    int dl = zoneDisplayLists.get(s)[mode];
                    if(dl == 0) {
                        dl = gl.glGenLists(1);
                        zoneDisplayLists.get(s)[mode] = dl;
                    }
                    gl.glNewList(dl, GL2.GL_COMPILE);
                    
                    Bcsv.Entry scenario = galaxyArchive.scenarioData.get(s);
                    renderZone(gl, scenario, galaxyName,(int)scenario.get(galaxyName), 0);

                    gl.glEndList();
                }
            } else {
                prerenderZone(gl, galaxyName);
                
                if(!zoneDisplayLists.containsKey(0))
                     zoneDisplayLists.put(0, new int[] {0,0,0});

                int dl = zoneDisplayLists.get(0)[mode];
                if(dl == 0) {
                    dl = gl.glGenLists(1);
                    zoneDisplayLists.get(0)[mode] = dl;
                }
                gl.glNewList(dl, GL2.GL_COMPILE);

                renderZone(gl, null, galaxyName, zoneModeLayerBitmask, 99);

                gl.glEndList();
            }
        }
        
        private void prerenderZone(GL2 gl, String zone) {
            int mode = -1;
            switch(renderInfo.renderMode) {
                case PICKING: mode = 0; break;
                case OPAQUE: mode = 1; break;
                case TRANSLUCENT: mode = 2; break;
            }
            
            StageArchive zonearc = zoneArchives.get(zone);
            Set<String> layers = zonearc.objects.keySet();
            for(String layer : layers) {
                String key = zone + "/" + layer.toLowerCase();
                if(!objDisplayLists.containsKey(key))
                    objDisplayLists.put(key, new int[] {0,0,0});
                
                int dl = objDisplayLists.get(key)[mode];
                if(dl == 0) { 
                    dl = gl.glGenLists(1); 
                    objDisplayLists.get(key)[mode] = dl;
                }
                
                gl.glNewList(dl, GL2.GL_COMPILE);
                
                for(AbstractObj obj : zonearc.objects.get(layer)) {
                    if(mode == 0) {
                        int uniqueid = obj.uniqueID << 3;
                        // set color to the object's uniqueID(RGB)
                        gl.glColor4ub(
                               (byte)(uniqueid >>> 16), 
                               (byte)(uniqueid >>> 8), 
                               (byte)uniqueid, 
                               (byte)0xFF);
                    }
                    
                    final String c = obj.getClass().getSimpleName();
                    switch(c) {
                        case "AreaObj":
                            if(tgbShowAreas.isSelected())
                                obj.render(renderInfo);
                            break;
                        case "CameraObj":
                            if(tgbShowCameras.isSelected())
                                obj.render(renderInfo);
                            break;
                        default:
                            obj.render(renderInfo);
                    }
                }
                
                if(mode == 2 && !selectedObjs.isEmpty())
                    renderSelectHighlight(gl, zone);
                
                // path rendering -- be lazy and hijack the display lists used for the Common objects
                if(layer.equalsIgnoreCase("common")) {
                    for(PathObj pobj : zonearc.paths) {
                        if(!tgbShowPaths.isSelected() && // isSelected? intuitive naming ftw :/
                                !displayedPaths.containsKey(pobj.pathID))
                            continue;
                        
                        pobj.render(renderInfo);
                        
                        if(mode == 1) {
                            PathPointObj ptobj = displayedPaths.get(pobj.pathID);
                            if(ptobj != null) {
                                ptobj.render(renderInfo, selectionArg);
                            }
                        }
                    }
                }
                
                gl.glEndList();
            }
        }
        
        private void renderZone(GL2 gl, Bcsv.Entry scenario, String zone, int layermask, int level) {
            String alphabet = "abcdefghijklmnop";
            int mode = -1;
            switch(renderInfo.renderMode) {
                case PICKING: mode = 0; break;
                case OPAQUE: mode = 1; break;
                case TRANSLUCENT: mode = 2; break;
            }
            
            gl.glCallList(objDisplayLists.get(zone + "/common")[mode]);
            
            for (int l = 0; l < 16; l++) {
                if((layermask & (1 << l)) != 0)
                    gl.glCallList(objDisplayLists.get(zone + "/layer" + alphabet.charAt(l))[mode]);
            }
            
            if (level < 5) {
                for(StageObj subzone : zoneArchives.get(zone).zones.get("common")) {
                    gl.glPushMatrix();
                    gl.glTranslatef(subzone.position.x, subzone.position.y, subzone.position.z);
                    gl.glRotatef(subzone.rotation.z, 0f, 0f, 1f);
                    gl.glRotatef(subzone.rotation.y, 0f, 1f, 0f);
                    gl.glRotatef(subzone.rotation.x, 1f, 0f, 0f);

                    String zonename = subzone.name;
                    renderZone(gl, scenario, zonename,(int)scenario.get(zonename), level + 1);

                    gl.glPopMatrix();
                }
                
                for(int l = 0; l < 16; l++) {
                    if((layermask &(1 << l)) != 0) {
                        for(StageObj subzone : zoneArchives.get(zone).zones.get("layer" + alphabet.charAt(l))) {
                            gl.glPushMatrix();
                            gl.glTranslatef(subzone.position.x, subzone.position.y, subzone.position.z);
                            gl.glRotatef(subzone.rotation.z, 0f, 0f, 1f);
                            gl.glRotatef(subzone.rotation.y, 0f, 1f, 0f);
                            gl.glRotatef(subzone.rotation.x, 1f, 0f, 0f);

                            String zonename = subzone.name;
                            renderZone(gl, scenario, zonename,(int)scenario.get(zonename), level + 1);

                            gl.glPopMatrix();
                        }
                    }
                }
            }
        }
        
        @Override
        public void dispose(GLAutoDrawable glad) {
            GL2 gl = glad.getGL().getGL2();
            renderInfo.drawable = glad;
            
            for(int[] dls : zoneDisplayLists.values()) {
                gl.glDeleteLists(dls[0], 1);
                gl.glDeleteLists(dls[1], 1);
                gl.glDeleteLists(dls[2], 1);
            }
            
            for(int[] dls : objDisplayLists.values()) {
                gl.glDeleteLists(dls[0], 1);
                gl.glDeleteLists(dls[1], 1);
                gl.glDeleteLists(dls[2], 1);
            }
            
            if(parentForm == null) {
                for(AbstractObj obj : globalObjList.values())
                    obj.closeRenderer(renderInfo);
            }
            
            RendererCache.clearRefContext();
        }
        
        private void doRerenderTasks() {
            try {
                GL2 gl = renderInfo.drawable.getGL().getGL2();

                while(!rerenderTasks.isEmpty()) {
                    String[] task = rerenderTasks.poll().split(":");
                    switch(task[0]) {
                        case "zone":
                            renderInfo.renderMode = GLRenderer.RenderMode.PICKING;      renderAllObjects(gl);
                            renderInfo.renderMode = GLRenderer.RenderMode.OPAQUE;       prerenderZone(gl, task[1]);
                            renderInfo.renderMode = GLRenderer.RenderMode.TRANSLUCENT;  prerenderZone(gl, task[1]);
                            break;

                        case "object":
                            {
                                int objid = Integer.parseInt(task[1]);
                                AbstractObj obj = globalObjList.get(objid);
                                obj.closeRenderer(renderInfo);
                                obj.initRenderer(renderInfo);
                                obj.oldName = obj.name;
                            }
                            break;

                        case "addobj":
                            {
                                int objid = Integer.parseInt(task[1]);
                                AbstractObj obj = globalObjList.get(objid);
                                obj.initRenderer(renderInfo);
                                obj.oldName = obj.name;
                            }
                            break;

                        case "delobj":
                            {
                                int objid = Integer.parseInt(task[1]);
                                AbstractObj obj = globalObjList.get(objid);
                                obj.closeRenderer(renderInfo);
                                globalObjList.remove(obj.uniqueID);
                            }
                            break;

                        case "allobjects":
                            renderInfo.renderMode = GLRenderer.RenderMode.PICKING;      renderAllObjects(gl);
                            renderInfo.renderMode = GLRenderer.RenderMode.OPAQUE;       renderAllObjects(gl);
                            renderInfo.renderMode = GLRenderer.RenderMode.TRANSLUCENT;  renderAllObjects(gl);
                            break;

                        case "path":
                            {
                                int pathid = Integer.parseInt(task[1]);
                                PathObj pobj = globalPathList.get(pathid);
                                pobj.prerender(renderInfo);
                            }
                            break;
                    }
                }
            } catch(GLException ex) {
                lblStatus.setText("Failed to render level!" + ex.getMessage());
                lblStatus.setOpaque(true);
                lblStatus.setVisible(false);
                lblStatus.setForeground(Color.red);
                lblStatus.setVisible(true);
            }
        }
        
        @Override
        public void display(GLAutoDrawable glad) {
            if(!initializedRenderer) return;
            GL2 gl = glad.getGL().getGL2();
            renderInfo.drawable = glad;
            
            doRerenderTasks();
            
            // Rendering pass 1 -- fakecolor rendering
            // the results are used to determine which object is clicked
            
            gl.glClearColor(0.118f, 0.118f, 0.784f, 1f);
            gl.glClearDepth(1f);
            gl.glClearStencil(0);
            gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT | GL2.GL_STENCIL_BUFFER_BIT);
            
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glLoadMatrixf(modelViewMatrix.m, 0);
            
            try { gl.glUseProgram(0); } catch(GLException ex) { }
            gl.glDisable(GL2.GL_ALPHA_TEST);
            gl.glDisable(GL2.GL_BLEND);
            gl.glDisable(GL2.GL_COLOR_LOGIC_OP);
            gl.glDisable(GL2.GL_LIGHTING);
            gl.glDisable(GL2.GL_DITHER);
            gl.glDisable(GL2.GL_POINT_SMOOTH);
            gl.glDisable(GL2.GL_LINE_SMOOTH);
            gl.glDisable(GL2.GL_POLYGON_SMOOTH);
            if(gl.isFunctionAvailable("glActiveTexture")) {
                for(int i = 0; i < 8; i++) {
                    try {
                        gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
                        gl.glDisable(GL2.GL_TEXTURE_2D);
                    } catch(GLException ex) {}
                }
            }
            gl.glDisable(GL2.GL_TEXTURE_2D);
            
            gl.glCallList(zoneDisplayLists.get(curScenarioID)[0]);
            
            gl.glDepthMask(true);
            
            gl.glFlush();
            
            gl.glReadPixels(mousePos.x - 1, glad.getSurfaceHeight() - mousePos.y + 1, 3, 3, GL2.GL_BGRA, GL2.GL_UNSIGNED_INT_8_8_8_8_REV,
                                                                                                                                pickingFrameBuffer);
            gl.glReadPixels(mousePos.x, glad.getSurfaceHeight() - mousePos.y, 1, 1, GL2.GL_DEPTH_COMPONENT, GL2.GL_FLOAT, pickingDepthBuffer);
            pickingDepth = -(Z_FAR * Z_NEAR /(pickingDepthBuffer.get(0) *(Z_FAR - Z_NEAR) - Z_FAR));
            
            if (Settings.getDebugFakeColor()) {
                glad.swapBuffers();
                return;
            }
           
            // Rendering pass 2 -- standard rendering
            //(what the user will see)

            gl.glClearColor(0.118f, 0.118f, 0.784f, 1f);
            gl.glClearDepth(1f);
            gl.glClearStencil(0);
            gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT | GL2.GL_STENCIL_BUFFER_BIT);
            
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glLoadMatrixf(modelViewMatrix.m, 0);
            
            gl.glEnable(GL2.GL_TEXTURE_2D);
            
            if(Settings.getDebugFastDrag()) {
                if(isDragging) {
                    gl.glPolygonMode(GL2.GL_FRONT, GL2.GL_LINE);
                    gl.glPolygonMode(GL2.GL_BACK, GL2.GL_POINT);
                }
                else 
                    gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_FILL);
            }
            
            gl.glCallList(zoneDisplayLists.get(curScenarioID)[1]);
            
            gl.glCallList(zoneDisplayLists.get(curScenarioID)[2]);
            
            gl.glDepthMask(true);
            try { gl.glUseProgram(0); } catch(GLException ex) { }
            if(gl.isFunctionAvailable("glActiveTexture")) {
                for(int i = 0; i < 8; i++) {
                    try {
                        gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
                        gl.glDisable(GL2.GL_TEXTURE_2D);
                    }
                    catch(GLException ex) {}
                }
            }
            
            gl.glDisable(GL2.GL_TEXTURE_2D);
            gl.glDisable(GL2.GL_BLEND);
            gl.glDisable(GL2.GL_ALPHA_TEST);
            
            if(tgbShowAxis.isSelected()) {
                gl.glBegin(GL2.GL_LINES);
                gl.glColor4f(1f, 0f, 0f, 1f);
                gl.glVertex3f(0f, 0f, 0f);
                gl.glVertex3f(100000f, 0f, 0f);
                gl.glColor4f(0f, 1f, 0f, 1f);
                gl.glVertex3f(0f, 0f, 0f);
                gl.glVertex3f(0, 100000f, 0f);
                gl.glColor4f(0f, 0f, 1f, 1f);
                gl.glVertex3f(0f, 0f, 0f);
                gl.glVertex3f(0f, 0f, 100000f);
                gl.glEnd();
            }
            
            // Apparently this prevents the infamous flickering glitch
            // glad.swapBuffers();
        }

        private RenderMode doHighLightSettings(GL2 gl) {
            try { gl.glUseProgram(0); } catch(GLException ex) { }
            if(gl.isFunctionAvailable("glActiveTexture")) {
                for(int i = 0; i < 8; i++) {
                    try {
                        gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
                        gl.glDisable(GL2.GL_TEXTURE_2D);
                    }
                    catch(GLException ex) {}
                }
            }
            gl.glDisable(GL2.GL_TEXTURE_2D);
            gl.glEnable(GL2.GL_BLEND);
            if(gl.isFunctionAvailable("glBlendEquation")) {
                gl.glBlendEquation(GL2.GL_FUNC_ADD);
                gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
            }
            gl.glDisable(GL2.GL_COLOR_LOGIC_OP);
            gl.glDisable(GL2.GL_ALPHA_TEST);
            gl.glDepthMask(false);
            gl.glEnable(GL2.GL_POLYGON_OFFSET_FILL);
            gl.glPolygonOffset(-1f, -1f);
            renderInfo.drawable = glCanvas;
            RenderMode oldmode = renderInfo.renderMode;
            renderInfo.renderMode = RenderMode.PICKING;
            gl.glColor4f(1f, 1f, 0.75f, 0.3f);
            return oldmode;
        }
        
        @Override
        public void reshape(GLAutoDrawable glad, int x, int y, int width, int height) {
            if(!initializedRenderer) return;
            
            GL2 gl = glad.getGL().getGL2();
            gl.glViewport(x, y, width, height);
            
            float aspectRatio =(float)width /(float)height;
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glLoadIdentity();
            float ymax = Z_NEAR *(float)Math.tan(0.5f * FOV);
            gl.glFrustum(-ymax * aspectRatio, ymax * aspectRatio,
                    -ymax, ymax,
                    Z_NEAR, Z_FAR);
            
            pixelFactorX =(2f * (float) Math.tan(FOV * 0.5f) * aspectRatio) / (float) width;
            pixelFactorY =(2f * (float) Math.tan(FOV * 0.5f)) / (float) height;
        }
        
        public void updateCamera() {
            Vec3f up;
            
            if(Math.cos(camRotation.y) < 0f) {
                isUpsideDown = true;
                up = new Vec3f(0f, -1f, 0f);
            }
            else {
                isUpsideDown = false;
                up = new Vec3f(0f, 1f, 0f);
            }
            
            camPosition.x = camDistance *(float)Math.cos(camRotation.x) *(float)Math.cos(camRotation.y);
            camPosition.y = camDistance *(float)Math.sin(camRotation.y);
            camPosition.z = camDistance *(float)Math.sin(camRotation.x) *(float)Math.cos(camRotation.y);
            
            Vec3f.add(camPosition, camTarget, camPosition);
            
            modelViewMatrix = Matrix4.lookAt(camPosition, camTarget, up);
            Matrix4.mult(Matrix4.scale(1f / SCALE_DOWN), modelViewMatrix, modelViewMatrix);
        }
        

        @Override
        public void mouseDragged(MouseEvent e) {
            if(!initializedRenderer) return;
            
            float xdelta = e.getX() - mousePos.x;
            float ydelta = e.getY() - mousePos.y;
            
            if(!isDragging && (Math.abs(xdelta) >= 3f || Math.abs(ydelta) >= 3f)) {
                pickingCapture = true;
                isDragging = true;
            }
            
            if(!isDragging)
                return;
            
            if(pickingCapture) {
                underCursor = pickingFrameBuffer.get(4) & 0xFFFFFF;
                depthUnderCursor = pickingDepth;
                pickingCapture = false;
            }
            
            mousePos = e.getPoint();
            
            if(!selectedObjs.isEmpty() && selectedObjs.containsKey(underCursor >>> 3)) {
                if(mouseButton == MouseEvent.BUTTON1) { // left click
                    float objz = depthUnderCursor;
                    
                    xdelta *= pixelFactorX * objz * SCALE_DOWN;
                    ydelta *= -pixelFactorY * objz * SCALE_DOWN;
                    
                    Vec3f delta = new Vec3f(
                           (xdelta *(float)Math.sin(camRotation.x)) -(ydelta *(float)Math.sin(camRotation.y) *(float)Math.cos(camRotation.x)),
                            ydelta *(float)Math.cos(camRotation.y),
                            -(xdelta *(float)Math.cos(camRotation.x)) -(ydelta *(float)Math.sin(camRotation.y) *(float)Math.sin(camRotation.x)));
                    applySubzoneRotation(delta);
                    offsetSelectionBy(delta);
                    
                    unsavedChanges = true;
                }
            } else {
                if(mouseButton == MouseEvent.BUTTON3) { // right click
                    if(isUpsideDown) xdelta = -xdelta;
                    
                    if(!Settings.getUseReverseRot()) {
                        xdelta = -xdelta;
                        ydelta = -ydelta ;
                    }
                    
                    if(underCursor == 0xFFFFFF || depthUnderCursor > camDistance) {
                        xdelta *= 0.002f;
                        ydelta *= 0.002f;
                        
                        camRotation.x -= xdelta;
                        camRotation.y -= ydelta;
                    }
                    else {
                        xdelta *= 0.002f;
                        ydelta *= 0.002f;
                        
                        float diff = camDistance - depthUnderCursor;
                        camTarget.x += diff * Math.cos(camRotation.x) * Math.cos(camRotation.y);
                        camTarget.y += diff * Math.sin(camRotation.y);
                        camTarget.z += diff * Math.sin(camRotation.x) * Math.cos(camRotation.y);
                        
                        camRotation.x -= xdelta;
                        camRotation.y -= ydelta;
                        
                        camTarget.x -= diff * Math.cos(camRotation.x) * Math.cos(camRotation.y);
                        camTarget.y -= diff * Math.sin(camRotation.y);
                        camTarget.z -= diff * Math.sin(camRotation.x) * Math.cos(camRotation.y);
                    }
                }
                else if(mouseButton == MouseEvent.BUTTON1) {
                    if(underCursor == 0xFFFFFF) {
                        xdelta *= 0.005f;
                        ydelta *= 0.005f;
                    }
                    else {
                        xdelta *= Math.min(0.005f, pixelFactorX * depthUnderCursor);
                        ydelta *= Math.min(0.005f, pixelFactorY * depthUnderCursor);
                    }

                    camTarget.x -= xdelta *(float)Math.sin(camRotation.x);
                    camTarget.x -= ydelta *(float)Math.cos(camRotation.x) *(float)Math.sin(camRotation.y);
                    camTarget.y += ydelta *(float)Math.cos(camRotation.y);
                    camTarget.z += xdelta *(float)Math.cos(camRotation.x);
                    camTarget.z -= ydelta *(float)Math.sin(camRotation.x) *(float)Math.sin(camRotation.y);
                }

                updateCamera();
            }
            e.getComponent().repaint();
        }
        
        @Override
        public void mouseMoved(MouseEvent e) {
            if(!initializedRenderer) return;
            
            mousePos = e.getPoint();
            
            if(startingMousePos == null)
            {
                System.out.println("reset startingMousePos");
                startingMousePos = new Point(1, 1);
            }
            
            if(glCanvas.isFocusOwner())
            {
                if(keyTranslating)
                    keyTranslating(e.isShiftDown(), e.isControlDown());
                if(keyScaling)
                    keyScaling(e.isShiftDown(), e.isControlDown());
                if(keyRotating)
                    keyRotating(e.isShiftDown(), e.isControlDown());
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {}

        @Override
        public void mousePressed(MouseEvent e) {
            if(!initializedRenderer) return;
            if(mouseButton != MouseEvent.NOBUTTON) return;
            
            mouseButton = e.getButton();
            mousePos = e.getPoint();
            
            isDragging = false;
            keyTranslating = false;
            keyScaling = false;
            keyRotating = false;
            keyAxis = "all";
            e.getComponent().repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if(!initializedRenderer) return;
            if(e.getButton() != mouseButton) return;
            
            mouseButton = MouseEvent.NOBUTTON;
            mousePos = e.getPoint();
            boolean shiftpressed = e.isShiftDown();
            boolean ctrlpressed = e.isControlDown();
            if(keyTranslating == false && keyScaling == false && keyRotating == false) {
                if(isDragging) {
                    isDragging = false;
                    if(Settings.getDebugFastDrag()) e.getComponent().repaint();
                    return;
                }

                int val = pickingFrameBuffer.get(4);
                if(    val != pickingFrameBuffer.get(1) ||
                        val != pickingFrameBuffer.get(3) ||
                        val != pickingFrameBuffer.get(5) ||
                        val != pickingFrameBuffer.get(7))
                    return;

                val &= 0xFFFFFF;
                int objid = val >>> 3;
                int arg = val & 0x7;
                if(objid != 0xFFFFFF && !globalObjList.containsKey(objid))
                    return;


                AbstractObj theobject = globalObjList.get(objid);
                int oldarg = selectionArg;
                selectionArg = 0;

                if(e.getButton() == MouseEvent.BUTTON3) {
                    // right click: cancels current add/delete command

                    if(!addingObject.isEmpty()) {
                        addingObject = "";
                        tgbAddObject.setSelected(false);
                        setDefaultStatus();
                    }
                    else if(deletingObjects) {
                        deletingObjects = false;
                        tgbDeleteObject.setSelected(false);
                        setDefaultStatus();
                    }
                }
                else {
                    // left click: places/deletes objects or selects

                    if(!addingObject.isEmpty()) {
                        addObject(mousePos);

                        if(!addingObject.startsWith("path")) {
                            undoList.add(new UndoEntry("addObj", newobj));
                            undoIndex++;
                        }
        
                        if(!shiftpressed) {
                            addingObject = "";
                            tgbAddObject.setSelected(false);
                            setDefaultStatus();
                        }
                    } else if(deletingObjects) {
                        deleteObject(objid);
                        if(!shiftpressed)  {
                            deletingObjects = false;
                            tgbDeleteObject.setSelected(false);
                            setDefaultStatus();
                        }                    
                    }
                    else {
                        // multiselect behavior
                        // Ctrl not pressed:
                        // * clicking an object selects/deselects it
                        // * selecting an object deselects the previous selection
                        // Ctrl pressed:
                        // * clicking an object adds it to the selection
                        // * or removes it if it's already selected

                        boolean wasselected = false;

                        if(ctrlpressed || shiftpressed) {
                            if(selectedObjs.containsKey(objid))
                                selectedObjs.remove(objid);
                            else {
                                selectedObjs.put(objid, theobject);
                                wasselected = true;
                            }
                        }
                        else {
                            LinkedHashMap<Integer, AbstractObj> oldsel = null;

                            if(!selectedObjs.isEmpty() && arg == oldarg) {
                                oldsel =(LinkedHashMap<Integer, AbstractObj>)selectedObjs.clone();

                                for(AbstractObj unselobj : oldsel.values()) {
                                    if(treeNodeList.containsKey(unselobj.uniqueID)) {
                                        TreeNode tn = treeNodeList.get(unselobj.uniqueID);
                                        TreePath tp = new TreePath(((DefaultTreeModel)treeObjects.getModel()).getPathToRoot(tn));
                                        treeObjects.removeSelectionPath(tp);
                                    }
                                    else
                                        addRerenderTask("zone:"+unselobj.stage.stageName);
                                }

                                selectionChanged();
                                selectedObjs.clear();
                            }

                            if(oldsel == null || !oldsel.containsKey(theobject.uniqueID) || arg != oldarg) {
                                selectedObjs.put(theobject.uniqueID, theobject);
                                wasselected = true;
                            }
                        }
                        int z;
                        for(z = 0; z < listZones.getModel().getSize(); z++) {
                            if(!listZones.getModel().getElementAt(z).toString().contains(theobject.stage.stageName))
                                continue;
                            listZones.setSelectedIndex(z);
                            break;
                        }
                        addRerenderTask("zone:"+theobject.stage.stageName);

                        if(wasselected) {
                            if(selectedObjs.size() == 1) {
                                if(isGalaxyMode) {
                                    String zone = selectedObjs.values().iterator().next().stage.stageName;
                                    listZones.setSelectedValue(zone, true);
                                }

                                selectionArg = arg;
                            }
                            tabData.setSelectedIndex(1);

                            // if the object is in the TreeView, all we have to do is tell the TreeView to select it
                            // and the rest will be handled there
                            if(treeNodeList.containsKey(objid)) {
                                TreeNode tn = treeNodeList.get(objid);
                                TreePath tp = new TreePath(((DefaultTreeModel)treeObjects.getModel()).getPathToRoot(tn));
                                if(ctrlpressed || shiftpressed)
                                    treeObjects.addSelectionPath(tp);
                                else
                                    treeObjects.setSelectionPath(tp);
                                treeObjects.scrollPathToVisible(tp);
                            }
                            else {
                                addRerenderTask("zone:"+theobject.stage.stageName);
                                selectionChanged();
                            }
                        } else {
                            if(treeNodeList.containsKey(objid)) {
                                TreeNode tn = treeNodeList.get(objid);
                                TreePath tp = new TreePath(((DefaultTreeModel)treeObjects.getModel()).getPathToRoot(tn));
                                treeObjects.removeSelectionPath(tp);
                            } else {
                                addRerenderTask("zone:"+theobject.stage.stageName);
                                selectionChanged();
                            }
                        }
                    }
                }

                e.getComponent().repaint();
            }
        }

        @Override
        public void mouseEntered(MouseEvent e) {}

        @Override
        public void mouseExited(MouseEvent e) {}

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if(!initializedRenderer) return;
            
            if(mouseButton == MouseEvent.BUTTON1 && !selectedObjs.isEmpty() && selectedObjs.containsKey(underCursor >>> 3)) {
                float delta =(float)e.getPreciseWheelRotation();
                delta =((delta < 0f) ? -1f:1f) *(float)Math.pow(delta, 2f) * 0.05f * SCALE_DOWN;
                
                Vec3f vdelta = new Vec3f(
                        delta *(float)Math.cos(camRotation.x) *(float)Math.cos(camRotation.y),
                        delta *(float)Math.sin(camRotation.y),
                        delta *(float)Math.sin(camRotation.x) *(float)Math.cos(camRotation.y));
                
                float xdist = delta *(mousePos.x -(glCanvas.getWidth() / 2f)) * pixelFactorX;
                float ydist = delta *(mousePos.y -(glCanvas.getHeight() / 2f)) * pixelFactorY;
                vdelta.x += -(xdist *(float)Math.sin(camRotation.x)) -(ydist *(float)Math.sin(camRotation.y) *(float)Math.cos(camRotation.x));
                vdelta.y += ydist *(float)Math.cos(camRotation.y);
                vdelta.z +=(xdist *(float)Math.cos(camRotation.x)) -(ydist *(float)Math.sin(camRotation.y) *(float)Math.sin(camRotation.x));
                
                applySubzoneRotation(vdelta);
                offsetSelectionBy(vdelta);
                
                unsavedChanges = true;
            } else {
                float delta =(float)(e.getPreciseWheelRotation() * Math.min(0.1f, pickingDepth / 10f));
                
                Vec3f vdelta = new Vec3f(
                        delta *(float)Math.cos(camRotation.x) *(float)Math.cos(camRotation.y),
                        delta *(float)Math.sin(camRotation.y),
                        delta *(float)Math.sin(camRotation.x) *(float)Math.cos(camRotation.y));
                
                float xdist = delta *(mousePos.x -(glCanvas.getWidth() / 2f)) * pixelFactorX;
                float ydist = delta *(mousePos.y -(glCanvas.getHeight() / 2f)) * pixelFactorY;
                vdelta.x += -(xdist *(float)Math.sin(camRotation.x)) -(ydist *(float)Math.sin(camRotation.y) *(float)Math.cos(camRotation.x));
                vdelta.y += ydist *(float)Math.cos(camRotation.y);
                vdelta.z +=(xdist *(float)Math.cos(camRotation.x)) -(ydist *(float)Math.sin(camRotation.y) *(float)Math.sin(camRotation.x));
                
                int mult = 1;
                
                // Fast scroll
                if(e.isShiftDown())
                    mult = 3;
                
                camTarget.x += vdelta.x * mult;
                camTarget.y += vdelta.y * mult;
                camTarget.z += vdelta.z * mult;

                updateCamera();
            }
            
            pickingCapture = true;
            e.getComponent().repaint();
        }
        
        @Override
        public void keyTyped(KeyEvent e) { }
        
        @Override
        public void keyReleased(KeyEvent e) {}

        @Override
        public void keyPressed(KeyEvent e) {
            if (!glCanvas.isFocusOwner()) {
                return;
            }
            
            int keyCode = e.getKeyCode();
            
            // Delete objects -- DEL
            if (keyCode == KeyEvent.VK_DELETE) {
                tgbDeleteObject.doClick();
            }
            // Hide & unhide objects -- H or Alt+H
            else if (keyCode == KeyEvent.VK_H) {
                if (e.isAltDown()) {
                    for (AbstractObj obj : globalObjList.values()) {
                        if (obj.isHidden) {
                            obj.isHidden = false;
                            rerenderTasks.add("zone:" + obj.stage.stageName);
                        }
                    }
                    
                    lblStatus.setText("Unhid all objects.");
                }
                else {
                    if (selectedObjs.isEmpty()) {
                        return;
                    }
                    
                    List<AbstractObj> hideObjs = new ArrayList(selectedObjs.values());
                    
                    for (AbstractObj obj : hideObjs) {
                        obj.isHidden = !obj.isHidden;
                        rerenderTasks.add("zone:" + obj.stage.stageName);
                    }
                    
                    lblStatus.setText("Hid/unhid selection.");
                }
                
                glCanvas.repaint();
                return;
            }
            // Undo event -- Ctrl+Z
            else if (keyCode == KeyEvent.VK_Z && e.isControlDown()) {
                undo();
                System.out.println("Undos left: " + undoIndex);
                glCanvas.repaint();
            }
            // Rotation X Axis -- X
            else if (keyCode == KeyEvent.VK_X) {
                keyAxis = "x";
            }
            // Rotation Y Axis -- Y
            else if (keyCode == KeyEvent.VK_Y) {
                keyAxis = "y";
            }
            // Rotation Z Axis -- Z
            else if (keyCode == KeyEvent.VK_Z) {
                keyAxis = "z";
            }
            // Move
            else if (keyCode == Settings.getKeyPosition() && !e.isAltDown() && !e.isControlDown() && !e.isShiftDown()) {
                startingMousePos = mousePos;
                keyTranslating = true;
            }
            // Rotate
            else if (keyCode == Settings.getKeyRotation() && !e.isAltDown() && !e.isControlDown() && !e.isShiftDown()) {
                startingMousePos = mousePos;
                keyRotating = true;
            }
            // Scale
            else if (keyCode == Settings.getKeyScale() && !e.isAltDown() && !e.isControlDown() && !e.isShiftDown()) {
                startingMousePos = mousePos;
                keyScaling = true;
            }
            // Pull Up Add menu
            else if (keyCode == KeyEvent.VK_A && e.isShiftDown()) {
                popupAddItems.setLightWeightPopupEnabled(false);
                popupAddItems.show(pnlGLPanel, mousePos.x, mousePos.y);
                popupAddItems.setVisible(true);
            }
            // Copy/Paste
            else if (e.isControlDown()) {
                // Copy -- Ctrl+C
                if (keyCode == KeyEvent.VK_C) {
                    copyObj = (LinkedHashMap<Integer, AbstractObj>)selectedObjs.clone();
                    lblStatus.setText("Copied current selection.");
                }
                // Paste -- Ctrl+V
                else if(keyCode == KeyEvent.VK_V) {
                    if(copyObj != null && !copyObj.isEmpty()) {
                        
                        for(AbstractObj currentObj : copyObj.values())
                            pasteObject(currentObj);

                        if(copyObj.size() == 1)
                            lblStatus.setText("Pasted " + new ArrayList<>(copyObj.values()).get(0).name + ".");
                        else
                            lblStatus.setText("Pasted objects.");

                        addingObject = "";
                        glCanvas.repaint();
                    }
                }
            }
            // Jump to Object -- SPC
            else if (keyCode == KeyEvent.VK_SPACE && selectedObjs.size() == 1) {
                AbstractObj obj = selectedObjs.values().iterator().next();
                
                camTarget.scale(1.0f / SCALE_DOWN, obj.position);
                camDistance = 0.25f;
                
                if (isGalaxyMode) {
                    String stageKey = String.format("%d/%s", curScenarioID, obj.stage.stageName);

                    if (zonePlacements.containsKey(stageKey)) {
                        scratchVec.scale(1.0f / SCALE_DOWN, zonePlacements.get(stageKey).position);
                        camTarget.add(scratchVec);
                        
                    }
                }
                
                updateCamera();
                glCanvas.repaint();
            }
            else {
                // Arrow Key Shortcuts
                Vec3f delta = new Vec3f();
                Vec3f finaldelta = new Vec3f();

                if(Settings.getUseWASD() ? e.getKeyCode() == KeyEvent.VK_A : e.getKeyCode() == KeyEvent.VK_LEFT) {
                    delta.x = 1;
                }
                if(Settings.getUseWASD() ? e.getKeyCode() == KeyEvent.VK_D : e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    delta.x = -1;
                }
                if(Settings.getUseWASD() ? e.getKeyCode() == KeyEvent.VK_E : e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                    delta.y = 1;
                }
                if(Settings.getUseWASD() ? e.getKeyCode() == KeyEvent.VK_Q : e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                    delta.y = -1;
                }
                if(Settings.getUseWASD() ? e.getKeyCode() == KeyEvent.VK_W : e.getKeyCode() == KeyEvent.VK_UP) {
                    delta.z = -1;
                }
                if(Settings.getUseWASD() ? e.getKeyCode() == KeyEvent.VK_S : e.getKeyCode() == KeyEvent.VK_DOWN) {
                    delta.z = 1;
                }


                if(!selectedObjs.isEmpty()) {
                    if(keyCode == Settings.getKeyPosition())
                        offsetSelectionBy(delta.multiplyScalar(100));
                    else if(keyCode == Settings.getKeyRotation())
                        rotateSelectionBy(delta.multiplyScalar(5));
                    else if(keyCode == Settings.getKeyScale())
                        scaleSelectionBy(delta);
                } else {
                    finaldelta.x =(float)(-(delta.x * Math.sin(camRotation.x)) - (delta.y * Math.cos(camRotation.x) * Math.sin(camRotation.y)) +
                            (delta.z * Math.cos(camRotation.x) * Math.cos(camRotation.y)));
                    finaldelta.y =(float)((delta.y * Math.cos(camRotation.y)) + (delta.z * Math.sin(camRotation.y)));
                    finaldelta.z =(float)((delta.x * Math.cos(camRotation.x)) - (delta.y * Math.sin(camRotation.x) * Math.sin(camRotation.y)) +
                            (delta.z * Math.sin(camRotation.x) * Math.cos(camRotation.y)));
                    camTarget.x += finaldelta.x * 0.005f;
                    camTarget.y += finaldelta.y * 0.005f;
                    camTarget.z += finaldelta.z * 0.005f;
                    
                    updateCamera();
                    glCanvas.repaint();
                }
            }
        }
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddScenario;
    private javax.swing.JButton btnAddZone;
    private javax.swing.JButton btnDeleteScenario;
    private javax.swing.JButton btnDeleteZone;
    private javax.swing.JButton btnEditScenario;
    private javax.swing.JButton btnEditZone;
    private javax.swing.JMenuItem itmPositionCopy;
    private javax.swing.JMenuItem itmPositionPaste;
    private javax.swing.JMenuItem itmRotationCopy;
    private javax.swing.JMenuItem itmRotationPaste;
    private javax.swing.JMenuItem itmScaleCopy;
    private javax.swing.JMenuItem itmScalePaste;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JToolBar.Separator jSeparator10;
    private javax.swing.JToolBar.Separator jSeparator4;
    private javax.swing.JToolBar.Separator jSeparator5;
    private javax.swing.JLabel lblScenarios;
    private javax.swing.JLabel lblStatus;
    private javax.swing.JLabel lblZones;
    private javax.swing.JList listScenarios;
    private javax.swing.JList listZones;
    private javax.swing.JMenuBar menu;
    private javax.swing.JMenuItem mniClose;
    private javax.swing.JMenuItem mniSave;
    private javax.swing.JMenu mnuCopy;
    private javax.swing.JMenu mnuEdit;
    private javax.swing.JMenu mnuFile;
    private javax.swing.JMenu mnuPaste;
    private javax.swing.JPanel pnlGLPanel;
    private javax.swing.JPanel pnlLayers;
    private javax.swing.JPanel pnlObjects;
    private javax.swing.JSplitPane pnlScenarioZone;
    private javax.swing.JPanel pnlScenarios;
    private javax.swing.JPanel pnlZones;
    private javax.swing.JScrollPane scrLayers;
    private javax.swing.JScrollPane scrObjSettings;
    private javax.swing.JScrollPane scrObjectTree;
    private javax.swing.JSplitPane scrObjects;
    private javax.swing.JScrollPane scrZones;
    private javax.swing.JToolBar.Separator sep1;
    private javax.swing.JToolBar.Separator sep2;
    private javax.swing.JToolBar.Separator sep3;
    private javax.swing.JToolBar.Separator sep4;
    private javax.swing.JToolBar.Separator sep5;
    private javax.swing.JToolBar.Separator sepZones;
    private javax.swing.JSplitPane split;
    private javax.swing.JTabbedPane tabData;
    private javax.swing.JToggleButton tgbAddObject;
    private javax.swing.JToggleButton tgbCopyObj;
    private javax.swing.JToggleButton tgbDeleteObject;
    private javax.swing.JButton tgbDeselect;
    private javax.swing.JToggleButton tgbPasteObj;
    private javax.swing.JToggleButton tgbShowAreas;
    private javax.swing.JToggleButton tgbShowAxis;
    private javax.swing.JToggleButton tgbShowCameras;
    private javax.swing.JToggleButton tgbShowGravity;
    private javax.swing.JToggleButton tgbShowPaths;
    private javax.swing.JToolBar tlbLayers;
    private javax.swing.JToolBar tlbObjects;
    private javax.swing.JToolBar tlbOptions;
    private javax.swing.JToolBar tlbScenarios;
    private javax.swing.JToolBar tlbZones;
    private javax.swing.JTree treeObjects;
    // End of variables declaration//GEN-END:variables

    
    // This object is used to save previous obj info for undo
    public class UndoEntry {
        public UndoEntry(AbstractObj obj) {
            position =(Vec3f) obj.position.clone();
            rotation =(Vec3f) obj.rotation.clone();
            scale =(Vec3f) obj.scale.clone();
            data =(Bcsv.Entry) obj.data.clone();
            id = obj.uniqueID;
            type = "changeObj";
            layer = obj.layerKey;
            name = obj.name;
            objType = obj.getFileType();
        }
        
        public UndoEntry(String editType, AbstractObj obj) {
            position =(Vec3f) obj.position.clone();
            
            if(obj.rotation == null)
                rotation = new Vec3f();
            else
                rotation =(Vec3f) obj.rotation.clone();
            
            if(obj.scale == null)
                rotation = new Vec3f();
            else
                scale =(Vec3f) obj.scale.clone();
            
            data =(Bcsv.Entry) obj.data.clone();
            id = obj.uniqueID;
            type = editType;
            layer = obj.layerKey;
            name = obj.name;
            
            if(obj instanceof PathPointObj) {
                objType = "pathpoint";
                parentPathId = ((PathPointObj) obj).path.pathID;
            } else {
                objType = obj.getFileType();
            }
        }
        
        public Vec3f position;
        public Vec3f rotation;
        public Vec3f scale;
        public Bcsv.Entry data;
        public String type, layer, name, objType;
        public int id;
        
        public int parentPathId;
    }
    
    /*
     * Misc Helpers
    */
    
    // Gets an unused switch id for the current zone.
    public int getValidSwitchInZone() {
        return getValidSwitchInZone(curZoneArc);
    }
    
    // Gets an unused switch id for a specified zone.  
    public int getValidSwitchInZone(StageArchive zoneArc) {
        // Make a list of all the used switch IDs.
        ArrayList<Integer> list = new ArrayList<>();
        list.addAll(getUniqueSwitchesInZone(zoneArc));
        
        // Generate an ID based on this list.
        int returnedSwitchID = generateUniqueSwitchID(list, false);
        
        // Show a funny prompt if they've used all the valid switches.
        if (returnedSwitchID==-1) 
            JOptionPane.showMessageDialog(parentForm, "Congratulations! You have used 128 switches! There's no way you unintentionally did this...");
        
        return returnedSwitchID;
    }
    
    // Gets an unused switch id for the current galaxy.
    public int getValidSwitchInGalaxy() {
        return getValidSwitchInGalaxy(zoneArchives);
    }
    
    // Gets an unused switch id for a hashmap of zone archives.
    public int getValidSwitchInGalaxy(HashMap<String, StageArchive> zoneArcs) {
        ArrayList<Integer> list = new ArrayList<>();
        
        // Add the used switches across all zones given to a list.
        for (StageArchive zone : zoneArcs.values()) {
            list.addAll(getUniqueSwitchesInZone(zone));
        }
        
        // Generate a switch ID based on this list.
        int returnSwitchID = generateUniqueSwitchID(list, true);
        
        // Show a funny prompt if they've used all the valid switches.
        if (returnSwitchID==-1) 
            JOptionPane.showMessageDialog(parentForm, "Congratulations! You have used 128 switches! There's no way you unintentionally did this...");
        
        return returnSwitchID;
    }
    
    // Generate a switch ID based on an array list of used switch IDs. 
    // Specify if you want it to generate a switch between 0-127 (False) or 1000-1127 (True).
    // This will return -1 if no valid switch ID was found.
    public int generateUniqueSwitchID(ArrayList<Integer> list, boolean isGalaxyMode) {
        // Set the starting switch ID based on isGalaxyMode.
        int switchID = 0;
        if (isGalaxyMode) {
            switchID = 1000;
        }
        int startingSwitchID = switchID;
        
        // Go through the list and find the first unique Switch ID.
        while (list.contains(switchID) && switchID < startingSwitchID+128) {
            switchID++;
        }
        
        // If the first switch ID that matches is not a valid switch ID, return -1.
        if (switchID > startingSwitchID+127) {
            switchID = -1;
        }
        
        return switchID;
    }
    
    // Returns a set hash set of all switch IDs used in the current zone.
    public Set<Integer> getUniqueSwitchesInZone() {
        return getUniqueSwitchesInZone(curZoneArc);
    }
    
    // Give a zone's StageArchive and it will return a hash set of all switch IDs used in that zone.
    public Set<Integer> getUniqueSwitchesInZone(StageArchive zoneArc) {
        // Using a hash set because it does not allow for duplicates
        Set<Integer> set = new HashSet<>(); 
        
        String[] switchFieldList = {"SW_APPEAR", "SW_DEAD", "SW_A", "SW_B", "SW_AWAKE", "SW_PARAM", "SW_SLEEP"};
        
        // Grab the SwitchID for all objects
        for (List<AbstractObj> layers : zoneArc.objects.values()) {
            for (AbstractObj obj : layers) {
                for (String field : switchFieldList) {
                    int switchId = obj.data.getInt(field, -1);
                    
                    // Add each switch ID seen to a hash set
                    if (switchId!=-1) {
                        set.add(switchId);
                    }
                }
            }
        }
        
        return set;
    }
     
    private HashMap<String, StageArchive> replaceSwitchID(int switchIdToReplace, int switchIdToReplaceWith, HashMap<String, StageArchive> zoneArcs) {
        return replaceSwitchID(switchIdToReplace, switchIdToReplaceWith, zoneArcs, "");
    }
    
    private HashMap<String, StageArchive> replaceSwitchID(int switchIdToReplace, int switchIdToReplaceWith, HashMap<String, StageArchive> zoneArcs, String zoneName) {
        String[] switchFieldList = {"SW_APPEAR", "SW_DEAD", "SW_A", "SW_B", "SW_AWAKE", "SW_PARAM", "SW_SLEEP"};
        
        for (StageArchive zoneArc : zoneArcs.values()) {
            if (zoneName.equals(zoneArc.stageName) || zoneName.isBlank()) { // for zone specific replacements
                for (List<AbstractObj> layers : zoneArc.objects.values()) {
                    for (AbstractObj obj : layers) {
                        for (String field : switchFieldList) {
                            int switchId = obj.data.getInt(field, -1);

                            if (switchId == switchIdToReplace)
                                obj.data.put(field, switchIdToReplaceWith);
                        }
                    }
                }
            }
        }
        
        return zoneArcs;
    }
}
