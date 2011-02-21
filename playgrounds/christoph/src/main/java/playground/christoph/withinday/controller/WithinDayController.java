/* *********************************************************************** *
 * project: org.matsim.*
 * WithinDayController.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package playground.christoph.withinday.controller;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.parallelEventsHandler.ParallelEventsManagerImpl;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.PersonAgent;
import org.matsim.core.mobsim.framework.events.SimulationInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.SimulationInitializedListener;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeCost;
import org.matsim.core.router.util.AStarLandmarksFactory;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.PersonalizableTravelTime;
import org.matsim.ptproject.qsim.interfaces.Netsim;

import playground.christoph.withinday.events.algorithms.FixedOrderQueueSimulationListener;
import playground.christoph.withinday.events.parallelEventsHandler.SimStepParallelEventsManagerImpl;
import playground.christoph.withinday.mobsim.DuringActivityReplanningModule;
import playground.christoph.withinday.mobsim.DuringLegReplanningModule;
import playground.christoph.withinday.mobsim.InitialReplanningModule;
import playground.christoph.withinday.mobsim.ReplanningManager;
import playground.christoph.withinday.mobsim.WithinDayPersonAgent;
import playground.christoph.withinday.mobsim.WithinDayQSim;
import playground.christoph.withinday.mobsim.WithinDayQSimFactory;
import playground.christoph.withinday.network.WithinDayLinkFactoryImpl;
import playground.christoph.withinday.replanning.identifiers.ActivityEndIdentifierFactory;
import playground.christoph.withinday.replanning.identifiers.InitialIdentifierImplFactory;
import playground.christoph.withinday.replanning.identifiers.LeaveLinkIdentifierFactory;
import playground.christoph.withinday.replanning.identifiers.interfaces.DuringActivityIdentifier;
import playground.christoph.withinday.replanning.identifiers.interfaces.DuringLegIdentifier;
import playground.christoph.withinday.replanning.identifiers.interfaces.InitialIdentifier;
import playground.christoph.withinday.replanning.identifiers.tools.ActivityReplanningMap;
import playground.christoph.withinday.replanning.identifiers.tools.LinkReplanningMap;
import playground.christoph.withinday.replanning.modules.ReplanningModule;
import playground.christoph.withinday.replanning.parallel.ParallelDuringActivityReplanner;
import playground.christoph.withinday.replanning.parallel.ParallelDuringLegReplanner;
import playground.christoph.withinday.replanning.parallel.ParallelInitialReplanner;
import playground.christoph.withinday.replanning.replanners.CurrentLegReplannerFactory;
import playground.christoph.withinday.replanning.replanners.InitialReplannerFactory;
import playground.christoph.withinday.replanning.replanners.NextLegReplannerFactory;
import playground.christoph.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplanner;
import playground.christoph.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplanner;
import playground.christoph.withinday.replanning.replanners.interfaces.WithinDayInitialReplanner;
import playground.christoph.withinday.router.costcalculators.OnlyTimeDependentTravelCostCalculator;
import playground.christoph.withinday.scoring.OnlyTimeDependentScoringFunctionFactory;
import playground.christoph.withinday.trafficmonitoring.TravelTimeCollector;
import playground.christoph.withinday.trafficmonitoring.TravelTimeCollectorFactory;

/**
 * Thimport playground.christoph.withinday.trafficmonitoring.TravelTimeCollector;
is Controller should give an Example what is needed to run
 * Simulations with WithinDayReplanning.
 * 
 * The Path to a Config File is needed as Argument to run the
 * Simulation.
 * 
 * Additional Parameters have to be set in the WithinDayControler
 * Class. Here we just add the Knowledge Component.
 * 
 * By default "test/scenarios/berlin/config.xml" should work.
 * 
 * @author Christoph Dobler
 */
public class WithinDayController extends Controler {

	/*
	 * Define the Probability that an Agent uses the
	 * Replanning Strategy. It is possible to assign
	 * multiple Strategies to the Agents.
	 */
	protected double pInitialReplanning = 0.0;
	protected double pActEndReplanning = 1.0;
	protected double pLeaveLinkReplanning = 1.0;

