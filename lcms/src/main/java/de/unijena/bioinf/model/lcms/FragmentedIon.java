package de.unijena.bioinf.model.lcms;

import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.lcms.peakshape.PeakShape;
import de.unijena.bioinf.lcms.quality.Quality;
import de.unijena.bionf.spectral_alignment.CosineQuerySpectrum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FragmentedIon extends IonGroup {

    protected final CosineQuerySpectrum msms;
    protected final Scan ms2Scan;
    protected final List<CorrelatedIon> adducts, inSourceFragments;
    protected PrecursorIonType detectedIonType;
    protected Set<PrecursorIonType> alternativeIonTypes;
    protected PeakShape peakShape;
    protected int alignments=0; // internal counter
    protected Quality ms2Quality;
    protected Polarity polarity;

    protected List<ChromatographicPeak> chimerics;
    private double chimericPollution;

    public FragmentedIon(Polarity polarity, Scan ms2Scan, CosineQuerySpectrum msms, Quality ms2Quality, ChromatographicPeak chromatographicPeak,ChromatographicPeak.Segment segment) {
        super(chromatographicPeak, segment, new ArrayList<>());
        this.polarity = polarity;
        this.msms = msms;
        this.ms2Scan = ms2Scan;
        this.adducts = new ArrayList<>();
        this.inSourceFragments = new ArrayList<>();
        this.ms2Quality = ms2Quality;
        this.alternativeIonTypes = Collections.emptySet();
        this.chimerics = new ArrayList<>();
    }

    public void setMs2Quality(Quality ms2Quality) {
        this.ms2Quality = ms2Quality;
    }

    public int getPolarity() {
        return polarity.charge;
    }

    public synchronized void incrementAlignments() {
        ++alignments;
    }
    public int alignmentCount() {
        return alignments;
    }

/*
    public double comparePeakWidthSmallToLarge(FragmentedIon other) {

        if (getIntensity() > other.getIntensity())
            return 1d/other.comparePeakWidthSmallToLarge(this);

        final double fwhm = segment.fwhm();
        final double otherFwhm = other.segment.fwhm(1d - 0.5d*getIntensity()/other.getIntensity());
        return fwhm / otherFwhm;
    }
*/

    public double comparePeakWidthSmallToLarge(FragmentedIon other) {

        if (getIntensity() > other.getIntensity()) {
            return other.getSegment().fwhm(0.5d) / getSegment().fwhm(0.5d);
        } else {
            return getSegment().fwhm(0.5d) / other.getSegment().fwhm(0.5d);
        }
    }

    public void setPeakShape(PeakShape peakShape) {
        this.peakShape = peakShape;
    }

    public PeakShape getPeakShape() {
        return peakShape;
    }

    public PrecursorIonType getDetectedIonType() {
        return detectedIonType;
    }

    public void setDetectedIonType(PrecursorIonType detectedIonType) {
        this.detectedIonType = detectedIonType;
    }

    public void setPossibleAdductTypes(Set<PrecursorIonType> possibleAdductTypes) {
        this.alternativeIonTypes = possibleAdductTypes;
    }

    public Set<PrecursorIonType> getPossibleAdductTypes() {
        return alternativeIonTypes;
    }

    public List<CorrelatedIon> getAdducts() {
        return adducts;
    }

    public List<CorrelatedIon> getInSourceFragments() {
        return inSourceFragments;
    }

    public double getMass() {
        return peak.getMzAt(segment.apex);
    }


    public long getRetentionTime() {
        return peak.getRetentionTimeAt(segment.apex);
    }

    public String toString() {
        return "MS/MS("+chargeState+") m/z = " + (msms==null ? "GAP FILLED" : ms2Scan.getPrecursor().getMass()) + ", apex = " + peak.getRetentionTimeAt(segment.getApexIndex())/60000d + " min";
    }

    public double getIntensity() {
        return peak.getIntensityAt(segment.apex);
    }

    public Quality getMsMsQuality() {
        return ms2Quality;
    }

    public CosineQuerySpectrum getMsMs() {
        return msms;
    }

    public Scan getMsMsScan() {
        return ms2Scan;
    }

    public void setChimerics(List<ChromatographicPeak> chimerics) {
        this.chimerics = chimerics;
    }

    public List<ChromatographicPeak> getChimerics() {
        return this.chimerics;
    }

    public void setChimericPollution(double chimericPollution) {
        this.chimericPollution = chimericPollution;
    }

    public double getChimericPollution() {
        return chimericPollution;
    }
}
