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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import org.intermine.xml.full.Reference;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ReferenceList;

/**
 * DataConverter to parse pombe disease file into Items.
 * 
 * @author Daniela Butano
 */
public class PombeDiseasesConverter extends BioFileConverter
{
    private String datasource, dataset, licence;
    private String datasetRefId = null;
    private static final String DATASET_TITLE = "PomBase disease data set";
    private static final String DATA_SOURCE_NAME = "PomBase";
    private static final String LICENCE = "http://creativecommons.org/licenses/by/4.0/";
    private Map<Disease, Integer> diseases = new LinkedHashMap<>();
    private Map<String, String> mondoTerms = new LinkedHashMap<>();
    private Map<String, String> publications = new LinkedHashMap<>();
    private Map<String, String> genes = new LinkedHashMap<>();
    private Map<Integer, List<String>> diseaseGenesMap =  new LinkedHashMap<Integer, List<String>>();
    private Map<Integer, List<String>> diseasePublicationsMap =  new LinkedHashMap<Integer, List<String>>();



    /**
     * Constructor
     *
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     * @throws Exception if an error occurs in storing or finding Model
     */
    public PombeDiseasesConverter(ItemWriter writer, Model model) throws Exception {
        super(writer, model);
    }

    /**
     * Set the licence, a URL to the licence for this ontology
     *
     * @param licence licence for these data. Expects a URL
     */
    public void setLicence(String licence) {
        this.licence = licence;
    }

    /**
     * Datasource for any bioentities created
     * @param dataSourceName name of datasource for items created
     */
    public void setDataSourceName(String dataSourceName) {
        this.datasource = dataSourceName;
    }

    /**
     * If a value is specified this title will used when a DataSet is created.
     * @param dataSetTitle the title of the DataSets of any new features
     */
    public void setDataSetTitle(String dataSetTitle) {
        this.dataset = dataSetTitle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Reader reader) throws ObjectStoreException, IOException {
        setDefaultDataset();

        BufferedReader br = new BufferedReader(reader);
        String line;
        // loop through entire file
        while ((line = br.readLine()) != null) {
            if (isHeader(line)) {
                continue;
            }
            String[] array = line.split("\t", -1); // keep trailing empty Strings
            String geneIdentifier = array[0];
            String geneSymbol = array[1];
            String mondoTermId = array[2];
            String pubMedId = array[3];

            if (geneIdentifier != null) {
                Disease disease = new Disease(mondoTermId, pubMedId);
                storeDisease(disease);
                storeGene(geneIdentifier, geneSymbol, disease);

            }
        }
        storeDiseaseGenes();
    }

    private void setDefaultDataset() throws ObjectStoreException {
        if (dataset == null) {
            dataset = DATASET_TITLE;
        }
        if (datasource == null) {
            datasource = DATA_SOURCE_NAME;
        }
        if (licence == null) {
            licence = LICENCE;
        }
        String datasourceRefId = getDataSource(datasource);
        datasetRefId = getDataSet(dataset, datasourceRefId, licence);
    }

    private boolean isHeader(String line) {
        if (line.startsWith("#")) {
            return true;
        }
        return false;
    }

    private void storeMondoTerm(String identifier) throws ObjectStoreException {
        if (StringUtils.isEmpty(identifier)) {
            return;
        }
        String mondoTermRefId = mondoTerms.get(identifier);
        if (mondoTermRefId == null) {
            Item item = createItem("MondoTerm");
            item.setAttribute("identifier", identifier);
            item.addToCollection("dataSets", datasetRefId);
            store(item);
            mondoTermRefId = item.getIdentifier();
            mondoTerms.put(identifier, mondoTermRefId);
        }
    }

    private void storePublication(String pubMedId) throws ObjectStoreException {
        if (StringUtils.isEmpty(pubMedId) || !pubMedId.contains("PMID:")) {
            return;
        }
        String publicationRefId = publications.get(pubMedId);
        if (publicationRefId == null) {
            Item item = createItem("Publication");
            item.setAttribute("pubMedId", pubMedId.substring(5));
            store(item);
            publicationRefId = item.getIdentifier();
            publications.put(pubMedId, publicationRefId);
        }
    }

    private void storeDisease(Disease disease) throws ObjectStoreException {
        storeMondoTerm(disease.mondoTermId);
        storePublication(disease.pubMedId);
        if (!diseases.containsKey(disease)) {
            Item item = createItem("Disease");
            String mondoTermRefId = mondoTerms.get(disease.mondoTermId);
            item.setReference("mondoTerm", mondoTermRefId);
            item.addToCollection("dataSets", datasetRefId);

            String pubRefId = publications.get(disease.pubMedId);
            if (pubRefId != null) {
                List<String> publication = new ArrayList<String>();
                publication.add(pubRefId);
                item.setCollection("publications", publication);
            }

            Integer id = store(item);
            diseases.put(disease, id);
        }
    }

    private void storeGene(String primaryIdentifier, String symbol, Disease disease)
            throws ObjectStoreException {
        String geneRefId = genes.get(primaryIdentifier);
        if (geneRefId == null) {
            Item gene = createItem("Gene");
            gene.setAttribute("primaryIdentifier", primaryIdentifier);
            gene.setAttributeIfNotNull("symbol", symbol);
            gene.addToCollection("dataSets", datasetRefId);
            store(gene);
            geneRefId = gene.getIdentifier();
            genes.put(primaryIdentifier, geneRefId);
        }
        Integer diseaseId = diseases.get(disease);
        if (!diseaseGenesMap.containsKey(diseaseId)) {
            List<String> genes = new ArrayList<>();
            genes.add(geneRefId);
            diseaseGenesMap.put(diseaseId, genes);
        } else {
            diseaseGenesMap.get(diseaseId).add(geneRefId);
        }
    }

    private void storeDiseaseGenes() throws ObjectStoreException {
        for (Map.Entry<Integer, List<String>> entry : diseaseGenesMap.entrySet()) {
            Integer diseaseId = entry.getKey();
            List<String> genesRefIds = entry.getValue();
            ReferenceList genes = new ReferenceList("genes", genesRefIds);
            store(genes, diseaseId);
        }
    }

    private void storeDiseasePublications() throws ObjectStoreException {
        for (Map.Entry<Integer, List<String>> entry : diseasePublicationsMap.entrySet()) {
            Integer diseaseId = entry.getKey();
            List<String> publicationsRefIds = entry.getValue();
            ReferenceList publications = new ReferenceList("publications", publicationsRefIds);
            store(publications, diseaseId);
        }
    }

    class Disease {
        String mondoTermId;
        String pubMedId;

        public Disease(String mondoTermId, String pubMedId) {
            this.mondoTermId = mondoTermId;
            this.pubMedId = pubMedId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Disease disease = (Disease) o;
            return Objects.equals(mondoTermId, disease.mondoTermId) &&
                    Objects.equals(pubMedId, disease.pubMedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mondoTermId, pubMedId);
        }
    }
}
