package de.unijena.bioinf.GibbsSampling.properties;

import de.unijena.bioinf.ms.annotations.Ms2ExperimentAnnotation;
import de.unijena.bioinf.ms.properties.DefaultProperty;

public class ZodiacEdgeFilterThresholds implements Ms2ExperimentAnnotation {

    /**
     * Defines the proportion of edges of the complete network which will be ignored.
     */
    @DefaultProperty public final double thresholdFilter;

    /**
     * Minimum number of candidates per compound which are forced to have at least [minLocalConnections] connections to other compounds.
     * E.g. 2 candidates per compound must have at least 10 connections to other compounds
     */
    @DefaultProperty public final int minLocalCandidates;

    /**
     * Minimum number of connections per candidate which are forced for at least [minLocalCandidates] candidates to other compounds.
     * E.g. 2 candidates per compound must have at least 10 connections to other compounds
     */
    @DefaultProperty public final int minLocalConnections;

    private ZodiacEdgeFilterThresholds() {
//        thresholdFilter = 0.95;
//        minLocalCandidates = 1;
//        minLocalConnections = 10;
        thresholdFilter = Double.NaN;
        minLocalCandidates = -1;
        minLocalConnections = -1;
    }

    public ZodiacEdgeFilterThresholds(double thresholdFilter, int minLocalCandidates, int minLocalConnections) {
        this.thresholdFilter = thresholdFilter;
        this.minLocalCandidates = minLocalCandidates;
        this.minLocalConnections = minLocalConnections;
    }
}
