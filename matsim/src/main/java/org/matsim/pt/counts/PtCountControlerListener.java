/* *********************************************************************** *
 * project: org.matsim.*
 * CountControlerListener.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.pt.counts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PtCountsConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.counts.CountSimComparison;
import org.matsim.counts.Counts;
import org.matsim.counts.MatsimCountsReader;
import org.matsim.counts.algorithms.CountsComparisonAlgorithm;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class PtCountControlerListener implements StartupListener, IterationEndsListener,
BeforeMobsimListener, AfterMobsimListener  {

	private static enum CountType { Boarding, Alighting, Occupancy }

	private static final Logger log = Logger.getLogger(PtCountControlerListener.class);

	/*
	 * String used to identify the operation in the IterationStopWatch.
	 */
	public static final String OPERATION_COMPAREPTCOUNTS = "compare with pt counts";

	private final static String MODULE_NAME = "ptCounts";
	// yy the above should be removed; the commands should be replaced by the "typed" commands.  kai, oct'10

	private final Config config;
	private final Counts boardCounts, alightCounts,occupancyCounts;
	private final OccupancyAnalyzer occupancyAnalyzer;

	public PtCountControlerListener(final Config config) {
		log.info("Using pt counts.");
		this.config = config;
		this.boardCounts = new Counts();
		this.alightCounts = new Counts();
		this.occupancyCounts = new Counts();
		this.occupancyAnalyzer = new OccupancyAnalyzer(3600, 24 * 3600 - 1) ;
	}

	@Override
	public void notifyStartup(final StartupEvent controlerStartupEvent) {
		PtCountsConfigGroup ptCounts = this.config.ptCounts();
		String boardCountsFilename = ptCounts.getBoardCountsFileName();
		String alightCountsFilename = ptCounts.getAlightCountsFileName();
		String occupancyCountsFilename = ptCounts.getOccupancyCountsFileName();
		if (boardCountsFilename == null || alightCountsFilename == null || occupancyCountsFilename == null) {
			throw new RuntimeException("for pt counts, at this point all three files must be given!");
		}
		new MatsimCountsReader(this.alightCounts).readFile(alightCountsFilename);
		new MatsimCountsReader(this.boardCounts).readFile(boardCountsFilename);
		new MatsimCountsReader(this.occupancyCounts).readFile(occupancyCountsFilename);
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		int iter = event.getIteration();
		if ( isActiveInThisIteration( iter ) ) {
			occupancyAnalyzer.reset(iter);
			event.getControler().getEvents().addHandler(occupancyAnalyzer);
		}
	}

	@Override
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		int it = event.getIteration();
		if ( isActiveInThisIteration( it ) ) {
			event.getControler().getEvents().removeHandler(occupancyAnalyzer);
			occupancyAnalyzer.write(event.getControler().getControlerIO()
					.getIterationFilename(it, "occupancyAnalysis.txt"));
		}
	}

	private boolean isActiveInThisIteration( int iter ) {
		return iter % config.ptCounts().getPtCountsInterval() == 0 && iter >= config.controler().getFirstIteration();
	}

	@Override
	public void notifyIterationEnds(final IterationEndsEvent event) {
		final Controler controler = event.getControler();
		int iter = event.getIteration();
		if ( isActiveInThisIteration( iter ) ) {

			controler.stopwatch.beginOperation(OPERATION_COMPAREPTCOUNTS);

			double countsScaleFactor = Double.parseDouble(this.config.getParam(MODULE_NAME, "countsScaleFactor"));
			Network network = controler.getNetwork();

			Map<CountType,CountsComparisonAlgorithm> cca = new HashMap<CountType,CountsComparisonAlgorithm>();
			cca.put( CountType.Boarding, new CountsComparisonAlgorithm(new CountsComparisonAlgorithm.VolumesForId() {

				@Override
				public double[] getVolumesForStop(Id<TransitStopFacility> locationId) {
					return copyFromIntArray(occupancyAnalyzer.getBoardVolumesForStop(locationId));
				}
			}, this.boardCounts, network, countsScaleFactor)) ;
			cca.put( CountType.Alighting, new CountsComparisonAlgorithm(new CountsComparisonAlgorithm.VolumesForId() {

				@Override
				public double[] getVolumesForStop(Id<TransitStopFacility> locationId) {
					return copyFromIntArray(occupancyAnalyzer.getAlightVolumesForStop(locationId));
				}
			}, this.alightCounts, network, countsScaleFactor) ) ;
			cca.put( CountType.Occupancy, new CountsComparisonAlgorithm(new CountsComparisonAlgorithm.VolumesForId() {

				@Override
				public double[] getVolumesForStop(Id<TransitStopFacility> locationId) {
					return copyFromIntArray(occupancyAnalyzer.getOccupancyVolumesForStop(locationId));
				}
			}, this.occupancyCounts, network, countsScaleFactor) ) ;

			String distanceFilterStr = this.config.findParam(MODULE_NAME, "distanceFilter");
			String distanceFilterCenterNodeId = this.config.findParam(MODULE_NAME, "distanceFilterCenterNode");
			
			if ((distanceFilterStr != null) && (distanceFilterCenterNodeId != null)) {

				
				for ( CountsComparisonAlgorithm algo : cca.values() ) {
					algo.setCountCoordUsingDistanceFilter(Double.parseDouble(distanceFilterStr), distanceFilterCenterNodeId) ;
				}
			}
			for ( CountsComparisonAlgorithm algo : cca.values() ) {
				algo.setCountsScaleFactor(countsScaleFactor);
				algo.run();
			}

			String outputFormat = this.config.findParam(MODULE_NAME, "outputformat");
			if (outputFormat.contains("kml") || outputFormat.contains("all")) {
				OutputDirectoryHierarchy ctlIO=controler.getControlerIO();

				String filename = ctlIO.getIterationFilename(iter, "ptcountscompare.kmz");

				// yy would be good to also just pass a collection/a map but we ain't there. kai, dec'13
				Map< CountType, List<CountSimComparison> > comparisons = new HashMap< CountType, List<CountSimComparison> > () ;
				for ( CountType countType : CountType.values()  ) {
					if ( cca.get( countType ) != null ) {
						comparisons.put( countType, cca.get(countType).getComparison() ) ;
					}
				}
				// the following should work since comparisons.get(...) for material that does not exist should return null, which should be a valid 
				// parameter.  kai, dec'13
				PtCountSimComparisonKMLWriter kmlWriter = new PtCountSimComparisonKMLWriter(comparisons.get(CountType.Boarding), 
						comparisons.get(CountType.Alighting), comparisons.get(CountType.Occupancy),
						TransformationFactory.getCoordinateTransformation(this.config.global().getCoordinateSystem(),TransformationFactory.WGS84),
						this.boardCounts, this.alightCounts,occupancyCounts);

				kmlWriter.setIterationNumber(iter);
				kmlWriter.writeFile(filename);
			}
			if (outputFormat.contains("txt") || outputFormat.contains("all")) {
				OutputDirectoryHierarchy ctlIO=controler.getControlerIO();

				for ( Entry<CountType,CountsComparisonAlgorithm> entry : cca.entrySet() ) {
					CountsComparisonAlgorithm algo = entry.getValue() ;
					new PtCountSimComparisonTableWriter(algo.getComparison()).write(ctlIO.getIterationFilename(iter, "simCountCompare" + entry.getKey().toString() + ".txt"));
				}
			}

			controler.stopwatch.endOperation(OPERATION_COMPAREPTCOUNTS);
		}
	}

	private static double[] copyFromIntArray(int[] source) {
	    double[] dest = new double[source.length];
	    for(int i=0; i<source.length; i++) {
	        dest[i] = source[i];
	    }
	    return dest;
	}
	
}
