package me.skinnyjeans.gmd;

import me.skinnyjeans.gmd.hooks.SaveManager;
import me.skinnyjeans.gmd.hooks.databases.*;
import me.skinnyjeans.gmd.models.Difficulty;
import me.skinnyjeans.gmd.models.Minecrafter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.logging.Level;

public class Affinity {
    protected Main m;
    protected SaveManager SQL;
    protected DataManager data;
    protected int minAffinity,maxAffinity,onDeath,onPVPKill,startAffinity,onInterval,onPlayerHit,worldAffinity,maxAffinityLimit,minAffinityLimit;
    protected boolean randomizer,calcMinAffinity,calcMaxAffinity,customArmorSpawnChance;
    protected String difficultyType,saveType,equipmentCheck;
    protected List<String> disabledWorlds,disabledMobs,minAffinityListItems,maxAffinityListItems;
    protected ArrayList<Integer> mobsOverrideIgnore = new ArrayList<>();
    protected ArrayList<Integer> ignoreMobs = new ArrayList<>();
    protected HashMap<String, Difficulty> difficultyList = new HashMap<>();
    protected HashMap<UUID, Minecrafter> playerList = new HashMap<>();
    protected HashMap<String, Integer> chancePerArmor = new HashMap<>();
    protected HashMap<String, Integer> chancePerWeapon = new HashMap<>();
    protected HashMap<String, Integer> mobsPVE = new HashMap<>();
    protected HashMap<String, Integer> blocks = new HashMap<>();
    protected HashMap<String, Integer> minAffinityItems = new HashMap<>();
    protected HashMap<String, Integer> maxAffinityItems = new HashMap<>();
    protected HashMap<String, Inventory> inventorySettings = new HashMap<>();
    protected HashMap<String, UUID> playersUUID = new HashMap<>();
    protected ArrayList<String> difficulties,customSpawnWeapons;
    protected ArrayList<PotionEffectType> effects = new ArrayList<>(Arrays.asList(PotionEffectType.WITHER, PotionEffectType.POISON,
            PotionEffectType.BLINDNESS, PotionEffectType.WEAKNESS, PotionEffectType.SLOW, PotionEffectType.CONFUSION, PotionEffectType.HUNGER));
    protected ArrayList<EntityPotionEffectEvent.Cause> effectCauses = new ArrayList<>(Arrays.asList(EntityPotionEffectEvent.Cause.ATTACK,
            EntityPotionEffectEvent.Cause.ARROW, EntityPotionEffectEvent.Cause.POTION_SPLASH));
    protected ArrayList<List<Enchantment>> enchantmentConflict = new ArrayList<>(Arrays.asList(
            Arrays.asList(Enchantment.MENDING, Enchantment.ARROW_INFINITE),
            Arrays.asList(Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.PROTECTION_EXPLOSIONS, Enchantment.PROTECTION_FIRE, Enchantment.PROTECTION_PROJECTILE),
            Arrays.asList(Enchantment.SILK_TOUCH, Enchantment.LOOT_BONUS_BLOCKS),
            Arrays.asList(Enchantment.DAMAGE_ALL, Enchantment.DAMAGE_ARTHROPODS, Enchantment.DAMAGE_UNDEAD),
            Arrays.asList(Enchantment.DEPTH_STRIDER, Enchantment.FROST_WALKER)));

