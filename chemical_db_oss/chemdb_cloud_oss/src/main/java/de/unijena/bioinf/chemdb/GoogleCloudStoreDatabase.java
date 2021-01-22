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

package de.unijena.bioinf.chemdb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.StorageOptions;
import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.fp.CdkFingerprintVersion;
import de.unijena.bioinf.ChemistryBase.fp.FingerprintVersion;
import de.unijena.bioinf.fingerid.utils.FingerIDProperties;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/*
    A file based database based on Google cloud storage consists of a directory of files (either .csv or .json), each file contains compounds from the
    same molecular formula. The filenames consists of the molecular formula strings.
 */
public class GoogleCloudStoreDatabase extends AbstractBlobBasedDatabase {

    private final Bucket bucket;
    protected Map<String, String> bucketLabels;

    public GoogleCloudStoreDatabase() throws IOException {
        this(FingerIDProperties.gcsBucketName());
    }

    public GoogleCloudStoreDatabase(String bucketName) throws IOException {
        this(USE_EXTENDED_FINGERPRINTS ? CdkFingerprintVersion.getExtended() : CdkFingerprintVersion.getDefault(), bucketName);
    }

    public GoogleCloudStoreDatabase(FingerprintVersion version, String bucketName) throws IOException {
        this(version, storageOptions().getService().get(bucketName));
    }

    public GoogleCloudStoreDatabase(FingerprintVersion version, Bucket bucket) throws IOException {
        super(version, bucket.getName());
        this.bucket = bucket;
        refresh();
    }

    private static StorageOptions storageOptions() {
        try {
            FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(
                    ServiceAccountCredentials.fromStream(Files.newInputStream(FingerIDProperties.gcsCredentialsPath())));
            return StorageOptions.newBuilder().setCredentials(credentialsProvider.getCredentials()).build();
        } catch (IOException e) {
            throw new RuntimeException("Could not found google cloud credentials json at: " + FingerIDProperties.gcsCredentialsPath());
        }
    }


    @Override
    protected void refresh() throws IOException {

        if (!bucket.exists())
            throw new IOException("Database bucket seems to be not existent or you have not the correct permissions");
        bucketLabels = bucket.getLabels();

        if (bucketLabels.containsKey("chemdb-format"))
            format = "." + bucketLabels.get("chemdb-format").replace('-', '.').toUpperCase(Locale.US);
        long time = System.currentTimeMillis();
        System.out.println("before");
        if (format == null) {
            final Iterable<Blob> blobs = bucket.list().iterateAll();
            for (Blob b : blobs) {
                final String name = b.getName();
                final String upName = name.toUpperCase(Locale.US);

                for (String s : SUPPORTED_FORMATS) {
                    if (upName.endsWith(s)) {
                        if (format == null || format.equals(s)) {
                            format = s;
                            break;
                        } else {
                            throw new IOException("Database contains several formats. Only one format is allowed! Given format is " + format + " but " + name + " found.");
                        }
                    }
                }
//                final String form = name.substring(0, name.length() - format.length());
//                MolecularFormula.parseAndExecute(form, formulas::add);

                if (format != null)
                    break;
            }
        }

        if (format == null) throw new IOException("Couldn't find any compounds in given database");

        format = format.toLowerCase();
        this.reader = format.equals(".json") || format.equals(".json.gz") ? new JSONReader() : new CSVReader();
        this.compressed = format.endsWith(".gz");

//        System.out.println("start parsing after: " + ((double) (System.currentTimeMillis() - time)) / 1000d);

        try (Reader r = getReaderFor("formulas")) {
            final Map<String, String> map = new ObjectMapper().readValue(r, new TypeReference<Map<String, String>>() {});
//            System.out.println("stop parsing after: " + ((double) (System.currentTimeMillis() - time)) / 1000d);

            this.formulas = new MolecularFormula[map.size()];
            final AtomicInteger i = new AtomicInteger(0);
            formulaFlags.clear();
            map.entrySet().stream().parallel().forEach(e -> {
                final MolecularFormula mf = MolecularFormula.parseOrThrow(e.getKey());
                final long flag = Long.parseLong(e.getValue());
                this.formulas[i.getAndIncrement()] = mf;
                synchronized (formulaFlags){
                    this.formulaFlags.put(mf, flag);
                }
            });
//            System.out.println("Filled map after: " + ((double) (System.currentTimeMillis() - time)) / 1000d);
            Arrays.sort(this.formulas);
//            System.out.println("Sorted after: " + ((double)(System.currentTimeMillis() - time))/1000d);
        }
    }

    @Override
    @Nullable
    public Reader getReaderFor(@NotNull MolecularFormula formula) throws IOException {
        return getReaderFor(formula.toString());
    }

    private Reader getReaderFor(@NotNull String name) throws IOException {
        Blob blob = bucket.get(name + format);
        if (blob == null || !blob.exists())
            return null;

        if (compressed) {
            return new InputStreamReader(new GZIPInputStream(Channels.newInputStream(blob.reader())), StandardCharsets.UTF_8);
        } else {
            return Channels.newReader(blob.reader(), StandardCharsets.UTF_8);
        }
    }
}