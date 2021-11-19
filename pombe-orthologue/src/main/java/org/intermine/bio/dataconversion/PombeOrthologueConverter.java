package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2021 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */


import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;

import java.io.File;
import java.io.Reader;
import java.util.*;
import java.io.BufferedReader;


/**
 * DataConverter to parse pombe orthologues
 * @author Daniela Butano
 */
public class PombeOrthologueConverter extends BioFileConverter
{
    private static final Logger LOG = Logger.getLogger(PombeOrthologueConverter.class);
    private static final String DATA_SOURCE_NAME = "PomBase";
    private static final String POMBE_TAXON_ID = "4896";
    private static final String ORTHOLOGUE_TYPE = "orthologue";
    private String dataset;
    private String datasetRefId;
    private Map<String, String> organismRefIds = new HashMap<>();

    /**
     * Constructor
     *
     * @param writer the ItemWriter used to handle the resultant items
     * @param model  the Model
     */
    public PombeOrthologueConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, null);
    }

    /**
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        File file = getCurrentFile();
        if (file == null) {
            return;
        }
        String fileName = file.getName();
        String homologueTaxonId;
        if (fileName.contains("cerevisiae")) {
            homologueTaxonId = "4932";
            datasetRefId = setDefaultDataset("cerevisiae-orthologs data set");
        } else if (fileName.contains("human")) {
            homologueTaxonId = "9606";
            datasetRefId = setDefaultDataset("human-orthologs data set");
        } else {
            return;
        }

        String organismRefId = storeOrganism(POMBE_TAXON_ID);
        String homoloueOrgRefId = storeOrganism(homologueTaxonId);

        BufferedReader br = new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            if (isHeader(line)) {
                continue;
            }
            String[] array = line.split("\t", -1);
            String geneIdentifier = array[0];
            if (array.length == 1) {
                continue;
            }
            String homologuesIds = array[1];
            if ("NONE".equals(homologuesIds)) {
                continue;
            }
            String[] homologues = StringUtils.split(homologuesIds, "|");
            for (int index = 0; index < homologues.length; index++) {
                storeHomologue(geneIdentifier, organismRefId, homologues[index], homoloueOrgRefId);
            }
        }
    }

    private String setDefaultDataset(String datasetName) throws ObjectStoreException {
        dataset = datasetName;
        String datasourceRefId = getDataSource(DATA_SOURCE_NAME);
        return getDataSet(dataset, datasourceRefId, null);
    }

    private String storeOrganism(String taxonId) {
        if (organismRefIds.containsKey(taxonId)) {
            return organismRefIds.get(taxonId);
        } else {
            Item organism = createItem("Organism");
            organism.setAttributeIfNotNull("taxonId", taxonId);
            try {
                store(organism);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing organism ", ex);
            }
            String organismRefId = organism.getIdentifier();
            organismRefIds.put(taxonId, organismRefId);
            return organismRefId;
        }
    }

    private boolean isHeader(String line) {
        if (line.startsWith("#")) {
            return true;
        }
        return false;
    }

    private void storeHomologue(String primaryIdentifier, String organismRefId,
                    String homologuePrimaryIdentifier, String homologueOrganismRefId) {
        Item homologue = createItem("Homologue");
        homologue.setAttribute("type", ORTHOLOGUE_TYPE);
        homologue.setReference("gene", storeGene(primaryIdentifier, organismRefId));
        homologue.setReference("homologue", storeGene(homologuePrimaryIdentifier, homologueOrganismRefId));
        homologue.addToCollection("dataSets", datasetRefId);
        try {
            store(homologue);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error storing homologue ", ex);
        }
    }

    private String storeGene(String primaryIdentifier, String organismRefId) {
        Item gene = createItem("Gene");
        gene.setAttributeIfNotNull("primaryIdentifier", primaryIdentifier);
        gene.setReference("organism", organismRefId);
        gene.addToCollection("dataSets", datasetRefId);
        try {
            store(gene);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error storing gene ", ex);
        }
        return gene.getIdentifier();
    }
}
