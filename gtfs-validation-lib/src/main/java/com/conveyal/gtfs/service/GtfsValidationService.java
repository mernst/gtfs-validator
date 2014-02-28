package com.conveyal.gtfs.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.geotools.geometry.jts.JTS;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsDao;
import org.opengis.referencing.operation.TransformException;

import com.conveyal.gtfs.model.DuplicateStops;
import com.conveyal.gtfs.model.InvalidValue;
import com.conveyal.gtfs.model.ValidationResult;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.index.strtree.STRtree;

public class GtfsValidationService {
	
	static GeometryFactory geometryFactory = new GeometryFactory();
	
	private GtfsDao gtfsDao = null;

	public GtfsValidationService(GtfsDao dao)  {
		
		gtfsDao = dao;		
	}
	
	/**
	 * Checks for invalid route values. Returns a ValidationResult object listing invalid/missing data.
	 * 
	 */
	public ValidationResult validateRoutes() {
		
		ValidationResult result = new ValidationResult();
		
		for(Route route : gtfsDao.getAllRoutes()) {
			
			String routeId = route.getId().toString();
			
			String shortName = "";
			String longName = "";
			String desc = "";
			
			if(route.getShortName() != null)
				shortName = route.getShortName().trim().toLowerCase();
			
			if(route.getLongName() != null)
				longName = route.getLongName().trim().toLowerCase();

			if(route.getDesc() != null)
				desc = route.getDesc().toLowerCase();
			
			
			//RouteShortAndLongNamesAreBlank
			if(longName.isEmpty() && shortName.isEmpty())
				result.add(new InvalidValue("route", "route_short_name,route_long_name", routeId , "RouteShortAndLongNamesAreBlank", "", null));
			
			//ValidateRouteShortNameIsTooLong
			if(shortName.length() > 6)
				result.add(new InvalidValue("route", "route_short_name", routeId, "ValidateRouteShortNameIsTooLong", "route_short_name is " +  shortName.length() + " chars ('" +  shortName + "')" , null));
			
			//ValidateRouteLongNameContainShortName
			if(!longName.isEmpty() && !shortName.isEmpty() &&longName.contains(shortName))
				result.add(new InvalidValue("route", "route_short_name,route_long_name", routeId, "ValidateRouteLongNameContainShortName", "'" + longName + "' contains '" + shortName + "'", null));
			
			//ValidateRouteDescriptionSameAsRouteName
			if(!desc.isEmpty() && (desc.equals(shortName) || desc.equals(longName)))
				result.add(new InvalidValue("route", "route_short_name,route_long_name,route_desc", routeId, "ValidateRouteDescriptionSameAsRouteName", "", null));
			
			//ValidateRouteTypeInvalidValid
			if(route.getType() < 0 || route.getType() > 7)
				result.add(new InvalidValue("route", "route_type", routeId, "ValidateRouteTypeInvalidValid", "route_type is " + route.getType(), null ));
			
		}
		
		return result;

	}
	
