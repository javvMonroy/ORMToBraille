//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                               C l u s t e r s R e t r i e v e r                                //
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
package org.audiveris.omr.sheet.grid;

import org.audiveris.omr.constant.Constant;
import org.audiveris.omr.constant.ConstantSet;
import org.audiveris.omr.glyph.dynamic.Compounds;
import org.audiveris.omr.math.GeoUtil;
import org.audiveris.omr.run.Orientation;
import static org.audiveris.omr.run.Orientation.*;
import org.audiveris.omr.sheet.Scale;
import org.audiveris.omr.sheet.Scale.InterlineScale;
import org.audiveris.omr.sheet.Sheet;
import org.audiveris.omr.sheet.Skew;
import org.audiveris.omr.util.Dumping;
import org.audiveris.omr.util.Navigable;
import org.audiveris.omr.util.Wrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Class {@code ClustersRetriever} performs vertical samplings of the horizontal
 * filaments in order to detect regular patterns of a preferred interline value and
 * aggregate the filaments into clusters of lines.
 *
 * @author Hervé Bitteur
 */
public class ClustersRetriever
{

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(ClustersRetriever.class);

    /**
     * For comparing Filament instances on their starting point.
     */
    private static final Comparator<StaffFilament> byStartAbscissa = new Comparator<StaffFilament>()
    {
        @Override
        public int compare (StaffFilament f1,
                            StaffFilament f2)
        {
            // Sort on start
            return Double.compare(f1.getStartPoint().getX(), f2.getStartPoint().getX());
        }
    };

    /**
     * For comparing Filament instances on their stopping point.
     */
    private static final Comparator<StaffFilament> byStopAbscissa = new Comparator<StaffFilament>()
    {
        @Override
        public int compare (StaffFilament f1,
                            StaffFilament f2)
        {
            // Sort on stop
            return Double.compare(f1.getStopPoint().getX(), f2.getStopPoint().getX());
        }
    };

    /** Comparator on cluster ordinate. */
    public Comparator<LineCluster> byOrdinate = new Comparator<LineCluster>()
    {
        @Override
        public int compare (LineCluster c1,
                            LineCluster c2)
        {
            double o1 = ordinateOf(c1);
            double o2 = ordinateOf(c2);

            if (o1 < o2) {
                return -1;
            }

            if (o1 > o2) {
                return +1;
            }

            return 0;
        }
    };

    /**
     * Comparator by page layout (this leads to systems).
     */
    public Comparator<LineCluster> byLayout = new Comparator<LineCluster>()
    {
        @Override
        public int compare (LineCluster c1,
                            LineCluster c2)
        {
            Point p1 = c1.getCenter();
            Point p2 = c2.getCenter();

            if (GeoUtil.xOverlap(c1.getBounds(), c2.getBounds()) < 0) {
                // No abscissa overlap, we are side by side: use abscissae
                return Integer.compare(p1.x, p2.x);
            } else {
                // Abscissa overlap, we are one under the other: use deskewed ordinates
                return Double.compare(ordinateOf(p1), ordinateOf(p2));
            }
        }
    };

    /** Related sheet */
    @Navigable(false)
    private final Sheet sheet;

    /** Related scale */
    private final Scale scale;

    /** Interline scale for these clusters. */
    private final InterlineScale interlineScale;

    /** Scale-dependent constants */
    private final Parameters params;

    /** Picture width to sample for combs */
    private final int pictureWidth;

    /** Long filaments to process */
    private final List<StaffFilament> filaments;

    /** Filaments discarded. */
    private final List<StaffFilament> discardedFilaments = new ArrayList<>();

    /** Skew of the sheet */
    private final Skew skew;

    /** A map (colIndex -> vertical list of samples), sorted on colIndex */
    private final Map<Integer, List<FilamentComb>> colCombs;

    /** Color used for comb display */
    private final Color combColor;

    /**
     * The popular size of combs detected for the specified interline
     * (typically: 4, 5 or 6)
     */
    private int popSize;

    /** X values per column index */
    private int[] colX;

    /** Collection of clusters */
    private final List<LineCluster> clusters = new ArrayList<>();

