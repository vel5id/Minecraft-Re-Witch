# Grimoire → Thaumonomicon — P1 (Renderer + de-Patchouli) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Patchouli `grimoire` book with the mod's own `GrimoireScreen` — tabbed per-category node-webs rendering the migrated 17 entries with text/item/recipe/image/multiblock/entity pages — with everything unlocked (no gate yet) and the Patchouli dependency removed.

**Architecture:** A pure, id-based content model (`com.vel5id.hexerei.grimoire`) parsed from our own JSON by a client resource-reload listener; a client `GrimoireScreen` that draws category tabs, a node-web per category (authored positions + prereq connector lines), and a paged entry view. The model holds only ids (`ResourceLocation`); the renderer resolves them to `ItemStack` / recipes / `EntityType` at draw time, so all parsing/layout logic is unit-testable without a Minecraft bootstrap. Gating, knowledge storage, and networking are **out of scope for P1** (they are P2/P3).

**Tech Stack:** NeoForge 1.21.1, Java 21, Gson (`JsonObject`, already on the classpath), JUnit 5 (pure-logic tier), vanilla `Screen`/`GuiGraphics`, `InventoryScreen.renderEntityInInventoryFollowsMouse` (entity page), `BlockRenderDispatcher` over a fake `BlockAndTintGetter` (multiblock page).

## Global Constraints

