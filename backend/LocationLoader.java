/* =====================================================================
 * FILE:    LocationLoader.java
 * PROJECT: Campus Navigation System
 * COLLEGE: Government Engineering College, Chamarajanagar
 * PURPOSE: Reads locations.json and edges.json from the data folder
 *          and fills the CampusGraph with locations and edges.
 *
 *          We do NOT use any JSON library. We use simple string
 *          searching with indexOf() and substring().
 * ===================================================================== */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class LocationLoader {

    /* -----------------------------------------------------------------
     * 1. FIELDS
     * ----------------------------------------------------------------- */

    private CampusGraph graph;
    private String dataFolder;   // where to look for the JSON files

    /* -----------------------------------------------------------------
     * 2. CONSTRUCTOR
     * ----------------------------------------------------------------- */
    public LocationLoader(CampusGraph graph, String dataFolder) {
        this.graph = graph;
        if (dataFolder == null || dataFolder.length() == 0) {
            this.dataFolder = "data";
        } else {
            this.dataFolder = dataFolder;
        }
    }

    /* -----------------------------------------------------------------
     * 3. PUBLIC METHOD - LOAD EVERYTHING
     * ----------------------------------------------------------------- */
    public void loadAll() throws IOException {
        int locationCount = loadLocations();
        int edgeCount = loadEdges();

        System.out.println("[LocationLoader] Loaded " + locationCount
                + " locations and " + edgeCount + " edges");

        System.out.println("[CampusGraph] Nodes = " + graph.getNodeCount()
                + ",  Edges = " + graph.getEdgeCount());
    }

    /* -----------------------------------------------------------------
     * 4. LOAD LOCATIONS
     * -----------------------------------------------------------------
     * File format (simplified):
     *   {
     *     "entrance": { "name": "...", "x": 80, "y": 520, ... },
     *     "library":  { "name": "...", "x": 350, "y": 320, ... },
     *     ...
     *   }
     *
     * Algorithm:
     *   - Find every "id" : { ... } block.
     *   - Inside each block, read the fields one by one.
     * ----------------------------------------------------------------- */
    public int loadLocations() throws IOException {

        String rawText = readFile("locations.json");
        String json = stripComments(rawText);

        int count = 0;
        int position = 0;

        // Loop through the file looking for every  "id" : { ... }  block.
        while (position < json.length()) {

            // --- Step 1: find the next quoted string (the location id) ---
            int openQuote = json.indexOf("\"", position);
            if (openQuote < 0) {
                break;   // no more quotes, we are done
            }
            int closeQuote = json.indexOf("\"", openQuote + 1);
            if (closeQuote < 0) {
                break;   // malformed file, stop
            }

            String id = json.substring(openQuote + 1, closeQuote);

            // --- Step 2: find the opening brace { after this id ---
            int openBrace = json.indexOf("{", closeQuote + 1);
            if (openBrace < 0) {
                break;
            }

            // --- Step 3: find the matching closing brace } ---
            int closeBrace = json.indexOf("}", openBrace + 1);
            if (closeBrace < 0) {
                break;
            }

            // --- Step 4: extract the body between { and } ---
            String body = json.substring(openBrace + 1, closeBrace);

            // --- Step 5: read the fields from the body ---
            String name = getStringValue(body, "name");
            int x = getIntValue(body, "x", 0);
            int y = getIntValue(body, "y", 0);
            String type = getStringValue(body, "type");
            String hours = getStringValue(body, "hours");

            // If name is missing, use the id as a fallback.
            if (name == null) {
                name = id;
            }
            if (type == null) {
                type = "unknown";
            }

            // Create and add the Location to the graph.
            CampusGraph.Location loc = new CampusGraph.Location(
                    id, name, x, y, type, hours);
            graph.addLocation(loc);

            count = count + 1;
            position = closeBrace + 1;
        }

        return count;
    }

    /* -----------------------------------------------------------------
     * 5. LOAD EDGES
     * -----------------------------------------------------------------
     * File format (simplified):
     *   [
     *     { "from": "entrance", "to": "junction1", "distance": 50 },
     *     { "from": "junction1", "to": "junction2", "distance": 60 },
     *     ...
     *   ]
     *
     * Algorithm:
     *   - Find every { ... } block.
     *   - Inside each block, read "from", "to", "distance".
     * ----------------------------------------------------------------- */
    public int loadEdges() throws IOException {

        String rawText = readFile("edges.json");
        String json = stripComments(rawText);

        int count = 0;
        int position = 0;

        while (position < json.length()) {

            // Find the next opening brace.
            int openBrace = json.indexOf("{", position);
            if (openBrace < 0) {
                break;
            }

            // Find the matching closing brace.
            int closeBrace = json.indexOf("}", openBrace + 1);
            if (closeBrace < 0) {
                break;
            }

            // Extract the body.
            String body = json.substring(openBrace + 1, closeBrace);

            String from = getStringValue(body, "from");
            String to = getStringValue(body, "to");
            int distance = getIntValue(body, "distance", 0);

            // Only add the edge if both ends are known and distance is valid.
            if (from != null && to != null) {
                if (distance <= 0) {
                    distance = 10;   // sensible default
                }
                graph.addEdge(from, to, distance);
                count = count + 1;
            }

            position = closeBrace + 1;
        }

        return count;
    }

    /* -----------------------------------------------------------------
     * 6. HELPER: READ A FILE WITH FALLBACK PATHS
     * -----------------------------------------------------------------
     * Depending on where the user runs the program from, the data
     * folder could be at different relative paths. We try several
     * likely locations and use the first one that exists.
     * ----------------------------------------------------------------- */
    private String readFile(String filename) throws IOException {

        ArrayList<String> candidates = new ArrayList<String>();

        // First try the folder passed to the constructor.
        candidates.add(dataFolder + "/" + filename);

        // Then try common alternatives.
        candidates.add("data/" + filename);
        candidates.add("backend/data/" + filename);
        candidates.add("../backend/data/" + filename);
        candidates.add("src/backend/data/" + filename);

        for (int i = 0; i < candidates.size(); i++) {
            String pathText = candidates.get(i);
            Path path = Paths.get(pathText);

            if (Files.exists(path) && Files.isRegularFile(path)) {
                byte[] bytes = Files.readAllBytes(path);
                String content = new String(bytes, StandardCharsets.UTF_8);
                System.out.println("[LocationLoader] Reading: " + path.toAbsolutePath());
                return content;
            }
        }

        // Nothing worked — throw a clear error.
        throw new IOException("Could not find file '" + filename
                + "'. Tried: " + candidates);
    }

    /* -----------------------------------------------------------------
     * 7. HELPER: REMOVE // AND BLOCK COMMENTS
     * -----------------------------------------------------------------
     * JSON does not officially support comments, but we allow them
     * in our data files so students can annotate coordinates.
     * This method removes them before parsing.
     * ----------------------------------------------------------------- */
    private String stripComments(String text) {


        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < text.length()) {

            char c = text.charAt(i);

            // Check for block comment:  /* ... */
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i = i + 2;   // skip /*
                while (i + 1 < text.length()) {
                    if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                        i = i + 2;   // skip */
                        break;
                    }
                    i = i + 1;
                }
            }
            // Check for line comment:  // ...
            else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i = i + 1;
                }
            }
            // Otherwise copy the character.
            else {
                result.append(c);
                i = i + 1;
            }
        }

        return result.toString();
    }

    /* -----------------------------------------------------------------
     * 8. HELPER: EXTRACT A STRING FIELD
     * -----------------------------------------------------------------
     * Given a body like:  "name" : "Main Entrance", "x" : 80
     * and a key like "name", return "Main Entrance".
     *
     * Returns null if the key is not found.
     * ----------------------------------------------------------------- */
    private String getStringValue(String body, String key) {

        // Build the pattern we are looking for, e.g.  "name"
        String searchText = "\"" + key + "\"";

        int keyPosition = body.indexOf(searchText);
        if (keyPosition < 0) {
            return null;   // key not present
        }

        // Find the colon after the key.
        int colonPosition = body.indexOf(":", keyPosition + searchText.length());
        if (colonPosition < 0) {
            return null;
        }

        // Find the opening quote of the value.
        int openQuote = body.indexOf("\"", colonPosition + 1);
        if (openQuote < 0) {
            return null;
        }

        // Find the closing quote of the value.
        int closeQuote = body.indexOf("\"", openQuote + 1);
        if (closeQuote < 0) {
            return null;
        }

        return body.substring(openQuote + 1, closeQuote);
    }

    /* -----------------------------------------------------------------
     * 9. HELPER: EXTRACT AN INTEGER FIELD
     * -----------------------------------------------------------------
     * Given a body like:  "name" : "X", "x" : 80
     * and a key like "x", return 80.
     *
     * If the key is not found or the value is not a number,
     * return the given defaultValue.
     * ----------------------------------------------------------------- */
    private int getIntValue(String body, String key, int defaultValue) {

        String searchText = "\"" + key + "\"";
        int keyPosition = body.indexOf(searchText);
        if (keyPosition < 0) {
            return defaultValue;
        }

        int colonPosition = body.indexOf(":", keyPosition + searchText.length());
        if (colonPosition < 0) {
            return defaultValue;
        }

        // Skip any spaces after the colon.
        int start = colonPosition + 1;
        while (start < body.length() && body.charAt(start) == ' ') {
            start = start + 1;
        }

        // Handle a possible minus sign.
        int end = start;
        if (end < body.length() && body.charAt(end) == '-') {
            end = end + 1;
        }

        // Read the digits.
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end = end + 1;
        }

        if (end == start) {
            return defaultValue;   // no digits found
        }

        String numberText = body.substring(start, end);

        try {
            return Integer.parseInt(numberText);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}