	/**
	 * Checks for invalid route values. Returns a ValidationResult object listing invalid/missing data.
	 * 
	 */
	public ValidationResult validateTrips() {
		
		ValidationResult result = new ValidationResult();
		
		
		// map stop time sequences to trip id
		
		HashMap<String, ArrayList<StopTime>> tripStopTimes = new HashMap<String, ArrayList<StopTime>>();
		
		HashSet<String> usedStopIds = new HashSet<String>();
		
		for(StopTime stopTime : gtfsDao.getAllStopTimes()) {
			
			String tripId = stopTime.getTrip().getId().toString();
			
			if(!tripStopTimes.containsKey(tripId))
				tripStopTimes.put(tripId, new ArrayList<StopTime>());
			
			tripStopTimes.get(tripId).add(stopTime);
			
			usedStopIds.add(stopTime.getStop().getId().toString());
			
		}
		
		// map shape geometries to shape id
		
		HashMap<String, Geometry> shapes = new HashMap<String, Geometry>();
		HashMap<String, ArrayList<ShapePoint>> shapePointMap = new HashMap<String, ArrayList<ShapePoint>>(); 
		
		for(ShapePoint shapePoint : gtfsDao.getAllShapePoints()) {
			
			String shapeId = shapePoint.getShapeId().getId();
			
			if(!shapePointMap.containsKey(shapeId))
				shapePointMap.put(shapeId, new ArrayList<ShapePoint>());
				
			shapePointMap.get(shapeId).add(shapePoint);
		
		}
	
		// create geometries from shapePoints
		
		for(String shapeId : shapePointMap.keySet()) {
			
			ArrayList<Coordinate> shapeCoords = new ArrayList<Coordinate>();
			
			ArrayList<ShapePoint> shapePoints = shapePointMap.get(shapeId);
			
			Collections.sort(shapePoints, new ShapePointComparator());
			
			for(ShapePoint shapePoint : shapePoints) {
				
				Coordinate stopCoord = new Coordinate(shapePoint.getLat(), shapePoint.getLon());
				
				ProjectedCoordinate projectedStopCoord = GeoUtils.convertLatLonToEuclidean(stopCoord);
				
				shapeCoords.add(projectedStopCoord);
			}
			
			Geometry geom = geometryFactory.createLineString(shapeCoords.toArray(new Coordinate[shapePoints.size()]));
			
			shapes.put(shapeId, geom);
			
		}
		
		
		// create service calendar date map
		
		HashMap<String, HashSet<Date>> serviceCalendarDates = new HashMap<String, HashSet<Date>>();
		
		for(ServiceCalendar calendar : gtfsDao.getAllCalendars()) {
		
			Date startDate = calendar.getStartDate().getAsDate();
			Date endDate = calendar.getEndDate().getAsDate();
		
			HashSet<Date> datesActive = new HashSet<Date>();
			
			Date currentDate = startDate;
			
			HashSet<Integer> daysActive = new HashSet<Integer>();
		
			if(calendar.getSunday() == 1)
				daysActive.add(Calendar.SUNDAY);
			else if(calendar.getMonday() == 1)
				daysActive.add(Calendar.MONDAY);
			else if(calendar.getTuesday() == 1)
				daysActive.add(Calendar.TUESDAY);
			else if(calendar.getWednesday() == 1)
				daysActive.add(Calendar.WEDNESDAY);
			else if(calendar.getThursday() == 1)
				daysActive.add(Calendar.THURSDAY);
			else if(calendar.getFriday() == 1)
				daysActive.add(Calendar.FRIDAY);
			else if(calendar.getSaturday() == 1)
				daysActive.add(Calendar.SATURDAY);
			
			while(currentDate.before(endDate) || currentDate.equals(endDate)) {

				Calendar cal = Calendar.getInstance();
		        cal.setTime(currentDate);

				if(daysActive.contains(cal.get(Calendar.DAY_OF_WEEK)))
					datesActive.add(currentDate);
				
		        cal.add(Calendar.DATE, 1); 
		        currentDate = cal.getTime();
			}
			
			serviceCalendarDates.put(calendar.getServiceId().getId(), datesActive);
			
		}
		
		// add/remove service exceptions
		for(ServiceCalendarDate calendarDate : gtfsDao.getAllCalendarDates()) {
			
			String serviceId = calendarDate.getServiceId().getId();
			
			if(serviceCalendarDates.containsKey(serviceId)) {
				
				
				int exceptionType = calendarDate.getExceptionType();
				
				if(exceptionType == 1)
					serviceCalendarDates.get(serviceId).add(calendarDate.getDate().getAsDate());
				else if (exceptionType == 2 && serviceCalendarDates.get(serviceId).contains(calendarDate.getDate().getAsDate()))
					serviceCalendarDates.get(serviceId).remove(calendarDate.getDate().getAsDate());
			}
			
		}
		
		// check for unused stops 
		
		for(Stop stop : gtfsDao.getAllStops()) {
			
			String stopId = stop.getId().toString();
			
			if(!usedStopIds.contains(stopId)) {
				result.add(new InvalidValue("stop", "stop_id", stopId, "UnusedStop", "Stop Id " + stopId + " is not used in any trips." , null));
			}
		}
		
		
		HashMap<String, ArrayList<BlockInterval>> blockIntervals = new HashMap<String, ArrayList<BlockInterval>>();
		
		HashMap<String, String> duplicateTripHash = new HashMap<String, String>();
		
		
		for(Trip trip : gtfsDao.getAllTrips()) {
			
			String tripId = trip.getId().toString();

			ArrayList<StopTime> stopTimes = tripStopTimes.get(tripId);
			
			if(stopTimes == null || stopTimes.isEmpty()) {
				result.add(new InvalidValue("trip", "trip_id", tripId, "NoStopTimesForTrip", "Trip Id " + tripId + " has no stop times." , null));
				continue;
			}
				
			Collections.sort(stopTimes, new StopTimeComparator());
			
			StopTime previousStopTime = null;
			for(StopTime stopTime : stopTimes) {
				
				if(stopTime.getDepartureTime() < stopTime.getArrivalTime()) 
					result.add(new InvalidValue("stop_time", "trip_id", tripId, "StopTimeDepartureBeforeArrival", "Trip Id " + tripId + " stop sequence " + stopTime.getStopSequence() + " departs before arriving.", null));
				
				if(previousStopTime != null) {					
					
					if(stopTime.getArrivalTime() < previousStopTime.getDepartureTime()) {
						result.add(new InvalidValue("stop_time", "trip_id", tripId, "StopTimesOutOfSequence", "Trip Id " + tripId + " stop sequence " + stopTime.getStopSequence() + " arrives before departing " + previousStopTime.getStopSequence(), null));
						
						// only capturing first out of sequence stop for now -- could consider collapsing duplicates based on tripId
						break;					
					}
						
				}
				
				previousStopTime = stopTime;
			}
				
					
			// store trip intervals by block id
			
			String blockId = "";
			
			if(trip.getBlockId() != null)
				blockId = trip.getBlockId();
			
			if(!blockId.isEmpty()) {
				
				BlockInterval blockInterval = new BlockInterval();
				blockInterval.trip = trip;
				blockInterval.startTime = stopTimes.get(0).getDepartureTime();
				blockInterval.firstStop = stopTimes.get(0);
				blockInterval.lastStop = stopTimes.get(stopTimes.size() -1);
				
				if(!blockIntervals.containsKey(blockId))
					blockIntervals.put(blockId, new ArrayList<BlockInterval>());
				
				blockIntervals.get(blockId).add(blockInterval);
				
			}

			// check for duplicate trips starting at the same time with the same service id
			
			String stopIds = "";
			
			for(StopTime stopTime : stopTimes) {
				stopIds += stopTime.getStop().getId().toString() + ",";
			}
			
			String tripKey = trip.getServiceId().getId() + "_"+ blockId + "_" + stopTimes.get(0).getDepartureTime() +"_" + stopTimes.get(stopTimes.size() -1).getArrivalTime() + "_" + stopIds;
			
			if(duplicateTripHash.containsKey(tripKey)) {
				String duplicateTripId = duplicateTripHash.get(tripKey);
				result.add(new InvalidValue("trip", "trip_id", tripId, "DuplicateTrip", "Trip Ids " + duplicateTripId + " & " + tripId + " are duplicates (" + tripKey + ")" , null));
				
			}
			else
				duplicateTripHash.put(tripKey, tripId);
			
			
		}
		
		// check for overlapping trips within block
		
		
		for(String blockId : blockIntervals.keySet()) {
			
			ArrayList<BlockInterval> invtervals = blockIntervals.get(blockId);
			
			Collections.sort(invtervals, new BlockIntervalComparator());
			
			int iOffset = 0;
			for(BlockInterval i1 : invtervals) { 
				for(BlockInterval i2 : invtervals.subList(iOffset, invtervals.size() - 1)) {
					
					
					String tripId1 = i1.trip.getId().toString();
					String tripId2 = i2.trip.getId().toString();
					
					
					if(!tripId1.equals(tripId2)) {
						// if trips don't overlap, skip 
						if(i1.lastStop.getDepartureTime() <= i2.firstStop.getArrivalTime() || i2.lastStop.getDepartureTime() <= i1.firstStop.getArrivalTime())
							continue;
						
						// if trips have same service id they overlap
						if(i1.trip.getServiceId().getId().equals(i2.trip.getServiceId().getId()))
							result.add(new InvalidValue("trip", "block_id", blockId, "OverlappingTripsInBlock", "Trip Ids " + tripId1 + " & " + tripId2 + " overlap and share block Id " + blockId , null));
						
						else {
							
							// if trips don't share service id check to see if service dates fall on the same days/day of week
							
							for(Date d1 : serviceCalendarDates.get(i1.trip.getServiceId().getId())) {
								
								if(serviceCalendarDates.get(i2.trip.getServiceId().getId()).contains(d1)) {
									result.add(new InvalidValue("trip", "block_id", blockId, "OverlappingTripsInBlock", "Trip Ids " + tripId1 + " & " + tripId2 + " overlap and share block Id " + blockId , null));
									break;
								}
							}
						}
					}
				}
			}
		}
		
		// check for reversed trip shapes and add to result list 
		result.add(this.listReversedTripShapes());
		
		return result;

	}
	
	
	/**
	 * Returns a list of coincident DuplicateStops. 
	 * @throws InputOutOfRange if lat/lon of stops can't be transformed to EPSG:4326
	 * 
	 */
	public ValidationResult duplicateStops()  {
		// default duplicate stops as coincident with a two meter buffer
		return duplicateStops(2.0);
	}
	
