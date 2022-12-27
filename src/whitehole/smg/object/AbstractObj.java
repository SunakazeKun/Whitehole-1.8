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
package whitehole.smg.object;

import com.jogamp.opengl.*;
import java.util.ArrayList;
import java.util.List;
import whitehole.Whitehole;
import whitehole.db.ObjectDB;
import whitehole.rendering.GLRenderer;
import whitehole.rendering.RendererCache;
import whitehole.smg.Bcsv;
import whitehole.smg.StageArchive;
import whitehole.smg.StageHelper;
import whitehole.util.PropertyGrid;
import whitehole.math.Vec3f;

public abstract class AbstractObj {
    public String name, layerKey, oldName;
    public Vec3f position, rotation, scale;
    public StageArchive stage;
    public Bcsv.Entry data;
    public ObjectDB.ObjectInfo objdbInfo;
    public GLRenderer renderer = null;
    public int uniqueID = -1;
    public boolean isHidden = false;
    
    public AbstractObj(StageArchive stge, String layerkey, Bcsv.Entry entry, String objname) {
        name = objname;
        layerKey = layerkey;
        oldName = objname;
        stage = stge;
        data = entry;
        
        loadDBInfo();
    }
    
    public abstract String getFileType();
    public abstract int save();
    public abstract void getProperties(PropertyGrid panel);
    
