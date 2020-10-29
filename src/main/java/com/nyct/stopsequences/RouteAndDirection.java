package com.nyct.stopsequences;

import lombok.Value;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;

import java.util.Map;
import java.util.Set;

@Value
public class RouteAndDirection {
    Route route;
    String directionId;
    DirectedAcyclicGraph<Stop, DefaultEdge> stopGraph;
    Map<Stop, Long> stopSequences;
    Set<String> tripHeadsigns;
}