	/**
	 * Returns a list of coincident DuplicateStops. 
	 * 
	 * @param the buffer distance for two stops to be considered duplicate
	  * 
	 */
	public ValidationResult duplicateStops(Double bufferDistance)  {
		
		ValidationResult result = new ValidationResult();
		
		Collection<Stop> stops = gtfsDao.getAllStops();
		
		STRtree stopIndex = new STRtree();
		
		HashMap<String, Geometry> stopProjectedGeomMap = new HashMap<String, Geometry>();
		
		for(Stop stop : stops) {
			
			Coordinate stopCoord = new Coordinate(stop.getLat(), stop.getLon());
			
			ProjectedCoordinate projectedStopCoord = GeoUtils.convertLatLonToEuclidean(stopCoord);
			
			Geometry geom = geometryFactory.createPoint(projectedStopCoord);
			
			stopIndex.insert(geom.getEnvelopeInternal(), stop);
			
			stopProjectedGeomMap.put(stop.getId().toString(), geom);
			
		}
		
		stopIndex.build();
		
		List<DuplicateStops> duplicateStops = new ArrayList<DuplicateStops>();
		
		for(Geometry stopGeom : stopProjectedGeomMap.values()) {
			
			Geometry bufferedStopGeom = stopGeom.buffer(bufferDistance);
			
			List<Stop> stopCandidates = (List<Stop>)stopIndex.query(bufferedStopGeom.getEnvelopeInternal());
			
			if(stopCandidates.size() > 1) {
				
				for(Stop stop1 : stopCandidates) {
					for(Stop stop2 : stopCandidates) {
					
						if(stop1.getId() != stop2.getId()) {
							
							Boolean stopPairAlreadyFound = false;
							for(DuplicateStops duplicate : duplicateStops) {
								
								if((duplicate.stop1.getId().getAgencyId().equals(stop1.getId().getAgencyId()) && duplicate.stop2.getId().getAgencyId().equals(stop2.getId().getAgencyId())) || 
								(duplicate.stop2.getId().getAgencyId().equals(stop1.getId().getAgencyId()) && duplicate.stop1.getId().getAgencyId().equals(stop2.getId().getAgencyId())))
									stopPairAlreadyFound = true;
							}
							
							if(stopPairAlreadyFound)
								continue;
						
							Geometry stop1Geom = stopProjectedGeomMap.get(stop1.getId().toString());
							Geometry stop2Geom = stopProjectedGeomMap.get(stop2.getId().toString());
							
							double distance = stop1Geom.distance(stop2Geom);
							
							// if stopDistance is within bufferDistance consider duplicate
							if(distance <= bufferDistance){
								
								// TODO: a good place to check if stops are part of a station grouping
								
								DuplicateStops duplicateStop = new DuplicateStops(stop1, stop2, distance);
								
								duplicateStops.add(duplicateStop);
								
								result.add(new InvalidValue("stop", "stop_lat,stop_lon", duplicateStop.getStopIds(), "DuplicateStops", duplicateStop.toString(), duplicateStop));
								
							}
						}

					}
				}
			}
		}
	
		return result;
	}
	
