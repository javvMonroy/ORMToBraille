//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                     S t e m s B u i l d e r                                    //
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
package org.audiveris.omr.sheet.stem;

import org.audiveris.omr.constant.Constant;
import org.audiveris.omr.constant.ConstantSet;
import org.audiveris.omr.glyph.Glyph;
import org.audiveris.omr.glyph.GlyphFactory;
import org.audiveris.omr.glyph.GlyphGroup;
import org.audiveris.omr.glyph.Glyphs;
import org.audiveris.omr.glyph.Shape;
import org.audiveris.omr.glyph.ShapeSet;
import org.audiveris.omr.glyph.dynamic.CompoundFactory;
import org.audiveris.omr.glyph.dynamic.SectionCompound;
import org.audiveris.omr.glyph.dynamic.StraightFilament;
import org.audiveris.omr.lag.Section;
import org.audiveris.omr.math.GeoOrder;
import org.audiveris.omr.math.GeoUtil;
import org.audiveris.omr.math.LineUtil;
import org.audiveris.omr.run.Orientation;
import static org.audiveris.omr.run.Orientation.*;
import org.audiveris.omr.run.Run;
import org.audiveris.omr.sheet.Scale;
import org.audiveris.omr.sheet.Scale.Fraction;
import org.audiveris.omr.sheet.Sheet;
import org.audiveris.omr.sheet.Skew;
import org.audiveris.omr.sheet.SystemInfo;
import org.audiveris.omr.sig.GradeImpacts;
import org.audiveris.omr.sig.SIGraph;
import org.audiveris.omr.sig.inter.AbstractBeamInter;
import org.audiveris.omr.sig.inter.BeamInter;
import org.audiveris.omr.sig.inter.HeadInter;
import org.audiveris.omr.sig.inter.Inter;
import org.audiveris.omr.sig.inter.Inters;
import org.audiveris.omr.sig.inter.StemInter;
import org.audiveris.omr.sig.relation.BeamPortion;
import org.audiveris.omr.sig.relation.BeamStemRelation;
import org.audiveris.omr.sig.relation.Exclusion.Cause;
import org.audiveris.omr.sig.relation.HeadStemRelation;
import org.audiveris.omr.sig.relation.Relation;
import org.audiveris.omr.ui.symbol.MusicFont;
import org.audiveris.omr.ui.symbol.ShapeSymbol;
import org.audiveris.omr.util.Corner;
import org.audiveris.omr.util.Dumping;
import org.audiveris.omr.util.HorizontalSide;
import static org.audiveris.omr.util.HorizontalSide.*;
import org.audiveris.omr.util.Navigable;
import org.audiveris.omr.util.StopWatch;
import static org.audiveris.omr.util.VerticalSide.*;
import org.audiveris.omr.util.Wrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Class {@code StemsBuilder} processes a system to build stems that connect to note
 * heads and perhaps beams.
 * <p>
 * At this point, beams have been identified as well as void and black heads, but no flags yet.
 * <p>
 * A stem is expected to be horizontally connected on the left or right side of a head and
 * vertically connected as well.
 * Such connections are looked up in the 4 corners of every head.
 * In poor-quality scores, stems can lack many pixels, resulting in vertical gaps between stem parts
 * and between head and nearest stem part, so we must accept such potential gaps (even if we lower
 * the resulting interpretation grade).
 * However we can be much more strict for the horizontal gap of the connection.
 * <p>
 * A stem can aggregate several items: stem seeds (built from long vertical sticks) and chunks
 * (built from suitable sections found in the corner), all being separated by vertical gaps.
 * Up to which point should we try to accept vertical gaps and increase a stem length starting from
 * a head?
 * <ol>
 * <li>If there is a beam in the corner, try a stem that at least reaches the beam.</li>
 * <li>Use a similar approach for the case of flag (if the flag is in the right direction), except
 * that we don't have identified flags yet!</li>
 * <li>If no obvious limit exists, accept all gaps in sequence while no too large gap is
 * encountered.</li>
 * </ol>
 * <p>
 * Stem-head connection uses criteria based on xGap and yGap at reference point.
 * Stem-beam connection uses yGap (and xGap in the case of beam side connection).
 * <p>
 * Every sequence of stem items built from the head is evaluated and potentially recorded as a
 * separate stem interpretation in the SIG.
 * <p>
 * TODO: We could analyze in the whole page the population of "good" stems to come up with most
 * common stem lengths according to stem configurations, and boost stem interpretations that match
 * these most common lengths.
 * More precisely, the length that goes from last head to end of stem (if this end is free from beam
 * or flag) should be rather constant between stems.
 * <p>
 * TODO: We could be more strict on verticality (modulo sheet slope) when retrieving stem seeds and
 * when building stems. We could thus expect a better split between the vertical runs that belong to
 * the stem and those that belong to the head.
 * This would imply a specific version of filament factory.
 * <p>
 * TODO: This class is getting too big, consider separate Head-Stem and Beam-Step linking topics.
 *
 * @author Hervé Bitteur
 */
public class StemsBuilder
{

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(StemsBuilder.class);

    /** The dedicated system. */
    @Navigable(false)
    private final SystemInfo system;

    /** The related SIG. */
    @Navigable(false)
    private final SIGraph sig;

    /** Sheet scale. */
    @Navigable(false)
    private final Scale scale;

    /** Dimension for black head symbol. */
    private final Dimension headSymbolDim;

    /** Global skew. */
    private final Skew skew;

    /** Scale-dependent parameters. */
    private Parameters params;

    /** Vertical seeds for this system. */
    private List<Glyph> systemSeeds;

    /** Beams and beam hooks for this system. */
    private List<Inter> systemBeams;

    /** Stems interpretations for this system. */
    private final List<StemInter> systemStems = new ArrayList<>();

    private VerticalsBuilder verticalsBuilder;

    /** Constructor for stem compound. */
    private final CompoundFactory.CompoundConstructor stemConstructor;

    /**
     * Creates a new StemsBuilder object.
     *
     * @param system the dedicated system
     */
    public StemsBuilder (SystemInfo system)
    {
        this.system = system;

        sig = system.getSig();

        Sheet sheet = system.getSheet();
        scale = sheet.getScale();
        skew = sheet.getSkew();

        ShapeSymbol symbol = Shape.NOTEHEAD_BLACK.getSymbol();
        headSymbolDim = symbol.getDimension(MusicFont.getHeadFont(scale, scale.getInterline()));

        stemConstructor = new StraightFilament.Constructor(scale.getInterline());
    }

    //--------------//
    // linkCueBeams //
    //--------------//
    /**
     * We reuse this class to connect a stem to potential cue beams.
     * Logic is rather simple for cue configurations. To be refined with a stem reuse to support
     * multiple cue heads on one stem.
     *
     * @param head   cue head
     * @param corner head corner for connection
     * @param stem   cue stem
     * @param beams  cue beams candidates
     */
    public void linkCueBeams (Inter head,
                              Corner corner,
                              Inter stem,
                              List<Inter> beams)
    {
        if (params == null) {
            params = new Parameters(system, scale);
        }

        new HeadLinker(head).linkCueCorner(corner, beams, (StemInter) stem);
    }

