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

/*
 *  This file is part of the SIRIUS Software for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer, Marvin Meusel and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schiller University.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License along with SIRIUS.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
 */

package de.unijena.bioinf.projectspace.fingerid;

import de.unijena.bioinf.ChemistryBase.fp.Fingerprint;
import de.unijena.bioinf.projectspace.ComponentSerializer;
import de.unijena.bioinf.projectspace.FormulaResultId;
import de.unijena.bioinf.projectspace.ProjectReader;
import de.unijena.bioinf.projectspace.ProjectWriter;
import de.unijena.bioinf.projectspace.FormulaResult;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class FBCandidateFingerprintSerializerTopK implements ComponentSerializer<FormulaResultId, FormulaResult, FBCandidateFingerprintsTopK> {
    final FBCandidateNumber k;
    final FBCandidateFingerprintSerializer source = new FBCandidateFingerprintSerializer();

    public FBCandidateFingerprintSerializerTopK(FBCandidateNumber k) {
        this.k = k;
    }

    @Override
    public @Nullable FBCandidateFingerprintsTopK read(ProjectReader projectReader, FormulaResultId formulaResultId, FormulaResult formulaResult) throws IOException {
        try {
            formulaResult.setAnnotation(FBCandidateNumber.class, k);
            final List<Fingerprint> fps = source.readFingerprints(projectReader, formulaResultId, formulaResult);
            return fps == null ? null : new FBCandidateFingerprintsTopK(fps);
        } finally {
            formulaResult.removeAnnotation(FBCandidateNumber.class);
        }
    }

    @Override
    public void write(ProjectWriter projectWriter, FormulaResultId formulaResultId, FormulaResult formulaResult, Optional<FBCandidateFingerprintsTopK> optional) throws IOException {
//        LoggerFactory.getLogger(getClass()).warn("Cannot write, this is a read only serializer");
    }

    @Override
    public void delete(ProjectWriter projectWriter, FormulaResultId formulaResultId) throws IOException {
//        LoggerFactory.getLogger(getClass()).warn("Cannot delete, this is a read only serializer");
    }
}