	/*
	 * How many parallel Threads shall do the Replanning.
	 */
	protected int numReplanningThreads = 2;

	protected PersonalizableTravelTime travelTime;

	protected ParallelInitialReplanner parallelInitialReplanner;
	protected ParallelDuringActivityReplanner parallelActEndReplanner;
	protected ParallelDuringLegReplanner parallelLeaveLinkReplanner;
	protected InitialIdentifier initialIdentifier;
	protected DuringActivityIdentifier duringActivityIdentifier;
	protected DuringLegIdentifier duringLegIdentifier;
	protected WithinDayInitialReplanner initialReplanner;
	protected WithinDayDuringActivityReplanner duringActivityReplanner;
	protected WithinDayDuringLegReplanner duringLegReplanner;

	protected ReplanningManager replanningManager;
	protected WithinDayQSim sim;
	protected FixedOrderQueueSimulationListener foqsl = new FixedOrderQueueSimulationListener();

	private static final Logger log = Logger.getLogger(WithinDayController.class);

	public WithinDayController(String[] args) {
		super(args);

		setConstructorParameters();
	}

	// only for Batch Runs
	public WithinDayController(Config config) {
		super(config);

		setConstructorParameters();
	}

	private void setConstructorParameters() {
		/*
		 * Use MyLinkImpl. They can carry some additional Information like their
		 * TravelTime or VehicleCount.
		 * This is needed to be able to use a LinkVehiclesCounter.
		 */
		this.getNetwork().getFactory().setLinkFactory(new WithinDayLinkFactoryImpl());

		// Use a Scoring Function, that only scores the travel times!
		this.setScoringFunctionFactory(new OnlyTimeDependentScoringFunctionFactory());
	}

	/*
	 * New Routers for the Replanning are used instead of using the controler's.
	 * By doing this every person can use a personalised Router.
	 */
	protected void initReplanningRouter() {

		travelTime = new TravelTimeCollectorFactory().createFreeSpeedTravelTimeCalculator(this.scenarioData);
		foqsl.addQueueSimulationInitializedListener((TravelTimeCollector)travelTime);	// for TravelTimeCollector
		foqsl.addQueueSimulationBeforeSimStepListener((TravelTimeCollector)travelTime);	// for TravelTimeCollector
		foqsl.addQueueSimulationAfterSimStepListener((TravelTimeCollector)travelTime);	// for TravelTimeCollector
		this.events.addHandler((TravelTimeCollector)travelTime);	// for TravelTimeCollector

		OnlyTimeDependentTravelCostCalculator travelCost = new OnlyTimeDependentTravelCostCalculator(travelTime);

		LeastCostPathCalculatorFactory factory = new AStarLandmarksFactory(this.network, new FreespeedTravelTimeCost(this.config.planCalcScore()));
		AbstractMultithreadedModule router = new ReplanningModule(config, network, travelCost, travelTime, factory);

		this.initialIdentifier = new InitialIdentifierImplFactory(this.sim).createIdentifier();
		this.initialReplanner = new InitialReplannerFactory(this.scenarioData, sim.getAgentCounter(), router, 1.0).createReplanner();
		this.initialReplanner.addAgentsToReplanIdentifier(this.initialIdentifier);
		this.parallelInitialReplanner.addWithinDayReplanner(this.initialReplanner);

		ActivityReplanningMap activityReplanningMap = new ActivityReplanningMap(this.getEvents());
		foqsl.addQueueSimulationListener(activityReplanningMap);
		this.duringActivityIdentifier = new ActivityEndIdentifierFactory(activityReplanningMap).createIdentifier();
		this.duringActivityReplanner = new NextLegReplannerFactory(this.scenarioData, sim.getAgentCounter(), router, 1.0).createReplanner();
		this.duringActivityReplanner.addAgentsToReplanIdentifier(this.duringActivityIdentifier);
		this.parallelActEndReplanner.addWithinDayReplanner(this.duringActivityReplanner);

		LinkReplanningMap linkReplanningMap = new LinkReplanningMap(this.getEvents());
		foqsl.addQueueSimulationListener(linkReplanningMap);
		this.duringLegIdentifier = new LeaveLinkIdentifierFactory(linkReplanningMap).createIdentifier();
		this.duringLegReplanner = new CurrentLegReplannerFactory(this.scenarioData, sim.getAgentCounter(), router, 1.0).createReplanner();
		this.duringLegReplanner.addAgentsToReplanIdentifier(this.duringLegIdentifier);
		this.parallelLeaveLinkReplanner.addWithinDayReplanner(this.duringLegReplanner);
	}

