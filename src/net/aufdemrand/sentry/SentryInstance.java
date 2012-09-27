package net.aufdemrand.sentry;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Owner;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.trait.waypoint.Waypoints;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPotion;
import net.minecraft.server.Packet;
import net.minecraft.server.Packet18ArmAnimation;

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public class SentryInstance {

	public int getHealth(){
		if (myNPC == null) return 0;
		if (myNPC.getBukkitEntity() == null) return 0;
		return ((CraftLivingEntity) myNPC.getBukkitEntity()).getHandle().getHealth(); 
	}

	public void setHealth(int health){
		if (myNPC == null) return;
		if (myNPC.getBukkitEntity() == null) return;
		((CraftLivingEntity) myNPC.getBukkitEntity()).getHandle().setHealth(health); 	
	}

	private class SentryLogicRunnable implements Runnable {

		@Override
		public void run() {
			// plugin.getServer().broadcastMessage("tick " + (myNPC ==null) +			
			if (((CitizensNPC)myNPC).getHandle() == null ) sentryStatus = Status.isDEAD; // incase it dies in a way im not handling.....

			if (sentryStatus != Status.isDEAD &&  HealRate > 0) {

				if(System.currentTimeMillis() > oktoheal ){
					if (getHealth() < sentryHealth && sentryStatus !=  Status.isDEAD && sentryStatus != Status.isDYING) {
						int heal = 1;
						if (HealRate <0.5) heal = (int) (0.5 / HealRate);

						if (getHealth() + heal <= sentryHealth){

							setHealth(	getHealth() + heal);
						}
						else{
							myNPC.getBukkitEntity().setHealth(sentryHealth);
						}

						if (healanim!=null)net.citizensnpcs.util.Util.sendPacketNearby(myNPC.getBukkitEntity().getLocation(),healanim , 64);

						if (getHealth() >= sentryHealth) _myDamamgers.clear(); //healed to full, forget attackers

					}
					oktoheal = (long) (System.currentTimeMillis() + HealRate * 1000);
				}

			}

			if (sentryStatus == Status.isDEAD && System.currentTimeMillis() > isRespawnable && RespawnDelaySeconds > 0) {
				// Respawn
				// plugin.getServer().broadcastMessage("Spawning...");
				if (guardEntity == null) {
					myNPC.spawn(Spawn);
				} else {
					myNPC.spawn(guardEntity.getLocation().add(2, 0, 2));
				}
				return;
			}
			else if ((sentryStatus == Status.isHOSTILE || sentryStatus == Status.isRETALIATING) && myNPC.isSpawned()) {

				if (sentryStatus == Status.isHOSTILE && System.currentTimeMillis() > oktoreasses) {
					LivingEntity target = findTarget(sentryRange);
					setTarget(target, false);
					oktoreasses = (long) (System.currentTimeMillis() + 3000);
				}

				if (projectileTarget != null && !projectileTarget.isDead() && projectileTarget.getWorld() == myNPC.getBukkitEntity().getLocation().getWorld() ) {
					if (_projTargetLostLoc == null)
						_projTargetLostLoc = projectileTarget.getLocation();

					if (!myNPC.getNavigator().isNavigating())	faceEntity(myNPC.getBukkitEntity(), projectileTarget);

					if (System.currentTimeMillis() > oktoFire) {
						// Fire!
						oktoFire = (long) (System.currentTimeMillis() + AttackRateSeconds * 1000.0);
						Fire(projectileTarget);
					}
					if (projectileTarget != null)
						_projTargetLostLoc = projectileTarget.getLocation();

					return; // keep at it
				}

				else if (meleeTarget != null && !meleeTarget.isDead()) {

					// Did it get away?
					if (meleeTarget.getWorld() != myNPC.getBukkitEntity().getLocation().getWorld() || meleeTarget.getLocation().distance(myNPC.getBukkitEntity().getLocation()) > sentryRange) {
						// it got away...
						setTarget(null, false);
					}

				}

				else {
					// target died or null
					setTarget(null, false);
				}

			}

			else if (sentryStatus == Status.isLOOKING && myNPC.isSpawned()) {

				if (guardEntity instanceof Player){
					if (((CraftPlayer)guardEntity).isOnline() == false){
						guardEntity = null;
						plugin.debug(myNPC.getName() + "offline!");
					}
				}

				if (guardTarget != null && guardEntity == null) {
					// daddy? where are u?
					setGuardTarget(guardTarget);
					plugin.debug(myNPC.getName() + "setgt2!");
				}	

				if (guardEntity !=null){
					if (guardEntity.getLocation().getWorld() != myNPC.getBukkitEntity().getLocation().getWorld()){
						myNPC.despawn();
						myNPC.spawn((guardEntity.getLocation().add(1, 0, 1)));
					}
					else{

						double dist = myNPC.getBukkitEntity().getLocation().distanceSquared(guardEntity.getLocation());
						plugin.debug(myNPC.getName() + dist + myNPC.getNavigator().isNavigating() + " " +myNPC.getNavigator().getEntityTarget() + " " );
						if(dist > 16 && !myNPC.getNavigator().isNavigating()) {
							myNPC.getNavigator().setTarget(guardEntity, false);
							myNPC.getNavigator().getLocalParameters().stationaryTicks(3*20);	
						}
						else if (dist < 16 && myNPC.getNavigator().isNavigating()) {
							myNPC.getNavigator().cancelNavigation();
						}
					}
				}

				LivingEntity target = findTarget(sentryRange);
				if (target !=null)	{
					oktoreasses = (long) (System.currentTimeMillis() + 3000);
					setTarget(target, false);
				}

			}

		}

	}

	public enum Status {
		isDEAD, isHOSTILE,isRETALIATING, isLOOKING, isDYING, isSTUCK
	}

	public enum hittype {
		normal, miss, block, injure, main, disembowel, glance,
	}

	/* plugin Constructer */
	Sentry plugin;

	/* Technicals */
	private Integer taskID = null;
	Long isRespawnable = System.currentTimeMillis();
	private long oktoFire = System.currentTimeMillis();
	private long oktoheal = System.currentTimeMillis();
	private long oktoreasses= System.currentTimeMillis();;
	private int _logicTick = 10;
	private List<Player> _myDamamgers = new ArrayList<Player>();

	private GiveUpStuckAction giveup = new GiveUpStuckAction(this);
	//private BodyguardTeleportStuckAction bgteleport = new BodyguardTeleportStuckAction(this);

	public LivingEntity projectileTarget;
	public LivingEntity meleeTarget;
	/* Internals */
	Status sentryStatus = Status.isDYING;

	public NPC myNPC = null;
	/* Setables */
	public SentryTrait myTrait;
	public List<String> validTargets = new ArrayList<String>();
	public List<String> ignoreTargets = new ArrayList<String>();
	public Integer sentryRange = 10;
	public Integer sentryHealth = 20;
	public float sentrySpeed =  (float) 1.0;
	public Double sentryWeight = 1.0;
	public String guardTarget = null;
	public LivingEntity guardEntity = null;
	public Boolean FriendlyFire = false;
	public Boolean LuckyHits = true;
	public Boolean Invincible = false;
	public Boolean Retaliate = true;
	public Boolean DropInventory = false;
	public Integer RespawnDelaySeconds = 10;
	public Integer Armor = 0;
	public Integer Strength = 1;
	public Integer NightVision = 16;
	public Double AttackRateSeconds = 2.0;
	public Double HealRate = 0.0;
	public Integer WarningRange = 0;
	public String WarningMessage = "�c<NPC> says: Halt! Come no further!";
	public String GreetingMessage = "�a<NPC> says: Welcome, <PLAYER>!";

	private Map<Player, Long> Warnings = new  HashMap<Player, Long>();

	public Location Spawn = null;

	private Location _projTargetLostLoc;

	private Class<? extends Projectile> myProjectile;

	private String getWarningMessage(Player player){
		return WarningMessage.replace("<NPC>", myNPC.getName()).replace("<PLAYER>", player.getName());
	}
	private String getGreetingMEssage(Player player){
		return GreetingMessage.replace("<NPC>", myNPC.getName()).replace("<PLAYER>", player.getName());
	}

	public boolean isPyromancer(){
		return (myProjectile == Fireball.class || myProjectile == SmallFireball.class) ;
	}

	public boolean isPyromancer1(){
		return (!inciendary && myProjectile == SmallFireball.class) ;
	}

	public boolean isPyromancer2(){
		return (inciendary && myProjectile == SmallFireball.class) ;
	}

	public boolean isPyromancer3(){
		return (myProjectile == Fireball.class) ;
	}

	public boolean isStormcaller(){
		return (lightning) ;
	}

	public boolean isWitchDoctor(){
		return (myProjectile == org.bukkit.entity.ThrownPotion.class) ;
	}

	public int epcount = 0;
	public boolean isWarlock1(){
		return (myProjectile == org.bukkit.entity.EnderPearl.class) ;
	}

	public SentryInstance(Sentry plugin) {
		this.plugin = plugin;
		isRespawnable = System.currentTimeMillis();
	}

	// private Random r = new Random();

	public void cancelRunnable() {
		if (taskID != null) {
			plugin.getServer().getScheduler().cancelTask(taskID);
		}
	}

	public boolean containsTarget(String theTarget) {
		for (String t: validTargets){
			if (t.equalsIgnoreCase(theTarget)) 	return true;
		}
		return false;
	}

	public boolean containsIgnore(String theTarget) {
		for (String t: ignoreTargets){
			if (t.equalsIgnoreCase(theTarget)) 	return true;
		}
		return false;
	}

	public void deactivate() {
		plugin.getServer().getScheduler().cancelTask(taskID);
	}

	private void faceEntity(Entity from, Entity at) {
		if (from.getWorld() != at.getWorld())
			return;
		Location loc = from.getLocation();

		double xDiff = at.getLocation().getX() - loc.getX();
		double yDiff = at.getLocation().getY() - loc.getY();
		double zDiff = at.getLocation().getZ() - loc.getZ();

		double distanceXZ = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
		double distanceY = Math.sqrt(distanceXZ * distanceXZ + yDiff * yDiff);

		double yaw = (Math.acos(xDiff / distanceXZ) * 180 / Math.PI);
		double pitch = (Math.acos(yDiff / distanceY) * 180 / Math.PI) - 90;
		if (zDiff < 0.0) {
			yaw = yaw + (Math.abs(180 - yaw) * 2);
		}

		EntityLiving handle = ((CraftLivingEntity) from).getHandle();
		handle.yaw = (float) yaw - 90;
		handle.pitch = (float) pitch;
		handle.as = handle.yaw;
	}

	private void faceForward() {
		EntityLiving handle = ((CraftLivingEntity) this.myNPC.getBukkitEntity()).getHandle();
		handle.as = handle.yaw;
		handle.pitch = 0;
	}

	public LivingEntity findTarget(Integer Range) {
		Range+=WarningRange; 
		List<Entity> EntitiesWithinRange = myNPC.getBukkitEntity().getNearbyEntities(Range, Range, Range);
		LivingEntity theTarget = null;
		Double distanceToBeat = 99999.0;

		// plugin.getServer().broadcastMessage("Targets scanned : " +
		// EntitiesWithinRange.toString());

		for (Entity aTarget : EntitiesWithinRange) {
			if (!(aTarget instanceof LivingEntity)) continue;

			// find closest target
			if (_checkTarget((LivingEntity) aTarget)) {

				// can i see it?
				// too dark?
				double ll = (double) aTarget.getLocation().getBlock().getLightLevel();
				// sneaking cut light in half
				if (aTarget instanceof Player)
					if (((Player) aTarget).isSneaking())
						ll /= 2;

				// too dark?
				if (ll >= (16 - this.NightVision)) {


					double dist = aTarget.getLocation().distance(myNPC.getBukkitEntity().getLocation());

					boolean LOS = (((CraftLivingEntity) myNPC.getBukkitEntity()).getHandle()).l(((CraftLivingEntity) aTarget).getHandle());
					if (LOS) {					


						if (WarningRange >0 && sentryStatus == Status.isLOOKING && aTarget instanceof Player &&  dist > (Range - WarningRange) && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget) & !(WarningMessage.isEmpty())){

							if (Warnings.containsKey(aTarget) && System.currentTimeMillis() < Warnings.get(aTarget) + 60*1000){
								//already warned u in last 30 seconds.
							}
							else{
								((Player)aTarget).sendMessage(getWarningMessage((Player) aTarget)); 
								if(!myNPC.getNavigator().isNavigating())	faceEntity(myNPC.getBukkitEntity(), aTarget);
								Warnings.put((Player) aTarget,System.currentTimeMillis());
							}

						}
						else if	(dist < distanceToBeat) {				
							// now find closes mob
							distanceToBeat = dist;
							theTarget = (LivingEntity) aTarget;
						}
					}


				}

			}
			else {
				//not a target

				if (WarningRange >0 && sentryStatus == Status.isLOOKING && aTarget instanceof Player &&  !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget) && !(GreetingMessage.isEmpty())){
					boolean LOS = (((CraftLivingEntity) myNPC.getBukkitEntity()).getHandle()).l(((CraftLivingEntity) aTarget).getHandle());
					if (LOS) {			
						if (Warnings.containsKey(aTarget) && System.currentTimeMillis() < Warnings.get(aTarget) + 60*1000){
							//already greeted u in last 30 seconds.
						}
						else{
							((Player)aTarget).sendMessage(getGreetingMEssage((Player) aTarget)); 
							faceEntity(myNPC.getBukkitEntity(), aTarget);
							Warnings.put((Player) aTarget,System.currentTimeMillis());
						}
					}
				}

			}

		}


		if (theTarget != null) {
			// plugin.getServer().broadcastMessage("Targeting: " +
			// theTarget.toString());
			return theTarget;
		}

		return null;
	}

	private boolean _checkTarget (LivingEntity aTarget){
		//cheak ignores

		if (this.containsIgnore("ENTITY:ALL")) 	return false;

		if (aTarget instanceof Player && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

			if (this.containsIgnore("ENTITY:PLAYER")) {
				return false;
			}

			else if ( this.containsIgnore("ENTITY:PLAYERS")) {
				return false;
			}

			else{
				String name = ((Player) aTarget).getName();

				if ( this.containsIgnore("PLAYER:" + name)) 	return false;

				if ( this.containsIgnore("ENTITY:OWNER")  && name.equalsIgnoreCase(myNPC.getTrait(Owner.class).getOwner()))		return false;

				else if( plugin.perms!=null && plugin.perms.isEnabled()) {

					String[] groups1 = plugin.perms.getPlayerGroups(aTarget.getWorld(),name); // world perms
					String[] groups2 = plugin.perms.getPlayerGroups((World)null,name); //global perms
					//		String[] groups3 = plugin.perms.getPlayerGroups(aTarget.getWorld().getName(),name); // world perms
					//	String[] groups4 = plugin.perms.getPlayerGroups((Player)aTarget); // world perms


					if (groups1 !=null){
						for (int i = 0; i < groups1.length; i++) {
							//	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found world1 group " + groups1[i] + " on " + name);
							if (this.containsIgnore("GROUP:" + groups1[i]))	return false;
						}	
					}

					if ( groups2 !=null){
						for (int i = 0; i < groups2.length; i++) {
							//	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found global group " + groups2[i] + " on " + name);
							if (this.containsIgnore("GROUP:" + groups2[i]))		return false;
						}	
					}
				}

				if( plugin.TownyActive ) {
					String[] info = plugin.getResidentTownyInfo((Player)aTarget);

					if (info[1]!=null) {
						if (this.containsIgnore("TOWN:" + info[1]))	return false;
					}

					if (info[0]!=null) {
						if (this.containsIgnore("NATION:" + info[0]))	return false;
					}
				}

				if( plugin.FactionsActive ) {
					String faction = plugin.getFactionsTag((Player)aTarget);
					//	plugin.getLogger().info(faction);
					if (faction !=null) {
						if (this.containsIgnore("FACTION:" + faction))	return false;
					}
				}
				if( plugin.WarActive ) {
					String team = plugin.getWarTeam((Player)aTarget);
					//	plugin.getLogger().info(faction);
					if (team !=null) {
						if (this.containsIgnore("TEAM:" + team))	return false;
					}
				}
			}
		}

		else if(net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)){

			if (this.containsIgnore("ENTITY:NPC")) {
				return false;
			}

			else if ( this.containsIgnore("ENTITY:NPCS")) {
				return false;
			}

			NPC npc =  net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(aTarget);

			if (npc !=null) {

				String name =npc.getName();

				if ( this.containsIgnore("NPC:" + name)) 	return false;

				else if( plugin.perms!=null && plugin.perms.isEnabled()) {

					String[] groups1 = plugin.perms.getPlayerGroups(aTarget.getWorld(),name); // world perms
					String[] groups2 = plugin.perms.getPlayerGroups((World)null,name); //global perms

					if (groups1 !=null){
						for (int i = 0; i < groups1.length; i++) {
							//	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found world1 group " + groups1[i] + " on " + name);
							if (this.containsIgnore("GROUP:" + groups1[i]))	return false;
						}	
					}

					if ( groups2 !=null){
						for (int i = 0; i < groups2.length; i++) {
							//	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found global group " + groups2[i] + " on " + name);
							if (this.containsIgnore("GROUP:" + groups2[i]))		return false;
						}	
					}
				}
			}
		}


		else if (aTarget instanceof Monster) {
			if (this.containsIgnore("ENTITY:MONSTER")) 			return false;
			else if ( this.containsIgnore("ENTITY:MONSTERS")) 			return false;
			else if ( this.containsIgnore("ENTITY:" + ((LivingEntity) aTarget).getType()))		return false;
		}

		else if (aTarget instanceof LivingEntity) {
			if (this.containsIgnore("ENTITY:" + ((LivingEntity) aTarget).getType()))	return false;
		}

		//not ignored, ok!


		if (this.containsTarget("ENTITY:ALL")) 	return true;

		//Check if target
		if (aTarget instanceof Player && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {


			if (this.containsTarget("ENTITY:PLAYER")) {
				return true;
			}
			else if ( this.containsTarget("ENTITY:PLAYERS")) {
				return true;
			}
			else{
				String name = ((Player) aTarget).getName();

				if ( this.containsTarget("PLAYER:" + name)) 	return true;

				if ( this.containsTarget("ENTITY:OWNER")  && name.equalsIgnoreCase(myNPC.getTrait(Owner.class).getOwner()))	 return true;

				else if( plugin.perms!=null && plugin.perms.isEnabled()) {

					String[] groups1 = plugin.perms.getPlayerGroups(aTarget.getWorld(),name); // world perms
					String[] groups2 = plugin.perms.getPlayerGroups((World)null,name); //global perms

					if (groups1 !=null){
						for (int i = 0; i < groups1.length; i++) {
							//			plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found world1 group " + groups1[i] + " on " + name);
							if (this.containsTarget("GROUP:" + groups1[i]))	return true;
						}	
					}

					if ( groups2 !=null){
						for (int i = 0; i < groups2.length; i++) {
							//	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found global group " + groups2[i] + " on " + name);
							if (this.containsTarget("GROUP:" + groups2[i]))	return true;
						}	
					}
				}

				if(plugin.TownyActive ) {
					String[] info = plugin.getResidentTownyInfo((Player)aTarget);

					if (info[1]!=null) {
						if (this.containsTarget("TOWN:" + info[1]))return true;
					}

					if (info[0]!=null) {
						if (this.containsTarget("NATION:" + info[0]))return true;
					}
				}

				if( plugin.FactionsActive ) {
					String faction = plugin.getFactionsTag((Player)aTarget);
					if (faction !=null) {
						if (this.containsTarget("FACTION:" + faction))return true;
					}
				}
				if( plugin.WarActive ) {
					String team = plugin.getWarTeam((Player)aTarget);
					//	plugin.getLogger().info(faction);
					if (team !=null) {
						if (this.containsTarget("TEAM:" + team))	return true;
					}
				}
			}
		}

		else if( net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)){

			if (this.containsTarget("ENTITY:NPC") || this.containsTarget("ENTITY:NPCS")) {
				return true;
			}

			NPC npc =  net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(aTarget);

			String name =npc.getName();

			if ( this.containsTarget("NPC:" + name)) return true;
			else if( plugin.perms!=null && plugin.perms.isEnabled()) {

				String[] groups1 = plugin.perms.getPlayerGroups(aTarget.getWorld(),name); // world perms
				String[] groups2 = plugin.perms.getPlayerGroups((World)null,name); //global perms
				//		String[] groups3 = plugin.perms.getPlayerGroups(aTarget.getWorld().getName(),name); // world perms
				//	String[] groups4 = plugin.perms.getPlayerGroups((Player)aTarget); // world perms

				if (groups1 !=null){
					for (int i = 0; i < groups1.length; i++) {
						//	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found world1 group " + groups1[i] + " on " + name);
						if (this.containsTarget("GROUP:" + groups1[i]))	return true;
					}	
				}

				if ( groups2 !=null){
					for (int i = 0; i < groups2.length; i++) {
						//	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found global group " + groups2[i] + " on " + name);
						if (this.containsTarget("GROUP:" + groups2[i]))		return true;
					}	
				}
			}	
		}
		else if (aTarget instanceof Monster) {
			if (this.containsTarget("ENTITY:MONSTER")) 		return true;
			else if (this.containsTarget("ENTITY:MONSTERS")) 		return true;
			else if ( this.containsTarget("ENTITY:" + ((LivingEntity) aTarget).getType()))	return true;
		}

		else if (aTarget instanceof LivingEntity) {
			if (this.containsTarget("ENTITY:" + ((LivingEntity) aTarget).getType())) return true;
		}


		return false;

	}


	Packet shootanim = null;
	Packet healanim = null;
	Random r = new Random();
	boolean lightning = false;
	boolean mclightning = false;
	short potiontype = 0;

	public void Fire(LivingEntity theEntity) {

		double v = 34;
		double g = 20;

		Effect effect = null;

		boolean ballistics = true;

		if (myProjectile == Arrow.class) {
			effect = Effect.BOW_FIRE;
		} else if (myProjectile == SmallFireball.class || myProjectile == Fireball.class) {
			effect = Effect.BLAZE_SHOOT;
			ballistics =false;
		}
		else if (myProjectile == org.bukkit.entity.ThrownPotion.class){
			v = 21;
			g = 20;
		}
		else {
			v = 17.75;
			g = 13.5;
		}

		if(lightning) {
			ballistics = false;
			effect =null;
		}

		// calc shooting spot.
		Location loc = Util.getFireSource(myNPC.getBukkitEntity(), theEntity);

		Location targetsHeart = theEntity.getLocation();
		targetsHeart = targetsHeart.add(0, .33, 0);

		Vector test = targetsHeart.clone().subtract(loc).toVector();

		Double elev = test.getY();

		Double testAngle = Util.launchAngle(loc, targetsHeart, v, elev, g);

		if (testAngle == null) {
			// testAngle = Math.atan( ( 2*g*elev + Math.pow(v, 2)) / (2*g*elev +
			// 2*Math.pow(v,2))); //cant hit it where it is, try aiming as far
			// as you can.
			setTarget(null, false);
			// plugin.getServer().broadcastMessage("Can't hit test angle");
			return;
		}

		// plugin.getServer().broadcastMessage("ta " + testAngle.toString());

		Double hangtime = Util.hangtime(testAngle, v, elev, g);
		// plugin.getServer().broadcastMessage("ht " + hangtime.toString());

		Vector targetVelocity = theEntity.getLocation().subtract(_projTargetLostLoc).toVector();
		// plugin.getServer().broadcastMessage("tv" + targetVelocity);

		targetVelocity.multiply(20 / _logicTick);

		Location to = Util.leadLocation(targetsHeart, targetVelocity, hangtime);
		// plugin.getServer().broadcastMessage("to " + to);
		// Calc range

		Vector victor = to.clone().subtract(loc).toVector();

		Double dist = Math.sqrt(Math.pow(victor.getX(), 2) + Math.pow(victor.getZ(), 2));
		elev = victor.getY();
		if (dist == 0)
			return;
		boolean LOS = (((CraftLivingEntity) myNPC.getBukkitEntity()).getHandle()).l(((CraftLivingEntity) theEntity).getHandle());
		if (!LOS) {
			// target cant be seen..
			setTarget(null, false);
			// plugin.getServer().broadcastMessage("No LoS");
			return;
		}

		// plugin.getServer().broadcastMessage("delta " + victor);

		// plugin.getServer().broadcastMessage("ld " +
		// to.clone().subtract(theEntity.getEyeLocation()));

		if(ballistics){
			Double launchAngle = Util.launchAngle(loc, to, v, elev, g);
			if (launchAngle == null) {
				// target cant be hit
				setTarget(null, false);
				// plugin.getServer().broadcastMessage("Can't hit lead");
				return;

			}

			//	plugin.getServer().broadcastMessage(anim.a + " " + anim.b + " " + anim.a() + " " +anim.);
			// Apply angle
			victor.setY(Math.tan(launchAngle) * dist);
			Vector noise = Vector.getRandom();
			// normalize vector
			victor = Util.normalizeVector(victor);

			noise = noise.multiply(1 / 10.0);

			// victor = victor.add(noise);

			if (myProjectile == Arrow.class || myProjectile == org.bukkit.entity.ThrownPotion.class){
				v = v + (1.188 * Math.pow(hangtime, 2));		
			}
			else {
				v = v + (.5 * Math.pow(hangtime, 2));	
			}



			v = v+ (r.nextDouble() - .8)/2;

			// apply power
			victor = victor.multiply(v / 20.0);

			// Shoot!
			// Projectile theArrow
			// =myNPC.getBukkitEntity().launchProjectile(myProjectile);

		}
		else{
			if (dist > sentryRange) {
				// target cant be hit
				setTarget(null, false);
				// plugin.getServer().broadcastMessage("Can't hit lead");
				return;

			}
		}


		if(lightning){
			if (mclightning){
				to.getWorld().strikeLightning(to);
			}
			else{
				to.getWorld().strikeLightningEffect(to);
				theEntity.damage(getStrength(), myNPC.getBukkitEntity());
			}	
		}
		else
		{

			Projectile theArrow = null;
			if(myProjectile == org.bukkit.entity.ThrownPotion.class){
				net.minecraft.server.World nmsWorld = ((CraftWorld)myNPC.getBukkitEntity().getWorld()).getHandle();

				EntityPotion ent = new EntityPotion(nmsWorld, loc.getX(), loc.getY(), loc.getZ(), potiontype);
				nmsWorld.addEntity(ent);
				theArrow = (Projectile) ent.getBukkitEntity();
			}

			else if(myProjectile == org.bukkit.entity.EnderPearl.class){
				theArrow = myNPC.getBukkitEntity().launchProjectile(myProjectile);	
			}

			else{
				theArrow = myNPC.getBukkitEntity().getWorld().spawn(loc, myProjectile);	
			}


			if (myProjectile == Fireball.class) {
				victor = victor.multiply(1/1000000000);
			}
			else if (myProjectile == SmallFireball.class) {
				victor = victor.multiply(1/1000000000);
				((SmallFireball)theArrow).setIsIncendiary(inciendary);
				if(!inciendary)	{
					((SmallFireball)theArrow).setFireTicks(0);
					((SmallFireball)theArrow).setYield(0);
				}
			}
			else if (myProjectile == org.bukkit.entity.EnderPearl.class){
				epcount++;
				if (epcount > Integer.MAX_VALUE-1) epcount=0;
				plugin.debug(epcount + "");
			}

			plugin.arrows.add(theArrow);
			theArrow.setShooter(myNPC.getBukkitEntity());
			theArrow.setVelocity(victor);
		}


		// OK we're shooting
		// go twang
		if (effect != null)
			myNPC.getBukkitEntity().getWorld().playEffect(myNPC.getBukkitEntity().getLocation(), effect, null);
		if (shootanim!=null)net.citizensnpcs.util.Util.sendPacketNearby(myNPC.getBukkitEntity().getLocation(),shootanim , 64);


	}

	public LivingEntity getGuardTarget() {
		return this.guardEntity;
	}

	public String getStats() {
		DecimalFormat df = new DecimalFormat("#.0");
		int h = getHealth();

		return ChatColor.RED + "[HP]:" + ChatColor.WHITE + h + "/" + sentryHealth + ChatColor.RED + " [AP]:" + ChatColor.WHITE + getArmor() + ChatColor.RED + " [STR]:" + ChatColor.WHITE + getStrength() + ChatColor.RED + " [SPD]:" + ChatColor.WHITE + df.format(getSpeed()) + ChatColor.RED + " [RNG]:" + ChatColor.WHITE + sentryRange + ChatColor.RED + " [ATK]:" + ChatColor.WHITE + AttackRateSeconds + ChatColor.RED + " [VIS]:" + ChatColor.WHITE + NightVision + ChatColor.RED + " [HEAL]:" + ChatColor.WHITE + HealRate + ChatColor.RED + " [WARN]:" + ChatColor.WHITE + WarningRange;

	}

	public void initialize() {

		// plugin.getServer().broadcastMessage("NPC " + npc.getName() +
		// " INITIALIZING!");

		// check for illegal values

		if (sentryWeight <= 0)
			sentryWeight = 1.0;
		if (AttackRateSeconds > 30)
			AttackRateSeconds = 30.0;

		if (sentryHealth < 0)
			sentryHealth = 0;

		if (sentryRange < 1)
			sentryRange = 1;
		if (sentryRange > 100)
			sentryRange = 100;

		if (sentryWeight <= 0)
			sentryWeight =  1.0;

		if (RespawnDelaySeconds < -1)
			RespawnDelaySeconds = -1;

		if (Spawn == null)
			Spawn = myNPC.getBukkitEntity().getLocation();


		// defaultSpeed = myNPC.getNavigator().getSpeed();


		setHealth(sentryHealth);
		//		}
		//		else {
		//			myNPC.getBukkitEntity().setHealth(myNPC.getBukkitEntity().getMaxHealth());
		//			_myhps = sentryHealth;
		//		}



		_myDamamgers.clear();

		this.sentryStatus = Status.isLOOKING;
		faceForward();

		shootanim = new Packet18ArmAnimation( ((CraftEntity)myNPC.getBukkitEntity()).getHandle(),1);
		healanim = new Packet18ArmAnimation( ((CraftEntity)myNPC.getBukkitEntity()).getHandle(),6);

		//	Packet derp = new net.minecraft.server.Packet15Place();

		if (guardTarget == null){
			myNPC.getBukkitEntity().teleport(Spawn); //it should be there... but maybe not if the position was saved elsewhere.
		}


		myNPC.getNavigator().getDefaultParameters().range(Math.min(25, sentryRange));
		myNPC.getNavigator().getDefaultParameters().stationaryTicks(5*20);
		//	myNPC.getNavigator().getDefaultParameters().stuckAction(new BodyguardTeleportStuckAction(this, this.plugin));

		// plugin.getServer().broadcastMessage("NPC GUARDING!");

		if (taskID == null) {
			taskID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new SentryLogicRunnable(), 40,  _logicTick);
		}

	}

	public void onEnvironmentDamae(EntityDamageEvent event){
		event.setCancelled(true);

		if(sentryStatus == Status.isDYING) return;


		if (!myNPC.isSpawned()) {
			// \\how did youg get here?
			return;
		}


		if (System.currentTimeMillis() <  okToTakedamage + 500) return;
		okToTakedamage = System.currentTimeMillis(); 


		if (Invincible)
			return;

		int finaldamage = event.getDamage();

		if (event.getCause() == DamageCause.CONTACT || event.getCause() == DamageCause.BLOCK_EXPLOSION){
			finaldamage -= getArmor();
		}


		if (finaldamage > 0 ){
			myNPC.getBukkitEntity().playEffect(EntityEffect.HURT);

			if (event.getCause() == DamageCause.FIRE){
				if (!myNPC.getNavigator().isNavigating()){
					Random R = new Random();
					myNPC.getNavigator().setTarget(myNPC.getBukkitEntity().getLocation().add(R.nextInt(2)-1, 0, R.nextInt(2)-1));
				}
			}



			if (getHealth() - finaldamage <= 0) {

				die(finaldamage);

				// plugin.getServer().broadcastMessage("Dead!");
			}
			else {
				myNPC.getBukkitEntity().damage(finaldamage);

			}
		}


	}


	private long okToTakedamage = 0;

	public void onDamage(EntityDamageByEntityEvent event) {

		event.setCancelled(true);

		if(sentryStatus == Status.isDYING) return;

		if (!myNPC.isSpawned()) {
			// \\how did youg get here?
			return;
		}

		if (System.currentTimeMillis() <  okToTakedamage + 500) return;
		okToTakedamage = System.currentTimeMillis(); 

		NPC npc = myNPC;

		LivingEntity player = null;

		hittype hit = hittype.normal;

		int finaldamage = event.getDamage();

		// Find the attacker
		if (event.getDamager() instanceof Projectile) {
			if (((Projectile) event.getDamager()).getShooter() instanceof LivingEntity) {
				player = ((Projectile) event.getDamager()).getShooter();
			}
		} else if (event.getDamager() instanceof LivingEntity) {
			player = (LivingEntity) event.getDamager();
		}


		if (Invincible)
			return;

		// can i kill it? lets go kill it.
		if (player != null) {
			if (this.Retaliate) {
				setTarget(player, true);
			}
		}

		if (LuckyHits) {
			// Calulate crits
			double damagemodifer = event.getDamage();

			Random r = new Random();
			int luckeyhit = r.nextInt(100);

			// if (damagemodifer == 1.0) luckeyhit += 30; //use a weapon, dummy

			if (luckeyhit < 3) {
				damagemodifer = damagemodifer * 2.00;
				hit = hittype.disembowel;
			} else if (luckeyhit < 17) {

				damagemodifer = damagemodifer * 1.75;
				hit = hittype.main;
			} else if (luckeyhit < 15) {
				damagemodifer = damagemodifer * 1.50;
				hit = hittype.injure;
			} else if (luckeyhit > 91) {
				damagemodifer = damagemodifer * 0.50;
				hit = hittype.glance;
			} else if (luckeyhit > 96) {

				damagemodifer = 0;
				hit = hittype.miss;

			}

			finaldamage = (int) Math.round(damagemodifer);
		}

		int arm = getArmor();

		if (finaldamage > 0) {

			if (player != null) {
				// knockback
				npc.getBukkitEntity().setVelocity( player.getLocation().getDirection().multiply(1.0 / (sentryWeight + (arm/5))));
			}

			// Apply armor
			finaldamage -= arm;

			// there was damamge before armor.
			if (finaldamage <= 0){
				npc.getBukkitEntity().getWorld().playEffect(npc.getBukkitEntity().getLocation(), org.bukkit.Effect.ZOMBIE_CHEW_IRON_DOOR,1);
				hit = hittype.block;
			}
		}

		if (player instanceof CraftPlayer && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(player)) {

			if(!_myDamamgers.contains(player)) _myDamamgers.add((Player) player);

			// Messages
			switch (hit) {
			case normal:
				((Player) player).sendMessage(ChatColor.WHITE + "*** You hit " + myNPC.getName() + " for " + finaldamage + " damage");
				break;
			case miss:
				((Player) player).sendMessage(ChatColor.GRAY + "*** You miss " + myNPC.getName());
				break;
			case block:
				((Player) player).sendMessage(ChatColor.GRAY + "*** You fail to penetrate " + myNPC.getName() + "'s armor");
				break;
			case main:
				((Player) player).sendMessage(ChatColor.GOLD + "*** You MAIM " + myNPC.getName() + " for " + finaldamage + " damage");
				break;
			case disembowel:
				((Player) player).sendMessage(ChatColor.RED + "*** You DISEMBOWEL " + myNPC.getName() + " for " + finaldamage + " damage");
				break;
			case injure:
				((Player) player).sendMessage(ChatColor.YELLOW + "*** You injure " + myNPC.getName() + " for " + finaldamage + " damage");
				break;
			case glance:
				((Player) player).sendMessage(ChatColor.GRAY + "*** Your blow glances off " + myNPC.getName() + " for " + finaldamage + " damage");
				break;

			}
		}

		if (finaldamage > 0) {
			npc.getBukkitEntity().playEffect(EntityEffect.HURT);

			// is he dead?
			if (getHealth() - finaldamage <= 0) {

				die(finaldamage);

				// plugin.getServer().broadcastMessage("Dead!");
			}
			else 	myNPC.getBukkitEntity().damage(finaldamage);
		}
	}

	private void die(int finaldamage){
		if (sentryStatus == Status.isDYING || sentryStatus == Status.isDEAD) return;
		sentryStatus = Status.isDYING;




		setTarget(null, false);
		//		myNPC.getTrait(Waypoints.class).getCurrentProvider().setPaused(true);

		if (RespawnDelaySeconds == -1) {
			sentryStatus = Status.isDEAD;
			cancelRunnable();
			myNPC.destroy();
			return;
		} else {
			isRespawnable = System.currentTimeMillis() + RespawnDelaySeconds * 1000;
		}

		if		(!plugin.SentryDeath(_myDamamgers, myNPC))	{
			//Denizen is NOT handling this death
			sentryStatus = Status.isDEAD;

			if (this.DropInventory)  myNPC.getBukkitEntity().getLocation().getWorld().spawn(myNPC.getBukkitEntity().getLocation(), ExperienceOrb.class).setExperience(plugin.SentryEXP);


			if (plugin.DieLikePlayers){
				if (myNPC.getBukkitEntity() instanceof HumanEntity && !this.DropInventory) {
					//delete armor so it wont drop naturally.
					((HumanEntity) myNPC.getBukkitEntity()).getInventory().clear();
					((HumanEntity) myNPC.getBukkitEntity()).getInventory().setArmorContents(null);
				}

				myNPC.getBukkitEntity().setHealth(0);		
			}
			else{
				if (myNPC.getBukkitEntity() instanceof HumanEntity && this.DropInventory) {
					//manually drop inventory.
					for( ItemStack is:	((HumanEntity) myNPC.getBukkitEntity()).getInventory().getArmorContents()){
						if (is.getTypeId()>0)	myNPC.getBukkitEntity().getWorld().dropItemNaturally(myNPC.getBukkitEntity().getLocation(), is);
					}	
					ItemStack is = ((HumanEntity) myNPC.getBukkitEntity()).getInventory().getItemInHand();
					if (is.getTypeId()>0) myNPC.getBukkitEntity().getWorld().dropItemNaturally(myNPC.getBukkitEntity().getLocation(),is );
				}		

				myNPC.despawn();
			}
		}
	}

	@EventHandler
	public void onRightClick(NPCRightClickEvent event) {

	}

	public boolean setGuardTarget(String name) {
		// plugin.getServer().broadcastMessage("Setting guard");

		if (myNPC == null)
			return false;

		if (name == null) {
			guardEntity = null;
			guardTarget = null;
			setTarget(null, false);// clear active hostile target
			return true;
		}

		List<Entity> EntitiesWithinRange = myNPC.getBukkitEntity().getNearbyEntities(sentryRange, sentryRange, sentryRange);

		for (Entity aTarget : EntitiesWithinRange) {

			if (aTarget instanceof Player) {
				if (((Player) aTarget).getName().equals(name)) {
					guardEntity = (LivingEntity) aTarget;
					guardTarget = ((Player) aTarget).getName();
					setTarget(null, false); // clear active hostile target
					return true;
				}

			}

		}
		return false;
	}

	private boolean inciendary = false;


	public boolean loaded = false; 

	public List<PotionEffect> potionEffects = null;

	public void setTarget(LivingEntity theEntity, boolean isretaliation) {
		if (((CitizensNPC)myNPC).getHandle() == null ) return;

		if (theEntity == myNPC.getBukkitEntity()) return; //I don't care how you got here. No. just No.

		if (theEntity == null) {
			// this gets called while npc is dead, reset things.
			sentryStatus = Status.isLOOKING;
			projectileTarget = null;
			meleeTarget = null;
			_projTargetLostLoc = null;
		}

		if (myNPC == null)
			return;
		if (!myNPC.isSpawned())
			return;

		if (theEntity == null) {
			// no hostile target
			//	plugin.getServer().broadcastMessage("Set Target Null");
			//		plugin.getServer().broadcastMessage(myNPC.getNavigator().getTargetAsLocation().toString());
			//plugin.getServer().broadcastMessage(((Boolean)myNPC.getTrait(Waypoints.class).getCurrentProvider().isPaused()).toString());

			if (guardEntity != null) {
				// yarr... im a guarrrd.
				myNPC.getDefaultGoalController().setPaused(true);
				//	if (!myNPC.getTrait(Waypoints.class).getCurrentProvider().isPaused())  myNPC.getTrait(Waypoints.class).getCurrentProvider().setPaused(true);

				if (myNPC.getNavigator().getEntityTarget() == null || (myNPC.getNavigator().getEntityTarget() != null && myNPC.getNavigator().getEntityTarget().getTarget() != guardEntity)){
					if (guardEntity.getLocation().getWorld() != myNPC.getBukkitEntity().getLocation().getWorld()){
						myNPC.getBukkitEntity().teleport(guardEntity);
						return;
					}
					myNPC.getNavigator().setTarget(guardEntity, false);
					//		myNPC.getNavigator().getLocalParameters().stuckAction(bgteleport);
					myNPC.getNavigator().getLocalParameters().stationaryTicks(3*20);
				}
			} else {
				//not a guard
				//		myNPC.getNavigator().cancelNavigation();
				if (myNPC.getDefaultGoalController().isPaused()) 
					myNPC.getDefaultGoalController().setPaused(false);
				//		if (myNPC.getTrait(Waypoints.class).getCurrentProvider().isPaused()) {
				//		myNPC.getTrait(Waypoints.class).getCurrentProvider().setPaused(false);
				//	}
				else faceForward();
			}
			return;
		}

		if (theEntity == guardEntity)
			return; // dont attack my dude.

		if (isretaliation) sentryStatus = Status.isRETALIATING;
		else sentryStatus = Status.isHOSTILE;

		int weapon = 0;
		if(!myNPC.getNavigator().isNavigating()) faceEntity(myNPC.getBukkitEntity(), theEntity);
		org.bukkit.inventory.ItemStack is = null;

		if (myNPC.getBukkitEntity() instanceof HumanEntity) {
			is = ((HumanEntity) myNPC.getBukkitEntity()).getInventory().getItemInHand();
			weapon = is.getTypeId();
			if(	weapon != plugin.witchdoctor) is.setDurability((short) 0);

		}

		lightning = false;
		mclightning = false;
		meleeTarget = null;
		projectileTarget = null;
		inciendary = false;
		potionEffects = plugin.WeaponEffects.get(weapon);

		if(weapon == plugin.archer){
			myProjectile = org.bukkit.entity.Arrow.class;
			projectileTarget = theEntity;
		}
		else if(weapon ==  plugin.pyro3){
			myProjectile = org.bukkit.entity.Fireball.class;
			projectileTarget = theEntity;
		}
		else if(weapon ==  plugin.pyro2){
			myProjectile = org.bukkit.entity.SmallFireball.class;
			projectileTarget = theEntity;
			inciendary = true;
		}
		else if(weapon ==  plugin.pyro1){
			myProjectile = org.bukkit.entity.SmallFireball.class;
			projectileTarget = theEntity;
			inciendary =false;
		}
		else if(weapon == plugin.magi){
			myProjectile = org.bukkit.entity.Snowball.class;
			projectileTarget = theEntity;
		}
		else if(weapon == plugin.warlock1){
			myProjectile = org.bukkit.entity.EnderPearl.class;
			projectileTarget = theEntity;
		}
		else if(weapon == plugin.bombardier){
			myProjectile = org.bukkit.entity.Egg.class;
			projectileTarget = theEntity;
		}
		else if(weapon == plugin.witchdoctor){
			myProjectile = org.bukkit.entity.ThrownPotion.class;
			projectileTarget = theEntity;
			potiontype = is.getDurability();
		}
		else if(weapon == plugin.sc1){
			myProjectile = org.bukkit.entity.ThrownPotion.class;
			projectileTarget = theEntity;
			meleeTarget = null;
			lightning = true;
		}
		else if (weapon == plugin.sc2){
			myProjectile = org.bukkit.entity.ThrownPotion.class;
			projectileTarget = theEntity;
			meleeTarget = null;
			lightning = true;
			mclightning = true;	
		}
		else{
			// Manual Attack
			meleeTarget = theEntity;

			if (myNPC.getNavigator().getEntityTarget() != null && myNPC.getNavigator().getEntityTarget().getTarget() == theEntity) return; //already attacking this, dummy.


			if (!myNPC.getDefaultGoalController().isPaused()) 
				myNPC.getDefaultGoalController().setPaused(true);

			myNPC.getNavigator().setTarget(theEntity, true);
			myNPC.getNavigator().getLocalParameters().speedModifier(getSpeed());
			myNPC.getNavigator().getLocalParameters().stuckAction(giveup);
			myNPC.getNavigator().getLocalParameters().stationaryTicks(5*20);
		}
	}

	public int getArmor(){

		double mod = 0;
		if ( myNPC.getBukkitEntity() instanceof Player){
			for (ItemStack is:((Player)myNPC.getBukkitEntity()).getInventory().getArmorContents()){
				if (plugin.ArmorBuffs.containsKey(is.getTypeId())) mod += plugin.ArmorBuffs.get(is.getTypeId());		
			}
		}

		return (int) (Armor + mod);
	}

	public int getStrength(){
		double mod = 0;

		if ( myNPC.getBukkitEntity() instanceof Player){
			if (plugin.StrengthBuffs.containsKey(((Player)myNPC.getBukkitEntity()).getInventory().getItemInHand().getTypeId())) mod += plugin.StrengthBuffs.get(((Player)myNPC.getBukkitEntity()).getInventory().getItemInHand().getTypeId());		
		}

		return (int) (Strength + mod);
	}


	public float getSpeed(){
		double mod = 0;
		if ( myNPC.getBukkitEntity() instanceof Player){
			for (ItemStack is:((Player)myNPC.getBukkitEntity()).getInventory().getArmorContents()){
				if (plugin.SpeedBuffs.containsKey(is.getTypeId())) mod += plugin.SpeedBuffs.get(is.getTypeId());		
			}
		}	
		return (float) (sentrySpeed + mod);
	}

}