    //-----------//
    // linkStems //
    //-----------//
    /**
     * Link stems to suitable heads and beams in the system.
     * Retrieval is driven by heads (since a stem always needs a head), and can use beams.
     *
     * <pre>
     * Synopsis:
     *
     * - retrieve systemSeeds, systemBeams, systemHeads
     *
     * FOREACH head in systemHeads:
     *      FOREACH stem corner of the head:
     *          - getReferencePoint()
     *          - getLookupArea()
     *          - link()
     *              - lookupBeams()
     *              - lookupSeeds()
     *              - lookupChunks()
     *              - includeItems()
     *                  - createStemInter() for each relevant item
     *                  - connectHeadStem() for each relevant item
     *              - connectBeamStem() for relevant beams
     *
     * - retrieve systemStems
     *
     * FOREACH head in systemHeads:
     *      FOREACH stem corner of the head:
     *          - reuse()
     *              - connectHeadStem() for relevant stems
     *
     * - performMutualExclusions
     * </pre>
     */
    public void linkStems ()
    {
        if (params == null) {
            params = new Parameters(system, scale);
        }

        verticalsBuilder = new VerticalsBuilder(system);

        StopWatch watch = new StopWatch("StemsBuilder S#" + system.getId());
        watch.start("collections");
        // The abscissa-sorted stem seeds for this system
        systemSeeds = system.getGroupedGlyphs(GlyphGroup.VERTICAL_SEED);

        // The abscissa-sorted beam (and beam hook) interpretations for this system
        systemBeams = sig.inters(AbstractBeamInter.class);
        Collections.sort(systemBeams, Inters.byAbscissa);

        // The abscissa-sorted head interpretations for this system
        final List<Inter> systemHeads = sig.inters(
                ShapeSet.getStemTemplateNotes(system.getSheet()));
        Collections.sort(systemHeads, Inters.byAbscissa);

        // First phase, look around heads for stems (and beams if any)
        watch.start("phase #1");

        for (Inter head : systemHeads) {
            new HeadLinker(head).linkStemCorners();
        }

        // Second phase, look for reuse of existing stems interpretations
        watch.start("phase #2");
        Collections.sort(systemStems, Inters.byAbscissa);

        for (Inter head : systemHeads) {
            new HeadLinker(head).reuseStemCorners();
        }

        // Handle stems mutual exclusions
        watch.start("stem exclusions");
        performMutualExclusions();

        // Check stems horizontal gap on each beam
        watch.start("checkBeamStems");

        for (Inter beam : sig.inters(BeamInter.class)) {
            checkBeamStems(beam);
            boostBeamSides(beam);
        }

        // Check carefully multiple stem links on same head
        watch.start("checkHeadStems");

        for (Inter head : systemHeads) {
            checkHeadStems((HeadInter) head);
        }

        // Discard heads with no stem link
        watch.start("checkNeededStems");
        checkNeededStems(systemHeads);

        if (constants.printWatch.isSet()) {
            watch.print();
        }
    }

    //----------------//
    // boostBeamSides //
    //----------------//
    private void boostBeamSides (Inter beam)
    {
        if (!beam.isGood()) {
            return;
        }

        List<BeamStemRelation> rels = new ArrayList<>();

        for (Relation rel : sig.edgesOf(beam)) {
            if (rel instanceof BeamStemRelation) {
                rels.add((BeamStemRelation) rel);
            }
        }

        for (BeamStemRelation rel : rels) {
            if (rel.getBeamPortion() != BeamPortion.CENTER) {
                StemInter stem = (StemInter) sig.getEdgeTarget(rel);
                stem.increase(constants.sideStemBoost.getValue());
            }
        }
    }

    //----------------//
    // checkBeamStems //
    //----------------//
    /**
     * Check whether the beam does not have a stem relation too close to another.
     * Too close stems are mutually exclusive.
     *
     * @param beam the beam to check
     */
    private void checkBeamStems (Inter beam)
    {
        if (beam.isVip()) {
            logger.info("VIP checkBeamStems? {}");
        }

        List<BeamStemRelation> rels = new ArrayList<>();

        for (Relation rel : sig.edgesOf(beam)) {
            if (rel instanceof BeamStemRelation) {
                rels.add((BeamStemRelation) rel);
            }
        }

        // Sort by abscissae
        Collections.sort(rels, new Comparator<BeamStemRelation>()
                 {
                     @Override
                     public int compare (BeamStemRelation o1,
                                         BeamStemRelation o2)
                     {
                         return Double.compare(o1.getExtensionPoint().getX(), o2.getExtensionPoint()
                                               .getX());
                     }
                 });

        final int size = rels.size();

        for (int i = 0; i < size; i++) {
            BeamStemRelation rel = rels.get(i);
            StemInter stem = (StemInter) sig.getEdgeTarget(rel);

            for (BeamStemRelation r : rels.subList(i + 1, size)) {
                double dx = r.getExtensionPoint().getX() - rel.getExtensionPoint().getX();

                if (dx < params.minBeamStemsGap) {
                    StemInter s = (StemInter) sig.getEdgeTarget(r);
                    sig.insertExclusion(stem, s, Cause.INCOMPATIBLE);
                } else {
                    break;
                }
            }
        }
    }

    //----------------//
    // checkHeadStems //
    //----------------//
    /**
     * A head can have links to two stems (non mutually exclusive) only when these stems
     * are compatible (head is on stem ends with one stem on bottom left and one stem on
     * top right).
     * <p>
     * Otherwise, we must clean up the configuration.
     * If there is a link with zero yGap, it has priority over non-zero yGap that are generally due
     * to stem extension. So cut the non-zero links.
     * If there are two zero yGaps, if they are opposed on normal sides, it's OK.
     * If not, cut the link to the one not on normal side.
     * If there are two non-zero yGaps, cut the one with larger yGap.
     *
     * @param head the note head to check
     */
    private void checkHeadStems (HeadInter head)
    {
        // Retrieve all stems connected to this head
        List<Inter> allStems = new ArrayList<>();

        for (Relation rel : sig.getRelations(head, HeadStemRelation.class)) {
            allStems.add(sig.getEdgeTarget(rel));
        }

        // List of non-conflicting stems ensembles
        List<List<Inter>> partners = sig.getPartitions(null, allStems);
        ShareChecker checker = null;

        for (List<Inter> ensemble : partners) {
            if (ensemble.size() <= 1) {
                continue;
            }

            if (checker == null) {
                checker = new ShareChecker(head);
            }

            checker.check(ensemble);
        }
    }

    //------------------//
    // checkNeededStems //
    //------------------//
    /**
     * For heads that need a stem, check there is at least one.
     *
     * @param systemHeads heads to check
     */
    private void checkNeededStems (List<Inter> systemHeads)
    {
        for (Inter head : systemHeads) {
            if (ShapeSet.StemHeads.contains(head.getShape())) {
                if (!sig.hasRelation(head, HeadStemRelation.class)) {
                    head.remove();
                }
            }
        }
    }

    //-------------------------//
    // performMutualExclusions //
    //-------------------------//
    /**
     * Browse the system interpretations to insert mutual exclusions wherever possible.
     * This is done for stems.
     */
    private void performMutualExclusions ()
    {
        final List<Inter> stems = sig.inters(Shape.STEM);
        final int size = stems.size();
        int count = 0;

        try {
            if (size < 2) {
                return;
            }

            Collections.sort(stems, Inters.byAbscissa);

            for (int i = 0; i < (size - 1); i++) {
                final Inter one = stems.get(i);
                final Rectangle oneBox = one.getGlyph().getBounds();
                final int xBreak = oneBox.x + oneBox.width;

                for (Inter two : stems.subList(i + 1, size)) {
                    Rectangle twoBox = two.getGlyph().getBounds();

                    // Is there an overlap between stems one & two?
                    if (oneBox.intersects(twoBox)) {
                        sig.insertExclusion(one, two, Cause.OVERLAP);
                        count++;
                    } else if (twoBox.x >= xBreak) {
                        break;
                    }
                }
            }
        } finally {
            logger.debug("S#{} stems: {} exclusions: {}", system.getId(), size, count);
        }
    }

