package betterquesting.quests.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.Level;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import betterquesting.client.gui.GuiQuesting;
import betterquesting.client.gui.editors.json.GuiJsonObject;
import betterquesting.client.gui.misc.GuiEmbedded;
import betterquesting.core.BetterQuesting;
import betterquesting.party.PartyInstance;
import betterquesting.party.PartyInstance.PartyMember;
import betterquesting.party.PartyManager;
import betterquesting.quests.QuestInstance;
import betterquesting.utils.JsonHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

public abstract class TaskBase {
	/**
	 * List of users that have completed this quest<br>
	 * Now locked (developers should be using <b>setCompletion(uuid, boolean)</b>)
	 */
	private List<UUID> completeUsers = Collections.synchronizedList(new ArrayList<>());

	/**
	 * Default unlocalised name for this quest type
	 */
	public abstract String getUnlocalisedName();

	/**
	 * Returns a localized version of the quest type name
	 */
	public String getDisplayName() {
		return StatCollector.translateToLocal(this.getUnlocalisedName());
	}

	/**
	 * Updates the questing logic based on the given player instance<br>
	 * WARNING: Keep processing to a minimum here as this is called through EntityLivingUpdate<br>
	 * NOTE: This is only run server side. Send custom packets if client side action is required
	 */
	public void Update(QuestInstance quest, EntityPlayer player) {
		Update(player); // For use by out dated expansions only!
	}

	@Deprecated
	public void Update(EntityPlayer player) {
	}

	/**
	 * Fired when someone presses the detect button for the quest (can be used occasionally in Update to auto-detect). Use this for item submissions or manual
	 * updates
	 */
	public void Detect(QuestInstance quest, EntityPlayer player) {
		Detect(player); // For use by out dated expansions only!
	}

	@Deprecated
	public void Detect(EntityPlayer player) {
	}

	/**
	 * Gets the percentage of participation this player has contributed<br>
	 * Defaults to completion state but should be overridden for progression tasks
	 */
	public float GetParticipation(UUID uuid) {
		return isComplete(uuid) ? 1F : 0F;
	}

	/**
	 * Called by repeatable quests to reset progress for the next attempt
	 */
	public void ResetProgress(UUID uuid) {
		completeUsers.remove(uuid);
	}

	/**
	 * Resets progress for all members of the given user's party or just this user if no party is found
	 */
	public void ResetPartyProgress(UUID uuid) {
		PartyInstance party = PartyManager.GetParty(uuid);

		if (party == null) {
			ResetProgress(uuid);
		} else {
			for (PartyMember mem : party.GetMembers()) {
				ResetProgress(mem.userID);
			}
		}
	}

	/**
	 * Clear all progress for all users
	 */
	public void ResetAllProgress() {
		completeUsers = new ArrayList<>();
	}

	public boolean isComplete(UUID uuid) {
		return completeUsers.contains(uuid);
	}

	public void setCompletion(UUID uuid, boolean state) {
		PartyInstance party = PartyManager.GetParty(uuid);

		if (party == null) {
			if (state) {
				if (!completeUsers.contains(uuid)) {
					completeUsers.add(uuid);
				}
			} else {
				completeUsers.remove(uuid);
			}
		} else {
			for (PartyMember mem : party.GetMembers()) {
				if (state) {
					if (!completeUsers.contains(mem.userID)) {
						completeUsers.add(mem.userID);
					}
				} else {
					completeUsers.remove(mem.userID);
				}
			}
		}
	}

	public void writeToJson(JsonObject json) {
	}

	public void readFromJson(JsonObject json) {
		// Backup loader. Should make sure old progress is retained as long as the original file was not modified
		if (json.has("completeUsers")) {
			jMig = json;
		}
	}

	JsonObject jMig = null;

	// overwritten in sub classes. Must be called
	public void writeProgressToJson(JsonObject json) {}

	public void writeProgressToJson(JsonObject json, List<UUID> userFilter) {
		JsonArray jArray = new JsonArray();

		for (UUID uuid : completeUsers) {
			if (userFilter == null || userFilter.contains(uuid)) {
				jArray.add(new JsonPrimitive(uuid.toString()));
			}
		}
		json.add("completeUsers", jArray);
		writeProgressToJson(json);
	}

	public void readProgressFromJson(JsonObject json) {
		JsonObject jTmp = jMig != null ? jMig : json;
		jMig = null;

		completeUsers = new ArrayList<>();
		for (JsonElement entry : JsonHelper.GetArray(jTmp, "completeUsers")) {
			if (entry == null || !entry.isJsonPrimitive()) {
				continue;
			}

			try {
				completeUsers.add(UUID.fromString(entry.getAsString()));
			} catch (Exception e) {
				BetterQuesting.logger.log(Level.ERROR, "Unable to load UUID for task", e);
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public GuiEmbedded getGui(QuestInstance quest, GuiQuesting screen, int posX, int posY, int sizeX, int sizeY) {
		return getGui(screen, posX, posY, sizeX, sizeY);
	}

	@Deprecated
	@SideOnly(Side.CLIENT)
	public GuiEmbedded getGui(GuiQuesting screen, int posX, int posY, int sizeX, int sizeY) {
		return null;
	}

	/**
	 * Returns a new editor screen for this Reward type to edit the given data
	 */
	@SideOnly(Side.CLIENT)
	public GuiScreen GetEditor(GuiScreen parent, JsonObject data) {
		return new GuiJsonObject(parent, data);
	}
}
