package me.caseload.kbsync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.PacketEvents;

import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import me.caseload.kbsync.command.Subcommands;
import me.caseload.kbsync.listener.Async;
import me.caseload.kbsync.listener.LagCompensator;
import me.caseload.kbsync.listener.PlayerHitListener;
import me.caseload.kbsync.listener.PlayerVelocityListener;

public final class KbSync extends JavaPlugin {

    private static KbSync instance;
    private static ProtocolManager protocolManager;
    private LagCompensator lagCompensator;
    private ExecutorService executorService;

    private static final Map<UUID, List<Long>> keepAliveTime = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, Integer> accuratePing = new HashMap<>();

    public static final Map<UUID, Double> kb = new HashMap<>();

    @Override
    public void onLoad() {
        // Inicializar PacketEvents en la fase de carga
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;
        protocolManager = ProtocolLibrary.getProtocolManager();
        executorService = Executors.newFixedThreadPool(2);

        // Inicializar PacketEvents en la fase de habilitación
        PacketEvents.getAPI().init();

        lagCompensator = new LagCompensator(executorService);

        // Crear la instancia de Async y registrar el HitPacketListener
        Async asyncHandler = new Async(lagCompensator);

        // Registramos la clase Async como listener de eventos de Bukkit
        getServer().getPluginManager().registerEvents(asyncHandler, this);

        saveDefaultConfig();
        setupProtocolLib();

        String pingRetrievalMethod = getConfig().getString("ping_retrieval.method").toLowerCase();
        if (pingRetrievalMethod.equals("runnable")) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PING);
                Bukkit.getOnlinePlayers().forEach(player -> sendPingPacket(player, packet));
            }, 0L, getConfig().getInt("ping_retrieval.runnable_ticks"));
            Bukkit.getLogger().info("[KbSync] Started Bukkit runnable");
        } else if (!pingRetrievalMethod.equals("hit")) {
            Bukkit.getLogger().severe("[KbSync] Disabling plugin. The ping retrieval method \"" + pingRetrievalMethod + "\" does not exist.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PlayerHitListener(protocolManager, lagCompensator), this);
        Bukkit.getLogger().info("[KbSync] Registered PlayerHitListener");
        getServer().getPluginManager().registerEvents(new PlayerVelocityListener(accuratePing, lagCompensator), this);
        Bukkit.getLogger().info("[KbSync] Registered PlayerVelocityListener");

        getCommand("knockbacksync").setExecutor(new Subcommands(accuratePing));
        getCommand("knockbacksync").setTabCompleter(new Subcommands(accuratePing));

        Bukkit.getLogger().info("[KbSync] Using the \"" + pingRetrievalMethod + "\" ping retrieval method.");
    }

    @Override
    public void onDisable() {
        executorService.shutdownNow();
        PacketEvents.getAPI().terminate();
    }

    public static KbSync getInstance() {
        return instance;
    }

    public int getAccuratePing(UUID playerUUID) {
        return accuratePing.getOrDefault(playerUUID, 0);
    }

    public static void sendPingPacket(Player p, PacketContainer packet) {
        UUID uuid = p.getUniqueId();
        Long currentTime = System.currentTimeMillis();
        List<Long> timeData = keepAliveTime.get(uuid);
        if (timeData == null) {
            timeData = new ArrayList<>(2);
            timeData.add(0L);
            timeData.add(0L);
        }
        timeData.set(0, currentTime);
        keepAliveTime.put(uuid, timeData);
        protocolManager.sendServerPacket(p, packet);
    }

    private void setupProtocolLib() {
        protocolManager.addPacketListener(new PacketAdapter(
                this,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.PONG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Long currentTime = System.currentTimeMillis();
                final Player player = event.getPlayer();
                UUID uuid = player.getUniqueId();

                Long pingTime = 0L;
                List<Long> timeData = keepAliveTime.get(uuid);
                if (timeData == null) {
                    timeData = new ArrayList<>(2);
                    timeData.add(0L);
                    timeData.add(0L);
                } else {
                    pingTime = currentTime - timeData.get(0);
                    timeData.set(1, pingTime);
                }
                keepAliveTime.put(uuid, timeData);
                final Long ping = pingTime;

                int exactPing = 0;
                try {
                    exactPing = Math.toIntExact(ping);
                } catch (ArithmeticException ignored) {}
                if (exactPing > 10000) exactPing = 0;
                if (player.isOnline()) {
                    accuratePing.put(uuid, exactPing);
                }
            }
        });
    }

    public LagCompensator getLagCompensator() {
        return lagCompensator;

    }
}
