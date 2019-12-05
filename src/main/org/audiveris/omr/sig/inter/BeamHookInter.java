//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                    B e a m H o o k I n t e r                                   //
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

import org.audiveris.omr.constant.ConstantSet;
import org.audiveris.omr.glyph.Glyph;
import org.audiveris.omr.glyph.Shape;
import org.audiveris.omr.math.GeoOrder;
import org.audiveris.omr.math.LineUtil;
import org.audiveris.omr.run.Orientation;
import org.audiveris.omr.sheet.Scale;
import org.audiveris.omr.sheet.SystemInfo;
import org.audiveris.omr.sig.GradeImpacts;
import org.audiveris.omr.sig.relation.BeamPortion;
import org.audiveris.omr.sig.relation.BeamStemRelation;
import org.audiveris.omr.sig.relation.Link;
import org.audiveris.omr.util.HorizontalSide;
import static org.audiveris.omr.util.HorizontalSide.LEFT;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class {@code BeamHookInter} represents a beam hook interpretation.
 *
 * @see BeamInter
 * @author Hervé Bitteur
 */
@XmlRootElement(name = "beam-hook")
public class BeamHookInter
        extends AbstractBeamInter
{

    private static final Constants constants = new Constants();

    /**
     * Creates a new HookInter object.
     *
     * @param impacts the grade details
     * @param median  median beam line
     * @param height  beam height
     */
    public BeamHookInter (GradeImpacts impacts,
                          Line2D median,
                          double height)
    {
        super(Shape.BEAM_HOOK, impacts, median, height);
    }

    /**
     * Creates manually a new HookInter ghost object
     *
     * @param grade quality grade
     */
    public BeamHookInter (double grade)
    {
        super(Shape.BEAM_HOOK, grade);
    }

    /**
     * Meant for JAXB.
     */
    private BeamHookInter ()
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

    //---------------//
    // checkAbnormal //
    //---------------//
    @Override
    public boolean checkAbnormal ()
    {
        // Check if hook is connected to a stem
        setAbnormal(!sig.hasRelation(this, BeamStemRelation.class));

        return isAbnormal();
    }

    //--------//
    // isHook //
    //--------//
    @Override
    public boolean isHook ()
    {
        return true;
    }

    //-------------//
    // searchLinks //
    //-------------//
    @Override
    public Collection<Link> searchLinks (SystemInfo system,
                                         boolean doit)
    {
        // Not very optimized!
        List<Inter> systemStems = system.getSig().inters(StemInter.class);
        Collections.sort(systemStems, Inters.byAbscissa);

        Link link = lookupLink(systemStems, system);

        if (link == null) {
            return Collections.emptyList();
        }

        if (doit) {
            link.applyTo(this);
        }

        return Collections.singleton(link);
    }

    //----------//
    // setGlyph //
    //----------//
    @Override
    public void setGlyph (Glyph glyph)
    {
        super.setGlyph(glyph);

        // Compute height and median parameters
        Rectangle box = glyph.getBounds();
        height = (int) Math.rint(glyph.getMeanThickness(Orientation.HORIZONTAL));

        Point centroid = glyph.getCentroid();
        double slope = 0.0;
        Point2D p1 = LineUtil.intersectionAtX(centroid, slope, box.x);
        Point2D p2 = LineUtil.intersectionAtX(centroid, slope, (box.x + box.width) - 1);
        median = new Line2D.Double(p1, p2);

        computeArea();
    }

    //------------//
    // lookupLink //
    //------------//
    /**
     * Try to detect a link between this beam hook instance and a stem nearby,
     * either on left or on right side.
     *
     * @param systemStems ordered collection of stems in system
     * @rapam system containing system
     * @return the link found or null
     */
    private Link lookupLink (List<Inter> systemStems,
                             SystemInfo system)
    {
        final Scale scale = system.getSheet().getScale();
        final int xMargin = scale.toPixels(constants.xMargin);
        final int yMargin = scale.toPixels(constants.yMargin);
        final Rectangle luBox = getBounds();
        luBox.grow(xMargin, yMargin);

        final List<Inter> stems = Inters.intersectedInters(
                systemStems,
                GeoOrder.BY_ABSCISSA,
                luBox);

        // Find out the best stem candidate, if any
        double bestDist = Double.MAX_VALUE;
        StemInter bestStem = null;
        BeamStemRelation bestRel = null;

        for (HorizontalSide side : HorizontalSide.values()) {
            Point refPt = (side == LEFT) ? getCenterLeft() : getCenterRight();

            for (Inter stemInter : stems) {
                StemInter stem = (StemInter) stemInter;
                double d2 = stem.getMedian().ptSegDist(refPt);

                if (bestDist > d2) {
                    bestDist = d2;
                    bestStem = stem;
                    bestRel = new BeamStemRelation();
                    bestRel.setExtensionPoint(refPt); // Approximate
                    bestRel.setBeamPortion((side == LEFT) ? BeamPortion.LEFT : BeamPortion.RIGHT);
                }
            }
        }

        if (bestRel == null) {
            return null;
        }

        return new Link(bestStem, bestRel, true);
    }

    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {

        Scale.Fraction xMargin = new Scale.Fraction(0.5, "Width of lookup area for stem");

        Scale.Fraction yMargin = new Scale.Fraction(0.5, "Height of lookup area for stem");
    }
}
