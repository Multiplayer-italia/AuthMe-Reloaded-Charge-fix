/*
 * Copyright 2011 Sebastian Köhler <sebkoehler@whoami.org.uk>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.whoami.authme.commands;

import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import uk.org.whoami.authme.cache.backup.FileCache;
import uk.org.whoami.authme.ConsoleLogger;
import uk.org.whoami.authme.Utils;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.security.PasswordSecurity;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.settings.Settings;

public class LoginCommand implements CommandExecutor {

    private Messages m = Messages.getInstance();
    private Settings settings = Settings.getInstance();
    private Utils utils = Utils.getInstance();
    private DataSource database;
    private FileCache playerCache = new FileCache();
    
    public LoginCommand(DataSource database) {
        this.database = database;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmnd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }

        if (!sender.hasPermission("authme." + label.toLowerCase())) {
            sender.sendMessage(m._("no_perm"));
            return true;
        }

        Player player = (Player) sender;
        String name = player.getName().toLowerCase();
        String ip = player.getAddress().getAddress().getHostAddress();

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            player.sendMessage(m._("logged_in"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(m._("usage_log"));
            return true;
        }

        if (!database.isAuthAvailable(player.getName().toLowerCase())) {
            player.sendMessage(m._("user_unknown"));
            return true;
        }
        //
        PlayerAuth hash = database.getAuth(name);
        
        try {
            if (PasswordSecurity.comparePasswordWithHash(args[0], hash.getHash())) {
                // Group id From Vbullettin Board for unactivated User
                if(hash.getGroupId() == settings.getNonActivatedGroup()) {
                        player.sendMessage(m._("vb_nonActiv"));
                        return true;                  
                }
                PlayerAuth auth = new PlayerAuth(name, hash.getHash(), ip, new Date().getTime());
                database.updateSession(auth);
                PlayerCache.getInstance().addPlayer(auth);
                LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
                if (limbo != null) {
                	CraftItemStack[] inv = limbo.getInventory();
                	CraftItemStack[] arm = limbo.getArmour();
                	
                	//set items, no support for custom enchants here
                	 player.getInventory().setContents(inv);
                     player.getInventory().setArmorContents(arm);
                     
                     //set charge if there is 
                     for(int i = 0; i < arm.length; i++){
                     	int charge = 0;
     					try{
     						//check if there was charge in CraftItemStack[]
     						charge = arm[i].getHandle().getTag().getInt("charge");
     						}
     						catch(Exception e){}
     					if(charge > 0) {;
     					//set NBTTagCompound (where charge is stored) @ arm
     					CraftItemStack ICItem = (CraftItemStack) player.getInventory().getArmorContents()[i];
     					ICItem.getHandle().setTag(arm[i].getHandle().getTag());
     					}
                     }
                     //same for inventory
                    for(int i = 0; i < inv.length; i++){
                    	int charge = 0;
    					try{
    						charge = inv[i].getHandle().getTag().getInt("charge");
    						}
    						catch(Exception e){}
    					if(charge > 0) {
    					CraftItemStack ICItem = (CraftItemStack) player.getInventory().getItem(i);
    					ICItem.getHandle().setTag(inv[i].getHandle().getTag());
    					}
                    }
                    player.setGameMode(GameMode.getByValue(limbo.getGameMode()));
                    player.setOp(limbo.getOperator());
  /*                  if (limbo.getOperator()) {
                         System.out.println("player is an operator after login success");
                    }*/
                    utils.addNormal(player, limbo.getGroup());
                   //System.out.println("il gruppo in logincommand "+limbo.getGroup());
                   //
                   // TODO: completly rewrite this part, too much if, else ...
                   // check quit location, check correct spawn, 
                   //
                    if (settings.isTeleportToSpawnEnabled() && !settings.isForceSpawnLocOnJoinEnabled()) {                  
                             if(settings.isForceExactSpawnEnabled() ) {
                                 // This is initial work around for prevent ppl to quit on bukkit bug
                                 // take last quit location from database and subtract y from safe spawn             
                                 // if the error range is smaller then 1, player can come back in his quit location
                                 // otherwise he try to spawn in a unsafe location!
                                
                                 if(settings.isSaveQuitLocationEnabled() && database.getAuth(name).getQuitLocY() != 0) {
                                     if((((int)limbo.getLoc().getY()-database.getAuth(name).getQuitLocY()) <= 1)  ) {
                                     Location quitLoc = new Location(player.getWorld(),(double)database.getAuth(name).getQuitLocX(),(double)database.getAuth(name).getQuitLocY(),(double)database.getAuth(name).getQuitLocZ());
                                     player.teleport(quitLoc);
                                     //System.out.println("quit location from db:"+quitLoc);
                                     } else {
                                    player.sendMessage(m._("unsafe_spawn"));
                                    player.teleport(player.getWorld().getSpawnLocation());
                                    }
                                 } else {
                                 player.teleport(limbo.getLoc());
                                 //System.out.println("quit location from bukkit:"+limbo.getLoc());
                                 }
                             
                             } else {
                                 if(settings.isSaveQuitLocationEnabled() && database.getAuth(name).getQuitLocY() != 0 ) {
                                     Location quitLoc = new Location(player.getWorld(),(double)database.getAuth(name).getQuitLocX(),(double)database.getAuth(name).getQuitLocY(),(double)database.getAuth(name).getQuitLocZ());
                                     player.teleport(quitLoc);
                                     //System.out.println("quit location from db:"+quitLoc);
                                 } else {
                                  //System.out.println("quit location from bukkit:"+limbo.getLoc());
                                 player.teleport(limbo.getLoc());
                                 }
                             }  
                    } else {
                        if(settings.isForceSpawnLocOnJoinEnabled()) {
                            player.teleport(player.getWorld().getSpawnLocation());  
                        } else {
                        if ( settings.isSaveQuitLocationEnabled() && database.getAuth(name).getQuitLocY() != 0) {
                              if( settings.isForceExactSpawnEnabled() ) {
                                 if((((int)limbo.getLoc().getY()-database.getAuth(name).getQuitLocY()) <= 1)) {  
                                     Location quitLoc = new Location(player.getWorld(),(double)database.getAuth(name).getQuitLocX(),(double)database.getAuth(name).getQuitLocY(),(double)database.getAuth(name).getQuitLocZ());
                                     player.teleport(quitLoc); }
                                 else {
                                    player.sendMessage(m._("unsafe_spawn"));
                                    player.teleport(player.getWorld().getSpawnLocation());} 
                              }
                          Location quitLoc = new Location(player.getWorld(),(double)database.getAuth(name).getQuitLocX(),(double)database.getAuth(name).getQuitLocY(),(double)database.getAuth(name).getQuitLocZ());
                          player.teleport(quitLoc);  
                          //System.out.println("quit location from db:"+quitLoc);
                        } else {
                                player.teleport(limbo.getLoc());
                                //System.out.println("quit location from bukkit:"+limbo.getLoc()); 
                                }  
                        }
                    } 
                sender.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
                LimboCache.getInstance().deleteLimboPlayer(name);
                if(playerCache.doesCacheExist(name)) {
                        playerCache.removeCache(name);
                    }   
                }
                player.sendMessage(m._("login"));
                ConsoleLogger.info(player.getDisplayName() + " logged in!");
               
                
            } else {
                ConsoleLogger.info(player.getDisplayName() + " used the wrong password");
                if (settings.isKickOnWrongPasswordEnabled()) {
                    player.kickPlayer(m._("wrong_pwd"));
                } else {
                    player.sendMessage(m._("wrong_pwd"));
                }
            }
        } catch (NoSuchAlgorithmException ex) {
            ConsoleLogger.showError(ex.getMessage());
            sender.sendMessage(m._("error"));
        }
        return true;
    }
}
