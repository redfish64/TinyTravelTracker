package com.igisw.openlocationtracker;

import com.mapzen.tangram.LngLat;
import com.mapzen.tangram.MapController;
import com.mapzen.tangram.MapData;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles drawing selected area sets to tangram, which are rectangluar areas the user selects
 * using long press.
 */
public class SasDrawer
{
    private final SelectedAreaSet sas;
    private MapController mapController;
    private MapData mapData;

    private List<LngLat> rectList = new ArrayList<>(4);

    public SasDrawer(SelectedAreaSet sas, MapController mapController)
    {
        for(int i = 0; i < 6; i++)
            rectList.add(new LngLat());

        this.sas = sas;
        this.mapController = mapController;
        this.mapData = mapController.addDataLayer("gt_selected_area_rect");
    }

    public void resetToSas() {
        List<Area> areas = sas.getRequestedAreas();

        if(areas.isEmpty()) {
            turnOffRectangle();
        }
        else {
            //TODO 4 currently there is only one requested area possible due to UI restrictions
            //  so we only need to display the first one
            Area a = areas.get(0);
            setRectangle(a.x1,a.y1,a.x2,a.y2);
        }
    }

    public void turnOffRectangle()
    {
        mapData.clear();
        mapController.requestRender();
    }

    public void setRectangle(int x1, int y1, int x2, int y2)
    {
        //mapData.beginChangeBlock();;
        mapData.clear();
        double lon1 = AreaPanel.convertXToLon(x1);
        double lat1 = AreaPanel.convertYToLat(y1);
        double lon2 = AreaPanel.convertXToLon(x2);
        double lat2 = AreaPanel.convertYToLat(y2);

        mapData.addPolyline(createPolylineForRect(lon1,lat1,lon2,lat2),null);
//        createPolylineForRect(lon1,lat1,lon2,lat2);
//        for(LngLat l : rectList)
//            mapData.addPoint(l,null);

//        mapData.endChangeBlock();

        mapController.requestRender();
    }

    private List<LngLat> createPolylineForRect(double lon1, double lat1, double lon2, double lat2) {
        rectList.get(0).set(lon1,lat1);
        rectList.get(1).set(lon1,lat2);
        rectList.get(2).set(lon2,lat2);
        rectList.get(3).set(lon2,lat1);
        rectList.get(4).set(lon1,lat1);
        rectList.get(5).set(lon1,lat2); //this extra one is to make the rectangle not have a gap
        //in the upper left corner

        return rectList;
    }

}
