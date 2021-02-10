package com.gamingmesh.jobs.Signs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.CMILib.CMIMaterial;
import com.gamingmesh.jobs.CMILib.ConfigReader;
import com.gamingmesh.jobs.CMILib.Version;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.TopList;

public class SignUtil {

    private final HashMap<String, HashMap<String, jobsSign>> signsByType = new HashMap<>();
    private final HashMap<String, jobsSign> signsByLocation = new HashMap<>();

    public HashMap<String, HashMap<String, jobsSign>> getSigns() {
	return signsByType;
    }

    public boolean removeSign(Location loc) {
	jobsSign jSign = signsByLocation.remove(jobsSign.locToBlockString(loc));
	if (jSign == null)
	    return false;

	HashMap<String, jobsSign> sub = signsByType.get(jSign.getIdentifier().toLowerCase());
	if (sub != null) {
	    sub.remove(jSign.locToBlockString());
	}
	return true;
    }

    public jobsSign getSign(Location loc) {
	return loc == null ? null : signsByLocation.get(jobsSign.locToBlockString(loc));
    }

    public void addSign(jobsSign jSign) {
	if (jSign == null)
	    return;

	signsByLocation.put(jSign.locToBlockString(), jSign);

	HashMap<String, jobsSign> old = signsByType.get(jSign.getIdentifier().toLowerCase());
	if (old == null) {
	    old = new HashMap<>();
	    signsByType.put(jSign.getIdentifier().toLowerCase(), old);
	}

	old.put(jSign.locToBlockString(), jSign);

	signsByType.put(jSign.getIdentifier().toLowerCase(), old);
    }

    public void LoadSigns() {
	if (!Jobs.getGCManager().SignsEnabled)
	    return;

	signsByType.clear();
	signsByLocation.clear();

	File file = new File(Jobs.getFolder(), "Signs.yml");
	YamlConfiguration f = YamlConfiguration.loadConfiguration(file);

	if (!f.isConfigurationSection("Signs"))
	    return;

	ConfigurationSection confCategory = f.getConfigurationSection("Signs");
	ArrayList<String> categoriesList = new ArrayList<>(confCategory.getKeys(false));
	if (categoriesList.isEmpty())
	    return;

	for (String category : categoriesList) {
	    ConfigurationSection nameSection = confCategory.getConfigurationSection(category);
	    jobsSign newTemp = new jobsSign();
	    if (nameSection.isString("World")) {
		newTemp.setWorldName(nameSection.getString("World"));
		newTemp.setX((int) nameSection.getDouble("X"));
		newTemp.setY((int) nameSection.getDouble("Y"));
		newTemp.setZ((int) nameSection.getDouble("Z"));
	    } else {
		newTemp.setLoc(nameSection.getString("Loc"));
	    }
	    if (nameSection.isString("Type"))
		newTemp.setType(SignTopType.getType(nameSection.getString("Type")));

	    newTemp.setNumber(nameSection.getInt("Number"));
	    if (nameSection.isString("JobName")) {
		SignTopType t = SignTopType.getType(nameSection.getString("JobName"));
		if (t == null)
		    newTemp.setJobName(nameSection.getString("JobName"));
	    }
	    newTemp.setSpecial(nameSection.getBoolean("Special"));

	    HashMap<String, jobsSign> old = signsByType.get(newTemp.getIdentifier().toLowerCase());
	    if (old == null) {
		old = new HashMap<>();
		signsByType.put(newTemp.getIdentifier().toLowerCase(), old);
	    }
	    String loc = newTemp.locToBlockString();
	    old.put(loc, newTemp);
	    signsByLocation.put(loc, newTemp);
	}

	if (!signsByLocation.isEmpty()) {
	    Jobs.consoleMsg("&e[Jobs] Loaded " + signsByLocation.size() + " top list signs");
	}
    }

