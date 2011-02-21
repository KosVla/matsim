/* *********************************************************************** *
 * project: org.matsim.*
 * QSimEngineRunner.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package org.matsim.ptproject.qsim.qnetsimengine;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;

import org.matsim.core.gbl.Gbl;
import org.matsim.ptproject.qsim.QSim;
import org.matsim.ptproject.qsim.interfaces.DepartureHandler;
import org.matsim.ptproject.qsim.interfaces.NetsimLink;
import org.matsim.ptproject.qsim.interfaces.NetsimNetworkFactory;
import org.matsim.ptproject.qsim.interfaces.NetsimNode;

public class QSimEngineRunner extends QSimEngineInternalI implements Runnable {

	private double time = 0.0;
	private boolean simulateAllNodes = false;
	private boolean simulateAllLinks = false;
	private boolean useNodeArray = QSimEngineImpl.useNodeArray;

	private volatile boolean simulationRunning = true;

	private final CyclicBarrier startBarrier;
	private final CyclicBarrier separationBarrier;
	private final CyclicBarrier endBarrier;

	private QNode[] nodesArray = null;
	private List<QNode> nodesList = null;
	private List<QLinkInternalI> linksList = new ArrayList<QLinkInternalI>();

	/** This is the collection of nodes that have to be activated in the current time step.
	 * This needs to be thread-safe since it is not guaranteed that each incoming link is handled
	 * by the same thread as a node itself. */
	private final Queue<QNode> nodesToActivate = new ConcurrentLinkedQueue<QNode>();

	/** This is the collection of links that have to be activated in the current time step */
	private final ArrayList<QLinkInternalI> linksToActivate = new ArrayList<QLinkInternalI>();
	private final QSim qsim;
	private final AgentSnapshotInfoBuilder positionInfoBuilder;

	/*package*/ QSimEngineRunner(boolean simulateAllNodes, boolean simulateAllLinks, CyclicBarrier startBarrier, CyclicBarrier separationBarrier, CyclicBarrier endBarrier,
			QSim sim, AgentSnapshotInfoBuilder positionInfoBuilder) {
		this.simulateAllNodes = simulateAllNodes;
		this.simulateAllLinks = simulateAllLinks;
		this.startBarrier = startBarrier;
		this.separationBarrier = separationBarrier;
		this.endBarrier = endBarrier;
		this.qsim = sim;
		this.positionInfoBuilder = positionInfoBuilder;
	}

	/*package*/ void setQNodeArray(QNode[] nodes) {
		this.nodesArray = nodes;
	}

	/*package*/ void setQNodeList(List<QNode> nodes) {
		this.nodesList = nodes;
	}

	/*package*/ void setLinks(List<QLinkInternalI> links) {
		this.linksList = links;
	}

	/*package*/ void setTime(final double t) {
		time = t;
	}

	@Override
	public void afterSim() {
		this.simulationRunning = false;
	}

	@Override
	public void doSimStep(double time) {
		// nothing to do here
	}

	@Override
	public void run() {
		/*
		 * The method is ended when the simulationRunning Flag is
		 * set to false.
		 */
		while(true) {
			try {
				/*
				 * The Threads wait at the startBarrier until they are
				 * triggered in the next TimeStep by the run() method in
				 * the ParallelQSimEngine.
				 */
				startBarrier.await();

				/*
				 * Check if Simulation is still running.
				 * Otherwise print CPU usage and end Thread.
				 */
				if (!simulationRunning) {
					Gbl.printCurrentThreadCpuTime();
					return;
				}

				/*
				 * Move Nodes
				 */
				if (useNodeArray) {
					for (QNode node : nodesArray) {
//						synchronized(node) {
							Random random = (Random) node.getCustomAttributes().get(Random.class.getName());
							if (node.isActive() /*|| node.isSignalized()*/ || simulateAllNodes) {
								node.moveNode(time, random);
							}
//						}
					}
				} else {
					ListIterator<QNode> simNodes = this.nodesList.listIterator();
					QNode node;

					while (simNodes.hasNext()) {
						node = simNodes.next();
						Random random = (Random) node.getCustomAttributes().get(Random.class.getName());
						node.moveNode(time, random);

						if (!node.isActive()) simNodes.remove();
					}
				}

				/*
				 * After moving the Nodes all we use a CyclicBarrier to synchronize
				 * the Threads. By using a Runnable within the Barrier we activate
				 * some Links.
				 */
				this.separationBarrier.await();

				/*
				 * Move Links
				 */
				ListIterator<QLinkInternalI> simLinks = this.linksList.listIterator();
				QLinkInternalI link;
				boolean isActive;

				while (simLinks.hasNext()) {
					link = simLinks.next();

					/*
					 * Synchronize on the QueueLink is only some kind of Workaround.
					 * It is only needed, if the QueueSimulation teleports Vehicles
					 * between different Threads. It would be probably faster, if the
					 * QueueSimulation would contain a synchronized method to do the
					 * teleportation instead of synchronize on EVERY QueueLink.
					 */
//					synchronized(link) {
						isActive = link.moveLink(time);

						if (!isActive && !simulateAllLinks) {
							simLinks.remove();
						}
//					}
				}

				/*
				 * The End of the Moving is synchronized with
				 * the endBarrier. If all Threads reach this Barrier
				 * the main Thread can go on.
				 */
				endBarrier.await();
			} catch (InterruptedException e) {
				Gbl.errorMsg(e);
			} catch (BrokenBarrierException e) {
            	Gbl.errorMsg(e);
            }
		}
	}	// run()

	@Override
	protected void activateLink(QLinkInternalI link) {
		if (!simulateAllLinks) {
			linksToActivate.add(link);
		}
	}

	/*package*/ void activateLinks() {
		this.linksList.addAll(this.linksToActivate);
		this.linksToActivate.clear();
	}

	@Override
	public int getNumberOfSimulatedLinks() {
		return this.linksList.size();
	}

	@Override
	protected void activateNode(QNode node) {
		if (!useNodeArray && !simulateAllNodes) {
			this.nodesToActivate.add(node);
		}
	}

	/*package*/ void activateNodes() {
		if (!useNodeArray && !simulateAllNodes) {
			this.nodesList.addAll(this.nodesToActivate);
			this.nodesToActivate.clear();
		}
	}

	@Override
	public int getNumberOfSimulatedNodes() {
		if (useNodeArray) return nodesArray.length;
		else return nodesList.size();
	}

	@Override
	public QSim getMobsim() {
		return this.qsim;
	}

	@Override
	public void onPrepareSim() {
		// currently nothing to do
	}

	@Override
	public AgentSnapshotInfoBuilder getAgentSnapshotInfoBuilder() {
		return this.positionInfoBuilder;
	}

	@Override
	public QNetwork getNetsimNetwork() {
		throw new UnsupportedOperationException("should never be called this way since this is just the runner");
	}

	@Override
	public NetsimNetworkFactory<QNode,QLinkInternalI> getNetsimNetworkFactory() {
		return new DefaultQNetworkFactory() ;
	}

	@Override
	public DepartureHandler getDepartureHandler() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException() ;
	}

}
