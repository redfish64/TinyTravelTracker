
package rtree;

import java.util.*;

import rtree.RTree.Processor;

/**
 * 3D R-Tree implementation for minecraft.
 * Uses algorithms from: http://www.sai.msu.su/~megera/postgres/gist/papers/Rstar3.pdf
 * @author Colonel32
 */
public class RTree
{
	private Node root;
	private int maxSize;
	private int minSize;
	private NodeSplitter splitter;
	
	public static interface Processor
	{
		/**
		 * @return true if processing should continue, false otherwise
		 */
		public boolean process(BoundedObject bo);
	}
	
	private class Node implements BoundedObject
	{
		Node parent;
		AABB box;
		ArrayList<Node> children;
		ArrayList<BoundedObject> data;
		
		public Node()
		{
		}

		public Node(boolean isLeaf)
		{
			if(isLeaf)
				data = new ArrayList<BoundedObject>(maxSize+1);
			else
				children = new ArrayList<Node>(maxSize+1);
		}

		public boolean isLeaf() { return data != null; }
		public boolean isRoot() { return parent == null; }

		public void addTo(Node parent)
		{
			assert(parent.children != null);
			parent.children.add(this);
			this.parent = parent;
			computeMBR();
			splitter.split(parent);
		}

		public void computeMBR()
		{
			computeMBR(true);
		}

		public void computeMBR(boolean doParents)
		{
			if(box == null) box = new AABB();
			if(!isLeaf())
			{
				if(children.isEmpty()) return;
				children.get(0).box.cloneInto(box);
				for(int i=1; i<children.size(); i++)
					box.merge(children.get(i).box);
			}
			else
			{
				if(data.isEmpty()) return;
				data.get(0).getBounds().cloneInto(box);
				for(int i=1; i<data.size(); i++)
					box.merge(data.get(i).getBounds());
			}

			if(doParents && parent != null) parent.computeMBR();
		}

		public void remove()
		{
			if(parent == null)
			{
				assert(root == this);
				root = null;
				return;
			}
			parent.children.remove(this);
			if(parent.children.isEmpty())
				parent.remove();
			else
				parent.computeMBR();
		}

		public ArrayList<? extends BoundedObject> getSubItems() { return isLeaf() ? data : children; }

		public AABB getBounds() { return box; }
		public boolean contains(int px, int py, int pz) { return box.contains(px,py,pz); }

		public int size() { return isLeaf() ? data.size() : children.size(); }
		public int depth()
		{
			Node n = this;
			int d = 0;
			while(n != null)
			{
				n = n.parent;
				d++;
			}
			return d;
		}

		public String toString()
		{
			return "Depth: "+depth()+", size: "+size();
		}
	}
	
	/**
	 * Node splitting algorithms selectors.
	 */
	public enum SplitterType
	{
		/**
		 * Quadratic splitting algorithm. Runs in O(n^2) time where n = maxChildren+1.
		 * Use this for R-trees that update with new data fairly often.
		 */
		QUADRATIC,
		/**
		 * NOT IMPLEMENTED.
		 * Exhaustive splitting algorithm. Runs in O(2^n) time where n = maxChildren+1.
		 * Produces a more optimized result than the quadratic algorithm
		 * at the cost of a high runtime. Use this for R-trees that update rarely
		 * and queried frequently, such as zone protection.
		 */
		EXHAUSTIVE,
	}
	
	private interface NodeSplitter
	{
		void split(Node n);
	}
	
