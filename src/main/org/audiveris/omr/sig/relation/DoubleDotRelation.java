//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                D o u b l e D o t R e l a t i o n                               //
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
package org.audiveris.omr.sig.relation;

import org.audiveris.omr.constant.Constant;
import org.audiveris.omr.constant.ConstantSet;
import org.audiveris.omr.sheet.Scale;
import org.audiveris.omr.sig.inter.AugmentationDotInter;
import org.audiveris.omr.sig.inter.Inter;

import org.jgrapht.event.GraphEdgeChangeEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class {@code DoubleDotRelation} represents the relation between a second
 * augmentation dot and a first augmentation dot (case of double dot augmentation).
 *
 * @author Hervé Bitteur
 */
@XmlRootElement(name = "double-dot")
public class DoubleDotRelation
        extends AbstractConnection
{

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(DoubleDotRelation.class);

    private static final double[] OUT_WEIGHTS = new double[]{
        constants.xOutWeight.getValue(),
        constants.yWeight.getValue()};

    @Override
    public Object clone ()
            throws CloneNotSupportedException
    {
        return super.clone(); //To change body of generated methods, choose Tools | Templates.
    }

    //-------------------//
    // getXOutGapMaximum //
    //-------------------//
    public static Scale.Fraction getXOutGapMaximum (boolean manual)
    {
        return manual ? constants.xOutGapMaxManual : constants.xOutGapMax;
    }

    //-------------------//
    // getXOutGapMinimum //
    //-------------------//
    public static Scale.Fraction getXOutGapMinimum (boolean manual)
    {
        return manual ? constants.xOutGapMinManual : constants.xOutGapMin;
    }

    //----------------//
    // getYGapMaximum //
    //----------------//
    public static Scale.Fraction getYGapMaximum (boolean manual)
    {
        return manual ? constants.yGapMaxManual : constants.yGapMax;
    }

    //-------//
    // added //
    //-------//
    @Override
    public void added (GraphEdgeChangeEvent<Inter, Relation> e)
    {
        final AugmentationDotInter secondDot = (AugmentationDotInter) e.getEdgeSource();
        secondDot.checkAbnormal();
    }

    //----------------//
    // isSingleSource //
    //----------------//
    @Override
    public boolean isSingleSource ()
    {
        return true;
    }

    //----------------//
    // isSingleTarget //
    //----------------//
    @Override
    public boolean isSingleTarget ()
    {
        return true;
    }

    //---------//
    // removed //
    //---------//
    @Override
    public void removed (GraphEdgeChangeEvent<Inter, Relation> e)
    {
        final AugmentationDotInter secondDot = (AugmentationDotInter) e.getEdgeSource();

        if (!secondDot.isRemoved()) {
            secondDot.checkAbnormal();
        }
    }

    //---------------//
    // getOutWeights //
    //---------------//
    @Override
    protected double[] getOutWeights ()
    {
        return OUT_WEIGHTS;
    }

    //----------------//
    // getSourceCoeff //
    //----------------//
    /**
     * @return the supporting coefficient for (source) second dot
     */
    @Override
    protected double getSourceCoeff ()
    {
        return constants.secondDotSupportCoeff.getValue();
    }

    @Override
    protected Scale.Fraction getXOutGapMax (boolean manual)
    {
        return getXOutGapMaximum(manual);
    }

    @Override
    protected Scale.Fraction getYGapMax (boolean manual)
    {
        return getYGapMaximum(manual);
    }

    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {

        private final Constant.Ratio secondDotSupportCoeff = new Constant.Ratio(
                5,
                "Supporting coeff for (source) second dot");

        private final Scale.Fraction xOutGapMax = new Scale.Fraction(
                1.25,
                "Maximum horizontal gap between dots centers");

        private final Scale.Fraction xOutGapMaxManual = new Scale.Fraction(
                2.0,
                "Maximum manual horizontal gap between dots centers");

        private final Scale.Fraction xOutGapMin = new Scale.Fraction(
                0.2,
                "Minimum horizontal gap between dot centers");

        private final Scale.Fraction xOutGapMinManual = new Scale.Fraction(
                0.1,
                "Minimum manual horizontal gap between dot centers");

        private final Scale.Fraction yGapMax = new Scale.Fraction(
                0.25,
                "Maximum vertical gap between dots centers");

        private final Scale.Fraction yGapMaxManual = new Scale.Fraction(
                0.4,
                "Maximum manual vertical gap between dots centers");

        private final Constant.Ratio xOutWeight = new Constant.Ratio(
                1,
                "Relative impact weight for xOutGap");

        private final Constant.Ratio yWeight = new Constant.Ratio(
                3,
                "Relative impact weight for yGap");
    }
}