    //---------------------//
    // getMinStemExtension //
    //---------------------//
    /**
     * Report the minimum extension that goes from last head to end of stem.
     *
     * @return the defined constant
     */
    public static Fraction getMinStemExtension ()
    {
        return constants.minStemExtension;
    }

    //------------//
    // HeadLinker //
    //------------//
    /**
     * A HeadLinker tries to establish links from a head to nearby stem interpretations,
     * processing stem corners (TOP_LEFT and BOTTOM_RIGHT).
     */
    private class HeadLinker
    {

        /** The head interpretation being processed. */
        private final Inter head;

        /** Head bounding box. */
        private final Rectangle headBox;

        /** All beams and hooks interpretations in head vicinity. */
        private List<Inter> neighborBeams;

        /** All stems seeds in head vicinity. */
        private Set<Glyph> neighborSeeds;

        /** All stems interpretations in head vicinity. */
        private List<Inter> neighborStems;

        HeadLinker (Inter head)
        {
            this.head = head;
            headBox = head.getBounds();
        }

        //---------------//
        // linkCueCorner //
        //---------------//
        public void linkCueCorner (Corner corner,
                                   List<Inter> beams,
                                   StemInter stem)
        {
            new CornerLinker(corner).linkCue(beams, stem);
        }

        //-----------------//
        // linkStemCorners //
        //-----------------//
        public void linkStemCorners ()
        {
            if (head.isVip()) {
                logger.info("VIP linkStemCorners? {}", head);
            }

            neighborBeams = getNeighboringInters(systemBeams);
            neighborSeeds = getNeighboringSeeds();

            for (Corner corner : Corner.values) {
                new CornerLinker(corner).link();
            }
        }

        //------------------//
        // reuseStemCorners //
        //------------------//
        public void reuseStemCorners ()
        {
            if (head.isVip()) {
                logger.info("VIP reuseStemCorners? {}", head);
            }

            neighborStems = getNeighboringInters(systemStems);

            for (Corner corner : Corner.values) {
                new CornerLinker(corner).reuse();
            }
        }

        //----------------------//
        // getNeighboringInters //
        //----------------------//
        /**
         * From the provided collection of interpretations, retrieve all those located
         * in the vicinity of the provided central interpretation.
         *
         * @param inters the collection of interpretations to search
         * @return the set of neighboring interpretations
         */
        private List<Inter> getNeighboringInters (List<? extends Inter> inters)
        {
            // Retrieve neighboring inters, using a box of system height and sufficiently wide,
            // just to play with a limited number of inters.
            Rectangle systemBox = system.getBounds();
            Rectangle fatBox = new Rectangle(
                    headBox.x,
                    systemBox.y,
                    headBox.width,
                    systemBox.height);
            fatBox.grow(params.vicinityMargin, 0);

            return Inters.intersectedInters(inters, GeoOrder.BY_ABSCISSA, fatBox);
        }

        //---------------------//
        // getNeighboringSeeds //
        //---------------------//
        /**
         * Retrieve all vertical seeds in the vicinity of the provided (head) inter.
         *
         * @return the set of neighboring seeds
         */
        private Set<Glyph> getNeighboringSeeds ()
        {
            // Retrieve neighboring stem seeds, using a box of system height and sufficiently wide,
            // just to play with a limited number of seeds.
            Rectangle systemBox = system.getBounds();
            Rectangle fatBox = new Rectangle(
                    headBox.x,
                    systemBox.y,
                    headBox.width,
                    systemBox.height);
            fatBox.grow(params.vicinityMargin, 0);

            return Glyphs.intersectedGlyphs(systemSeeds, fatBox);
        }

        //
        //--------------//
        // CornerLinker //
        //--------------//
        /**
         * A CornerLinker searches for all acceptable head -> stem links in a given corner.
         */
        protected class CornerLinker
        {

            /** The corner being processed. */
            private final Corner corner;

            /** Direction of abscissae when going away from head. */
            private final int xDir;

            /** Direction of ordinates when going away from head. */
            private final int yDir;

            /** The head reference point for the corner. */
            private final Point2D refPt;

            /** The distant target point for the stem. */
            private Point2D targetPt;

            /** The look up area for the corner. */
            private Area area;

            /** The stems seeds found in the corner. */
            private List<Glyph> seeds;

            /** The most probable stem target line. */
            private Line2D targetLine;

            /** Ordinate range between refPt & limit. */
            private Rectangle yRange;

            CornerLinker (Corner corner)
            {
                this.corner = corner;

                xDir = (corner.hSide == RIGHT) ? 1 : (-1);
                yDir = (corner.vSide == BOTTOM) ? 1 : (-1);
                refPt = getReferencePoint();
            }

            //------//
            // link //
            //------//
            /**
             * Look for all acceptable stems interpretations that can be connected to
             * the head in the desired corner.
             * Stop the search at the first good beam found or at the first non acceptable yGap,
             * whichever comes first.
             */
            public void link ()
            {
                if (head.isVip()) {
                    logger.info("VIP link? {} {}", head, corner);
                }

                area = getLuArea();

                // Compute target end of stem
                Rectangle systemBox = system.getBounds();
                int sysY = (yDir > 0) ? (systemBox.y + systemBox.height) : systemBox.y;
                targetPt = getTargetPt(new Line2D.Double(0, sysY, 100, sysY));

                // Look for beams and beam hooks in the corner
                List<Inter> beamCandidates = Inters.intersectedInters(
                        neighborBeams,
                        GeoOrder.BY_ABSCISSA,
                        area);

                // Look for suitable beam groups
                List<List<AbstractBeamInter>> beamGroups = lookupBeamGroups(beamCandidates);

                Line2D line = new Line2D.Double(refPt, targetPt);
                head.addAttachment("t" + corner.getId(), line);

                yRange = getYRange(targetPt.getY());

                // Define the best target line (and collect suitable seeds)
                targetLine = getTargetLine();

                // Look for additional chunks built out of sections found.
                // Assign special role to a fat section part of head (if any)
                Wrapper<Section> fatHeadSection = new Wrapper<>(null);
                List<Glyph> chunks = lookupChunks(fatHeadSection);

                // Aggregate seeds and chunks up to the limit
                List<Glyph> items = new ArrayList<>(seeds);

                if (!chunks.isEmpty()) {
                    items.addAll(chunks);
                    sortByDistance(items);
                }

                double refY = refPt.getY(); // Reference ordinate

                if (fatHeadSection.value != null) {
                    // Shift the reference ordinate accordingly
                    Rectangle runBox = getRunBox(fatHeadSection.value, corner.hSide);
                    int contrib = getContrib(runBox);

                    if (contrib > 0) {
                        refY += (yDir * contrib);
                    }
                }

                // Include each item (seed / chunk) until limit is reached
                List<StemInter> stems = includeItems(items, refY, fatHeadSection.value);

                // Beam - Stem connection(s)?
                if (!beamGroups.isEmpty() && !stems.isEmpty()) {
                    linkBeamsAndStems(beamGroups, stems);
                }
            }

            //---------//
            // linkCue //
            //---------//
            /**
             * Specific link for cue (head & beam).
             */
            public void linkCue (List<Inter> candidates,
                                 StemInter stem)
            {
                // Look for beams in the corner
                List<List<AbstractBeamInter>> beamGroups = lookupBeamGroups(candidates);
                linkBeamsAndStems(beamGroups, Collections.singletonList(stem));
            }

