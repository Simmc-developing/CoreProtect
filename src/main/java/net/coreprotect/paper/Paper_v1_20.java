package net.coreprotect.paper;

import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.regex.Matcher;

import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.PlayerProfile;

import net.coreprotect.config.Config;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.ErrorReporter;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Paper_v1_20 extends Paper_v1_19 {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    @Override
    public String getLine(Sign sign, int line) {
        // https://docs.adventure.kyori.net/serializer/
        if (line < 4) {
            return LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.FRONT).line(line));
        }
        else {
            return LegacyComponentSerializer.legacySection().serialize(sign.getSide(Side.BACK).line(line - 4));
        }
    }

    @Override
    public String getSkullOwner(Skull skull) {
        PlayerProfile playerProfile = skull.getPlayerProfile();
        if (playerProfile == null) {
            return null;
        }

        String owner = playerProfile.getName();
        if (playerProfile.getId() != null) {
            owner = playerProfile.getId().toString();
        }
        else if (Config.getGlobal().MYSQL && owner != null && owner.length() > 255) {
            return owner.substring(0, 255);
        }

        return owner;
    }

    @Override
    public void setSkullOwner(Skull skull, String owner) {
        if (owner == null || owner.length() == 0) {
            return;
        }

        if (owner.length() >= 32 && owner.contains("-")) {
            skull.setPlayerProfile(Bukkit.createProfile(UUID.fromString(owner)));
        }
        else {
            skull.setPlayerProfile(Bukkit.createProfile(owner));
        }
    }

    @Override
    public String getSkullSkin(Skull skull) {
        PlayerProfile playerProfile = skull.getPlayerProfile();
        if (playerProfile == null) {
            return null;
        }

        URL skin = playerProfile.getTextures().getSkin();
        if (skin == null) {
            return null;
        }

        return skin.toString();
    }

    @Override
    public void setSkullSkin(Skull skull, String skin) {
        try {
            if (skin == null || skin.length() == 0) {
                return;
            }

            String skinUrl = SkullSkin.getSkinUrl(skin);
            if (skinUrl == null) {
                return;
            }

            PlayerProfile playerProfile = skull.getPlayerProfile();
            if (playerProfile == null) {
                playerProfile = Bukkit.createProfile(UUID.randomUUID());
            }

            PlayerTextures textures = playerProfile.getTextures();
            textures.setSkin(URI.create(skinUrl).toURL());
            playerProfile.setTextures(textures);
            skull.setPlayerProfile(playerProfile);
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
    }

    @Override
    public boolean sendItemComponent(CommandSender sender, String string, String bypass) {
        try {
            sender.sendMessage(buildItemComponent(string, bypass));
            return true;
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    static Component buildItemComponent(String string, String bypass) {
        Component message = Component.empty();
        StringBuilder builder = new StringBuilder();
        Matcher matcher = Util.tagParser.matcher(string);

        while (matcher.find()) {
            String value = matcher.group(1);
            if (value == null) {
                builder.append(matcher.group(2));
                continue;
            }

            if (builder.length() > 0) {
                message = message.append(deserialize(builder.toString()));
                builder.setLength(0);
            }

            String[] data = value.split("\\|", 4);
            if (data[0].equals(Chat.COMPONENT_COMMAND) && data.length >= 3) {
                Component component = deserialize(data[2]).clickEvent(ClickEvent.runCommand(data[1]));
                if (Config.getGlobal().HOVER_EVENTS) {
                    component = component.hoverEvent(HoverEvent.showText(deserialize(StringUtils.hoverCommandFilter(data[1]))));
                }
                message = message.append(component);
            }
            else if (data[0].equals(Chat.COMPONENT_POPUP) && data.length >= 3) {
                Component component = deserialize(data[2]);
                if (Config.getGlobal().HOVER_EVENTS) {
                    component = component.hoverEvent(HoverEvent.showText(deserialize(processComponent(data[1]))));
                }
                message = message.append(component);
            }
            else if (data[0].equals(Chat.COMPONENT_ITEM) && data.length == 4) {
                message = message.append(createItemComponent(data));
            }
        }

        if (builder.length() > 0) {
            message = message.append(deserialize(builder.toString()));
        }
        if (bypass != null) {
            message = message.append(Component.text(bypass));
        }
        return message;
    }

    private static Component createItemComponent(String[] data) {
        Component component = deserialize(data[3]);
        if (!Config.getGlobal().HOVER_EVENTS) {
            return component;
        }

        try {
            ItemStack item = ItemUtils.getGivableItem(Integer.parseInt(data[1]));
            if (item != null) {
                return component.hoverEvent(item.asHoverEvent());
            }
        }
        catch (RuntimeException | LinkageError e) {
            // Fall through to the existing text tooltip.
        }

        String tooltip = processComponent(data[2]);
        return tooltip.isEmpty() ? component : component.hoverEvent(HoverEvent.showText(deserialize(tooltip)));
    }

    private static Component deserialize(String text) {
        return LEGACY_SERIALIZER.deserialize(text);
    }

    private static String processComponent(String component) {
        return component.replace(Chat.COMPONENT_PIPE, "|");
    }

}
