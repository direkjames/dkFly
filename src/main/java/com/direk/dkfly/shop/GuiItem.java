package com.direk.dkfly.shop;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.direk.dkcore.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * One icon from gui.yml. Supports normal items and player heads:
 * <pre>
 * material: PLAYER_HEAD
 * texture: 'eyJ0ZXh0dXJlcyI6...'   # minecraft-heads.com "Value" (Base64)
 * texture: 'a1b2c3...'             # or just the texture ID / hash
 * texture: 'http://textures.minecraft.net/texture/a1b2c3...'  # or the full URL
 * texture: '%player%'              # the viewer's own head
 * </pre>
 * Head textures are applied offline (no web lookups), so opening the shop never lags.
 * Author: direk james
 */
public final class GuiItem {

    private static final String TEXTURE_URL = "http://textures.minecraft.net/texture/";

    private final Material material;
    private final int amount;
    private final String name;
    private final List<String> lore;
    private final boolean glow;
    private final String texture;     // already turned into a Base64 value, or "%player%", or null
    private final UUID headId;        // stable id per texture so identical heads stack

    private GuiItem(Material material, int amount, String name, List<String> lore, boolean glow, String texture) {
        this.material = material;
        this.amount = amount;
        this.name = name;
        this.lore = lore;
        this.glow = glow;
        this.texture = texture;
        this.headId = texture == null ? null : UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads material, amount, name, lore, glow and texture from a gui.yml section. */
    public static GuiItem from(ConfigurationSection sec, String fallbackMaterial, Logger log) {
        String matName = sec.getString("material", fallbackMaterial);
        Material mat = Material.matchMaterial(matName == null ? "" : matName);
        if (mat == null || !mat.isItem() || mat.isAir()) {
            log.warning("gui.yml: '" + sec.getCurrentPath() + ".material' (" + matName + ") is not an item; using "
                    + fallbackMaterial);
            mat = Material.matchMaterial(fallbackMaterial);
            if (mat == null) mat = Material.STONE;
        }
        String rawTexture = sec.getString("texture", "");
        String texture = null;
        if (rawTexture != null && !rawTexture.isBlank()) {
            texture = normaliseTexture(rawTexture.trim());
            if (texture == null) {
                log.warning("gui.yml: '" + sec.getCurrentPath() + ".texture' is not a valid head texture; ignored.");
            } else if (mat != Material.PLAYER_HEAD) {
                mat = Material.PLAYER_HEAD; // a texture only makes sense on a head
            }
        }
        int amount = Math.max(1, Math.min(64, sec.getInt("amount", 1)));
        return new GuiItem(mat, amount, sec.getString("name", ""), sec.getStringList("lore"),
                sec.getBoolean("glow", false), texture);
    }

    /** Builds the item for one viewer. */
    public ItemStack build(Player viewer, Messages messages, TagResolver placeholders) {
        ItemStack item = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (name != null && !name.isEmpty()) {
            meta.displayName(noItalic(messages.parse(name, placeholders)));
        } else {
            meta.displayName(Component.empty());
        }
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) lines.add(noItalic(messages.parse(line, placeholders)));
            meta.lore(lines);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());

        if (meta instanceof SkullMeta skull && texture != null) {
            if (texture.equals("%player%")) {
                skull.setPlayerProfile(viewer.getPlayerProfile());
            } else {
                PlayerProfile profile = Bukkit.createProfile(headId, "dkFlyHead");
                profile.setProperty(new ProfileProperty("textures", texture));
                skull.setPlayerProfile(profile);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Accepts a minecraft-heads.com Value (Base64), a texture URL or a bare texture hash
     * and returns the Base64 "textures" value Minecraft expects. Null if it can't be understood.
     */
    static String normaliseTexture(String in) {
        if (in.equalsIgnoreCase("%player%")) return "%player%";

        String url = null;
        if (in.startsWith("http://") || in.startsWith("https://")) {
            url = in.replace("https://", "http://");
        } else if (in.matches("[0-9a-fA-F]{32,128}")) {
            url = TEXTURE_URL + in.toLowerCase(Locale.ROOT);
        }
        if (url != null) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }

        // Otherwise it must already be a Base64 value containing a textures.minecraft.net URL.
        try {
            String decoded = new String(Base64.getDecoder().decode(in), StandardCharsets.UTF_8);
            return decoded.contains("textures.minecraft.net/texture/") ? in : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
