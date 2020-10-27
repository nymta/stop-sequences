package com.nyct.stopsequences;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Streams;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class Main {
    private final Predicate<StopTime> REVENUE_STOPTIME = st -> st.getPickupType() == 0 || st.getDropOffType() == 0;
    private final Map<String, String> DIRECTIONS = ImmutableMap.of("0", "N", "1", "S");

    GtfsMutableRelationalDao dao;

    public void run() throws IOException {
        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(new File("/Users/kurt/Downloads/google_transit_brooklyn.zip"));

        dao = new GtfsRelationalDaoImpl();
        reader.setEntityStore(dao);

        reader.run();

        final List<MasterStopListEntry> masterStopListEntries = dao.getAllRoutes()
                .stream()
                .flatMap(
                        route -> DIRECTIONS.keySet().stream()
                                .flatMap(directionId -> stopSequencesForRouteAndDirection(route, directionId))
                )
                .collect(toImmutableList());

        final CsvMapper mapper = new CsvMapper();
        mapper.registerModule(new ParameterNamesModule());
        final CsvSchema schema = mapper.schemaFor(MasterStopListEntry.class).withHeader();
        final ObjectWriter writer = new CsvMapper().writerFor(MasterStopListEntry.class).with(schema);

        final SequenceWriter sequenceWriter = writer.writeValues(new File("master_stop_list.csv"));

        sequenceWriter.writeAll(masterStopListEntries);

        sequenceWriter.close();
    }

    public static void main(String[] args) throws IOException {
        new Main().run();
    }

    private Stream<Trip> tripsForRouteAndDirection(Route route, String directionId) {
        return dao.getTripsForRoute(route).stream()
                .filter(t -> t.getDirectionId().equals(directionId));
    }

    private Stream<MasterStopListEntry> stopSequencesForRouteAndDirection(Route route, String directionId) {
        DirectedAcyclicGraph<Stop, DefaultEdge> stopsDag = new DirectedAcyclicGraph<>(DefaultEdge.class);

        tripsForRouteAndDirection(route, directionId)
                .flatMap(t -> dao.getStopTimesForTrip(t).stream())
                .filter(REVENUE_STOPTIME)
                .map(StopTime::getStop)
                .distinct()
                .forEach(stopsDag::addVertex);

        tripsForRouteAndDirection(route, directionId)
                .forEach(t -> {
                    PeekingIterator<StopTime> it = Iterators.peekingIterator(dao.getStopTimesForTrip(t)
                            .stream()
                            .filter(REVENUE_STOPTIME)
                            .iterator());

                    while (it.hasNext()) {
                        StopTime now = it.next();
                        if (it.hasNext()) {
                            StopTime next = it.peek();
                            if (!stopsDag.containsEdge(next.getStop(), now.getStop())) {
                                stopsDag.addEdge(now.getStop(), next.getStop());
                            }
                        }
                    }

                });

        TopologicalOrderIterator<Stop, DefaultEdge> stopIterator = new TopologicalOrderIterator<>(stopsDag,
                new FerrisHeuristicStopComparator(stopsDag));

        return Streams.mapWithIndex(Streams.stream(stopIterator),
                (stop, index) -> new MasterStopListEntry(route.getShortName(),
                        DIRECTIONS.get(directionId),
                        (int) index + 1,
                        stop.getId().getId(),
                        "N",
                        0,
                        0,
                        0));
    }
}

