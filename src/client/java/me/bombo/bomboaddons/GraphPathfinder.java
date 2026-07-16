package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GraphPathfinder {

    public static class GraphNode {
        public final String id;
        public final BlockPos pos;
        public final String name;
        public final Map<String, Double> neighbours = new HashMap<>();

        public GraphNode(String id, BlockPos pos, String name) {
            this.id = id;
            this.pos = pos;
            this.name = name;
        }
    }

    private static final Map<String, Map<String, GraphNode>> loadedGraphs = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static Map<String, GraphNode> getGraph(String islandName) {
        if (islandName == null || islandName.isEmpty()) return null;
        
        String formattedName = islandName.trim().toUpperCase().replace(" ", "_");
        if (loadedGraphs.containsKey(formattedName)) {
            return loadedGraphs.get(formattedName);
        }

        try {
            InputStream is = GraphPathfinder.class.getResourceAsStream("/island_graphs/" + formattedName + ".json");
            if (is == null) {
                loadedGraphs.put(formattedName, null);
                return null;
            }

            JsonObject json = GSON.fromJson(new InputStreamReader(is), JsonObject.class);
            Map<String, GraphNode> graph = new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String id = entry.getKey();
                JsonObject obj = entry.getValue().getAsJsonObject();
                
                String posStr = obj.get("Position").getAsString();
                String[] parts = posStr.split(":");
                BlockPos pos = new BlockPos((int) Double.parseDouble(parts[0]), (int) Double.parseDouble(parts[1]), (int) Double.parseDouble(parts[2]));
                
                String name = obj.has("Name") ? obj.get("Name").getAsString() : null;
                
                GraphNode node = new GraphNode(id, pos, name);
                
                if (obj.has("Neighbours")) {
                    JsonObject neighboursObj = obj.getAsJsonObject("Neighbours");
                    for (Map.Entry<String, JsonElement> nEntry : neighboursObj.entrySet()) {
                        node.neighbours.put(nEntry.getKey(), nEntry.getValue().getAsDouble());
                    }
                }
                
                graph.put(id, node);
            }

            loadedGraphs.put(formattedName, graph);
            return graph;
        } catch (Exception e) {
            e.printStackTrace();
            loadedGraphs.put(formattedName, null);
            return null;
        }
    }

    private static class NodeData implements Comparable<NodeData> {
        GraphNode node;
        double gCost;
        NodeData parent;

        NodeData(GraphNode node, double gCost, NodeData parent) {
            this.node = node;
            this.gCost = gCost;
            this.parent = parent;
        }

        @Override
        public int compareTo(NodeData o) {
            return Double.compare(this.gCost, o.gCost);
        }
    }

    public static void computePathAsync(String islandName, BlockPos start, BlockPos end, java.util.function.Consumer<List<Vec3>> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, GraphNode> graph = getGraph(islandName);
                if (graph == null || graph.isEmpty()) {
                    callback.accept(null);
                    return;
                }

                GraphNode startNode = getClosestNode(graph, start);
                GraphNode endNode = getClosestNode(graph, end);

                if (startNode == null || endNode == null) {
                    callback.accept(null);
                    return;
                }

                PriorityQueue<NodeData> openSet = new PriorityQueue<>();
                Map<String, NodeData> allNodes = new HashMap<>();

                NodeData startData = new NodeData(startNode, 0, null);
                openSet.add(startData);
                allNodes.put(startNode.id, startData);

                NodeData closest = startData;
                double closestDist = startNode.pos.distSqr(endNode.pos);

                while (!openSet.isEmpty()) {
                    NodeData current = openSet.poll();

                    if (current.node.id.equals(endNode.id)) {
                        callback.accept(reconstructPath(current, start, end));
                        return;
                    }

                    for (Map.Entry<String, Double> neighbourEntry : current.node.neighbours.entrySet()) {
                        GraphNode neighborNode = graph.get(neighbourEntry.getKey());
                        if (neighborNode == null) continue;

                        double tentativeGCost = current.gCost + neighbourEntry.getValue();

                        NodeData neighborData = allNodes.get(neighborNode.id);
                        if (neighborData == null) {
                            neighborData = new NodeData(neighborNode, tentativeGCost, current);
                            allNodes.put(neighborNode.id, neighborData);
                            openSet.add(neighborData);
                        } else if (tentativeGCost < neighborData.gCost) {
                            neighborData.parent = current;
                            neighborData.gCost = tentativeGCost;
                            openSet.remove(neighborData);
                            openSet.add(neighborData);
                        }

                        double distToTarget = neighborNode.pos.distSqr(endNode.pos);
                        if (distToTarget < closestDist) {
                            closestDist = distToTarget;
                            closest = neighborData;
                        }
                    }
                }

                callback.accept(reconstructPath(closest, start, end));
            } catch (Exception e) {
                e.printStackTrace();
                callback.accept(null);
            }
        });
    }

    private static GraphNode getClosestNode(Map<String, GraphNode> graph, BlockPos pos) {
        GraphNode closest = null;
        double minDistance = Double.MAX_VALUE;

        for (GraphNode node : graph.values()) {
            double dist = node.pos.distSqr(pos);
            if (dist < minDistance) {
                minDistance = dist;
                closest = node;
            }
        }
        return closest;
    }

    private static List<Vec3> reconstructPath(NodeData node, BlockPos actualStart, BlockPos actualEnd) {
        List<Vec3> path = new ArrayList<>();
        
        path.add(new Vec3(actualEnd.getX() + 0.5, actualEnd.getY() + 0.1, actualEnd.getZ() + 0.5));

        NodeData curr = node;
        while (curr != null) {
            path.add(new Vec3(curr.node.pos.getX() + 0.5, curr.node.pos.getY() + 0.1, curr.node.pos.getZ() + 0.5));
            curr = curr.parent;
        }

        path.add(new Vec3(actualStart.getX() + 0.5, actualStart.getY() + 0.1, actualStart.getZ() + 0.5));

        Collections.reverse(path);
        return path;
    }
}
