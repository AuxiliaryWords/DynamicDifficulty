package me.skinnyjeans.gmd.commands;

import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.skinnyjeans.gmd.PlayerAffinity;

public class PlayerCommands implements CommandExecutor {
	
	private PlayerAffinity affinity = null;
	
	public PlayerCommands(PlayerAffinity pa) {
		affinity = pa;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(label.equalsIgnoreCase("affinity")) {
			String msg = "";
			boolean console = false;
			
			if(!(sender instanceof Player))
				console = true;
			
			if(checkPermission(Bukkit.getPlayer(sender.getName()), args[0].toLowerCase()) || console) {
				try {
					switch(args[0].toLowerCase()){
						case "set":
							msg = setAffinity(Bukkit.getPlayer(args[1]), Integer.parseInt(args[2]));
							break;
						case "get":
							msg = getAffinity(Bukkit.getPlayer(args[1]));
							break;
						case "add":
							msg = addAffinity(Bukkit.getPlayer(args[1]), Integer.parseInt(args[2]));
							break;
						case "remove":
							msg = addAffinity(Bukkit.getPlayer(args[1]), Integer.parseInt(args[2])*-1);
							break;
						case "setmax":
							msg = setMaxAffinity(Bukkit.getPlayer(args[1]), Integer.parseInt(args[2]));
							break;
						case "removemax":
							msg = removeMaxAffinity(Bukkit.getPlayer(args[1]));
							break;
						case "author":
							msg = "The author of this plugin is: SkinnyJeans";
							break;
						default:
							msg = "Sorry, I don't recognize the command: " + args[0];
							break;
					}
				}
				catch(NumberFormatException e) {
					msg = "Third argument requires a number";
				}
				catch(ArrayIndexOutOfBoundsException e) {
					msg = "You forgot to include the user or a number";
				}
				catch(Exception e) {
					msg = "Something went wrong, please check the console for more info";
					System.out.println(e);
				}
			}
			else {
				msg = "You don't have permission to do that";
			}
			
			
			if (sender instanceof Player) {
				((Player) sender).getPlayer().sendMessage(msg);
			} else {
				Bukkit.getConsoleSender().sendMessage(msg);
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Sets the affinity for the player
	 * 
	 * @param UUID of the user
	 * @param perm is the permission to check
	 * @return Boolean whether this player has the permission or not
	 */
	private boolean checkPermission(Player user, String perm) {
		if(user.hasPermission("affinity."+perm) || user.isOp())
			return true;
		return false;
	}
	
	/**
	 * Sets the affinity for the player
	 * 
	 * @param UUID of the user
	 * @param Amount of affinity that will be set to this user
	 * @return String about how it was executed
	 */
	private String setMaxAffinity(Player user, int amount) {
		try {
			UUID uuid = user.getUniqueId();
			amount = affinity.calcAffinity(uuid, amount);
			affinity.setMaxAffinityUser(uuid, amount);
			return "Set the Max Affinity to "+amount+" for "+user.getName();
		}
		catch(NullPointerException e) {
			return "I'm sorry, this user doesn't exist";
		}
		catch(Exception e) {
			Bukkit.getLogger().log(Level.WARNING, "Exception caught: "+e);
			return "Something went wrong, please check the console for more info";
		}
	}
	
	/**
	 * Removes the max affinity for the player
	 * 
	 * @param UUID of the user
	 * @return String about how it was executed
	 */
	private String removeMaxAffinity(Player user) {
		try {
			affinity.setMaxAffinityUser(user.getUniqueId(), -1);
			return "Removed the Max Affinity of "+user.getName();
		}
		catch(NullPointerException e) {
			return "I'm sorry, this user doesn't exist";
		}
		catch(Exception e) {
			Bukkit.getLogger().log(Level.WARNING, "Exception caught: "+e);
			return "Something went wrong, please check the console for more info";
		}
	}
	
	/**
	 * Sets the affinity for the player
	 * 
	 * @param UUID of the user
	 * @param Amount of affinity that will be set to this user
	 * @return String about how it was executed
	 */
	private String setAffinity(Player user, int amount) {
		try {
			UUID uuid = user.getUniqueId();
			amount = affinity.calcAffinity(uuid, amount);
			affinity.setAffinityUser(uuid, amount);
			return user.getName()+" is on "+affinity.calcDifficulty(uuid)+" Difficulty with "+affinity.getAffinityUser(uuid)+" Affinity points";
		}
		catch(NullPointerException e) {
			return "I'm sorry, this user doesn't exist";
		}
		catch(Exception e) {
			Bukkit.getLogger().log(Level.WARNING, "Exception caught: "+e);
			return "Something went wrong, please check the console for more info";
		}
	}
	
	/**
	 * Gets the affinity of the player
	 * 
	 * @param UUID of the user
	 * @return Amount of affinity a user has or an error
	 */
	private String getAffinity(Player user) {
		try {
			return user.getName()+" is on "+affinity.calcDifficulty(user.getUniqueId())+" Difficulty with "+affinity.getAffinityUser(user.getUniqueId())+" Affinity points \nmax affinity: "+affinity.getMaxAffinityUser(user.getUniqueId());
		}
		catch(NullPointerException e) {
			return "I'm sorry, this user doesn't exist";
		}
		catch(Exception e) {
			Bukkit.getLogger().log(Level.WARNING, "Exception caught: "+e);
			return "Something went wrong, please check the console for more info";
		}
	}
	
	/**
	 * Adds a given amount of affinity to a certain player
	 * 
	 * @param UUID of the user
	 * @param Amount of affinity that will be added to this user
	 * @return String about how it was executed
	 */
	private String addAffinity(Player user, int amount) {
		try {
			UUID uuid = user.getUniqueId();
			int x = affinity.calcAffinity(uuid, affinity.getAffinityUser(uuid) + amount);
			affinity.setAffinityUser(uuid, x);
			return user.getName()+" is on "+affinity.calcDifficulty(user.getUniqueId())+" Difficulty with "+x+" Affinity points";
		}
		catch(NullPointerException e) {
			return "I'm sorry, this user doesn't exist";
		}
		catch(Exception e) {
			Bukkit.getLogger().log(Level.WARNING, "Exception caught: "+e);
			return "Something went wrong, please check the console for more info";
		}
	}
}