    public Affinity(Main ma) {
        m = ma;
        emptyHitMobsList();
        loadConfig();
        if(Bukkit.getOnlinePlayers().size() > 0) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED+"[DynamicDifficulty] Reloading or loading DynamicDifficulty with a plugin manager may break it!");
            Bukkit.getOnlinePlayers().forEach(usr -> {
                UUID uuid = usr.getUniqueId();
                SQL.getAffinityValues(uuid.toString(), r -> {
                    Minecrafter mc = new Minecrafter(uuid, usr.getName());
                    if (r.get(0) == -1) {
                        mc.setAffinity(startAffinity);
                        mc.setMaxAffinity(-1);
                        mc.setMinAffinity(-1);
                        playerList.put(uuid, mc);
                        SQL.updatePlayer(uuid.toString(), startAffinity, -1, -1);
                    } else {
                        mc.setAffinity(r.get(0));
                        mc.setMaxAffinity(r.get(1));
                        mc.setMinAffinity(r.get(2));
                        playerList.put(uuid, mc);
                    }
                    playersUUID.put(usr.getName(), uuid);
                });
            });
        }
    }

    public void reloadConfig() {
        saveData();
        mobsPVE.clear(); blocks.clear(); inventorySettings.clear(); difficultyList.clear();
        difficulties.clear(); disabledWorlds.clear(); mobsOverrideIgnore.clear();
        loadConfig();
    }

    /** Load's everything in from the config file and sorts or calculates different data from it */
    private void loadConfig(){
        data = new DataManager(m);
        saveType = data.getConfig().getString("saving-data.type");
        randomizer = data.getConfig().getBoolean("difficulty-modifiers.randomize");
        minAffinity = data.getConfig().getInt("min-affinity");
        maxAffinity = data.getConfig().getInt("max-affinity");
        onDeath = data.getConfig().getInt("death");
        onPVPKill = data.getConfig().getInt("pvp-kill");
        startAffinity = data.getConfig().getInt("starting-affinity");
        onInterval = data.getConfig().getInt("points-per-minute");
        onPlayerHit = data.getConfig().getInt("player-hit");
        difficultyType = data.getConfig().getString("difficulty-modifiers.type");
        equipmentCheck = data.getConfig().getString("calculating-affinity.check-equipment");
        disabledWorlds = data.getConfig().getStringList("disabled-worlds");
        disabledMobs = data.getConfig().getStringList("disabled-mobs");
        customArmorSpawnChance = data.getConfig().getBoolean("advanced-features.custom-mob-items-spawn-chance");
        calcMinAffinity = data.getConfig().getBoolean("advanced-features.auto-calculate-min-affinity");
        calcMaxAffinity = data.getConfig().getBoolean("advanced-features.auto-calculate-max-affinity");
        HashMap<Integer, String> tmpMap = new HashMap<>();
        ArrayList<String> tmpList = new ArrayList<>();
        ConfigurationSection section = data.getConfig().getConfigurationSection("difficulty");

        if((calcMinAffinity || calcMaxAffinity) && !difficultyType.equalsIgnoreCase("world")) {
            long time = 0L;
            if(equipmentCheck.equalsIgnoreCase("every-minute")) {
                time = 1200L;
            } else if(equipmentCheck.equalsIgnoreCase("every-5-minutes")) {
                time = 6000L;
            }

            if(time != 0L) {
                Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(m, () -> {
                    if (Bukkit.getOnlinePlayers().size() > 0)
                        checkEquipmentEvery();
                }, 0L, time);
            }

            if(calcMinAffinity) {
                minAffinityLimit = data.getConfig().getInt("calculating-affinity.min-affinity-changes.affinity-limit");
                for(Object s : data.getConfig().getList("calculating-affinity.min-affinity-changes.items-held-or-worn").toArray()) {
                    String[] sep = s.toString().replaceAll("[{|}]","").split("=");
                    minAffinityListItems.add(sep[0]);
                    try{
                        minAffinityItems.put(sep[0], Integer.parseInt(sep[1]));
                    } catch(Exception e){
                        minAffinityItems.put(sep[0], 1);
                    }
                }
            }

            if(calcMaxAffinity) {
                maxAffinityLimit = maxAffinity - data.getConfig().getInt("calculating-affinity.max-affinity-changes.affinity-limit");
                for(Object s : data.getConfig().getList("calculating-affinity.max-affinity-changes.items-held-or-worn").toArray()) {
                    String[] sep = s.toString().replaceAll("[{|}]","").split("=");
                    maxAffinityListItems.add(sep[0]);
                    try{
                        maxAffinityItems.put(sep[0], Integer.parseInt(sep[1]));
                    } catch(Exception e){
                        maxAffinityItems.put(sep[0], 1);
                    }
                }
            }
        }

        if(SQL == null) {
            try{
                if(saveType.equalsIgnoreCase("mysql") || saveType.equalsIgnoreCase("sqlite") || saveType.equalsIgnoreCase("postgresql")){
                    SQL = new SQL(m, data, saveType);
                } else if(saveType.equalsIgnoreCase("mongodb")) {
                    SQL = new MongoDB(m, data);
                } else if(saveType.equalsIgnoreCase("none")){
                    SQL = new None();
                } else {
                    SQL = new File(m, data);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED+"[DynamicDifficulty] Can't connect to the database, switching to 'file' mode");
                SQL = new File(m, data);
            }
        }

        try {
            SQL.getAffinityValues("world", r -> {
                if(r.get(0) == -1){
                    SQL.updatePlayer("world", startAffinity, -1, -1);
                    worldAffinity = startAffinity;
                } else {
                    worldAffinity = r.get(0);
                }
            });
        } catch(Exception e){
            e.printStackTrace();
        }

        if(minAffinity > maxAffinity){
            int tmp = maxAffinity;
            maxAffinity = minAffinity;
            minAffinity = tmp;
            Bukkit.getLogger().log(Level.WARNING, "[DynamicDifficulty] MinAffinity is larger than MaxAffinity, so their values have been switched.");
        }

        for (String key : section.getKeys(false)) {
            Difficulty tmp = new Difficulty(key);

            String d = "difficulty-modifiers.";
            int xpMult = (int) Math.ceil(section.getDouble(key + ".experience-multiplier", 100.0) * data.getConfig().getDouble(d+"experience-multiplier", 1.0));
            int dblLoot = (int) Math.ceil(section.getDouble(key + ".double-loot-chance", 100.0) * data.getConfig().getDouble(d+"double-loot-chance-multiplier", 1.0));
            int dmgByMobs = (int) Math.ceil(section.getDouble(key + ".damage-done-by-mobs", 100.0) * data.getConfig().getDouble(d+"damage-done-by-mobs-multiplier", 1.0));
            int dmgOnMobs = (int) Math.ceil(section.getDouble(key + ".damage-done-on-mobs", 100.0) * data.getConfig().getDouble(d+"damage-done-on-mobs-multiplier", 1.0));

            tmp.setAffinity(section.getInt(key + ".affinity-required"));
            tmp.setIgnoredMobs(section.getStringList(key + ".mobs-ignore-player"));
            tmp.setPrefix(section.getString(key + ".prefix", key));
            tmp.setHungerDrain(section.getInt(key + ".hunger-drain-chance", 100));
            tmp.setKeepInventory(section.getBoolean(key + ".keep-inventory", false));
            tmp.setEffectsOnAttack(section.getBoolean(key + ".effects-when-attacked", true));
            tmp.setDamageByMobs(dmgByMobs);
            tmp.setDamageOnMobs(dmgOnMobs);
            tmp.setDoubleLoot(dblLoot);
            tmp.setExperienceMultiplier(xpMult);

            tmpList.add(key);
            difficultyList.put(key, tmp);
        }

        if(customArmorSpawnChance) {
            section = data.getConfig().getConfigurationSection("custom-mob-items-spawn-chance.difficulties");
            for (String key : section.getKeys(false)) {
                if(difficultyList.containsKey(key)) {
                    Difficulty d = difficultyList.get(key);
                    d.setChanceToEnchant(section.getDouble(key + ".chance-to-enchant-a-piece"));
                    d.setChanceToHaveArmor(section.getDouble(key + ".chance-to-have-armor"));
                    d.setMaxEnchants(section.getInt(key + ".max-enchants"));
                    d.setMaxEnchantLevel(section.getInt(key + ".max-level"));
                    d.setArmorDropChance(section.getDouble(key + ".armor-drop-chance"));

                    HashMap<String, Double> chances = new HashMap<>();
                    chances.put("helmet", section.getDouble(key + ".helmet-chance"));
                    chances.put("weapon", section.getDouble(key + ".weapon-chance"));
                    chances.put("chest", section.getDouble(key + ".chest-chance"));
                    chances.put("leggings", section.getDouble(key + ".leggings-chance"));
                    chances.put("boots", section.getDouble(key + ".boots-chance"));
                    d.setEnchantChances(chances);
                }
            }
            ArrayList<String> array = new ArrayList<>(Arrays.asList("leather", "gold", "chain", "iron", "diamond", "netherite"));
            int total = 0;
            for(String key : array) {
                int count = data.getConfig().getInt("custom-mob-items-spawn-chance.armor-set-weight." + key);
                total += count;
                chancePerArmor.put(key, data.getConfig().getInt("custom-mob-items-spawn-chance.armor-set-weight." + key));
            }
            chancePerArmor.put("total", total);

            loadWeapons();
        }
        loadMobs();
        loadBlocks();
        createPlayerInventory();
        createIndividialPlayerInventory();

        // Everything beneath this comment is to sort the difficulties by their affinity requirement
        for (String s : tmpList) tmpMap.put(difficultyList.get(s).getAffinity(), s);
        TreeMap<Integer, String> tm = new TreeMap<>(tmpMap);
        String lastKey = null;
        for (int key : tm.keySet()) {
            String thisKey = tmpMap.get(key).replace(" ", "_");
            difficulties.add(thisKey);
            if(tmpMap.size() == difficulties.size()) {
                difficultyList.get(thisKey).setUntil(maxAffinity);
            } else if (lastKey != null) {
                difficultyList.get(lastKey).setUntil(key - 1);
            }
            lastKey = thisKey;
        }
        tm.clear(); tmpList.clear(); tmpMap.clear();
    }

    private void checkEquipmentEvery(){
        if(calcMinAffinity) {
            Bukkit.getOnlinePlayers().forEach(pl -> {
                ArrayList<String> equipment = new ArrayList<>(Arrays.asList(pl.getEquipment().getItemInMainHand().getType().toString()));
                for(ItemStack item : pl.getEquipment().getArmorContents()) { equipment.add(item.getType().toString()); }
                for(int i=0;i<equipment.size();i++)
                    if(minAffinityListItems.contains(equipment.get(i))) {
                        Minecrafter mc = playerList.get(pl.getUniqueId());
                        if(mc.getMinAffinity() == -1)
                            mc.setMinAffinity(minAffinity);
                        mc.addMinAffinity(minAffinityItems.get(equipment.get(i)), minAffinityLimit);
                    }
            });
        }

        if(calcMaxAffinity) {
            Bukkit.getOnlinePlayers().forEach(pl -> {
                ArrayList<String> equipment = new ArrayList<>(Arrays.asList(pl.getEquipment().getItemInMainHand().getType().toString()));
                for(ItemStack item : pl.getEquipment().getArmorContents()) { equipment.add(item.getType().toString()); }
                for(int i=0;i<equipment.size();i++)
                    if(maxAffinityListItems.contains(equipment.get(i))) {
                        Minecrafter mc = playerList.get(pl.getUniqueId());
                        if(mc.getMaxAffinity() == -1)
                            mc.setMaxAffinity(maxAffinity);
                        mc.addMaxAffinity(maxAffinityItems.get(equipment.get(i))*-1, maxAffinityLimit);
                    }
            });
        }
    }

    private void loadWeapons() {
        String weaponNames = "";
        Object[] weapons = data.getConfig().getList("custom-mob-items-spawn-chance.weapons-include").toArray();
        int total = 0;
        for(Object s : weapons) {
            String[] sep = s.toString().replaceAll("[{|}]","").split("=");
            int count;
            try {
                if(NamespacedKey.fromString(Material.valueOf(sep[0]).getKey().toString()) == null)
                    weaponNames += sep[0] + ", ";
                count = Integer.parseInt(sep[1]);
            } catch(Exception e) {
                count = 1;
            }
            total += count;
            customSpawnWeapons.add(sep[0]);
            chancePerWeapon.put(sep[0], count);
        }
        chancePerWeapon.put("total", total);
        if(weaponNames != "")
            Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW+"[DynamicDifficulty] Invalid blocks in blocks: "+weaponNames);
    }

    private void loadMobs() {
        String mobNames = "";
        Object[] tmpMobs = data.getConfig().getList("mobs-count-as-pve").toArray();
        for(Object s : tmpMobs){
            String[] sep = s.toString().replaceAll("[{|}]","").split("=");
            try{
                if(NamespacedKey.fromString(EntityType.valueOf(sep[0]).getKey().toString()) == null)
                    mobNames += sep[0] + ", ";
                mobsPVE.put(sep[0], Integer.parseInt(sep[1]));
            } catch(Exception e){
                mobsPVE.put(sep[0], data.getConfig().getInt("pve-kill", 2));
            }
        }
        if(mobNames != "")
            Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW+"[DynamicDifficulty] Invalid blocks in mobs-count-as-pve: "+mobNames);
    }

    private void loadBlocks(){
        String blockNames = "";
        Object[] tmpBlocks = data.getConfig().getList("blocks").toArray();
        for(Object s : tmpBlocks){
            String[] sep = s.toString().replaceAll("[{|}]","").split("=");
            try {
                if(NamespacedKey.fromString(Material.valueOf(sep[0]).getKey().toString()) == null)
                    blockNames += sep[0] + ", ";
                blocks.put(sep[0], Integer.parseInt(sep[1]));
            } catch(Exception e) {
                blocks.put(sep[0], data.getConfig().getInt("block-mined", 1));
            }
        }
        if(blockNames != "")
            Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW+"[DynamicDifficulty] Invalid blocks in blocks: "+blockNames);
    }

    public UUID getPlayerUUID(String name) {
        return playersUUID.getOrDefault(name, null);
    }

    public int getAffinity(UUID uuid) {
        if(uuid == null)
            return worldAffinity;
        if(playerList.containsKey(uuid))
            return playerList.get(uuid).getAffinity();
        return -1;
    }

    public void setAffinity(UUID uuid, int x) {
        if (uuid == null) { worldAffinity = calcAffinity(x); }
        else { playerList.get(uuid).setAffinity(calcAffinity(x)); }
    }

    private void emptyHitMobsList() {
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(m, () -> {
            if(mobsOverrideIgnore.size() > 0)
                mobsOverrideIgnore.clear();
        }, 0L, 1200L);
    }

    public int getMaxAffinity(UUID uuid) { return playerList.get(uuid).getMaxAffinity(); }
    public void setMaxAffinity(UUID uuid, int x) { playerList.get(uuid).setMaxAffinity(calcAffinity(x)); }
    public int getMinAffinity(UUID uuid) { return playerList.get(uuid).getMinAffinity(); }
    public void setMinAffinity(UUID uuid, int x) { playerList.get(uuid).setMinAffinity(calcAffinity(x)); }
    public boolean hasDifficulty(String x) { return difficulties.contains(x); }
    public int getDifficultyAffinity(String x) { return difficultyList.get(x).getAffinity(); }
    public ArrayList<String> getDifficulties() { return difficulties; }
    public String getPrefix(UUID uuid){ return difficultyList.get(calcDifficulty(uuid)).getPrefix(); }
    public interface findIntegerCallback { void onQueryDone(List<Integer> r); }

    public int getVariable(String x) {
        if(x.equals("min-affinity")) { return minAffinity; }
        else if(x.equals("max-affinity")) { return maxAffinity; }
        return -1;
    }

    /** Saves all player and world data every few minutes. */
    public void saveData(){
        SQL.updatePlayer("world", worldAffinity, -1, -1);
        playersUUID.forEach((name, uuid) -> {
            Minecrafter pl = playerList.get(uuid);
            SQL.updatePlayer(uuid.toString(), pl.getAffinity(), pl.getMaxAffinity(), pl.getMinAffinity());
        });
        if(saveType.equalsIgnoreCase("file"))
            data.saveData();
    }

    /**
     * Calculates if the amount exceeds the users Maximum or the servers Minimum/Maximum
     *
     * @param x is the affinity given to calculate
     * @return INT the affinity after it has been checked
     */
    public int calcAffinity(int x) {
        if(x == -1) { return -1; }
        if (x > maxAffinity) {
            x = maxAffinity;
        } else if (x < minAffinity) {
            x = minAffinity;
        }
        return x;
    }

    public ItemStack calcEnchant(ItemStack item, String piece, String diff, double chanceToEnchant) {
        for(int j=0;j<difficultyList.get(diff).getMaxEnchants();j++) {
            Enchantment chosenEnchant = null;
            int total_amount = 0;
            for(Object s : data.getConfig().getList("custom-mob-items-spawn-chance."+piece+"-enchants-include").toArray()) {
                String[] sep = s.toString().replaceAll("[{|}]","").split("=");
                try {
                    total_amount += Integer.parseInt(sep[1]);
                } catch(Exception e) {
                    total_amount += 1;
                }
            }
            int random = new Random().nextInt(total_amount + 1) - 1;
            total_amount = 0;
            for(Object s : data.getConfig().getList("custom-mob-items-spawn-chance."+piece+"-enchants-include").toArray()) {
                String[] sep = s.toString().replaceAll("[{|}]","").split("=");
                int curr;
                try {
                    curr = Integer.parseInt(sep[1]);
                } catch(Exception e) {
                    curr = 1;
                }
                if(total_amount < random && (total_amount + curr) >= random) {
                    chosenEnchant = Enchantment.getByKey(NamespacedKey.minecraft(sep[0].toLowerCase()));
                    break;
                }
                total_amount += curr;
            }

            boolean allowed = true;
            if(!data.getConfig().getBoolean("custom-mob-items-spawn-chance.override-enchant-conflicts", false))
                for(List<Enchantment> enchantList : enchantmentConflict)
                    if(allowed) {
                        for(Enchantment currentEnchant : enchantList)
                            if(chosenEnchant.equals(currentEnchant) || item.containsEnchantment(currentEnchant))
                                allowed = false;
                    } else {
                        break;
                    }
            if (!allowed) {
                j--;
                continue;
            }

            int chosenLevel;
            if(data.getConfig().getBoolean("custom-mob-items-spawn-chance.override-default-limits", false)){
                chosenLevel = new Random().nextInt((int)Math.round(difficultyList.get(diff).getMaxEnchantLevel() - 1)) + 1;
            } else {
                int maxlvl = Math.round(difficultyList.get(diff).getMaxEnchantLevel());
                if(maxlvl > chosenEnchant.getMaxLevel()) {
                    chosenLevel = new Random().nextInt(chosenEnchant.getMaxLevel() - 1) + 1;
                } else {
                    chosenLevel = new Random().nextInt(maxlvl);
                }
            }
            item.addUnsafeEnchantment(chosenEnchant, chosenLevel);
            if(new Random().nextDouble() > chanceToEnchant)
                break;
        }
        return item;
    }

    /** Closes all databases */
    public void exitProgram() {
        try {
            SQL.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the difficulty of an user or the world
     *
     * @param uuid of the user or the world if null
     * @return String of the difficulty the world/user is on
     */
    public String calcDifficulty(UUID uuid) {
        if(randomizer) { return difficulties.get(new Random().nextInt(difficulties.size() - 1)); }
        int af = (uuid != null) ? playerList.get(uuid).getAffinity() : worldAffinity;
        try {
            for (int i = 0; i < difficulties.size(); i++)
                if(difficultyList.get(difficulties.get(i)).getAffinity() >= af && difficultyList.get(difficulties.get(i)).getUntil() <= af)
                    return difficulties.get(i);
        } catch(IndexOutOfBoundsException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED+"[DynamicDifficulty] "+e+": Looks like the difficulties didn't load in properly, will try to load them in again. Unless difficulties is empty...");
            reloadConfig();
            if(difficulties.size() == 0) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED+"[DynamicDifficulty] Difficulties still haven't loaded in after reloading, Make sure you have atleast 1 difficulty in the config!");
                Difficulty d = new Difficulty("Normal");
                d.setAffinity(minAffinity);
                d.setUntil(maxAffinity);
                d.setDoubleLoot(0);
                d.setExperienceMultiplier(90);
                d.setDamageOnMobs(100);
                d.setDamageByMobs(75);
                d.setPrefix("&7&l[&9&lNormal&7&l]&r");
                d.setEffectsOnAttack(true);
                d.setKeepInventory(false);
                difficultyList.put("Normal", d);
                difficulties.add("Normal");
                Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW+"[DynamicDifficulty] Added the Normal difficulty from the default config");
            }
        }
        return difficulties.get(0);
    }

    /**
     * Calculates the exact percentage between 2 difficulties
     *
     * @param uuid of the user
     * @param mode which is used to select the correct variable
     * @return Double of the exact or the difficulty based percentage
     */
    protected double calcPercentage(UUID uuid, String mode) {
        if(randomizer) { return getHashData(mode, calcDifficulty(uuid)); }
        int thisDiff = difficulties.indexOf(calcDifficulty(null));
        int affinity = worldAffinity;
        if(uuid != null) {
            thisDiff = difficulties.indexOf(calcDifficulty(uuid));
            affinity = playerList.get(uuid).getAffinity();
        }

        if (thisDiff + 1 != difficulties.size() && data.getConfig().getBoolean("difficulty-modifiers.exact-percentage", false)) {
            int differencePercentage = getHashData(mode, difficulties.get(thisDiff+1)) - getHashData(mode, difficulties.get(thisDiff));

            if(differencePercentage == 0)
                return getHashData(mode, difficulties.get(thisDiff));

            if(differencePercentage < 0)
                differencePercentage*=-1;

            int a = difficultyList.get(thisDiff+1).getAffinity();
            int b = difficultyList.get(thisDiff).getAffinity();
            double c = (100.0 / (a - b) * (affinity - b));
            double extraPercentage = (differencePercentage / 100.0) * c;

            return (getHashData(mode, difficulties.get(thisDiff)) + extraPercentage);
        }
        return getHashData(mode, difficulties.get(thisDiff));
    }

    /**
     * Returns data from the made HashMaps
     *
     * @param mode which is used to select the correct variable
     * @param diff which is the difficulty setting
     * @return INT from the selected variable and difficulty
     */
    private int getHashData(String mode, String diff) {
        if(difficultyList.containsKey(diff)) {
            Difficulty d = difficultyList.get(diff);
            if(mode.equalsIgnoreCase("damage-done-by-mobs")) { return d.getDamageByMobs(); }
            if(mode.equalsIgnoreCase("damage-done-on-mobs")) { return d.getDamageOnMobs(); }
            if(mode.equalsIgnoreCase("experience-multiplier")) { return d.getExperienceMultiplier(); }
            if(mode.equalsIgnoreCase("double-loot-chance")) { return d.getDoubleLoot(); }
            if(mode.equalsIgnoreCase("hunger-drain-chance")) { return d.getHungerDrain(); }
            if(mode.equalsIgnoreCase("chance-to-have-armor")) { return (int) Math.round(d.getChanceToHaveArmor()*100); }
            if(mode.equalsIgnoreCase("chance-to-enchant-a-piece")) { return (int) Math.round(d.getChanceToEnchant()*100); }
            if(mode.equalsIgnoreCase("weapon-chance")) { return (int) Math.round(d.getEnchantChance("weapon")*100); }
            if(mode.equalsIgnoreCase("helmet-chance")) { return (int) Math.round(d.getEnchantChance("helmet")*100); }
            if(mode.equalsIgnoreCase("chest-chance")) { return (int) Math.round(d.getEnchantChance("chest")*100); }
            if(mode.equalsIgnoreCase("leggings-chance")) { return (int) Math.round(d.getEnchantChance("leggings")*100); }
            if(mode.equalsIgnoreCase("boots-chance")) { return (int) Math.round(d.getEnchantChance("boots")*100); }
            if(mode.equalsIgnoreCase("armor-drop-chance")) { return (int) Math.round(d.getArmorDropChance()*100); }
        }
        return -1;
    }

    protected void addAmountOfAffinity(UUID uuid, int x) {
        if (difficultyType.equalsIgnoreCase("world")) { worldAffinity = calcAffinity(worldAffinity + x);
        } else { playerList.get(uuid).addAffinity(x); }
    }

    /* To increase/decrease players score every few minutes */
    public void onInterval() {
        if(onInterval != 0) {
            if(difficultyType.equalsIgnoreCase("world")) {
                worldAffinity = calcAffinity(worldAffinity + onInterval);
            } else {
                Bukkit.getOnlinePlayers().forEach(pl -> playerList.get(pl.getUniqueId()).addAffinity(onInterval));
            }
        }
    }

    public void openPlayersInventory(Player user, int page) {
        Inventory tmp = inventorySettings.get("player");
        if(Bukkit.getOnlinePlayers().size() < 45) {
            int i = 0;
            for(Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
                setPlayerHead(skull, player);
                setItemStackName(skull, player.getName());
                String c1 = ChatColor.BOLD+""+ChatColor.DARK_GREEN;
                String c2 = ChatColor.BOLD+""+ChatColor.GREEN;
                Minecrafter pl = playerList.get(uuid);
                setLore(skull, new ArrayList<>(Arrays.asList(c1+"Affinity: "+c2+pl.getAffinity(),c1+"Min Affinity: "+c2+pl.getMinAffinity(),c1+"Max Affinity: "+c2+pl.getMaxAffinity())));
                tmp.setItem(i++, skull);
            }
        } else {
            int curr = page * 45;
            Bukkit.getConsoleSender().sendMessage("page: "+page + " curr: "+curr);
            Player[] pl = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            ItemStack goldIngot = new ItemStack(Material.GOLD_INGOT);
            setLore(goldIngot, new ArrayList<>(Arrays.asList(String.valueOf(page > 0 ? page-1 : 0))));
            setItemStackName(goldIngot, ChatColor.AQUA+""+ChatColor.BOLD+"Previous page");
            ItemStack chestPlate = new ItemStack(Material.IRON_CHESTPLATE);
            setLore(chestPlate, new ArrayList<>(Arrays.asList(String.valueOf(page))));
            setItemStackName(chestPlate, ChatColor.AQUA+""+ChatColor.BOLD+"Current page");
            ItemStack ironIngot = new ItemStack(Material.IRON_INGOT);
            setLore(ironIngot, new ArrayList<>(Arrays.asList(String.valueOf(page+1))));
            setItemStackName(ironIngot, ChatColor.AQUA+""+ChatColor.BOLD+"Next page");
            tmp.setItem(3, goldIngot);
            tmp.setItem(4, chestPlate);
            tmp.setItem(5, ironIngot);
            for(int i=0;i<45;i++) {
                if(i + curr < pl.length) {
                    String name = pl[i + curr].getName();
                    UUID uuid = pl[i + curr].getUniqueId();
                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
                    setItemStackName(skull, name);
                    setPlayerHead(skull, Bukkit.getServer().getPlayer(name));
                    String c1 = ChatColor.BOLD+""+ChatColor.DARK_GREEN;
                    String c2 = ChatColor.BOLD+""+ChatColor.GREEN;
                    Minecrafter p = playerList.get(uuid);
                    setLore(skull, new ArrayList<>(Arrays.asList(c1+"Affinity: "+c2+p.getAffinity(),c1+"Min Affinity: "+c2+p.getMinAffinity(),c1+"Max Affinity: "+c2+p.getMaxAffinity())));
                    tmp.setItem(i+9, skull);
                } else {
                    break;
                }
            }
        }
        user.openInventory(tmp);
    }

    public void createPlayerInventory() {
        Inventory inv = Bukkit.createInventory(null, 54, "DynamicDifficulty - Players");
        inventorySettings.put("player", inv);
    }

    public void createIndividialPlayerInventory() {
        Inventory tmpinv = Bukkit.createInventory(null, 27, "DynamicDifficulty - Individual Player");
        List<String> settings = new ArrayList<>(Arrays.asList("Affinity","Min Affinity","Max Affinity"));
        List<String> changeSettings = new ArrayList<>(Arrays.asList("","-100","-10","-1", "", "+1", "+10", "+100", "Default"));
        List<String> woolColors = new ArrayList<>(Arrays.asList("LIME", "PINK", "MAGENTA", "PURPLE", "", "BLUE", "CYAN", "LIGHT_BLUE", "RED"));
        for(int i=0;i<27;i++) {
            if(i % 9 != 4) {
                ItemStack wool;
                if (i % 9 == 0) {
                    wool = new ItemStack(Material.LIME_WOOL, 1);
                    int tmp = 0;
                    if(i != 0) { tmp = i / 9; }
                    setItemStackName(wool, settings.get(tmp));
                } else {
                    if(woolColors.get((i % 9)) == "") { continue; }
                    wool = new ItemStack(Material.getMaterial(woolColors.get((i % 9)) + "_WOOL"), 1);
                    setItemStackName(wool, changeSettings.get(i % 9));
                }
                tmpinv.setItem(i, wool);
            }
        }
        inventorySettings.put("iplayer",tmpinv);
    }

    public void setItemStackName(ItemStack renamed, String customName) {
        ItemMeta renamedMeta = renamed.getItemMeta();
        renamedMeta.setDisplayName(customName);
        renamed.setItemMeta(renamedMeta);
    }

    public void setPlayerHead(ItemStack skull, Player name) {
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(name);
    }

    public void setLore(ItemStack item, List<String> loreSet) {
        ItemMeta meta = item.getItemMeta();
        meta.setLore(loreSet);
        item.setItemMeta(meta);
    }
}
