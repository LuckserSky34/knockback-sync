package me.caseload.kbsync.utils;

import org.bukkit.util.Vector;

public class Ray implements Cloneable {

    private Vector origin;
    private Vector direction;

    public Ray(Vector origin, Vector direction) {
        this.origin = origin != null ? origin : new Vector(0, 0, 0);
        this.direction = direction != null ? direction : new Vector(0, 0, 0);
    }

    public Vector getPointAtDistance(double distance) {
        if (direction == null) {
            return origin; // Maneja el caso donde la dirección es null
        }
        Vector dir = new Vector(direction.getX(), direction.getY(), direction.getZ());
        Vector orig = new Vector(origin.getX(), origin.getY(), origin.getZ());
        return orig.add(dir.multiply(distance));
    }

    @Override
    public Ray clone() {
        try {
            Ray clone = (Ray) super.clone();
            clone.origin = this.origin != null ? this.origin.clone() : null;
            clone.direction = this.direction != null ? this.direction.clone() : null;
            return clone;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace(); // Log the exception
        }
        return null;
    }

    @Override
    public String toString() {
        return "origin: " + origin + " direction: " + direction;
    }

    public Vector getOrigin() {
        return origin;
    }

    public Vector getDirection() {
        return direction;
    }
}
