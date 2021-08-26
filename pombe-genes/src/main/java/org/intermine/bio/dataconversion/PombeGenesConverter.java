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
    private String fileName = null;
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
        super(writer, model, "Pombase", "Pombase data");
    }

    /**
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        File file = getCurrentFile();
        if (file != null) { // test is run with an internal file, file will be null
            fileName = file.getName();

            LOG.info("======================================");
            LOG.info("READING " + fileName);
            LOG.info("======================================");

            JsonNode root = new ObjectMapper().readTree(reader);
            Iterator<JsonNode> it = root.elements();
            while (it.hasNext()) {
                storeGene(it.next());
            }
        }
    }

    private void storeGene(JsonNode geneRoot) {
        Item gene = createItem("Gene");
        String primaryIdentifier = geneRoot.path("systematic_id").asText();
        gene.setAttribute("primaryIdentifier", primaryIdentifier);
        String symbol = geneRoot.path("name").asText();
        if (!StringUtils.isEmpty(symbol)) {
            gene.setAttribute("symbol", symbol);
        }
        String name = geneRoot.path("product").asText();
        if (!StringUtils.isEmpty(name)) {
            gene.setAttribute("name", name);
        }
        String featureType = geneRoot.path("featureType").asText();
        if (!StringUtils.isEmpty(featureType)) {
            gene.setAttribute("featureType", featureType);
        }

        //set organism
        String taxonId = geneRoot.path("taxonid").asText();
        gene.setReference("organism", storeOrganism(taxonId));
        //set synonyms
        storeSynonyms(geneRoot.path("synonyms"), gene);
        //set chromosome and chromosome location
        JsonNode location = geneRoot.path("location");
        if (location != null) {
            String chromosomePrimaryIdentifier = location.path("chromosome_name").asText();
            gene.setReference("chromosome", storeChromosome(chromosomePrimaryIdentifier));
            gene.setReference("chromosomeLocation", storeLocation(location, gene));
        }
        //set transcripts
        storeTranscripts(geneRoot.path("transcripts"), gene);
        //set protein
        String uniprotId = geneRoot.path("uniprot_identifier").asText();
        if (!StringUtils.isEmpty(uniprotId)) {
            storeProtein(uniprotId, gene);
        }
        try {
            store(gene);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error storing gene ", ex);
        }
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
            try {
                store(synonym);
                synonymIds.add(synonym.getIdentifier());
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing synonym ", ex);
            }
        }
        bioEntity.setCollection("synonyms", synonymIds);
    }

    private Item storeChromosome(String primaryIdentifier) {
        if (chromosomes.containsKey(primaryIdentifier)) {
            return chromosomes.get(primaryIdentifier);
        } else {
            Item chromosome = createItem("Chromosome");
            chromosome.setAttributeIfNotNull("primaryIdentifier", primaryIdentifier);
            try {
                store(chromosome);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing protein ", ex);
            }
            chromosomes.put(primaryIdentifier, chromosome);
            return chromosome;
        }
    }
    private Item storeLocation(JsonNode locationNode, Item bioEntity) {
        Item location = createItem("Location");
        location.setAttributeIfNotNull("start", locationNode.path("start_pos").asText());
        location.setAttributeIfNotNull("end", locationNode.path("end_pos").asText());
        String strandValue = locationNode.path("strand").asText();
        switch (strandValue) {
            case "forward":
                location.setAttribute("strand", "1");
            case "reverse":
                location.setAttribute("strand", "-1");
            default:
                location.setAttribute("strand", "0");
        }
        location.setReference("locatedOn", bioEntity);
        try {
            store(location);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error storing protein ", ex);
        }
        return location;
    }

    private void storeProtein(String uniprotId, Item bioEntity) {
        Item protein;
        if (proteins.containsKey(uniprotId)) {
            protein = proteins.get(uniprotId);
        } else {
            protein = createItem("Protein");
            protein.setAttributeIfNotNull("primaryAccession", uniprotId);
            try {
                store(protein);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing protein ", ex);
            }
            proteins.put(uniprotId, protein);
        }
        bioEntity.setCollection("proteins", Arrays.asList(protein.getIdentifier()));
    }

    private void storeProtein(JsonNode proteinNode, Item bioEntity) {
        String primaryAccession = proteinNode.path("uniquename").asText();
        Item protein;
        if (proteins.containsKey(primaryAccession)) {
            protein = proteins.get(primaryAccession);
        } else {
            protein = createItem("Protein");
            protein.setAttributeIfNotNull("primaryAccession", primaryAccession);
            protein.setAttributeIfNotNull("name", proteinNode.path("prodict").asText());
            protein.setAttributeIfNotNull("molecularWeight", proteinNode.path("molecular_weight").asText());
            try {
                store(protein);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing protein ", ex);
            }
            bioEntity.setReference("protein", protein);
        }
        proteins.put(primaryAccession, protein);
    }

    private void storeTranscripts(JsonNode transcripts, Item gene) {
        List<String> transcriptIds = new ArrayList<>();
        for (JsonNode transcriptNode : transcripts) {
            Item transcript = createItem("Transcript");
            transcript.setAttributeIfNotNull("primaryIdentifier", transcriptNode.path("uniquename").asText());
            transcript.setAttributeIfNotNull("transcriptType", transcriptNode.path("transcript_type").asText());
            //set chromosome and chromosome location
            JsonNode location = transcriptNode.path("location");
            if (location != null) {
                String chromosomePrimaryIdentifier = location.path("chromosome_name").asText();
                transcript.setReference("chromosome", storeChromosome(chromosomePrimaryIdentifier));
                transcript.setReference("chromosomeLocation", storeLocation(location, transcript));
            }
            transcript.setReference("gene", gene);
            storeProtein(transcriptNode.path("protein"), transcript);
            try {
                store(transcript);
                transcriptIds.add(transcript.getIdentifier());
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing transcript ", ex);
            }
        }
        gene.setCollection("transcripts", transcriptIds);
    }

}
