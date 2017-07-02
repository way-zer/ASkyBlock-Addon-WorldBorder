package cf.wayzer.skyblock_addon;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.bukkit.Achievement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.ASkyBlockAPI;
import com.wasteofplastic.askyblock.Island;
import com.wasteofplastic.askyblock.events.ChallengeCompleteEvent;
import com.wasteofplastic.askyblock.events.IslandEnterEvent;
import com.wasteofplastic.askyblock.events.IslandNewEvent;

import cf.wayzer.skyblock_addon.utils.CDatabase;
import cf.wayzer.util.WorldBorderUtil;

public class SkyBlockAddon extends JavaPlugin implements Listener {
	static ASkyBlockAPI skyBlockAPI;
	static ASkyBlock skyBlock;
	static CDatabase database;
	private LoadingCache<UUID, DataIsland> datas = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES)
			.build(new CacheLoader<UUID, DataIsland>() {
				public DataIsland load(UUID uuid) throws Exception {
					DataIsland data = database.getDatabase().find(DataIsland.class).where().ieq("uuid", uuid.toString()) //$NON-NLS-1$
							.findUnique();
					if (data == null)
						data = new DataIsland();
					data.setUuid(uuid.toString());
					return data;
				};
			});

	public DataIsland getData(Island land) {
		try {
			DataIsland data = datas.get(land.getOwner());
			data.island = land;
			return data;
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean hasIslandAndIsLeader(UUID uuid) {
		return skyBlockAPI.hasIsland(uuid)
				&& (skyBlockAPI.getTeamLeader(uuid) == null || uuid.equals(skyBlockAPI.getTeamLeader(uuid)));
	}

	@Override
	public void onEnable() {
		skyBlockAPI = ASkyBlockAPI.getInstance();
		skyBlock = ASkyBlock.getPlugin();
		database = new CDatabase(this) {
			private final List<Class<?>> classes = Arrays.asList(DataIsland.class);

			@Override
			protected List<Class<?>> getDatabaseClasses() {
				return classes;
			}
		};
		database.initializeDatabase(Messages.getString("SQLConnect.Driver"), Messages.getString("SQLConnect.Address"), Messages.getString("SQLConnect.User"), Messages.getString("SQLConnect.Password"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"SERIALIZABLE", false, false); //$NON-NLS-1$
		getServer().getPluginManager().registerEvents(this, this);
		for (Player p : getServer().getOnlinePlayers()) {
			if (skyBlockAPI.islandAtLocation(p.getLocation())) {
				Island land = skyBlockAPI.getIslandAt(p.getLocation());
				DataIsland data;
				if (land.getOwner() == null)
					try {
						data = datas.get(p.getUniqueId());
					} catch (ExecutionException e1) {
						throw new RuntimeException(e1);
					}
				else
					data = getData(land);
				WorldBorderUtil.sendToPlayer(p, data.island.getCenter(), data.getSize());
			}
		}
	}

	@Override
	public void onDisable() {
		for (Player p : getServer().getOnlinePlayers()) {
			if (hasIslandAndIsLeader(p.getUniqueId())) {
				try {
					datas.get(p.getUniqueId()).save();
				} catch (ExecutionException e1) {
					throw new RuntimeException(e1);
				}
			}
		}
	}

	@EventHandler
	public void onChallenge(ChallengeCompleteEvent e) {
		UUID uuid = e.getPlayer().getUniqueId();
		if (hasIslandAndIsLeader(uuid)) {
			double add = e.getExpReward() / 100D;
			DataIsland data = getData(skyBlockAPI.getIslandOwnedBy(uuid));
			for (Player p : getServer().getOnlinePlayers()) {
				if (data.island.onIsland(p.getLocation()))
					WorldBorderUtil.addSize(p, data.getSize(), data.getSize() + add);
			}
			data.setSize(data.getSize() + add);
		}
	}

	@EventHandler
	public void onAchievement(PlayerAchievementAwardedEvent e) {
		UUID uuid = e.getPlayer().getUniqueId();
		if (hasIslandAndIsLeader(uuid)) {
			double add = 1;
			DataIsland data = getData(skyBlockAPI.getIslandOwnedBy(uuid));
			for (Player p : getServer().getOnlinePlayers()) {
				if (data.island.onIsland(p.getLocation()))
					WorldBorderUtil.addSize(p, data.getSize(), data.getSize() + add);
			}
			data.setSize(data.getSize() + add);
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		getServer().getScheduler().runTask(this, new Runnable() {
			public void run() {
				if (skyBlockAPI.islandAtLocation(e.getPlayer().getLocation())) {
					Island land = skyBlockAPI.getIslandAt(e.getPlayer().getLocation());
					DataIsland data;
					if (land.getOwner() == null)
						try {
							data = datas.get(e.getPlayer().getUniqueId());
						} catch (ExecutionException e1) {
							throw new RuntimeException(e1);
						}
					else
						data = getData(land);
					WorldBorderUtil.sendToPlayer(e.getPlayer(), data.island.getCenter(), data.getSize());
				}
			}
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		if (hasIslandAndIsLeader(e.getPlayer().getUniqueId())) {
			try {
				datas.get(e.getPlayer().getUniqueId()).save();
			} catch (ExecutionException e1) {
				throw new RuntimeException(e1);
			}
		}
	}

	@EventHandler
	public void onTP(PlayerTeleportEvent e) {
		Island land = skyBlockAPI.getIslandAt(e.getPlayer().getLocation());
		DataIsland data;
		if (land.getOwner() == null)
			try {
				data = datas.get(e.getPlayer().getUniqueId());
			} catch (ExecutionException e1) {
				throw new RuntimeException(e1);
			}
		else
			data = getData(land);
		WorldBorderUtil.sendToPlayer(e.getPlayer(), data.island.getCenter(), data.getSize());
	}

	@EventHandler
	public void onNew(IslandNewEvent e) {
		System.out.println(2);
		e.getPlayer().removeAchievement(Achievement.OPEN_INVENTORY);
		DataIsland data = getData(skyBlockAPI.getIslandAt(e.getIslandLocation()));
		data.setSize(4);
		WorldBorderUtil.sendToPlayer(e.getPlayer(), data.island.getCenter(), data.getSize());
	}

	@EventHandler
	public void onJoin(IslandEnterEvent e) {
		System.out.println(3);
		DataIsland data = getData(e.getIsland());
		WorldBorderUtil.sendToPlayer(getServer().getPlayer(e.getPlayer()), data.island.getCenter(), data.getSize());
	}

	@EventHandler
	public void ondeath(PlayerRespawnEvent e) {
		getServer().getScheduler().runTask(this, new Runnable() {
			@Override
			public void run() {
				Island land = skyBlockAPI.getIslandAt(e.getRespawnLocation());
				if (land != null) {
					DataIsland data = getData(land);
					WorldBorderUtil.sendToPlayer(e.getPlayer(), data.island.getCenter(), data.getSize());
				}
			}
		});
	}
	
	@EventHandler
	public void onJoinEnd(PlayerChangedWorldEvent e){
		getServer().getScheduler().runTask(this, new Runnable() {
			@Override
			public void run() {
				Island land = skyBlockAPI.getIslandAt(e.getPlayer().getLocation());
				if (land != null) {
					DataIsland data = getData(land);
					WorldBorderUtil.sendToPlayer(e.getPlayer(), data.island.getCenter(), data.getSize());
				}
			}
		});
	}
}
