package com.nyct.stopsequences;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Streams;
import freemarker.template.TemplateException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

@Command(name = "stop-sequences", mixinStandardHelpOptions = true, version = "1.0-SNAPSHOT",
        description = "Generate a stop sequences file from GTFS.")
public class StopSequences implements Callable<Integer> {
    private final Predicate<StopTime> REVENUE_STOPTIME = st -> st.getPickupType() == 0 || st.getDropOffType() == 0;
    private final Map<String, String> DIRECTIONS = ImmutableMap.of("0", "N", "1", "S");

    private GtfsMutableRelationalDao dao;

    @Parameters(index = "0", description = "GTFS Zip file or directory")
    private File gtfsFile;

    @Parameters(index = "1", description = "master stop list CSV")
    private File masterStopListFile;

    @Option(names = {"--debugOutput"}, description = "HTML debug output file")
    private File debugOutputFile;

    @Override
    public Integer call() throws IOException, TemplateException {
        final GtfsReader reader = new GtfsReader();
        reader.setInputLocation(gtfsFile);

        dao = new GtfsRelationalDaoImpl();
        reader.setEntityStore(dao);

        reader.run();

        final List<RouteAndDirection> routes = dao.getAllRoutes()
                .stream()
                .flatMap(route -> DIRECTIONS
                        .keySet()
                        .stream()
                        .map(directionId -> {
                            final DirectedAcyclicGraph<Stop, DefaultEdge> stopsDag = stopsDagForRouteAndDirection(route, directionId);
                            final Map<Stop, Long> stopSequences = stopSequences(stopsDag);
                            final Set<String> tripHeadsigns = tripHeadsignsForRouteAndDirection(route, directionId);

                            return new RouteAndDirection(route, directionId, stopsDag, stopSequences, tripHeadsigns);
                        }))
                .collect(toImmutableList());

        if (debugOutputFile != null) {
            SequenceDebugger.visualizeSequences(routes, debugOutputFile);
        }

        final List<MasterStopListEntry> masterStopListEntries = routes
                .stream()
                .flatMap(r -> {
                    final Route route = r.getRoute();
                    final String directionId = r.getDirectionId();
                    final Map<Stop, Long> stopSequences = r.getStopSequences();

                    return buildMasterStopListEntries(route, directionId, stopSequences);
                })
                .collect(toImmutableList());

        final CsvMapper mapper = new CsvMapper();
        final CsvSchema schema = mapper.schemaFor(MasterStopListEntry.class).withHeader();
        final ObjectWriter writer = new CsvMapper().writerFor(MasterStopListEntry.class).with(schema);

        try (final SequenceWriter sequenceWriter = writer.writeValues(masterStopListFile)) {
            sequenceWriter.writeAll(masterStopListEntries);
        }

        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new StopSequences()).execute(args));
    }

    private Stream<Trip> tripsForRouteAndDirection(Route route, String directionId) {
        return dao.getTripsForRoute(route)
                .stream()
                .filter(t -> t.getDirectionId().equals(directionId));
    }

    private Set<String> tripHeadsignsForRouteAndDirection(Route route, String directionId) {
        return tripsForRouteAndDirection(route, directionId)
                .map(Trip::getTripHeadsign)
                .collect(toImmutableSet());
    }

    private DirectedAcyclicGraph<Stop, DefaultEdge> stopsDagForRouteAndDirection(Route route, String directionId) {
        final DirectedAcyclicGraph<Stop, DefaultEdge> stopsDag = new DirectedAcyclicGraph<>(DefaultEdge.class);

        tripsForRouteAndDirection(route, directionId)
                .flatMap(t -> dao.getStopTimesForTrip(t).stream())
                .filter(REVENUE_STOPTIME)
                .map(StopTime::getStop)
                .distinct()
                .forEach(stopsDag::addVertex);

        tripsForRouteAndDirection(route, directionId)
                .forEach(t -> {
                    final PeekingIterator<StopTime> it = Iterators.peekingIterator(
                            dao.getStopTimesForTrip(t)
                                    .stream()
                                    .filter(REVENUE_STOPTIME)
                                    .iterator()
                    );

                    while (it.hasNext()) {
                        final StopTime now = it.next();
                        if (it.hasNext()) {
                            final StopTime next = it.peek();
                            if (!stopsDag.containsEdge(next.getStop(), now.getStop())) {
                                stopsDag.addEdge(now.getStop(), next.getStop());
                            }
                        }
                    }
                });

        return stopsDag;
    }

    @SuppressWarnings("UnstableApiUsage")
    private Map<Stop, Long> stopSequences(DirectedAcyclicGraph<Stop, DefaultEdge> stopsDag) {
        final TopologicalOrderIterator<Stop, DefaultEdge> stopIterator = new TopologicalOrderIterator<>(
                stopsDag,
                new FerrisHeuristicStopComparator(stopsDag)
        );

        return Streams.mapWithIndex(Streams.stream(stopIterator), ImmutablePair::of)
                .collect(toImmutableMap(Pair::getKey, Pair::getValue));
    }

    private Stream<MasterStopListEntry> buildMasterStopListEntries(Route route,
                                                                   String directionId,
                                                                   Map<Stop, Long> stopSequences) {
        return stopSequences
                .entrySet()
                .stream()
                .map(e -> {
                            final Stop stop = e.getKey();
                            final long sequence = e.getValue();

                            return new MasterStopListEntry(
                                    route.getShortName(),
                                    DIRECTIONS.get(directionId),
                                    sequence + 1,
                                    stop.getId().getId(),
                                    "N",
                                    0,
                                    0,
                                    0
                            );
                        }
                );
    }
}

