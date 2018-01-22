package de.unijena.bioinf.ChemistryBase.ms;

import de.unijena.bioinf.ChemistryBase.chem.Ionization;
import de.unijena.bioinf.ChemistryBase.chem.PeriodicTable;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;

import java.util.*;

/**
 * Can be attached to a Ms2Experiment or ProcessedInput. If PrecursorIonType is unknown, SIRIUS will use this
 * object and compute trees for all ion types with probability > 0.
 * If probability is unknown, you can assign a constant to each ion type.
 */
public class PossibleIonModes {

    public static PossibleIonModes deterministic(PrecursorIonType precursorIonType) {
        final PossibleIonModes a = new PossibleIonModes();
        a.add(precursorIonType, 1);
        a.disableGuessFromMs1();
        return a;
    }

    public static PossibleIonModes defaultFor(int charge) {
        final PossibleIonModes a = new PossibleIonModes();
        final PeriodicTable t = PeriodicTable.getInstance();
        if (charge > 0) {
            a.add(t.ionByName("[M+H]+").getIonization(), 0.95);
            a.add(t.ionByName("[M+Na]+").getIonization(), 0.03);
            a.add(t.ionByName("[M+K]+").getIonization(), 0.02);
        } else {
            a.add(t.ionByName("[M-H]-").getIonization(), 0.95);
            a.add(t.ionByName("[M+Cl]-").getIonization(), 0.03);
            a.add(t.ionByName("[M+Br]-").getIonization(), 0.02);
        }
        a.enableGuessFromMs1();
        return a;
    }

    public static class ProbabilisticIonization {
        public final Ionization ionMode;
        public final double probability;

        private ProbabilisticIonization(Ionization ionMode, double probability) {
            this.ionMode = ionMode;
            this.probability = probability;
        }

        @Override
        public String toString() {
            return ionMode.toString() + "=" + probability;
        }
    }

    protected List<ProbabilisticIonization> ionTypes;
    protected double totalProb;
    protected boolean enableGuessFromMs1;

    public PossibleIonModes(PossibleIonModes pi) {
        this.ionTypes = new ArrayList<>();
        for (ProbabilisticIonization i : pi.ionTypes)
            this.ionTypes.add(new ProbabilisticIonization(i.ionMode, i.probability));
        this.totalProb = pi.totalProb;
        this.enableGuessFromMs1 = pi.enableGuessFromMs1;
    }

    public PossibleIonModes() {
        this.ionTypes = new ArrayList<>();
        this.enableGuessFromMs1 = true;
    }

    public boolean isGuessFromMs1Enabled() {
        return enableGuessFromMs1;
    }

    public void enableGuessFromMs1(boolean enabled) {
        this.enableGuessFromMs1 = enabled;
    }
    public void enableGuessFromMs1(){
        enableGuessFromMs1(true);
    }
    public void disableGuessFromMs1() {
        enableGuessFromMs1(false);
    }

    public void add(ProbabilisticIonization ionMode) {
        ionTypes.add(ionMode);
        totalProb += ionMode.probability;
    }

    public void add(PrecursorIonType ionType, double probability) {
        ionTypes.add(new ProbabilisticIonization(ionType.getIonization(), probability));
        totalProb += probability;
    }

    public void add(String ionType, double probability) {
        add(PrecursorIonType.getPrecursorIonType(ionType), probability);
    }

    public void add(Ionization ionType, double probability) {
        add(PrecursorIonType.getPrecursorIonType(ionType), probability);
    }

    public void add(Ionization ionType) {
        double minProb = Double.POSITIVE_INFINITY;
        for (ProbabilisticIonization pi : ionTypes)
            if (pi.probability > 0)
                minProb = Math.min(pi.probability, minProb);
        if (Double.isInfinite(minProb)) minProb = 1d;
        add(PrecursorIonType.getPrecursorIonType(ionType), minProb);
    }

    public List<ProbabilisticIonization> probabilisticIonizations() {
        return ionTypes;
    }

    public boolean hasPositiveCharge() {
        for (ProbabilisticIonization a : ionTypes)
            if (a.ionMode.getCharge() > 0)
                return true;
        return false;
    }

    public boolean hasNegativeCharge() {
        for (ProbabilisticIonization a : ionTypes)
            if (a.ionMode.getCharge() < 0)
                return true;
        return false;
    }

    public double getProbabilityFor(PrecursorIonType ionType) {
        if (ionTypes.isEmpty()) return 0d;
        for (ProbabilisticIonization a : ionTypes) {
            if (a.ionMode.equals(ionType.getIonization())) return a.probability / totalProb;
        }
        return 0d;
    }

    public double getProbabilityFor(Ionization ionType) {
        if (ionTypes.isEmpty()) return 0d;
        double prob = 0d;
        for (ProbabilisticIonization a : ionTypes) {
            if (a.ionMode.equals(ionType)) prob += a.probability;
        }
        return prob / totalProb;
    }

    public List<Ionization> getIonModes() {
        final Set<Ionization> ions = new HashSet<>(ionTypes.size());
        for (ProbabilisticIonization a : ionTypes) ions.add(a.ionMode);
        return new ArrayList<>(ions);
    }

    public List<PrecursorIonType> getIonModesAsPrecursorIonType() {
        final Set<PrecursorIonType> ions = new HashSet<>(ionTypes.size());
        for (ProbabilisticIonization a : ionTypes) ions.add(PrecursorIonType.getPrecursorIonType(a.ionMode));
        return new ArrayList<>(ions);
    }


    public static PossibleIonModes reduceTo(PossibleIonModes source, Collection<String> toKeep) {
        if (toKeep instanceof Set)
            return reduceTo(source, (Set<String>) toKeep);
        else
            return reduceTo(source, (new HashSet<>(toKeep)));

    }

    public static PossibleIonModes reduceTo(PossibleIonModes source, Set<String> toKeep) {
        PossibleIonModes nu = new PossibleIonModes();
        for (ProbabilisticIonization n : source.ionTypes) {
            if (toKeep.contains(n.ionMode.toString()))
                nu.add(n);
        }
        return nu;
    }

    @Override
    public String toString() {
        return ionTypes.toString();
    }
}
