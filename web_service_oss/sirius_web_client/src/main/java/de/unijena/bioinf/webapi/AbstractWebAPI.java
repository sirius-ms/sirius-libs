/*
 *
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer and Sebastian Böcker,
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
 *  You should have received a copy of the GNU Lesser General Public License along with SIRIUS. If not, see <https://www.gnu.org/licenses/lgpl-3.0.txt>
 */

package de.unijena.bioinf.webapi;

import de.unijena.bioinf.chemdb.AbstractChemicalDatabase;
import de.unijena.bioinf.fingerid.CSIPredictor;
import de.unijena.bioinf.fingerid.StructurePredictor;
import de.unijena.bioinf.fingerid.predictor_types.PredictorType;
import de.unijena.bioinf.ms.rest.model.canopus.CanopusData;
import de.unijena.bioinf.ms.rest.model.fingerid.FingerIdData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.EnumMap;

public abstract class AbstractWebAPI<D extends AbstractChemicalDatabase> implements WebAPI<D> {


    //caches predicors so that we do not have to download the statistics and fingerprint info every time
    private final EnumMap<PredictorType, StructurePredictor> fingerIdPredictors = new EnumMap<>(PredictorType.class);

    public @NotNull StructurePredictor getStructurePredictor(@NotNull PredictorType type) throws IOException {
        synchronized (fingerIdPredictors) {
            if (!fingerIdPredictors.containsKey(type)) {
                final CSIPredictor p = new CSIPredictor(type, this);
                p.initialize();
                fingerIdPredictors.put(type, p);
            }
        }
        return fingerIdPredictors.get(type);
    }


    private final EnumMap<PredictorType, FingerIdData> fingerIdData = new EnumMap<>(PredictorType.class);

    public FingerIdData getFingerIdData(@NotNull PredictorType predictorType) throws IOException {
        synchronized (fingerIdData) {
            if (!fingerIdData.containsKey(predictorType))
                fingerIdData.put(predictorType, getFingerIdDataUncached(predictorType));
        }
        return fingerIdData.get(predictorType);
    }
    protected abstract FingerIdData getFingerIdDataUncached(@NotNull PredictorType predictorType) throws IOException;



    private final EnumMap<PredictorType, CanopusData> canopusData = new EnumMap<>(PredictorType.class);

    public final CanopusData getCanopusdData(@NotNull PredictorType predictorType) throws IOException {
        synchronized (canopusData) {
            if (!canopusData.containsKey(predictorType))
                canopusData.put(predictorType, getCanopusDataUncached(predictorType));
        }
        return canopusData.get(predictorType);
    }

    protected abstract CanopusData getCanopusDataUncached(@NotNull PredictorType predictorType) throws IOException;

}
