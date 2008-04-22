package com.tilab.msn;

import jade.util.Logger;

import java.util.ArrayList;
import java.util.List;

import android.content.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;

import android.location.Location;

import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

import com.google.android.maps.Overlay;
import com.google.android.maps.Point;

public class ContactsPositionOverlay extends Overlay {
	
	private final Logger myLogger = Logger.getMyLogger(this.getClass().getName());
	private int UPPER_THRESHOLD = 0;
	private  int LOWER_THRESHOLD = 0;
	
	private MapController mapController; 
	private Paint myPaint;
	private Bitmap ylwbmp;
	private Bitmap bluebmp;
	private Resources appRes;
	
	//The SCROLL area represents the area that finds when points are going out of the screen 
	//If one of them is out of the hot area we need to recenter the map to follow the point.
	//HotAreaWidth/ScreenWidth
	private final float SCROLL_AREA_WIDTH_RATIO = 0.70f;
	private final float SCROLL_AREA_HEIGHT_RATIO= 0.70f;
	
	//This ratio is referred to SCREEN WIDTH
	private final float UPPER_THRESHOLD_RATIO = 0.5f;
	private final float LOWER_THRESHOLD_RATIO = 0.2f;
	
	
	
	private int SCROLL_AREA_WIDTH=-1;
	private int SCROLL_AREA_HEIGHT=-1;
	
	private final int CONTACT_LOC_POINT_RADIUS = 5;
	
	private MapView myMapView;

	private int zoomLevel;
	private int SQUARED_WIDTH=-1; 
	private int WIDTH=-1;
	private int HEIGHT=-1;
	private int centerScreenX;
	private int centerScreenY;
	
	private final android.graphics.Point PIN_HOTSPOT = new android.graphics.Point(0,0); 
    

	private final int ZOOM_MAX=0;
	private final int RECOMPUTE_ZOOM=1;
	private final int NO_ZOOM=2;
	
	private Rect scrollingArea;
	
	public ContactsPositionOverlay(MapView myMapView, Resources ctn){
		mapController = myMapView.getController();
		appRes= ctn;
		myPaint = new Paint();
		this.myMapView = myMapView;
		scrollingArea= new Rect();
		
		ylwbmp = BitmapFactory.decodeResource(appRes,R.drawable.ylw_circle); 
		bluebmp = BitmapFactory.decodeResource(appRes,R.drawable.blu_circle); 
		
		
	}
	
	
	
	
	private boolean scrollingIsNeeded(List<ContactLayoutData> pointList){
		
		for (int i = 0; i < pointList.size(); i++) {
			ContactLayoutData cData = pointList.get(i);
			if (!scrollingArea.contains(cData.positionOnScreen[0], cData.positionOnScreen[1])){
				return true;
			}
		}
		
		return false;
		
	}
	
	private int zoomChangeIsNeeded(PointClusterParams params){
		
		int retval = NO_ZOOM;
		
		int currentNumberOfPoints = params.contactPoints.size();
		
		//If we have just one point left, we need to zoom to max level
		if (currentNumberOfPoints == 1 &&  myMapView.getZoomLevel() < 21){
			retval = ZOOM_MAX;
		} else if (currentNumberOfPoints > 1){
		 
			//If we have many points compute the max squared distance from the midpoint
				int maxDistSquared = getMaxDistSquared(params.contactPoints, params.midpointOnScreen);
					
			//if we are in the too far or too near range
				if (maxDistSquared < LOWER_THRESHOLD || maxDistSquared > UPPER_THRESHOLD){
					retval = RECOMPUTE_ZOOM;
				}
				
					
		}
		return retval;
	}
	
	private void doScrolling(PointClusterParams params){
/*		mapController.zoomTo(18);
		int longSpan = myMapView.getLongitudeSpan();
		int latSpan = myMapView.getLatitudeSpan();
		Point center = myMapView.getMapCenter();
		Point newCenter = pixelToMap(params.midpointOnScreen, WIDTH, HEIGHT, latSpan, longSpan, center);
*/		
		mapController.centerMapTo(params.midpointOnMap, true);
	}
	
