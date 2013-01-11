/*******************************************************************************
 * Copyright 2012-2013 Trento RISE
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either   express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package eu.trentorise.smartcampus.dt.custom.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.MotionEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

import eu.trentorise.smartcampus.dt.R;
import eu.trentorise.smartcampus.dt.custom.CategoryHelper;
import eu.trentorise.smartcampus.dt.model.BaseDTObject;

public class DTItemizedOverlay extends ItemizedOverlay<OverlayItem> {

    private static int densityX = 5;
    private static int densityY = 5;

	private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
	private ArrayList<BaseDTObject> mObjects = new ArrayList<BaseDTObject>();
	private Set<OverlayItem> mGeneric = new HashSet<OverlayItem>();

	private SparseArray<int[]> item2group = new SparseArray<int[]>();
	
	private BaseDTObjectMapItemTapListener listener = null;

	private Drawable groupMarker = null;
	
	private Context mContext = null;
	
	private MapView mMapView = null;

	List<List<List<OverlayItem>>> grid = new ArrayList<List<List<OverlayItem>>>(densityX);
	private Map<String,OverlayItem> layerMap = new HashMap<String,OverlayItem>();

	private boolean isPinch = false;

	
	
	public DTItemizedOverlay(Context mContext, MapView mapView) {
		super(boundCenterBottom(mContext.getResources().getDrawable(R.drawable.poi)));
		this.mContext = mContext;
		this.mMapView = mapView;
		populate();
	}


	public void populateAll() {
		populate();
	}
	
	public void setMapItemTapListener(BaseDTObjectMapItemTapListener listener) {
		this.listener = listener;
	}



	public void addOverlay(BaseDTObject o) {
		if (o.getLocation() != null) {
			GeoPoint point = new GeoPoint((int)(o.getLocation()[0]*1E6),(int)(o.getLocation()[1]*1E6));
			OverlayItem overlayitem = new OverlayItem(point, o.getTitle(), o.getDescription());
			Drawable drawable = mContext.getResources().getDrawable(CategoryHelper.getMapIconByType(o.getType()));
			drawable.setBounds(-drawable.getIntrinsicWidth()/2, -drawable.getIntrinsicHeight(), drawable.getIntrinsicWidth() /2, 0);
			overlayitem.setMarker(drawable);
			mOverlays.add(overlayitem);
			mObjects.add(o);
//			populate();
		}
	}

	@Override
	protected OverlayItem createItem(int i) {
		return mOverlays.get(i);
	}

	@Override
	public int size() {
		return mOverlays.size();
	}

	public void addGenericOverlay(OverlayItem overlay, String key) {
		//actually is used only for my position. So clear and add the new one
		//mGeneric.clear();
		//mObjects.add(null);
		//mGeneric.add(overlay);
		//populate();
		//mMapView.invalidate();
		
		if (layerMap.containsKey(key)){
			//elimina layer esistente da mOverlay e poi dalla hashmap
			OverlayItem precLayer = layerMap.get(key);
			mOverlays.remove(precLayer);
			mGeneric.remove(precLayer);
			layerMap.remove(key);
	
		}
		layerMap.put(key, overlay);
		mOverlays.add(overlay);
		mObjects.add(null);
		mGeneric.add(overlay);
		mMapView.invalidate();
		populate();

	}

	public void clearMarkers() {
		mOverlays.clear();
		mObjects.clear();
		populate();
		
	}

	
	@Override
	public boolean onTouchEvent(MotionEvent e, MapView mapView)
	{
	    int fingers = e.getPointerCount();
	    if( e.getAction()==MotionEvent.ACTION_DOWN ){
	        isPinch=false;  // Touch DOWN, don't know if it's a pinch yet
	    }
	    if( e.getAction()==MotionEvent.ACTION_MOVE && fingers==2 ){
	        isPinch=true;   // Two fingers, def a pinch
	    }
	    return super.onTouchEvent(e,mapView);
	}

	
	
	protected boolean onTap(int index) {
		 if ( !isPinch ){
		if (listener != null) {
			if (mObjects.size() >= index && mObjects.get(index) != null) {
				if (item2group.get(index) != null) {
					int[] coords = item2group.get(index);
					try {
						List<OverlayItem> list = grid.get(coords[0]).get(coords[1]);
						if (list.size() > 1) {
							MapManager.fitMapWithOverlays(list, mMapView);
							return super.onTap(index);
						}
					} catch (Exception e) {
						return super.onTap(index);
					} 
				}
				listener.onBaseDTObjectTap(mObjects.get(index));
				return true;
			} 
		}
		return super.onTap(index);
		 }
		 else return false;
	}


	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		 // binning:
        
        item2group.clear();
        
        // 2D array with some configurable, fixed density
        grid.clear(); 

        for(int i = 0; i<densityX; i++){
            ArrayList<List<OverlayItem>> column = new ArrayList<List<OverlayItem>>(densityY);
            for(int j = 0; j < densityY; j++){
                column.add(new ArrayList<OverlayItem>());
            }
            grid.add(column);
        }

        int idx = 0;
        for (OverlayItem m : mOverlays) {
        	if (!mGeneric.contains(m)) {
                int binX;
                int binY;

                Projection proj = mapView.getProjection();
                Point p = proj.toPixels(m.getPoint(), null);

                if (isWithin(p, mapView)) {
                    double fractionX = ((double)p.x / (double)mapView.getWidth());
                    binX = (int) (Math.floor(densityX * fractionX));
                    double fractionY = ((double)p.y / (double)mapView.getHeight());
                    binY = (int) (Math
                            .floor(densityX * fractionY));
                    item2group.put(idx, new int[]{binX,binY});
                    grid.get(binX).get(binY).add(m); // just push the reference
                }
        	}
            idx++;
        }

        // drawing:

        for (int i = 0; i < densityX; i++) {
            for (int j = 0; j < densityY; j++) {
                List<OverlayItem> markerList = grid.get(i).get(j);
                if (markerList.size() > 1) {
                    drawGroup(canvas, mapView, markerList);
                } else {
                    // draw single marker
                    drawSingle(canvas, mapView, markerList);
                }
            }
        }
        
        for (OverlayItem m : mGeneric) {
        	drawSingleItem(canvas, mapView, m);
        }
	}

	private void drawGroup(Canvas canvas, MapView mapView, List<OverlayItem> markerList) {
		OverlayItem item = markerList.get(0);
		GeoPoint point = item.getPoint();
		Point ptScreenCoord = new Point();
		mapView.getProjection().toPixels(point, ptScreenCoord);
		
		if (groupMarker == null) {
			groupMarker = mContext.getResources().getDrawable(R.drawable.marker_poi_generic);
			groupMarker.setBounds(-groupMarker.getIntrinsicWidth()/2, -groupMarker.getIntrinsicHeight(), groupMarker.getIntrinsicWidth() /2, 0);
		}

		drawAt(canvas, groupMarker, ptScreenCoord.x, ptScreenCoord.y, true);
		drawAt(canvas, groupMarker, ptScreenCoord.x, ptScreenCoord.y, false);
        
        Paint paint = new Paint();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(20);
        paint.setAntiAlias(true);
        paint.setARGB(255, 255, 255, 255);
        // show text to the right of the icon
        canvas.drawText(""+markerList.size(), ptScreenCoord.x, ptScreenCoord.y - 23, paint);
    }

    private void drawSingle(Canvas canvas, MapView mapView, List<OverlayItem> markerList) {
        for (OverlayItem item : markerList) {
            drawSingleItem(canvas, mapView, item);
        }
    }


	protected Point drawSingleItem(Canvas canvas, MapView mapView,
			OverlayItem item) {
		GeoPoint point = item.getPoint();
		Point ptScreenCoord = new Point();
		mapView.getProjection().toPixels(point, ptScreenCoord);

		drawAt(canvas, item.getMarker(0), ptScreenCoord.x, ptScreenCoord.y, true);
		drawAt(canvas, item.getMarker(0), ptScreenCoord.x, ptScreenCoord.y, false);
		return ptScreenCoord;
	}
	public static boolean isWithin(Point p, MapView mapView) {
        return (p.x > 0 & p.x < mapView.getWidth() & p.y > 0 & p.y < mapView
                .getHeight());
    }
	
}	