    /**
     * Creates a new ClustersRetriever object, for a given staff
     * interline.
     *
     * @param sheet          the sheet to process
     * @param filaments      the current collection of filaments
     * @param interlineScale interline scaling info
     * @param combColor      color to be used for combs display
     */
    public ClustersRetriever (Sheet sheet,
                              List<StaffFilament> filaments,
                              InterlineScale interlineScale,
                              Color combColor)
    {
        this.sheet = sheet;
        this.filaments = filaments;
        this.interlineScale = interlineScale;
        this.combColor = combColor;

        skew = sheet.getSkew();
        pictureWidth = sheet.getWidth();
        scale = sheet.getScale();
        colCombs = new TreeMap<>();

        params = new Parameters(scale, interlineScale);
    }

    //-----------//
    // buildInfo //
    //-----------//
    /**
     * Organize the filaments into clusters as possible.
     *
     * @param checkConsistency true if cluster consistency must be checked
     * @return the filaments that could not be clustered
     */
    public List<StaffFilament> buildInfo (boolean checkConsistency)
    {
        // Retrieve all vertical combs gathering filaments
        retrieveCombs();

        popSize = 5; // Imposed!

        // Interconnect filaments via the network of combs
        followCombsNetwork();

        // Retrieve clusters
        retrieveClusters(checkConsistency);

        logger.info(
                "Retrieved line clusters: {} of size: {} with interline: {}",
                clusters.size(),
                popSize,
                interlineScale);

        return discardedFilaments;
    }

    //-------------//
    // getClusters //
    //-------------//
    /**
     * Report the sequence of clusters detected by this retriever using
     * its provided interline value.
     *
     * @return the sequence of interline-based clusters
     */
    public List<LineCluster> getClusters ()
    {
        return clusters;
    }

    //--------------//
    // getInterline //
    //--------------//
    /**
     * Report the value of the interline this retriever is based upon
     *
     * @return the interline value
     */
    public int getInterline ()
    {
        return interlineScale.main;
    }

    //-----------//
    // bestMatch //
    //-----------//
    /**
     * Find the best match between provided sequences.
     * (which may contain null values when related data is not available)
     *
     * @param one       first sequence
     * @param two       second sequence
     * @param bestDelta output: best delta between the two sequences
     * @return the best distance found
     */
    private double bestMatch (Double[] one,
                              Double[] two,
                              Wrapper<Integer> bestDelta)
    {
        final int deltaMax = one.length - 1;
        final int deltaMin = -deltaMax;

        double bestDist = Double.MAX_VALUE;
        bestDelta.value = null;

        for (int delta = deltaMin; delta <= deltaMax; delta++) {
            double distSum = 0.0;
            int count = 0;

            for (int oneIdx = 0; oneIdx < one.length; oneIdx++) {
                int twoIdx = oneIdx + delta;

                if ((twoIdx >= 0) && (twoIdx < two.length)) {
                    Double oneVal = one[oneIdx];
                    Double twoVal = two[twoIdx];

                    if ((oneVal != null) && (twoVal != null)) {
                        count++;
                        distSum += Math.abs(twoVal - oneVal);
                    }
                }
            }

            if (count > 0) {
                double dist = distSum / count;

                if (dist < bestDist) {
                    bestDist = dist;
                    bestDelta.value = delta;
                }
            }
        }

        return bestDist;
    }