	private void doZoom(PointClusterParams params, int howToZoom){
		if (howToZoom == ZOOM_MAX)
			mapController.zoomTo(18);
		if (howToZoom == RECOMPUTE_ZOOM)
			mapController.zoomToSpan(params.coordMaxSpan[0],params.coordMaxSpan[1]);
	}
	
	
	private void drawOnlineContacts(Canvas c, Paint p, List<ContactLayoutData> layoutDataList){
		
		int size = layoutDataList.size();
		int oldColor = p.getColor();
		FontMetrics fm = p.getFontMetrics();
		int offset =((int) fm.bottom)+CONTACT_LOC_POINT_RADIUS;
		int blueheight= bluebmp.getHeight();
        int bluewidth= bluebmp.getWidth()/2;
        int ylwheight= ylwbmp.getHeight();
        int ylwwidth= ylwbmp.getWidth()/2;
		for (int i=0; i < size; i++){
			ContactLayoutData cData = layoutDataList.get(i);			
			if (cData.isMyContact){
				myPaint.setColor(Color.YELLOW);					
				myPaint.setStrokeWidth(10);
				c.drawBitmap(ylwbmp, cData.positionOnScreen[0]-ylwwidth, cData.positionOnScreen[1]-ylwheight, myPaint);
			} else {
				c.drawBitmap(bluebmp, cData.positionOnScreen[0]-bluewidth, cData.positionOnScreen[1]-blueheight, myPaint);
				myPaint.setColor(Color.BLUE);	
			}
		  int textheight= bluebmp.getHeight()+5;		  
          c.drawText(cData.name, cData.positionOnScreen[0], cData.positionOnScreen[1]-textheight, myPaint);	
          
         
		}
		
		p.setColor(oldColor);
	}

	
	
	
	@Override
	public void draw(Canvas canvas, PixelCalculator calculator, boolean shadow) {
		// TODO Auto-generated method stub
		super.draw(canvas, calculator, shadow);
		
		//GET the current contacts
		Contact myCont = ContactManager.getInstance().getMyContact();
		List<Contact> contacts = ContactManager.getInstance().getOtherContactList();		
		
		//Compute params needed for further computations on the point cluster
		PointClusterParams params = extractParams(contacts, myCont, calculator);
		
		//Things we do just the first time
		if (WIDTH == -1){
			initialize(params, calculator);
		} 
		
		//if any pixel is out our scrolling area
		if (scrollingIsNeeded(params.contactPoints)){
			//change map center
			doScrolling(params);
		}
		
		int howToZoom = zoomChangeIsNeeded(params);
		
		if (howToZoom != NO_ZOOM)	{
			doZoom(params,howToZoom);
		}
	
		//Draw all the contacts
		drawOnlineContacts(canvas, myPaint, params.contactPoints);
	
	}
	
	
	
	private void initialize(PointClusterParams params, PixelCalculator calculator) {
			WIDTH = calculator.getMapWidth();
			HEIGHT = calculator.getMapHeight();
			centerScreenX = WIDTH / 2;
			centerScreenY = HEIGHT / 2;

			
			myLogger.log(Logger.INFO, "WIDTH = "+WIDTH);
			myLogger.log(Logger.INFO, "HEIGHT = "+HEIGHT);
			myLogger.log(Logger.INFO, "MapViev WIDTH = "+myMapView.getWidth());
			myLogger.log(Logger.INFO, "Map View HEIGHT = "+myMapView.getHeight());
			
			SQUARED_WIDTH = WIDTH*WIDTH;
			SCROLL_AREA_HEIGHT = (int) (HEIGHT * SCROLL_AREA_HEIGHT_RATIO);
			SCROLL_AREA_WIDTH = (int) (WIDTH * SCROLL_AREA_WIDTH_RATIO);
			scrollingArea.top = (HEIGHT - SCROLL_AREA_HEIGHT)/2;
			scrollingArea.bottom = scrollingArea.top + SCROLL_AREA_HEIGHT;
			scrollingArea.left = (WIDTH - SCROLL_AREA_WIDTH)/2;
			scrollingArea.right = scrollingArea.left + SCROLL_AREA_WIDTH;
			int tmpThresh =  (int ) (WIDTH * UPPER_THRESHOLD_RATIO);
			UPPER_THRESHOLD = tmpThresh * tmpThresh;
			tmpThresh = (int) (WIDTH * LOWER_THRESHOLD_RATIO);
			LOWER_THRESHOLD = tmpThresh * tmpThresh;
			doScrolling(params);
			int howToZoom = zoomChangeIsNeeded(params);
			doZoom(params, howToZoom);
	
	}
	private PointClusterParams extractParams(List<Contact> contacts, Contact myContact, PixelCalculator calc){
		int contactsOnLine = 1;
		int maxLat;
		int minLat;
		int maxLong;
		int minLong;
		
		int midPointX=0;
		int midPointY=0;
		
		PointClusterParams params = new PointClusterParams();
		params.contactPoints = new ArrayList<ContactLayoutData>();

		//Compute needed params for my contact
		Location myContactLoc = myContact.getLocation();
		maxLat = (int)(myContactLoc.getLatitude() * 1E6);
		maxLong = (int)(myContactLoc.getLongitude() * 1E6);
		minLong = (int)(myContactLoc.getLongitude() * 1E6);
		minLat = (int)(myContactLoc.getLatitude() * 1E6);
		ContactLayoutData myPointData = new ContactLayoutData(myContact.getName(),myContactLoc ,calc );
		myPointData.isMyContact = true;
		
		//Compute mid x and y
		midPointX += myPointData.positionOnScreen[0];
		midPointY += myPointData.positionOnScreen[1];
		
		params.contactPoints.add(myPointData);
		
		//compute params for all the others
		for (int i = 0; i < contacts.size(); i++) {
			Contact ctn = contacts.get(i);
			
			//only online contacts are drawn on the map
			if (ctn.isOnline()){
			
				contactsOnLine++;
				
				Location contactLoc = ctn.getLocation();
				
				int tmpLat = (int)(contactLoc.getLatitude() * 1E6);
				int tmpLong = (int)(contactLoc.getLongitude() * 1E6);
				
				maxLat = (tmpLat > maxLat)? tmpLat : maxLat;
				maxLong = (tmpLong > maxLong)? tmpLong : maxLong;
				minLong = (tmpLong < minLong)? tmpLong : minLong;
				minLat = (tmpLat < minLat)? tmpLat : minLat;
				
				
				ContactLayoutData pointData = new ContactLayoutData(ctn.getName(),contactLoc ,calc );
				
				midPointX += pointData.positionOnScreen[0];
				midPointY += pointData.positionOnScreen[1];
				
				params.contactPoints.add(pointData);
			}
		}
		
		params.midpointOnScreen = new int[2];
		params.midpointOnScreen[0] = midPointX / contactsOnLine;
		params.midpointOnScreen[1] = midPointY / contactsOnLine;
		
		//test
		params.midpointOnMap = screenToMap(params.midpointOnScreen);
		//		params.midpointOnMap = new Point((maxLat + minLat)/2, (maxLong + minLong)/2);
		params.coordMaxSpan = new int[2];
		
		//we need to zoom in another way if we have a single point
		if (maxLat == minLat){
			params.coordMaxSpan[0] = -1;
			params.coordMaxSpan[1] = -1;
		} else {
			params.coordMaxSpan[0] = maxLat -minLat;
			params.coordMaxSpan[1] = maxLong - minLong;
		}	
		
		return params;
	}
	
