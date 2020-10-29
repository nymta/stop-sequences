package com.nyct.stopsequences;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import freemarker.template.*;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.UtilityClass;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultEdge;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import se.sawano.java.text.AlphanumericComparator;

import java.awt.*;
import java.io.*;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;

@UtilityClass
public class SequenceDebugger {

    private final Version FREEMARKER_VERSION = Configuration.VERSION_2_3_30;

    private Element visualizeGraph(RouteAndDirection data) {
        final JGraphXAdapter<Stop, DefaultEdge> graphAdapter = new JGraphXAdapter<>(data.getStopGraph());
        graphAdapter.getStylesheet().getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, "1");
        graphAdapter.setAutoSizeCells(true);

        graphAdapter.getVertexToCellMap().forEach((stop, cell) -> {
            cell.setValue(String.format("%s: %s (%d)", stop.getId().getId(), stop.getName(), data.getStopSequences().get(stop)));
            graphAdapter.updateCellSize(cell);
        });

        final mxIGraphLayout layout = new mxHierarchicalLayout(graphAdapter);
        layout.execute(graphAdapter.getDefaultParent());

        final Document svgDocument = mxCellRenderer.createSvgDocument(graphAdapter, null, 1, Color.WHITE, null);

        return svgDocument.getDocumentElement();
    }

    public void visualizeSequences(List<RouteAndDirection> data, File outputFile) throws IOException, TemplateException {

        System.setProperty("java.awt.headless", "true");

        final List<RouteEntry> routeEntries = data
                .stream()
                .filter(r -> !r.getStopGraph().vertexSet().isEmpty())
                .sorted(Comparator.comparing((RouteAndDirection r) -> r.getRoute().getShortName(), new AlphanumericComparator())
                        .thenComparing(RouteAndDirection::getDirectionId))
                .map(r -> new RouteEntry(
                        r.getRoute(),
                        r.getDirectionId(),
                        visualizeGraph(r),
                        r.getTripHeadsigns()
                ))
                .collect(toImmutableList());

        final Configuration freemarkerConfiguration = new Configuration(FREEMARKER_VERSION);
        freemarkerConfiguration.setClassForTemplateLoading(SequenceDebugger.class, "");
        freemarkerConfiguration.setDefaultEncoding("UTF-8");
        freemarkerConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarkerConfiguration.setLogTemplateExceptions(false);
        freemarkerConfiguration.setWrapUncheckedExceptions(true);
        freemarkerConfiguration.setFallbackOnNullLoopVariable(false);

        final SimpleHash root = new SimpleHash(new DefaultObjectWrapperBuilder(FREEMARKER_VERSION).build());
        root.put("routes", routeEntries);

        final Template sequenceDebugTemplate = freemarkerConfiguration.getTemplate("sequenceDebug.ftlh");

        try (final FileOutputStream fos = new FileOutputStream(outputFile);
             final Writer out = new OutputStreamWriter(fos)) {
            sequenceDebugTemplate.process(root, out);
        }
    }

    @Value
    public static class RouteEntry {
        Route route;
        String directionId;
        @ToString.Exclude
        Element graphSvg;
        Set<String> headsigns;
    }
}

