/* *********************************************************************** *
 * project: org.matsim.*
 * Agent2DFactory.java
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
package playground.gregor.sim2d_v2.simulation;

import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.MatsimFactory;
import org.matsim.ptproject.qsim.agents.PersonDriverAgentImpl;
import org.matsim.ptproject.qsim.interfaces.Netsim;

/**
 * @author laemmel
 * 
 */
public class Agent2DFactory implements MatsimFactory {

	private final Netsim sim;

	public Agent2DFactory(Netsim sim) {
		this.sim = sim;
	}

	/**
	 * @param p
	 * @return
	 */
	public PersonDriverAgentImpl createPersonAgent(Person p) {
		return new Agent2D(p, this.sim);
	}

}
