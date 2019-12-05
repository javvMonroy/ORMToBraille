//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                   F i n g e r i n g I n t e r                                  //
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
package org.audiveris.omr.sig.inter;

import org.audiveris.omr.glyph.Glyph;
import org.audiveris.omr.glyph.Shape;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class {@code FingeringInter} represents the fingering for guitar left-hand.
 *
 * @author Hervé Bitteur
 */
@XmlRootElement(name = "fingering")
public class FingeringInter
        extends AbstractInter
        implements StringSymbolInter
{

    /** Integer value for the number. (0, 1, 2, 3, 4) */
    private final int value;

    /**
     * Creates a new FingeringInter object.
     *
     * @param glyph underlying glyph
     * @param shape precise shape
     * @param grade evaluation value
     */
    public FingeringInter (Glyph glyph,
                           Shape shape,
                           double grade)
    {
        super(glyph, null, shape, grade);
        this.value = (shape != null) ? valueOf(shape) : (-1);
    }

    /**
     * No-arg constructor meant for JAXB.
     */
    private FingeringInter ()
    {
        this.value = 0;
    }

    //--------//
    // accept //
    //--------//
    @Override
    public void accept (InterVisitor visitor)
    {
        visitor.visit(this);
    }

    //-----------------//
    // getSymbolString //
    //-----------------//
    @Override
    public String getSymbolString ()
    {
        return String.valueOf(value);
    }

    /**
     * @return the value
     */
    public int getValue ()
    {
        return value;
    }

    //-----------//
    // internals //
    //-----------//
    @Override
    protected String internals ()
    {
        return super.internals() + " " + shape;
    }

    //---------//
    // valueOf //
    //---------//
    private static int valueOf (Shape shape)
    {
        switch (shape) {
        case DIGIT_0:
            return 0;

        case DIGIT_1:
            return 1;

        case DIGIT_2:
            return 2;

        case DIGIT_3:
            return 3;

        case DIGIT_4:
            return 4;

        case DIGIT_5:
            return 5;

        //        // Following shapes may be useless
        //        case DIGIT_6:
        //            return 6;
        //
        //        case DIGIT_7:
        //            return 7;
        //
        //        case DIGIT_8:
        //            return 8;
        //
        //        case DIGIT_9:
        //            return 9;
        }

        throw new IllegalArgumentException("No fingering value for " + shape);
    }
}