    //----------//
    // canMerge //
    //----------//
    /**
     * Check for merge possibility between two clusters
     *
     * @param one      first cluster
     * @param two      second cluster
     * @param deltaPos output: delta in positions between these clusters if the test has succeeded
     * @return true if successful
     */
    private boolean canMerge (LineCluster one,
                              LineCluster two,
                              Wrapper<Integer> deltaPos)
    {
        if (one.isVip() && two.isVip()) {
            logger.info("VIP canMerge run on {} & {}", one, two);
        }

        final Rectangle oneBox = one.getBounds();
        final Rectangle twoBox = two.getBounds();

        final int oneLeft = oneBox.x;
        final int oneRight = (oneBox.x + oneBox.width) - 1;
        final int twoLeft = twoBox.x;
        final int twoRight = (twoBox.x + twoBox.width) - 1;

        final int minRight = Math.min(oneRight, twoRight);
        final int maxLeft = Math.max(oneLeft, twoLeft);
        final int gap = maxLeft - minRight;
        double dist;

        logger.debug("gap:{}", gap);

        if (gap > params.maxMergeDx) {
            logger.debug("Gap {} too wide between {} & {}", gap, one, two);

            return false;
        }

        if (gap <= 0) {
            // Overlap: measure vertical distances at middle abscissa of common part
            final int xMid = (maxLeft + minRight) / 2;
            final double slope = sheet.getSkew().getSlope();
            dist = bestMatch(
                    ordinatesOf(one.getPointsAt(xMid, params.maxExpandDx, slope)),
                    ordinatesOf(two.getPointsAt(xMid, params.maxExpandDx, slope)),
                    deltaPos);

            if (dist <= params.maxMergeDy) {
                // Check there is no collision on common lines
                return checkCollision(one, two, deltaPos.value);
            }

            return false;
        }

        if (oneLeft < twoLeft) { // Case one --- two
            dist = bestMatch(ordinatesOf(one.getStops()), ordinatesOf(two.getStarts()), deltaPos);
        } else { // Case two --- one
            dist = bestMatch(ordinatesOf(one.getStarts()), ordinatesOf(two.getStops()), deltaPos);
        }

        // Check best distance
        logger.debug("canMerge dist: {} one:{} two:{}", dist, one, two);

        return dist <= params.maxMergeDy;
    }

    //----------------//
    // checkCollision //
    //----------------//
    /**
     * Check whether the two provided overlapping clusters do not collide on their
     * common line(s).
     *
     * @param one   one cluster
     * @param two   another cluster
     * @param delta delta line index
     * @return true if OK
     */
    private boolean checkCollision (LineCluster one,
                                    LineCluster two,
                                    int delta)
    {
        final List<StaffFilament> oneLines = new ArrayList<>(one.getLines());
        final List<StaffFilament> twoLines = new ArrayList<>(two.getLines());

        for (int i1 = 0; i1 < oneLines.size(); i1++) {
            final StaffFilament f1 = oneLines.get(i1);
            final Rectangle r1 = f1.getBounds();
            final int i2 = i1 + delta;

            if ((i2 >= 0) && (i2 < twoLines.size())) {
                // We have a common line
                final StaffFilament f2 = twoLines.get(i2);
                final Rectangle r2 = f2.getBounds();
                final int overlap = GeoUtil.xOverlap(r1, r2);

                if (overlap >= 0) {
                    // Check resulting thickness at middle of range
                    final int mid = Math.max(r1.x, r2.x) + (overlap / 2);
                    double thickness = Compounds.getThicknessAt(mid, HORIZONTAL, scale, f1, f2);

                    if (thickness > scale.getMaxFore()) {
                        logger.debug("Cluster collision {} between {} & {}", thickness, one, two);

                        return false;
                    }
                }
            }
        }

        return true; // No collision detected
    }

    //-------------------------//
    // computeAcceptableLength //
    //-------------------------//
    private double computeAcceptableLength ()
    {
        // Determine minimum true length for valid clusters
        List<Integer> lengths = new ArrayList<>();

        for (LineCluster cluster : clusters) {
            lengths.add(cluster.getTrueLength());
        }

        Collections.sort(lengths);

        int medianLength = lengths.get(lengths.size() / 2);
        double minLength = medianLength * constants.minClusterLengthRatio.getValue();

        logger.debug("medianLength: {} minLength: {}", medianLength, minLength);

        return minLength;
    }

    //------------------//
    // connectAncestors //
    //------------------//
    private void connectAncestors (StaffFilament one,
                                   StaffFilament two)
    {
        StaffFilament oneAnc = (StaffFilament) one.getAncestor();
        StaffFilament twoAnc = (StaffFilament) two.getAncestor();

        if (oneAnc != twoAnc) {
            if (oneAnc.getLength(Orientation.HORIZONTAL) >= twoAnc.getLength(
                    Orientation.HORIZONTAL)) {
                ///logger.info("Inclusion " + twoAnc + " into " + oneAnc);
                oneAnc.include(twoAnc);
                oneAnc.getCombs().putAll(twoAnc.getCombs());
            } else {
                ///logger.info("Inclusion " + oneAnc + " into " + twoAnc);
                twoAnc.include(oneAnc);
                twoAnc.getCombs().putAll(oneAnc.getCombs());
            }
        }
    }

