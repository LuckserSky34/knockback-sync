package me.caseload.kbsync.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;

import me.caseload.kbsync.KbSync;

public class PlayerHitListener implements Listener {

    private final ProtocolManager protocolManager;
    private final LagCompensator lagCompensator;

    public PlayerHitListener(ProtocolManager protocolManager, LagCompensator lagCompensator) {
        this.protocolManager = protocolManager;
        this.lagCompensator = lagCompensator;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();

        // Calcular el valor de knockback basado en el delay de 1.8 (10 ticks)
        double knockbackValue = calculateKnockback(damager, victim);
        KbSync.kb.put(victim.getUniqueId(), knockbackValue);

        // Compensar el lag usando LagCompensator
        lagCompensator.registerMovement(victim, victim.getLocation());

        String pingRetrievalMethod = KbSync.getInstance().getConfig().getString("ping_retrieval.method").toLowerCase();

        if (pingRetrievalMethod.equals("hit")) {
            // Enviar el paquete de ping asíncronamente
            Bukkit.getScheduler().runTaskAsynchronously(KbSync.getInstance(), () -> {
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PING);
                KbSync.sendPingPacket(victim, packet);
            });
        }

        // Actualizar la posición del jugador después de recibir el knockback
        updatePlayerPosition(victim);
    }

    public double calculateKnockback(Player attacker, Player victim) {
        float attackCooldown = attacker.getAttackCooldown();
        double knockbackValue = (attackCooldown > 0.9) ? 0.1 : 0.11080000519752503;

        if (!attacker.isSprinting()) {
            knockbackValue = 0.11080000519752503;
            double reduction = 0.11000000119;
            knockbackValue -= reduction;
        }

        return knockbackValue;
    }

    private void updatePlayerPosition(Player victim) {
        // Aquí puedes añadir la lógica para ajustar la posición del jugador
        // según el knockback calculado y el lag compensado.
        // Puedes utilizar la clase LagCompensator para obtener la ubicación
        // compensada y ajustar la posición del jugador.

        // Ejemplo:
        // Location newLocation = lagCompensator.getCompensatedLocation(victim);
        // if (newLocation != null) {
        //     victim.teleport(newLocation);
        // }
    }
}