- Target stack is **NeoForge 1.21.1 / Java 21**; build with `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon …` (the JDK 21 toolchain auto-downloads).
- **No parallel currency / no new knowledge resource** — P1 introduces no gating at all, so this is trivially satisfied; do not add unlock logic here.
- **RU parity is mandatory** — every migrated entry exists in both `grimoire/en_us/` and `grimoire/ru_ru/` (project memory: the RU pack is first-class).
- **Two-tier testing** — pure logic (model, parser, node layout) is unit-tested in `src/test/java/`; GUI/Screen behavior is verified by **launching the client** and looking at it (the project's rule for world/GUI-dependent code), not by tightening flaky GameTests.
- **Commit convention:** `type(hexerei): summary` (e.g. `feat(hexerei): …`). End commit bodies with the Co-Authored-By trailer used in this repo.
- **Constitution:** P1 is the `read` verb's presentation layer only; it writes no State. Do not add discovery/Act writes in P1.
- Model stores **ids**, never resolved game objects — keeps the parser pure.

---

### Task 1: Grimoire content model (pure records)

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/grimoire/GrimoireBook.java`
- Create: `src/main/java/com/vel5id/hexerei/grimoire/GrimoirePage.java`
- Test: `src/test/java/com/vel5id/hexerei/grimoire/GrimoireModelTest.java`

**Interfaces:**
- Produces:
  - `record GrimoireBook(Map<ResourceLocation, GrimoireBook.Category> categories, List<GrimoireBook.Entry> entries)`
  - `record GrimoireBook.Category(ResourceLocation id, String nameKey, String descKey, ResourceLocation icon, int sortnum)`
  - `record GrimoireBook.Entry(ResourceLocation id, ResourceLocation category, GrimoireBook.NodePos node, List<ResourceLocation> prereq, ResourceLocation icon, String nameKey, List<GrimoirePage> pages)` — `node` may be `null` (grid fallback handled in Task 3).
  - `record GrimoireBook.NodePos(int x, int y)`
  - `sealed interface GrimoirePage permits GrimoirePage.Text, GrimoirePage.Item, GrimoirePage.Recipe, GrimoirePage.Image, GrimoirePage.Multiblock, GrimoirePage.Entity {}`
    - `record Text(String textKey) implements GrimoirePage`
    - `record Item(ResourceLocation item, String textKey) implements GrimoirePage`
    - `record Recipe(ResourceLocation recipeId, String textKey) implements GrimoirePage`
    - `record Image(ResourceLocation texture, String textKey) implements GrimoirePage`
    - `record Multiblock(ResourceLocation structure, String textKey) implements GrimoirePage`
    - `record Entity(ResourceLocation entityType, String textKey) implements GrimoirePage`

- [ ] **Step 1: Write the failing test**

```java
package com.vel5id.hexerei.grimoire;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GrimoireModelTest {

    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath("hexerei", p); }

    @Test
    void entryHoldsItsPagesAndNode() {
        GrimoirePage.Entity ent = new GrimoirePage.Entity(rl("wolf_familiar"), "hexerei.grimoire.x");
        GrimoireBook.Entry e = new GrimoireBook.Entry(
                rl("rituals/bound_beast"), rl("rituals"),
                new GrimoireBook.NodePos(40, 80), List.of(rl("rituals/circles")),
                rl("bone"), "hexerei.grimoire.bound_beast.name", List.of(ent));
        assertEquals(40, e.node().x());
        assertEquals(1, e.pages().size());
        assertInstanceOf(GrimoirePage.Entity.class, e.pages().get(0));
    }

    @Test
    void bookIndexesCategoriesById() {
        GrimoireBook.Category c = new GrimoireBook.Category(rl("herbs"), "k.name", "k.desc", rl("mandrake_root"), 1);
        GrimoireBook book = new GrimoireBook(Map.of(c.id(), c), List.of());
        assertSame(c, book.categories().get(rl("herbs")));
    }

    @Test
    void nodeMayBeNullForGridFallback() {
        GrimoireBook.Entry e = new GrimoireBook.Entry(
                rl("herbs/garlic"), rl("herbs"), null, List.of(), rl("garlic"),
                "k", List.of(new GrimoirePage.Text("k.body")));
        assertNull(e.node());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.grimoire.GrimoireModelTest'`
Expected: FAIL — `GrimoireBook`/`GrimoirePage` do not exist (compile error).

- [ ] **Step 3: Write the model**

`GrimoirePage.java`:

```java
package com.vel5id.hexerei.grimoire;

import net.minecraft.resources.ResourceLocation;

/** A single page in a Grimoire entry. The model holds ids only; the renderer resolves them at draw time. */
public sealed interface GrimoirePage
        permits GrimoirePage.Text, GrimoirePage.Item, GrimoirePage.Recipe,
                GrimoirePage.Image, GrimoirePage.Multiblock, GrimoirePage.Entity {

    String textKey();

    record Text(String textKey) implements GrimoirePage {}
    record Item(ResourceLocation item, String textKey) implements GrimoirePage {}
    record Recipe(ResourceLocation recipeId, String textKey) implements GrimoirePage {}
    record Image(ResourceLocation texture, String textKey) implements GrimoirePage {}
    record Multiblock(ResourceLocation structure, String textKey) implements GrimoirePage {}
    record Entity(ResourceLocation entityType, String textKey) implements GrimoirePage {}
}
```

`GrimoireBook.java`:

```java
package com.vel5id.hexerei.grimoire;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** The parsed Grimoire: categories keyed by id, plus a flat list of entries. Immutable, pure data. */
public record GrimoireBook(Map<ResourceLocation, Category> categories, List<Entry> entries) {

    public record Category(ResourceLocation id, String nameKey, String descKey, ResourceLocation icon, int sortnum) {}

    /** {@code node} may be null → the layout grid-places it (Task 3). */
    public record Entry(ResourceLocation id, ResourceLocation category, NodePos node,
                        List<ResourceLocation> prereq, ResourceLocation icon, String nameKey,
                        List<GrimoirePage> pages) {}

    public record NodePos(int x, int y) {}

    /** Entries belonging to one category, in declaration order. */
    public List<Entry> entriesIn(ResourceLocation categoryId) {
        return entries.stream().filter(e -> e.category().equals(categoryId)).toList();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.grimoire.GrimoireModelTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/vel5id/hexerei/grimoire/ src/test/java/com/vel5id/hexerei/grimoire/GrimoireModelTest.java
git commit -m "feat(hexerei): grimoire content model — pure id-based records"
```

---

### Task 2: `GrimoireParser` — JSON → model (pure, unit-tested)

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/grimoire/GrimoireParser.java`
- Test: `src/test/java/com/vel5id/hexerei/grimoire/GrimoireParserTest.java`

**Interfaces:**
- Consumes: Task 1 records; `com.google.gson.JsonObject`.
- Produces:
  - `static GrimoireBook.Category parseCategory(ResourceLocation id, JsonObject json)`
  - `static GrimoireBook.Entry parseEntry(ResourceLocation id, JsonObject json)`
  - Entry JSON schema: `{ "category", "node":{"x","y"}?, "prereq":[..]?, "icon", "name" (lang key), "pages":[{"type":"text|item|recipe|image|multiblock|entity", ...}] }`. Page fields: `text` (lang key) on all; `item`/`recipeId`/`texture`/`structure`/`entity` on the respective types.

- [ ] **Step 1: Write the failing test**

```java
package com.vel5id.hexerei.grimoire;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrimoireParserTest {

    private static JsonObject obj(String s) { return JsonParser.parseString(s).getAsJsonObject(); }
    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath("hexerei", p); }

    @Test
    void parsesEntryWithNodeAndPrereq() {
        GrimoireBook.Entry e = GrimoireParser.parseEntry(rl("altars/power"), obj("""
            { "category":"hexerei:altars", "node":{"x":120,"y":40},
              "prereq":["hexerei:altars/landing"], "icon":"hexerei:altar",
              "name":"hexerei.grimoire.altars.power.name",
              "pages":[{"type":"text","text":"hexerei.grimoire.altars.power.p0"}] }
            """));
        assertEquals(rl("altars"), e.category());
        assertEquals(120, e.node().x());
        assertEquals(rl("altars/landing"), e.prereq().get(0));
        assertInstanceOf(GrimoirePage.Text.class, e.pages().get(0));
    }

    @Test
    void missingNodeAndPrereqDefaultToNullAndEmpty() {
        GrimoireBook.Entry e = GrimoireParser.parseEntry(rl("herbs/garlic"), obj("""
            { "category":"hexerei:herbs", "icon":"hexerei:garlic", "name":"k",
              "pages":[{"type":"text","text":"k.body"}] }
            """));
        assertNull(e.node());
        assertTrue(e.prereq().isEmpty());
    }

    @Test
    void parsesAllPageTypes() {
        GrimoireBook.Entry e = GrimoireParser.parseEntry(rl("x/y"), obj("""
            { "category":"hexerei:x", "icon":"hexerei:a", "name":"k", "pages":[
              {"type":"item","item":"hexerei:mandrake_root","text":"k0"},
              {"type":"recipe","recipeId":"hexerei:altar","text":"k1"},
              {"type":"image","texture":"hexerei:textures/gui/x.png","text":"k2"},
              {"type":"multiblock","structure":"hexerei:altar_3x3","text":"k3"},
              {"type":"entity","entity":"minecraft:wolf","text":"k4"} ] }
            """));
        assertInstanceOf(GrimoirePage.Item.class, e.pages().get(0));
        assertEquals(rl("mandrake_root"), ((GrimoirePage.Item) e.pages().get(0)).item());
        assertInstanceOf(GrimoirePage.Recipe.class, e.pages().get(1));
        assertInstanceOf(GrimoirePage.Image.class, e.pages().get(2));
        assertInstanceOf(GrimoirePage.Multiblock.class, e.pages().get(3));
        assertInstanceOf(GrimoirePage.Entity.class, e.pages().get(4));
        assertEquals(ResourceLocation.parse("minecraft:wolf"),
                ((GrimoirePage.Entity) e.pages().get(4)).entityType());
    }

    @Test
    void parsesCategory() {
        GrimoireBook.Category c = GrimoireParser.parseCategory(rl("altars"), obj("""
            { "name":"hexerei.grimoire.altars.name", "description":"hexerei.grimoire.altars.desc",
              "icon":"hexerei:altar", "sortnum":0 }
            """));
        assertEquals("hexerei.grimoire.altars.name", c.nameKey());
        assertEquals(0, c.sortnum());
    }

    @Test
    void unknownPageTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> GrimoireParser.parseEntry(rl("x/y"), obj("""
            { "category":"hexerei:x", "icon":"hexerei:a", "name":"k",
              "pages":[{"type":"bogus","text":"k"}] }
            """)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.grimoire.GrimoireParserTest'`
Expected: FAIL — `GrimoireParser` does not exist.

- [ ] **Step 3: Write the parser**

```java
package com.vel5id.hexerei.grimoire;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Parses Grimoire JSON into the pure model. Stores ids only — no game-object resolution here. */
public final class GrimoireParser {
    private GrimoireParser() {}

    public static GrimoireBook.Category parseCategory(ResourceLocation id, JsonObject json) {
        return new GrimoireBook.Category(
                id,
                json.get("name").getAsString(),
                json.has("description") ? json.get("description").getAsString() : "",
                ResourceLocation.parse(json.get("icon").getAsString()),
                json.has("sortnum") ? json.get("sortnum").getAsInt() : 0);
    }

    public static GrimoireBook.Entry parseEntry(ResourceLocation id, JsonObject json) {
        ResourceLocation category = ResourceLocation.parse(json.get("category").getAsString());

        GrimoireBook.NodePos node = null;
        if (json.has("node")) {
            JsonObject n = json.getAsJsonObject("node");
            node = new GrimoireBook.NodePos(n.get("x").getAsInt(), n.get("y").getAsInt());
        }

        List<ResourceLocation> prereq = new ArrayList<>();
        if (json.has("prereq")) {
            for (var el : json.getAsJsonArray("prereq")) prereq.add(ResourceLocation.parse(el.getAsString()));
        }

        List<GrimoirePage> pages = new ArrayList<>();
        JsonArray pageArr = json.getAsJsonArray("pages");
        if (pageArr != null) for (var el : pageArr) pages.add(parsePage(el.getAsJsonObject()));

        return new GrimoireBook.Entry(
                id, category, node, List.copyOf(prereq),
                ResourceLocation.parse(json.get("icon").getAsString()),
                json.get("name").getAsString(),
                List.copyOf(pages));
    }

    private static GrimoirePage parsePage(JsonObject p) {
        String type = p.get("type").getAsString();
        String text = p.has("text") ? p.get("text").getAsString() : "";
        return switch (type) {
            case "text"       -> new GrimoirePage.Text(text);
            case "item"       -> new GrimoirePage.Item(ResourceLocation.parse(p.get("item").getAsString()), text);
            case "recipe"     -> new GrimoirePage.Recipe(ResourceLocation.parse(p.get("recipeId").getAsString()), text);
            case "image"      -> new GrimoirePage.Image(ResourceLocation.parse(p.get("texture").getAsString()), text);
            case "multiblock" -> new GrimoirePage.Multiblock(ResourceLocation.parse(p.get("structure").getAsString()), text);
            case "entity"     -> new GrimoirePage.Entity(ResourceLocation.parse(p.get("entity").getAsString()), text);
            default -> throw new IllegalArgumentException("Unknown grimoire page type: " + type);
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.grimoire.GrimoireParserTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/vel5id/hexerei/grimoire/GrimoireParser.java src/test/java/com/vel5id/hexerei/grimoire/GrimoireParserTest.java
git commit -m "feat(hexerei): grimoire JSON parser (pure, id-based)"
```

---

### Task 3: `GrimoireNodeLayout` — node positions (pure, unit-tested)

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/grimoire/GrimoireNodeLayout.java`
- Test: `src/test/java/com/vel5id/hexerei/grimoire/GrimoireNodeLayoutTest.java`

**Interfaces:**
- Consumes: Task 1 records.
- Produces:
  - `record Placed(GrimoireBook.Entry entry, int x, int y)`
  - `static List<Placed> layout(List<GrimoireBook.Entry> entries, int cols, int cell)` — authored `node` used verbatim; entries with `null` node are grid-placed in declaration order at `(col*cell, row*cell)`, skipping cells already occupied by an authored node is **not** required (authored and grid coordinate spaces may overlap; authored positions win and grid fills sequential indices ignoring authored ones).

- [ ] **Step 1: Write the failing test**

```java
package com.vel5id.hexerei.grimoire;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrimoireNodeLayoutTest {

    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath("hexerei", p); }

    private static GrimoireBook.Entry entry(String id, GrimoireBook.NodePos node) {
        return new GrimoireBook.Entry(rl(id), rl("herbs"), node, List.of(), rl("garlic"),
                "k", List.of(new GrimoirePage.Text("k")));
    }

    @Test
    void authoredNodesUsedVerbatim() {
        var placed = GrimoireNodeLayout.layout(List.of(entry("a", new GrimoireBook.NodePos(120, 40))), 4, 30);
        assertEquals(120, placed.get(0).x());
        assertEquals(40, placed.get(0).y());
    }

    @Test
    void nullNodesGridPlacedInOrder() {
        var placed = GrimoireNodeLayout.layout(
                List.of(entry("a", null), entry("b", null), entry("c", null)), 2, 30);
        assertEquals(0, placed.get(0).x());   assertEquals(0, placed.get(0).y());
        assertEquals(30, placed.get(1).x());  assertEquals(0, placed.get(1).y());
        assertEquals(0, placed.get(2).x());   assertEquals(30, placed.get(2).y()); // wraps to row 1
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.grimoire.GrimoireNodeLayoutTest'`
Expected: FAIL — `GrimoireNodeLayout` missing.

- [ ] **Step 3: Write the layout**

```java
package com.vel5id.hexerei.grimoire;

import java.util.ArrayList;
import java.util.List;

/** Resolves each entry to a pixel position in its category canvas. Authored node wins; null → grid fill. */
public final class GrimoireNodeLayout {
    private GrimoireNodeLayout() {}

    public record Placed(GrimoireBook.Entry entry, int x, int y) {}

    public static List<Placed> layout(List<GrimoireBook.Entry> entries, int cols, int cell) {
        List<Placed> out = new ArrayList<>(entries.size());
        int gridIndex = 0;
        for (GrimoireBook.Entry e : entries) {
            if (e.node() != null) {
                out.add(new Placed(e, e.node().x(), e.node().y()));
            } else {
                int col = gridIndex % cols;
                int row = gridIndex / cols;
                out.add(new Placed(e, col * cell, row * cell));
                gridIndex++;
            }
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.grimoire.GrimoireNodeLayoutTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/vel5id/hexerei/grimoire/GrimoireNodeLayout.java src/test/java/com/vel5id/hexerei/grimoire/GrimoireNodeLayoutTest.java
git commit -m "feat(hexerei): grimoire node layout (authored + grid fallback)"
```

---

### Task 4: Client loader + book holder (resource-reload listener)

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/client/grimoire/ClientGrimoireBook.java`
- Create: `src/main/java/com/vel5id/hexerei/client/grimoire/GrimoireClientLoader.java`
- Modify: `src/main/java/com/vel5id/hexerei/client/HexereiClient.java` (add `RegisterClientReloadListenersEvent` handler)

**Interfaces:**
- Consumes: Tasks 1–2; `net.minecraft.server.packs.resources.SimplePreparableReloadListener`, `ResourceManager`, `Minecraft.getInstance().getLanguageManager()`.
- Produces:
  - `ClientGrimoireBook.set(GrimoireBook)` / `ClientGrimoireBook.get()` (static holder, defaults to empty book).
  - Loader reads `assets/hexerei/grimoire/<lang>/categories/*.json` and `assets/hexerei/grimoire/<lang>/entries/**/*.json`, falling back to `en_us` for any path the current language lacks. Entry id = path under `entries/` minus `.json` (e.g. `entries/herbs/garlic.json` → `hexerei:herbs/garlic`). Category id = filename stem (e.g. `categories/altars.json` → `hexerei:altars`).

**Verification:** loader is GUI-adjacent (needs a resource manager), so this task is verified by a **client run** that logs the loaded counts, not a unit test.

- [ ] **Step 1: Write `ClientGrimoireBook`**

```java
package com.vel5id.hexerei.client.grimoire;

import com.vel5id.hexerei.grimoire.GrimoireBook;

import java.util.List;
import java.util.Map;

/** Client-side holder for the parsed Grimoire. Repopulated on every resource reload. */
public final class ClientGrimoireBook {
    private static GrimoireBook book = new GrimoireBook(Map.of(), List.of());
    private ClientGrimoireBook() {}

    public static void set(GrimoireBook b) { book = b; }
    public static GrimoireBook get() { return book; }
}
```

- [ ] **Step 2: Write `GrimoireClientLoader`**

```java
package com.vel5id.hexerei.client.grimoire;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.grimoire.GrimoireBook;
import com.vel5id.hexerei.grimoire.GrimoireParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the Grimoire JSON for the active language (en_us fallback) on resource reload. */
public final class GrimoireClientLoader extends SimplePreparableReloadListener<GrimoireBook> {

    @Override
    protected GrimoireBook prepare(ResourceManager rm, ProfilerFiller profiler) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        Map<ResourceLocation, GrimoireBook.Category> categories = new HashMap<>();
        List<GrimoireBook.Entry> entries = new ArrayList<>();

        // Categories: load en_us first, then overlay the active language.
        loadCategories(rm, "en_us", categories);
        if (!lang.equals("en_us")) loadCategories(rm, lang, categories);

        Map<ResourceLocation, GrimoireBook.Entry> byId = new HashMap<>();
        loadEntries(rm, "en_us", byId);
        if (!lang.equals("en_us")) loadEntries(rm, lang, byId);
        entries.addAll(byId.values());

        HexereiMod.LOGGER.info("[grimoire] loaded {} categories, {} entries (lang {})",
                categories.size(), entries.size(), lang);
        return new GrimoireBook(categories, entries);
    }

    private void loadCategories(ResourceManager rm, String lang, Map<ResourceLocation, GrimoireBook.Category> out) {
        String dir = "grimoire/" + lang + "/categories";
        rm.listResources(dir, p -> p.getPath().endsWith(".json")).forEach((loc, res) -> {
            if (!loc.getNamespace().equals(HexereiMod.MODID)) return;
            String file = loc.getPath().substring((dir + "/").length(), loc.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, file);
            JsonObject json = read(res);
            if (json != null) out.put(id, GrimoireParser.parseCategory(id, json));
        });
    }

    private void loadEntries(ResourceManager rm, String lang, Map<ResourceLocation, GrimoireBook.Entry> out) {
        String dir = "grimoire/" + lang + "/entries";
        rm.listResources(dir, p -> p.getPath().endsWith(".json")).forEach((loc, res) -> {
            if (!loc.getNamespace().equals(HexereiMod.MODID)) return;
            String file = loc.getPath().substring((dir + "/").length(), loc.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, file);
            JsonObject json = read(res);
            if (json != null) out.put(id, GrimoireParser.parseEntry(id, json));
        });
    }

    private static JsonObject read(Resource res) {
        try (BufferedReader r = res.openAsReader()) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            HexereiMod.LOGGER.error("[grimoire] failed to read {}", res.sourcePackId(), e);
            return null;
        }
    }

    @Override
    protected void apply(GrimoireBook book, ResourceManager rm, ProfilerFiller profiler) {
        ClientGrimoireBook.set(book);
    }
}
```

- [ ] **Step 3: Register the loader in `HexereiClient`**

Add the import `net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;` and this handler (sibling of the other `@SubscribeEvent` methods):

```java
    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new com.vel5id.hexerei.client.grimoire.GrimoireClientLoader());
    }