    //----------------//
    // createClusters //
    //----------------//
    private void createClusters ()
    {
        Collections.sort(filaments, Compounds.byReverseLength(Orientation.HORIZONTAL));

        for (StaffFilament fil : filaments) {
            fil = (StaffFilament) fil.getAncestor();

            if ((fil.getCluster() == null) && !fil.getCombs().isEmpty()) {
                LineCluster cluster = new LineCluster(scale, interlineScale, fil);
                clusters.add(cluster);
            }
        }

        removeMergedClusters();
    }

    //-----------------------------//
    // destroyInconsistentClusters //
    //-----------------------------//
    /**
     * Destroy any cluster with non-consistent lines lengths.
     */
    private void destroyInconsistentClusters ()
    {
        for (Iterator<LineCluster> it = clusters.iterator(); it.hasNext();) {
            LineCluster cluster = it.next();

            if (!isConsistent(cluster)) {
                logger.info("Destroying non-consistent {}", cluster);

                cluster.destroy();
                it.remove();
            }
        }
    }

    //----------------------------//
    // destroyNonStandardClusters //
    //----------------------------//
    private void destroyNonStandardClusters ()
    {
        for (Iterator<LineCluster> it = clusters.iterator(); it.hasNext();) {
            LineCluster cluster = it.next();

            if (cluster.getSize() != popSize) {
                logger.debug("Destroying non standard {}", cluster);

                cluster.destroy();
                it.remove();
            }
        }
    }

    //------------------------------//
    // discardNonClusteredFilaments //
    //------------------------------//
    private void discardNonClusteredFilaments ()
    {
        for (Iterator<StaffFilament> it = filaments.iterator(); it.hasNext();) {
            StaffFilament fil = it.next();

            if (fil.getCluster() == null) {
                it.remove();
                discardedFilaments.add(fil);
            }
        }
    }

    //--------------//
    // dumpClusters //
    //--------------//
    private void dumpClusters ()
    {
        for (LineCluster cluster : clusters) {
            logger.info("{} {}", cluster.getCenter(), cluster.toString());
        }
    }

    //---------------//
    // expandCluster //
    //---------------//
    /**
     * Try to expand the provided cluster with filaments taken out of
     * the provided sorted collection of isolated filaments
     *
     * @param cluster the cluster to work on
     * @param fils    the (properly sorted) collection of filaments
     */
    private void expandCluster (LineCluster cluster,
                                List<StaffFilament> fils)
    {
        final double slope = sheet.getSkew().getSlope();
        Rectangle clusterBox = null;

        for (StaffFilament fil : fils) {
            fil = (StaffFilament) fil.getAncestor();

            if (fil.getCluster() != null) {
                continue;
            }

            // For VIP debugging
            final boolean areVips = cluster.isVip() && fil.isVip();
            String vips = null;

            if (areVips) {
                vips = "F" + fil.getId() + "&C" + cluster.getId() + ": "; // BP here!
            }

            if (clusterBox == null) {
                clusterBox = cluster.getBounds();
                clusterBox.grow(params.maxMergeDx, params.clusterYMargin);
            }

            Rectangle filBox = fil.getBounds();
            Point middle = new Point();
            middle.x = filBox.x + (filBox.width / 2);
            middle.y = (int) Math.rint(fil.getPositionAt(middle.x, HORIZONTAL));

            if (clusterBox.contains(middle)) {
                // Check if this filament matches a cluster line
                List<Point2D> points = cluster.getPointsAt(middle.x, params.maxExpandDx, slope);

                for (Point2D point : points) {
                    // Check vertical distance, if point is available
                    if (point == null) {
                        continue;
                    }

                    double dy = Math.abs(middle.y - point.getY());

                    if (dy <= params.maxExpandDy) {
                        int index = points.indexOf(point);

                        if (cluster.includeFilamentByIndex(fil, index)) {
                            if (logger.isDebugEnabled() || fil.isVip() || cluster.isVip()) {
                                logger.info(
                                        "VIP aggregated F{} to C{} at index {}",
                                        fil.getId(),
                                        cluster.getId(),
                                        index);

                                if (fil.isVip()) {
                                    cluster.setVip(true);
                                }
                            }

                            clusterBox = null; // Invalidate cluster box

                            break;
                        }
                    } else if (areVips) {
                        logger.info("VIP {}dy={} vs {}", vips, dy, params.maxExpandDy);
                    }
                }
            } else if (areVips) {
                logger.info("{}No box intersection", vips);
            }
        }
    }

