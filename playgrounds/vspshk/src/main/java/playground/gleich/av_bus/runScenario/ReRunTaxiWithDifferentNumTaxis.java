package playground.gleich.av_bus.runScenario;

import java.io.File;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.av.intermodal.router.VariableAccessTransitRouterModule;
import org.matsim.contrib.av.intermodal.router.config.VariableAccessConfigGroup;
import org.matsim.contrib.av.intermodal.router.config.VariableAccessModeConfigGroup;
import org.matsim.contrib.dvrp.data.FleetImpl;
import org.matsim.contrib.dvrp.data.file.VehicleReader;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.taxi.run.TaxiConfigConsistencyChecker;
import org.matsim.contrib.taxi.run.TaxiConfigGroup;
import org.matsim.contrib.taxi.run.TaxiModule;
import org.matsim.contrib.taxi.run.TaxiOutputModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;

import playground.gleich.av_bus.FilePaths;

public class ReRunTaxiWithDifferentNumTaxis {
// Override FixedDistanceBasedVariableAccessModule in order to return taxi only for access/egress trips originating or ending within study area
	public static void main(String[] args) {
		Config config = ConfigUtils.loadConfig(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_CONFIG_BERLIN__10PCT_TAXI_KEEP_LAST_SELECTED_PLAN,
				new TaxiConfigGroup(), new DvrpConfigGroup());
		VariableAccessConfigGroup vacfg = new VariableAccessConfigGroup();
		vacfg.setVariableAccessAreaShpFile(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_AV_OPERATION_AREA_SHP);
		vacfg.setVariableAccessAreaShpKey(FilePaths.AV_OPERATION_AREA_SHP_KEY);
		vacfg.setStyle("fixed"); //FixedDistanceBasedVariableAccessModule
		{
			VariableAccessModeConfigGroup taxi = new VariableAccessModeConfigGroup();
			taxi.setDistance(20000);
			taxi.setTeleported(false);
			taxi.setMode("taxi");
			vacfg.setAccessModeGroup(taxi);
		}
		{
			VariableAccessModeConfigGroup walk = new VariableAccessModeConfigGroup();
			walk.setDistance(1000);
			walk.setTeleported(true);
			walk.setMode("walk");
			vacfg.setAccessModeGroup(walk);
		}
		config.addModule(vacfg);

		// ScenarioUtils.loadScenario(config) searches files starting at the directory where the config is located
		config.network().setInputFile("../../../../" + FilePaths.PATH_NETWORK_BERLIN__10PCT);
		//rerun with different number of taxis
		config.plans().setInputFile("../../../../" + "data/output/Berlin10pct/Taxi_50_exactGeometry/output_plans.xml.gz");
		config.transit().setVehiclesFile("../../../../" + FilePaths.PATH_TRANSIT_VEHICLES_BERLIN__10PCT);
		config.transit().setTransitScheduleFile("../../../../" + FilePaths.PATH_TRANSIT_SCHEDULE_BERLIN__10PCT_WITHOUT_BUSES_IN_STUDY_AREA);
		config.transitRouter().setSearchRadius(15000);
		config.transitRouter().setExtensionRadius(0);
		config.addConfigConsistencyChecker(new TaxiConfigConsistencyChecker());
		config.checkConsistency();
		config.global().setNumberOfThreads(4);
		config.transitRouter().setDirectWalkFactor(100);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(2);
		config.controler().setOutputDirectory(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_OUTPUT_BERLIN__10PCT_TAXI_20);
		config.controler().setWritePlansInterval(1);
		config.qsim().setEndTime(60*60*60);
		config.controler().setWriteEventsInterval(1);	
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		
//		TaxiConfigGroup.get(config).setTaxisFile(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_TAXI_VEHICLES_20_BERLIN__10PCT);
		System.out.println((new File(FilePaths.PATH_TAXI_VEHICLES_20_BERLIN__10PCT)).getAbsolutePath());
		System.out.println((new File("../../../../" + FilePaths.PATH_TAXI_VEHICLES_20_BERLIN__10PCT)).getAbsolutePath());
		System.out.println((new File(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_TAXI_VEHICLES_20_BERLIN__10PCT)).getAbsolutePath());
		TaxiConfigGroup.get(config).setTaxisFile("../../../../" + FilePaths.PATH_TAXI_VEHICLES_20_BERLIN__10PCT);
		FleetImpl fleet = new FleetImpl();
		new VehicleReader(scenario.getNetwork(), fleet).readFile(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_TAXI_VEHICLES_20_BERLIN__10PCT);
		
		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new TaxiOutputModule());
        controler.addOverridingModule(new TaxiModule());
		controler.addOverridingModule(new VariableAccessTransitRouterModule());
		
		controler.run();
	}

}
