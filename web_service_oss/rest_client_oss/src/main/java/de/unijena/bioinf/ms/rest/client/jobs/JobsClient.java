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

package de.unijena.bioinf.ms.rest.client.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unijena.bioinf.ChemistryBase.utils.IOFunctions;
import de.unijena.bioinf.ms.rest.client.AbstractClient;
import de.unijena.bioinf.ms.rest.model.JobId;
import de.unijena.bioinf.ms.rest.model.JobTable;
import de.unijena.bioinf.ms.rest.model.JobUpdate;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class JobsClient extends AbstractClient {
    private static final Logger LOG = LoggerFactory.getLogger(JobsClient.class);

    public JobsClient(@NotNull URI serverUrl, @NotNull IOFunctions.IOConsumer<HttpUriRequest> requestDecorator) {
        super(serverUrl, requestDecorator);
    }


    public EnumMap<JobTable, List<JobUpdate<?>>> getJobs(Collection<JobTable> jobTablesToCheck, @NotNull CloseableHttpClient client) throws IOException {
        return executeFromJson(client,
                () -> new HttpGet(buildVersionSpecificWebapiURI("/jobs/" + CID)
                        .setParameter("types", jobTablesToCheck.stream().map(JobTable::name).collect(Collectors.joining(",")))
                        .build()),
                new TypeReference<>() {}
        );
    }

    /**
     * Unregisters Client and deletes all its jobs on server
     */
    public void deleteAllJobs(@NotNull CloseableHttpClient client) throws IOException {
        execute(client, () -> new HttpPatch(buildVersionSpecificWebapiURI("/jobs/" + CID + "/delete").build()));
    }


    public void deleteJobs(Collection<JobId> jobsToDelete, Map<JobId, Integer> countingHashes, @NotNull CloseableHttpClient client) throws IOException {
        execute(client, () -> {
            Map<String, String> body = new HashMap<>();
            body.put("jobs", new ObjectMapper().writeValueAsString(jobsToDelete));
            if (countingHashes != null && !countingHashes.isEmpty()) //add client sided counting if available
                body.put("countingHashes", new ObjectMapper().writeValueAsString(countingHashes));
            HttpPatch patch = new HttpPatch(buildVersionSpecificWebapiURI("/jobs/" + CID + "/delete").build());
            patch.setEntity(new StringEntity(new ObjectMapper().writeValueAsString(body)));
            return patch;
        });
    }

    public int getCountedJobs(@NotNull Date monthAndYear, boolean byMonth, @NotNull CloseableHttpClient client) throws IOException {
        return getCountedJobs(monthAndYear, null, byMonth, client);
    }

    public int getCountedJobs(@NotNull Date monthAndYear, @Nullable JobTable jobType, boolean byMonth, @NotNull CloseableHttpClient client) throws IOException {
        return executeFromJson(client,
                () -> {
                    URIBuilder builder = buildVersionSpecificWebapiURI("/jobs/count")
                            .setParameter("date", Long.toString(monthAndYear.getTime()))
                            .setParameter("byMonth", Boolean.toString(byMonth));
                    if (jobType != null)
                        builder.setParameter("jobType", new ObjectMapper().writeValueAsString(jobType));

                    return new HttpGet(builder.build());
                },
                new TypeReference<>() {}
        );
    }
}