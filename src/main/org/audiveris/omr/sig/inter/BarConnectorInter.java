//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                B a r C o n n e c t o r I n t e r                               //
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

import org.audiveris.omr.glyph.Grades;
import org.audiveris.omr.glyph.Shape;
import org.audiveris.omr.sheet.grid.BarConnection;
import org.audiveris.omr.sig.GradeImpacts;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class {@code BarConnectorInter} represents a vertical connector between two bar
 * lines across staves.
 *
 * @author Hervé Bitteur
 */
@XmlRootElement(name = "bar-connector")
public class BarConnectorInter
        extends AbstractVerticalInter
{

    private final BarConnection connection;

    /**
     * Creates a new {@code BarConnectorInter} object.
     *
     * @param connection the underlying connection
     * @param shape      the assigned shape
     * @param impacts    the assignment details
     */
    public BarConnectorInter (BarConnection connection,
                              Shape shape,
                              GradeImpacts impacts)
    {
        super(null, shape, impacts, connection.getMedian(), connection.getWidth());
        this.connection = connection;
    }

    /**
     * No-arg constructor meant for JAXB.
     */
    private BarConnectorInter ()
    {
        super(null, null, null, null, null);
        this.connection = null;
    }

    //--------//
    // accept //
    //--------//
    @Override
    public void accept (InterVisitor visitor)
    {
        visitor.visit(this);
    }

    //--------//
    // isGood //
    //--------//
    @Override
    public boolean isGood ()
    {
        return grade >= Grades.goodBarConnectorGrade;
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