	/*
	 * Initializes the ParallelReplannerModules
	 */
	protected void initParallelReplanningModules() {
		this.parallelInitialReplanner = new ParallelInitialReplanner(numReplanningThreads, this);
		this.parallelActEndReplanner = new ParallelDuringActivityReplanner(numReplanningThreads, this);
		this.parallelLeaveLinkReplanner = new ParallelDuringLegReplanner(numReplanningThreads, this);
	}

	/*
	 * Creates the Handler and Listener Object so that they can be handed over to the Within Day
	 * Replanning Modules (TravelCostWrapper, etc).
	 *
	 * The full initialization of them is done later (we don't have all necessary Objects yet).
	 */
	protected void createHandlersAndListeners() {
		replanningManager = new ReplanningManager();
	}

	@Override
	protected void setUp() {
		/*
		 * SimStepParallelEventsManagerImpl might be moved to org.matsim.
		 * Then this piece of code could be placed in the controller.
		 */
		if (this.events instanceof ParallelEventsManagerImpl) {
			log.info("Replacing ParallelEventsManagerImpl with SimStepParallelEventsManagerImpl. This is needed for Within-Day Replanning.");
			SimStepParallelEventsManagerImpl manager = new SimStepParallelEventsManagerImpl();
			this.foqsl.addQueueSimulationAfterSimStepListener(manager);
			this.events = manager;
		}
		
		super.setUp();
	}
	
	@Override
	protected void runMobSim() {
		createHandlersAndListeners();

		sim = new WithinDayQSimFactory().createMobsim(this.scenarioData, this.events);

		ReplanningFlagInitializer rfi = new ReplanningFlagInitializer(this);
		foqsl.addQueueSimulationInitializedListener(rfi);

		/*
		 * Use a FixedOrderQueueSimulationListener to bundle the Listeners and
		 * ensure that they are started in the needed order.
		 */
		foqsl.addQueueSimulationInitializedListener(replanningManager);
		foqsl.addQueueSimulationBeforeSimStepListener(replanningManager);

		sim.addQueueSimulationListeners(foqsl);
		
		log.info("Initialize Parallel Replanning Modules");
		initParallelReplanningModules();

		log.info("Initialize Replanning Routers");
		initReplanningRouter();

		InitialReplanningModule initialReplanningModule = new InitialReplanningModule(parallelInitialReplanner);
		DuringActivityReplanningModule actEndReplanning = new DuringActivityReplanningModule(parallelActEndReplanner);
		DuringLegReplanningModule leaveLinkReplanning = new DuringLegReplanningModule(parallelLeaveLinkReplanner);

		replanningManager.setInitialReplanningModule(initialReplanningModule);
		replanningManager.setActEndReplanningModule(actEndReplanning);
		replanningManager.setLeaveLinkReplanningModule(leaveLinkReplanning);

		sim.run();
	}

	public static class ReplanningFlagInitializer implements SimulationInitializedListener {

		protected WithinDayController withinDayControler;
		protected Map<Id, WithinDayPersonAgent> withinDayPersonAgents;

		protected int noReplanningCounter = 0;
		protected int initialReplanningCounter = 0;
		protected int actEndReplanningCounter = 0;
		protected int leaveLinkReplanningCounter = 0;

		public ReplanningFlagInitializer(WithinDayController controler) {
			this.withinDayControler = controler;
		}

		@Override
		public void notifySimulationInitialized(SimulationInitializedEvent e) {
			collectAgents((Netsim) e.getQueueSimulation());
			setReplanningFlags();
		}

