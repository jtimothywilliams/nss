/*
 * Copyright (c) 2010
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gis.spatial.query;

import static org.neo4j.gis.spatial.GeometryUtils.*;

import org.neo4j.graphdb.Node;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;


/**
 * @author Davide Savazzi
 */
public class SearchEqual extends AbstractSearchIntersection {

	public SearchEqual(Geometry other) {
		super(other);
	}

	protected void onEnvelopeIntersection(Node geomNode, Envelope geomEnvelope) {
		if (geomEnvelope.equals(other.getEnvelopeInternal())) {		
			Geometry geometry = decode(geomNode, geometryFactory);
			if (geometry.equals(other)) add(geomNode, geometry);
		}
	}

}