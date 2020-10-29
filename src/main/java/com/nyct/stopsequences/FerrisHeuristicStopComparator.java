/*
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nyct.stopsequences;

import lombok.AllArgsConstructor;
import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalCoordinates;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.onebusaway.gtfs.model.Stop;

import java.util.*;

@AllArgsConstructor
class FerrisHeuristicStopComparator implements Comparator<Stop> {
    private final DirectedAcyclicGraph<Stop, DefaultEdge> graph;

    private final Map<Stop, Double> maxDistanceMap = new HashMap<>();

    private static final GeodeticCalculator GC = new GeodeticCalculator();

    public int compare(Stop o1, Stop o2) {
        final double d1 = getMaxDistance(o1);
        final double d2 = getMaxDistance(o2);
        return Double.compare(d2, d1);
    }

    private static double computeDistance(Stop s1, Stop s2) {
        return GC.calculateGeodeticCurve(
                Ellipsoid.WGS84,
                new GlobalCoordinates(s1.getLat(), s1.getLon()),
                new GlobalCoordinates(s2.getLat(), s2.getLon())
        )
                .getEllipsoidalDistance();
    }

    private double getMaxDistance(Stop stop) {
        return getMaxDistance(stop, new HashSet<>());
    }

    private double getMaxDistance(Stop stop, Set<Stop> visited) {
        /*
         * This looks like a great use case for Map.computeIfAbsent(), right?
         * Unfortunately we can't, because getMaxDistance() updates the map recursively, which the contract for
         * Map.computeIfAbsent() forbids: "The mapping function should not modify this map during computation."
         */
        if (!maxDistanceMap.containsKey(stop)) {
            if (!visited.add(stop)) {
                throw new IllegalStateException("Graph contains a cycle; this shouldn't be possible ");
            }

            final double dMax = Graphs.successorListOf(graph, stop)
                    .stream()
                    .mapToDouble(next -> computeDistance(stop, next) + getMaxDistance(next, visited))
                    .max()
                    .orElse(0.0);

            maxDistanceMap.put(stop, dMax);
        }

        return maxDistanceMap.get(stop);
    }
}