            //-------//
            // reuse //
            //-------//
            /**
             * Check the stems interpretations in the vicinity and try to connect the
             * head to them, if not already done.
             */
            public void reuse ()
            {
                area = getLuArea();

                // Look for stems inters that intersect the lookup area
                List<Inter> stems = Inters.intersectedInters(
                        neighborStems,
                        GeoOrder.BY_ABSCISSA,
                        area);

                for (Inter inter : stems) {
                    StemInter stemInter = (StemInter) inter;
                    // (try to) connect
                    connectHeadStem(null, stemInter);
                }
            }

            //--------------------//
            // areGroupCompatible //
            //--------------------//
            /**
             * Check whether the two beams can be consecutive beams in the same beam
             * group, using ordinate gap.
             *
             * @param one current beam
             * @param two following beam, in 'dir' direction
             * @return true if OK
             */
            private boolean areGroupCompatible (AbstractBeamInter one,
                                                AbstractBeamInter two)
            {
                // Vertical gap?
                Point2D onePt = getTargetPt(one.getMedian());
                Point2D twoPt = getTargetPt(two.getMedian());
                final double yDistance = Math.abs(onePt.getY() - twoPt.getY());

                if (yDistance > params.maxBeamDistance) {
                    logger.debug("{} & {} are too distant", one, two);

                    return false;
                }

                return true;
            }

            //-----------------//
            // connectBeamStem //
            //-----------------//
            /**
             * (Try to) connect beam and stem.
             *
             * @param beam the beam or hook interpretation
             * @param stem the stem interpretation
             * @return the beam stem relation if successful, null otherwise
             */
            private BeamStemRelation connectBeamStem (AbstractBeamInter beam,
                                                      StemInter stem)
            {
                if (beam.isVip() && stem.isVip()) {
                    logger.info("VIP connectBeamStem? {} & {}", beam, stem);
                }

                // Relation beam -> stem (if not yet present)
                BeamStemRelation bRel;
                bRel = (BeamStemRelation) sig.getRelation(beam, stem, BeamStemRelation.class);

                if (bRel == null) {
                    final Line2D beamLimit = getLimit(beam);
                    bRel = new BeamStemRelation();

                    // Precise cross point
                    Point2D start = stem.getTop();
                    Point2D stop = stem.getBottom();
                    Point2D crossPt = crossing(stem, beam);

                    // Extension point
                    bRel.setExtensionPoint(
                            new Point2D.Double(
                                    crossPt.getX(),
                                    crossPt.getY() + (yDir * (beam.getHeight() - 1))));

                    // Abscissa -> beamPortion
                    // toLeft & toRight are >0 if within beam, <0 otherwise
                    double toLeft = crossPt.getX() - beamLimit.getX1();
                    double toRight = beamLimit.getX2() - crossPt.getX();
                    final double xGap;

                    if (beam instanceof BeamInter && (Math.min(
                            toLeft,
                            toRight) > params.maxBeamInDx)) {
                        // It's a beam center connection
                        bRel.setBeamPortion(BeamPortion.CENTER);
                        xGap = 0;
                    } else if (toLeft < toRight) {
                        bRel.setBeamPortion(BeamPortion.LEFT);
                        xGap = Math.max(0, -toLeft);
                    } else {
                        bRel.setBeamPortion(BeamPortion.RIGHT);
                        xGap = Math.max(0, -toRight);
                    }

                    // Ordinate
                    final double yGap = (yDir > 0) ? Math.max(0, crossPt.getY() - stop.getY())
                            : Math.max(0, start.getY() - crossPt.getY());

                    bRel.setOutGaps(scale.pixelsToFrac(xGap), scale.pixelsToFrac(yGap), false);

                    if (bRel.getGrade() >= bRel.getMinGrade()) {
                        sig.addEdge(beam, stem, bRel);
                        logger.debug("{} {} {}", head, corner, bRel);
                    } else {
                        bRel = null;
                    }
                }

                return bRel;
            }

            //-----------------//
            // connectHeadStem //
            //-----------------//
            /**
             * (Try to) connect head and stem.
             *
             * @param headSection the head section found, if any
             * @param stemInter   the stem interpretation to connect
             * @return the head-stem relation or null
             */
            private HeadStemRelation connectHeadStem (Section headSection,
                                                      StemInter stemInter)
            {
                // New relation head -> stem (if not yet present)
                HeadStemRelation hRel = (HeadStemRelation) sig.getRelation(
                        head,
                        stemInter,
                        HeadStemRelation.class);

                if (hRel == null) {
                    hRel = new HeadStemRelation();
                    hRel.setHeadSide(corner.hSide);

                    if (head.isVip() && stemInter.isVip()) {
                        logger.info("VIP connectHeadStem? {} & {}", head, stemInter);
                    }

                    final Glyph stemGlyph = stemInter.getGlyph();
                    final Rectangle stemBox = stemGlyph.getBounds();
                    final double xGap;
                    final double yGap;
                    final double xAnchor;

                    if (headSection != null) {
                        // xGap computed on head section
                        // yGap measured between head section and stem glyph
                        Rectangle runBox = getRunBox(headSection, corner.hSide);
                        xGap = xDir * (runBox.x - refPt.getX());
                        xAnchor = runBox.x;

                        int overlap = GeoUtil.yOverlap(runBox, stemBox);
                        yGap = Math.abs(Math.min(overlap, 0));
                    } else {
                        // Use stem line to compute both xGap and yGap
                        Point2D start = stemGlyph.getStartPoint(VERTICAL);
                        Point2D stop = stemGlyph.getStopPoint(VERTICAL);
                        xAnchor = LineUtil.xAtY(start, stop, refPt.getY());
                        xGap = xDir * (xAnchor - refPt.getX());

                        if (refPt.getY() < start.getY()) {
                            yGap = start.getY() - refPt.getY();
                        } else if (refPt.getY() > stop.getY()) {
                            yGap = refPt.getY() - stop.getY();
                        } else {
                            yGap = 0;
                        }
                    }

                    hRel.setInOutGaps(scale.pixelsToFrac(xGap), scale.pixelsToFrac(yGap), false);

                    if (hRel.getGrade() >= hRel.getMinGrade()) {
                        hRel.setExtensionPoint(
                                new Point2D.Double(
                                        xAnchor,
                                        (yDir > 0) ? headBox.y
                                                : ((headBox.y + headBox.height) - 1)));
                        sig.addEdge(head, stemInter, hRel);

                        if (stemInter.isVip()) {
                            logger.info("VIP linked {} {} {} to {}", head, corner, hRel, stemInter);
                        }
                    } else {
                        if (stemInter.isVip()) {
                            logger.info(
                                    "VIP failed link {} to {} {} {}",
                                    stemInter,
                                    head,
                                    corner,
                                    hRel.getDetails());
                        }

                        hRel = null;
                    }
                }

                return hRel;
            }

            //-----------------//
            // createStemInter //
            //-----------------//
            /**
             * (Try to) create stem interpretation with proper grade.
             *
             * @param items the sequence of items (seeds / chunks) that compose the stem
             * @return the proper stem interpretation or null if too weak
             */
            private StemInter createStemInter (List<Glyph> items)
            {
                Glyph stem = (items.size() == 1) ? items.get(0) : GlyphFactory.buildGlyph(items);
                stem = system.getSheet().getGlyphIndex().registerOriginal(stem);

                if (stem.isVip()) {
                    logger.info("VIP createStemInter? {}", stem);
                }

                // Stem interpretation (if not yet present for this glyph)
                StemInter stemInter = getStemInter(stem);

                if (stemInter == null) {
                    GradeImpacts impacts = verticalsBuilder.checkStem(stem);
                    double grade = impacts.getGrade();

                    if (grade >= StemInter.getMinGrade()) {
                        stemInter = new StemInter(stem, impacts);
                        sig.addVertex(stemInter);
                        systemStems.add(stemInter);
                    }
                }

                return stemInter;
            }