	public ValidationResult listReversedTripShapes() {
		return listReversedTripShapes(1.0);
	}
	
	public ValidationResult listReversedTripShapes(Double distanceMultiplier) {
    	
		ValidationResult result = new ValidationResult();
		
		Collection<Trip> trips = gtfsDao.getAllTrips();
		
		Collection<StopTime> stopTimes = gtfsDao.getAllStopTimes();
		
		
		HashMap<String, StopTime> firstStopMap = new HashMap<String, StopTime>();
		HashMap<String, StopTime> lastStopMap = new HashMap<String, StopTime>();
		
		// map first and last stops for each trip id
		
		for(StopTime stopTime : stopTimes) {
			String tripId = stopTime.getTrip().getId().toString();
		
			if(firstStopMap.containsKey(tripId)) {
				if(firstStopMap.get(tripId).getStopSequence() > stopTime.getStopSequence())
					firstStopMap.put(tripId, stopTime);
			}
			else 
				firstStopMap.put(tripId, stopTime);
		
			if(lastStopMap.containsKey(tripId)) {
				if(lastStopMap.get(tripId).getStopSequence() < stopTime.getStopSequence())
					lastStopMap.put(tripId, stopTime);
			}
			else 
				lastStopMap.put(tripId, stopTime);
		}
		
		Collection<ShapePoint> shapePoints = gtfsDao.getAllShapePoints();
		
		HashMap<String, ShapePoint> firstShapePoint = new HashMap<String, ShapePoint>();
		HashMap<String, ShapePoint> lastShapePoint = new HashMap<String, ShapePoint>();
    	
		// map first and last shape points
		
		for(ShapePoint shapePoint : shapePoints) {
		
			String shapeId = shapePoint.getShapeId().getId();
			
			if(firstShapePoint.containsKey(shapeId)) {
				if(firstShapePoint.get(shapeId).getSequence() > shapePoint.getSequence())
					firstShapePoint.put(shapeId, shapePoint);
			}
			else 
				firstShapePoint.put(shapeId, shapePoint);
		
			if(lastShapePoint.containsKey(shapeId)) {
				if(lastShapePoint.get(shapeId).getSequence() < shapePoint.getSequence())
					lastShapePoint.put(shapeId, shapePoint);
			}
			else 
				lastShapePoint.put(shapeId, shapePoint);
			
		}
	 		
    	for(Trip trip : trips) {
    		
    	  String tripId = trip.getId().toString();
    	  if (trip.getShapeId() == null) {
    	    result.add(new InvalidValue("trip", "shape_id", tripId, "MissingShape", "Trip " + tripId + " is missing a shape", null));
    	    continue;
    	  }
    		String shapeId = trip.getShapeId().getId();
    		
    		StopTime firstStop = firstStopMap.get(tripId);
    		StopTime lastStop = lastStopMap.get(tripId);
    		
    		Coordinate firstStopCoord = null;
    		Coordinate lastStopCoord = null;
    		try {
    		  firstStopCoord = new Coordinate(firstStop.getStop().getLat(), firstStop.getStop().getLon());
    		  lastStopCoord = new Coordinate(lastStop.getStop().getLat(), lastStop.getStop().getLon());
    		} catch (Exception any) {
    		  // TODO narrow down what could be wrong here
    		  result.add(new InvalidValue("trip", "shape_id", tripId, "MissingCoorinates", "Trip " + tripId + " is missing coordinates", null));
    		  continue;
    		}
    		
    		Geometry firstStopGeom = geometryFactory.createPoint(GeoUtils.convertLatLonToEuclidean(firstStopCoord));
    		Geometry lastStopGeom = geometryFactory.createPoint(GeoUtils.convertLatLonToEuclidean(lastStopCoord));
    		
    		ShapePoint p1 = firstShapePoint.get(shapeId);
    		ShapePoint p2 = lastShapePoint.get(shapeId);
    		
    		Coordinate firstShapeCoord = new Coordinate(firstShapePoint.get(shapeId).getLat(), firstShapePoint.get(shapeId).getLon());
    		Coordinate lastShapeCoord = new Coordinate(lastShapePoint.get(shapeId).getLat(), lastShapePoint.get(shapeId).getLon());
    		
    		Geometry firstShapeGeom = geometryFactory.createPoint(GeoUtils.convertLatLonToEuclidean(firstShapeCoord));
    		Geometry lastShapeGeom = geometryFactory.createPoint(GeoUtils.convertLatLonToEuclidean(lastShapeCoord));
    		
			Double distanceFirstStopToStart = firstStopGeom.distance(firstShapeGeom);
			Double distanceFirstStopToEnd = firstStopGeom.distance(lastShapeGeom);
			
			Double distanceLastStopToEnd = lastStopGeom.distance(lastShapeGeom);
			Double distanceLastStopToStart = lastStopGeom.distance(firstShapeGeom);
			
			// check if first stop is x times closer to end of shape than the beginning or last stop is x times closer to start than the end
			if(distanceFirstStopToStart > (distanceFirstStopToEnd * distanceMultiplier) && distanceLastStopToEnd > (distanceLastStopToStart * distanceMultiplier))
				result.add(new InvalidValue("trip", "shape_id", tripId, "ReversedTripShape", "Trip " + tripId + " references reversed shape " + shapeId, null));
			 	
    	}
    	
    	return result;

    }
	
	private class BlockInterval implements Comparable<BlockInterval> {
		Trip trip;
		Integer startTime;
		StopTime firstStop;
		StopTime lastStop;
		
		public int compareTo(BlockInterval o) {
			return new Integer(this.firstStop.getArrivalTime()).compareTo(new Integer(o.firstStop.getArrivalTime()));
		}
	}
	
	private class BlockIntervalComparator implements Comparator<BlockInterval> {
	    
	    public int compare(BlockInterval a, BlockInterval b) {
	        return new Integer(a.startTime).compareTo(new Integer(b.startTime));
	    }
	}
	
	private class StopTimeComparator implements Comparator<StopTime> {
	    
	    public int compare(StopTime a, StopTime b) {
	        return new Integer(a.getStopSequence()).compareTo(new Integer(b.getStopSequence()));
	    }
	}
	
	private class ShapePointComparator implements Comparator<ShapePoint> {
	    
	    public int compare(ShapePoint a, ShapePoint b) {
	        return new Integer(a.getSequence()).compareTo(new Integer(b.getSequence()));
	    }
	}
}