    //----------------//
    // expandClusters //
    //----------------//
    /**
     * Aggregate non-clustered filaments to close clusters when appropriate.
     */
    private void expandClusters ()
    {
        List<StaffFilament> startFils = new ArrayList<>(filaments);
        Collections.sort(startFils, byStartAbscissa);

        List<StaffFilament> stopFils = new ArrayList<>(startFils);
        Collections.sort(stopFils, byStopAbscissa);

        // Browse clusters, starting with the longest ones
        Collections.sort(clusters, LineCluster.byReverseLength);

        for (LineCluster cluster : clusters) {
            logger.debug("Expanding {}", cluster);

            // Expanding on left side
            expandCluster(cluster, stopFils);
            // Expanding on right side
            expandCluster(cluster, startFils);
        }
    }

    //--------------------//
    // followCombsNetwork //
    //--------------------//
    /**
     * Use the network of combs and filaments to interconnect filaments via common combs.
     */
    private void followCombsNetwork ()
    {
        logger.debug("Following combs network");

        for (StaffFilament fil : filaments) {
            Map<Integer, FilamentComb> combs = fil.getCombs();

            // Sequence of lines around the filament, indexed by relative pos
            Map<Integer, StaffFilament> lines = new TreeMap<>();

            // Loop on all combs this filament is involved in
            for (FilamentComb comb : combs.values()) {
                int posPivot = comb.getIndex(fil);

                for (int pos = 0; pos < comb.getCount(); pos++) {
                    int line = pos - posPivot;

                    if (line != 0) {
                        StaffFilament f = lines.get(line);

                        if (f != null) {
                            connectAncestors(f, comb.getFilament(pos));
                        } else {
                            lines.put(line, comb.getFilament(pos));
                        }
                    }
                }
            }
        }

        removeMergedFilaments();
    }

    //--------------//
    // isConsistent //
    //--------------//
    /**
     * Check whether the provided cluster has raw lines of rather similar length.
     * When this method is called, clusters have already been merged horizontally.
     *
     * @param cluster the cluster to check
     * @return true if OK
     */
    private boolean isConsistent (LineCluster cluster)
    {
        int minLg = Integer.MAX_VALUE;
        int maxLg = Integer.MIN_VALUE;

        for (StaffFilament sFil : cluster.getLines()) {
            int lg = sFil.getLength(HORIZONTAL);
            minLg = Math.min(minLg, lg);
            maxLg = Math.max(maxLg, lg);
        }

        final double meanLg = (minLg + maxLg) / 2.0;
        final double diffRatio = (maxLg - minLg) / meanLg;

        if (diffRatio > constants.maxClusterDiffLengthRatio.getValue()) {
            logger.debug("diff length ratio: {} for {}", diffRatio, cluster);

            return false;
        }

        return true;
    }

    //-------------------//
    // mergeClusterPairs //
    //-------------------//
    /**
     * Merge clusters horizontally or destroy short clusters.
     */
    private void mergeClusterPairs ()
    {
        if (clusters.isEmpty()) {
            return;
        }

        // Sort clusters according to their ordinate in page
        Collections.sort(clusters, byOrdinate);

        double minLength = computeAcceptableLength();
        WholeLoop:
        for (int idx = 0; idx < clusters.size();) {
            LineCluster cluster = clusters.get(idx);
            Rectangle clusterBox = cluster.getBounds();
            Point2D dskCenter = skew.deskewed(cluster.getCenter());
            double yMax = dskCenter.getY() + params.maxMergeCenterDy;

            for (LineCluster cl : clusters.subList(idx + 1, clusters.size())) {
                // Check dy
                if (skew.deskewed(cl.getCenter()).getY() > yMax) {
                    break;
                }

                // Check for blank space (2 systems side by side)
                Rectangle clBox = cl.getBounds();

                if (GeoUtil.xGap(clusterBox, clBox) > params.maxMergeDx) {
                    // Too wide horizontal gap, must be side by side
                    continue;
                }

                // Merge
                logger.info("Pairing clusters C{} & C{}", cluster.getId(), cl.getId());
                cluster.mergeWith(cl, 0);
                clusters.remove(cl);

                continue WholeLoop; // Recheck at same index
            }

            // Short isolated?
            if (cluster.getTrueLength() < minLength) {
                logger.info("Destroying spurious {}", cluster);
                clusters.remove(cluster);
            } else {
                idx++; // Move forward
            }
        }

        removeMergedFilaments();
    }

