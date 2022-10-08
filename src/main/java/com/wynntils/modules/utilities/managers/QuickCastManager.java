/*
 *  * Copyright © Wynntils - 2022.
 */

package com.wynntils.modules.utilities.managers;

import com.wynntils.McIf;
import com.wynntils.Reference;
import com.wynntils.core.framework.enums.ClassType;
import com.wynntils.core.framework.instances.PlayerInfo;
import com.wynntils.core.framework.instances.data.ActionBarData;
import com.wynntils.core.framework.instances.data.CharacterData;
import com.wynntils.core.framework.instances.data.SpellData;
import com.wynntils.core.utils.ItemUtils;
import com.wynntils.modules.core.managers.PacketQueue;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wynntils.core.framework.instances.data.SpellData.SPELL_LEFT;
import static com.wynntils.core.framework.instances.data.SpellData.SPELL_RIGHT;

public class QuickCastManager {

    private static final CPacketAnimation leftClick = new CPacketAnimation(EnumHand.MAIN_HAND);
    private static final CPacketPlayerTryUseItem rightClick = new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND);
    private static final CPacketPlayerDigging releaseClick = new CPacketPlayerDigging(CPacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN);

    private static final int[] spellUnlock = { 1, 11, 21, 31 };

    // private static final Pattern WEAPON_SPEED_PATTERN = Pattern.compile("§7(.+) Attack Speed");
    private static final Pattern CLASS_REQ_OK_PATTERN = Pattern.compile("§a✔§7 Class Req:.+");
    private static final Pattern COMBAT_LVL_REQ_OK_PATTERN = Pattern.compile("§a✔§7 Combat Lv. Min:.+");
    private static final Pattern SPELL_POINT_MIN_NOT_REACHED_PATTERN = Pattern.compile("§c✖§7 (.+) Min: (\\d+)");

    private static final boolean[] NO_SPELL = new boolean[0];

    // Queue spells that the user is casting in the order they are pressed preventing overlap
    public static ArrayList<Integer> spellsInProgress = new ArrayList<Integer>();
    public static boolean meleeAttackInProgress = false;

    private static long lastMeleeAttackMillis = 0;

    /**
     * This method casts the first spell in spellsInProgress
     */
    private static void castQueuedSpell() {
        if (spellsInProgress.size() == 0) return;

        boolean[] fullSpell = getMouseClicks(spellsInProgress.get(0));
        boolean[] maybeHalfSpell = canCastSpell(spellsInProgress.get(0), fullSpell);

        if (maybeHalfSpell.length == 0) { // Ignore casting this spell if canCastSpells returns empty
            spellsInProgress.remove(0);
            castQueuedSpell();
            return;
        }

        int level = PlayerInfo.get(CharacterData.class).getLevel();
        boolean isLowLevel = level <= 11;
        Class<?> packetClass = isLowLevel ? SPacketTitle.class : SPacketChat.class;
        int offset = 3 - maybeHalfSpell.length;
        for (int i = 0; i < maybeHalfSpell.length; i++) {
            final int finalI = i;
            PacketQueue.queueComplexPacket(maybeHalfSpell[i] == SPELL_LEFT ? leftClick : rightClick, 
                packetClass, e -> checkSpellClick(e, finalI + offset, maybeHalfSpell[finalI], isLowLevel)
            ).onDrop(() -> onSpellClickDrop(finalI + offset));
        }
    }
    /**
     * This method takes a spell number and returns the relevent inputs for the spell
     * @return boolean array (left for False, right for True), empty for error
     */
    private static boolean[] getMouseClicks(int spellNumber) {
        if (PlayerInfo.get(CharacterData.class).getCurrentClass() == ClassType.ARCHER) {
            switch (spellNumber) {
                case 1:
                    return new boolean[]{SPELL_LEFT, SPELL_RIGHT, SPELL_LEFT};
            
                case 2:
                    return new boolean[]{SPELL_LEFT, SPELL_LEFT, SPELL_LEFT};
                    
                case 3:
                    return new boolean[]{SPELL_LEFT, SPELL_RIGHT, SPELL_RIGHT};

                case 4:
                    return new boolean[]{SPELL_LEFT, SPELL_LEFT, SPELL_RIGHT};

                default:
                    Reference.LOGGER.info("Wynntils Quick Cast Manager Spell Number Exceeded Bounds");
                    break;
            }
        } else {
            switch (spellNumber) {
                case 1:
                    return new boolean[]{SPELL_RIGHT, SPELL_LEFT, SPELL_RIGHT};
            
                case 2:
                    return new boolean[]{SPELL_RIGHT, SPELL_RIGHT, SPELL_RIGHT};
                    
                case 3:
                    return new boolean[]{SPELL_RIGHT, SPELL_LEFT, SPELL_LEFT};

                case 4:
                    return new boolean[]{SPELL_RIGHT, SPELL_RIGHT, SPELL_LEFT};

                default:
                    Reference.LOGGER.info("Wynntils Quick Cast Manager Spell Number Exceeded Bounds");
                    break;
            }
        }
        
        return new boolean[]{};
    }

    public static void castFirstSpell() {
        spellsInProgress.add(1);
        // Only begin casting if there is not a spell in progress
        if (spellsInProgress.size() == 1) castQueuedSpell();
    }

    public static void castSecondSpell() {
        spellsInProgress.add(2);
        // Only begin casting if there is not a spell in progress
        if (spellsInProgress.size() == 1) castQueuedSpell();
    }

    public static void castThirdSpell() {
        spellsInProgress.add(3);
        // Only begin casting if there is not a spell in progress
        if (spellsInProgress.size() == 1) castQueuedSpell();
    }

    public static void castFourthSpell() {
        spellsInProgress.add(4);
        // Only begin casting if there is not a spell in progress
        if (spellsInProgress.size() == 1) castQueuedSpell();
    }

    private static boolean[] canCastSpell(int spell, boolean[] checkedSpell) {
        if (!Reference.onWorld || !PlayerInfo.get(CharacterData.class).isLoaded()) {
            return NO_SPELL;
        }

        if (PlayerInfo.get(CharacterData.class).getLevel() < spellUnlock[spell - 1]) {
            McIf.player().sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "You have not yet unlocked this spell! You need to be level " + spellUnlock[spell - 1]
                    ));
            return NO_SPELL;
        }

        if (PlayerInfo.get(CharacterData.class).getCurrentMana() == 0) {
            McIf.player().sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "You do not have enough mana to cast this spell!"
                    ));
            return NO_SPELL;
        }

        ItemStack heldItem = McIf.player().getHeldItemMainhand();

        List<String> lore = ItemUtils.getLore(heldItem);

        //If item has attack speed line, it is a weapon
        boolean isWeapon = ItemUtils.isWeapon(heldItem);
        //Check class reqs to see if the weapon can be used by current class
        boolean classReqOk = false;
        //Is the current combat level enough to use the weapon
        boolean combatLvlMinReached = false;
        //If there is a spell point requirement that is not reached, store it and print it later
        String notReachedSpellPointRequirements = null;

        if (!isWeapon)
        {
            McIf.player().sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "The held item is not a weapon."
                    ));
            return NO_SPELL;
        }

        int i = 0;
        for (; i < lore.size(); i++) {
            if (CLASS_REQ_OK_PATTERN.matcher(lore.get(i)).matches())
            {
                classReqOk = true;
                break;
            }
        }

        if (!classReqOk)
        {
            McIf.player().sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "The held weapon is not for this class."
                    ));
            return NO_SPELL;
        }

        for (; i < lore.size(); i++) {
            if (COMBAT_LVL_REQ_OK_PATTERN.matcher(lore.get(i)).matches())
            {
                combatLvlMinReached = true;
                break;
            }
        }

        if (!combatLvlMinReached)
        {
            McIf.player().sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "The current class level is too low to use the held weapon."
                    ));
            return NO_SPELL;
        }

        for (; i < lore.size(); i++) {
            Matcher matcher = SPELL_POINT_MIN_NOT_REACHED_PATTERN.matcher(lore.get(i));
            if (matcher.matches())
            {
                notReachedSpellPointRequirements = matcher.group(1);
                break;
            }
        }

        if (notReachedSpellPointRequirements != null) {
            McIf.player().sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "The current class does not have enough " + notReachedSpellPointRequirements + " assigned to use the held weapon."
                    ));
            return NO_SPELL;
        }

        //If there is a partial spell, try to complete the spell
        boolean[] lastSpell = PlayerInfo.get(SpellData.class).getLastSpell();
        int lastSpellLength = lastSpell.length;
        if (lastSpellLength != 0 && lastSpellLength != 3) {
            for (int i1 = 0; i1 < lastSpellLength; i1++) {
                if (lastSpell[i1] != checkedSpell[i1]) {
                    McIf.player().sendMessage(new TextComponentString(
                            TextFormatting.GRAY + "Cannot start casting a spell while another spell cast is in progress."
                            ));
                    return NO_SPELL;
                }
            }

            boolean[] halfSpell = new boolean[checkedSpell.length - lastSpellLength];
            System.arraycopy(checkedSpell, lastSpellLength, halfSpell, 0, halfSpell.length);
            return halfSpell;
        }

        return checkedSpell;
    }

    /**
     * This method checks that the packets we recieve from the server are valid
     * @return boolean true if the response is valid, false if it was not
     */
    private static boolean checkSpellClick(Packet<?> input, int pos, boolean clickType, boolean isLowLevel) {
        boolean[] spell;

        SpellData data = PlayerInfo.get(SpellData.class);
        if (isLowLevel) {
            SPacketTitle title = (SPacketTitle) input;
            if (title.getType() != SPacketTitle.Type.SUBTITLE) return false;

            spell = data.parseSpellFromTitle(McIf.getFormattedText(title.getMessage()));
        } else {
            SPacketChat title = (SPacketChat) input;
            if (title.getType() != ChatType.GAME_INFO) return false;

            PlayerInfo.get(ActionBarData.class).updateActionBar(McIf.getUnformattedText(title.getChatComponent()));

            spell = data.getLastSpell();
        }
        // Sometimes we do not get the final click of the spell above the hotbar as after casting it shows coordinates
        // if this happens spell.length == 0 for the last input, if the input wasnt recieved spell.length == 2
        boolean successful = (pos < spell.length && spell[pos] == clickType) || (spell.length == 0 && pos == 2);

        // If the final click was successful, we should cast the next spell
        // NOTE: We do not need to consider failure, this is handled by onSpellClickDrop
        if (spellsInProgress.size() > 0 && pos == 2 && successful) {
            spellsInProgress.remove(0);
            
            castQueuedSpell();
        }

        return successful;
    }

    /**
     * This method is called when the PacketQueue drops our spell click to avoid spamming
     */
    private static void onSpellClickDrop(int pos) {
        if (pos == 2 && spellsInProgress.size() > 0) {
            // If the final click in a spell was never acknowledged, assume that it was received and continue casting
            spellsInProgress.remove(0);
            castQueuedSpell();
        }
    }

    /**
     * Queues the player to make a melee attack if they are able to
     */
    public static void doMeleeAttack() {
        if (!Reference.onWorld || !PlayerInfo.get(CharacterData.class).isLoaded()) return;

        // Note: I used 2 timers for this function, reasons stated below
        // The CooldownTracker gets the minecraft cooldown seen in the hotbar, but there is a 1-2 tick delay where after attacking the cooldown is still 0
        // Using only this timer, multiple attack packets are sent immediately after recieving attacking
        // lastMeleeAttackMillis is used to complement this by waiting ~150 ms (same as Super Fast Attack Speed) so the CooldownTracker has time to update
        // This is the only way Ive found to get the player attack speed after modifiers such as gear calculations
        //
        // This could be improved by finding a better way to get the players true attack speed (heldItem fields, and attack strength do not work from my testing)
        if (lastMeleeAttackMillis + 150 > System.currentTimeMillis()) return;
        ItemStack heldItem = McIf.player().getHeldItemMainhand();
        if (!ItemUtils.isWeapon(heldItem)) return;

        if((McIf.player().getCooldownTracker().getCooldown(heldItem.getItem(), 0) > 0) || meleeAttackInProgress) return;

        Class<?> packetClass = PlayerInfo.get(CharacterData.class).getCurrentClass() == ClassType.ARCHER ? CPacketPlayerTryUseItem.class : CPacketAnimation.class;
        
        // We set the attack timer to reset both if the response is recieved or it is dropped
        PacketQueue.queueComplexPacket(PlayerInfo.get(CharacterData.class).getCurrentClass() == ClassType.ARCHER ? rightClick : leftClick,
            packetClass, e -> updateMeleeTimer()
        ).onDrop(() -> updateMeleeTimer());

        meleeAttackInProgress = true;
    }

    /**
     * Updates the Quickcast Attack Timer Variables when a packet is recieved
     */
    private static boolean updateMeleeTimer() {
        meleeAttackInProgress = false;
        lastMeleeAttackMillis = System.currentTimeMillis();

        return true;
    }
}