		protected void setReplanningFlags() {
			noReplanningCounter = 0;
			initialReplanningCounter = 0;
			actEndReplanningCounter = 0;
			leaveLinkReplanningCounter = 0;

			Random random = MatsimRandom.getLocalInstance();

			for (WithinDayPersonAgent withinDayPersonAgent : this.withinDayPersonAgents.values()) {
				double probability;
				boolean noReplanning = true;

				probability = random.nextDouble();
				if (probability > withinDayControler.pInitialReplanning) ;
				else {
					withinDayPersonAgent.getReplannerAdministrator().addWithinDayReplanner(withinDayControler.initialReplanner.getId());
					noReplanning = false;
					initialReplanningCounter++;
				}

				// No Replanning
				probability = random.nextDouble();
				if (probability > withinDayControler.pActEndReplanning);
				else {
					withinDayPersonAgent.getReplannerAdministrator().addWithinDayReplanner(withinDayControler.duringActivityReplanner.getId());
					noReplanning = false;
					actEndReplanningCounter++;
				}

				// No Replanning
				probability = random.nextDouble();
				if (probability > withinDayControler.pLeaveLinkReplanning) ;
				else {
					withinDayPersonAgent.getReplannerAdministrator().addWithinDayReplanner(withinDayControler.duringLegReplanner.getId());
					noReplanning = false;
					leaveLinkReplanningCounter++;
				}

				// if non of the Replanning Modules was activated
				if (noReplanning) noReplanningCounter++;

				// (de)activate replanning if they are not needed
				if (initialReplanningCounter == 0) withinDayControler.replanningManager.doInitialReplanning(false);
				else withinDayControler.replanningManager.doInitialReplanning(true);

				if (actEndReplanningCounter == 0) withinDayControler.replanningManager.doActEndReplanning(false);
				else withinDayControler.replanningManager.doActEndReplanning(true);

				if (leaveLinkReplanningCounter == 0) withinDayControler.replanningManager.doLeaveLinkReplanning(false);
				else withinDayControler.replanningManager.doLeaveLinkReplanning(true);
			}

			log.info("Initial Replanning Probability: " + withinDayControler.pInitialReplanning);
			log.info("Act End Replanning Probability: " + withinDayControler.pActEndReplanning);
			log.info("Leave Link Replanning Probability: " + withinDayControler.pLeaveLinkReplanning);

			double numPersons = withinDayControler.population.getPersons().size();
			log.info(noReplanningCounter + " persons don't replan their Plans ("+ noReplanningCounter / numPersons * 100.0 + "%)");
			log.info(initialReplanningCounter + " persons replan their plans initially (" + initialReplanningCounter / numPersons * 100.0 + "%)");
			log.info(actEndReplanningCounter + " persons replan their plans after an activity (" + actEndReplanningCounter / numPersons * 100.0 + "%)");
			log.info(leaveLinkReplanningCounter + " persons replan their plans at each node (" + leaveLinkReplanningCounter / numPersons * 100.0 + "%)");
		}

		protected void collectAgents(Netsim sim) {
			this.withinDayPersonAgents = new TreeMap<Id, WithinDayPersonAgent>();

			for (MobsimAgent mobsimAgent : ((WithinDayQSim) sim).getAgents()) {
				if (mobsimAgent instanceof PersonAgent) {
					PersonAgent personAgent = (PersonAgent) mobsimAgent;
					withinDayPersonAgents.put(personAgent.getId(), (WithinDayPersonAgent) personAgent);
				} else {
					log.warn("MobsimAgent was expected to be from type PersonAgent, but was from type " + mobsimAgent.getClass().toString());
				}
			}
		}
	}

	/*
	 * ===================================================================
	 * main
	 * ===================================================================
	 */

	public static void main(final String[] args) {
		if ((args == null) || (args.length == 0)) {
			System.out.println("No argument given!");
			System.out.println("Usage: Controler config-file [dtd-file]");
			System.out.println();
		} else {
			final WithinDayController controller = new WithinDayController(args);
			controller.setOverwriteFiles(true);
			controller.run();
		}
		System.exit(0);
	}
	
}