package me.caseload.kbsync.listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import me.caseload.kbsync.KbSync;
import me.caseload.kbsync.ServerSidePlayerHitEvent;
import me.caseload.kbsync.utils.AABB;
import me.caseload.kbsync.utils.Ray;
import net.jafama.FastMath;

public class Async implements Listener {

    public final LagCompensator lagCompensator;
    private final Map<Integer, Player> entityIdCache = new ConcurrentHashMap<>();
    private static final double MAX_HIT_REACH = 3.0;
    private final AABB playerBoundingBox;
    private final float reach;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2); // Pool de hilos

    public Async(LagCompensator lagCompensator) {
        this.lagCompensator = lagCompensator;

        // Inicialización de la caja de colisión y otros parámetros
        double length = 0.9;
        double height = 1.8;
        reach = 4.0f;
        playerBoundingBox = new AABB(new Vector(-length / 2, 0, -length / 2), new Vector(length / 2, height, length / 2));

        // Registrar el PacketListener con la prioridad más alta
        PacketEvents.getAPI().getEventManager().registerListener(new HitPacketListener(lagCompensator));
    }

    public class HitPacketListener extends PacketListenerAbstract {
        private final LagCompensator lagCompensator;

        public HitPacketListener(LagCompensator lagCompensator) {
            super(PacketListenerPriority.HIGHEST); // Máxima prioridad
            this.lagCompensator = lagCompensator;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                // Clonar el evento para mantener los datos del paquete
                PacketReceiveEvent copy = event.clone();
                EXECUTOR.execute(() -> {
                    try {
                        WrapperPlayClientInteractEntity interaction = new WrapperPlayClientInteractEntity(copy);
                        if (interaction.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                            Player attacker = Bukkit.getPlayer(copy.getUser().getUUID()); // Obtener Player de Bukkit
                            if (attacker == null) {
                                return; // Salir si el atacante no está en línea
                            }

                            int entityId = interaction.getEntityId();

                            // Procesar el hit de manera asíncrona
                            Player target = getPlayerFromEntityId(entityId);
                            if (target != null && isHitValid(attacker, target)) {
                                int rewindMillisecs = 20;  // Ajusta este valor según sea necesario
                                Location compensatedLocation = lagCompensator.getHistoryLocation(rewindMillisecs, target);
                                if (compensatedLocation == null) {
                                    return;  // Salir si compensatedLocation es null
                                }

                                // Traducir la caja de colisión del objetivo a la ubicación compensada
                                AABB victimBox = playerBoundingBox.clone();
                                Vector compensatedVector = compensatedLocation.toVector();
                                victimBox.translateTo(compensatedVector);
                                victimBox.translate(playerBoundingBox.getMin());

                                Location attackerLocation = attacker.getLocation();
                                if (attackerLocation == null) {
                                    return;  // Salir si la ubicación del atacante es null
                                }
                                Ray ray = new Ray(attackerLocation.toVector().add(new Vector(0, attacker.isSneaking() ? 1.52625 : 1.62, 0)), attackerLocation.getDirection());

                                if (victimBox.intersectsRay(ray, 0, reach) != null) {
                                    // Programar la tarea en el hilo principal del servidor
                                    Bukkit.getScheduler().runTask(KbSync.getInstance(), () -> handleHit(attacker, target));
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Log la excepción
                        e.printStackTrace();
                    } finally {
                        copy.cleanUp(); // Limpieza del evento para evitar fugas de memoria
                    }
                });
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            // Puedes agregar lógica adicional si es necesario
        }
    }

    private Player getPlayerFromEntityId(int entityId) {
        Player player = entityIdCache.get(entityId);
        if (player == null) {
            player = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getEntityId() == entityId)
                .findFirst()
                .orElse(null);
            if (player != null) {
                entityIdCache.put(entityId, player);
            }
        }
        return player;
    }

    private boolean isHitValid(Player attacker, Player target) {
        if (attacker == null || target == null || !attacker.getWorld().equals(target.getWorld())) {
            return false;
        }

        Location attackerLoc = attacker.getLocation();
        Location targetLoc = target.getLocation();
        if (attackerLoc == null || targetLoc == null) {
            return false;
        }

        double dx = attackerLoc.getX() - targetLoc.getX();
        double dy = attackerLoc.getY() - targetLoc.getY();
        double dz = attackerLoc.getZ() - targetLoc.getZ();
        return FastMath.sqrt(FastMath.pow2(dx) + FastMath.pow2(dy) + FastMath.pow2(dz)) <= MAX_HIT_REACH;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        entityIdCache.put(player.getEntityId(), player);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        entityIdCache.remove(player.getEntityId());
    }

    private void handleHit(Player attacker, Player target) {
        if (attacker != null && target != null) {
            Bukkit.getPluginManager().callEvent(new ServerSidePlayerHitEvent(attacker, target));
        }
    }
}
