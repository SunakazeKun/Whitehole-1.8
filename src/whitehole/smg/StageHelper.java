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
package whitehole.smg;

import java.io.IOException;
import whitehole.io.RarcFile;

public class StageHelper {
    public static String layerKeyToLayer(String layerKey) {
        if (layerKey.equals("common")) {
            return "Common";
        }
        else {
            return "Layer" + layerKey.substring(5).toUpperCase();
        }
    }
    
    public static Bcsv getOrCreateJMapPlacementFile(RarcFile archive, String folder, String layer, String file, int game) throws IOException {
        String basePath = "/Stage/jmp";
        
        if (game == 1) {
            folder = folder.toLowerCase();
            layer = layer.toLowerCase();
            file = file.toLowerCase();
            basePath = basePath.toLowerCase();
        }
        
        String folderPath = String.format("%s/%s", basePath, folder);
        String layerPath = String.format("%s/%s", folderPath, layer);
        String filePath = String.format("%s/%s", layerPath, file);
        
        if (!archive.directoryExists(folderPath)) {
            archive.createDirectory(basePath, folder);
        }
        if (!archive.directoryExists(layerPath)) {
            archive.createDirectory(folderPath, layer);
        }
        if (!archive.fileExists(filePath)) {
            archive.createFile(layerPath, file);
        }
        
        Bcsv bcsv = new Bcsv(archive.openFile(filePath));
        populateJMapFields(bcsv, file.toLowerCase(), game);
        return bcsv;
    }
    
    public static void populateJMapFields(Bcsv bcsv, String type, int game) {
        switch(type) {
            case "stageobjinfo": populateJMapFieldsStageObjInfo(bcsv); break;
            case "objinfo": populateJMapFieldsObjInfo(bcsv, game); break;
            case "mappartsinfo": populateJMapFieldsMapPartsInfo(bcsv, game); break;
            case "areaobjinfo": populateJMapFieldsAreaObjInfo(bcsv, game); break;
            case "cameracubeinfo": populateJMapFieldsCameraCubeInfo(bcsv, game); break;
            case "planetobjinfo": populateJMapFieldsPlanetObjInfo(bcsv, game); break;
            case "demoobjinfo": populateJMapFieldsDemoObjInfo(bcsv, game); break;
            case "startinfo": populateJMapFieldsStartInfo(bcsv); break;
            case "generalposinfo": populateJMapFieldsGeneralPosInfo(bcsv, game); break;
            case "debugmoveinfo": populateJMapFieldsDebugMoveInfo(bcsv); break;
            case "childobjinfo": populateJMapFieldsChildObjInfo(bcsv); break;
            case "soundinfo": populateJMapFieldsSoundInfo(bcsv); break;
        }
    }
    