```

> If `HexereiMod.LOGGER` does not exist, use the mod's existing logger reference (grep `LOGGER` / `getLogger` in `HexereiMod.java`); substitute the actual field name in the loader.

- [ ] **Step 4: Build to confirm it compiles**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon compileJava`
Expected: BUILD SUCCESSFUL. (Runtime load-count verification happens after Task 5 adds content.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/vel5id/hexerei/client/grimoire/ src/main/java/com/vel5id/hexerei/client/HexereiClient.java
git commit -m "feat(hexerei): client grimoire loader + book holder (reload listener)"
```

---

### Task 5: Migrate the 17 entries + 5 categories to the new format

**Files:**
- Create: `src/main/resources/assets/hexerei/grimoire/en_us/categories/{altars,herbs,brewing,rituals,charms}.json`
- Create: `src/main/resources/assets/hexerei/grimoire/en_us/entries/**/*.json` (17 entries, mirroring the current Patchouli paths)
- Create: `src/main/resources/assets/hexerei/grimoire/ru_ru/...` (full RU mirror)
- Modify: `src/main/resources/assets/hexerei/lang/en_us.json`, `ru_ru.json` (add `hexerei.grimoire.*` keys for every page/entry/category string)
- Keep (do NOT delete yet): the `patchouli_books/` tree — removed in Task 11 after the new screen works.

**Interfaces:**
- Consumes: the schema from Task 2 (entry + category JSON), Task 4 (path → id mapping).
- Migration mapping from the old Patchouli pages: `patchouli:text` → `text`; `patchouli:spotlight` (has `item`+`text`) → `item`; `patchouli:crafting` (has `recipe`) → `recipe` (`recipeId` = the old `recipe` value). Move all inline `text`/`name`/`description` **strings into lang keys** (`hexerei.grimoire.<category>.<entry>.<n>` for pages, `.name` for entry/category names, `.desc` for category descriptions) and reference the keys from the JSON. **Add a `node:{x,y}`** to every entry (lay each category's entries out as a small readable constellation; landing/basics entries near origin, dependents below-right). **Add `prereq` edges** where one entry clearly builds on another (e.g. `altars/power` prereq `altars/landing`).
- **Coverage additions (new page types):** add one `multiblock` page to `entries/altars/landing.json` (or `power.json`) pointing at a structure id you create or reference, and one `entity` page to `entries/rituals/` showing `minecraft:wolf` (the `BoundBeastRite` familiar). These give the Task 8/9 renderers real content. If no saved structure exists for the altar, defer the multiblock page's `structure` to a placeholder id and note it — Task 8 covers structure sourcing.

- [ ] **Step 1: Author the 5 category files (en_us)**

Example `grimoire/en_us/categories/altars.json`:

```json
{ "name": "hexerei.grimoire.altars.name",
  "description": "hexerei.grimoire.altars.desc",
  "icon": "hexerei:altar",
  "sortnum": 0 }
```

Repeat for herbs/brewing/rituals/charms with ascending `sortnum`.

- [ ] **Step 2: Convert each of the 17 entries**

For every file under the old `patchouli_books/grimoire/en_us/entries/`, write the new entry JSON. Example — `grimoire/en_us/entries/herbs/mandrake.json` (was a single spotlight page):

```json
{ "category": "hexerei:herbs",
  "node": { "x": 60, "y": 30 },
  "prereq": ["hexerei:herbs/growing"],
  "icon": "hexerei:mandrake_root",
  "name": "hexerei.grimoire.herbs.mandrake.name",
  "pages": [ { "type": "item", "item": "hexerei:mandrake_root",
              "text": "hexerei.grimoire.herbs.mandrake.p0" } ] }
```

Example with a recipe page — `grimoire/en_us/entries/rituals/circles.json` keeps its `patchouli:crafting` page as `{ "type":"recipe", "recipeId":"<old recipe value>", "text":"hexerei.grimoire.rituals.circles.pN" }`.

- [ ] **Step 3: Add lang keys**

In `lang/en_us.json`, add every referenced key with the English string taken verbatim from the old Patchouli JSON. In `lang/ru_ru.json`, add the same keys with the Russian strings taken verbatim from the old `ru_ru` Patchouli tree. Keep the existing `item.hexerei.grimoire` key.

- [ ] **Step 4: Mirror the RU asset tree**

Copy the new `grimoire/en_us/` JSON structure to `grimoire/ru_ru/` (the structural JSON is identical — only the lang strings differ, and those live in the lang files, so the RU tree may be a byte copy of en_us). This satisfies the loader's per-language lookup with en_us fallback.

> Note: because all player-facing text is now in lang files, the `en_us`/`ru_ru` asset trees are structurally identical. The parallel trees are kept (per spec) so a future translator could diverge structure if ever needed, but P1 keeps them identical.

- [ ] **Step 5: Build & verify load counts in a client run**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon runClient`
In the log, confirm: `[grimoire] loaded 5 categories, 17 entries (lang en_us)`. Close the client.
Expected: counts match. (No screen yet — that's Task 6.)

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/assets/hexerei/grimoire src/main/resources/assets/hexerei/lang
git commit -m "feat(hexerei): migrate grimoire content to own format (+ node positions, lang keys, RU parity)"
```

---

### Task 6: `GrimoireScreen` — tabs + node-web + text/item pages

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/client/grimoire/GrimoireScreen.java`

**Interfaces:**
- Consumes: `ClientGrimoireBook.get()` (Task 4), `GrimoireNodeLayout.layout(...)` (Task 3), `GrimoireBook.entriesIn(...)`.
- Produces: `public GrimoireScreen()` constructor (opened in Task 10); two view states — **web view** (category tabs on the left rail; nodes drawn at laid-out positions with prereq connector lines via `GuiGraphics.fill`/line; node = entry icon via `guiGraphics.renderItem`; hover shows `nameKey` tooltip; click → entry view) and **entry view** (renders the clicked entry's current page; Prev/Next buttons cycle pages; Back returns to web view). Render only `Text` and `Item` pages here; Recipe/Image/Multiblock/Entity get a "(page type)" stub until Tasks 7–9.

- [ ] **Step 1: Implement the screen skeleton**

Create `GrimoireScreen extends net.minecraft.client.gui.screens.Screen`. Hold: selected category id (default = lowest `sortnum`), optional selected entry, current page index. In `init()`, build the category tab buttons from `ClientGrimoireBook.get().categories()` sorted by `sortnum`. In `render(GuiGraphics, mouseX, mouseY, partialTick)`:
  - call `renderBackground`,
  - if no entry selected → draw the left rail + the node-web: compute `GrimoireNodeLayout.layout(book.entriesIn(selectedCategory), COLS, CELL)`, offset by a canvas origin, draw a connector line for each `prereq` edge (from the prereq node center to this node center) with `guiGraphics.fill` (thin rect) or `hLine`/`vLine`, then `guiGraphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(entry.icon())), x, y)` per node, and a hover tooltip of `Component.translatable(entry.nameKey())`,
  - if an entry is selected → render its page (Task-6 scope: `Text` → `guiGraphics.drawWordWrap(font, Component.translatable(((Text)page).textKey()), ...)`; `Item` → the item icon + its `textKey`; others → `drawString(font, "[" + page.getClass().getSimpleName() + " page]", ...)`).
  - Handle `mouseClicked`: in web view, hit-test node rects → select entry, reset page index; in entry view, Prev/Next/Back buttons.

> Use exact 1.21.1 signatures: `Screen(Component title)` super; `GuiGraphics.renderItem(ItemStack, int, int)`; `GuiGraphics.drawWordWrap(Font, Component, int x, int y, int width, int color)`; `BuiltInRegistries.ITEM.get(ResourceLocation)`. If a signature differs, the IDE/compiler is authoritative — adjust.

- [ ] **Step 2: Build**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification (deferred to Task 10)**

The screen has no opener yet — it is exercised in Task 10's run. Note this; do not block here.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/vel5id/hexerei/client/grimoire/GrimoireScreen.java
git commit -m "feat(hexerei): GrimoireScreen — category tabs, node-web, text/item pages"
```

---

### Task 7: Recipe + image page rendering

**Files:**
- Modify: `src/main/java/com/vel5id/hexerei/client/grimoire/GrimoireScreen.java`

**Interfaces:**
- Consumes: `GrimoirePage.Recipe`, `GrimoirePage.Image`; `Minecraft.getInstance().level.getRecipeManager().byKey(ResourceLocation)`.
- Produces: recipe page draws the matched recipe's ingredient grid + result item icons (look up via the recipe manager; for a `CraftingRecipe` draw its `getIngredients()` into a 3×3 of slots and `getResultItem(level.registryAccess())`); image page draws `guiGraphics.blit(texture, x, y, 0, 0, w, h, w, h)`.

- [ ] **Step 1: Replace the Recipe/Image stubs with real rendering** (code per the interface above; resolve recipe lazily and null-guard when `level` or the recipe is absent — show the page `textKey` regardless).
- [ ] **Step 2: Build** — `./gradlew --no-daemon compileJava` → SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "feat(hexerei): grimoire recipe + image pages"`.

---

### Task 8: Multiblock page rendering (ghost preview)

**Files:**
- Modify: `src/main/java/com/vel5id/hexerei/client/grimoire/GrimoireScreen.java`
- Create (if needed): a structure source for the altar — either a saved structure NBT under `data/hexerei/structures/altar_3x3.nbt` (via `StructureTemplateManager`) **or** an inline block map helper. Pick the simpler: an inline `Map<BlockPos, BlockState>` helper `GrimoireMultiblocks.altar()` in `client/grimoire/` so no datapack asset is needed for P1.

**Interfaces:**
- Consumes: `GrimoirePage.Multiblock` (its `structure` id selects a known inline pattern), `Minecraft.getInstance().getBlockRenderer()`.
- Produces: renders the pattern's `BlockState`s into the page via `BlockRenderDispatcher.renderSingleBlock(state, poseStack, bufferSource, light, overlay)` inside a scaled/rotated `PoseStack` transform, slow-auto-rotating on `partialTick`. Provide a `GrimoireMultiblocks.byId(ResourceLocation) → Map<BlockPos,BlockState>` lookup; unknown id → empty (page still shows its `textKey`).

- [ ] **Step 1:** Implement `GrimoireMultiblocks` with at least the altar pattern keyed by the id used in Task 5's altar entry.
- [ ] **Step 2:** Render the pattern in the multiblock page branch (PoseStack push, translate to page center, scale ~16, `mulPose` a Y rotation from a frame counter, iterate blocks rendering each at its `BlockPos`, pop; flush the `bufferSource`).
- [ ] **Step 3: Build** — `./gradlew --no-daemon compileJava` → SUCCESSFUL.
- [ ] **Step 4: Commit** — `git commit -am "feat(hexerei): grimoire multiblock ghost preview"`.

---

### Task 9: Entity page rendering (familiars/golems)

**Files:**
- Modify: `src/main/java/com/vel5id/hexerei/client/grimoire/GrimoireScreen.java`

**Interfaces:**
- Consumes: `GrimoirePage.Entity` (its `entityType` id), `net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(...)`, `net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(...)`.
- Produces: resolves the `EntityType`, creates a **non-added** client dummy instance (`type.create(Minecraft.getInstance().level)`), and draws it via the vanilla GUI helper at the page center with a fixed scale, slowly turning. Null entity / null level → show only the page `textKey`. Cache the dummy per entity id so it isn't recreated each frame.

- [ ] **Step 1:** Implement the entity page branch using `InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, x1,y1,x2,y2, scale, yOffset, mouseX, mouseY, livingEntity)` (verify the exact 1.21.1 overload against the IDE; the wolf is a `LivingEntity`). Guard non-living types by skipping the render.
- [ ] **Step 2: Build** — `./gradlew --no-daemon compileJava` → SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "feat(hexerei): grimoire entity page (familiars/golems via vanilla GUI render)"`.

---

### Task 10: Open `GrimoireScreen` from the item; drop `PatchouliAPI`

**Files:**
- Modify: `src/main/java/com/vel5id/hexerei/item/GrimoireItem.java`
- Create: `src/main/java/com/vel5id/hexerei/client/grimoire/GrimoireScreenOpener.java` (client-only opener, called via the dist guard)

**Interfaces:**
- Consumes: `GrimoireScreen` (Task 6).
- Produces: `GrimoireItem.use` returns success on both sides; on the client dist it opens the screen. The `PatchouliAPI` import and call are removed.

- [ ] **Step 1: Write the client opener**

```java
package com.vel5id.hexerei.client.grimoire;

import net.minecraft.client.Minecraft;

/** Client-only: opens the Grimoire screen. Reached only behind a Dist.CLIENT guard. */
public final class GrimoireScreenOpener {
    private GrimoireScreenOpener() {}
    public static void open() { Minecraft.getInstance().setScreen(new GrimoireScreen()); }
}
```

- [ ] **Step 2: Rewrite `GrimoireItem.use`**

```java
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.vel5id.hexerei.client.grimoire.GrimoireScreenOpener.open();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
```

Remove the `import vazkii.patchouli.api.PatchouliAPI;` line and the old `PatchouliAPI.get().openBookGUI(...)` block. The `BOOK_ID` constant may stay (harmless) or be deleted.

- [ ] **Step 3: Build** — `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon compileJava` → SUCCESSFUL.

- [ ] **Step 4: Manual verification — RUN THE CLIENT**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon runClient`
In-game: creative-give `hexerei:grimoire`, right-click → `GrimoireScreen` opens. Verify: 5 category tabs; clicking a tab shows that category's node-web with connector lines; hovering a node shows its name; clicking a node opens the entry; Prev/Next cycle pages; Back returns; an `item`/`text` page renders; the altar entry's `multiblock` page rotates; a `rituals` entry's `entity` page shows the wolf; a `recipe` page shows the grid. Screenshot the web view and one of each page type. **Look at the screenshots** — a blank frame is a failure.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/vel5id/hexerei/item/GrimoireItem.java src/main/java/com/vel5id/hexerei/client/grimoire/GrimoireScreenOpener.java
git commit -m "feat(hexerei): open own GrimoireScreen from the item, drop PatchouliAPI"
```

---

### Task 11: Remove the Patchouli dependency and old book tree

**Files:**
- Modify: `build.gradle` (delete the `implementation 'vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE'` line + its comment, and the repository entry that exists only for it if any)
- Modify: `src/main/templates/META-INF/neoforge.mods.toml` (delete the `[[dependencies.hexerei]]` block for `patchouli`)
- Modify: `src/main/java/com/vel5id/hexerei/registry/HexereiItems.java` (update the GRIMOIRE comment — no longer Patchouli)
- Delete: `src/main/resources/assets/hexerei/patchouli_books/` and `src/main/resources/data/hexerei/patchouli_books/` (the entire old tree, including `book.json` and the `grimoire` recipe if it lived there) — keep `data/hexerei/recipe/grimoire.json` (the craft recipe for the item) intact.

**Interfaces:** none produced; this is cleanup. Must come last so Tasks 5–10 could rely on the new content while the old book still existed.

- [ ] **Step 1: Remove the dependency** from `build.gradle` and the `neoforge.mods.toml` patchouli dependency block. Grep to confirm no remaining compile reference: `grep -rn "patchouli\|vazkii" src/main/java` → must be empty.
- [ ] **Step 2: Delete the old book trees** (`assets/.../patchouli_books`, `data/.../patchouli_books`). Confirm the item's craft recipe (`data/hexerei/recipe/grimoire.json`) is **not** under `patchouli_books` and survives.
- [ ] **Step 3: Update the `HexereiItems.GRIMOIRE` comment** to "// The in-game guide book (opens the mod's own GrimoireScreen)."
- [ ] **Step 4: Full build + unit tests**

Run: `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon build`
Expected: BUILD SUCCESSFUL, `:test` green, jar at `build/libs/hexerei-1.21.1-0.1.0.jar`.

- [ ] **Step 5: Run the client once more** to confirm the Grimoire still opens and renders with Patchouli fully gone (no missing-dependency crash on boot). Screenshot the opened book.

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/main/templates/META-INF/neoforge.mods.toml src/main/java/com/vel5id/hexerei/registry/HexereiItems.java
git rm -r src/main/resources/assets/hexerei/patchouli_books src/main/resources/data/hexerei/patchouli_books
git commit -m "chore(hexerei): drop Patchouli dependency and old book tree"
```

---

## Self-review (spec coverage)

- **Own engine, Patchouli dropped** → Tasks 4,6–11. ✅
- **Tabbed per-category node-web + connector lines + manual positions/grid fallback** → Tasks 3,6 + Task 5 node authoring. ✅
- **Page types v1: text/image/recipe/multiblock/entity** → model/parser Tasks 1–2; renderers Tasks 6–9. ✅
- **Entity = familiars/golems (wolf today), not spirits** → Task 9 + Task 5 entity page on `minecraft:wolf`. ✅
- **Migrate 17 entries, RU parity** → Task 5. ✅
- **Everything unlocked in P1 (no gate), no parallel currency** → no gating tasks present; Global Constraints state it. ✅
- **Two-tier testing (pure unit-tested, GUI run-verified)** → Tasks 1–3 unit tests; Tasks 6–11 client-run verification. ✅
- **Remove "disable Patchouli for GameTests" workaround** → resolved transitively by Task 11 (no code workaround exists; dependency removal is the fix). ✅

**Out of P1 (carried to later plans):** `knownEntries` storage, `GrimoireDiscovery`, contact/state triggers, `GrimoireSyncS2CPacket`, `ClientGrimoireCache`, locked silhouettes, teasers, toasts → **P2/P3** (separate plans authored after P1 lands and the screen API is concrete).