    //---------------//
    // mergeClusters //
    //---------------//
    /**
     * Merge compatible clusters as much as possible.
     */
    private void mergeClusters ()
    {
        // Sort clusters according to their ordinate in page
        Collections.sort(clusters, byOrdinate);

        for (LineCluster current : clusters) {
            LineCluster candidate = current;

            // Keep on working while we do have a candidate to check for merge
            CandidateLoop:
            while (true) {
                Wrapper<Integer> deltaPos = new Wrapper<>(null);
                Rectangle candidateBox = candidate.getBounds();
                candidateBox.grow(params.maxMergeDx, params.clusterYMargin);

                // Check the candidate vs all clusters until current excluded
                for (LineCluster head : clusters) {
                    if (head == current) {
                        break CandidateLoop; // Actual end of sub list
                    }

                    if ((head == candidate) || (head.getParent() != null)) {
                        continue;
                    }

                    // Check rough proximity
                    Rectangle headBox = head.getBounds();

                    if (headBox.intersects(candidateBox)) {
                        // Try a merge
                        if (canMerge(head, candidate, deltaPos)) {
                            logger.debug(
                                    "Merging {} with {} delta:{}",
                                    candidate,
                                    head,
                                    deltaPos.value);

                            // Do the merge
                            candidate.mergeWith(head, deltaPos.value);

                            break;
                        }
                    }
                }
            }
        }

        removeMergedClusters();
        removeMergedFilaments();
    }

    //------------//
    // ordinateOf //
    //------------//
    /**
     * Report the orthogonal distance of the provided point
     * to the sheet top edge tilted with global slope.
     */
    private Double ordinateOf (Point2D point)
    {
        if (point != null) {
            return sheet.getSkew().deskewed(point).getY();
        } else {
            return null;
        }
    }

    //------------//
    // ordinateOf //
    //------------//
    /**
     * Report the orthogonal distance of the cluster center
     * to the sheet top edge tilted with global slope.
     */
    private double ordinateOf (LineCluster cluster)
    {
        return ordinateOf(cluster.getCenter());
    }

    //-------------//
    // ordinatesOf //
    //-------------//
    private Double[] ordinatesOf (Collection<Point2D> points)
    {
        Double[] ys = new Double[points.size()];
        int index = 0;

        for (Point2D p : points) {
            ys[index++] = ordinateOf(p);
        }

        return ys;
    }

    //----------------------//
    // removeMergedClusters //
    //----------------------//
    private void removeMergedClusters ()
    {
        for (Iterator<LineCluster> it = clusters.iterator(); it.hasNext();) {
            LineCluster cluster = it.next();

            if (cluster.getParent() != null) {
                it.remove();
            }
        }
    }

    //-----------------------//
    // removeMergedFilaments //
    //-----------------------//
    private void removeMergedFilaments ()
    {
        for (Iterator<StaffFilament> it = filaments.iterator(); it.hasNext();) {
            StaffFilament fil = it.next();

            if (fil.getPartOf() != null) {
                it.remove();
            }
        }
    }

    //------------------//
    // retrieveClusters //
    //------------------//
    /**
     * Connect filaments via the combs they are involved in,
     * and come up with clusters of lines.
     *
     * @param checkConsistency true for checking consistency
     */
    private void retrieveClusters (boolean checkConsistency)
    {
        // Create clusters recursively out of filements
        createClusters();

        // Aggregate filaments left over when possible (first)
        expandClusters();

        // Merge clusters
        mergeClusters();

        // Trim clusters with too many lines
        trimClusters();

        // Discard non standard clusters
        destroyNonStandardClusters();

        // Merge clusters horizontally, when relevant
        mergeClusterPairs();

        // Discard clusters with inconsistent lines lengths
        if (checkConsistency) {
            destroyInconsistentClusters();
        }

        // Aggregate filaments left over when possible (second)
        expandClusters();

        // Discard non-clustered filaments
        discardNonClusteredFilaments();

        removeMergedFilaments();

        // Debug
        if (logger.isDebugEnabled()) {
            dumpClusters();
        }
    }