    private static void populateJMapFieldsStageObjInfo(Bcsv bcsv) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
    }
    
    private static void populateJMapFieldsObjInfo(Bcsv bcsv, int game) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("Obj_arg0", 0, -1, 0, -1);
        bcsv.addField("Obj_arg1", 0, -1, 0, -1);
        bcsv.addField("Obj_arg2", 0, -1, 0, -1);
        bcsv.addField("Obj_arg3", 0, -1, 0, -1);
        bcsv.addField("Obj_arg4", 0, -1, 0, -1);
        bcsv.addField("Obj_arg5", 0, -1, 0, -1);
        bcsv.addField("Obj_arg6", 0, -1, 0, -1);
        bcsv.addField("Obj_arg7", 0, -1, 0, -1);
        bcsv.addField("CameraSetId", 0, -1, 0, -1);
        bcsv.addField("SW_APPEAR", 0, -1, 0, -1);
        bcsv.addField("SW_DEAD", 0, -1, 0, -1);
        bcsv.addField("SW_A", 0, -1, 0, -1);
        bcsv.addField("SW_B", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("SW_AWAKE", 0, -1, 0, -1);
            bcsv.addField("SW_PARAM", 0, -1, 0, -1);
        }
        else {
            bcsv.addField("SW_SLEEP", 0, -1, 0, -1);
        }
        
        bcsv.addField("MessageId", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("ParamScale", 2, -1, 0, 0.0f);
        }
        
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
        bcsv.addField("CastId", 0, -1, 0, -1);
        bcsv.addField("ViewGroupId", 0, -1, 0, -1);
        bcsv.addField("ShapeModelNo", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("CommonPath_ID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("ClippingGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("GroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("DemoGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("MapParts_ID", 4, 0xFFFF, 0, (short)-1);
        
        if (game == 2) {
            bcsv.addField("Obj_ID", 4, 0xFFFF, 0, (short)-1);
            bcsv.addField("GeneratorID", 4, 0xFFFF, 0, (short)-1);
        }
    }
    
    private static void populateJMapFieldsMapPartsInfo(Bcsv bcsv, int game) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("MoveConditionType", 0, -1, 0, -1);
        bcsv.addField("RotateSpeed", 0, -1, 0, -1);
        bcsv.addField("RotateAngle", 0, -1, 0, -1);
        bcsv.addField("RotateAxis", 0, -1, 0, -1);
        bcsv.addField("RotateAccelType", 0, -1, 0, -1);
        bcsv.addField("RotateStopTime", 0, -1, 0, -1);
        bcsv.addField("RotateType", 0, -1, 0, -1);
        bcsv.addField("ShadowType", 0, -1, 0, -1);
        bcsv.addField("SignMotionType", 0, -1, 0, -1);
        bcsv.addField("PressType", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("ParamScale", 2, -1, 0, 0.0f);
        }
        
        bcsv.addField("CameraSetId", 0, -1, 0, -1);
        bcsv.addField("FarClip", 0, -1, 0, -1);
        bcsv.addField("Obj_arg0", 0, -1, 0, -1);
        bcsv.addField("Obj_arg1", 0, -1, 0, -1);
        bcsv.addField("Obj_arg2", 0, -1, 0, -1);
        bcsv.addField("Obj_arg3", 0, -1, 0, -1);
        bcsv.addField("SW_APPEAR", 0, -1, 0, -1);
        bcsv.addField("SW_DEAD", 0, -1, 0, -1);
        bcsv.addField("SW_A", 0, -1, 0, -1);
        bcsv.addField("SW_B", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("SW_AWAKE", 0, -1, 0, -1);
            bcsv.addField("SW_PARAM", 0, -1, 0, -1);
        }
        else {
            bcsv.addField("SW_SLEEP", 0, -1, 0, -1);
        }
        
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
        bcsv.addField("CastId", 0, -1, 0, -1);
        bcsv.addField("ViewGroupId", 0, -1, 0, -1);
        bcsv.addField("ShapeModelNo", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("CommonPath_ID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("ClippingGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("GroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("DemoGroupId", 4, 0xFFFF, 0, (short)-1);
        
        if (game == 2) {
            bcsv.addField("MapParts_ID", 4, 0xFFFF, 0, (short)-1);
            bcsv.addField("Obj_ID", 4, 0xFFFF, 0, (short)-1);
            bcsv.addField("ParentId", 4, 0xFFFF, 0, (short)-1);
        }
    }
    
    private static void populateJMapFieldsAreaObjInfo(Bcsv bcsv, int game) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("Obj_arg0", 0, -1, 0, -1);
        bcsv.addField("Obj_arg1", 0, -1, 0, -1);
        bcsv.addField("Obj_arg2", 0, -1, 0, -1);
        bcsv.addField("Obj_arg3", 0, -1, 0, -1);
        bcsv.addField("Obj_arg4", 0, -1, 0, -1);
        bcsv.addField("Obj_arg5", 0, -1, 0, -1);
        bcsv.addField("Obj_arg6", 0, -1, 0, -1);
        bcsv.addField("Obj_arg7", 0, -1, 0, -1);
        bcsv.addField("Priority", 0, -1, 0, -1);
        bcsv.addField("SW_APPEAR", 0, -1, 0, -1);
        bcsv.addField("SW_A", 0, -1, 0, -1);
        bcsv.addField("SW_B", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("SW_AWAKE", 0, -1, 0, -1);
        }
        else {
            bcsv.addField("SW_SLEEP", 0, -1, 0, -1);
        }
        
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
        bcsv.addField("FollowId", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("AreaShapeNo", 4, 0xFFFF, 0, (short)-1);
        }
        
        bcsv.addField("CommonPath_ID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("ClippingGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("GroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("DemoGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("MapParts_ID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("Obj_ID", 4, 0xFFFF, 0, (short)-1);
        
        if (game == 1) {
            bcsv.addField("ChildObjId", 4, 0xFFFF, 0, (short)-1);
        }
    }
    
    private static void populateJMapFieldsCameraCubeInfo(Bcsv bcsv, int game) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("Obj_arg0", 0, -1, 0, -1);
        bcsv.addField("Obj_arg1", 0, -1, 0, -1);
        bcsv.addField("Obj_arg2", 0, -1, 0, -1);
        bcsv.addField("Obj_arg3", 0, -1, 0, -1);
        bcsv.addField("InterpolateIn", 0, -1, 0, -1);
        bcsv.addField("InterpolateOut", 0, -1, 0, -1);
        bcsv.addField("SW_APPEAR", 0, -1, 0, -1);
        bcsv.addField("SW_A", 0, -1, 0, -1);
        bcsv.addField("SW_B", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("SW_AWAKE", 0, -1, 0, -1);
        }
        else {
            bcsv.addField("SW_SLEEP", 0, -1, 0, -1);
        }
        
        bcsv.addField("Validity", 6, -1, 0, "Valid");
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
        bcsv.addField("FollowId", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("AreaShapeNo", 4, 0xFFFF, 0, (short)-1);
        }
        
        bcsv.addField("MapParts_ID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("Obj_ID", 4, 0xFFFF, 0, (short)-1);
        
        if (game == 1) {
            bcsv.addField("ChildObjId", 4, 0xFFFF, 0, (short)-1);
        }
    }
    
    private static void populateJMapFieldsPlanetObjInfo(Bcsv bcsv, int game) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("Obj_arg0", 0, -1, 0, -1);
        bcsv.addField("Obj_arg1", 0, -1, 0, -1);
        bcsv.addField("Obj_arg2", 0, -1, 0, -1);
        bcsv.addField("Obj_arg3", 0, -1, 0, -1);
        bcsv.addField("Range", 2, -1, 0, 0.0f);
        bcsv.addField("Distant", 2, -1, 0, 0.0f);
        bcsv.addField("Priority", 0, -1, 0, -1);
        bcsv.addField("Inverse", 0, -1, 0, -1);
        bcsv.addField("Power", 6, -1, 0, "");
        bcsv.addField("Gravity_type", 6, -1, 0, "");
        bcsv.addField("SW_APPEAR", 0, -1, 0, -1);
        bcsv.addField("SW_DEAD", 0, -1, 0, -1);
        bcsv.addField("SW_A", 0, -1, 0, -1);
        bcsv.addField("SW_B", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("SW_AWAKE", 0, -1, 0, -1);
        }
        else {
            bcsv.addField("SW_SLEEP", 0, -1, 0, -1);
        }
        
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
        bcsv.addField("FollowId", 0, -1, 0, -1);
        bcsv.addField("CommonPath_ID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("ClippingGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("GroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("DemoGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("MapParts_ID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("Obj_ID", 4, 0xFFFF, 0, (short)-1);
        
        if (game == 1) {
            bcsv.addField("ChildObjId", 4, 0xFFFF, 0, (short)-1);
        }
    }
    
    private static void populateJMapFieldsDemoObjInfo(Bcsv bcsv, int game) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("DemoName", 6, -1, 0, "");
        bcsv.addField("TimeSheetName", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("SW_APPEAR", 0, -1, 0, -1);
        bcsv.addField("SW_DEAD", 0, -1, 0, -1);
        bcsv.addField("SW_A", 0, -1, 0, -1);
        bcsv.addField("SW_B", 0, -1, 0, -1);
        
        if (game == 2) {
            bcsv.addField("DemoSkip", 0, -1, 0, -1);
        }
        
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
    }
    
    private static void populateJMapFieldsStartInfo(Bcsv bcsv) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("MarioNo", 0, -1, 0, -1);
        bcsv.addField("Obj_arg0", 0, -1, 0, -1);
        bcsv.addField("Camera_id", 0, -1, 0, -1);
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
    }
    
    private static void populateJMapFieldsGeneralPosInfo(Bcsv bcsv, int game) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("PosName", 6, -1, 0, "");
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("Obj_ID", 4, 0xFFFF, 0, (short)-1);
        
        if (game == 1) {
            bcsv.addField("ChildObjId", 4, 0xFFFF, 0, (short)-1);
        }
    }
    
    private static void populateJMapFieldsDebugMoveInfo(Bcsv bcsv) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
    }
    
    private static void populateJMapFieldsChildObjInfo(Bcsv bcsv) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("Obj_arg0", 0, -1, 0, -1);
        bcsv.addField("Obj_arg1", 0, -1, 0, -1);
        bcsv.addField("Obj_arg2", 0, -1, 0, -1);
        bcsv.addField("Obj_arg3", 0, -1, 0, -1);
        bcsv.addField("Obj_arg4", 0, -1, 0, -1);
        bcsv.addField("Obj_arg5", 0, -1, 0, -1);
        bcsv.addField("Obj_arg6", 0, -1, 0, -1);
        bcsv.addField("Obj_arg7", 0, -1, 0, -1);
        bcsv.addField("CameraSetId", 0, -1, 0, -1);
        bcsv.addField("SW_APPEAR", 0, -1, 0, -1);
        bcsv.addField("SW_DEAD", 0, -1, 0, -1);
        bcsv.addField("SW_A", 0, -1, 0, -1);
        bcsv.addField("SW_B", 0, -1, 0, -1);
        bcsv.addField("SW_SLEEP", 0, -1, 0, -1);
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
        bcsv.addField("CastId", 0, -1, 0, -1);
        bcsv.addField("ViewGroupId", 0, -1, 0, -1);
        bcsv.addField("MessageId", 0, -1, 0, -1);
        
        bcsv.addField("ParentID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("ShapeModelNo", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("CommonPath_ID", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("ClippingGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("GroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("DemoGroupId", 4, 0xFFFF, 0, (short)-1);
        bcsv.addField("MapParts_ID", 4, 0xFFFF, 0, (short)-1);
    }
    
    private static void populateJMapFieldsSoundInfo(Bcsv bcsv) {
        bcsv.fields.clear();
        
        bcsv.addField("name", 6, -1, 0, "");
        bcsv.addField("l_id", 0, -1, 0, -1);
        bcsv.addField("Obj_arg0", 0, -1, 0, -1);
        bcsv.addField("Obj_arg1", 0, -1, 0, -1);
        bcsv.addField("Obj_arg2", 0, -1, 0, -1);
        bcsv.addField("Obj_arg3", 0, -1, 0, -1);
        bcsv.addField("SW_A", 0, -1, 0, -1);
        bcsv.addField("SW_B", 0, -1, 0, -1);
        bcsv.addField("SW_APPEAR", 0, -1, 0, -1);
        bcsv.addField("pos_x", 2, -1, 0, 0.0f);
        bcsv.addField("pos_y", 2, -1, 0, 0.0f);
        bcsv.addField("pos_z", 2, -1, 0, 0.0f);
        bcsv.addField("dir_x", 2, -1, 0, 0.0f);
        bcsv.addField("dir_y", 2, -1, 0, 0.0f);
        bcsv.addField("dir_z", 2, -1, 0, 0.0f);
        bcsv.addField("scale_x", 2, -1, 0, 1.0f);
        bcsv.addField("scale_y", 2, -1, 0, 1.0f);
        bcsv.addField("scale_z", 2, -1, 0, 1.0f);
        bcsv.addField("CommonPath_ID", 4, 0xFFFF, 0, (short)-1);
    }
}
