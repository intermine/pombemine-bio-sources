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


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;


import java.io.File;
import java.io.Reader;
import java.util.*;


/**
 * @author Daniela Butano
 */
public class PombeGenesConverter extends BioFileConverter
{
    private static final Logger LOG = Logger.getLogger(PombeGenesConverter.class);
    private String dataSourceName, dataSetTitle;
    private static final String DEFAULT_DATA_SOURCE_NAME = "PomBase";
    private static final String DEFAULT_DATA_SET_NAME = "PomBase data set";
    private String datasetRefId;
    private Map<String, Item> organisms = new HashMap<>();
    private Map<String, Item> chromosomes = new HashMap<>();
    private Map<String, Item> proteins = new HashMap<>();

    /**
     * Constructor
     *
     * @param writer the ItemWriter used to handle the resultant items
     * @param model  the Model
     */
    public PombeGenesConverter(ItemWriter writer, Model model) {
        super(writer, model);
    }

    /**
     * Datasource for any bioentities created
     * @param dataSourceName name of datasource for items created
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * If a value is specified this title will used when a DataSet is created.
     * @param dataSetTitle the title of the DataSets of any new features
     */
    public void setDataSetTitle(String dataSetTitle) {
        this.dataSetTitle = dataSetTitle;
    }

    private void storeDefaultDataset() throws ObjectStoreException {
        if (dataSetTitle == null) {
            dataSetTitle = DEFAULT_DATA_SET_NAME;
        }
        if (dataSourceName == null) {
            dataSourceName = DEFAULT_DATA_SOURCE_NAME;
        }
        String datasourceRefId = getDataSource(dataSourceName);
        datasetRefId = getDataSet(dataSetTitle, datasourceRefId, null);
    }