            //----------//
            // crossing //
            //----------//
            /**
             * Compute the crossing point between a stem and a beam.
             *
             * @param stem the stem
             * @param beam the beam
             * @return the precise crossing point
             */
            private Point2D crossing (StemInter stem,
                                      AbstractBeamInter beam)
            {
                Point2D start = stem.getTop();
                Point2D stop = stem.getBottom();
                Line2D beamLimit = getLimit(beam);

                return LineUtil.intersection(start, stop, beamLimit.getP1(), beamLimit.getP2());
            }

            //------------//
            // getContrib //
            //------------//
            /**
             * Report the (vertical) contribution of a rectangle to the filling of white
             * space above or below the head.
             *
             * @param box the rectangle to check
             * @return the corresponding height within white space
             */
            private int getContrib (Rectangle box)
            {
                return Math.max(0, GeoUtil.yOverlap(yRange, box));
            }

            //------------//
            // getInPoint //
            //------------//
            /**
             * Report the reference point slightly translated to the interior of the head,
             * to catch stem candidates.
             *
             * @return the inner refPt
             */
            private Point2D getInPoint ()
            {
                return new Point2D.Double(refPt.getX() - (xDir * params.maxHeadInDx), refPt.getY());
            }

            //----------//
            // getLimit //
            //----------//
            /**
             * Report proper beam limit, according to corner direction
             *
             * @param beam the beam or hook of interest
             * @return the top or bottom beam limit, according to dir
             */
            private Line2D getLimit (AbstractBeamInter beam)
            {
                return beam.getBorder(corner.vSide.opposite());
            }

            //-----------//
            // getLuArea //
            //-----------//
            /**
             * Define the lookup area on given corner, knowing the reference point of the
             * entity (head).
             * Global slope is used (plus and minus slopeMargin).
             *
             * @return the lookup area
             */
            private Area getLuArea ()
            {
                final double slope = skew.getSlope();
                final double dSlope = -xDir * yDir * params.slopeMargin;

                final Point2D outPt = getOutPoint();
                final Point2D inPt = getInPoint();

                // Look Up path, start by head horizontal segment
                final Path2D lu = new Path2D.Double();
                lu.moveTo(outPt.getX(), outPt.getY());
                lu.lineTo(inPt.getX(), inPt.getY());

                // Then segment away from head (system limit)
                final Rectangle systemBox = system.getBounds();
                final double yLimit = (yDir > 0) ? systemBox.getMaxY() : systemBox.getMinY();
                final double dy = yLimit - outPt.getY();
                lu.lineTo(inPt.getX() + ((slope + dSlope) * dy), yLimit);
                lu.lineTo(outPt.getX() + ((slope - dSlope) * dy), yLimit);
                lu.closePath();

                // Attachment
                StringBuilder sb = new StringBuilder();
                sb.append((corner.vSide == TOP) ? "T" : "B");
                sb.append((corner.hSide == LEFT) ? "L" : "R");
                head.addAttachment(sb.toString(), lu);

                return new Area(lu);
            }

            //-------------//
            // getOutPoint //
            //-------------//
            /**
             * Report the reference point slightly translated to the exterior of the head,
             * to catch stem candidates.
             *
             * @return the outer refPt
             */
            private Point2D getOutPoint ()
            {
                return new Point2D.Double(
                        refPt.getX() + (xDir * params.maxHeadOutDx),
                        refPt.getY());
            }

            //-------------------//
            // getReferencePoint //
            //-------------------//
            /**
             * Compute head reference point for this corner (the point where a stem could
             * be connected).
             * For best precision, we use the related shape descriptor.
             *
             * @return the refPt
             */
            private Point2D getReferencePoint ()
            {
                HeadInter note = (HeadInter) head;

                return note.getStemReferencePoint(corner.stemAnchor(), scale.getInterline());
            }

            //-----------//
            // getRunBox //
            //-----------//
            /**
             * Report the run box of the first or last run of the provided section
             * according to the desired side.
             *
             * @param section the section for which the side run is retrieved
             * @param side    the desired side
             * @return the run bounding box
             */
            private Rectangle getRunBox (Section section,
                                         HorizontalSide side)
            {
                final Run run = (xDir < 0) ? section.getFirstRun() : section.getLastRun();
                final int pos = (xDir < 0) ? section.getFirstPos() : section.getLastPos();

                return new Rectangle(pos, run.getStart(), 1, run.getLength());
            }

            //----------//
            // getInter //
            //----------//
            /**
             * Report the first stem interpretation if any for the glyph at hand.
             *
             * @param glyph the underlying glyph
             * @return the existing stem interpretation if any, or null
             */
            private StemInter getStemInter (Glyph glyph)
            {
                for (ListIterator<StemInter> it = systemStems.listIterator(systemStems.size()); it
                        .hasPrevious();) {
                    StemInter inter = it.previous();

                    if (inter.getGlyph() == glyph) {
                        return inter;
                    }
                }

                return null;
            }

            //---------------//
            // getTargetLine //
            //---------------//
            /**
             * Build the best possible target line.
             * First, we use (head) refPt and (distant) targetPt to define a theoretical line.
             * Then, we look for suitable seeds if any to refine the line.
             * The non-suitable seeds are removed from the collection.
             *
             * @return the best target line for stem
             */
            private Line2D getTargetLine ()
            {
                // Theoretical line
                Line2D theory = new Line2D.Double(refPt, targetPt);

                // Look for stems seeds
                seeds = new ArrayList<>(Glyphs.intersectedGlyphs(neighborSeeds, area));

                if (!seeds.isEmpty()) {
                    // Purge seeds that do not contribute to ordinate range
                    // or that are too abscissa-distant from theoretical line
                    for (Iterator<Glyph> it = seeds.iterator(); it.hasNext();) {
                        Glyph seed = it.next();
                        int contrib = getContrib(seed.getBounds());

                        if (contrib == 0) {
                            it.remove();

                            continue;
                        }

                        Point2D seedCenter = seed.getCentroid();
                        double dist = theory.ptLineDist(seedCenter);

                        if (dist > params.maxSeedJitter) {
                            it.remove();
                        }
                    }

                    // In case of overlap, simply keep the most contributive
                    List<Glyph> kept = new ArrayList<>();
                    sortByContrib(seeds);

                    StemLoop:
                    for (Glyph seed : seeds) {
                        Rectangle stemBox = seed.getBounds();

                        for (Glyph k : kept) {
                            if (GeoUtil.yOverlap(stemBox, k.getBounds()) > 0) {
                                continue StemLoop;
                            }
                        }

                        // No overlap
                        kept.add(seed);
                    }

                    seeds.retainAll(kept);

                    // Finally, define line based on seed(s) kept if any
                    if (!seeds.isEmpty()) {
                        sortByDistance(seeds);

                        Point2D start = seeds.get(0).getStartPoint(VERTICAL);
                        Point2D stop = seeds.get(seeds.size() - 1).getStopPoint(VERTICAL);

                        return new Line2D.Double(start, stop);
                    }
                }

                return theory;
            }

            //-------------//
            // getTargetPt //
            //-------------//
            /**
             * Compute the point where the (skewed) vertical from head reference point
             * crosses the provided limit.
             *
             * @param limit the end of the white space
             * @return the crossing point
             */
            private Point2D getTargetPt (Line2D limit)
            {
                final Point2D refPt2 = new Point2D.Double(
                        refPt.getX() - (100 * skew.getSlope()),
                        refPt.getY() + 100);

                return LineUtil.intersection(refPt, refPt2, limit.getP1(), limit.getP2());
            }

