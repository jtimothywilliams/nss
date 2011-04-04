/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
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
package org.neo4j.gis.spatial;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.junit.Test;
import org.neo4j.gis.spatial.geotools.data.Neo4jSpatialDataStore;
import org.neo4j.gis.spatial.osm.OSMDataset;
import org.neo4j.gis.spatial.osm.OSMDataset.Way;
import org.neo4j.gis.spatial.osm.OSMGeometryEncoder;
import org.neo4j.gis.spatial.osm.OSMImporter;
import org.neo4j.gis.spatial.osm.OSMLayer;
import org.neo4j.gis.spatial.osm.OSMRelation;
import org.neo4j.gis.spatial.query.SearchWithin;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

public class TestOSMImport extends Neo4jTestCase {
	public static final String spatialTestMode = System.getProperty("spatial.test.mode");
	public static boolean runLongTests = false;
	static {
        if (spatialTestMode != null && spatialTestMode.equals("long")) {
        	runLongTests = true;
        } else {
        	runLongTests = false;
        }
	}

	@Test
	public void testImport_One() throws Exception {
		runImport("one-street.osm");
	}

	@Test
	public void testImport_One_X() throws Exception {
		runImport("one-street.osm", false);
	}

	@Test
	public void testImport_Two() throws Exception {
		runImport("two-street.osm");
	}

	@Test
	public void testImport_Two_X() throws Exception {
		runImport("two-street.osm", false);
	}

	@Test
	public void testImport_Map1() throws Exception {
		runImport("map.osm");
	}

	@Test
	public void testImport_Map1_X() throws Exception {
		runImport("map.osm", false);
	}

	@Test
	public void testImport_Map2() throws Exception {
		runImport("map2.osm");
	}

	@Test
	public void testImport_Map2_X() throws Exception {
		runImport("map2.osm", false);
	}

	@Test
	public void testImport_Cyprus() throws Exception {
		runImport("cyprus.osm");
	}

	@Test
	public void testImport_Cyprus_X() throws Exception {
		runImport("cyprus.osm", false);
	}

	@Test
	public void testImport_Croatia() throws Exception {
		runImport("croatia.osm");
	}

	@Test
	public void testImport_Croatia_X() throws Exception {
		runImport("croatia.osm", false);
	}

	@Test
	public void testImport_Denmark() throws Exception {
		runImport("denmark.osm");
	}

	private void runImport(String osmFile) throws Exception {
		runImport(osmFile, true);
	}

	private void runImport(String osmFile, boolean includePoints) throws Exception {
		// TODO: Consider merits of using dependency data in target/osm,
		// downloaded by maven, as done in TestSpatial, versus the test data
		// commited to source code as done here
		if (!checkOSMFile(new File(osmFile))) {
			return;
		}
		printDatabaseStats();
		loadTestOsmData(osmFile, includePoints, 1000);
		checkOSMLayer(osmFile);
		printDatabaseStats();
	}

	private boolean checkOSMFile(File osmFile) {
		if (!osmFile.exists()) {
			return false;
		}
		if (!runLongTests && osmFile.length() > 100000000) {
			return false;
		}
		return true;
	}

	private void loadTestOsmData(String layerName, boolean includePoints, int commitInterval) throws Exception {
		String osmPath = layerName;
		System.out.println("\n=== Loading layer " + layerName + " from " + osmPath + " ===");
		reActivateDatabase(false, true, false);
		long start = System.currentTimeMillis();
		OSMImporter importer = new OSMImporter(layerName);
		importer.importFile(getBatchInserter(), osmPath, false);
		reActivateDatabase(false, false, false);
		// Weird hack to force GC on large loads
		if (System.currentTimeMillis() - start > 300000) {
			for (int i = 0; i < 3; i++) {
				System.gc();
				Thread.sleep(1000);
			}
		}
		importer.reIndex(graphDb(), commitInterval, includePoints, false);
	}

	private void checkOSMLayer(String layerName) throws IOException {
		SpatialDatabaseService spatialService = new SpatialDatabaseService(graphDb());
		OSMLayer layer = (OSMLayer) spatialService.getOrCreateLayer(layerName, OSMGeometryEncoder.class, OSMLayer.class);
		assertNotNull("OSM Layer index should not be null", layer.getIndex());
		assertNotNull("OSM Layer index envelope should not be null", layer.getIndex().getLayerBoundingBox());
		Envelope bbox = layer.getIndex().getLayerBoundingBox();
		debugEnvelope(bbox, layerName, "bbox");
		//((RTreeIndex)layer.getIndex()).debugIndexTree();
		checkIndexAndFeatureCount(layer);
		checkChangesetsAndUsers(layer);
		checkOSMSearch(layer);
	}

	private void checkOSMSearch(OSMLayer layer) throws IOException {
		OSMDataset osm = (OSMDataset) layer.getDataset();
		Way way = null;
		int count = 0;
		for (Way wayNode : osm.getWays()) {
			way = wayNode;
			if (count++ > 100)
				break;
		}
		assertNotNull("Should be at least one way", way);
		Envelope bbox = way.getEnvelope();
		Geometry searchArea = layer.getGeometryFactory().toGeometry(bbox);
		SearchWithin search = new SearchWithin(searchArea);
		layer.getIndex().executeSearch(search);
		List<SpatialDatabaseRecord> results = search.getResults();
		assertTrue("Should be at least one result, but got zero", results.size() > 0);
	}

	private void debugEnvelope(Envelope bbox, String layer, String name) {
		System.out.println("Layer '" + layer + "' has envelope '" + name + "': " + bbox);
		System.out.println("\tX: [" + bbox.getMinX() + ":" + bbox.getMaxX() + "]");
		System.out.println("\tY: [" + bbox.getMinY() + ":" + bbox.getMaxY() + "]");
	}

