package playground.mmoyo.Validators;

import java.util.List;

import org.matsim.api.basic.v01.Coord;
import org.matsim.api.basic.v01.Id;
import org.matsim.core.api.network.Node;
import org.matsim.core.network.NetworkLayer;

import playground.mmoyo.PTCase2.PTStation;

public class StationValidator {
	NetworkLayer net;
	
	public StationValidator(NetworkLayer net){
		this.net = net;
	}
	
	/*
	 * Validates that all nodes in an intersection have the same coordinate
	*/
	public boolean hasValidCoordinates(PTStation ptStation){
		for (List<Id> list : ptStation.getIntersecionMap().values()) {
			Id firstId= list.get(0);
			Node firstNode=  net.getNode(firstId);
			Coord firstCoord = firstNode.getCoord();
			for (Id id : list){
				Node node=  net.getNode(id);
				Coord coord = node.getCoord();
				if(!firstCoord.equals(coord))
					throw new java.lang.NullPointerException(id + "PTNode does not have the same coordinates as their sibling PTNodes ");
			}
		}
		return true;
	}
	
}
