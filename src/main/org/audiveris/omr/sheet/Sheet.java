//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                           S h e e t                                            //
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
package org.audiveris.omr.sheet;

import org.audiveris.omr.OMR;
import org.audiveris.omr.classifier.Annotations;
import org.audiveris.omr.classifier.AnnotationsBuilder;
import org.audiveris.omr.classifier.SampleRepository;
import org.audiveris.omr.classifier.SampleSheet;
import org.audiveris.omr.glyph.Glyph;
import org.audiveris.omr.glyph.GlyphIndex;
import org.audiveris.omr.glyph.GlyphsModel;
import org.audiveris.omr.glyph.Shape;
import org.audiveris.omr.glyph.dynamic.FilamentIndex;
import org.audiveris.omr.glyph.ui.GlyphsController;
import org.audiveris.omr.glyph.ui.SymbolsEditor;
import org.audiveris.omr.image.ImageFormatException;
import org.audiveris.omr.lag.LagManager;
import org.audiveris.omr.lag.Lags;
import org.audiveris.omr.run.RunTable;
import org.audiveris.omr.score.Page;
import org.audiveris.omr.score.PageRef;
import org.audiveris.omr.score.Score;
import org.audiveris.omr.score.ScoreExporter;
import org.audiveris.omr.score.ScoreReduction;
import org.audiveris.omr.score.ui.BookPdfOutput;
import org.audiveris.omr.sheet.ui.BinarizationBoard;
import org.audiveris.omr.sheet.ui.PictureView;
import org.audiveris.omr.sheet.ui.PixelBoard;
import org.audiveris.omr.sheet.ui.SheetAssembly;
import org.audiveris.omr.sheet.ui.SheetTab;
import org.audiveris.omr.sheet.ui.StubsController;
import org.audiveris.omr.sig.InterIndex;
import org.audiveris.omr.sig.SIGraph;
import org.audiveris.omr.sig.inter.AbstractPitchedInter;
import org.audiveris.omr.sig.inter.Inter;
import org.audiveris.omr.sig.ui.InterController;
import org.audiveris.omr.step.Step;
import org.audiveris.omr.step.StepException;
import org.audiveris.omr.ui.BoardsPane;
import org.audiveris.omr.ui.Colors;
import org.audiveris.omr.ui.ErrorsEditor;
import org.audiveris.omr.ui.selection.LocationEvent;
import org.audiveris.omr.ui.selection.PixelEvent;
import org.audiveris.omr.ui.selection.SelectionService;
import org.audiveris.omr.ui.util.ItemRenderer;
import org.audiveris.omr.ui.util.WeakItemRenderer;
import org.audiveris.omr.util.Dumping;
import org.audiveris.omr.util.FileUtil;
import org.audiveris.omr.util.Jaxb;
import org.audiveris.omr.util.Navigable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.audiveris.omr.constant.Constant;
import org.audiveris.omr.constant.ConstantSet;
import org.audiveris.omr.util.IndentingXMLStreamWriter;

