/* =====================================================================
 * FILE:    CampusGraph.java
 * PROJECT: Campus Navigation System
 * COLLEGE: Government Engineering College, Chamarajanagar
 * PURPOSE: Stores campus locations and paths as a graph.
 *          Provides:
 *            1. addLocation()  - add a place (node)
 *            2. addEdge()      - add a path between two places
 *            3. bfs()          - shortest path by number of stops
 *            4. dijkstra()     - shortest path by total distance
 *            5. getTurn()      - decide left/right/straight using math
 * ===================================================================== */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class CampusGraph {

    /* -----------------------------------------------------------------
     * 1. INNER CLASSES  (simple data holders)
     * ----------------------------------------------------------------- */

    // A "Location" is a place on campus (library, canteen, etc.)
    public static class Location {
        public String id;       // short code like "library"
        public String name;     // full name like "Central Library"
        public int x;           // x coordinate on map
        public int y;           // y coordinate on map
        public String type;     // gate, building, lab, office, facility
        public String hours;    // like "9AM-6PM", may be null

        public Location(String id, String name, int x, int y,
                        String type, String hours) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.type = type;
            this.hours = hours;
        }
    }

    // An "Edge" is a path from one place to another.
    // distance is stored in metres.
    public static class Edge {
        public String to;       // destination location id
        public int distance;    // metres

        public Edge(String to, int distance) {
            this.to = to;
            this.distance = distance;
        }
    }

    // A "PathResult" is the answer to "find shortest path".
    // It contains the list of stops and the total distance.
    public static class PathResult {
        public ArrayList<String> path;  // ordered list of location ids
        public int totalDistance;       // total metres
        public boolean found;           // true if a path was found

        public PathResult(ArrayList<String> path, int totalDistance, boolean found) {
            this.path = path;
            this.totalDistance = totalDistance;
            this.found = found;
        }
    }

    /* -----------------------------------------------------------------
     * 2. DATA STORAGE
     * -----------------------------------------------------------------
     * We keep two HashMaps:
     *   locations  -> id to Location object (name, x, y, etc.)
     *   adjacency  -> id to list of Edges leaving that location
     * ----------------------------------------------------------------- */

    private HashMap<String, Location> locations;
    private HashMap<String, ArrayList<Edge>> adjacency;

    /* -----------------------------------------------------------------
     * 3. CONSTRUCTOR
     * ----------------------------------------------------------------- */
    public CampusGraph() {
        locations = new HashMap<String, Location>();
        adjacency = new HashMap<String, ArrayList<Edge>>();
    }

    /* -----------------------------------------------------------------
     * 4. ADDING DATA
     * ----------------------------------------------------------------- */

    // Add a place to the graph.
    public void addLocation(Location loc) {
        if (loc == null || loc.id == null) {
            return;
        }
        locations.put(loc.id, loc);

        // If this id is new, create an empty list of neighbours for it.
        if (!adjacency.containsKey(loc.id)) {
            adjacency.put(loc.id, new ArrayList<Edge>());
        }
    }

    // Add a path between two places.
    // Because walking paths work both ways, we add the edge twice.
    public void addEdge(String from, String to, int distance) {
        if (from == null || to == null) {
            return;
        }
        if (from.equals(to)) {
            return; // no self loops
        }
        if (distance < 1) {
            distance = 1; // distance must be positive
        }

        // Make sure both endpoints exist.
        if (!adjacency.containsKey(from)) {
            addLocation(new Location(from, from, 0, 0, "unknown", null));
        }
        if (!adjacency.containsKey(to)) {
            addLocation(new Location(to, to, 0, 0, "unknown", null));
        }

        // Add "from -> to"
        adjacency.get(from).add(new Edge(to, distance));

        // Add "to -> from"  (undirected graph)
        adjacency.get(to).add(new Edge(from, distance));
    }

    /* -----------------------------------------------------------------
     * 5. SIMPLE LOOKUP HELPERS
     * ----------------------------------------------------------------- */

    public boolean hasNode(String id) {
        if (id == null) {
            return false;
        }
        return adjacency.containsKey(id);
    }

    public Location getLocation(String id) {
        return locations.get(id);
    }

    public ArrayList<Location> getAllLocations() {
        // We return a new ArrayList so callers can sort it freely.
        ArrayList<Location> list = new ArrayList<Location>();
        for (Location loc : locations.values()) {
            list.add(loc);
        }
        return list;
    }

    public ArrayList<Edge> getNeighbours(String id) {
        ArrayList<Edge> list = adjacency.get(id);
        if (list == null) {
            return new ArrayList<Edge>();
        }
        return list;
    }

    public int getNodeCount() {
        return adjacency.size();
    }

    public int getEdgeCount() {
        // Every edge is stored twice (once in each direction).
        // So the real count = total entries / 2.
        int total = 0;
        for (String id : adjacency.keySet()) {
            total = total + adjacency.get(id).size();
        }
        return total / 2;
    }

    // Returns the direct distance between two neighbours,
    // or -1 if they are not directly connected.
    public int getEdgeWeight(String a, String b) {
        ArrayList<Edge> list = adjacency.get(a);
        if (list == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            Edge e = list.get(i);
            if (e.to.equals(b)) {
                return e.distance;
            }
        }
        return -1;
    }

    /* -----------------------------------------------------------------
     * 6. BFS  (Breadth First Search)
     * -----------------------------------------------------------------
     * Finds the path with the FEWEST number of stops.
     * Ignores distance. Useful for comparison in the viva.
     * ----------------------------------------------------------------- */
    public PathResult bfs(String start, String end) {

        // If either id is missing, no path is possible.
        if (!hasNode(start) || !hasNode(end)) {
            return new PathResult(new ArrayList<String>(), 0, false);
        }

        // Same start and end -> path is just that one node.
        if (start.equals(end)) {
            ArrayList<String> single = new ArrayList<String>();
            single.add(start);
            return new PathResult(single, 0, true);
        }

        // Queue for BFS, Set for visited, Map for remembering the parent.
        Queue<String> queue = new LinkedList<String>();
        Set<String> visited = new HashSet<String>();
        HashMap<String, String> parent = new HashMap<String, String>();

        queue.add(start);
        visited.add(start);

        boolean reached = false;

        while (!queue.isEmpty()) {
            String current = queue.remove();

            if (current.equals(end)) {
                reached = true;
                break;
            }

            ArrayList<Edge> neighbours = getNeighbours(current);
            for (int i = 0; i < neighbours.size(); i++) {
                Edge edge = neighbours.get(i);
                if (!visited.contains(edge.to)) {
                    visited.add(edge.to);
                    parent.put(edge.to, current);
                    queue.add(edge.to);
                }
            }
        }

        if (!reached) {
            return new PathResult(new ArrayList<String>(), 0, false);
        }

        // Rebuild the path by walking backwards through parent.
        ArrayList<String> path = new ArrayList<String>();
        String step = end;
        while (step != null) {
            path.add(0, step);   // add to front
            step = parent.get(step);
        }

        // For BFS, "distance" is the number of edges (hops).
        int hops = path.size() - 1;
        return new PathResult(path, hops, true);
    }

    /* -----------------------------------------------------------------
     * 7. DIJKSTRA  (shortest by distance in metres)
     * -----------------------------------------------------------------
     * We keep a "dist" map of best-known distance from start.
     * Every round we pick the unvisited node with smallest dist.
     * Then we relax its neighbours.
     *
     * This is simple O(V^2) Dijkstra — easy to understand.
     * Good enough for 36 nodes.
     * ----------------------------------------------------------------- */
    public PathResult dijkstra(String start, String end) {

        if (!hasNode(start) || !hasNode(end)) {
            return new PathResult(new ArrayList<String>(), 0, false);
        }

        if (start.equals(end)) {
            ArrayList<String> single = new ArrayList<String>();
            single.add(start);
            return new PathResult(single, 0, true);
        }

        // Constants
        int INFINITY = 1000000000;

        // dist[id] = best known distance from start to id
        HashMap<String, Integer> dist = new HashMap<String, Integer>();

        // parent[id] = previous node on the best path to id
        HashMap<String, String> parent = new HashMap<String, String>();

        // visited set = nodes we have already finished with
        Set<String> visited = new HashSet<String>();

        // Step 1: set every distance to INFINITY except the start.
        for (String id : adjacency.keySet()) {
            dist.put(id, INFINITY);
        }
        dist.put(start, 0);

        // Step 2: main loop
        while (true) {

            // 2a. Find the unvisited node with the smallest distance.
            String current = null;
            int bestDist = INFINITY;

            for (String id : adjacency.keySet()) {
                if (visited.contains(id)) {
                    continue;
                }
                int d = dist.get(id);
                if (d < bestDist) {
                    bestDist = d;
                    current = id;
                }
            }

            // 2b. If no reachable unvisited node, or we reached the end, stop.
            if (current == null) {
                break;
            }
            if (current.equals(end)) {
                break;
            }

            visited.add(current);

            // 2c. Relax all neighbours of 'current'.
            ArrayList<Edge> neighbours = getNeighbours(current);
            for (int i = 0; i < neighbours.size(); i++) {
                Edge edge = neighbours.get(i);
                if (visited.contains(edge.to)) {
                    continue;
                }
                int newDist = dist.get(current) + edge.distance;
                if (newDist < dist.get(edge.to)) {
                    dist.put(edge.to, newDist);
                    parent.put(edge.to, current);
                }
            }
        }

        // If the end is still INFINITY, we never reached it.
        if (dist.get(end) == INFINITY) {
            return new PathResult(new ArrayList<String>(), 0, false);
        }

        // Rebuild path by walking backwards through parent.
        ArrayList<String> path = new ArrayList<String>();
        String step = end;
        while (step != null) {
            path.add(0, step);
            step = parent.get(step);
        }

        return new PathResult(path, dist.get(end), true);
    }

    /* -----------------------------------------------------------------
     * 8. TURN DIRECTION  (left / right / straight)
     * -----------------------------------------------------------------
     * Given three nodes  A -> B -> C, we look at the two vectors:
     *    v1 = B - A
     *    v2 = C - B
     * Then we compute the cross product:
     *    cross = v1.x * v2.y - v1.y * v2.x
     *
     * In screen coordinates (y grows downwards):
     *    cross > 0  ->  turn LEFT
     *    cross < 0  ->  turn RIGHT
     *    cross = 0  ->  go STRAIGHT
     * ----------------------------------------------------------------- */
    public String getTurn(String aId, String bId, String cId) {

        Location a = locations.get(aId);
        Location b = locations.get(bId);
        Location c = locations.get(cId);

        if (a == null || b == null || c == null) {
            return "straight";
        }

        int v1x = b.x - a.x;
        int v1y = b.y - a.y;
        int v2x = c.x - b.x;
        int v2y = c.y - b.y;

        int cross = v1x * v2y - v1y * v2x;

        // Small threshold to ignore tiny rounding wobble.
        if (cross > 4) {
            return "left";
        } else if (cross < -4) {
            return "right";
        } else {
            return "straight";
        }
    }
}