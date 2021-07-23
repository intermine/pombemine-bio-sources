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


import org.apache.log4j.Logger;
import org.intermine.bio.util.OrganismRepository;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.Reference;
import org.intermine.xml.full.ReferenceList;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Date;
import java.util.ArrayList;
import java.util.Set;

import static org.apache.commons.io.FilenameUtils.removeExtension;


/**
 * @author Daniela Butano
 */
public class PombeGenesConverter extends BioFileConverter
{
    private static final Logger LOG = Logger.getLogger(IsaConverter.class);
    private static final OrganismRepository OR = OrganismRepository.getOrganismRepository();

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

        // TODO: decide if to use blunt ids or not

        File file = getCurrentFile();
        if (file != null) { // test is run with an internal file, f will be null
            currentFile = f.getName();

            LOG.info("======================================");
            LOG.info("READING " + currentFile);
            LOG.info("======================================");
        }
    }

    private void clearMaps() {
    }
}