	private class QuadraticNodeSplitter implements NodeSplitter
	{
		public void split(Node n)
		{
			if(n.size() <= maxSize) return;
			boolean isleaf = n.isLeaf();

			// Choose seeds. Would write a function for this, but it requires returning 2 objects
			BoundedObject seed1 = null, seed2 = null;
			ArrayList<? extends BoundedObject> list;
			if(isleaf)
				list = n.data;
			else
				list = n.children;

			int maxD = Integer.MIN_VALUE;
			AABB box = new AABB();
			for(int i=0; i<list.size(); i++)
				for(int j=0; j<list.size(); j++)
				{
					if(i == j) continue;
					BoundedObject n1 = list.get(i), n2 = list.get(j);
					n1.getBounds().cloneInto(box);
					box.merge(n2.getBounds());
					int d = box.getVolume() - n1.getBounds().getVolume() - n2.getBounds().getVolume();
					if(d > maxD)
					{
						maxD = d;
						seed1 = n1;
						seed2 = n2;
					}
				}
			assert(seed1 != null && seed2 != null);

			// Distribute
			Node group1 = new Node(isleaf);
			group1.box = seed1.getBounds().clone();
			Node group2 = new Node(isleaf);
			group2.box = seed2.getBounds().clone();
			if(isleaf)
				distributeLeaves(n, group1, group2);
			else
				distributeBranches(n, group1, group2);

			Node parent = n.parent;
			if(parent == null)
			{
				parent = new Node(false);
				root = parent;
			}
			else
				parent.children.remove(n);

			group1.parent = parent;
			parent.children.add(group1);
			group1.computeMBR();
			split(parent);

			group2.parent = parent;
			parent.children.add(group2);
			group2.computeMBR();
			split(parent);
		}
		
		private void distributeBranches(Node n, Node g1, Node g2)
		{
			assert(!(n.isLeaf() || g1.isLeaf() || g2.isLeaf()));

			while(!n.children.isEmpty() && g1.children.size() < maxSize - minSize + 1 &&
					g2.children.size() < maxSize - minSize + 1)
			{
				// Pick next
				int difmax = Integer.MIN_VALUE;
				int nmax_index = -1;
				for(int i=0; i<n.children.size(); i++)
				{
					Node node = n.children.get(i);
					int dif = Math.abs(node.box.expansionNeeded(g1.box) - node.box.expansionNeeded(g2.box));
					if(dif > difmax)
					{
						difmax = dif;
						nmax_index = i;
					}
				}
				assert(nmax_index != -1);

				// Distribute Entry
				Node nmax = n.children.remove(nmax_index);
				Node parent = null;

				// ... to the one with the least expansion
				int overlap1 = nmax.box.expansionNeeded(g1.box);
				int overlap2 = nmax.box.expansionNeeded(g2.box);
				if(overlap1 > overlap2) parent = g1;
				else if(overlap2 > overlap1) parent = g2;
				else
				{
					// Or the one with the lowest volume
					int vol1 = g1.box.getVolume();
					int vol2 = g2.box.getVolume();
					if(vol1 > vol2) parent = g2;
					else if(vol2 > vol1) parent = g1;
					else
					{
						// Or the one with the least items
						if(g1.children.size() < g2.children.size()) parent = g1;
						else parent = g2;
					}
				}
				assert(parent != null);
				parent.children.add(nmax);
				nmax.parent = parent;
			}

			if(!n.children.isEmpty())
			{
				Node parent = null;
				if(g1.children.size() == maxSize - minSize + 1)
					parent = g2;
				else
					parent = g1;

				for(int i=0; i<n.children.size(); i++)
				{
					parent.children.add(n.children.get(i));
					n.children.get(i).parent = parent;
				}
				n.children.clear();
			}
		}
		
		private void distributeLeaves(Node n, Node g1, Node g2)
		{
			// Same process as above; just different types.
			assert(n.isLeaf() && g1.isLeaf() && g2.isLeaf());

			while(!n.data.isEmpty() && g1.data.size() < maxSize - minSize + 1 &&
					g2.data.size() < maxSize - minSize + 1)
			{
				// Pick next
				int difmax = Integer.MIN_VALUE;
				int nmax_index = -1;
				for(int i=0; i<n.data.size(); i++)
				{
					BoundedObject node = n.data.get(i);
					int d1 = node.getBounds().expansionNeeded(g1.box);
					int d2 = node.getBounds().expansionNeeded(g2.box);
					int dif = Math.abs(d1 - d2);
					if(dif > difmax)
					{
						difmax = dif;
						nmax_index = i;
					}
				}
				assert(nmax_index != -1);

				// Distribute Entry
				BoundedObject nmax = n.data.remove(nmax_index);

				// ... to the one with the least expansion
				int overlap1 = nmax.getBounds().expansionNeeded(g1.box);
				int overlap2 = nmax.getBounds().expansionNeeded(g2.box);
				if(overlap1 > overlap2) g1.data.add(nmax);
				else if(overlap2 > overlap1) g2.data.add(nmax);
				else
				{
					int vol1 = g1.box.getVolume();
					int vol2 = g2.box.getVolume();
					if(vol1 > vol2) g2.data.add(nmax);
					else if(vol2 > vol1) g1.data.add(nmax);
					else
					{
						if(g1.data.size() < g2.data.size()) g1.data.add(nmax);
						else g2.data.add(nmax);
					}
				}
			}

			if(!n.data.isEmpty())
			{
				if(g1.data.size() == maxSize - minSize + 1)
					g2.data.addAll(n.data);
				else
					g1.data.addAll(n.data);
				n.data.clear();
			}
		}
	}

