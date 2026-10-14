/* =====================================================================
 * FILE:    map.js
 * PROJECT: Campus Navigation System
 * COLLEGE: Government Engineering College, Chamarajanagar
 * PURPOSE: Draws the campus map as an SVG inside #map-container.
 *          Provides a small API on window.MapRenderer that app.js
 *          calls to:
 *             - initialise the SVG
 *             - draw all locations
 *             - highlight a path
 *             - clear the path
 *
 * We build SVG elements using document.createElementNS().
 * Simple helper functions keep the code short and readable.
 * ===================================================================== */


/* =====================================================================
 * WRAP EVERYTHING IN A FUNCTION SO WE DO NOT POLLUTE THE GLOBAL SCOPE
 * ===================================================================== */
(function () {
  "use strict";

  /* -----------------------------------------------------------------
   * 1. CONSTANTS
   * ----------------------------------------------------------------- */

  // SVG namespace — required for creating SVG elements with JS.
  var SVG_NS = "http://www.w3.org/2000/svg";

  // The map coordinate space (matches locations.json).
  var MAP_WIDTH  = 800;
  var MAP_HEIGHT = 600;

  // Node circle radius and label offset.
  var NODE_RADIUS  = 9;
  var LABEL_OFFSET = 22;

  // Arrow triangle size.
  var ARROW_SIZE = 8;

  // Two nodes closer than this (in map units) get a faint road line
  // between them. Purely decorative — the real edges come from the API.
  var ROAD_MAX_DISTANCE = 170;

  /* -----------------------------------------------------------------
   * 2. STATE  (all mutable data lives here)
   * ----------------------------------------------------------------- */
  var state = {
    container:   null,   // the <div id="map-container">
    svg:         null,   // the <svg> root
    layerRoads:  null,   // group for decorative road lines
    layerNodes:  null,   // group for node circles
    layerLabels: null,   // group for text labels
    layerPath:   null,   // group for the highlighted route

    locations:   {},     // id -> location object (name, x, y, type)
    nodeGroups:  {},     // id -> <g> element (for styling start/end)

    onNodeClick: null    // callback set by app.js
  };


  /* =================================================================
   * 3. PUBLIC API  (window.MapRenderer)
   * ================================================================= */
  var MapRenderer = {

    /* ---------------------------------------------------------------
     * init(container)
     * Called once at startup. Creates the SVG skeleton.
     * --------------------------------------------------------------- */
    init: function (container) {
      if (!container) {
        console.error("[MapRenderer] No container element passed to init()");
        return;
      }

      state.container = container;

      // Remove the placeholder spinner (the <div class="map-placeholder">).
      var placeholder = container.querySelector(".map-placeholder");
      if (placeholder) {
        placeholder.parentNode.removeChild(placeholder);
      }

      // ---- Create the root <svg> ----
      var svg = document.createElementNS(SVG_NS, "svg");
      svg.setAttribute("viewBox", "0 0 " + MAP_WIDTH + " " + MAP_HEIGHT);
      svg.setAttribute("preserveAspectRatio", "xMidYMid meet");
      svg.setAttribute("role", "img");
      svg.setAttribute("aria-label", "Campus map");

      // ---- Background rectangle ----
      var bg = document.createElementNS(SVG_NS, "rect");
      bg.setAttribute("x", 0);
      bg.setAttribute("y", 0);
      bg.setAttribute("width", MAP_WIDTH);
      bg.setAttribute("height", MAP_HEIGHT);
      bg.setAttribute("class", "map-bg");
      svg.appendChild(bg);

      // ---- Layer groups (order matters = painting order) ----
      state.layerRoads  = createGroup("roads");
      state.layerNodes  = createGroup("nodes");
      state.layerLabels = createGroup("labels");
      state.layerPath   = createGroup("path");

      svg.appendChild(state.layerRoads);
      svg.appendChild(state.layerNodes);
      svg.appendChild(state.layerLabels);
      svg.appendChild(state.layerPath);

      // ---- Attach to the page ----
      container.appendChild(svg);
      state.svg = svg;
    },


    /* ---------------------------------------------------------------
     * renderLocations(locations)
     * Draw every location as a circle + label.
     * `locations` is the array returned by /api/locations.
     * --------------------------------------------------------------- */
    renderLocations: function (locations) {

      if (!state.svg) {
        console.error("[MapRenderer] Call init() before renderLocations()");
        return;
      }
      if (!locations || locations.length === 0) {
        return;
      }

      // Clear any previous drawing.
      clearChildren(state.layerRoads);
      clearChildren(state.layerNodes);
      clearChildren(state.layerLabels);
      state.nodeGroups = {};
      state.locations = {};

      // ---- Index locations by id so lookups are O(1) ----
      var i;
      for (i = 0; i < locations.length; i++) {
        state.locations[locations[i].id] = locations[i];
      }

      // ---- Draw decorative roads first (they go under the nodes) ----
      drawRoads(locations);

      // ---- Draw every node ----
      for (i = 0; i < locations.length; i++) {
        var loc = locations[i];
        var group = createNodeGroup(loc);
        state.layerNodes.appendChild(group);
        state.nodeGroups[loc.id] = group;
      }

      // ---- Draw labels on a separate layer so they sit above the path ----
      for (i = 0; i < locations.length; i++) {
        var label = createLabel(locations[i]);
        state.layerLabels.appendChild(label);
      }
    },


    /* ---------------------------------------------------------------
     * highlightPath(nodeIds, startId, endId)
     * Draws a blue polyline through the given nodes, plus small
     * arrows on each segment. Also marks the start/end nodes.
     * --------------------------------------------------------------- */
    highlightPath: function (nodeIds, startId, endId) {

      if (!state.svg) {
        return;
      }
      if (!nodeIds || nodeIds.length === 0) {
        return;
      }

      // Remove any previous path first.
      this.clearPath();

      // Collect the coordinates of each node in order.
      var points = [];
      var i;
      for (i = 0; i < nodeIds.length; i++) {
        var loc = state.locations[nodeIds[i]];
        if (loc) {
          points.push({ x: loc.x, y: loc.y });
        }
      }

      if (points.length < 2) {
        return;
      }

      // ---- Draw the main polyline ----
      var polyline = document.createElementNS(SVG_NS, "polyline");
      var pointText = "";
      for (i = 0; i < points.length; i++) {
        if (i > 0) {
          pointText = pointText + " ";
        }
        pointText = pointText + points[i].x + "," + points[i].y;
      }
      polyline.setAttribute("points", pointText);
      polyline.setAttribute("class", "map-path");
      state.layerPath.appendChild(polyline);

      // ---- Draw one arrow per segment ----
      for (i = 0; i < points.length - 1; i++) {
        drawArrow(points[i], points[i + 1]);
      }

      // ---- Mark start and end nodes with special classes ----
      var id;
      for (id in state.nodeGroups) {
        if (state.nodeGroups.hasOwnProperty(id)) {
          var g = state.nodeGroups[id];
          g.classList.remove("start");
          g.classList.remove("end");
          g.classList.remove("on-path");
        }
      }

      for (i = 0; i < nodeIds.length; i++) {
        var nodeId = nodeIds[i];
        if (state.nodeGroups[nodeId]) {
          state.nodeGroups[nodeId].classList.add("on-path");
        }
      }

      if (state.nodeGroups[startId]) {
        state.nodeGroups[startId].classList.add("start");
      }
      if (state.nodeGroups[endId]) {
        state.nodeGroups[endId].classList.add("end");
      }
    },


    /* ---------------------------------------------------------------
     * clearPath()
     * Removes the polyline, arrows, and start/end markers.
     * --------------------------------------------------------------- */
    clearPath: function () {

      if (!state.layerPath) {
        return;
      }

      clearChildren(state.layerPath);

      var id;
      for (id in state.nodeGroups) {
        if (state.nodeGroups.hasOwnProperty(id)) {
          var g = state.nodeGroups[id];
          g.classList.remove("start");
          g.classList.remove("end");
          g.classList.remove("on-path");
        }
      }
    },


    /* ---------------------------------------------------------------
     * onNodeClick(fn)
     * Lets app.js register a callback that runs when a node is tapped.
     * --------------------------------------------------------------- */
    onNodeClick: function (fn) {
      if (typeof fn === "function") {
        state.onNodeClick = fn;
      } else {
        state.onNodeClick = null;
      }
    },


    /* ---------------------------------------------------------------
     * getLocation(id)
     * Returns the raw location object (or null).
     * --------------------------------------------------------------- */
    getLocation: function (id) {
      if (state.locations[id]) {
        return state.locations[id];
      }
      return null;
    }
  };


  /* =================================================================
   * 4. INTERNAL HELPER FUNCTIONS
   * ================================================================= */

  /* ---------------------------------------------------------------
   * createGroup(name)
   * Creates a <g> element with a data-layer attribute.
   * --------------------------------------------------------------- */
  function createGroup(name) {
    var g = document.createElementNS(SVG_NS, "g");
    g.setAttribute("data-layer", name);
    return g;
  }


  /* ---------------------------------------------------------------
   * clearChildren(element)
   * Removes every child node. Used before redrawing.
   * --------------------------------------------------------------- */
  function clearChildren(element) {
    while (element.firstChild) {
      element.removeChild(element.firstChild);
    }
  }


  /* ---------------------------------------------------------------
   * createNodeGroup(loc)
   * Builds a <g class="node type-..."> containing:
   *    - a large invisible circle (makes taps easy on mobile)
   *    - a visible circle
   * A click listener reports the node id to app.js.
   * --------------------------------------------------------------- */
  function createNodeGroup(loc) {

    var g = document.createElementNS(SVG_NS, "g");
    g.setAttribute("class", "node type-" + (loc.type || "unknown"));
    g.setAttribute("data-id", loc.id);
    g.setAttribute("transform", "translate(" + loc.x + " " + loc.y + ")");
    g.setAttribute("role", "button");
    g.setAttribute("aria-label", loc.name || loc.id);

    // Invisible hit target (bigger than the visible circle).
    var hit = document.createElementNS(SVG_NS, "circle");
    hit.setAttribute("r", NODE_RADIUS + 8);
    hit.setAttribute("fill", "transparent");
    hit.style.cursor = "pointer";
    g.appendChild(hit);

    // Visible circle.
    var circle = document.createElementNS(SVG_NS, "circle");
    circle.setAttribute("r", NODE_RADIUS);
    circle.setAttribute("class", "map-node type-" + (loc.type || "unknown"));
    g.appendChild(circle);

    // Click handler reports the id to app.js.
    g.addEventListener("click", function () {
      if (state.onNodeClick) {
        state.onNodeClick(loc.id);
      }
    });

    return g;
  }


  /* ---------------------------------------------------------------
   * createLabel(loc)
   * Creates a <text> element below the node.
   * --------------------------------------------------------------- */
  function createLabel(loc) {
    var text = document.createElementNS(SVG_NS, "text");
    text.setAttribute("x", loc.x);
    text.setAttribute("y", loc.y + LABEL_OFFSET);
    text.setAttribute("class", "map-label");
    text.setAttribute("text-anchor", "middle");
    text.textContent = shorten(loc.name || loc.id, 18);
    return text;
  }


  /* ---------------------------------------------------------------
   * drawRoads(locations)
   * Draws faint grey lines between nodes that are close to each
   * other. This is only for visual flair — the real route is drawn
   * by highlightPath().
   * --------------------------------------------------------------- */
  function drawRoads(locations) {

    var i, j;

    for (i = 0; i < locations.length; i++) {
      for (j = i + 1; j < locations.length; j++) {

        var a = locations[i];
        var b = locations[j];

        var dx = a.x - b.x;
        var dy = a.y - b.y;
        var distance = Math.sqrt(dx * dx + dy * dy);

        if (distance > ROAD_MAX_DISTANCE) {
          continue;
        }

        var line = document.createElementNS(SVG_NS, "line");
        line.setAttribute("x1", a.x);
        line.setAttribute("y1", a.y);
        line.setAttribute("x2", b.x);
        line.setAttribute("y2", b.y);
        line.setAttribute("class", "map-road");
        state.layerRoads.appendChild(line);
      }
    }
  }


  /* ---------------------------------------------------------------
   * drawArrow(from, to)
   * Draws a small triangle at the midpoint of the segment,
   * rotated to point from `from` towards `to`.
   * --------------------------------------------------------------- */
  function drawArrow(from, to) {

    var midX = (from.x + to.x) / 2;
    var midY = (from.y + to.y) / 2;

    // atan2 gives us the angle in radians; convert to degrees.
    var angleRad = Math.atan2(to.y - from.y, to.x - from.x);
    var angleDeg = angleRad * 180 / Math.PI;

    var arrow = document.createElementNS(SVG_NS, "polygon");
    arrow.setAttribute("class", "map-arrow");

    // Triangle points (centred on 0,0 pointing to the right).
    var side = ARROW_SIZE * 1.4;
    var half = ARROW_SIZE * 0.7;
    var pointsText = "0,0 " + (-side) + "," + half + " " + (-side) + "," + (-half);
    arrow.setAttribute("points", pointsText);

    // Translate to midpoint and rotate.
    arrow.setAttribute(
      "transform",
      "translate(" + midX + " " + midY + ") rotate(" + angleDeg + ")"
    );

    state.layerPath.appendChild(arrow);
  }


  /* ---------------------------------------------------------------
   * shorten(text, maxLength)
   * Truncates long names with an ellipsis.
   * --------------------------------------------------------------- */
  function shorten(text, maxLength) {
    if (!text) {
      return "";
    }
    if (text.length <= maxLength) {
      return text;
    }
    return text.substring(0, maxLength - 1) + "…";
  }


  /* =================================================================
   * 5. EXPORT
   * ================================================================= */
  window.MapRenderer = MapRenderer;

})();