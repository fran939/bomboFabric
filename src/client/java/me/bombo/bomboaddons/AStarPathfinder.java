package me.bombo.bomboaddons;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AStarPathfinder {

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

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.fCost, o.fCost);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return pos.equals(node.pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }
    }

    public static void computePathAsync(Level level, BlockPos start, BlockPos end, java.util.function.Consumer<List<Vec3>> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                int maxNodes = 200000;
                PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
                Map<BlockPos, Node> allNodes = new HashMap<>();

                Node startNode = new Node(start, null, 0, Math.sqrt(start.distSqr(end)));
                openSet.add(startNode);
                allNodes.put(start, startNode);

                Node closest = startNode;
                double closestDist = startNode.hCost;

                int nodesEvaluated = 0;

                while (!openSet.isEmpty()) {
                    if (nodesEvaluated > maxNodes) {
                        callback.accept(reconstructPath(closest));
                        return;
                    }

                    Node current = openSet.poll();

                    if (current.pos.distManhattan(end) <= 2) {
                        callback.accept(reconstructPath(current));
                        return;
                    }

                    nodesEvaluated++;

                    for (BlockPos neighborPos : getNeighbors(level, current.pos)) {
                        double tentativeGCost = current.gCost + Math.sqrt(current.pos.distSqr(neighborPos)) + getWallPenalty(level, neighborPos);

                        Node neighbor = allNodes.get(neighborPos);
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
            } catch (Exception e) {
                callback.accept(null);
            }
        });
    }

    private static List<Vec3> reconstructPath(Node node) {
        List<Vec3> path = new ArrayList<>();
        Node curr = node;
        while (curr != null) {
            path.add(new Vec3(curr.pos.getX() + 0.5, curr.pos.getY() + 0.1, curr.pos.getZ() + 0.5));
            curr = curr.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private static List<BlockPos> getNeighbors(Level level, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        int[][] dirs = {
            {1,0}, {-1,0}, {0,1}, {0,-1}
        };

        for (int[] d : dirs) {
            int dx = d[0];
            int dz = d[1];
            
            // Check y, y+1, y-1, y-2
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos nextPos = pos.offset(dx, dy, dz);
                
                // If jumping up, check ceiling above current position
                if (dy > 0 && !isPassable(level, pos.above(2))) {
                    continue;
                }
                
                if (isPassable(level, nextPos) && isPassable(level, nextPos.above()) && !isPassable(level, nextPos.below())) {
                    neighbors.add(nextPos);
                    break; // Pick highest valid block in this column
                }
            }
        }
        return neighbors;
    }

    public static double getWallPenalty(Level level, BlockPos pos) {
        double penalty = 0;
        int[][] dirs = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        for (int[] d : dirs) {
            if (!isPassable(level, pos.offset(d[0], 0, d[1])) || !isPassable(level, pos.offset(d[0], 1, d[1]))) {
                penalty += 1.5; // Add penalty for each adjacent wall
            }
        }
        return penalty;
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
