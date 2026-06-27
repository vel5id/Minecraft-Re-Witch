package com.vel5id.hexerei.soul;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side orchestration of the "drink the Dreaming Draught → enter a dream" verb (Slice 1).
 * Reads soul State (it is the {@code read} verb), charges essence, applies the dreaming MobEffect
 * set, and on a nightmare writes BOTH a personal fear mark and place disturbance — feeding the
 * Article III loop. Pure decisions come from {@link DreamResolver}/{@link DreamOnset}; this class
 * only applies their verdict to the world.
 */
public final class DreamEntry {
    private DreamEntry() {}

    /** Dreaming MobEffect duration (ticks). [UNVERIFIED] */
    public static final int DREAM_TICKS = 600;
    /** dreadPenalty (0..1) → THRESHOLD disturbance added to the chunk on a nightmare. [UNVERIFIED] */
    public static final float DISTURB_SCALE = 20f;
    /** spiritType id stamped on a nightmare's fear mark (not a registered entity — a record tag). */
    private static final ResourceLocation NIGHTMARE_SPIRIT =
            ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "nightmare");

    public static void onDrink(Player player, ServerLevel level) {
        PlayerSoulData psd = player.getData(HexereiAttachments.PLAYER_SOUL);
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        ChunkSoulData csd = chunk.getData(HexereiAttachments.CHUNK_SOUL);

        Map<Correspondence, Float> disturbance = csd.disturbanceView();
        float debtN = DreamNormalize.debtN(psd.totalDebt());
        float marksN = DreamNormalize.marksN(psd.marks());
        DreamReading reading = DreamResolver.read(debtN, marksN, disturbance);
        DreamOutcome outcome = DreamOnset.onDrink(psd.essence(), reading);

        if (!outcome.entered()) {
            // Not enough essence to cross — the draught fizzles.
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.5f, 0.7f);
            return;
        }

        psd.spendEssence(outcome.essenceSpent());
        applyDreamingEffects(player, outcome.nightmare());

        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.3, 0.5, 0.3, 0.05);
        // Null-safe: MockPlayer (GameTests) has no connection; real ServerPlayer sends the action-bar message.
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable(outcome.nightmare() ? "dream.hexerei.nightmare" : "dream.hexerei.entered"),
                    true);
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.server.level.ServerLevel dream = sp.getServer().getLevel(DreamWorld.DREAM);
            if (dream != null) {
                DreamWorld.preparePlatform(dream);
                DreamState st = sp.getData(HexereiAttachments.DREAM_STATE);
                st.begin(level.dimension(), sp.blockPosition(), level.getGameTime() + DREAM_TICKS);
                DreamInventory.sealInto(sp, st);   // enter empty-handed; snapshot held in DreamState
                sp.teleportTo(dream, DreamWorld.ANCHOR.getX() + 0.5, DreamWorld.ANCHOR.getY(),
                        DreamWorld.ANCHOR.getZ() + 0.5, sp.getYRot(), sp.getXRot());
            }
        }

        if (outcome.nightmare()) {
            long now = level.getGameTime();
            Bond fearMark = new Bond(UUID.randomUUID(), NIGHTMARE_SPIRIT, Correspondence.THRESHOLD,
                    new Disposition(0f, 0f, outcome.dreadPenalty(), 0f),
                    null, List.of(), List.of(), now, now);
            psd.addMark(fearMark);
            csd.addDisturbance(Correspondence.THRESHOLD, outcome.dreadPenalty() * DISTURB_SCALE);
            chunk.setUnsaved(true);
        }
    }

    private static void applyDreamingEffects(Player p, boolean nightmare) {
        if (nightmare) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DREAM_TICKS, 0));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DREAM_TICKS, 0));        // nausea
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DREAM_TICKS, 0)); // slowness
        } else {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DREAM_TICKS, 0));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DREAM_TICKS, 0));
        }
    }
}