            //-----------//
            // getYRange //
            //-----------//
            /**
             * Compute the range to be covered by stem items
             *
             * @param yLimit the limit farthest from head
             * @return a range rectangle
             */
            private Rectangle getYRange (double yLimit)
            {
                return new Rectangle(
                        0, // x is irrelevant
                        (int) Math.rint((yDir > 0) ? refPt.getY() : yLimit),
                        0, // width is irrelevant
                        (int) Math.rint(Math.abs(yLimit - refPt.getY())));
            }

            //--------------//
            // includeItems //
            //--------------//
            /**
             * Include the stem items, one after the other.
             * We may have insufficient clean value for first items (resulting in no intermediate
             * StemInter created) but we must go on.
             *
             * @param items          the sequence of stem items, sorted by distance from head
             * @param refY           the ordinate of head ref point
             * @param fatHeadSection the fat head section if any
             * @return the list of StemInter instances built
             */
            private List<StemInter> includeItems (List<Glyph> items,
                                                  double refY,
                                                  Section fatHeadSection)
            {
                double lastY = refY; // Current end of stem
                List<StemInter> allStemInters = new ArrayList<>();

                for (int i = 0; i < items.size(); i++) {
                    Glyph item = items.get(i);
                    Rectangle itemBox = item.getBounds();

                    // Are we past the beam limit (if any)?
                    if (getContrib(itemBox) == 0) {
                        break;
                    }

                    // Is gap with previous item acceptable?
                    final int itemY = (yDir > 0) ? itemBox.y : ((itemBox.y + itemBox.height) - 1);
                    final double itemStart = (yDir > 0) ? Math.max(itemY, refY)
                            : Math.min(itemY, refY);
                    final double yGap = yDir * (itemStart - lastY);

                    if (yGap > params.maxYGap) {
                        break; // Too large gap
                    }

                    if ((i == 0) && (yGap > params.maxStemHeadGapY)) {
                        break; // Initial item too far from head
                    }

                    // Check minimum stem extension from head
                    double itemStop = itemY + (yDir * (itemBox.height - 1));
                    lastY = (yDir > 0) ? Math.max(lastY, itemStop) : Math.min(lastY, itemStop);

                    final double extension = Math.abs(lastY - refY);

                    if (extension < params.minStemExtension) {
                        continue;
                    }

                    // OK, build a stem interpretation with all items so far
                    List<Glyph> stemItems = items.subList(0, i + 1);
                    StemInter stemInter = createStemInter(stemItems);

                    if (stemInter != null) {
                        if (null != connectHeadStem(fatHeadSection, stemInter)) {
                            allStemInters.add(stemInter);
                        }
                    }
                }

                return allStemInters;
            }

