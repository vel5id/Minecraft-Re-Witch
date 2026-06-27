package com.vel5id.hexerei.soul;

/**
 * A deterministic read of accumulated soul-state — a dream glimpsed, not a
 * mark written. The dream is the {@code read} verb of the Law: it consumes
 * nothing, alters nothing, and returns the same answer for the same inputs.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code clarity} — how lucid the dream is (1 = crystal, 0 = murk).</li>
 *   <li>{@code dread} — weight of debt, marks, and land-disturbance pressing on the soul.</li>
 *   <li>{@code nightmare} — true when dread is high <i>and</i> clarity is low;
 *        a nightmare-domain may bleed through.</li>
 *   <li>{@code dominantDomain} — the loudest Correspondence in the chunk's
 *        disturbance, or {@code null} when nothing speaks.</li>
 * </ul>
 *
 * @see DreamResolver#read(float, float, java.util.Map)
 */
public record DreamReading(float clarity, float dread, boolean nightmare,
                           Correspondence dominantDomain) {
}