    /**
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        File file = getCurrentFile();
        if (file != null) {
            String fileName = file.getName();
            LOG.info("======================================");
            LOG.info("READING " + fileName);
            LOG.info("======================================");

            JsonNode root = new ObjectMapper().readTree(reader);
            Iterator<JsonNode> it = root.elements();
            storeDefaultDataset();
            while (it.hasNext()) {
                storeGene(it.next());
            }
        }
    }

    private void storeGene(JsonNode geneRoot) {
        Item gene = createItem("Gene");
        gene.setAttributeIfNotNull("primaryIdentifier", geneRoot.path("systematic_id").asText());
        gene.setAttributeIfNotNull("symbol", geneRoot.path("name").asText());
        gene.setAttributeIfNotNull("name", geneRoot.path("product").asText());
        gene.setAttributeIfNotNull("featureType", geneRoot.path("featureType").asText());
        gene.addToCollection("dataSets", datasetRefId);

        //create organism
        String taxonId = geneRoot.path("taxonid").asText();
        Item organism = storeOrganism(taxonId);
        setOrganism(gene, organism);
        //set synonyms
        storeSynonyms(geneRoot.path("synonyms"), gene);
        //set chromosome and chromosome location
        JsonNode location = geneRoot.path("location");
        Item chromosome = storeChromosome(location, gene, organism);
        storeLocation(location, gene, chromosome);
        //set transcripts
        storeTranscripts(geneRoot.path("transcripts"), gene, organism);
        //set protein
        String uniprotId = geneRoot.path("uniprot_identifier").asText();
        if (!StringUtils.isEmpty(uniprotId)) {
            storeUniProtEntry(uniprotId, gene, organism);
        }
        try {
            store(gene);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error storing gene ", ex);
        }
    }

    private void setOrganism(Item bioEntity, Item organism) {
        bioEntity.setReference("organism", organism);
    }

    private Item storeOrganism(String taxonId) {
        if (organisms.containsKey(taxonId)) {
            return organisms.get(taxonId);
        } else {
            Item organism = createItem("Organism");
            organism.setAttributeIfNotNull("taxonId", taxonId);
            try {
                store(organism);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing organism ", ex);
            }
            organisms.put(taxonId, organism);
            return organism;
        }
    }

    private void storeSynonyms(JsonNode synonyms, Item bioEntity) {
        List<String> synonymIds = new ArrayList<>();
        for (JsonNode synonymNode : synonyms) {
            Item synonym = createItem("Synonym");
            synonym.setAttributeIfNotNull("value", synonymNode.path("name").asText());
            synonym.setAttributeIfNotNull("type", synonymNode.path("type").asText());
            synonym.setReference("subject", bioEntity);
            synonym.addToCollection("dataSets", datasetRefId);
            try {
                store(synonym);
                synonymIds.add(synonym.getIdentifier());
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing synonym ", ex);
            }
        }
        bioEntity.setCollection("synonyms", synonymIds);
    }

    private Item storeChromosome(JsonNode location, Item bioEntity, Item organism) {
        Item chromosome = null;
        if (location != null) {
            String primaryIdentifier = location.path("chromosome_name").asText();
            if (chromosomes.containsKey(primaryIdentifier)) {
                chromosome = chromosomes.get(primaryIdentifier);
            } else {
                chromosome = createItem("Chromosome");
                chromosome.setAttributeIfNotNull("primaryIdentifier",
                        generateChromosomeIdentifier(primaryIdentifier));
                chromosome.addToCollection("dataSets", datasetRefId);
                setOrganism(chromosome, organism);
                try {
                    store(chromosome);
                } catch (ObjectStoreException ex) {
                    throw new RuntimeException("Error storing Item chromosome ", ex);
                }
                chromosomes.put(primaryIdentifier, chromosome);
            }
            bioEntity.setReference("chromosome", chromosome);
        }
        return chromosome;
    }

    private String generateChromosomeIdentifier(String primaryIdentifier) {
        if (!primaryIdentifier.startsWith("chromosome_")) {
            return primaryIdentifier;
        } else if (primaryIdentifier.equalsIgnoreCase("chromosome_1")) {
            return "I";
        } else if (primaryIdentifier.equalsIgnoreCase("chromosome_2")) {
            return "II";
        } else if (primaryIdentifier.equalsIgnoreCase("chromosome_3")) {
            return "III";
        } else {
            return primaryIdentifier;
        }
    }

    private void storeLocation(JsonNode locationNode, Item bioEntity, Item chromosome) {
        if (locationNode != null) {
            Item location = createItem("Location");
            location.setAttributeIfNotNull("start", locationNode.path("start_pos").asText());
            location.setAttributeIfNotNull("end", locationNode.path("end_pos").asText());
            location.setAttributeIfNotNull("phase", locationNode.path("phase").asText());
            location.addToCollection("dataSets", datasetRefId);
            String strandValue = locationNode.path("strand").asText();
            switch (strandValue) {
                case "forward":
                    location.setAttribute("strand", "1");
                    break;
                case "reverse":
                    location.setAttribute("strand", "-1");
                    break;
                default:
                    location.setAttribute("strand", "0");
            }
            location.setReference("feature", bioEntity);
            location.setReference("locatedOn", chromosome);
            bioEntity.setReference("chromosomeLocation", location);
            try {
                store(location);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing location ", ex);
            }
        }
    }

    private void storeUniProtEntry(String uniprotId, Item gene, Item organism) {
        Item protein;
        if (proteins.containsKey(uniprotId)) {
            protein = proteins.get(uniprotId);
        } else {
            protein = createItem("UniProtEntry");
            protein.setAttributeIfNotNull("primaryAccession", uniprotId);
            protein.setReference("gene", gene);
            protein.addToCollection("dataSets", datasetRefId);
            setOrganism(protein, organism);
            try {
                store(protein);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing protein ", ex);
            }
            proteins.put(uniprotId, protein);
        }
        gene.setReference("uniProtEntry", protein);
    }

    private void storeTranscripts(JsonNode transcripts, Item gene, Item organism) {
        List<String> transcriptIds = new ArrayList<>();
        for (JsonNode transcriptNode : transcripts) {
            Item transcript = createItem("Transcript");
            transcript.setAttributeIfNotNull("primaryIdentifier", transcriptNode.path("uniquename").asText());
            transcript.setAttributeIfNotNull("transcriptType", transcriptNode.path("transcript_type").asText());
            transcript.addToCollection("dataSets", datasetRefId);
            setOrganism(transcript, organism);
            //set part
            storeParts(transcriptNode.path("parts"), gene, organism);
            //set chromosome and chromosome location
            JsonNode location = transcriptNode.path("location");
            Item chromosome = storeChromosome(location, gene, organism);
            storeLocation(location, transcript, chromosome);
            storeProtein(transcriptNode.path("protein"), transcript, organism);
            storeCDS(transcriptNode.path("cds_location"), transcript, organism);
            transcript.setReference("gene", gene);
            try {
                store(transcript);
                transcriptIds.add(transcript.getIdentifier());
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing transcript ", ex);
            }
        }
        gene.setCollection("transcripts", transcriptIds);
    }

    private void storeParts(JsonNode parts, Item gene, Item organism) {
        if (parts != null) {
            List<String> featureIds = new ArrayList<>();
            for (JsonNode partNode : parts) {
                String featureType = partNode.path("feature_type").asText();
                Item feature = null;
                String collectionName = null;
                switch (featureType) {
                    case "exon":
                        feature = createItem("Exon");
                        collectionName = "exons";
                        break;
                    case "five_prime_utr":
                        feature = createItem("FivePrimeUTR");
                        collectionName = "UTRs";
                        break;
                    case "three_prime_utr":
                        feature = createItem("ThreePrimeUTR");
                        collectionName = "UTRs";
                        break;
                    case "cds_intron":
                        feature = createItem("Intron");
                        collectionName = "introns";
                        break;
                }
                if (feature != null) {
                    feature.setAttributeIfNotNull("primaryIdentifier", partNode.path("uniquename").asText());
                    feature.addToCollection("dataSets", datasetRefId);
                    setOrganism(feature, organism);
                    //set chromosome and chromosome location
                    JsonNode location = partNode.path("location");
                    Item chromosome = storeChromosome(location, gene, organism);
                    storeLocation(location, feature, chromosome);
                    storeSequence(partNode.path("residues").asText(), feature);
                    if (!featureType.equals("cds_intron")) {
                        feature.setReference("gene", gene);
                    } //otherwise should be genes?
                    try {
                        store(feature);
                        featureIds.add(feature.getIdentifier());
                    } catch (ObjectStoreException ex) {
                        throw new RuntimeException("Error storing feature ", ex);
                    }
                    gene.setCollection(collectionName, featureIds);
                }
            }
        }
    }

    private void storeProtein(JsonNode proteinNode, Item bioEntity, Item organism) {
        String primaryAccession = proteinNode.path("uniquename").asText();
        Item protein;
        if (proteins.containsKey(primaryAccession)) {
            protein = proteins.get(primaryAccession);
        } else {
            protein = createItem("Protein");
            protein.setAttributeIfNotNull("primaryAccession", primaryAccession);
            protein.setAttributeIfNotNull("name", proteinNode.path("product").asText());
            protein.setAttributeIfNotNull("molecularWeight", proteinNode.path("molecular_weight").asText());
            protein.addToCollection("dataSets", datasetRefId);
            storeSequence(proteinNode.path("sequence").asText(), protein);
            setOrganism(protein, organism);
            try {
                store(protein);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing protein ", ex);
            }
            bioEntity.setReference("protein", protein);
        }
        proteins.put(primaryAccession, protein);
    }

    private void storeSequence(String sequence, Item sequenceFeature) {
        if (!sequence.isEmpty()) {
            Item sequenceItem = createItem("Sequence");
            sequenceItem.setAttribute("residues", sequence);
            sequenceFeature.setReference("sequence", sequenceItem);
            try {
                store(sequenceItem);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing sequence ", ex);
            }
        }
    }

    private void storeCDS(JsonNode cdsNode, Item bioEntity, Item organism) {
        Item cds = createItem("CDS");
        cds.setAttribute("primaryIdentifier", createCDSIdentifier(bioEntity));
        cds.setReference("transcript", bioEntity);
        cds.addToCollection("dataSets", datasetRefId);
        setOrganism(cds, organism);
        Item chromosome = storeChromosome(cdsNode, cds, organism);
        storeLocation(cdsNode, cds, chromosome);
        try {
            store(cds);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error storing cds ", ex);
        }
    }

    private String createCDSIdentifier(Item bioEntity) {
        String primaryIdentifier = bioEntity.getAttribute("primaryIdentifier").getValue();
        return primaryIdentifier.concat("_CDS");
    }
}