            //-------------------//
            // linkBeamsAndStems //
            //-------------------//
            /**
             * Try to build links between the provided beams and the provided stems.
             *
             * @param beamGroups groups of beam candidates
             * @param stems      stem candidates
             */
            private void linkBeamsAndStems (List<List<AbstractBeamInter>> beamGroups,
                                            List<StemInter> stems)
            {
                for (List<AbstractBeamInter> group : beamGroups) {
                    AbstractBeamInter firstBeam = group.get(0);

                    for (StemInter stem : stems) {
                        // Try to connect first beam & stem
                        BeamStemRelation rel = connectBeamStem(firstBeam, stem);

                        if (rel != null) {
                            // Extend stem connection till end of current beam group, if relevant
                            if (firstBeam.isGood() && (group.size() > 1)) {
                                for (AbstractBeamInter next : group.subList(1, group.size())) {
                                    if (sig.getRelation(
                                            next,
                                            stem,
                                            BeamStemRelation.class) == null) {
                                        BeamStemRelation r = new BeamStemRelation();
                                        r.setBeamPortion(rel.getBeamPortion());

                                        Point2D crossPt = crossing(stem, next);
                                        r.setExtensionPoint(
                                                new Point2D.Double(
                                                        crossPt.getX(),
                                                        crossPt.getY() + (yDir * (next.getHeight()
                                                                                          - 1))));
                                        r.setGrade(rel.getGrade());
                                        sig.addEdge(next, stem, r);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //------------------//
            // lookupBeamGroups //
            //------------------//
            /**
             * Look for (groups of) beam interpretations in the lookup area.
             *
             * @param candidates provided collection of candidate beams
             * @return the list of groups, ordered by distance from head
             */
            private List<List<AbstractBeamInter>> lookupBeamGroups (List<Inter> candidates)
            {
                // Reject beam candidates which are not in corner direction
                // (this can happen because of beam bounding rectangle)
                for (Iterator<Inter> it = candidates.iterator(); it.hasNext();) {
                    AbstractBeamInter b = (AbstractBeamInter) it.next();

                    if ((yDir * (getTargetPt(getLimit(b)).getY() - refPt.getY())) <= 0) {
                        it.remove();
                    }
                }

                // Sort candidates by distance from head
                Collections.sort(candidates, new Comparator<Inter>()
                         {
                             @Override
                             public int compare (Inter i1,
                                                 Inter i2)
                             {
                                 AbstractBeamInter b1 = (AbstractBeamInter) i1;
                                 AbstractBeamInter b2 = (AbstractBeamInter) i2;

                                 return Double.compare(
                                         yDir * (getTargetPt(getLimit(b1)).getY() - refPt.getY()),
                                         yDir * (getTargetPt(getLimit(b2)).getY() - refPt.getY()));
                             }
                         });

                // Build the list of (groups of) beams
                List<List<AbstractBeamInter>> groups = new ArrayList<>();
                List<AbstractBeamInter> group = null;
                AbstractBeamInter prevBeam = null;
                boolean groupIsGood = false;

                for (Inter inter : candidates) {
                    AbstractBeamInter beam = (AbstractBeamInter) inter;

                    if (groups.isEmpty()) {
                        // Check if beam is far enough from head
                        final Point2D beamPt = getTargetPt(getLimit(beam));
                        final double distToBeam = yDir * (beamPt.getY() - refPt.getY());

                        if (distToBeam < params.minHeadBeamDistance) {
                            continue;
                        }
                    }

                    if (groupIsGood && areGroupCompatible(prevBeam, beam)) {
                        // Grow the current good group
                        group.add(beam);
                    } else {
                        // Start a brand new group
                        groups.add(group = new ArrayList<>());
                        group.add(beam);
                        groupIsGood = beam.isGood();
                    }

                    prevBeam = beam;
                }

                return groups;
            }

            //--------------//
            // lookupChunks //
            //--------------//
            /**
             * Retrieve chunks of stems out of additional compatible sections (not part
             * of stem seeds) found in the corner.
             *
             * @param fatHeadSection (output) specific section, part of head rather than stem
             * @return the collection of chunks found
             */
            private List<Glyph> lookupChunks (Wrapper<Section> fatHeadSection)
            {
                // Look up suitable sections
                List<Section> sections = lookupSections(fatHeadSection);

                // Aggregate these sections into glyphs & check them
                List<SectionCompound> chunks = CompoundFactory.buildCompounds(
                        sections,
                        stemConstructor);

                // Remove useless glyphs and put wide glyphs apart
                List<SectionCompound> wides = new ArrayList<>();

                for (Iterator<SectionCompound> it = chunks.iterator(); it.hasNext();) {
                    SectionCompound chunk = it.next();
                    Rectangle chunkBox = chunk.getBounds();

                    if (getContrib(chunkBox) == 0) {
                        it.remove();
                    } else if (chunk.getWeight() < params.minChunkWeight) {
                        it.remove();
                    } else {
                        int meanWidth = (int) Math.rint(
                                chunk.getMeanThickness(Orientation.VERTICAL));

                        if (meanWidth > params.maxStemThickness) {
                            wides.add(chunk);
                            it.remove();
                        }
                    }
                }

                // For too wide chunks we just keep the biggest section
                if (!wides.isEmpty()) {
                    for (SectionCompound wide : wides) {
                        List<Section> members = new ArrayList<>(wide.getMembers());
                        Collections.sort(members, Section.reverseWeightComparator);

                        SectionCompound compound = CompoundFactory.buildCompound(
                                Arrays.asList(members.get(0)),
                                stemConstructor);
                        chunks.add(compound);
                    }
                }

                // Convert section compounds to glyphs
                List<Glyph> glyphs = new ArrayList<>(chunks.size());

                for (SectionCompound chunk : chunks) {
                    glyphs.add(chunk.toGlyph(null));
                }

                return glyphs;
            }

            //----------------//
            // lookupSections //
            //----------------//
            /**
             * To complement stem seeds, look up for relevant sections in the lookup area
             * that could be part of a global stem.
             *
             * @param fatHeadSection (potential output) a thick section, part of head, that accounts
             *                       for stem range
             * @return the collection of additional sections found
             */
            private List<Section> lookupSections (Wrapper<Section> fatHeadSection)
            {
                // Horizontal line around refPt
                final Point2D outPt = getOutPoint();
                final Point2D inPt = getInPoint();
                final Line2D hLine = (corner.hSide == LEFT) ? new Line2D.Double(outPt, inPt)
                        : new Line2D.Double(inPt, outPt);
                final int refY = (int) Math.rint(refPt.getY());
                final List<Section> sections = new ArrayList<>();
                final List<Section> headSections = new ArrayList<>();

                // Widen head box with max stem width
                final Rectangle wideHeadBox = head.getBounds();
                wideHeadBox.grow(system.getSheet().getScale().getMaxStem(), 0);

                // Browse both vertical and horizontal sections in the system
                for (Collection<Section> collection : Arrays.asList(
                        system.getVerticalSections(),
                        system.getHorizontalSections())) {
                    SectionLoop:
                    for (Section section : collection) {
                        Rectangle sectBox = section.getBounds();

                        if (section.isVip()) {
                            logger.info("VIP {}", section);
                        }

                        // Check intersection at least
                        if (!area.intersects(sectBox)) {
                            continue;
                        }

                        // Containment is mandatory except for a head section
                        // (a section that intersects head glyph)
                        if (!area.contains(sectBox)) {
                            if (!sectBox.intersects(wideHeadBox) || !GeoUtil.yEmbraces(
                                    sectBox,
                                    refY)) {
                                continue;
                            }

                            // Section is likely to be part of head itself.
                            // Even if too thick, use part of its length as stem
                            // portion (if it does not overlap stem seeds)
                            if (section.isVertical() && (sectBox.width > params.maxStemThickness)) {
                                // Consider the touching run
                                Rectangle runBox = getRunBox(section, corner.hSide);

                                for (Glyph seed : seeds) {
                                    if (GeoUtil.yOverlap(runBox, seed.getBounds()) > 0) {
                                        continue SectionLoop;
                                    }
                                }

                                // Make sure this run is within area width
                                if (GeoUtil.xEmbraces(hLine, runBox.x)) {
                                    // Use head section that brings best contribution
                                    if (fatHeadSection.value != null) {
                                        Rectangle otherBox = getRunBox(
                                                fatHeadSection.value,
                                                corner.hSide);

                                        if (getContrib(runBox) > getContrib(otherBox)) {
                                            fatHeadSection.value = section;
                                        }
                                    } else {
                                        fatHeadSection.value = section;
                                    }
                                }

                                continue;
                            }

                            // A headSection must provide significant contribution
                            // otherwise it belongs to the head, not to the stem.
                            int sectContrib = getContrib(sectBox);

                            if (sectContrib < params.minHeadSectionContribution) {
                                logger.debug("Discarding tiny headSection {}", section);

                                continue;
                            }

                            headSections.add(section);
                        }

                        // Contraint section width <= stem width
                        if (sectBox.width > params.maxStemThickness) {
                            continue;
                        }

                        // A section which intersects an existing seed is useless
                        for (Glyph seed : seeds) {
                            if (GeoUtil.yOverlap(sectBox, seed.getBounds()) > 0) {
                                continue SectionLoop;
                            }
                        }

                        // Check section distance to target line
                        Point center = section.getCentroid();
                        double dist = targetLine.ptLineDist(center);

                        if (dist <= params.maxSectionJitter) {
                            sections.add(section);
                        }
                    }
                }

                // Handle overlap between standard sections and fatHeadSection
                // if any, by keeping the most contributive one
                if (fatHeadSection.value != null) {
                    final Rectangle runBox = getRunBox(fatHeadSection.value, corner.hSide);
                    final int runContrib = getContrib(runBox);

                    for (Iterator<Section> it = sections.iterator(); it.hasNext();) {
                        final Section section = it.next();
                        final Rectangle sctBox = section.getBounds();

                        if (GeoUtil.yOverlap(runBox, sctBox) > 0) {
                            if (getContrib(sctBox) <= runContrib) {
                                it.remove();
                            } else {
                                logger.debug("Cancelling fatHeadSection {}", fatHeadSection);
                                fatHeadSection.value = null;

                                break;
                            }
                        }
                    }
                }

                // Handle the case of several head sections that might result in a too thick glyph
                headSections.retainAll(sections);

                if (headSections.size() > 1) {
                    // Keep only the most contributive section
                    Section bestSection = null;
                    int bestContrib = Integer.MIN_VALUE;

                    for (Section section : headSections) {
                        int contrib = getContrib(section.getBounds());

                        if (contrib > bestContrib) {
                            bestContrib = contrib;
                            bestSection = section;
                        }
                    }

                    sections.removeAll(headSections);
                    headSections.clear();
                    headSections.add(bestSection);
                    sections.addAll(headSections);
                }

                return sections;
            }

            //---------------//
            // sortByContrib //
            //---------------//
            /**
             * Sort stem items by their decreasing contribution.
             */
            private void sortByContrib (List<Glyph> glyphs)
            {
                Collections.sort(glyphs, new Comparator<Glyph>()
                         {
                             @Override
                             public int compare (Glyph o1,
                                                 Glyph o2)
                             {
                                 // Sort by decreasing contribution
                                 int c1 = getContrib(o1.getBounds());
                                 int c2 = getContrib(o2.getBounds());

                                 return Integer.signum(c2 - c1);
                             }
                         });
            }

            //----------------//
            // sortByDistance //
            //----------------//
            /**
             * Sort stem items by their increasing vertical distance from head.
             */
            private void sortByDistance (List<Glyph> glyphs)
            {
                Collections.sort(glyphs, (yDir > 0) ? Glyphs.byOrdinate : Glyphs.byReverseBottom);
            }
        }
    }

    //--------------//
    // ShareChecker //
    //--------------//
    /**
     * Checks and cleans up an ensemble of stems around a head.
     */
    private class ShareChecker
    {

        private final HeadInter head;

        private final List<HeadStemRelation> rels = new ArrayList<>();

        ShareChecker (HeadInter head)
        {
            this.head = head;
        }

        public void check (List<Inter> stems)
        {
            // Retrieve stem relations
            rels.clear();

            for (Inter stem : stems) {
                HeadStemRelation rel = (HeadStemRelation) sig.getRelation(
                        head,
                        stem,
                        HeadStemRelation.class);

                if (rel != null) {
                    rels.add(rel);
                }
            }

            if (head.isVip() && (rels.size() > 1)) {
                logger.info("VIP {} with multiple stems {}", head, stems);
            }

            // Get down to a maximum of 2 stems
            while (rels.size() > 2) {
                if (!discardLargeGap()) {
                    discardWeakerStem();
                }
            }

            // Do we still have a conflict to solve?
            if (rels.size() == 2) {
                // If not canonical, try to discard one of the stem link
                if (!isCanonicalShare()) {
                    if (!discardLargeGap()) {
                        ///discardWeakerStem(); // Weaker stem may not be the good criteria!!!
                        if (head.isVip()) {
                            logger.info("VIP {} could not decide on stems {}", head, stems);
                        }
                    }
                }
            }
        }

        /**
         * Discard the stem link with largest significant y gap, if any.
         *
         * @return true if such stem link was found
         */
        private boolean discardLargeGap ()
        {
            double worstGap = 0;
            HeadStemRelation worstRel = null;

            for (HeadStemRelation rel : rels) {
                double yGap = rel.getDy();

                if (worstGap < yGap) {
                    worstGap = yGap;
                    worstRel = rel;
                }
            }

            if (worstGap > constants.yGapTiny.getValue()) {
                if (head.isVip()) {
                    logger.info("VIP {} discarding gap {}", head, sig.getEdgeTarget(worstRel));
                }

                rels.remove(worstRel);
                sig.removeEdge(worstRel);

                return true;
            } else {
                return false;
            }
        }

        /**
         * Discard the link to the stem with lower intrinsic grade.
         */
        private void discardWeakerStem ()
        {
            double worstGrade = Double.MAX_VALUE;
            HeadStemRelation worstRel = null;
            StemInter worstStem = null;

            for (HeadStemRelation rel : rels) {
                StemInter stem = (StemInter) sig.getEdgeTarget(rel);
                double grade = stem.getGrade();

                if (grade < worstGrade) {
                    worstGrade = grade;
                    worstStem = stem;
                    worstRel = rel;
                }
            }

            if (worstRel != null) {
                if (head.isVip()) {
                    logger.info("VIP {} discarding weaker {}", head, sig.getEdgeTarget(worstRel));
                }

                rels.remove(worstRel);
                sig.removeEdge(worstRel);

                // If this false relation is really invading, use exclusion
                if (worstRel.isInvading()) {
                    if (worstStem.isVip() || head.isVip()) {
                        logger.info("VIP invasion between {} & {}", head, worstStem);
                    }

                    sig.insertExclusion(head, worstStem, Cause.OVERLAP);
                }
            }
        }

        /**
         * Check whether this is the canonical "shared" configuration.
         * <p>
         * For this test, we cannot trust stem extensions and must stay with physical stem limits.
         *
         * @return true if canonical
         */
        private boolean isCanonicalShare ()
        {
            StemInter leftStem = null;
            StemInter rightStem = null;

            for (HeadStemRelation rel : rels) {
                if (rel.getDy() > constants.yGapTiny.getValue()) {
                    return false;
                }

                if (rel.getHeadSide() == LEFT) {
                    leftStem = (StemInter) sig.getOppositeInter(head, rel);
                } else {
                    rightStem = (StemInter) sig.getOppositeInter(head, rel);
                }
            }

            if (leftStem == null || rightStem == null) {
                return false;
            }

            return HeadStemRelation.isCanonicalShare(leftStem, head, rightStem);
        }
    }

    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {

        private final Constant.Boolean printWatch = new Constant.Boolean(
                false,
                "Should we print out the stop watch?");

        private final Scale.Fraction vicinityMargin = new Scale.Fraction(
                1.0,
                "Rough abscissa margin when looking for neighbors");

        private final Constant.Double slopeMargin = new Constant.Double(
                "tangent",
                0.02,
                "Margin around slope to define corner lookup area");

        private final Scale.Fraction minHeadSectionContribution = new Scale.Fraction(
                0.2,
                "Minimum stem contribution for a section near head");

        private final Scale.Fraction minStemExtension = new Scale.Fraction(
                0.8,
                "Minimum vertical distance from head to end of stem");

        private final Scale.Fraction minHeadBeamDistance = new Scale.Fraction(
                0.125,
                "Minimum vertical distance between head and beam");

        private final Scale.Fraction maxBeamDistance = new Scale.Fraction(
                1.5,
                "Default maximum vertical distance between two consecutive grouped beams");

        private final Scale.Fraction minBeamStemsGap = new Scale.Fraction(
                1.0,
                "Minimum x gap between two stems on the same beam");

        private final Constant.Ratio maxSeedJitter = new Constant.Ratio(
                2.0,
                "Maximum distance from stem seed to theoretical line,"
                        + " as ratio of typical stem width");

        private final Constant.Ratio maxSectionJitter = new Constant.Ratio(
                1.0,
                "Maximum distance from section center to target line,"
                        + " as ratio of typical stem width");

        private final Scale.Fraction yGapTiny = new Scale.Fraction(
                0.1,
                "Maximum vertical tiny gap between stem & head");

        private final Constant.Ratio sideStemBoost = new Constant.Ratio(
                0.5,
                "How much do we boost beam side stems");
    }

    //------------//
    // Parameters //
    //------------//
    /**
     * Class {@code Parameters} gathers all pre-scaled constants.
     */
    private static class Parameters
    {

        final double slopeMargin;

        final int maxHeadOutDx;

        final int maxBeamInDx;

        final int maxHeadInDx;

        final int vicinityMargin;

        final int maxStemHeadGapY;

        final int maxYGap;

        final int maxStemThickness;

        final int minChunkWeight;

        final int minHeadSectionContribution;

        final int minStemExtension;

        final int minHeadBeamDistance;

        final int minBeamStemsGap;

        final double maxSeedJitter;

        final double maxSectionJitter;

        final int maxBeamDistance;

        /**
         * Creates a new Parameters object.
         *
         * @param scale the scaling factor
         */
        Parameters (SystemInfo system,
                    Scale scale)
        {
            slopeMargin = constants.slopeMargin.getValue();
            maxHeadOutDx = scale.toPixels(HeadStemRelation.getXOutGapMaximum(false));
            maxBeamInDx = scale.toPixels(BeamStemRelation.getXInGapMaximum(false));
            maxHeadInDx = scale.toPixels(HeadStemRelation.getXInGapMaximum(false));
            vicinityMargin = scale.toPixels(constants.vicinityMargin);
            maxStemHeadGapY = scale.toPixels(HeadStemRelation.getYGapMaximum(false));
            maxYGap = scale.toPixels(VerticalsBuilder.getMaxYGap());
            minHeadSectionContribution = scale.toPixels(constants.minHeadSectionContribution);
            minStemExtension = scale.toPixels(getMinStemExtension());
            minHeadBeamDistance = scale.toPixels(constants.minHeadBeamDistance);
            minBeamStemsGap = scale.toPixels(constants.minBeamStemsGap);

            final int stemThickness = scale.getMaxStem();
            maxStemThickness = stemThickness;
            maxSeedJitter = constants.maxSeedJitter.getValue() * stemThickness;
            maxSectionJitter = constants.maxSectionJitter.getValue() * stemThickness;

            minChunkWeight = scale.getStemThickness(); // TODO: check this

            Double beamDistance = scale.getBeamMeanDistance();

            if (beamDistance != null) {
                maxBeamDistance = (int) Math.ceil(
                        beamDistance + (2 * scale.getBeamSigmaDistance()));
            } else {
                maxBeamDistance = scale.toPixels(constants.maxBeamDistance);
            }

            if (logger.isDebugEnabled()) {
                new Dumping().dump(this);
            }
        }
    }
}