    //---------------//
    // retrieveCombs //
    //---------------//
    /**
     * Detect regular patterns of (staff) lines.
     * Use vertical sampling on regularly-spaced abscissae
     */
    private void retrieveCombs ()
    {
        /** Minimum acceptable delta y */
        final int dMin = interlineScale.min - params.combMinMargin;

        /** Maximum acceptable delta y */
        final int dMax = interlineScale.max + params.combMaxMargin;

        /** Number of vertical samples to collect */
        final int sampleCount = -1 + (int) Math.rint((double) pictureWidth / params.samplingDx);

        /** Exact columns abscissae */
        colX = new int[sampleCount + 1];

        /** Precise x interval */
        double samplingDx = (double) pictureWidth / (sampleCount + 1);

        for (int col = 1; col <= sampleCount; col++) {
            final List<FilamentComb> colList = new ArrayList<>();
            colCombs.put(col, colList);

            final int x = (int) Math.rint(samplingDx * col);
            colX[col] = x;

            // Retrieve Filaments with ordinate at x, sorted by increasing y
            List<FilY> filys = retrieveFilamentsAtX(x);

            // Second, check y deltas to detect combs
            FilamentComb comb = null;
            FilY prevFily = null;

            for (FilY fily : filys) {
                if (prevFily != null) {
                    final int dy = (int) Math.rint(fily.y - prevFily.y);

                    if ((dy >= dMin) && (dy <= dMax)) {
                        if (comb == null) {
                            // Start of a new comb
                            comb = new FilamentComb(col);
                            colList.add(comb);
                            comb.append(prevFily.filament, prevFily.y);

                            if (prevFily.filament.isVip()) {
                                logger.info("VIP created {} with {}", comb, prevFily.filament);
                            }
                        }

                        // Extend comb
                        comb.append(fily.filament, fily.y);

                        if (fily.filament.isVip()) {
                            logger.info("VIP appended {} to {}", fily.filament, comb);
                        }
                    } else {
                        // No comb active
                        comb = null;
                    }
                }

                prevFily = fily;
            }
        }
    }

    //----------------------//
    // retrieveFilamentsAtX //
    //----------------------//
    /**
     * For a given abscissa, retrieve the filaments that are intersected
     * by vertical x, and sort them according to their ordinate at x.
     *
     * @param x the desired abscissa
     * @return the sorted list of structures (Fil + Y), perhaps empty
     */
    private List<FilY> retrieveFilamentsAtX (double x)
    {
        List<FilY> list = new ArrayList<>();

        for (StaffFilament fil : filaments) {
            if ((x >= fil.getStartPoint().getX()) && (x <= fil.getStopPoint().getX())) {
                list.add(new FilY(fil, fil.getPositionAt(x, HORIZONTAL)));
            }
        }

        Collections.sort(list, FilY.byOrdinate);

        return list;
    }

    //    //---------------------//
    //    // retrievePopularSize //
    //    //---------------------//
    //    /**
    //     * Retrieve the most popular size (line count) among all combs.
    //     */
    //    private void retrievePopularSize ()
    //    {
    //        // Build histogram of combs lengths
    //        Histogram<Integer> histo = new Histogram<>();
    //
    //        for (List<FilamentComb> list : colCombs.values()) {
    //            for (FilamentComb comb : list) {
    //                histo.increaseCount(comb.getCount(), comb.getCount());
    //            }
    //        }
    //
    //        // Use the most popular length
    //        // Should be 4 for bass tab, 5 for standard notation, 6 for guitar tab
    //        //TODO: NO: simply pickup the most popular size WITHIN 4..6 !!! avoid 2!
    //        popSize = histo.getMaxBucket();
    //
    //        logger.debug("Popular line comb: {} histo:{}", popSize, histo.dataString());
    //    }
    //
    //--------------//
    // trimClusters //
    //--------------//
    private void trimClusters ()
    {
        Collections.sort(clusters, byOrdinate);

        // Trim clusters with too many lines
        for (LineCluster cluster : clusters) {
            cluster.trim(popSize);
        }
    }

