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
 * DataConverter to parse a pombe phenotype file into Items.
 * 
 * @author Daniela Butano
 */
public class PombeAllelesConverter extends BioFileConverter
{
    private String datasource, dataset, licence;
    private String datasetRefId = null;
    private static final String DATASET_TITLE = "Pombemine phenotypes data set";
    private static final String DATA_SOURCE_NAME = "POMBE";
    private static final Logger LOG = Logger.getLogger(PombeAllelesConverter.class);
    private Map<String, String> genes;
    private Map<String, Integer> storedGenesIds;
    private List<Allele> alleles;
    private Map<String, List<String>> geneAllelesRefIds;
    protected Map<String, String> phenotypeTerms = new LinkedHashMap<String, String>();
    protected Map<String, String> pecoTerms = new LinkedHashMap<String, String>();

    /**
     * Constructor
     *
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     * @throws Exception if an error occurs in storing or finding Model
     */
    public PombeAllelesConverter(ItemWriter writer, Model model) throws Exception {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
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
     * Set the data set for this ontology
     *
     * @param dataset data set for this ontology
     */
    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Reader reader) throws ObjectStoreException, IOException {
        initialiseMapsForFile();
        datasetRefId = setDefaultDataset();

        BufferedReader br = new BufferedReader(reader);
        String line = null;
        int counter = 0;
        // loop through entire file
        while ((line = br.readLine()) != null && counter <5000) {
            if (isHeader(line)) {
                continue;
            }
            String[] array = line.split("\t", -1); // keep trailing empty Strings
            String geneIdentifier = array[1];
            String fypoId = array[2];
            String geneSymbol = array[8];
            String evidence = array[12];
            String condition = array[13];
            String penetrance = array[14];
            String severity = array[15];
            String extension = array[16];
            String pubMedId = array[17];

            if (geneIdentifier != null) {
                // null if no pub found
                String geneRefId = storeGene(geneIdentifier, geneSymbol);
                Allele allele = new Allele(line, geneRefId);
                storeAllele(allele);
                String phenotypeTermIdentifier = createPhenotypeTerm(fypoId);
                String severityTermIdentifier = createPhenotypeTerm(severity);
                List<String> conditionsTermIdentifiers = createPECOTerms(condition);
                storePhenotypeAnnotation(phenotypeTermIdentifier, penetrance,
                        severityTermIdentifier,conditionsTermIdentifiers);
            }
            storeGeneAlleles();
            counter++;
        }
    }

    /**
     * Reset maps that don't need to retain their contents between files.
     */
    private void initialiseMapsForFile() {
        genes = new HashMap<>();
        storedGenesIds = new HashMap<>();
        alleles = new ArrayList<>();
        geneAllelesRefIds = new LinkedHashMap<String, List<String>>();
    }

    private String setDefaultDataset() throws ObjectStoreException {
        if (dataset == null) {
            dataset = DATASET_TITLE;
        }
        String datasourceRefId = getDataSource(datasource);
        return getDataSet(dataset, datasourceRefId, licence);
    }

    private boolean isHeader(String line) {
        if (line.startsWith("#")) {
            return true;
        }
        return false;
    }

    private String storeGene(String primaryIdentifier, String symbol) throws ObjectStoreException {
        if (!genes.containsKey(primaryIdentifier)) {
            Item gene = createItem("Gene");
            gene.setAttribute("primaryIdentifier", primaryIdentifier);
            gene.setAttributeIfNotNull("symbol", symbol);
            Integer id = store(gene);
            genes.put(primaryIdentifier, gene.getIdentifier());
            storedGenesIds.put(gene.getIdentifier(), id);
        }
        return genes.get(primaryIdentifier);
    }