	private void checkIndexAndFeatureCount(Layer layer) throws IOException {
		if(layer.getIndex().count()<1) {
			System.out.println("Warning: index count zero: "+layer.getName());
		}
		System.out.println("Layer '" + layer.getName() + "' has " + layer.getIndex().count() + " entries in the index");
		DataStore store = new Neo4jSpatialDataStore(graphDb());
		SimpleFeatureCollection features = store.getFeatureSource(layer.getName()).getFeatures();
		System.out.println("Layer '" + layer.getName() + "' has " + features.size() + " features");
		assertEquals("FeatureCollection.size for layer '" + layer.getName() + "' not the same as index count", layer.getIndex()
				.count(), features.size());
	}

	private void checkChangesetsAndUsers(OSMLayer layer) {
		double totalMatch = 0.0;
		int waysMatched = 0;
		int waysCounted = 0;
		int nodesCounted = 0;
		int waysMissing = 0;
		int nodesMissing = 0;
		int usersMissing = 0;
		float maxMatch = 0.0f;
		float minMatch = 1.0f;
		HashMap<Long,Integer> userNodeCount = new HashMap<Long,Integer>();
		HashMap<Long,String> userNames = new HashMap<Long,String>();
		HashMap<Long,Long> userIds = new HashMap<Long,Long>();
		OSMDataset dataset = (OSMDataset)layer.getDataset();
		for (Node way : dataset.getAllWayNodes()) {
			int node_count = 0;
			int match_count = 0;
			assertNull("Way has changeset property",way.getProperty("changeset", null));
			Node wayChangeset = dataset.getChangeset(way);
			if (wayChangeset != null) {
				long wayCS = (Long)wayChangeset.getProperty("changeset");
				for(Node node : dataset.getWayNodes(way)) {
					assertNull("Node has changeset property",node.getProperty("changeset", null));
					Node nodeChangeset = dataset.getChangeset(node);
					if (nodeChangeset == null) {
						nodesMissing++;
					} else {
						long nodeCS = (Long)nodeChangeset.getProperty("changeset");
						if (nodeChangeset.equals(wayChangeset)) {
							match_count++;
						} else {
							assertFalse("Two changeset nodes should not have the same changeset number: way(" + wayCS + ")==node("
									+ nodeCS + ")", wayCS == nodeCS);
						}
						Node user = dataset.getUser(nodeChangeset);
						if (user != null) {
							long userid = user.getId();
							if (userNodeCount.containsKey(userid)) {
								userNodeCount.put(userid, userNodeCount.get(userid) + 1);
							} else {
								userNodeCount.put(userid, 1);
								userNames.put(userid, (String) user.getProperty("name", null));
								userIds.put(userid, (Long) user.getProperty("uid", null));
							}
						} else {
							if (usersMissing++ < 10) {
								System.out.println("Changeset " + nodeCS + " should have user: " + nodeChangeset);
							}
						}
					}
					node_count ++;
				}
			} else {
				waysMissing ++;
			}
			if (node_count > 0) {
				waysMatched++;
				float match = ((float) match_count) / ((float) node_count);
				maxMatch = Math.max(maxMatch, match);
				minMatch = Math.min(minMatch, match);
				totalMatch += match;
				nodesCounted += node_count;
			}
			waysCounted ++;
		}
		System.out.println("After checking " + waysCounted + " ways:");
		System.out.println("\twe found " + waysMatched + " ways with an average of " + (nodesCounted / waysMatched) + " nodes");
		System.out.println("\tand an average of " + (100.0 * totalMatch / waysMatched) + "% matching changesets");
		System.out.println("\twith min-match " + (100.0 * minMatch) + "% and max-match " + (100.0 * maxMatch) + "%");
		System.out.println("\tWays missing changsets: " + waysMissing);
		System.out.println("\tNodes missing changsets: " + nodesMissing + " (~" + (nodesMissing / waysMatched) + " / way)");
		System.out.println("\tUnique users: " + userNodeCount.size() + " (with " + usersMissing + " changeset missing users)");
		ArrayList<ArrayList<Long>> userCounts = new ArrayList<ArrayList<Long>>();
		for(long user:userNodeCount.keySet()){
			int count = userNodeCount.get(user);
			userCounts.ensureCapacity(count);
			while (userCounts.size() < count + 1) {
				userCounts.add(null);
			}
			ArrayList<Long> userSet = userCounts.get(count);
			if(userSet == null) {
				userSet = new ArrayList<Long>();
			}
			userSet.add(user);
			userCounts.set(count, userSet);
		}
		if (userCounts.size() > 1) {
			System.out.println("\tTop 20 users (nodes: users):");
			for (int ui = userCounts.size() - 1, i = 0; i < 20 && ui >= 0; ui--) {
				ArrayList<Long> userSet = userCounts.get(ui);
				if (userSet != null && userSet.size() > 0) {
					i++;
					StringBuffer us = new StringBuffer();
					for (long user : userSet) {
						Node userNode = graphDb().getNodeById(user);
						int csCount = 0;
						for(@SuppressWarnings("unused") Relationship rel: userNode.getRelationships(OSMRelation.USER, Direction.INCOMING)){
							csCount ++;
						}
						String name = userNames.get(user);
						Long uid = userIds.get(user);
						if (us.length() > 0) {
							us.append(", ");
						}
						us.append("" + name + " (uid=" + uid + ", id=" + user + ", changesets=" + csCount + ")");
					}
					System.out.println("\t\t" + ui + ": " + us.toString());
				}
			}
		}
	}
}
