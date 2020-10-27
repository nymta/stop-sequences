package com.nyct.stopsequences;

import lombok.AllArgsConstructor;
import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalCoordinates;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.onebusaway.gtfs.model.Stop;

import java.util.*;

@AllArgsConstructor
class FerrisHeuristicStopComparator implements Comparator<Stop> {

    private final DirectedAcyclicGraph<Stop, DefaultEdge> _graph;

    private final Map<Stop, Double> _maxDistance = new HashMap<>();

    private final GeodeticCalculator _gc = new GeodeticCalculator();

    public int compare(Stop o1, Stop o2) {
        double d1 = getMaxDistance(o1);
        double d2 = getMaxDistance(o2);
        return Double.compare(d2, d1);
    }

    private double getMaxDistance(Stop stop) {
        return getMaxDistance(stop, new HashSet<Stop>());
    }

    private double getMaxDistance(Stop stop, Set<Stop> visited) {
        Double d = _maxDistance.get(stop);
        if (d != null) {
            return d;
        }

        if (!visited.add(stop)) {
            throw new IllegalStateException("cycle");
        }

        double dMax = 0.0;
        for (Stop next : _graph.getDescendants(stop)) {
            double potential = _gc.calculateGeodeticCurve(
                    Ellipsoid.WGS84,
                    new GlobalCoordinates(stop.getLon(), stop.getLat()),
                    new GlobalCoordinates(next.getLon(), next.getLat())
            )
                    .getEllipsoidalDistance()
                    + getMaxDistance(next, visited);
            if (potential > dMax) {
                dMax = potential;
            }
        }
        _maxDistance.put(stop, dMax);
        return dMax;
    }
}
