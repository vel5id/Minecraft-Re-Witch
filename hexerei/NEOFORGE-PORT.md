# NeoForge 1.21.1 Port — Sweep Playbook

Authoritative reference for porting Hexerei from **Forge 1.20.1** to **NeoForge 1.21.1**.
Read this in full before editing. Per-file recon notes (every NBT key, packet field, call site) live in the
research dump referenced at the bottom.

## Target stack (pinned, verified from maven)
- Minecraft **1.21.1**, NeoForge **21.1.234**, ModDevGradle **2.0.141**, Gradle **8.10.2**, Java **21**
- Parchment **2024.11.17-1.21.1**, Patchouli **1.21.1-93-NEOFORGE**

## P0 foundation is DONE — do NOT re-port these (call them, don't change their contract)
Build files, `neoforge.mods.toml`, `pack.mcmeta`; `HexereiMod`, `HexereiLevelEvents`, `HexereiNetwork` +
3 payloads, `HexereiAttachments` (replaces `HexereiCapabilities`, now deleted), `HexereiDataComponents`,
`PlayerSoulData`, `ChunkSoulData`, `client/ClientTaintCache`, `client/ClientBloodMoonCache`.

## THE NEW API SURFACE established by P0 — call exactly these
- **Registry handles** keep their field names (`HexereiBlocks.ALTAR`, `HexereiItems.AMULET`, …). Their type
  becomes `DeferredHolder`/`DeferredBlock`/`DeferredItem`, but `.get()` is unchanged. (You port the registry
  *classes* themselves per Rule 2; consumers just keep calling `.get()`.)
- **Soul attachments** — `com.vel5id.hexerei.soul.HexereiAttachments.PLAYER_SOUL` / `.CHUNK_SOUL`
  (`Supplier<AttachmentType<…>>`). Access: `holder.getData(HexereiAttachments.PLAYER_SOUL)` (creates default),
  `holder.getExistingData(…)` (Optional, no create), `holder.setData(…, v)` (auto-marks dirty).
- **Data components** — `com.vel5id.hexerei.registry.HexereiDataComponents`:
  `SEALED_BOND` (`Bond`), `TAGLOCK_TARGET` (`HexereiDataComponents.TaglockTarget(UUID id, String name)`),
  `BREW_ID` (`String`), `POUCH_CONTENTS` (`ItemContainerContents`), `SELECTED_RITE` (`String`).
  Use `stack.get(X.get())`, `stack.set(X.get(), v)`, `stack.has(X.get())`, `stack.getOrDefault(X.get(), def)`,
  `stack.remove(X.get())` — note the `.get()` (the field is the deferred holder).
- **Network** — payload class names unchanged. Send via `net.neoforged.neoforge.network.PacketDistributor`:
  `sendToPlayer(serverPlayer, payload)`, `sendToPlayersInDimension(serverLevel, payload)`,
  `sendToPlayersTrackingChunk(serverLevel, chunkPos, payload)`, and client→server `sendToServer(payload)`.

## GOLDEN RULES (apply everywhere)

1. **Imports** `net.minecraftforge.*` → `net.neoforged.*` (see table). `MinecraftForge.EVENT_BUS` →
   `net.neoforged.neoforge.common.NeoForge.EVENT_BUS`.
2. **Registries**: `ForgeRegistries.X` → `net.minecraft.core.registries.Registries.X`;
   `net.minecraftforge.registries.{DeferredRegister,RegistryObject}` →
   `net.neoforged.neoforge.registries.{DeferredRegister,DeferredHolder}`. **Keep the existing
   `REGISTER.register(name, () -> new Thing(Properties.of()...))` bodies verbatim.**
   **DO NOT add `.setId(...)` — NeoForge 1.21.1 does NOT require it** (that is a 1.21.2+ change).
   `RegistryObject<Block>` field type → `DeferredHolder<Block, …>` (or `DeferredBlock<…>`); `.get()` unchanged.
3. **ResourceLocation**: `new ResourceLocation(ns, path)` → `ResourceLocation.fromNamespaceAndPath(ns, path)`;
   `new ResourceLocation("a:b")` → `ResourceLocation.parse("a:b")`. For our own ids always
   `fromNamespaceAndPath(HexereiMod.MODID, path)` (`parse` with no colon defaults to `minecraft:`).
