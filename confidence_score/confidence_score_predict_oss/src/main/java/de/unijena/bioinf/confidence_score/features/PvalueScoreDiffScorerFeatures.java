/*
 *
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schilller University.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS. If not, see <https://www.gnu.org/licenses/lgpl-3.0.txt>
 */

package de.unijena.bioinf.confidence_score.features;

import de.unijena.bioinf.ChemistryBase.algorithm.ParameterHelper;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.Scored;
import de.unijena.bioinf.ChemistryBase.chem.CompoundWithAbstractFP;
import de.unijena.bioinf.ChemistryBase.data.DataDocument;
import de.unijena.bioinf.ChemistryBase.fp.Fingerprint;
import de.unijena.bioinf.ChemistryBase.fp.PredictionPerformance;
import de.unijena.bioinf.ChemistryBase.fp.ProbabilityFingerprint;
import de.unijena.bioinf.chemdb.FingerprintCandidate;
import de.unijena.bioinf.confidence_score.FeatureCreator;
import de.unijena.bioinf.fingerid.blast.FingerblastScoring;
import de.unijena.bioinf.sirius.IdentificationResult;

/**
 * Created by martin on 16.07.18.
 */
public class PvalueScoreDiffScorerFeatures implements FeatureCreator {

    Scored<FingerprintCandidate>[] rankedCands;
    Scored<FingerprintCandidate>[] rankedCands_filtered;
    Scored<FingerprintCandidate> best_hit_scorer;

    FingerblastScoring scoring;



    /**
     * rescores the best hit of a scorer with a different scorer, than calculates pvalue and compares it to the best hit of the 2nd scorer
     */
    public PvalueScoreDiffScorerFeatures(Scored<FingerprintCandidate>[] rankedCands, Scored<FingerprintCandidate>[] rankedCands_filtered,Scored<FingerprintCandidate> hit, FingerblastScoring scoring){
        this.rankedCands=rankedCands;
        this.best_hit_scorer=hit;
        this.rankedCands_filtered=rankedCands_filtered;

        this.scoring=scoring;


    }



    @Override
    public void prepare(PredictionPerformance[] statistics) {

    }

    @Override
    public int weight_direction() {
        return -1;
    }

    @Override
    public double[] computeFeatures(ProbabilityFingerprint query, IdentificationResult idresult) {
        double[] pvalueScore = new double[1];



        scoring.prepare(query);

        double score = scoring.score(query,best_hit_scorer.getCandidate().getFingerprint());

        Scored<FingerprintCandidate> current = new Scored<FingerprintCandidate>(best_hit_scorer.getCandidate(),score);

        PvalueScoreUtils utils = new PvalueScoreUtils();

        pvalueScore[0] = Math.log(utils.computePvalueScore(rankedCands,rankedCands_filtered,current));

    //    pvalueScore[1] = Math.log(pvalueScore[0]);






        return pvalueScore;
    }

    @Override
    public int getFeatureSize() {
        return 1;
    }

    @Override
    public boolean isCompatible(ProbabilityFingerprint query, CompoundWithAbstractFP<Fingerprint>[] rankedCandidates) {
        return false;
    }

    @Override
    public int getRequiredCandidateSize() {
        return 0;
    }

    @Override
    public String[] getFeatureNames() {

        String[] name = new String[1];
        name[0] = "pvaluescorediffscorer";
        return name;
    }

    @Override
    public <G, D, L> void importParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {

    }

    @Override
    public <G, D, L> void exportParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {

    }
}
