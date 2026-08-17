package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class AStarPathfinder {
   public static void computePathAsync(Level level, BlockPos start, BlockPos end, Consumer<List<Vec3>> callback) {
      CompletableFuture.runAsync(() -> {
         try {
            int maxNodes = 200000;
            PriorityQueue<Node> openSet = new PriorityQueue(Comparator.comparingDouble((Node n) -> n.fCost));
            Map<BlockPos, Node> allNodes = new HashMap();
            Node startNode = new Node(start, (Node)null, (double)0.0F, Math.sqrt(start.distSqr(end)));
            openSet.add(startNode);
            allNodes.put(start, startNode);
            Node closest = startNode;
            double closestDist = startNode.hCost;
            int nodesEvaluated = 0;

            while(!openSet.isEmpty()) {
               if (nodesEvaluated > maxNodes) {
                  callback.accept(reconstructPath(closest));
                  return;
               }

               Node current = (Node)openSet.poll();
               if (current.pos.distManhattan(end) <= 2) {
                  callback.accept(reconstructPath(current));
                  return;
               }

               ++nodesEvaluated;

               for(BlockPos neighborPos : getNeighbors(level, current.pos)) {
                  double tentativeGCost = current.gCost + Math.sqrt(current.pos.distSqr(neighborPos)) + getWallPenalty(level, neighborPos);
                  Node neighbor = (Node)allNodes.get(neighborPos);
                  if (neighbor == null) {
                     neighbor = new Node(neighborPos, current, tentativeGCost, Math.sqrt(neighborPos.distSqr(end)));
                     allNodes.put(neighborPos, neighbor);
                     openSet.add(neighbor);
                  } else if (tentativeGCost < neighbor.gCost) {
                     neighbor.parent = current;
                     neighbor.gCost = tentativeGCost;
                     neighbor.fCost = neighbor.gCost + neighbor.hCost;
                     openSet.remove(neighbor);
                     openSet.add(neighbor);
                  }

                  if (neighbor.hCost < closestDist) {
                     closestDist = neighbor.hCost;
                     closest = neighbor;
                  }
               }
            }

            callback.accept(reconstructPath(closest));
         } catch (Exception var18) {
            callback.accept(null);
         }

      });
   }

   private static List<Vec3> reconstructPath(Node node) {
      List<Vec3> path = new ArrayList();

      for(Node curr = node; curr != null; curr = curr.parent) {
         path.add(new Vec3((double)curr.pos.getX() + (double)0.5F, (double)curr.pos.getY() + 0.1, (double)curr.pos.getZ() + (double)0.5F));
      }

      Collections.reverse(path);
      return path;
   }

   private static List<BlockPos> getNeighbors(Level level, BlockPos pos) {
      List<BlockPos> neighbors = new ArrayList();
      int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

      for(int[] d : dirs) {
         int dx = d[0];
         int dz = d[1];

         for(int dy = 1; dy >= -2; --dy) {
            BlockPos nextPos = pos.offset(dx, dy, dz);
            if ((dy <= 0 || isPassable(level, pos.above(2))) && isPassable(level, nextPos) && isPassable(level, nextPos.above()) && !isPassable(level, nextPos.below())) {
               neighbors.add(nextPos);
               break;
            }
         }
      }

      return neighbors;
   }

   public static double getWallPenalty(Level level, BlockPos pos) {
      double penalty = (double)0.0F;
      int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

      for(int[] d : dirs) {
         if (!isPassable(level, pos.offset(d[0], 0, d[1])) || !isPassable(level, pos.offset(d[0], 1, d[1]))) {
            ++penalty;
         }
      }

      return penalty;
   }

   private static boolean isPassable(Level level, BlockPos pos) {
      return !level.isLoaded(pos) ? false : level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   public static class Node implements Comparable<Node> {
      public final BlockPos pos;
      public Node parent;
      public double gCost;
      public double hCost;
      public double fCost;

      public Node(BlockPos pos) {
         this.pos = pos;
         this.gCost = Double.MAX_VALUE;
         this.fCost = Double.MAX_VALUE;
      }

      public Node(BlockPos pos, Node parent, double gCost, double hCost) {
         this.pos = pos;
         this.parent = parent;
         this.gCost = gCost;
         this.hCost = hCost;
         this.fCost = gCost + hCost;
      }

      public int compareTo(Node o) {
         return Double.compare(this.fCost, o.fCost);
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            Node node = (Node)o;
            return this.pos.equals(node.pos);
         } else {
            return false;
         }
      }

      public int hashCode() {
         return this.pos.hashCode();
      }
   }
}
