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
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;

/**
 * DataConverter to parse a pombe phenotype file into Items.
 * 
 * @author Daniela Butano
 */
public class PombeAllelesConverter extends BioFileConverter
{
    private String datasource, dataset, licence;
    private String datasetRefId = null;
    private static final String DATASET_TITLE = "PomBase phenotypes data set";
    private static final String DATA_SOURCE_NAME = "PomBase";
    private Map<String, String> genes = new LinkedHashMap<>();
    private Map<String, String> publications = new LinkedHashMap<>();
    private Map<String, Allele> alleles = new LinkedHashMap<>();
    protected Map<String, String> phenotypeTerms = new LinkedHashMap<>();
    protected Map<String, String> ecoTerms = new LinkedHashMap<>();
    protected Map<String, String> pecoTerms = new LinkedHashMap<>();
    private Map<String, String> evidences = new LinkedHashMap<>();
    private Map<String, String> annotationExtensions = new LinkedHashMap<>();
    private Map<String, String> organismRefIds = new HashMap<>();
    private static final String LICENCE = "http://creativecommons.org/licenses/by/4.0/";

    /**
     * Constructor
     *
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     * @throws Exception if an error occurs in storing or finding Model
     */
    public PombeAllelesConverter(ItemWriter writer, Model model) throws Exception {
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
            String geneIdentifier = array[1];
            String phenotypeTermId = array[2];
            String geneSymbol = array[8];
            String evidence = array[12];
            String condition = array[13];
            String penetrance = array[14];
            String severity = array[15];
            String extension = array[16];
            String pubMedId = array[17];
            String taxonId = array[18];

            if (geneIdentifier != null) {
                String organismRefId = storeOrganism(taxonId);
                String geneRefId = storeGene(geneIdentifier, geneSymbol, organismRefId);
                Allele allele = new Allele(line, geneRefId);
                String alleleRefId = storeAllele(allele, organismRefId);
                String pubMedRefId = storePublication(pubMedId);
                String phenotypeTermRefId = storePhenotypeTerm(phenotypeTermId);
                String severityTermRefId = storePhenotypeTerm(severity);
                List<String> conditionsTermRefIds = storePECOTerms(condition);
                String evidenceRefId = storeEvidence(pubMedRefId, evidence);
                String annotationRefId = storeAnnotationExtension(extension);
                storePhenotypeAnnotation(alleleRefId, phenotypeTermRefId, evidenceRefId,
                        annotationRefId, penetrance, severityTermRefId, conditionsTermRefIds);
            }
        }
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

    private String storeGene(String primaryIdentifier, String symbol, String organismRefId)
            throws ObjectStoreException {
        if (!genes.containsKey(primaryIdentifier)) {
            Item gene = createItem("Gene");
            gene.setAttribute("primaryIdentifier", primaryIdentifier);
            gene.setAttributeIfNotNull("symbol", symbol);
            gene.setReference("organism", organismRefId);
            gene.addToCollection("dataSets", datasetRefId);
            store(gene);
            genes.put(primaryIdentifier, gene.getIdentifier());
        }
        return genes.get(primaryIdentifier);
    }

    private String storePublication(String pubMedId) throws ObjectStoreException {
        String pubRefId = "";
        if (pubMedId != null && pubMedId.startsWith("PMID:")) {
            pubMedId = pubMedId.substring(5);
            pubRefId = publications.get(pubMedId);
            if (pubRefId == null) {
                Item item = createItem("Publication");
                item.setAttribute("pubMedId", pubMedId);
                pubRefId = item.getIdentifier();
                publications.put(pubMedId, pubRefId);
                store(item);
            }
        }
        return pubRefId;
    }

    private String storeEvidence(String pubMedIdentifier, String evidence) throws ObjectStoreException {
        String evidenceKey = pubMedIdentifier.concat(evidence);
        String evidenceRefId = evidences.get(evidenceKey);
        if (evidenceRefId == null) {
            Item evidenceItem = createItem("OntologyEvidence");
            List<String> publication = new ArrayList<String>();
            publication.add(pubMedIdentifier);
            if (!StringUtils.isEmpty(evidence)) {
                evidenceItem.setReference("evidence", storeEvidenceEcoTerm(evidence));
            }
            //evidenceItem.setAttributeIfNotNull("description", evidence);
            evidenceItem.setCollection("publications", publication);
            store(evidenceItem);
            evidenceRefId = evidenceItem.getIdentifier();
            evidences.put(evidenceKey, evidenceRefId);
        }
        return evidenceRefId;
    }

    private String storeEvidenceEcoTerm(String evidence) throws ObjectStoreException {
        String ecoTermIdentifier = ecoTerms.get(evidence);
        if (ecoTermIdentifier == null) {
            Item item = createItem("ECOTerm");
            item.setAttribute("identifier", evidence);
            item.addToCollection("dataSets", datasetRefId);
            store(item);
            ecoTermIdentifier = item.getIdentifier();
            ecoTerms.put(evidence, ecoTermIdentifier);
        }
        return ecoTermIdentifier;
    }

