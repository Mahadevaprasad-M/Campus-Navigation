/* =====================================================================
 * FILE:    app.js
 * PROJECT: Campus Navigation System
 * COLLEGE: Government Engineering College, Chamarajanagar
 * PURPOSE: All the interactive behaviour of the page:
 *
 *   1. Reads the current location from the URL (/loc/entrance)
 *   2. Fetches the list of locations from /api/locations
 *   3. Fills the dropdown and search box
 *   4. Fetches the route from /api/path when the user clicks Find Route
 *   5. Renders the step-by-step directions
 *   6. Reads the steps aloud with the Web Speech API
 *   7. Toggles between English and Kannada
 *   8. Emergency button -> route to nearest gate
 *   9. Shows open/closed badge using the "hours" field
 *
 * DEPENDS ON: map.js (uses window.MapRenderer)
 * ===================================================================== */


/* =====================================================================
 * WRAP IN A FUNCTION SO WE DO NOT POLLUTE GLOBAL SCOPE
 * ===================================================================== */
(function () {
  "use strict";

  /* -----------------------------------------------------------------
   * 1. CONFIGURATION
   * ----------------------------------------------------------------- */

  // If the backend is on a different URL (e.g. Render), we set this
  // before app.js loads. Otherwise it is empty and we use the same
  // origin (localhost:8080 in development).
  var API_BASE = "";
  if (window.API_BASE) {
    API_BASE = window.API_BASE;
  }

  // The two gates we consider as exits for the emergency button.
  var EXIT_NODES = ["entrance", "gate2"];

  /* -----------------------------------------------------------------
   * 2. STATE  (all mutable data lives here)
   * ----------------------------------------------------------------- */
  var state = {
    currentLocationId: null,   // where the user is standing (from URL)
    destinationId:     null,   // what they picked
    allLocations:      [],     // array from /api/locations
    locationsById:     {},     // id -> location
    lastPath:          null,   // last route response from /api/path
    lang:              "en"    // "en" or "kn"
  };


  /* -----------------------------------------------------------------
   * 3. TRANSLATIONS (English / Kannada)
   * ----------------------------------------------------------------- */
  var STRINGS = {
    en: {
      youAreHere:  "You are here",
      chooseDest:  "Where do you want to go?",
      orPick:      "Or pick from the full list",
      findRoute:   "Find Route",
      emergency:   "Emergency — Nearest Exit",
      directions:  "Directions",
      distance:    "Distance",
      walkTime:    "Walk time",
      newRoute:    "New Route",
      swap:        "Reverse",
      searchPlaceholder: "Search a place…",
      selectDest:  "Select a destination…",
      pickFirst:   "Please pick a destination first.",
      noPath:      "No walking route exists between these locations.",
      loadFail:    "Could not load locations. Is the backend running?",
      routeFail:   "Could not fetch the route. Try again.",
      unknownLoc:  "Unknown location — showing the main entrance instead.",
      arrivedAt:   "You have arrived at",
      alreadyThere:"You are already there."
    },
    kn: {
      youAreHere:  "ನೀವು ಇಲ್ಲಿದ್ದೀರಿ",
      chooseDest:  "ನೀವು ಎಲ್ಲಿಗೆ ಹೋಗಬೇಕು?",
      orPick:      "ಅಥವಾ ಪೂರ್ಣ ಪಟ್ಟಿಯಿಂದ ಆಯ್ಕೆಮಾಡಿ",
      findRoute:   "ದಾರಿ ತೋರಿಸಿ",
      emergency:   "ತುರ್ತು — ಹತ್ತಿರದ ನಿರ್ಗಮನ",
      directions:  "ದಾರಿ ಸೂಚನೆಗಳು",
      distance:    "ದೂರ",
      walkTime:    "ನಡಿಗೆ ಸಮಯ",
      newRoute:    "ಹೊಸ ದಾರಿ",
      swap:        "ತಿರುಗಿಸಿ",
      searchPlaceholder: "ಸ್ಥಳ ಹುಡುಕಿ…",
      selectDest:  "ಗಮ್ಯಸ್ಥಾನ ಆಯ್ಕೆಮಾಡಿ…",
      pickFirst:   "ದಯವಿಟ್ಟು ಮೊದಲು ಗಮ್ಯಸ್ಥಾನ ಆಯ್ಕೆಮಾಡಿ.",
      noPath:      "ಈ ಸ್ಥಳಗಳ ನಡುವೆ ನಡಿಗೆ ದಾರಿ ಇಲ್ಲ.",
      loadFail:    "ಸ್ಥಳಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ. ಸರ್ವರ್ ಚಾಲನೆಯಲ್ಲಿದೆಯೇ?",
      routeFail:   "ದಾರಿ ಪಡೆಯಲಾಗಲಿಲ್ಲ. ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.",
      unknownLoc:  "ಅಪರಿಚಿತ ಸ್ಥಳ — ಮುಖ್ಯ ಪ್ರವೇಶದ್ವಾರ ತೋರಿಸಲಾಗುತ್ತಿದೆ.",
      arrivedAt:   "ನೀವು ತಲುಪಿದ್ದೀರಿ",
      alreadyThere:"ನೀವು ಈಗಾಗಲೇ ಅಲ್ಲಿದ್ದೀರಿ."
    }
  };

  // Look up a key in the current language.
  function t(key) {
    var table = STRINGS[state.lang];
    if (table && table[key]) {
      return table[key];
    }
    if (STRINGS.en[key]) {
      return STRINGS.en[key];
    }
    return key;
  }


  /* -----------------------------------------------------------------
   * 4. DOM REFERENCES  (filled in during init)
   * ----------------------------------------------------------------- */
  var els = {};


  /* -----------------------------------------------------------------
   * 5. START  (called when the page finishes loading)
   * ----------------------------------------------------------------- */
  function start() {

    // ---- Grab all the element references once ----
    els.currentName   = document.getElementById("current-name");
    els.currentHours  = document.getElementById("current-hours");
    els.search        = document.getElementById("dest-search");
    els.searchClear   = document.getElementById("search-clear");
    els.suggestions   = document.getElementById("dest-suggestions");
    els.select        = document.getElementById("dest-select");
    els.findBtn       = document.getElementById("find-route-btn");
    els.emergencyBtn  = document.getElementById("emergency-btn");
    els.mapContainer  = document.getElementById("map-container");
    els.dirCard       = document.getElementById("directions-card");
    els.speakBtn      = document.getElementById("speak-btn");
    els.summaryDist   = document.getElementById("summary-distance");
    els.summaryTime   = document.getElementById("summary-time");
    els.stepsList     = document.getElementById("steps-list");
    els.resetBtn      = document.getElementById("reset-btn");
    els.swapBtn       = document.getElementById("swap-btn");
    els.loading       = document.getElementById("loading-overlay");
    els.errorToast    = document.getElementById("error-toast");
    els.errorText     = document.getElementById("error-text");
    els.errorClose    = document.getElementById("error-close");
    els.langBtn       = document.getElementById("lang-toggle");
    els.langLabel     = document.getElementById("lang-label");

    // ---- Initialise the SVG map ----
    if (window.MapRenderer && els.mapContainer) {
      window.MapRenderer.init(els.mapContainer);
      window.MapRenderer.onNodeClick(handleMapNodeClick);
    }

    // ---- Attach event listeners ----
    attachEvents();

    // ---- Read current location from URL ----
    state.currentLocationId = readLocationFromUrl();

    // ---- Fetch locations and set up the page ----
    loadLocations();
  }


  /* -----------------------------------------------------------------
   * 6. EVENT LISTENERS
   * ----------------------------------------------------------------- */
  function attachEvents() {

    if (els.search) {
      els.search.addEventListener("input", handleSearchInput);
      els.search.addEventListener("keydown", handleSearchKey);
    }
    if (els.searchClear) {
      els.searchClear.addEventListener("click", clearSearch);
    }
    if (els.select) {
      els.select.addEventListener("change", handleSelectChange);
    }
    if (els.findBtn) {
      els.findBtn.addEventListener("click", findRoute);
    }
    if (els.emergencyBtn) {
      els.emergencyBtn.addEventListener("click", emergencyRoute);
    }
    if (els.resetBtn) {
      els.resetBtn.addEventListener("click", resetRoute);
    }
    if (els.swapBtn) {
      els.swapBtn.addEventListener("click", swapRoute);
    }
    if (els.speakBtn) {
      els.speakBtn.addEventListener("click", speakDirections);
    }
    if (els.errorClose) {
      els.errorClose.addEventListener("click", hideToast);
    }
    if (els.langBtn) {
      els.langBtn.addEventListener("click", toggleLanguage);
    }

    // Hide suggestions when the user clicks elsewhere.
    document.addEventListener("click", function (evt) {
      if (els.suggestions && !els.suggestions.hidden) {
        if (els.suggestions.contains(evt.target)) {
          return;
        }
        if (els.search && els.search.contains(evt.target)) {
          return;
        }
        hideSuggestions();
      }
    });
  }


  /* -----------------------------------------------------------------
   * 7. READ CURRENT LOCATION FROM URL
   * -----------------------------------------------------------------
   *   /loc/entrance   -> "entrance"
   *   ?loc=library    -> "library"   (fallback)
   *   ?from=canteen   -> "canteen"   (fallback)
   * ----------------------------------------------------------------- */
  function readLocationFromUrl() {

    var path = window.location.pathname;

    // Check for /loc/<id>
    var marker = "/loc/";
    var markerIndex = path.indexOf(marker);
    if (markerIndex >= 0) {
      var id = path.substring(markerIndex + marker.length);
      // Remove any trailing slash.
      if (id.charAt(id.length - 1) === "/") {
        id = id.substring(0, id.length - 1);
      }
      if (id.length > 0) {
        return decodeURIComponent(id);
      }
    }

    // Fallback: check ?loc= and ?from=
    var query = window.location.search;
    var locFromQuery = getQueryParam(query, "loc");
    if (locFromQuery) {
      return locFromQuery;
    }
    var fromQuery = getQueryParam(query, "from");
    if (fromQuery) {
      return fromQuery;
    }

    return null;
  }

  // Very small query parser (no URLSearchParams — keep it simple).
  function getQueryParam(query, name) {
    if (!query) {
      return null;
    }
    var search = name + "=";
    var parts = query.substring(1).split("&");
    var i;
    for (i = 0; i < parts.length; i++) {
      if (parts[i].indexOf(search) === 0) {
        return decodeURIComponent(parts[i].substring(search.length));
      }
    }
    return null;
  }


  /* -----------------------------------------------------------------
   * 8. FETCH LOCATIONS
   * ----------------------------------------------------------------- */
  function loadLocations() {

    var url = API_BASE + "/api/locations";

    fetch(url)
      .then(function (res) {
        if (!res.ok) {
          throw new Error("HTTP " + res.status);
        }
        return res.json();
      })
      .then(function (list) {
        state.allLocations = list;
        state.locationsById = {};
        var i;
        for (i = 0; i < list.length; i++) {
          state.locationsById[list[i].id] = list[i];
        }

        // If the URL pointed at an unknown place, fall back to entrance.
        if (!state.currentLocationId
            || !state.locationsById[state.currentLocationId]) {
          if (state.currentLocationId) {
            showToast(t("unknownLoc"));
          }
          state.currentLocationId = "entrance";
        }

        // Draw the map and fill the dropdown.
        if (window.MapRenderer) {
          window.MapRenderer.renderLocations(list);
        }
        fillDropdown(list);
        updateCurrentLocationUI();
      })
      .catch(function (err) {
        console.error("[app] loadLocations failed:", err);
        showToast(t("loadFail"));
      });
  }


  /* -----------------------------------------------------------------
   * 9. FILL THE DROPDOWN
   * ----------------------------------------------------------------- */
  function fillDropdown(list) {

    if (!els.select) {
      return;
    }

    // Remove everything except the placeholder (first option).
    while (els.select.options.length > 1) {
      els.select.remove(1);
    }

    var i;
    for (i = 0; i < list.length; i++) {
      var loc = list[i];

      // Skip the current location — you do not walk to yourself.
      if (loc.id === state.currentLocationId) {
        continue;
      }

      var option = document.createElement("option");
      option.value = loc.id;
      option.textContent = loc.name + "  ·  " + typeLabel(loc.type);
      els.select.appendChild(option);
    }
  }


  /* -----------------------------------------------------------------
   * 10. UPDATE THE CURRENT LOCATION CARD
   * ----------------------------------------------------------------- */
  function updateCurrentLocationUI() {

    var loc = state.locationsById[state.currentLocationId];
    if (!loc || !els.currentName) {
      return;
    }

    els.currentName.textContent = loc.name;

    if (els.currentHours) {
      if (loc.hours) {
        els.currentHours.hidden = false;
        els.currentHours.textContent = "🕐 " + loc.hours;
      } else {
        els.currentHours.hidden = true;
      }
    }
  }


  /* -----------------------------------------------------------------
   * 11. FIND ROUTE  (the main action)
   * ----------------------------------------------------------------- */
  function findRoute() {

    if (!state.destinationId) {
      showToast(t("pickFirst"));
      return;
    }
    if (state.destinationId === state.currentLocationId) {
      showToast(t("alreadyThere"));
      return;
    }

    showLoading(true);

    var url = API_BASE
            + "/api/path?from=" + encodeURIComponent(state.currentLocationId)
            + "&to="   + encodeURIComponent(state.destinationId);

    fetch(url)
      .then(function (res) {
        if (!res.ok) {
          throw new Error("HTTP " + res.status);
        }
        return res.json();
      })
      .then(function (data) {

        if (data.error === "no_path") {
          showToast(t("noPath"));
          return;
        }

        state.lastPath = data;
        renderDirections(data);

        if (window.MapRenderer) {
          window.MapRenderer.highlightPath(data.path, data.from, data.to);
        }
      })
      .catch(function (err) {
        console.error("[app] findRoute failed:", err);
        showToast(t("routeFail"));
      })
      .then(function () {
        showLoading(false);   // always runs, even after an error
      });
  }


  /* -----------------------------------------------------------------
   * 12. RENDER DIRECTIONS
   * ----------------------------------------------------------------- */
  function renderDirections(data) {

    if (!data || !data.steps) {
      return;
    }

    // Distance and time summary.
    els.summaryDist.textContent = data.totalDistance + " m";
    els.summaryTime.textContent = data.estimatedTimeMinutes + " min";

    // Clear any old steps.
    while (els.stepsList.firstChild) {
      els.stepsList.removeChild(els.stepsList.firstChild);
    }

    // Build one <li> per step.
    var i;
    for (i = 0; i < data.steps.length; i++) {
      var step = data.steps[i];

      var li = document.createElement("li");
      li.className = "step";
      if (step.direction === "arrived") {
        li.className = li.className + " arrived";
      }

      // Circle with the step number.
      var circle = document.createElement("div");
      circle.className = "step-num dir-" + (step.direction || "straight");
      circle.textContent = step.step;

      // Text and meta line.
      var body = document.createElement("div");
      body.className = "step-body";

      var text = document.createElement("p");
      text.className = "step-text";
      text.textContent = step.instruction;

      var meta = document.createElement("p");
      meta.className = "step-meta";
      if (step.distance > 0) {
        meta.textContent = step.distance + " m · " + (step.direction || "straight");
      } else {
        meta.textContent = step.nodeName || "";
      }

      body.appendChild(text);
      body.appendChild(meta);

      li.appendChild(circle);
      li.appendChild(body);
      els.stepsList.appendChild(li);
    }

    // Show the card.
    els.dirCard.hidden = false;
    els.dirCard.scrollIntoView({ behavior: "smooth", block: "start" });
  }


  /* -----------------------------------------------------------------
   * 13. EMERGENCY ROUTE  (nearest gate)
   * -----------------------------------------------------------------
   * We do not need the graph for this. We pick the gate that is
   * closest to the current location using its x,y coordinates.
   * ----------------------------------------------------------------- */
  function emergencyRoute() {

    var current = state.locationsById[state.currentLocationId];
    if (!current) {
      return;
    }

    var bestId = null;
    var bestDistance = 999999999;

    var i;
    for (i = 0; i < EXIT_NODES.length; i++) {
      var gateId = EXIT_NODES[i];
      var gate = state.locationsById[gateId];
      if (!gate) {
        continue;
      }
      var dx = gate.x - current.x;
      var dy = gate.y - current.y;
      var distance = Math.sqrt(dx * dx + dy * dy);

      if (distance < bestDistance) {
        bestDistance = distance;
        bestId = gateId;
      }
    }

    if (bestId) {
      state.destinationId = bestId;
      if (els.select) {
        els.select.value = bestId;
      }
      findRoute();
    }
  }


  /* -----------------------------------------------------------------
   * 14. RESET / SWAP
   * ----------------------------------------------------------------- */
  function resetRoute() {

    state.destinationId = null;

    if (els.select) {
      els.select.value = "";
    }
    if (els.search) {
      els.search.value = "";
    }
    if (els.searchClear) {
      els.searchClear.hidden = true;
    }
    if (els.dirCard) {
      els.dirCard.hidden = true;
    }
    if (els.stepsList) {
      while (els.stepsList.firstChild) {
        els.stepsList.removeChild(els.stepsList.firstChild);
      }
    }

    hideSuggestions();

    if (window.MapRenderer) {
      window.MapRenderer.clearPath();
    }
  }

  function swapRoute() {

    if (!state.lastPath) {
      return;
    }

    var oldFrom = state.lastPath.from;
    var oldTo = state.lastPath.to;

    // Walk backwards: the destination becomes the new start.
    state.currentLocationId = oldTo;
    state.destinationId = oldFrom;

    var loc = state.locationsById[oldTo];
    if (els.currentName && loc) {
      els.currentName.textContent = loc.name;
    }

    // Repopulate the dropdown because the current location changed.
    fillDropdown(state.allLocations);

    if (els.select) {
      els.select.value = oldFrom;
    }
    if (els.search) {
      var dest = state.locationsById[oldFrom];
      els.search.value = dest ? dest.name : "";
    }

    findRoute();
  }


  /* -----------------------------------------------------------------
   * 15. DROPDOWN AND SEARCH HANDLERS
   * ----------------------------------------------------------------- */
  function handleSelectChange(evt) {
    state.destinationId = evt.target.value;

    if (els.findBtn) {
      els.findBtn.disabled = !state.destinationId;
    }
    if (els.search && state.destinationId) {
      var loc = state.locationsById[state.destinationId];
      if (loc) {
        els.search.value = loc.name;
      }
    }
    hideSuggestions();
  }

  function handleSearchInput(evt) {

    var query = evt.target.value.toLowerCase();
    query = query.replace(/^\s+|\s+$/g, "");   // trim

    if (els.searchClear) {
      els.searchClear.hidden = (query.length === 0);
    }

    if (query.length < 1) {
      hideSuggestions();
      return;
    }

    // Filter locations by name.
    var matches = [];
    var i;
    for (i = 0; i < state.allLocations.length; i++) {
      var loc = state.allLocations[i];

      if (loc.id === state.currentLocationId) {
        continue;
      }
      var nameLower = (loc.name || "").toLowerCase();
      var idLower = (loc.id || "").toLowerCase();

      if (nameLower.indexOf(query) >= 0 || idLower.indexOf(query) >= 0) {
        matches.push(loc);
      }
      if (matches.length >= 8) {
        break;
      }
    }

    showSuggestions(matches);
  }

  function handleSearchKey(evt) {
    if (evt.key === "Enter") {
      var first = els.suggestions.querySelector("li");
      if (first) {
        first.click();
      }
    } else if (evt.key === "Escape") {
      hideSuggestions();
    }
  }

  function showSuggestions(items) {

    if (!els.suggestions) {
      return;
    }

    // Clear.
    while (els.suggestions.firstChild) {
      els.suggestions.removeChild(els.suggestions.firstChild);
    }

    if (items.length === 0) {
      els.suggestions.hidden = true;
      return;
    }

    var i;
    for (i = 0; i < items.length; i++) {
      var loc = items[i];

      var li = document.createElement("li");

      var name = document.createElement("span");
      name.className = "sug-name";
      name.textContent = loc.name;

      var type = document.createElement("span");
      type.className = "sug-type";
      type.textContent = typeLabel(loc.type);

      li.appendChild(name);
      li.appendChild(type);

      // Attach click handler using a closure that captures loc.id.
      li.onclick = makeSuggestionClickHandler(loc);

      els.suggestions.appendChild(li);
    }

    els.suggestions.hidden = false;
  }

  // Helper that returns a click handler for a specific location.
  // This avoids the classic "var in loop" closure bug.
  function makeSuggestionClickHandler(loc) {
    return function () {
      state.destinationId = loc.id;
      if (els.select) {
        els.select.value = loc.id;
      }
      if (els.search) {
        els.search.value = loc.name;
      }
      if (els.findBtn) {
        els.findBtn.disabled = false;
      }
      hideSuggestions();
    };
  }

  function hideSuggestions() {
    if (els.suggestions) {
      els.suggestions.hidden = true;
      while (els.suggestions.firstChild) {
        els.suggestions.removeChild(els.suggestions.firstChild);
      }
    }
  }

  function clearSearch() {
    if (els.search) {
      els.search.value = "";
    }
    if (els.searchClear) {
      els.searchClear.hidden = true;
    }
    hideSuggestions();
  }


  /* -----------------------------------------------------------------
   * 16. MAP NODE CLICK
   * -----------------------------------------------------------------
   * When the user taps a node on the map, treat it as "set this as
   * destination".
   * ----------------------------------------------------------------- */
  function handleMapNodeClick(nodeId) {
    if (!nodeId) {
      return;
    }
    if (nodeId === state.currentLocationId) {
      return;
    }

    state.destinationId = nodeId;
    if (els.select) {
      els.select.value = nodeId;
    }
    if (els.findBtn) {
      els.findBtn.disabled = false;
    }
    var loc = state.locationsById[nodeId];
    if (els.search && loc) {
      els.search.value = loc.name;
    }
  }


  /* -----------------------------------------------------------------
   * 17. SPEAK DIRECTIONS  (Web Speech API)
   * ----------------------------------------------------------------- */
  function speakDirections() {

    if (!state.lastPath || !state.lastPath.steps) {
      return;
    }
    if (!("speechSynthesis" in window)) {
      showToast("Voice guidance is not supported in this browser.");
      return;
    }

    // Stop anything currently being spoken.
    window.speechSynthesis.cancel();

    // Join every step into one long sentence.
    var text = "";
    var i;
    for (i = 0; i < state.lastPath.steps.length; i++) {
      if (i > 0) {
        text = text + ". ";
      }
      text = text + state.lastPath.steps[i].instruction;
    }

    var utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 0.95;
    utterance.pitch = 1.0;

    // Try to pick a voice that matches the current language.
    var voices = window.speechSynthesis.getVoices();
    var wantLang = (state.lang === "kn") ? "kn" : "en";
    for (i = 0; i < voices.length; i++) {
      if (voices[i].lang && voices[i].lang.toLowerCase().indexOf(wantLang) === 0) {
        utterance.voice = voices[i];
        break;
      }
    }

    // Show the "speaking" pulse while talking.
    if (els.speakBtn) {
      els.speakBtn.classList.add("speaking");
    }

    utterance.onend = function () {
      if (els.speakBtn) {
        els.speakBtn.classList.remove("speaking");
      }
    };

    window.speechSynthesis.speak(utterance);
  }


  /* -----------------------------------------------------------------
   * 18. LANGUAGE TOGGLE
   * ----------------------------------------------------------------- */
  function toggleLanguage() {

    if (state.lang === "en") {
      state.lang = "kn";
    } else {
      state.lang = "en";
    }

    // Update the search placeholder.
    if (els.search) {
      els.search.placeholder = t("searchPlaceholder");
    }

    // Update the dropdown placeholder (the first option).
    if (els.select && els.select.options.length > 0) {
      els.select.options[0].textContent = t("selectDest");
    }

    // Update the "Find Route" button.
    if (els.findBtn) {
      var label = els.findBtn.querySelector("span");
      if (label) {
        label.textContent = t("findRoute");
      }
    }

    // Update the "Emergency" button.
    if (els.emergencyBtn) {
      var emLabel = els.emergencyBtn.querySelector("span");
      if (emLabel) {
        emLabel.textContent = t("emergency");
      }
    }

    // Update the section headings.
    var headings = document.querySelectorAll(".section-title");
    var i;
    for (i = 0; i < headings.length; i++) {
      var text = headings[i].textContent;
      if (text.indexOf("Where do you") >= 0 || text.indexOf("ಎಲ್ಲಿಗೆ") >= 0) {
        headings[i].textContent = t("chooseDest");
      }
    }

    // Update the small "You are here" label.
    var label = document.querySelector(".current-label");
    if (label) {
      label.textContent = t("youAreHere");
    }

    // Change the button face: "ಕ" when in English, "EN" when in Kannada.
    if (els.langLabel) {
      if (state.lang === "en") {
        els.langLabel.textContent = "ಕ";
      } else {
        els.langLabel.textContent = "EN";
      }
    }
  }


  /* -----------------------------------------------------------------
   * 19. HELPERS
   * ----------------------------------------------------------------- */

  // Convert a type like "building" into a short label for the UI.
  function typeLabel(type) {
    if (!type) {
      return "place";
    }
    if (type === "gate")     { return "gate"; }
    if (type === "junction") { return "path"; }
    if (type === "building") { return "building"; }
    if (type === "lab")      { return "lab"; }
    if (type === "office")   { return "office"; }
    if (type === "facility") { return "facility"; }
    return type;
  }

  // Show or hide the loading overlay.
  function showLoading(on) {
    if (els.loading) {
      els.loading.hidden = !on;
    }
  }

  // Show the red toast with a message.
  var toastTimer = null;
  function showToast(message) {
    if (!els.errorToast || !els.errorText) {
      return;
    }
    els.errorText.textContent = message;
    els.errorToast.hidden = false;

    // Auto-dismiss after 4 seconds.
    if (toastTimer) {
      clearTimeout(toastTimer);
    }
    toastTimer = setTimeout(hideToast, 4000);
  }

  function hideToast() {
    if (els.errorToast) {
      els.errorToast.hidden = true;
    }
  }


  /* =================================================================
   * 20. KICK OFF
   * ================================================================= */
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }

})();