    //-------------//
    // renderItems //
    //-------------//
    /**
     * Render the vertical combs of filaments
     *
     * @param g graphics context
     */
    void renderItems (Graphics2D g)
    {
        Color oldColor = g.getColor();
        g.setColor(combColor);

        for (Entry<Integer, List<FilamentComb>> entry : colCombs.entrySet()) {
            int col = entry.getKey();
            int x = colX[col];

            for (FilamentComb comb : entry.getValue()) {
                g.draw(new Line2D.Double(x, comb.getY(0), x, comb.getY(comb.getCount() - 1)));
            }
        }

        g.setColor(oldColor);
    }

    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {

        private final Scale.Fraction samplingDx = new Scale.Fraction(
                1,
                "Typical delta X between two vertical samplings");

        private final Scale.Fraction maxExpandDx = new Scale.Fraction(
                2,
                "Maximum dx to aggregate a filament to a cluster");

        private final Scale.Fraction maxExpandDy = new Scale.Fraction(
                0.175,
                "Maximum dy to aggregate a filament to a cluster");

        private final Scale.Fraction maxMergeDx = new Scale.Fraction(
                6,
                "Maximum dx to merge two clusters");

        private final Scale.Fraction maxMergeDy = new Scale.Fraction(
                0.4,
                "Maximum dy to merge two clusters");

        private final Scale.Fraction maxMergeCenterDy = new Scale.Fraction(
                1.0,
                "Maximum center dy to merge two clusters");

        private final Scale.Fraction clusterYMargin = new Scale.Fraction(
                2,
                "Rough margin around cluster ordinate");

        private final Scale.Fraction combMinMargin = new Scale.Fraction(
                0.0,
                "Comb margin below minimum interline (use with caution)");

        private final Scale.Fraction combMaxMargin = new Scale.Fraction(
                0.0,
                "Comb margin above maximum interline (use with caution)");

        private final Constant.Ratio minClusterLengthRatio = new Constant.Ratio(
                0.2,
                "Minimum cluster true length (as ratio of median true length)");

        private final Constant.Ratio maxClusterDiffLengthRatio = new Constant.Ratio(
                0.5,
                "Maximum ratio of difference in length within raw lines of a cluster");
    }

    //------//
    // FilY //
    //------//
    /**
     * Class meant to define an ordering relationship between filaments,
     * knowing their ordinate at a common abscissa value.
     */
    private static class FilY
    {

        public static final Comparator<FilY> byOrdinate = new Comparator<FilY>()
        {
            @Override
            public int compare (FilY f1,
                                FilY f2)
            {
                return Double.compare(f1.y, f2.y);
            }
        };

        final StaffFilament filament;

        final double y;

        FilY (StaffFilament filament,
              double y)
        {
            this.filament = filament;
            this.y = y;
        }

        @Override
        public String toString ()
        {
            return "{F" + filament.getId() + " y:" + y + "}";
        }
    }

    //------------//
    // Parameters //
    //------------//
    /**
     * Class {@code Parameters} gathers all constants related to
     * horizontal frames.
     */
    private static class Parameters
    {

        final int samplingDx;

        final int maxExpandDx;

        final int maxExpandDy;

        final int maxMergeDx;

        final int maxMergeDy;

        final int maxMergeCenterDy;

        final int clusterYMargin;

        final int combMinMargin;

        final int combMaxMargin;

        /**
         * Creates a new Parameters object.
         *
         * @param scale          the sheet global scaling factor
         * @param interlineScale the scaling for these clusters
         */
        Parameters (Scale scale,
                    InterlineScale interlineScale)
        {
            samplingDx = scale.toPixels(constants.samplingDx);
            maxExpandDx = scale.toPixels(constants.maxExpandDx);
            maxMergeDx = scale.toPixels(constants.maxMergeDx);

            // Specific interline scaling
            maxExpandDy = interlineScale.toPixels(constants.maxExpandDy);
            maxMergeDy = interlineScale.toPixels(constants.maxMergeDy);
            maxMergeCenterDy = interlineScale.toPixels(constants.maxMergeCenterDy);
            clusterYMargin = interlineScale.toPixels(constants.clusterYMargin);
            combMinMargin = interlineScale.toPixels(constants.combMinMargin);
            combMaxMargin = interlineScale.toPixels(constants.combMaxMargin);

            if (logger.isDebugEnabled()) {
                new Dumping().dump(this);
            }
        }
    }
}
