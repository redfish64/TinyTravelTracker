
package rtree;

/**
 * 3D Axis Aligned Bounding Box
 * @author Colonel32
 */
public class AABB implements BoundedObject
{
	public int minX, minY, minZ;
	public int maxX, maxY, maxZ;

	public AABB()
	{
		minX = minY = minZ = 0;
		maxX = maxY = maxZ = 0;
	}

	public void setMinCorner(int px, int py, int pz)
	{
		minX = px;
		minY = py;
		minZ = pz;
	}
	public void setMaxCorner(int px, int py, int pz)
	{
		maxX = px;
		maxY = py;
		maxZ = pz;
	}

	public boolean contains(int px, int py, int pz)
	{
		return px >= minX && px <= maxX &&
				py >= minY && py <= maxY &&
				pz >= minZ && pz <= maxZ;
	}

	public boolean overlaps(AABB other)
	{
		if(minX > other.maxX) return false;
		if(maxX < other.minX) return false;
		if(minY > other.maxY) return false;
		if(maxY < other.minY) return false;
		if(minZ > other.maxZ) return false;
		if(maxZ < other.minZ) return false;
		return true;
	}
	
	/**
	 * Returns the amount of overlap between 2 AABBs. Result will be negative if they
	 * do not overlap.
	 */
	public int getOverlap(AABB other)
	{
		int overlapx =  (maxX - minX +other.maxX - other.minX) - Math.abs(minX+maxX-other.minX-other.minX);
		int overlapy =  (maxY - minY +other.maxY - other.minY) - Math.abs(minX+maxY-other.minY-other.minY);
		int overlapz =  (maxZ - minZ +other.maxZ - other.minZ) - Math.abs(minX+maxZ-other.minZ-other.minZ);
		
		return Math.max(overlapx, Math.max(overlapy, overlapz));
		
	}

	/**
	 * Returns the amount that other will need to be expanded to fit this.
	 */
	public int expansionNeeded(AABB other)
	{
		int total = 0;

		if(other.minX < minX) total += minX - other.minX;
		if(other.maxX > maxX) total += other.maxX - maxX;

		if(other.minY < minY) total += minY - other.minY;
		if(other.maxY > maxY) total += other.maxY - maxY;

		if(other.minZ < minZ) total += minZ - other.minZ;
		if(other.maxZ > maxZ) total += other.maxZ - maxZ;

		return total;
	}

	/**
	 * Computes an AABB that contains both this and other and stores it in this.
	 * @return this
	 */
	public AABB merge(AABB other)
	{
		minX = Math.min(minX, other.minX);
		maxX = Math.max(maxX, other.maxX);

		minY = Math.min(minY, other.minY);
		maxY = Math.max(maxY, other.maxY);

		minZ = Math.min(minZ, other.minZ);
		maxZ = Math.max(maxZ, other.maxZ);

		return this;
	}

	public int getVolume()
	{
		return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
	}

	public AABB clone()
	{
		AABB clone = new AABB();
		clone.minX = minX;
		clone.minY = minY;
		clone.minZ = minZ;

		clone.maxX = maxX;
		clone.maxY = maxY;
		clone.maxZ = maxZ;
		return clone;
	}

	public void cloneInto(AABB target)
	{
		target.minX = minX;
		target.minY = minY;
		target.minZ = minZ;

		target.maxX = maxX;
		target.maxY = maxY;
		target.maxZ = maxZ;
	}

	public boolean equals(AABB other)
	{
		return minX == other.minX && maxX == other.maxX &&
				minY == other.minY && maxY == other.maxY &&
				minZ == other.minZ && maxZ == other.maxZ;
	}

	public AABB getBounds() { return this; }
	public String toString()
	{
		return String.format("(%1$d,%2$d,%3$d):(%4$d,%5$d,%6$d)", minX, minY, minZ, maxX, maxY, maxZ);
	}
}