4. **Item NBT → data components** (per-item table below). Delete `getOrCreateTag/getTag/hasTag/setTag` for
   custom data; use the component accessors.
5. **Capabilities → attachments** (call-site table below). Replace every `HexereiCapabilities.*` with
   `HexereiAttachments.*`. **After mutating a `CHUNK_SOUL` instance in place, call `chunk.setUnsaved(true)`.**
6. **BlockEntity persistence**: `public void load(CompoundTag)` → `protected void loadAdditional(CompoundTag,
   HolderLookup.Provider)`; `saveAdditional(CompoundTag)` → `saveAdditional(CompoundTag, HolderLookup.Provider)`;
   `getUpdateTag()` → `getUpdateTag(HolderLookup.Provider)`; `onDataPacket(Connection, pkt)` →
   `onDataPacket(Connection, pkt, HolderLookup.Provider)`. Match `super.…(tag, registries)`. `getUpdatePacket()`
   unchanged: `return ClientboundBlockEntityDataPacket.create(this);`.
7. **SavedData**: `save(CompoundTag)` → `save(CompoundTag, HolderLookup.Provider)`; the load fn becomes
   `(CompoundTag, HolderLookup.Provider)`; obtain via
   `storage.computeIfAbsent(new SavedData.Factory<>(Ctor::new, T::load), "filename")` (2-arg Factory, 2-arg
   computeIfAbsent).
8. **ItemStackHandler**: `net.minecraftforge.items.ItemStackHandler` →
   `net.neoforged.neoforge.items.ItemStackHandler`; `serializeNBT()` → `serializeNBT(HolderLookup.Provider)`;
   `deserializeNBT(tag)` → `deserializeNBT(HolderLookup.Provider, tag)` (**provider FIRST**).
9. **MobEffect**: `MobEffects.X` and registered effects are `Holder<MobEffect>`. `new MobEffectInstance(holder,
   dur, amp)` takes the Holder directly (no `.get()`). To look up a registered effect by id:
   `Holder<MobEffect> fx = BuiltInRegistries.MOB_EFFECT.getHolder(rl).orElse(null);` then
   `entity.addEffect(new MobEffectInstance(fx, …))`, and read fields via `fx.value().getDescriptionId()` /
   `fx.value().getCategory()`. `addEffect/hasEffect/removeEffect` take `Holder<MobEffect>`.
   A `DeferredRegister<MobEffect>.register(...)` returns `Holder<MobEffect>`.
10. **GameTest**: `net.minecraftforge.gametest.{GameTestHolder,PrefixGameTestTemplate}` →
    `net.neoforged.neoforge.gametest.*`. `@GameTest` stays `net.minecraft.gametest.framework.GameTest`.
11. **Dist/OnlyIn**: `net.minecraftforge.api.distmarker.*` → `net.neoforged.api.distmarker.*`.
    **`DistExecutor` is REMOVED** → replace `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> X)` with
    `if (net.neoforged.fml.loading.FMLEnvironment.dist == Dist.CLIENT) { X; }` calling into a client class.
12. **Events**: `@SubscribeEvent` → `net.neoforged.bus.api.SubscribeEvent`; `@Mod.EventBusSubscriber` →
    `@net.neoforged.fml.common.EventBusSubscriber` with `EventBusSubscriber.Bus.MOD`/`.GAME`
    (Forge `Bus.FORGE` → `Bus.GAME`). Common events under `net.neoforged.neoforge.event.*`. Forge
    `TickEvent.LevelTickEvent`(phase END) → `net.neoforged.neoforge.event.tick.LevelTickEvent.Post`
    (`event.getLevel()`, no phase check). `ChunkWatchEvent.Watch` →
    `net.neoforged.neoforge.event.level.ChunkWatchEvent.Watch` (`getLevel()`/`getPos()`/`getPlayer()`).