    private String storeAnnotationExtension(String annotationExtensionDesc) throws ObjectStoreException {
        String annotationExtensionRefId = annotationExtensions.get(annotationExtensionDesc);
        if (annotationExtensionRefId == null) {
            if (StringUtils.isNotEmpty(annotationExtensionDesc)) {
                Item annotationExtension = createItem("AnnotationExtension");
                annotationExtension.setAttribute("description", annotationExtensionDesc);
                store(annotationExtension);
                annotationExtensionRefId = annotationExtension.getIdentifier();
                annotationExtensions.put(annotationExtensionDesc, annotationExtensionRefId);
            }
        }
        return annotationExtensionRefId;
    }

    private void storePhenotypeAnnotation(String alleleRefId,
            String phenotypeTermRefId, String evidenceRefId, String annotationRefId,
            String penetrance, String severityTermRefId, List<String> conditionsTermRefIds)
            throws ObjectStoreException {
        Item phenotypeAnnotation = createItem("PhenotypeAnnotation");
        if (alleleRefId != null) {
            phenotypeAnnotation.setReference("allele", alleleRefId);
            phenotypeAnnotation.setReference("subject", alleleRefId);
        }
        phenotypeAnnotation.addToCollection("dataSets", datasetRefId);
        if(!StringUtils.isEmpty(phenotypeTermRefId)) {
            phenotypeAnnotation.setReference("ontologyTerm", phenotypeTermRefId);
        }
        phenotypeAnnotation.setAttributeIfNotNull("penetrance", penetrance);
        if(!StringUtils.isEmpty(severityTermRefId)) {
            phenotypeAnnotation.setReference("severity", severityTermRefId);
        }
        if(!StringUtils.isEmpty(annotationRefId)) {
            phenotypeAnnotation.setReference("annotationExtension", annotationRefId);
        }
        if(!conditionsTermRefIds.isEmpty()) {
            phenotypeAnnotation.setCollection("conditions", conditionsTermRefIds);
        }
        if(!evidenceRefId.isEmpty()) {
            List<String> evidence = new ArrayList<>();
            evidence.add(evidenceRefId);
            phenotypeAnnotation.setCollection("evidence", evidence);
        }
        phenotypeAnnotation.addToCollection("dataSets", datasetRefId);

        store(phenotypeAnnotation);
    }

    private String storePhenotypeTerm(String identifier) throws ObjectStoreException {
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

    private List<String> storePECOTerms(String identifiers) throws ObjectStoreException {
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
                store(item);

                pecoTermIdentifier = item.getIdentifier();
                pecoTerms.put(identifier, pecoTermIdentifier);
            }
            pecoTermsIdentifiers.add(pecoTermIdentifier);
        }
        return pecoTermsIdentifiers;
    }

    private String storeAllele(Allele allele, String organismRefId) throws ObjectStoreException {
        Allele alleleStored = alleles.get(allele.primaryIdentifier);
        if (alleleStored == null) {
            Item alleleItem = createItem("Allele");
            alleleItem.setAttributeIfNotNull("primaryIdentifier", allele.getPrimaryIdentifier());
            alleleItem.setAttributeIfNotNull("symbol", allele.symbol);
            alleleItem.setAttributeIfNotNull("description", allele.description);
            alleleItem.setAttributeIfNotNull("type", allele.type);
            alleleItem.setAttributeIfNotNull("expression", allele.expression);
            alleleItem.setReference("organism", organismRefId);
            alleleItem.addToCollection("dataSets", datasetRefId);
            Reference gene = new Reference("gene", allele.geneRefId);
            Integer id = store(alleleItem);
            store(gene, id);
            String alleleRefId = alleleItem.getIdentifier();
            allele.setIdentifier(alleleRefId);
            alleles.put(allele.primaryIdentifier, allele);

            return alleleRefId;
        }
        return alleleStored.getIdentifier();
    }

    private class Allele
    {
        String symbol;
        String description;
        String type;
        String expression;
        String primaryIdentifier;
        String geneRefId;
        String identifier;

        public Allele(String symbol, String type, String expression) {
            this.symbol = symbol;
            this.type = type;
            this.expression = expression;
        }

        public Allele(String line, String geneRefId) {
            String[] array = line.split("\t", -1); // keep trailing empty Strings
            this.description = array[3];
            this.expression = array[4];
            this.symbol = array[9];
            this.type = array[11];
            primaryIdentifier = createPrimaryIdentifier();
            this.geneRefId = geneRefId;
        }

        private String createPrimaryIdentifier() {
            StringBuilder primaryidentifier = new StringBuilder();
            if (!StringUtils.isEmpty(symbol)) {
                primaryidentifier.append(symbol);
            }
            if (!StringUtils.isEmpty(description)) {
                primaryidentifier.append("(").append(description).append(")");
            }
            return primaryidentifier.toString();
        }

        public String getPrimaryIdentifier() {
            return primaryIdentifier;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
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
