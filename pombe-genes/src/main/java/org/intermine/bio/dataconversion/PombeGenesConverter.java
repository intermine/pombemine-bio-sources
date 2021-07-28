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
import org.intermine.xml.full.Reference;
import org.intermine.xml.full.ReferenceList;


import java.io.File;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Date;
import java.util.ArrayList;
import java.util.Set;


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
    private Map<String, List<String>> proteinGeneIds = new HashMap<>();
    //private Map

    /**
     * Constructor
     *
     * @param writer the ItemWriter used to handle the resultant items
     * @param model  the Model
     */
    public PombeGenesConverter(ItemWriter writer, Model model) {
        super(writer, model, "Pombase", "Pombase data");
    }

    /*
     *    json structure:
     *    ---------------
     *    
     */ 


    /**
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        File file = getCurrentFile();
        if (file != null) { // test is run with an internal file, f will be null
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

        //set organism
        String taxonId = geneRoot.path("taxonid").asText();
        gene.setReference("organism", createOrganism(taxonId));
        //set chromosome and chromosome location
        JsonNode location = geneRoot.path("location");
        if (location != null) {
            String chromosomePrimaryIdentifier = location.path("chromosome_name").asText();
            gene.setReference("chromosome", createChromosome(chromosomePrimaryIdentifier));
            gene.setReference("chromosomeLocation", createLocation(location, gene));
        }
        //set protein
        String uniprotId = geneRoot.path("uniprot_identifier").asText();
        if (!StringUtils.isEmpty(uniprotId)) {
            storeProtein(uniprotId);
            gene.setCollection("proteins", Arrays.asList(proteins.get(uniprotId).getIdentifier()));
        }
        try {
            store(gene);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error storing gene ", ex);
        }
    }

    private void storeOrganims() {
        try {
            for (String taxonId : organisms.keySet()) {
                Item organism = organisms.get(taxonId);
                store(organism);
            }
        } catch (ObjectStoreException e) {
            throw new RuntimeException("Error storing organism ", e);
        }
    }

    private Item createOrganism(String taxonId) {
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

    private Item createChromosome(String primaryIdentifier) {
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
    private Item createLocation(JsonNode locationNode, Item gene) {
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
        location.setReference("locatedOn", gene);
        try {
            store(location);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error storing protein ", ex);
        }
        return location;
    }

    private Item storeProtein(String uniprotId) {
        if (proteins.containsKey(uniprotId)) {
            return proteins.get(uniprotId);
        } else {
            Item protein = createItem("Protein");
            protein.setAttributeIfNotNull("primaryAccession", uniprotId);
            try {
                store(protein);
            } catch (ObjectStoreException ex) {
                throw new RuntimeException("Error storing protein ", ex);
            }
            proteins.put(uniprotId, protein);
            return protein;
        }
    }

    private void clearMaps() {
    }
}