13. **Menu**: `net.minecraftforge.common.extensions.IForgeMenuType.create` →
    `net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create`; `ForgeRegistries.MENU_TYPES` →
    `Registries.MENU`. The client menu factory's buffer is `RegistryFriendlyByteBuf` (not `FriendlyByteBuf`).
    `NetworkHooks.openScreen(serverPlayer, provider, buf-writer)` → `serverPlayer.openMenu(provider, buf-writer)`
    (`openMenu` is on `ServerPlayer`).
14. **Client events**: `net.minecraftforge.client.event.{RegisterColorHandlersEvent,
    RegisterParticleProvidersEvent,InputEvent}` → `net.neoforged.neoforge.client.event.*`. `MenuScreens.register`
    and `ItemProperties.register` are unchanged (still client). `InputEvent.MouseScrollingEvent` →
    `net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent` (`getScrollDeltaY()` in 1.21);
    client scroll send: `PacketDistributor.sendToServer(new CycleRiteC2SPacket(delta))`.

## VERSION TRAPS — these compile on newer NeoForge but NOT on 21.1.234; do NOT use
- `BlockBehaviour.Properties.setId(...)` / `Item.Properties.setId(...)` — **not in 1.21.1.**
- `chunk.markUnsaved()` — use `chunk.setUnsaved(true)`.
- `CompoundTag.getIntOr(...)` / `ValueInput`/`ValueOutput` — use the unchanged `getInt`/`getString`/etc.
- `ClientPacketDistributor` / `RegisterClientPayloadHandlersEvent` — use the single
  `RegisterPayloadHandlersEvent` + `PacketDistributor.sendToServer`.
- Attachment `.serialize(MapCodec)` / ValueIO serializer — use a plain `Codec` or `AttachmentType.serializable`.

## Import swap table (Forge → NeoForge)
| Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|
| `net.minecraftforge.fml.common.Mod` | `net.neoforged.fml.common.Mod` |
| `net.minecraftforge.eventbus.api.{SubscribeEvent,IEventBus}` | `net.neoforged.bus.api.{SubscribeEvent,IEventBus}` |
| `net.minecraftforge.api.distmarker.{Dist,OnlyIn}` | `net.neoforged.api.distmarker.{Dist,OnlyIn}` |
| `net.minecraftforge.registries.{DeferredRegister,ForgeRegistries,RegistryObject}` | `net.neoforged.neoforge.registries.{DeferredRegister,DeferredHolder}` + `net.minecraft.core.registries.Registries` |
| `net.minecraftforge.items.ItemStackHandler` | `net.neoforged.neoforge.items.ItemStackHandler` |
| `net.minecraftforge.common.util.INBTSerializable` | `net.neoforged.neoforge.common.util.INBTSerializable` |
| `net.minecraftforge.common.MinecraftForge` | `net.neoforged.neoforge.common.NeoForge` |
| `net.minecraftforge.common.extensions.IForgeMenuType` | `net.neoforged.neoforge.common.extensions.IMenuTypeExtension` |
| `net.minecraftforge.network.PacketDistributor` | `net.neoforged.neoforge.network.PacketDistributor` |
| `net.minecraftforge.client.event.*` | `net.neoforged.neoforge.client.event.*` |
| `net.minecraftforge.gametest.*` | `net.neoforged.neoforge.gametest.*` |

## Per-item component mapping (Rule 4)
- **AmuletItem** — `SEALED_BOND` (`Bond`). `writeBond` → `stack.set(SEALED_BOND.get(), bond)`. `readBond` →
  `stack.get(SEALED_BOND.get())` (nullable, no manual `Bond.CODEC`/`NbtOps`). `isSealed` →
  `stack.getItem() instanceof AmuletItem && stack.has(SEALED_BOND.get())`. Mob-effect tooltip per Rule 9.
- **TaglockItem** — `TAGLOCK_TARGET`. `bind` → `stack.set(TAGLOCK_TARGET.get(), new
  HexereiDataComponents.TaglockTarget(victim.getUUID(), victim.getName().getString()))`. `hasTarget` →
  `stack.has(TAGLOCK_TARGET.get())`. `getTarget` → `var t = stack.get(TAGLOCK_TARGET.get()); return t==null?null:t.id();`.
  name → `t.name()`.
