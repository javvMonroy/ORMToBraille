//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                         P a t h T a s k                                        //
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

import java.nio.file.Path;

/**
 * Class {@code PathTask} is a VoidTask that operates on a path.
 *
 * @author Hervé Bitteur
 */
public abstract class PathTask
        extends VoidTask
{

    /** Underlying path. */
    protected Path path;

    /**
     * Creates a new {@code PathTask} object.
     */
    public PathTask ()
    {
    }

    /**
     * Creates a new {@code PathTask} object.
     *
     * @param path the related path
     */
    protected PathTask (Path path)
    {
        this.path = path;
    }

    /**
     * Set the path value.
     *
     * @param path the path used by the task
     */
    public void setPath (Path path)
    {
        this.path = path;
    }
}