    public void saveSigns() {
	File f = new File(Jobs.getFolder(), "Signs.yml");
	YamlConfiguration conf = YamlConfiguration.loadConfiguration(f);

	ConfigReader reader = new ConfigReader(f);

	conf.options().copyDefaults(true);
	reader.addComment("Signs", "DO NOT EDIT THIS FILE BY HAND!");

	if (!conf.isConfigurationSection("Signs"))
	    conf.createSection("Signs");

	int i = 0;
	for (jobsSign sign : signsByLocation.values()) {
	    ++i;
	    String path = "Signs." + i;
	    reader.set(path + ".Loc", sign.locToBlockString());
	    reader.set(path + ".Number", sign.getNumber());
	    reader.set(path + ".Type", sign.getType().toString());
	    reader.set(path + ".JobName", sign.getJobName());
	    reader.set(path + ".Special", sign.isSpecial());
	}

	try {
	    reader.save(f);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public void updateAllSign(Job job) {
	for (SignTopType types : SignTopType.values()) {
	    if (types != SignTopType.questtoplist)
		SignUpdate(job, types);
	}
    }

    public boolean SignUpdate(Job job) {
	return SignUpdate(job, SignTopType.toplist);
    }

    public boolean SignUpdate(Job job, SignTopType type) {
	if (!Jobs.getGCManager().SignsEnabled)
	    return true;

	if (type == null)
	    type = SignTopType.toplist;

	String jobNameOrType = jobsSign.getIdentifier(job, type);

	HashMap<String, jobsSign> signs = this.signsByType.get(jobNameOrType.toLowerCase());
	if (signs == null || signs.isEmpty())
	    return false;

	List<TopList> playerList = new ArrayList<>();

	switch (type) {
	case gtoplist:
	    playerList = Jobs.getJobsDAO().getGlobalTopList(0);
	    break;
	case questtoplist:
	    playerList = Jobs.getJobsDAO().getQuestTopList(0);
	    break;
	default:
	    break;
	}

	int timelapse = 1;

	HashMap<String, List<TopList>> temp = new HashMap<>();

	boolean save = false;
	for (jobsSign jSign : (new HashMap<String, jobsSign>(signs)).values()) {
	    Location loc = jSign.getLocation();
	    if (loc == null)
		continue;

	    Block block = loc.getBlock();
	    if (!(block.getState() instanceof Sign)) {
		if (!jobNameOrType.isEmpty()) {
		    HashMap<String, jobsSign> tt = this.signsByType.get(jobNameOrType.toLowerCase());
		    if (tt != null) {
			tt.remove(jSign.locToBlockString());
		    }
		}

		this.signsByLocation.remove(jSign.locToBlockString());
		save = true;
		continue;
	    }

	    String signJobName = jSign.getJobName();
	    if (type == SignTopType.toplist && (playerList = temp.get(signJobName)) == null) {
		playerList = Jobs.getJobsDAO().toplist(signJobName);
		temp.put(signJobName, playerList);
	    }

	    if (playerList.isEmpty())
		continue;

	    int number = jSign.getNumber() - 1;
	    Sign sign = (Sign) block.getState();

	    if (!jSign.isSpecial()) {
		for (int i = 0; i < 4; i++) {
		    if (i + number >= playerList.size()) {
			sign.setLine(i, "");
			continue;
		    }

		    TopList pl = playerList.get(i + number);
		    String playerName = pl.getPlayerInfo().getName();
		    if (playerName.length() > 15) {
			// We need to split 10 char of name, because of sign rows
			playerName = playerName.split("(?<=\\G.{10})")[0] + "~";
		    }

		    String line = "";
		    switch (type) {
		    case toplist:
		    case gtoplist:
			line = Jobs.getLanguage().getMessage("signs.List", "[number]", i + number + 1, "[player]", playerName, "[level]", pl.getLevel());
			break;
		    case questtoplist:
			line = Jobs.getLanguage().getMessage("signs.questList", "[number]", i + number + 1, "[player]", playerName, "[quests]", pl.getLevel());
			break;
		    default:
			break;
		    }

		    if (!line.isEmpty())
			sign.setLine(i, line);
		}
		sign.update();
		if (!UpdateHead(sign, playerList.get(0).getPlayerInfo().getName(), timelapse)) {
		    timelapse--;
		}
	    } else {
		if (jSign.getNumber() > playerList.size())
		    continue;

		TopList pl = playerList.get(jSign.getNumber() - 1);
		String playerName = pl.getPlayerInfo().getName();
		if (playerName.length() > 15) {
		    playerName = playerName.split("(?<=\\G.{10})")[0] + "~";
		}

		int no = jSign.getNumber() + number + 1;
		sign.setLine(0, translateSignLine("signs.SpecialList.p" + jSign.getNumber(), no, playerName, pl.getLevel(), signJobName));
		sign.setLine(1, translateSignLine("signs.SpecialList.name", no, playerName, pl.getLevel(), signJobName));

		switch (type) {
		case toplist:
		case gtoplist:
		    sign.setLine(2, Jobs.getLanguage().getMessage("signs.SpecialList.level", "[number]", no, "[player]", playerName, "[level]", pl.getLevel(), "[job]", signJobName));
		    break;
		case questtoplist:
		    sign.setLine(2, Jobs.getLanguage().getMessage("signs.SpecialList.quests", "[number]", no, "[player]", playerName, "[quests]", pl.getLevel(), "[job]", signJobName));
		    break;
		default:
		    break;
		}

		sign.setLine(3, translateSignLine("signs.SpecialList.bottom", no, playerName, pl.getLevel(), signJobName));
		sign.update();
		if (!UpdateHead(sign, pl.getPlayerInfo().getName(), timelapse)) {
		    timelapse--;
		}
	    }
	    timelapse++;
	}

	if (save)
	    saveSigns();

	return true;
    }

    private static String translateSignLine(String path, int number, String playerName, int level, String jobname) {
	return Jobs.getLanguage().getMessage(path,
	    "[number]", number,
	    "[player]", playerName,
	    "[level]", level,
	    "[job]", jobname);
    }

    @SuppressWarnings("deprecation")
    public boolean UpdateHead(final Sign sign, final String playerName, int timelapse) {
	if (playerName == null)
	    return false;

	timelapse = timelapse < 1 ? 1 : timelapse;

	BlockFace directionFacing = null;
	if (Version.isCurrentEqualOrLower(Version.v1_13_R2)) {
	    org.bukkit.material.Sign signMat = (org.bukkit.material.Sign) sign.getData();
	    directionFacing = signMat.getFacing();
	} else {
	    if (CMIMaterial.isWallSign(sign.getType())) {
		org.bukkit.block.data.type.WallSign data = (org.bukkit.block.data.type.WallSign) sign.getBlockData();
		directionFacing = data.getFacing();
	    } else {
		org.bukkit.block.data.type.Sign data = (org.bukkit.block.data.type.Sign) sign.getBlockData();
		directionFacing = data.getRotation();
	    }
	}

	final Location loc = sign.getLocation().clone();
	loc.add(0, 1, 0);

	if (directionFacing != null && !(loc.getBlock().getState() instanceof Skull))
	    loc.add(directionFacing.getOppositeFace().getModX(), 0, directionFacing.getOppositeFace().getModZ());

	Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Jobs.getInstance(), new Runnable() {
	    @Override
	    public void run() {
		if (!(loc.getBlock().getState() instanceof Skull))
		    return;

		Skull skull = (Skull) loc.getBlock().getState();
		if (playerName.equalsIgnoreCase(skull.getOwner()))
		    return;

		skull.setOwner(playerName);
		skull.update();
	    }
	}, timelapse * Jobs.getGCManager().InfoUpdateInterval * 20L);
	return true;
    }
}
