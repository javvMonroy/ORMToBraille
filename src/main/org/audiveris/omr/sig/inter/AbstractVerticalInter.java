//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                            A b s t r a c t V e r t i c a l I n t e r                           //
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
import org.audiveris.omr.math.AreaUtil;
import org.audiveris.omr.sig.GradeImpacts;
import org.audiveris.omr.util.Jaxb;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Class {@code AbstractVerticalInter} is the basis for similar vertical inter classes:
 * {@link BarlineInter} and {@link BracketInter}.
 *
 * @author Hervé Bitteur
 */
@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractVerticalInter
        extends AbstractInter
{

    /** Line width. */
    @XmlAttribute
    @XmlJavaTypeAdapter(Jaxb.Double1Adapter.class)
    protected final Double width;

    /** Median line. */
    @XmlElement
    @XmlJavaTypeAdapter(Jaxb.Line2DAdapter.class)
    protected final Line2D median;

    /**
     * Creates a new {@code AbstractVerticalInter} object.
     *
     * @param glyph   the underlying glyph
     * @param shape   the assigned shape
     * @param impacts the assignment details
     * @param median  the median line
     * @param width   the line width
     */
    public AbstractVerticalInter (Glyph glyph,
                                  Shape shape,
                                  GradeImpacts impacts,
                                  Line2D median,
                                  Double width)
    {
        super(glyph, null, shape, impacts);
        this.median = median;
        this.width = width;

        if (median != null) {
            computeArea();
        }
    }

    /**
     * Creates a new {@code AbstractVerticalInter} object.
     *
     * @param glyph  the underlying glyph
     * @param shape  the assigned shape
     * @param grade  the assignment quality
     * @param median the median line
     * @param width  the line width
     */
    public AbstractVerticalInter (Glyph glyph,
                                  Shape shape,
                                  double grade,
                                  Line2D median,
                                  Double width)
    {
        super(glyph, null, shape, grade);
        this.median = median;
        this.width = width;

        if (median != null) {
            computeArea();
        }
    }

    //--------//
    // accept //
    //--------//
    @Override
    public void accept (InterVisitor visitor)
    {
        visitor.visit(this);
    }

    //-----------//
    // getMedian //
    //-----------//
    /**
     * @return the median
     */
    public Line2D getMedian ()
    {
        return median;
    }

    //----------//
    // getWidth //
    //----------//
    /**
     * @return the width
     */
    public double getWidth ()
    {
        return width;
    }

    //----------------//
    // afterUnmarshal //
    //----------------//
    /**
     * Called after all the properties (except IDREF) are unmarshalled for this object,
     * but before this object is set to the parent object.
     */
    @SuppressWarnings("unused")
    private void afterUnmarshal (Unmarshaller um,
                                 Object parent)
    {
        if (median != null) {
            computeArea();
        }
    }

    //-------------//
    // computeArea //
    //-------------//
    private void computeArea ()
    {
        setArea(AreaUtil.verticalRibbon(new Path2D.Double(median), width));

        // Define precise bounds based on this path
        setBounds(getArea().getBounds());
    }

    //---------//
    // Impacts //
    //---------//
    /**
     * Grade impacts.
     */
    public static class Impacts
            extends GradeImpacts
    {

        private static final String[] NAMES = new String[]{"core", "gap", "start", "stop"};

        private static final double[] WEIGHTS = new double[]{1, 1, 1, 1};

        /**
         * Create an Impacts object.
         *
         * @param core  value of black core impact
         * @param gap   value of vertical gap impact
         * @param start derivative impact at start abscissa
         * @param stop  derivative impact at stop abscissa
         */
        public Impacts (double core,
                        double gap,
                        double start,
                        double stop)
        {
            super(NAMES, WEIGHTS);
            setImpact(0, core);
            setImpact(1, gap);
            setImpact(2, start);
            setImpact(3, stop);
        }
    }
}