    @Override
    public String toString() {
        if (objdbInfo.isValid()) {
            return String.format("%s <%s>", objdbInfo.toString(), getLayerName());
        }
        else {
            return String.format("\"%s\" <%s>", name, getLayerName());
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Helper functions
    
    protected final Vec3f getVector(String prefix) {
        float x = (float)data.getOrDefault(prefix + "_x", 0.0f);
        float y = (float)data.getOrDefault(prefix + "_y", 0.0f);
        float z = (float)data.getOrDefault(prefix + "_z", 0.0f);
        return new Vec3f(x, y, z);
    }
    
    protected final void putVector(String prefix, Vec3f vector) {
        data.put(prefix + "_x", vector.x);
        data.put(prefix + "_y", vector.y);
        data.put(prefix + "_z", vector.z);
    }
    
    public final String getLayerName() {
        return StageHelper.layerKeyToLayer(layerKey);
    }
    
    public final void loadDBInfo() {
        objdbInfo = ObjectDB.getObjectInfo(name);
    }
    
    private static final List<String> CHOICES_GRAVITY_POWERS =  new ArrayList() {{ add("Normal"); add("Light"); add("Heavy"); }};
    private static final List<String> CHOICES_GRAVITY_TYPES = new ArrayList() {{ add("Normal"); add("Shadow"); add("Magnet"); }};
    
    protected final void addField(PropertyGrid panel, String field) {
        switch(field) {
            case "pos_x":
                panel.addField("pos_x", "X position", "float", null, position.x, "Default");
                break;
            case "pos_y":
                panel.addField("pos_y", "Y position", "float", null, position.y, "Default");
                break;
            case "pos_z":
                panel.addField("pos_z", "Z position", "float", null, position.z, "Default");
                break;
            case "dir_x":
                panel.addField("dir_x", "X rotation", "float", null, rotation.x, "Default");
                break;
            case "dir_y":
                panel.addField("dir_y", "Y rotation", "float", null, rotation.y, "Default");
                break;
            case "dir_z":
                panel.addField("dir_z", "Z rotation", "float", null, rotation.z, "Default");
                break;
            case "scale_x":
                panel.addField("scale_x", "X size", "float", null, scale.x, "Default");
                break;
            case "scale_y":
                panel.addField("scale_y", "Y size", "float", null, scale.y, "Default");
                break;
            case "scale_z":
                panel.addField("scale_z", "Z size", "float", null, scale.z, "Default");
                break;
                
            // Obj_args
            case "Obj_arg0":
            case "Obj_arg1":
            case "Obj_arg2":
            case "Obj_arg3":
            case "Obj_arg4":
            case "Obj_arg5":
            case "Obj_arg6":
            case "Obj_arg7":
                String desc = objdbInfo.simpleParameterName(Whitehole.getCurrentGameType(), field);
                panel.addField(field, desc, "int", null, data.getInt(field, -1), "Default");
                break;
            
            // Switches
            case "SW_APPEAR":
            case "SW_DEAD":
            case "SW_A":
            case "SW_B":
            case "SW_SLEEP":
            case "SW_AWAKE":
            case "SW_PARAM":
                panel.addField(field, field, "switchid", null, data.getInt(field, -1), "Default");
                break;
                
            // Linked Objects
            case "l_id":
                panel.addField(field, "Link ID", "int", null, data.getInt(field, -1), "Default");
                break;
            case "Obj_ID":
                panel.addField(field, "Linked Object ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "MapParts_ID":
                panel.addField(field, "Linked MapParts ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "ChildObjId":
                panel.addField(field, "Linked Child ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "FollowId":
                panel.addField(field, "Linked Area ID", "int", null, data.getInt(field, -1), "Default");
                break;
            
            // Object Groups
            case "GroupId":
                panel.addField(field, "Group ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "ClippingGroupId":
                panel.addField(field, "Clipping Group ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "ViewGroupId":
                panel.addField(field, "View Group ID", "int", null, data.getInt(field, -1), "Default");
                break;
            case "DemoGroupId":
                panel.addField(field, "Cutscene Group ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "CastId":
                panel.addField(field, "Cast Group ID", "int", null, data.getInt(field, -1), "Default");
                break;
            
            // Miscellaneous
            case "CommonPath_ID":
                panel.addField(field, "Path ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "ShapeModelNo":
                panel.addField(field, "Model ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "CameraSetId":
                panel.addField(field, "Camera Set ID", "int", null, data.getInt(field, -1), "Default");
                break;
            case "MessageId":
                panel.addField(field, "Message ID", "int", null, data.getInt(field, -1), "Default");
                break;
            case "ParamScale":
                panel.addField(field, "Speed Scale", "float", null, data.getFloat(field, 1.0f), "Default");
                break;
            case "GeneratorID":
            case "ParentID":
                panel.addField(field, "Generator Object ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;        
                
            // AreaObjInfo
            case "AreaShapeNo":
                panel.addField(field, "Shape ID", "int", null, data.getShort(field, (short)0), "Default");
                break;
            case "Priority":
                panel.addField(field, "Priority", "int", null, data.getInt(field, -1), "Default");
                break;
                
            // CameraCubeInfo
            case "Validity":
                panel.addField("Validity", "Validity", "text", null, data.getString("Validity", "Valid"), "Default");
                break;
            
            // DemoObjInfo
            case "DemoName":
                panel.addField(field, "Cutscene Name", "text", null, data.getString(field, "undefined"), "Default");
                break;
            case "TimeSheetName":
                panel.addField(field, "Sheet Name", "text", null, data.getString(field, "undefined"), "Default");
                break;
            case "DemoSkip":
                panel.addField(field, "Skippable?", "int", null, data.getInt(field, -1), "Default");
                break;
                
            // GeneralPosInfo
            case "PosName":
                panel.addField(field, "Identifier", "text", null, data.getString(field, "undefined"), "Default");
                break;
                
            // MapPartsInfo
            case "ParentId":
                panel.addField(field, "Parent Object ID", "int", null, data.getShort(field, (short)-1), "Default");
                break;
            case "MoveConditionType":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "RotateSpeed":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "RotateAngle":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "RotateAxis":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "RotateAccelType":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "RotateStopTime":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "RotateType":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "ShadowType":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "SignMotionType":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "PressType":
                panel.addField(field, field, "int", null, data.getInt(field), "Default");
                break;
            case "FarClip":
                panel.addField(field, field, "int", null, data.getInt(field, -1), "Default");
                break;
                
            // PlanetObjInfo
            case "Range":
                panel.addField(field, field, "float", null, data.getFloat(field, 0.0f), "Default");
                break;
            case "Distant":
                panel.addField(field, "Distance", "float", null, data.getFloat(field, 0.0f), "Default");
                break;
            case "Inverse":
                panel.addField(field, "Inverse?", "int", null, data.getInt(field, -1), "Default");
                break;
            case "Power":
                panel.addField(field, "Power", "list", CHOICES_GRAVITY_POWERS, data.getString(field, "Normal"), "Default");
                break;
            case "Gravity_type":
                panel.addField(field, "Type", "list", CHOICES_GRAVITY_TYPES, data.getString(field, "Normal"), "Default");
                break;
                
            // StartInfo
            case "MarioNo":
                panel.addField(field, "Spawn ID", "int", null, data.getInt(field, 0), "Default");
                break;
            case "Camera_id":
                panel.addField(field, "Camera ID", "int", null, data.getInt(field, -1), "Default");
                break;
            
            default:
                throw new IllegalArgumentException("No preset defined for field " + field);
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    // Rendering
    
    public void initRenderer(GLRenderer.RenderInfo info) {
        if (renderer != null) {
            return;
        }
        
        renderer = RendererCache.getObjectRenderer(info, this);
        renderer.compileDisplayLists(info);
        renderer.releaseStorage();
    }
    
    public void closeRenderer(GLRenderer.RenderInfo info) {
        if (renderer == null) {
            return;
        }
        
        RendererCache.closeObjectRenderer(info, this);
        renderer = null;
    }
    
    public void render(GLRenderer.RenderInfo info) {
        if (isHidden || renderer == null) {
            return;
        }
        
        GL2 gl = info.drawable.getGL().getGL2();
        gl.glPushMatrix();
        
        gl.glTranslatef(position.x, position.y, position.z);
        gl.glRotatef(rotation.z, 0f, 0f, 1f);
        gl.glRotatef(rotation.y, 0f, 1f, 0f);
        gl.glRotatef(rotation.x, 1f, 0f, 0f);
        
        if (renderer.isScaled()) {
            gl.glScalef(scale.x, scale.y, scale.z);
        }
        
        try {
            gl.glCallList(renderer.getDisplayList(info.renderMode));
        }
        catch(NullPointerException ex) {
            // This line gives an error when exiting out of fullscreen, catching it to prevent that
        }
        
        gl.glPopMatrix();
    }
}
