package de.unijena.bioinf.sirius.projectspace;

import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.sirius.IdentificationResult;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.List;

public class ExperimentResult {
    public static enum ErrorCause {TIMEOUT, NORESULTS, ERROR, NOERROR};

    protected String experimentName, experimentSource;
    protected Ms2Experiment experiment;
    protected List<IdentificationResult> results;
    protected ErrorCause error;
    protected String errorMessage;

    public ExperimentResult(Ms2Experiment experiment, List<IdentificationResult> results, String source, String name) {
        this.experiment = experiment;
        this.results = results;
        this.experimentName = name;
        this.experimentSource = source;
        this.error = ErrorCause.NOERROR;
    }

    public ExperimentResult(Ms2Experiment experiment, List<IdentificationResult> results) {
        this.experiment = experiment;
        this.results = results;
        this.experimentName = simplify(experiment.getName());
        this.experimentSource = simplifyURL(experiment.getSource());
        this.error = ErrorCause.NOERROR;
    }

    @Deprecated
    public ExperimentResult(Ms2Experiment experiment, List<IdentificationResult> results, String errorString) {
        this(experiment,results);
        this.error = ErrorCause.valueOf(errorString);
        if (error==null) error = ErrorCause.ERROR;
    }

    public ExperimentResult(Ms2Experiment experiment, List<IdentificationResult> results, ErrorCause error) {
        this(experiment,results);
        this.error = error;
    }

    public ExperimentResult(Ms2Experiment experiment, List<IdentificationResult> results, ErrorCause error, String errorMessage) {
        this(experiment,results);
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public boolean hasError(){
        return !error.equals(ErrorCause.NOERROR);
    }

    public String getErrorString() {
        return error.toString();
    }

    public ErrorCause getError() {
        return error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public String getExperimentSource() {
        return experimentSource;
    }

    public Ms2Experiment getExperiment() {
        return experiment;
    }

    public List<IdentificationResult> getResults() {
        return results;
    }

    private static String simplify(@Nullable String name) {
        if (name == null) return null;
        if (name.length()>64)
            name = name.substring(0,48);
        return name.replaceAll("[^A-Za-z0-9,\\-]+", "");
    }

    private static String simplifyURL(@Nullable URL url) {
        if (url == null) return null;
        return simplifyURL(url.getFile());
    }

    private static String simplifyURL(@Nullable String filename) {
        if (filename == null) return null;
        filename = new File(filename).getName();
        int i = Math.min(48,filename.lastIndexOf('.'));
        return filename.substring(0,i);
    }
}