	/**
	 * Creates an R-Tree. Sets the splitting algorithm to quadratic splitting.
	 * @param minChildren Minimum children in a node.  {@code 2 <= minChildren <= maxChildren/2}
	 * @param maxChildren Maximum children in a node. Node splits at this number + 1
	 */
	public RTree(int minChildren, int maxChildren)
	{
		this(minChildren, maxChildren, SplitterType.QUADRATIC);
	}
	
	public RTree(int minChildren, int maxChildren, SplitterType splittertyp)
	{
		if(minChildren < 2 || minChildren > maxChildren/2) throw new IllegalArgumentException("2 <= minChildren <= maxChildren/2");
		
		switch(splittertyp)
		{
			case QUADRATIC:
				splitter = new QuadraticNodeSplitter();
				break;
			case EXHAUSTIVE:
				throw new UnsupportedOperationException("Not implemented yet.");
			default:
				throw new RuntimeException("Invalid node splitter");
		}
		
		this.minSize = minChildren;
		this.maxSize = maxChildren;
		root = null;
	}
	
	/**
	 * Adds items whose AABB intersects the query AABB to results
	 * @param results A collection to store the query results
	 * @param box The query... if null all items are returned
	 */
	public void query(Collection<? super BoundedObject> results, AABB box)
	{
		query(results, box, root);
	}
	private void query(Collection<? super BoundedObject> results, AABB box, Node node)
	{
		if(node == null) return;
		if(node.isLeaf())
		{
			for(int i=0; i<node.data.size(); i++)
				if(box == null || node.data.get(i).getBounds().overlaps(box))
					results.add(node.data.get(i));
		}
		else
		{
			for(int i=0; i<node.children.size(); i++)
				if(box == null || node.children.get(i).box.overlaps(box))
					query(results, box, node.children.get(i));
		}
	}

	public boolean query(Processor processor, AABB box)
	{
		return query(processor, box, root);
	}
	private boolean query(Processor processor, AABB box, Node node)
	{
		if(node == null) return true;
		if(node.isLeaf())
		{
			for(int i=0; i<node.data.size(); i++)
				if(box == null || node.data.get(i).getBounds().overlaps(box))
					if(!processor.process(node.data.get(i)))
						return false;
		}
		else
		{
			for(int i=0; i<node.children.size(); i++)
				if(box == null || node.children.get(i).box.overlaps(box))
					if(!query(processor, box, node.children.get(i)))
						return false;
		}
		
		return true;
	}

	/**
	 * Returns one item that intersects the query box, or null if nothing intersects
	 * the query box.
	 */
	public BoundedObject queryOne(AABB box)
	{
		return queryOne(box,root);
	}
	private BoundedObject queryOne(AABB box, Node node)
	{
		if(node == null) return null;
		if(node.isLeaf())
		{
			for(int i=0; i<node.data.size(); i++)
				if(node.data.get(i).getBounds().overlaps(box))
					return node.data.get(i);
			return null;
		}
		else
		{
			for(int i=0; i<node.children.size(); i++)
				if(node.children.get(i).box.overlaps(box))
				{
					BoundedObject result = queryOne(box,node.children.get(i));
					if(result != null) return result;
				}
			return null;
		}
	}

