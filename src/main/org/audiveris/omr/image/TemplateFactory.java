//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                    T e m p l a t e F a c t o r y                               //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//
//  Copyright © Audiveris 2018. All rights reserved.
//
//  This program is free software: you can redistribute it and/or modify it under the terms of the
//  GNU Affero General Public License as published by the Free Software Foundation, either version
//  3 of the License, or (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
//  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//  See the GNU Affero General Public License for more details.
//
//  You should have received a copy of the GNU Affero General Public License along with this
//  program.  If not, see <http://www.gnu.org/licenses/>.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package org.audiveris.omr.image;

import org.audiveris.omr.glyph.Shape;
import org.audiveris.omr.glyph.ShapeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Class {@code TemplateFactory} builds needed instances of {@link Template} class
 * and keeps a catalog per desired point size and shape.
 *
 * @author Hervé Bitteur
 */
public class TemplateFactory
{

    private static final Logger logger = LoggerFactory.getLogger(TemplateFactory.class);

    /** Singleton. */
    private static final TemplateFactory INSTANCE = new TemplateFactory();

    /** Catalog of all templates already allocated, mapped by point size. */
    private final Map<Integer, Catalog> allSizes;

    /**
     * (Private) Creates the singleton object.
     */
    private TemplateFactory ()
    {
        allSizes = new HashMap<>();
    }

    //------------//
    // getCatalog //
    //------------//
    /**
     * Report the template catalog dedicated to the provided interline.
     *
     * @param pointSize provided point size
     * @return the catalog of all templates for the point size value
     */
    public Catalog getCatalog (int pointSize)
    {
        Catalog catalog = allSizes.get(pointSize);

        if (catalog == null) {
            synchronized (allSizes) {
                catalog = allSizes.get(pointSize);

                if (catalog == null) {
                    allSizes.put(pointSize, catalog = new Catalog(pointSize));
                }
            }
        }

        return catalog;
    }

    //-------------//
    // getInstance //
    //-------------//
    /**
     * Report this singleton instance.
     *
     * @return the TemplateFactory single instance
     */
    public static TemplateFactory getInstance ()
    {
        return INSTANCE;
    }

    //---------//
    // Catalog //
    //---------//
    /**
     * Handles all templates for a given pointSize value.
     */
    public static class Catalog
    {

        /** Point size value for this catalog. */
        final int pointSize;

        /** Map of all descriptors for this catalog. */
        final Map<Shape, ShapeDescriptor> descriptors = new EnumMap<>(Shape.class);

        /**
         * Create a {@code Catalog} object.
         *
         * @param pointSize provided pointSize value
         */
        public Catalog (int pointSize)
        {
            this.pointSize = pointSize;
            buildAllTemplates();
        }

        //---------------//
        // getDescriptor //
        //---------------//
        /**
         * Report the descriptor for a given shape.
         *
         * @param shape given shape
         * @return corresponding descriptor (not null, since all have been constructed)
         */
        public ShapeDescriptor getDescriptor (Shape shape)
        {
            return descriptors.get(shape);
        }

        //-------------//
        // getTemplate //
        //-------------//
        /**
         * Report the template for the desired shape
         *
         * @param shape desired shape
         * @return the template
         */
        public Template getTemplate (Shape shape)
        {
            ShapeDescriptor descriptor = descriptors.get(shape);

            return descriptor.getTemplate();
        }

        //-------------------//
        // buildAllTemplates //
        //-------------------//
        private void buildAllTemplates ()
        {
            for (Shape shape : ShapeSet.getTemplateNotes(null)) {
                descriptors.put(shape, new ShapeDescriptor(shape, pointSize));
            }
        }
    }
}