	private int getMaxDistSquared(List<ContactLayoutData> points,int[] midpoint){
		
		int maxDist =0;
		
		//For each point
		for (int i = 0; i < points.size(); i++) {
			//Compute distance squared
			ContactLayoutData data  = points.get(i);
			int distX = midpoint[0] - data.positionOnScreen[0];
			int distY = midpoint[1] - data.positionOnScreen[1];
			int distSq = distX*distX + distY*distY;
			
			if (distSq > maxDist)
				maxDist = distSq;
		}
		
		return maxDist;
	}
	

	//Converts a point on screen (pixel coordinates) into  point on the map (Lat/Long Coordinated)
	
	private Point screenToMap(int [] point) {
		//Calculate ratio 
		int latitudeSpan = myMapView.getLatitudeSpan();
		int longitudeSpan = myMapView.getLongitudeSpan();

		int microDegreePerPixelLatitude = latitudeSpan / HEIGHT;
		int microDegreePerPixelLongitude = longitudeSpan / WIDTH;
		int deltaX = centerScreenX - point[0];
		int deltaY = centerScreenY - point[1];
		int deltaLatitude = microDegreePerPixelLatitude * deltaY;
		int deltaLongitude = microDegreePerPixelLongitude * deltaX;
		int computedLocationLatitude = myMapView.getMapCenter().getLatitudeE6()
		          + deltaLatitude;
		int computedLocationLongitude = myMapView.getMapCenter().getLongitudeE6()
		          - deltaLongitude;

		
		return  new Point(computedLocationLatitude,computedLocationLongitude);
	}
	private class PointClusterParams {
		public int[] coordMaxSpan;
		public Point midpointOnMap;
		public List<ContactLayoutData> contactPoints;
		public int[] midpointOnScreen;
		
	}
	
	//This class represents the data of the contact to be displayed (position, name, color)
	private class ContactLayoutData{
	
		public int[] positionOnScreen;
		public String name;
		public boolean isMyContact;
		
		//Constructor for storing midpoint data
		public ContactLayoutData(int x, int y){
			name = "Midpoint";
			isMyContact=false;
			positionOnScreen= new int[2];
			positionOnScreen[0] =x;
			positionOnScreen[1] = y;
		}
		
		
		public ContactLayoutData(String cname, Location contactLoc, PixelCalculator pixelCalc){
			this.name = cname;
			isMyContact = false;
			positionOnScreen = new int[2];
			int latitudeE6 = (int)(contactLoc.getLatitude() * 1E6);
			int longitudeE6 = (int) (contactLoc.getLongitude() * 1E6);
			pixelCalc.getPointXY(new Point(latitudeE6,longitudeE6), positionOnScreen);
		}	
	}		
}