	/**
	 * Adds items whose AABB contains the specified point.
	 * @param results A collection to store the query results.
	 * @param px Point X coordinate
	 * @param py Point Y coordinate
	 * @param pz Point Z coordinate
	 */
	public void query(Collection<? super BoundedObject> results, int px, int py, int pz)
	{
		query(results, px, py, pz, root);
	}

	private void query(Collection<? super BoundedObject> results, int px, int py, int pz, Node node)
	{
		if(node == null) return;
		if(node.isLeaf())
		{
			for(int i=0; i<node.data.size(); i++)
				if(node.data.get(i).getBounds().contains(px,py,pz))
					results.add(node.data.get(i));
		}
		else
		{
			for(int i=0; i<node.children.size(); i++)
				if(node.children.get(i).box.contains(px,py,pz))
					query(results, px,py,pz, node.children.get(i));
		}
	}

	/**
	 * Returns one item that intersects the query point, or null if no items intersect that point.
	 */
	public BoundedObject queryOne(int px, int py, int pz)
	{
		return queryOne(px,py,pz,root);
	}
	private BoundedObject queryOne(int px, int py, int pz, Node node)
	{
		if(node == null) return null;
		if(node.isLeaf())
		{
			for(int i=0; i<node.data.size(); i++)
				if(node.data.get(i).getBounds().contains(px,py,pz))
					return node.data.get(i);
			return null;
		}
		else
		{
			for(int i=0; i<node.children.size(); i++)
				if(node.children.get(i).box.contains(px,py,pz))
				{
					BoundedObject result = queryOne(px,py,pz, node.children.get(i));
					if(result != null) return result;
				}
			return null;
		}
	}

	/**
	 * Removes the specified object if it is in the tree.
	 * @param o
	 */
	public void remove(BoundedObject o)
	{
		Node n = chooseLeaf(o, root);
		assert(n.isLeaf());
		if(!n.data.remove(o))
			assert(false);

		//T.E. commented out, there was no weird bug, it was my fault
		// Although this should help the memory leak, it's not too serious
//		//T.E. I added this call to remove if the data is empty due to some weird bug
//		//when removing and adding. I surmised that, without if you remove the last data point
//		// the leaf stays there with the same area, which is a memory leak as well.
//		if(n.data.isEmpty())
//			n.remove();
//		else
			n.computeMBR();
	}

	/**
	 * Inserts object o into the tree. Note that if the value of o.getAABB() changes
	 * while in the R-tree, the result is undefined.
	 * @throws NullPointerException If o == null
	 */
	public void insert(BoundedObject o)
	{
		if(o == null) throw new NullPointerException("Cannot store null object");
		if(root == null)
			root = new Node(true);

		Node n = chooseLeaf(o, root);
		assert(n.isLeaf());
		n.data.add(o);
		n.computeMBR();
		splitter.split(n);
	}

	/**
	 * Counts the number of items in the tree.
	 */
	public int count()
	{
		if(root == null) return 0;
		return count(root);
	}
	private int count(Node n)
	{
		assert(n != null);
		if(n.isLeaf())
		{
			return n.data.size();
		}
		else
		{
			int sum = 0;
			for(int i=0; i<n.children.size(); i++)
				sum += count(n.children.get(i));
			return sum;
		}
	}

	private Node chooseLeaf(BoundedObject o, Node n)
	{
		assert(n != null);
		if(n.isLeaf()) return n;
		else
		{
			AABB box = o.getBounds();

			int maxOverlap = Integer.MAX_VALUE;
			Node maxnode = null;
			for(int i=0; i<n.children.size(); i++)
			{
				int overlap = n.children.get(i).box.expansionNeeded(box);
				if((overlap < maxOverlap) 
						|| (overlap == maxOverlap 
								&& n.children.get(i).box.getVolume() < maxnode.box.getVolume()))
				{
					maxOverlap = overlap;
					maxnode = n.children.get(i);
				}
			}
			if(maxnode == null) // Not sure how this could occur
				return null;
			return chooseLeaf(o,maxnode);
		}
	}

}