- **BrewItem** — `BREW_ID` (`String`). `of` → `stack.set(BREW_ID.get(), brew.id())`. `brewOf` →
  `String id = stack.get(BREW_ID.get()); return id==null?null:Brews.byId(id);`. `modelIndex` →
  `Brews.indexOf(stack.getOrDefault(BREW_ID.get(), ""))`. Mob-effects per Rule 9. (`HexereiClient`'s
  `ItemProperties.register` brew predicate keeps calling `BrewItem.modelIndex`.)
- **CharmPouchItem** — `POUCH_CONTENTS` (`ItemContainerContents`). `readHandler` → build the 3-slot
  `ItemStackHandler` then `ItemContainerContents c = pouch.get(POUCH_CONTENTS.get()); if (c!=null)
  c.copyInto(handler.getStacks?...)` — simplest: iterate `c.stream()`/`c.getSlots()` into the handler, or
  `for (int i=0;i<c.getSlots();i++) handler.setStackInSlot(i, c.getStackInSlot(i))`. `writeHandler` →
  `pouch.set(POUCH_CONTENTS.get(), ItemContainerContents.fromItems(stacksList))` (build a `List<ItemStack>`
  of `handler.getSlots()` slots). `NetworkHooks.openScreen` → `serverPlayer.openMenu(provider, buf ->
  buf.writeBoolean(offhand))`. Verify `ItemContainerContents` slot accessors against the IDE; if an API
  member is uncertain, prefer iterating `c.nonEmptyItems()` / `c.stream()` and rebuilding.
- **RitualChalkItem** — `SELECTED_RITE` (`String`). `getSelectedRecipe` →
  `RitualRecipes.fromId(stack.getOrDefault(SELECTED_RITE.get(), ""))`. **Add `RitualRecipes.fromId(String)`**
  (mirror the current `fromTag` lookup of `"hexerei:rite"`, defaulting to the first recipe on empty/unknown).
  The C2S `CycleRiteC2SPacket` already writes `SELECTED_RITE` (P0).

## Capability → attachment call-site mapping (Rule 5)
Replace `HexereiCapabilities.PLAYER_SOUL` → `HexereiAttachments.PLAYER_SOUL`; `CHUNK_SOUL` likewise.
- `x.getCapability(PLAYER_SOUL).ifPresent(d -> BODY)` →
  `PlayerSoulData d = x.getData(HexereiAttachments.PLAYER_SOUL); BODY` (player attachment always resolvable).
- `x.getCapability(PLAYER_SOUL).resolve()` (`Optional`) → `java.util.Optional.of(x.getData(...))`, or use
  `x.getExistingData(...)` when the original truly meant "only if present".
- `x.getCapability(PLAYER_SOUL).map(d -> EXPR).orElse(DEF)` → `EXPR` over `x.getData(...)` (default created).
- `chunk.getCapability(CHUNK_SOUL).resolve().orElse(null)` → `chunk.getData(HexereiAttachments.CHUNK_SOUL)`
  (never null). **If the code mutates the returned `ChunkSoulData`, add `chunk.setUnsaved(true)` after.**
Affected files: `blockentity/AltarBlockEntity`, `item/AmuletTickHandler`, `item/CurseTickHandler`,
`ritual/CurseRite`, `ritual/HungeringRite`, `soul/AltarDrain`, `soul/Disturbance`, `test/AmuletGameTests`,
`test/CurseGameTests`. (`HexereiMod`'s old `HexereiCapabilities.register` is already replaced.)

## Behaviour preservation (WARRANTLY)
This is a mechanics-preserving port: do **not** change balance numbers, drop/add mechanics, or alter the
Article III loop. Substrate only. If a faithful 1:1 port of some call is genuinely impossible, leave a
`// PORT-TODO:` and report it rather than inventing behaviour.

## Per-file recon detail
Exhaustive per-file notes (every NBT key, packet field, persistence hook, capability call) are in the research
dump JSON: **`/tmp/claude-1000/-home-h621l-minecraft/9bb215cf-c868-4224-bfba-6afc65aef6c6/tasks/w0p2u8n6r.output`**
— the `result.recon[]` array, keyed by `area`/`fileNotes[].path`. The `result.api[]` array holds the verified,
cited NeoForge 1.21.1 code patterns. Consult it for any file you are unsure about.
