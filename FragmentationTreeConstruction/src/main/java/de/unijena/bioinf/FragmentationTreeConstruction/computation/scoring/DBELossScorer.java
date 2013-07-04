package de.unijena.bioinf.FragmentationTreeConstruction.computation.scoring;

import de.unijena.bioinf.ChemistryBase.algorithm.Called;
import de.unijena.bioinf.ChemistryBase.algorithm.ParameterHelper;
import de.unijena.bioinf.ChemistryBase.data.DataDocument;
import de.unijena.bioinf.FragmentationTreeConstruction.model.Loss;
import de.unijena.bioinf.FragmentationTreeConstruction.model.ProcessedInput;

@Called("Loss RDBE")
public class DBELossScorer implements LossScorer {

    private double score;

    public DBELossScorer() {
        this(Math.log(0.25));
    }

    public DBELossScorer(double score) {
        this.score = score;
    }

    @Override
    public Object prepare(ProcessedInput input) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public double score(Loss loss, ProcessedInput input, Object precomputed) {
        final int rdb = loss.getLoss().doubledRDBE();
        if (rdb < 0) return score;
        else return 0;
    }

    @Override
    public <G, D, L> void importParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        score = document.getDoubleFromDictionary(dictionary, "score");
    }

    @Override
    public <G, D, L> void exportParameters(ParameterHelper helper, DataDocument<G, D, L> document, D dictionary) {
        document.addToDictionary(dictionary, "score", score);
    }
}