/**
 * Class {@code Sheet} corresponds to one image in a book image file.
 * <p>
 * If a movement break occurs in the middle of a sheet, this sheet will contain at least two pages,
 * but in most cases there is exactly one {@link Page} instance per Sheet instance.
 * <p>
 * Methods are organized as follows:
 * <dl>
 * <dt>Administration</dt>
 * <dd>
 * <ul>
 * <li>{@link #getId}</li>
 * <li>{@link #getStub}</li>
 * <li>{@link #getSheetFileName}</li>
 * <li>{@link #store}</li>
 * <li>{@link #unmarshal}</li>
 * <li>{@link #afterReload}</li>
 * <li>{@link #reset}</li>
 * <li>{@link #getLagManager}</li>
 * <li>{@link #getFilamentIndex}</li>
 * <li>{@link #getGlyphIndex}</li>
 * <li>{@link #getInterIndex}</li>
 * <li>{@link #getPersistentIdGenerator}</li>
 * </ul>
 * </dd>
 * <dt>Pages, Systems and Staves</dt>
 * <dd>
 * <ul>
 * <li>{@link #addPage}</li>
 * <li>{@link #getPages}</li>
 * <li>{@link #getLastPage}</li>
 * <li>{@link #getStaffManager}</li>
 * <li>{@link #getSystemManager}</li>
 * <li>{@link #getSystems}</li>
 * <li>{@link #dumpSystemInfos}</li>
 * </ul>
 * </dd>
 * <dt>Samples</dt>
 * <dd>
 * <ul>
 * <li>{@link #annotate()}</li>
 * <li>{@link #annotate(java.nio.file.Path)}</li>
 * <li>{@link #sample}</li>
 * </ul>
 * </dd>
 * <dt>Artifacts</dt>
 * <dd>
 * <ul>
 * <li>{@link #setImage}</li>
 * <li>{@link #hasPicture}</li>
 * <li>{@link #getPicture}</li>
 * <li>{@link #getHeight}</li>
 * <li>{@link #getWidth}</li>
 * <li>{@link #setScale}</li>
 * <li>{@link #getScale}</li>
 * <li>{@link #getInterline}</li>
 * <li>{@link #setSkew}</li>
 * <li>{@link #getSkew}</li>
 * <li>{@link #print}</li>
 * <li>{@link #export}</li>
 * <li>{@link #setSheetDelta}</li>
 * <li>{@link #getSheetDelta}</li>
 * </ul>
 * </dd>
 * <dt>UI</dt>
 * <dd>
 * <ul>
 * <li>{@link #getSymbolsEditor}</li>
 * <li>{@link #createBinaryView}</li>
 * <li>{@link #createPictureView}</li>
 * <li>{@link #displayDataTab}</li>
 * <li>{@link #displayMainTabs}</li>
 * <li>{@link #getErrorsEditor}</li>
 * <li>{@link #getGlyphsController}</li>
 * <li>{@link #getInterController}</li>
 * <li>{@link #getLocationService}</li>
 * <li>{@link #addItemRenderer}</li>
 * <li>{@link #renderItems}</li>
 * </ul>
 * </dd>
 * </dl>
 * The picture below represents the data model used for marshalling/unmarshalling a sheet to/from
 * a sheet#n.xml file within a book .omr file
 * <p>
 * Most entities are represented here. Some Inter instances are listed only via their containing
 * entity, such as tuplets in MeasureStack, slurs and lyrics in Part, ledgers and bars in Staff,
 * graceChords and restChords in Measure, wholeChord in Voice.
 * <p>
 * Once an instance of Sheet has been unmarshalled, transient members of some entities need to
 * be properly set. This is the purpose of the "afterReload()" methods which are called in a certain
 * order as mentioned by the "(ar #n)" indications on these entities.
 * <p>
 * <img alt="Sheet Binding" src="doc-files/SheetBinding.png">
 *
 * @author Hervé Bitteur
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "sheet")
public class Sheet
{

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(Sheet.class);

    /** The radix used for folder of this sheet internals. */
    public static final String INTERNALS_RADIX = "sheet#";

    /** Events that can be published on sheet location service. */
    private static final Class<?>[] allowedEvents = new Class<?>[]{
        LocationEvent.class,
        PixelEvent.class};

    /** Un/marshalling context for use with JAXB. */
    private static volatile JAXBContext jaxbContext;

    // Persistent data
    //----------------
    //
    /** Global id used to uniquely identify a persistent entity instance. */
    @XmlAttribute(name = "last-persistent-id")
    @XmlJavaTypeAdapter(Jaxb.AtomicIntegerAdapter.class)
    private final AtomicInteger lastPersistentId = new AtomicInteger(0);

    /** The related picture. */
    @XmlElement(name = "picture")
    private Picture picture;

    /** Global scale for this sheet. */
    @XmlElement(name = "scale")
    private Scale scale;

    /** Global skew. */
    @XmlElement(name = "skew")
    private Skew skew;

    /** Corresponding page(s). A single sheet may relate to several pages. */
    @XmlElement(name = "page")
    private final List<Page> pages = new ArrayList<>();

    /**
     * Global glyph index.
     * See annotated get/set methods: {@link #getGlyphIndexContent()}
     */
    private GlyphIndex glyphIndex;

    // Transient data
    //---------------
    //
    /** Corresponding sheet stub. */
    @Navigable(false)
    private SheetStub stub;

    /** Inter index for all systems in this sheet. */
    private InterIndex interIndex;

    /** Staves. */
    private StaffManager staffManager;

    /** Systems management. */
    private SystemManager systemManager;

    /** Dictionary of sheet lags. */
    private LagManager lagManager;

    //-- UI ----------------------------------------------------------------------------------------
    //
    /** Selections for this sheet. (SheetLocation, PixelLevel) */
    private SelectionService locationService;

    /** Registered item renderers, if any. */
    private Set<ItemRenderer> itemRenderers;

    /** Related errors editor, if any. */
    private ErrorsEditor errorsEditor;

    /** Specific builder dealing with glyphs. */
    private volatile GlyphsController glyphsController;

    /** Specific UI manager dealing with inters. */
    private volatile InterController interController;

    /** Related symbols editor, if any. */
    private SymbolsEditor symbolsEditor;

    //-- resettable members ------------------------------------------------------------------------
    //
    /** Global filaments index. */
    private FilamentIndex filamentIndex;

    /** Delta measurements. */
    private SheetDiff sheetDelta;

    /**
     * Creates a new {@code Sheet} object with a binary table.
     *
     * @param stub        the related sheet stub
     * @param binaryTable the binary table, if any
     */
    public Sheet (SheetStub stub,
                  RunTable binaryTable)
    {
        this(stub);

        if (binaryTable != null) {
            setBinary(binaryTable);
        }
    }

    /**
     * Create a new {@code Sheet} instance with an image.
     *
     * @param stub  the related sheet stub
     * @param image the already loaded image, if any
     * @throws StepException if processing failed at this step
     */
    public Sheet (SheetStub stub,
                  BufferedImage image)
            throws StepException
    {
        this(stub);

        if (image != null) {
            setImage(image);
        }
    }

    /**
     * Create a new {@code Sheet} instance within a book.
     *
     * @param stub the related sheet stub
     */
    private Sheet (SheetStub stub)
    {
        Objects.requireNonNull(stub, "Cannot create a sheet in a null stub");

        glyphIndex = new GlyphIndex();

        initTransients(stub);

        interIndex = new InterIndex();
        interIndex.initTransients(this);
    }

    /**
     * No-arg constructor needed for JAXB.
     */
    private Sheet ()
    {
    }

    //-----------------//
    // addItemRenderer //
    //-----------------//
    /**
     * In non batch mode, register a class instance to render items on top of UI views.
     *
     * @param renderer an item renderer
     * @return true if renderer was added, false in batch
     */
    public boolean addItemRenderer (ItemRenderer renderer)
    {
        if ((renderer != null) && (OMR.gui != null)) {
            return itemRenderers.add(new WeakItemRenderer(renderer));

            ///return itemRenderers.add(renderer);
        }

        return false;
    }

    //---------//
    // addPage //
    //---------//
    /**
     * Add a related page to this sheet.
     *
     * @param page the detected page
     */
    public void addPage (Page page)
    {
        pages.add(page);
    }

    //-------------//
    // afterReload //
    //-------------//
    /**
     * Complete sheet initialization, after reload.
     *
     * @param stub the sheet stub
     */
    public void afterReload (SheetStub stub)
    {
        try {
            // Predefined StaffHolder's are no longer useful
            Staff.StaffHolder.clearStaffHolders();

            // Complete sheet initialization
            initTransients(stub);

            // Make sure hLag & vLag are available and their sections dispatched to relevant systems
            if (stub.isDone(Step.GRID)) {
                systemManager.dispatchHorizontalSections();
                systemManager.dispatchVerticalSections();
            }

            // Complete inters index
            interIndex = new InterIndex();
            interIndex.initTransients(this);

            for (SystemInfo system : getSystems()) {
                // Forward reload request down system hierarchy
                system.afterReload();
            }
        } catch (Exception ex) {
            logger.warn("Error in " + getClass() + " afterReload() " + ex, ex);
        }
    }

    //----------//
    // annotate //
    //----------//
    /**
     * Save sheet symbols annotations.
     */
    public void annotate ()
    {
        try {
            final Book book = stub.getBook();
            final Path bookFolder = BookManager.getDefaultBookFolder(book);
            annotate(bookFolder);
        } catch (Exception ex) {
            logger.warn("Annotations failed {}", ex);
        }
    }

    //----------//
    // annotate //
    //----------//
    /**
     * Save sheet symbols annotations into the provided folder.
     *
     * @param sheetFolder target folder (perhaps in a zip file system)
     */
    public void annotate (Path sheetFolder)
    {
        OutputStream os = null;

        try {
            // Sheet annotations
            Path annPath = sheetFolder.resolve(getId() + Annotations.SHEET_ANNOTATIONS_EXTENSION);
            new AnnotationsBuilder(this, annPath).processSheet();

            // Sheet image
            Path imgPath = sheetFolder.resolve(getId() + Annotations.SHEET_IMAGE_EXTENSION);
            RunTable runTable = picture.getTable(Picture.TableKey.BINARY);
            BufferedImage img = runTable.getBufferedImage();
            os = Files.newOutputStream(imgPath, CREATE);
            ImageIO.write(img, Annotations.SHEET_IMAGE_FORMAT, os);
        } catch (IOException |
                 JAXBException ex) {
            logger.warn("Error annotating {} {}", stub, ex.toString(), ex);
        } finally {
            if (os != null) {
                try {
                    os.flush();
                    os.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    //------------------//
    // createBinaryView //
    //------------------//
    /**
     * Create and display the binary view.
     */
    public void createBinaryView ()
    {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(new Runnable()
                {
                    @Override
                    public void run ()
                    {
                        createBinaryView();
                    }
                });
            } catch (InterruptedException |
                     InvocationTargetException ex) {
                logger.warn("invokeAndWait error", ex);
            }
        } else {
            final SheetAssembly assembly = stub.getAssembly();
            final SheetTab tab = SheetTab.BINARY_TAB;

            if (assembly.getPane(tab.label) == null) {
                locationService.subscribeStrongly(LocationEvent.class, picture);

                // Display sheet binary
                PictureView pictureView = new PictureView(this);
                assembly.addViewTab(
                        tab,
                        pictureView,
                        new BoardsPane(new PixelBoard(this), new BinarizationBoard(this)));
            } else {
                assembly.selectViewTab(tab);
            }
        }
    }

    //-------------------//
    // createPictureView //
    //-------------------//
    /**
     * Create and display the picture view.
     */
    public void createPictureView ()
    {
        locationService.subscribeStrongly(LocationEvent.class, picture);

        // Display sheet picture
        PictureView pictureView = new PictureView(this);
        stub.getAssembly().addViewTab(
                SheetTab.PICTURE_TAB,
                pictureView,
                new BoardsPane(new PixelBoard(this), new BinarizationBoard(this)));
    }

    //----------------//
    // displayDataTab //
    //----------------//
    /**
     * Display the DATA_TAB.
     */
    public void displayDataTab ()
    {
        try {
            getSymbolsEditor();
            stub.getAssembly().selectViewTab(SheetTab.DATA_TAB);
        } catch (Throwable ex) {
            logger.warn("Error in displayDataTab " + ex, ex);
        }
    }

    //-----------------//
    // displayMainTabs //
    //-----------------//
    /**
     * Display the main tabs related to this sheet.
     */
    public void displayMainTabs ()
    {
        if (stub.isDone(Step.GRID)) {
            displayDataTab(); // Display DATA tab
        } else if (stub.isDone(Step.BINARY)) {
            createBinaryView(); // Display BINARY tab
        } else {
            createPictureView(); // Display Picture tab
        }

        if (!stub.isValid()) {
            StubsController.getInstance().markTab(stub, Colors.SHEET_INVALID);
        }
    }

    //-----------------//
    // dumpSystemInfos //
    //-----------------//
    /**
     * Utility method, to dump all sheet systems
     */
    public void dumpSystemInfos ()
    {
        System.out.println("--- SystemInfos ---");

        int i = 0;

        for (SystemInfo system : getSystems()) {
            new Dumping().dump(system, "#" + i++);
        }

        System.out.println("--- SystemInfos end ---");
    }

    //--------//
    // export //
    //--------//
    /**
     * Export a single sheet in MusicXML.
     * <p>
     * The output is structured differently according to whether the sheet contains one or several
     * pages.
     * <ul>
     * <li>A single-page sheet results in one score output.</li>
     * <li>A multi-page sheet results in one opus output (if useOpus is set) or a series of scores
     * (is useOpus is not set).</li>
     * </ul>
     *
     * @param path sheet export path
     */
    public void export (Path path)
    {
        if (pages.isEmpty()) {
            return;
        }

        final Book book = getBook();

        try {
            Path folder = path.getParent();

            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }

            final String ext = FileUtil.getExtension(path);
            final String sheetName = FileUtil.getNameSansExtension(path.getFileName());
            final boolean compressed = (ext.equals(OMR.COMPRESSED_SCORE_EXTENSION)) ? true
                    : ((ext.equals(OMR.SCORE_EXTENSION)) ? false : BookManager.useCompression());
            final boolean useSig = BookManager.useSignature();

            int modifs = 0; // Count of modifications

            if (pages.size() > 1) {
                // One file per page
                for (PageRef pageRef : stub.getPageRefs()) {
                    final Score score = new Score();
                    score.setBook(book);
                    score.addPageRef(stub.getNumber(), pageRef);
                    modifs += new ScoreReduction(score).reduce();

                    final int idx = pageRef.getId();
                    final String scoreName = sheetName + OMR.MOVEMENT_EXTENSION + idx;
                    final Path scorePath = path.resolveSibling(scoreName + ext);
                    new ScoreExporter(score).export(scorePath, sheetName, useSig, compressed);
                }
            } else {
                // Export the sheet single page as a score
                final Score score = new Score();
                score.setBook(book);
                score.addPageRef(stub.getNumber(), stub.getFirstPageRef());
                modifs += new ScoreReduction(score).reduce();

                final String scoreName = sheetName;
                final Path scorePath = path.resolveSibling(scoreName + ext);
                new ScoreExporter(score).export(scorePath, scoreName, useSig, compressed);
            }

            if (modifs > 0) {
                book.setModified(true);
            }

            // Remember the book export path in the book itself
            book.setExportPathSansExt(folder.resolve(book.getRadix()));
        } catch (Exception ex) {
            logger.warn("Error exporting " + this + ", " + ex, ex);
        }
    }

    //-----------------//
    // getErrorsEditor //
    //-----------------//
    /**
     * In non batch mode, report the editor dealing with detected errors in this sheet.
     *
     * @return the errors editor, or null
     */
    public ErrorsEditor getErrorsEditor ()
    {
        return errorsEditor;
    }

    //------------------//
    // getFilamentIndex //
    //------------------//
    /**
     * Report the global index for filaments of this sheet, or null.
     *
     * @return the index for filaments, perhaps null
     */
    public FilamentIndex getFilamentIndex ()
    {
        if (filamentIndex == null) {
            filamentIndex = new FilamentIndex(this);
        }

        return filamentIndex;
    }

    //---------------//
    // getGlyphIndex //
    //---------------//
    /**
     * Report the global index for glyphs of this sheet, or null.
     *
     * @return the nest for glyphs, perhaps null
     */
    public GlyphIndex getGlyphIndex ()
    {
        if (glyphIndex == null) {
            glyphIndex = new GlyphIndex();
        }

        return glyphIndex;
    }

    //---------------------//
    // getGlyphsController //
    //---------------------//
    /**
     * In non batch mode, report the UI module for symbol assignment in this sheet
     *
     * @return the glyphs controller
     */
    public GlyphsController getGlyphsController ()
    {
        if (glyphsController == null) {
            createGlyphsController();
        }

        return glyphsController;
    }

    //-----------//
    // getHeight //
    //-----------//
    /**
     * Report the picture height in pixels
     *
     * @return the picture height
     */
    public int getHeight ()
    {
        return picture.getHeight();
    }

    //-------//
    // getId //
    //-------//
    /**
     * Report the distinguished name for this sheet.
     *
     * @return sheet name
     */
    public String getId ()
    {
        if (stub != null) {
            return stub.getId();
        }

        return null;
    }

    //--------------------//
    // getInterController //
    //--------------------//
    /**
     * In non batch mode, report the UI module for inter management in this sheet
     *
     * @return the inter controller
     */
    public InterController getInterController ()
    {
        if (interController == null) {
            interController = new InterController(this);
        }

        return interController;
    }

    //---------------//
    // getInterIndex //
    //---------------//
    /**
     * Report the global index for inters in this sheet
     *
     * @return the sheet Inter index
     */
    public InterIndex getInterIndex ()
    {
        return interIndex;
    }

    //--------------//
    // getInterline //
    //--------------//
    /**
     * Convenient method to report the key scaling information of the sheet
     *
     * @return the scale interline value
     */
    public int getInterline ()
    {
        return scale.getInterline();
    }

    //---------------//
    // getLagManager //
    //---------------//
    /**
     * Access to the lag manager for this sheet
     *
     * @return the lag Manager
     */
    public LagManager getLagManager ()
    {
        return lagManager;
    }

    //-------------//
    // getLastPage //
    //-------------//
    /**
     * Report the last page of the sheet, if any.
     *
     * @return the last page or null
     */
    public Page getLastPage ()
    {
        if (pages.isEmpty()) {
            return null;
        }

        return pages.get(pages.size() - 1);
    }

    //--------------------//
    // getLocationService //
    //--------------------//
    /**
     * In non batch mode, give access to sheet location service.
     *
     * @return the selection service dedicated to location in sheet (null in batch mode)
     */
    public SelectionService getLocationService ()
    {
        return locationService;
    }

    //----------//
    // getPages //
    //----------//
    /**
     * Report the sequence of pages found in this sheet (generally just one).
     *
     * @return the list of page(s)
     */
    public List<Page> getPages ()
    {
        return pages;
    }

    //--------------------------//
    // getPersistentIdGenerator //
    //--------------------------//
    /**
     * Access to the generator of persistent IDs for this sheet.
     *
     * @return the ID generator
     */
    public AtomicInteger getPersistentIdGenerator ()
    {
        return lastPersistentId;
    }

    //------------//
    // getPicture //
    //------------//
    /**
     * Report the picture of this sheet, that provides sources and tables.
     *
     * @return the related picture
     */
    public Picture getPicture ()
    {
        if (picture == null) {
            BufferedImage img = getBook().loadSheetImage(stub.getNumber());

            try {
                setImage(img);
            } catch (StepException ex) {
                logger.warn("Error setting image id {}", stub.getNumber(), ex);
            }
        }

        return picture;
    }

    //----------//
    // getScale //
    //----------//
    /**
     * Report the computed scale of this sheet.
     * This drives several processing thresholds.
     *
     * @return the sheet scale
     */
    public Scale getScale ()
    {
        return scale;
    }

    //----------//
    // setScale //
    //----------//
    /**
     * Remember scale information to this sheet
     *
     * @param scale the computed sheet global scale
     */
    public void setScale (Scale scale)
    {
        this.scale = scale;
    }

    //----------//
    // setImage //
    //----------//
    /**
     * Assign the related image to this sheet
     *
     * @param image the loaded image
     * @throws StepException if processing failed at this step
     */
    public final void setImage (BufferedImage image)
            throws StepException
    {
        try {
            picture = new Picture(this, image, locationService);

            if (OMR.gui != null) {
                createPictureView();
            }

            done(Step.LOAD);
        } catch (ImageFormatException ex) {
            String msg = "Unsupported image format in file " + stub.getBook().getInputPath()
                                 + "\n"
                                 + ex.getMessage();

            if (OMR.gui != null) {
                OMR.gui.displayWarning(msg);
            } else {
                logger.warn(msg);
            }

            throw new StepException(ex);
        } catch (Throwable ex) {
            logger.warn("Error loading image", ex);
        }
    }

    //---------------//
    // getSheetDelta //
    //---------------//
    /**
     * Report the measured difference between entities and pixels.
     *
     * @return the sheetDelta
     */
    public SheetDiff getSheetDelta ()
    {
        return sheetDelta;
    }

    //---------------//
    // setSheetDelta //
    //---------------//
    /**
     * Remember the sheet delta in sheet
     *
     * @param sheetDelta difference between input (pixels) and output (recognized entities)
     */
    public void setSheetDelta (SheetDiff sheetDelta)
    {
        this.sheetDelta = sheetDelta;
    }

    //---------//
    // getSkew //
    //---------//
    /**
     * Report the skew information for this sheet.
     *
     * @return the skew information
     */
    public Skew getSkew ()
    {
        return skew;
    }

    //---------//
    // setSkew //
    //---------//
    /**
     * Link skew information to this sheet
     *
     * @param skew the skew information
     */
    public void setSkew (Skew skew)
    {
        this.skew = skew;
    }

    //-----------------//
    // getStaffManager //
    //-----------------//
    /**
     * Access to the staff manager for this sheet
     *
     * @return the staff Manager
     */
    public StaffManager getStaffManager ()
    {
        return staffManager;
    }

    //---------//
    // getStub //
    //---------//
    /**
     * Report the related sheet stub.
     *
     * @return the related stub (non null)
     */
    public SheetStub getStub ()
    {
        return stub;
    }

    //------------------//
    // getSymbolsEditor //
    //------------------//
    /**
     * In non batch mode, report the editor dealing with symbols recognition in this sheet
     *
     * @return the symbols editor, or null
     */
    public SymbolsEditor getSymbolsEditor ()
    {
        if (symbolsEditor == null) {
            interController = new InterController(this);
            symbolsEditor = new SymbolsEditor(this, getGlyphsController(), interController);
            interController.setSymbolsEditor(symbolsEditor);
        }

        return symbolsEditor;
    }

    //------------------//
    // getSystemManager //
    //------------------//
    /**
     * Access to the system manager for this sheet
     *
     * @return the SystemManager instance
     */
    public SystemManager getSystemManager ()
    {
        return systemManager;
    }

    //------------//
    // getSystems //
    //------------//
    /**
     * Convenient way to get an unmodifiable view on sheet systems.
     *
     * @return a view on systems list
     */
    public List<SystemInfo> getSystems ()
    {
        return systemManager.getSystems();
    }

    //----------//
    // getWidth //
    //----------//
    /**
     * Report the picture width in pixels
     *
     * @return the picture width
     */
    public int getWidth ()
    {
        return picture.getWidth();
    }

    //------------//
    // hasPicture //
    //------------//
    /**
     * Report whether the Picture instance exists in sheet.
     *
     * @return true if so
     */
    public boolean hasPicture ()
    {
        return picture != null;
    }

    //-------//
    // print //
    //-------//
    /**
     * Print the sheet physical appearance using PDF format.
     *
     * @param sheetPrintPath path of sheet print file
     */
    public void print (Path sheetPrintPath)
    {
        // Actually write the PDF
        try {
            Path parent = sheetPrintPath.getParent();

            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            new BookPdfOutput(getBook(), sheetPrintPath.toFile()).write(this);
            logger.info("Sheet printed to {}", sheetPrintPath);
        } catch (Exception ex) {
            logger.warn("Cannot print sheet to " + sheetPrintPath + " " + ex, ex);
        }
    }

    //-------------//
    // renderItems //
    //-------------//
    /**
     * In non batch mode, apply the registered item renderings on the provided graphics.
     *
     * @param g the graphics context
     */
    public void renderItems (Graphics2D g)
    {
        if (OMR.gui != null) {
            for (ItemRenderer renderer : itemRenderers) {
                renderer.renderItems(g);
            }
        }
    }

    //--------//
    // sample //
    //--------//
    /**
     * Save sheet samples into book repository.
     */
    public void sample ()
    {
        final Book book = getBook();
        final SampleRepository repository = book.getSpecificSampleRepository();
        final SampleSheet sampleSheet = repository.findSampleSheet(this);

        for (SystemInfo system : getSystems()) {
            SIGraph sig = system.getSig();

            for (Inter inter : sig.vertexSet()) {
                Shape shape = inter.getShape();
                Staff staff = inter.getStaff();
                Glyph glyph = inter.getGlyph();

                if ((shape != null) && (staff != null) && (glyph != null)) {
                    Double pitch = (inter instanceof AbstractPitchedInter)
                            ? ((AbstractPitchedInter) inter).getPitch() : null;
                    repository.addSample(
                            inter.getShape(),
                            glyph,
                            staff.getSpecificInterline(),
                            sampleSheet,
                            pitch);
                } else {
                    logger.debug(
                            "No sample for {} shape:{} staff:{} glyph:{}",
                            inter,
                            shape,
                            staff,
                            glyph);
                }
            }
        }
    }

    //-------//
    // store //
    //-------//
    /**
     * Store sheet internals into book file system.
     *
     * @param sheetFolder    path of sheet folder in (new) book file
     * @param oldSheetFolder path of sheet folder in old book file, if any
     */
    public void store (Path sheetFolder,
                       Path oldSheetFolder)
    {
        // Picture internals, if any
        if (picture != null) {
            try {
                // Make sure the folder exists for sheet internals
                Files.createDirectories(sheetFolder);

                // Save picture tables
                picture.store(sheetFolder, oldSheetFolder);
            } catch (IOException ex) {
                logger.warn("IOException on storing " + this, ex);
            }
        }

        // Sheet structure (sheet#n.xml)
        try {
            Path structurePath = sheetFolder.resolve(getSheetFileName(stub.getNumber()));
            Files.deleteIfExists(structurePath);
            Files.createDirectories(sheetFolder);

            try (OutputStream os = Files.newOutputStream(structurePath, CREATE);) {
                Marshaller m = getJaxbContext().createMarshaller();
                XMLStreamWriter writer = new IndentingXMLStreamWriter(
                        XMLOutputFactory.newInstance().createXMLStreamWriter(os, "UTF-8"));

                if (constants.useMarshalLogger.isSet()) {
                    m.setListener(new Jaxb.MarshalLogger());
                }

                m.marshal(this, writer);
                os.flush();
            }

            stub.setModified(false);
            stub.setUpgraded(false);
            logger.info("Stored {}", structurePath);
        } catch (IOException |
                 JAXBException |
                 XMLStreamException ex) {
            logger.warn("Error in saving sheet structure " + ex, ex);
        }
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder("Sheet{");

        if (getId() != null) {
            sb.append(getId());
        }

        sb.append('}');

        return sb.toString();
    }

    //------------------------//
    // createGlyphsController //
    //------------------------//
    private void createGlyphsController ()
    {
        GlyphsModel model = new GlyphsModel(this, getGlyphIndex().getEntityService());
        glyphsController = new GlyphsController(model);
    }

    //------//
    // done //
    //------//
    /**
     * Remember that the provided step has been completed on the sheet.
     *
     * @param step the provided step
     */
    private void done (Step step)
    {
        stub.done(step);
    }

    //---------//
    // getBook //
    //---------//
    private Book getBook ()
    {
        return stub.getBook();
    }

    //----------------------//
    // getGlyphIndexContent // Needed for JAXB
    //----------------------//
    /**
     * Mean for JAXB marshalling only.
     *
     * @return collection of glyphs from glyphIndex.weakIndex
     */
    @SuppressWarnings("unchecked")
    @XmlElement(name = "glyph-index")
    @XmlJavaTypeAdapter(GlyphListAdapter.class)
    private ArrayList<Glyph> getGlyphIndexContent ()
    {
        if (glyphIndex == null) {
            return null;
        }

        return glyphIndex.getEntities();
    }

    //----------------------//
    // setGlyphIndexContent // Needed for JAXB
    //----------------------//
    /**
     * Meant for JAXB unmarshalling only.
     *
     * @param glyphs collection of glyphs to feed to the glyphIndex.weakIndex
     */
    @SuppressWarnings("unchecked")
    private void setGlyphIndexContent (ArrayList<Glyph> glyphs)
    {
        getGlyphIndex().setEntities(glyphs);
    }

    //-----------//
    // getNumber // Needed for JAXB
    //-----------//
    /**
     * Sheet 1-based number within book.
     */
    @XmlAttribute(name = "number")
    private int getNumber ()
    {
        return stub.getNumber();
    }

    //----------------//
    // initTransients //
    //----------------//
    /**
     * Initialize needed transient members.
     * (which by definition have not been set by the unmarshalling).
     *
     * @param stub the related stub
     */
    private void initTransients (SheetStub stub)
    {
        logger.debug("Sheet#{} initTransients", stub.getNumber());

        this.stub = stub;

        // Update UI information if so needed
        if (OMR.gui != null) {
            locationService = new SelectionService("locationService", allowedEvents);
            errorsEditor = new ErrorsEditor(this);
            itemRenderers = new LinkedHashSet<>();
            addItemRenderer(staffManager);
        }

        if (picture != null) {
            picture.initTransients(this);
        }

        if (glyphIndex != null) {
            glyphIndex.initTransients(this);
        }

        for (Page page : pages) {
            page.initTransients(this);
        }

        if (systemManager == null) {
            systemManager = new SystemManager(this);
        } else {
            systemManager.initTransients(this);
        }

        // systemManager
        List<SystemInfo> systems = new ArrayList<>();

        for (Page page : pages) {
            for (SystemInfo system : page.getSystems()) {
                system.initTransients(this, page);
                systems.add(system);

                List<Staff> systemStaves = new ArrayList<>();

                for (Part part : system.getParts()) {
                    part.setSystem(system);

                    for (Staff staff : part.getStaves()) {
                        staff.setPart(part);
                        systemStaves.add(staff);
                    }
                }

                system.setStaves(systemStaves);
            }
        }

        systemManager.setSystems(systems);

        staffManager = new StaffManager(this);

        lagManager = new LagManager(this);
    }

    //-----------//
    // setBinary //
    //-----------//
    private void setBinary (RunTable binaryTable)
    {
        try {
            picture = new Picture(this, binaryTable);

            if (OMR.gui != null) {
                createBinaryView();
            }

            done(Step.LOAD);
            done(Step.BINARY);
        } finally {
        }
    }

    //-------//
    // reset //
    //-------//
    /**
     * Reinitialize the sheet members, according to step needs.
     *
     * @param step the starting step
     */
    void reset (Step step)
    {
        switch (step) {
        default:
            break;

        case LOAD:
            picture = null;

        // Fall-through!
        case BINARY:
            scale = null;

        // Fall-through!
        case SCALE:
        case GRID:
            pages.clear();
            stub.clearPageRefs();
            skew = null;

            lagManager.setLag(Lags.HLAG, null);
            lagManager.setLag(Lags.VLAG, null);

            staffManager.reset();
            systemManager.reset();
            glyphsController = null;
            symbolsEditor = null;
        }

        // Clear errors and history for this step
        if (OMR.gui != null) {
            getErrorsEditor().clearStep(step);

            if (interController != null) {
                SwingUtilities.invokeLater(new Runnable()
                {
                    // This part is run on swing thread
                    @Override
                    public void run ()
                    {
                        interController.clearHistory();
                    }
                });
            }
        }
    }

    //------------------//
    // getSheetFileName //
    //------------------//
    /**
     * Report the file name of a sheet in the .omr zip file system.
     *
     * @param number sheet number (counted from 1) within the containing book
     * @return the sheet file name
     */
    public static String getSheetFileName (int number)
    {
        return Sheet.INTERNALS_RADIX + number + ".xml";
    }

    //-----------//
    // unmarshal //
    //-----------//
    /**
     * Unmarshal the provided XML stream to allocate the corresponding sheet.
     *
     * @param in the input stream that contains the sheet in XML format.
     *           The stream is not closed by this method
     * @return the allocated sheet.
     * @exception JAXBException raised when unmarshalling goes wrong
     */
    public static Sheet unmarshal (InputStream in)
            throws JAXBException
    {
        Unmarshaller um = getJaxbContext().createUnmarshaller();

        if (constants.useUnmarshalLogger.isSet()) {
            um.setListener(new Jaxb.UnmarshalLogger());
        }

        Sheet sheet = (Sheet) um.unmarshal(in);
        logger.debug("Sheet unmarshalled");

        return sheet;
    }

    //----------------//
    // getJaxbContext //
    //----------------//
    private static JAXBContext getJaxbContext ()
            throws JAXBException
    {
        // Lazy creation
        if (jaxbContext == null) {
            jaxbContext = JAXBContext.newInstance(Sheet.class);
        }

        return jaxbContext;
    }

    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {

        private final Constant.Boolean useMarshalLogger = new Constant.Boolean(
                false,
                "Should we log every sheet marshalling?");

        private final Constant.Boolean useUnmarshalLogger = new Constant.Boolean(
                false,
                "Should we log every sheet unmarshalling?");
    }

    //-----------//
    // GlyphList // For glyphIndex (un)marshalling
    //-----------//
    private static class GlyphList
    {

        @XmlElement(name = "glyph")
        public ArrayList<Glyph> glyphs;

        GlyphList ()
        {
        }

        GlyphList (ArrayList<Glyph> glyphs)
        {
            this.glyphs = glyphs;
        }

        @Override
        public String toString ()
        {
            final StringBuilder sb = new StringBuilder("GlyphList{");

            if (glyphs != null) {
                sb.append("size:").append(glyphs.size());
            }

            sb.append('}');

            return sb.toString();
        }
    }

    //------------------//
    // GlyphListAdapter // For glyphIndex (un)marshalling
    //------------------//
    private static class GlyphListAdapter
            extends XmlAdapter<GlyphList, ArrayList<Glyph>>
    {

        @Override
        public GlyphList marshal (ArrayList<Glyph> glyphs)
                throws Exception
        {
            return new GlyphList(glyphs);
        }

        @Override
        public ArrayList<Glyph> unmarshal (GlyphList list)
                throws Exception
        {
            return list.glyphs;
        }
    }
}
