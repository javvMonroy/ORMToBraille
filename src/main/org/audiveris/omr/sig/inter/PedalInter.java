//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                       P e d a l I n t e r                                      //
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
import org.audiveris.omr.sheet.Staff;
import org.audiveris.omr.sig.relation.ChordPedalRelation;
import org.audiveris.omr.sig.relation.Relation;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class {@code PedalInter} represents a pedal (start) or pedal up (stop) event
 *
 * @author Hervé Bitteur
 */
@XmlRootElement(name = "pedal")
public class PedalInter
        extends AbstractDirectionInter
{

    /**
     * Creates a new {@code PedalInter} object.
     *
     * @param glyph the pedal glyph
     * @param shape PEDAL_MARK (start) or PEDAL_UP_MARK (stop)
     * @param grade the interpretation quality
     */
    public PedalInter (Glyph glyph,
                       Shape shape,
                       double grade)
    {
        super(glyph, (glyph != null) ? glyph.getBounds() : null, shape, grade);
    }

    /**
     * No-arg constructor meant for JAXB.
     */
    private PedalInter ()
    {
        super(null, null, null, 0);
    }

    //--------//
    // accept //
    //--------//
    @Override
    public void accept (InterVisitor visitor)
    {
        visitor.visit(this);
    }

    //----------//
    // getChord //
    //----------//
    /**
     * Report the related chord, if any.
     *
     * @return the related chord, or null
     */
    public AbstractChordInter getChord ()
    {
        if (sig != null) {
            for (Relation rel : sig.getRelations(this, ChordPedalRelation.class)) {
                AbstractChordInter chord = (AbstractChordInter) sig.getOppositeInter(this, rel);

                return chord;
            }
        }

        return null;
    }

    //----------//
    // getStaff //
    //----------//
    @Override
    public Staff getStaff ()
    {
        if (staff == null) {
            if (sig != null) {
                staff = sig.getSystem().getStaffAtOrAbove(getCenter());
            }
        }

        return staff;
    }

    //-----------//
    // internals //
    //-----------//
    @Override
    protected String internals ()
    {
        return super.internals() + " " + shape;
    }
}