    private Integer storePhenotypeAnnotation(
            String pynotypeTermIdentifier, String penetrance,
            String severityTermIdentifier, List<String> conditionsTermIdentifiers)
            throws ObjectStoreException {
        Item phenotypeAnnotation = createItem("PhenotypeAnnotation");
        if(!StringUtils.isEmpty(pynotypeTermIdentifier)) {
            phenotypeAnnotation.setReference("ontologyTerm", pynotypeTermIdentifier);
        }
        phenotypeAnnotation.setAttributeIfNotNull("penetrance", penetrance);
        if(!StringUtils.isEmpty(severityTermIdentifier)) {
            phenotypeAnnotation.setReference("severity", severityTermIdentifier);
        }
        if(!conditionsTermIdentifiers.isEmpty()) {
            phenotypeAnnotation.setCollection("conditions", conditionsTermIdentifiers);
        }

        /*if ("gene".equals(productType)) {
            addProductCollection(productIdentifier, goAnnotation.getIdentifier());
        }
        if (annotationExtRefId != null) {
            goAnnotation.setReference("annotationExtension", annotationExtRefId);
        }*/

        return store(phenotypeAnnotation);
    }

    private String createPhenotypeTerm(String identifier) throws ObjectStoreException {
        if (StringUtils.isEmpty(identifier)) {
            return null;
        }

        String phenotypeTermIdentifier = phenotypeTerms.get(identifier);
        if (phenotypeTermIdentifier == null) {
            Item item = createItem("PhenotypeTerm");
            item.setAttribute("identifier", identifier);
            item.addToCollection("dataSets", datasetRefId);
            store(item);

            phenotypeTermIdentifier = item.getIdentifier();
            phenotypeTerms.put(identifier, phenotypeTermIdentifier);
        }
        return phenotypeTermIdentifier;
    }

    private List<String> createPECOTerms(String identifiers) throws ObjectStoreException {
        List<String> pecoTermsIdentifiers = new ArrayList<String>();
        if (StringUtils.isEmpty(identifiers)) {
            return pecoTermsIdentifiers;
        }
        String[] identifiersList = identifiers.split(",");
        for (String identifier : identifiersList) {
            String pecoTermIdentifier = pecoTerms.get(identifier);
            if (pecoTermIdentifier == null) {
                Item item = createItem("Condition");
                item.setAttribute("identifier", identifier);
                item.addToCollection("dataSets", datasetRefId);
                store(item);

                pecoTermIdentifier = item.getIdentifier();
                pecoTerms.put(identifier, pecoTermIdentifier);
            }
            pecoTermsIdentifiers.add(pecoTermIdentifier);
        }
        return pecoTermsIdentifiers;
    }

    private void storeAllele(Allele allele) throws ObjectStoreException {
        if (!alleles.contains(allele)) {
            alleles.add(allele);
            Item alleleItem = createItem("Allele");
            alleleItem.setAttributeIfNotNull("symbol", allele.symbol);
            alleleItem.setAttributeIfNotNull("type", allele.type);
            alleleItem.setAttributeIfNotNull("expression", allele.expression);
            Reference gene = new Reference("gene", allele.geneRefId);
            store(gene, store(alleleItem));
            List<String> allelesRefIds;
            if (geneAllelesRefIds.containsKey(allele.geneRefId)) {
                allelesRefIds = geneAllelesRefIds.get(allele.geneRefId);
            } else {
                allelesRefIds = new ArrayList<>();
                geneAllelesRefIds.put(allele.geneRefId, allelesRefIds);
            }
            String alleleRefId = alleleItem.getIdentifier();
            allelesRefIds.add(alleleRefId);
        }
    }

    private void storeGeneAlleles() throws ObjectStoreException {
        for (String geneRefId : genes.values()) {
            List<String> allelesRefIds = geneAllelesRefIds.get(geneRefId);
            if (allelesRefIds != null && !allelesRefIds.isEmpty()) {
                ReferenceList alleles = new ReferenceList("alleles", allelesRefIds);
                store(alleles, storedGenesIds.get(geneRefId));
            }
        }
    }

    private class Allele
    {
        String symbol;
        String type;
        String expression;
        String geneRefId;

        public Allele(String symbol, String type, String expression) {
            this.symbol = symbol;
            this.type = type;
            this.expression = expression;
        }

        public Allele(String line, String geneRefId) {
            String[] array = line.split("\t", -1); // keep trailing empty Strings
            this.expression = array[4];
            this.symbol = array[9];
            this.type = array[11];
            this.geneRefId = geneRefId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Allele allele = (Allele) o;
            return Objects.equals(symbol, allele.symbol) &&
                    Objects.equals(type, allele.type) &&
                    Objects.equals(expression, allele.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(symbol, type, expression);
        }
    }
}
