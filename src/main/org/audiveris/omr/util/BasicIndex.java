//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                       B a s i c I n d e x                                      //
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
package org.audiveris.omr.util;

import org.audiveris.omr.glyph.Glyph;
import org.audiveris.omr.ui.selection.EntityService;
import org.audiveris.omr.ui.symbol.BasicSymbol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Class {@code BasicIndex}
 *
 * @param <E> precise type for indexed entities
 * @author HervÃ© Bitteur
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement
@XmlType(propOrder = {"lastIdValue", "entities"})
public class BasicIndex<E extends Entity>
        implements EntityIndex<E>
{

    private static final Logger logger = LoggerFactory.getLogger(BasicIndex.class);

    // Persistent data
    //----------------
    //
    /** Collection of all entities registered in this index, sorted on ID. */
    @XmlElement(name = "entities")
    @XmlJavaTypeAdapter(Adapter.class)
    protected final ConcurrentSkipListMap<Integer, E> entities = new ConcurrentSkipListMap<>();

    // Transient data
    //---------------
    //
    /** Global id used to uniquely identify an entity instance. */
    protected AtomicInteger lastId;

    /** Selection service, if any. */
    protected EntityService<E> entityService;

    /** List of IDs for declared VIP entities. */
    private List<Integer> vipIds;

    /** (debug) for easy inspection via browser. */
    private Collection<E> values;

    /**
     * Creates a new {@code BasicIndex} object.
     *
     * @param lastId provided ID generator, perhaps null
     */
    public BasicIndex (AtomicInteger lastId)
    {
        this();
        this.lastId = lastId;
    }

    /**
     * Creates a new {@code BasicIndex} object.
     */
    protected BasicIndex ()
    {
        values = entities.values(); // Useful for debugging only
    }

    //----------------------//
    // getContainedEntities //
    //----------------------//
    @Override
    public List<E> getContainedEntities (Rectangle rectangle)
    {
        return Entities.containedEntities(iterator(), rectangle);
    }

    //-----------------------//
    // getContainingEntities //
    //-----------------------//
    @Override
    public List<E> getContainingEntities (Point point)
    {
        return Entities.containingEntities(iterator(), point);
    }

    //-------------//
    // getEntities //
    //-------------//
    @Override
    public Collection<E> getEntities ()
    {
        return Collections.unmodifiableCollection(entities.values());
    }

    //-----------//
    // getEntity //
    //-----------//
    @Override
    public E getEntity (int id)
    {
        return entities.get(id);
    }

    //------------------//
    // getEntityService //
    //------------------//
    @Override
    public EntityService<E> getEntityService ()
    {
        return entityService;
    }

    //------------------//
    // setEntityService //
    //------------------//
    @Override
    public void setEntityService (EntityService<E> entityService)
    {
        this.entityService = entityService;
        entityService.connect();
    }

    //------------//
    // getIdAfter //
    //------------//
    @Override
    public int getIdAfter (int id)
    {
        if (id == 0) {
            if (!entities.isEmpty()) {
                for (int i = 0, iMax = lastId.get(); i <= iMax; i++) {
                    final E entity = entities.get(i);

                    if (isValid(entity)) {
                        return i;
                    }
                }
            } else {
                return 0;
            }
        }

        for (int i = id + 1, iMax = lastId.get(); i <= iMax; i++) {
            final E entity = entities.get(i);

            if (isValid(entity)) {
                return i;
            }
        }

        return 0;
    }

    //-------------//
    // getIdBefore //
    //-------------//
    @Override
    public int getIdBefore (int id)
    {
        if (id == 0) {
            return 0;
        }

        for (int i = id - 1; i >= 0; i--) {
            final E entity = entities.get(i);

            if (isValid(entity)) {
                return i;
            }
        }

        return 0;
    }

    //-----------//
    // getLastId //
    //-----------//
    @Override
    public int getLastId ()
    {
        return lastId.get();
    }

    //-----------//
    // setLastId //
    //-----------//
    @Override
    public void setLastId (int lastId)
    {
        this.lastId.set(lastId);
    }

    //---------//
    // getName //
    //---------//
    @Override
    public String getName ()
    {
        return "";
    }

    //--------//
    // insert //
    //--------//
    /**
     * Insert an entity with its ID already defined.
     * This method is meant for re-loading an index.
     *
     * @param entity the entity to insert
     */
    public void insert (E entity)
    {
        int id = entity.getId();

        if (id == 0) {
            throw new IllegalArgumentException("Entity has no ID");
        }

        entities.put(id, entity);

        if (isVipId(id)) {
            entity.setVip(true);
            logger.info("VIP insert {}", entity);
        }
    }

    //---------//
    // isVipId //
    //---------//
    @Override
    public boolean isVipId (int id)
    {
        return (vipIds != null) && vipIds.contains(id);
    }

    //----------//
    // Iterator //
    //----------//
    @Override
    public Iterator<E> iterator ()
    {
        return entities.values().iterator();
    }

    //----------//
    // register //
    //----------//
    @Override
    public int register (E entity)
    {
        // Already registered?
        if (entity.getId() != 0) {
            return entity.getId();
        }

        int id = generateId();
        entity.setId(id);

        entities.put(id, entity);

        if (isVipId(id)) {
            entity.setVip(true);
            logger.info("VIP registered {}", entity);
        }

        return id;
    }

    //--------//
    // remove //
    //--------//
    @Override
    public void remove (E entity)
    {
        entities.remove(entity.getId());
    }

    //-------//
    // reset //
    //-------//
    @Override
    public void reset ()
    {
        lastId.set(0);
        entities.clear();
    }

    //-----------//
    // setVipIds //
    //-----------//
    /**
     * Record the IDs of VIP entities.
     *
     * @param vipIds collection of VIP IDs
     */
    public void setVipIds (List<Integer> vipIds)
    {
        this.vipIds = vipIds;
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("{");
        sb.append(internals());
        sb.append("}");

        return sb.toString();
    }

    //------------//
    // generateId //
    //------------//
    /**
     * Report the next available ID value.
     *
     * @return next available ID
     */
    protected int generateId ()
    {
        return lastId.incrementAndGet();
    }

    //-----------//
    // internals //
    //-----------//
    /**
     * Report a description string of class internals.
     *
     * @return description string of internals
     */
    protected String internals ()
    {
        return getName();
    }

    //---------//
    // isValid //
    //---------//
    /**
     * Report whether the provided entity is valid
     *
     * @param entity the entity to check
     * @return true if entity is valid
     */
    protected boolean isValid (E entity)
    {
        return entity != null;
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
        values = entities.values();
    }

    //------------------//
    // InterfaceAdapter //
    //------------------//
    /**
     * @param <E> precise entity type
     */
    public static class InterfaceAdapter<E extends AbstractEntity>
            extends XmlAdapter<BasicIndex<E>, EntityIndex<E>>
    {

        @Override
        public BasicIndex<E> marshal (EntityIndex<E> itf)
                throws Exception
        {
            return (BasicIndex<E>) itf;
        }

        @Override
        public EntityIndex<E> unmarshal (BasicIndex<E> basic)
                throws Exception
        {
            return basic;
        }
    }

    //---------//
    // Adapter //
    //---------//
    /**
     * This adapter converts an un-mappable ConcurrentHashMap<String, E> to/from
     * a JAXB-mappable IndexValue<E> (a flat list).
     *
     * @param <E> the specific entity type
     */
    private static class Adapter<E extends AbstractEntity>
            extends XmlAdapter<IndexValue<E>, ConcurrentSkipListMap<Integer, E>>
    {

        @Override
        public IndexValue<E> marshal (ConcurrentSkipListMap<Integer, E> map)
                throws Exception
        {
            IndexValue<E> value = new IndexValue<>();
            value.list = new ArrayList<>(map.values());

            return value;
        }

        @Override
        public ConcurrentSkipListMap<Integer, E> unmarshal (IndexValue<E> value)
                throws Exception
        {
            // TODO: is sorting needed?
            Collections.sort(value.list, new Comparator<E>()
                     {
                         @Override
                         public int compare (E e1,
                                             E e2)
                         {
                             return Integer.compare(e1.getId(), e2.getId());
                         }
                     });

            ConcurrentSkipListMap<Integer, E> map = new ConcurrentSkipListMap<>();

            for (E entity : value.list) {
                map.put(entity.getId(), entity);
            }

            return map;
        }
    }

    //------------//
    // IndexValue //
    //------------//
    /**
     * Class {@code IndexValue} is just a flat list of entities, with each item name
     * based on actual item type.
     *
     * @param <E> the specific entity type
     */
    private static class IndexValue<E extends AbstractEntity>
    {

        @XmlElementRefs({
            @XmlElementRef(type = Glyph.class),
            @XmlElementRef(type = BasicSymbol.class)})
        ArrayList<E> list; // Flat list of entities (each with its embedded id)
    }
}
