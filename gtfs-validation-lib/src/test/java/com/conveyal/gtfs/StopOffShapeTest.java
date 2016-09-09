package com.conveyal.gtfs;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

import org.junit.BeforeClass;
import org.junit.Test;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;

import com.conveyal.gtfs.model.ValidationResult;
import com.conveyal.gtfs.service.GtfsValidationService;


public class StopOffShapeTest {
	
	static GtfsRelationalDaoImpl gtfsMDao = null;

	@BeforeClass 
	public static void setUpClass() {      
		GtfsReader reader = new GtfsReader();
		gtfsMDao = new GtfsRelationalDaoImpl();

		File gtfsFile = new File("src/test/resources/gtfs_bx10.zip");

		try {
			reader.setInputLocation(gtfsFile);
		} catch (IOException e) {
			e.printStackTrace();
		}

		reader.setEntityStore(gtfsMDao);

		try {
			reader.run();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Test
	public void test() {
		GtfsValidationService valService = new GtfsValidationService(gtfsMDao);
		ValidationResult r = valService.listStopsAwayFromShape(130.0);
		assertTrue(r.invalidValues.size() > 0);
		assertTrue(r.toString().contains("BX100109"));
	}

}
