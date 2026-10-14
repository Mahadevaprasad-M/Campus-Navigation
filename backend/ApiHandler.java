/* =====================================================================
 * FILE:    ApiHandler.java
 * PROJECT: Campus Navigation System
 * COLLEGE: Government Engineering College, Chamarajanagar
 * PURPOSE: Handles requests that start with /api/.
 *
 * Endpoints:
 *    GET /api/health            -> simple status JSON
 *    GET /api/locations         -> list of all locations
 *    GET /api/location/{id}     -> one location
 *    GET /api/path?from=X&to=Y  -> route + step-by-step directions
 *
 * Extra query parameter:
 *    &algo=bfs                  -> use BFS instead of Dijkstra
 * ===================================================================== */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class ApiHandler implements HttpHandler {

    /* -----------------------------------------------------------------
     * 1. CONSTANTS
     * ----------------------------------------------------------------- */

    // Average walking speed in metres per minute (about 4.3 km/h).
    private static final double WALKING_SPEED = 72.0;

    // Safety limit so we never generate too many steps.
    private static final int MAX_STEPS = 200;

    /* -----------------------------------------------------------------
     * 2. FIELD
     * ----------------------------------------------------------------- */
    private CampusGraph graph;

    /* -----------------------------------------------------------------
     * 3. CONSTRUCTOR
     * ----------------------------------------------------------------- */
    public ApiHandler(CampusGraph graph) {
        this.graph = graph;
    }

    /* -----------------------------------------------------------------
     * 4. MAIN ENTRY POINT
     * -----------------------------------------------------------------
     * The HTTP server calls handle() for every request to /api/*.
     * We look at the URL path and decide what to do.
     * ----------------------------------------------------------------- */
    public void handle(HttpExchange exchange) throws IOException {

        // Allow the frontend (GitHub Pages, etc.) to call this API.
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        String method = exchange.getRequestMethod();

        // Browsers first send an OPTIONS request for CORS.
        // We answer it with 204 (no content) and stop.
        if (method.equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        // We only allow GET from here on.
        if (!method.equalsIgnoreCase("GET")) {
            sendJson(exchange, 405, errorJson("method_not_allowed",
                    "Only GET is allowed on /api/*"));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        // Very last-resort try/catch so we never crash the server.
        try {
            if (path.equals("/api/health")) {
                handleHealth(exchange);
            } else if (path.equals("/api/locations")) {
                handleLocations(exchange);
            } else if (path.startsWith("/api/location/")) {
                String id = path.substring("/api/location/".length());
                handleSingleLocation(exchange, id);
            } else if (path.equals("/api/path")) {
                handlePath(exchange);
            } else {
                sendJson(exchange, 404, errorJson("unknown_endpoint",
                        "No handler for " + path));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, errorJson("internal_error",
                    "Server error: " + e.getMessage()));
        }
    }

    /* -----------------------------------------------------------------
     * 5. /api/health
     * ----------------------------------------------------------------- */
    private void handleHealth(HttpExchange exchange) throws IOException {

        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", "ok");
        body.put("nodes", graph.getNodeCount());
        body.put("edges", graph.getEdgeCount());
        body.put("algorithm", "dijkstra");

        sendJson(exchange, 200, JsonHelper.obj(body));
    }

    /* -----------------------------------------------------------------
     * 6. /api/locations
     * -----------------------------------------------------------------
     * Returns a JSON array of every location, sorted by name.
     * ----------------------------------------------------------------- */
    private void handleLocations(HttpExchange exchange) throws IOException {

        // Get all locations from the graph.
        ArrayList<CampusGraph.Location> list = graph.getAllLocations();

        // Sort them alphabetically by name (simple Comparator).
        Collections.sort(list, new Comparator<CampusGraph.Location>() {
            public int compare(CampusGraph.Location a, CampusGraph.Location b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        // Build the JSON array by hand.
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(locationToJson(list.get(i)));
        }

        sb.append("]");
        sendJson(exchange, 200, sb.toString());
    }

    /* -----------------------------------------------------------------
     * 7. /api/location/{id}
     * ----------------------------------------------------------------- */
    private void handleSingleLocation(HttpExchange exchange, String rawId)
            throws IOException {

        String id = urlDecode(rawId);

        if (id.length() == 0 || !graph.hasNode(id)) {
            sendJson(exchange, 404, errorJson("unknown_location",
                    "No location with id '" + id + "'"));
            return;
        }

        CampusGraph.Location loc = graph.getLocation(id);
        sendJson(exchange, 200, locationToJson(loc));
    }

    /* -----------------------------------------------------------------
     * 8. /api/path?from=X&to=Y
     * ----------------------------------------------------------------- */
    private void handlePath(HttpExchange exchange) throws IOException {

        // Parse query parameters into a simple HashMap.
        HashMap<String, String> query = parseQuery(exchange.getRequestURI().getQuery());

        String from = query.get("from");
        String to = query.get("to");
        String algo = query.get("algo");

        // --- Check that both 'from' and 'to' are present ---
        if (from == null || from.trim().length() == 0
                || to == null || to.trim().length() == 0) {
            sendJson(exchange, 400, errorJson("missing_params",
                    "Both 'from' and 'to' query parameters are required."));
            return;
        }

        from = from.trim();
        to = to.trim();

        // --- Check that both ids exist in the graph ---
        if (!graph.hasNode(from)) {
            sendJson(exchange, 404, errorJson("unknown_from",
                    "Unknown start location: '" + from + "'"));
            return;
        }
        if (!graph.hasNode(to)) {
            sendJson(exchange, 404, errorJson("unknown_to",
                    "Unknown destination: '" + to + "'"));
            return;
        }

        // --- Choose algorithm ---
        CampusGraph.PathResult result;
        if (algo != null && algo.equalsIgnoreCase("bfs")) {
            result = graph.bfs(from, to);
        } else {
            result = graph.dijkstra(from, to);
        }

        // --- If no path was found, send a friendly message ---
        if (!result.found) {
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("error", "no_path");
            body.put("message", "No walking route exists between these locations.");
            body.put("from", from);
            body.put("to", to);
            sendJson(exchange, 200, JsonHelper.obj(body));
            return;
        }

        // --- Build the list of steps ---
        ArrayList<Object> steps = buildSteps(result.path, result.totalDistance);

        // --- Compose the final JSON response ---
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("from", from);
        body.put("to", to);

        if (algo != null && algo.equalsIgnoreCase("bfs")) {
            body.put("algorithm", "bfs");
        } else {
            body.put("algorithm", "dijkstra");
        }

        body.put("totalDistance", result.totalDistance);
        body.put("estimatedTimeMinutes", estimateMinutes(result.totalDistance));
        body.put("path", result.path);
        body.put("steps", steps);

        CampusGraph.Location startLoc = graph.getLocation(from);
        CampusGraph.Location endLoc = graph.getLocation(to);

        String startName = (startLoc != null) ? startLoc.name : from;
        String endName = (endLoc != null) ? endLoc.name : to;

        body.put("startName", startName);
        body.put("endName", endName);

        sendJson(exchange, 200, JsonHelper.obj(body));
    }

    /* -----------------------------------------------------------------
     * 9. BUILD STEPS
     * -----------------------------------------------------------------
     * Given a path like [entrance, j1, j2, library, principal],
     * produce a list of steps. Each step has:
     *    step        - number (1, 2, 3, ...)
     *    instruction - plain-English sentence
     *    distance    - metres on this leg
     *    direction   - "left", "right", "straight", or "arrived"
     *    nodeId      - id of the destination of this leg
     *    nodeName    - full name of the destination of this leg
     * ----------------------------------------------------------------- */
    private ArrayList<Object> buildSteps(ArrayList<String> path, int totalDistance) {

        ArrayList<Object> steps = new ArrayList<Object>();

        if (path == null || path.size() == 0) {
            return steps;
        }

        // ---- Special case: start and end are the same ----
        if (path.size() == 1) {
            String onlyId = path.get(0);
            CampusGraph.Location loc = graph.getLocation(onlyId);
            String name = (loc != null) ? loc.name : onlyId;

            LinkedHashMap<String, Object> step = new LinkedHashMap<String, Object>();
            step.put("step", 1);
            step.put("instruction", "You are already at " + name + ".");
            step.put("distance", 0);
            step.put("direction", "arrived");
            step.put("nodeId", onlyId);
            step.put("nodeName", name);

            steps.add(step);
            return steps;
        }

        // ---- Normal case: walk through each leg of the path ----
        int stepNumber = 1;

        for (int i = 0; i < path.size() - 1; i++) {

            if (stepNumber > MAX_STEPS) {
                break;
            }

            String currentId = path.get(i);
            String nextId = path.get(i + 1);

            CampusGraph.Location currentLoc = graph.getLocation(currentId);
            CampusGraph.Location nextLoc = graph.getLocation(nextId);

            String currentName = (currentLoc != null) ? currentLoc.name : currentId;
            String nextName = (nextLoc != null) ? nextLoc.name : nextId;

            // Get the distance of this leg.
            int legDistance = graph.getEdgeWeight(currentId, nextId);
            if (legDistance < 0) {
                legDistance = 0;
            }

            String direction;
            String instruction;

            if (i == 0) {
                // First leg — there is no previous node.
                direction = "straight";
                instruction = "Start at " + currentName
                        + " and walk " + legDistance + "m towards "
                        + nextName + ".";
            } else {
                // Middle legs — use the cross product to decide turn.
                String prevId = path.get(i - 1);
                direction = graph.getTurn(prevId, currentId, nextId);

                String turnText;
                if (direction.equals("left")) {
                    turnText = "Turn left at " + currentName;
                } else if (direction.equals("right")) {
                    turnText = "Turn right at " + currentName;
                } else {
                    turnText = "Continue straight past " + currentName;
                }

                instruction = turnText + ", then walk " + legDistance
                        + "m towards " + nextName + ".";
            }

            LinkedHashMap<String, Object> step = new LinkedHashMap<String, Object>();
            step.put("step", stepNumber);
            step.put("instruction", instruction);
            step.put("distance", legDistance);
            step.put("direction", direction);
            step.put("nodeId", nextId);
            step.put("nodeName", nextName);

            steps.add(step);
            stepNumber = stepNumber + 1;
        }

        // ---- Final arrival step ----
        String endId = path.get(path.size() - 1);
        CampusGraph.Location endLoc = graph.getLocation(endId);
        String endName = (endLoc != null) ? endLoc.name : endId;

        LinkedHashMap<String, Object> arrival = new LinkedHashMap<String, Object>();
        arrival.put("step", stepNumber);
        arrival.put("instruction", "You have arrived at " + endName + ".");
        arrival.put("distance", 0);
        arrival.put("direction", "arrived");
        arrival.put("nodeId", endId);
        arrival.put("nodeName", endName);

        steps.add(arrival);
        return steps;
    }

    /* -----------------------------------------------------------------
     * 10. HELPER: ESTIMATE TIME IN MINUTES
     * ----------------------------------------------------------------- */
    private int estimateMinutes(int metres) {
        if (metres <= 0) {
            return 0;
        }
        // Convert metres to minutes, round up.
        int minutes = (int) Math.ceil(metres / WALKING_SPEED);
        if (minutes < 1) {
            minutes = 1;   // any positive distance takes at least 1 minute
        }
        return minutes;
    }

    /* -----------------------------------------------------------------
     * 11. HELPER: LOCATION TO JSON
     * ----------------------------------------------------------------- */
    private String locationToJson(CampusGraph.Location loc) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", loc.id);
        m.put("name", loc.name);
        m.put("x", loc.x);
        m.put("y", loc.y);
        m.put("type", loc.type);
        m.put("hours", loc.hours);
        return JsonHelper.obj(m);
    }

    /* -----------------------------------------------------------------
     * 12. HELPER: BUILD AN ERROR OBJECT
     * ----------------------------------------------------------------- */
    private String errorJson(String code, String message) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("error", code);
        m.put("message", message);
        return JsonHelper.obj(m);
    }

    /* -----------------------------------------------------------------
     * 13. HELPER: SEND JSON RESPONSE
     * ----------------------------------------------------------------- */
    private void sendJson(HttpExchange exchange, int statusCode, String json)
            throws IOException {

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type",
                "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    /* -----------------------------------------------------------------
     * 14. HELPER: PARSE QUERY STRING
     * -----------------------------------------------------------------
     * Input:  "from=entrance&to=library&algo=bfs"
     * Output: HashMap with three entries.
     * ----------------------------------------------------------------- */
    private HashMap<String, String> parseQuery(String query) {

        HashMap<String, String> result = new HashMap<String, String>();

        if (query == null || query.length() == 0) {
            return result;
        }

        // Split on '&' into key=value pairs.
        String[] pairs = query.split("&");

        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            if (pair.length() == 0) {
                continue;
            }

            int equalsPosition = pair.indexOf("=");

            String key;
            String value;

            if (equalsPosition < 0) {
                key = pair;
                value = "";
            } else {
                key = pair.substring(0, equalsPosition);
                value = pair.substring(equalsPosition + 1);
            }

            result.put(urlDecode(key), urlDecode(value));
        }

        return result;
    }

    /* -----------------------------------------------------------------
     * 15. HELPER: URL DECODE
     * ----------------------------------------------------------------- */
    private String urlDecode(String text) {
        if (text == null) {
            return "";
        }
        try {
            return URLDecoder.decode(text, "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }
}