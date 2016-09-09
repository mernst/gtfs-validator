package com.conveyal.gtfs;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.serialization.GtfsReader;

import com.conveyal.gtfs.service.impl.GtfsStatisticsService;
 
public class GtfsStatisticsServiceTest {
 
	static GtfsDaoImpl store = null;
	static GtfsStatisticsService gtfsStats = null;
	
	@BeforeClass 
    public static void setUpClass() {      
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        
        store = new GtfsDaoImpl();
        GtfsReader reader = new GtfsReader();
        
        File gtfsFile = new File("src/test/resources/st_gtfs_good.zip");
        
        try {
			reader.setInputLocation(gtfsFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        try {
			reader.setInputLocation(gtfsFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
        
    	reader.setEntityStore(store);

    	try {
			reader.run();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	gtfsStats = new GtfsStatisticsService(store);
    }
	
	@Test
	public void agencyCount() {
		Assert.assertEquals(gtfsStats.getAgencyCount(), new Integer(1));
	}
 
	@Test
	public void routeCount() {
		Assert.assertEquals(gtfsStats.getRouteCount(), new Integer(16));
	}
	
	@Test
	public void tripCount() {
		Assert.assertEquals(gtfsStats.getTripCount(), new Integer(559));
	}
 
	@Test
	public void stopCount() {
		Assert.assertEquals(gtfsStats.getStopCount(), new Integer(93));
	}
	
	@Test
	public void stopTimeCount() {
		Assert.assertEquals(gtfsStats.getStopTimesCount(), new Integer(7345));
	}
	
	@Test
	public void calendarDateRangeStart() {
		Assert.assertEquals(gtfsStats.getCalendarDateStart().get(), new Date(1401062400000l));
	}
	
	@Test
	public void calendarDateRangeEnd() {
		Assert.assertEquals(gtfsStats.getCalendarDateEnd().get(), new Date(1401062400000l));
	}

}

