package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class GraphPathfinder {
   private static final Map<String, Map<String, GraphNode>> loadedGraphs = new HashMap();
   private static final Gson GSON = new Gson();

   public static Map<String, GraphNode> getGraph(String islandName) {
      if (islandName != null && !islandName.isEmpty()) {
         String formattedName = islandName.trim().toUpperCase().replace(" ", "_");
         if (loadedGraphs.containsKey(formattedName)) {
            return (Map)loadedGraphs.get(formattedName);
         } else {
            try {
               InputStream is = GraphPathfinder.class.getResourceAsStream("/island_graphs/" + formattedName + ".json");
               if (is == null) {
                  loadedGraphs.put(formattedName, null);
                  return null;
               } else {
                  JsonObject json = (JsonObject)GSON.fromJson(new InputStreamReader(is), JsonObject.class);
                  Map<String, GraphNode> graph = new HashMap();

                  for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
                     String id = (String)entry.getKey();
                     JsonObject obj = ((JsonElement)entry.getValue()).getAsJsonObject();
                     String posStr = obj.get("Position").getAsString();
                     String[] parts = posStr.split(":");
                     BlockPos pos = new BlockPos((int)Double.parseDouble(parts[0]), (int)Double.parseDouble(parts[1]), (int)Double.parseDouble(parts[2]));
                     String name = obj.has("Name") ? obj.get("Name").getAsString() : null;
                     GraphNode node = new GraphNode(id, pos, name);
                     if (obj.has("Neighbours")) {
                        JsonObject neighboursObj = obj.getAsJsonObject("Neighbours");

                        for(Map.Entry<String, JsonElement> nEntry : neighboursObj.entrySet()) {
                           node.neighbours.put((String)nEntry.getKey(), ((JsonElement)nEntry.getValue()).getAsDouble());
                        }
                     }

                     graph.put(id, node);
                  }

                  loadedGraphs.put(formattedName, graph);
                  return graph;
               }
            } catch (Exception e) {
               e.printStackTrace();
               loadedGraphs.put(formattedName, null);
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static void computePathAsync(String islandName, BlockPos start, BlockPos end, Consumer<List<Vec3>> callback) {
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

            PriorityQueue<NodeData> openSet = new PriorityQueue();
            Map<String, NodeData> allNodes = new HashMap();
            NodeData startData = new NodeData(startNode, (double)0.0F, (NodeData)null);
            openSet.add(startData);
            allNodes.put(startNode.id, startData);
            NodeData closest = startData;
            double closestDist = startNode.pos.distSqr(endNode.pos);

            while(!openSet.isEmpty()) {
               NodeData current = (NodeData)openSet.poll();
               if (current.node.id.equals(endNode.id)) {
                  callback.accept(reconstructPath(current, start, end));
                  return;
               }

               for(Map.Entry<String, Double> neighbourEntry : current.node.neighbours.entrySet()) {
                  GraphNode neighborNode = (GraphNode)graph.get(neighbourEntry.getKey());
                  if (neighborNode != null) {
                     double tentativeGCost = current.gCost + (Double)neighbourEntry.getValue();
                     NodeData neighborData = (NodeData)allNodes.get(neighborNode.id);
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

      for(GraphNode node : graph.values()) {
         double dist = node.pos.distSqr(pos);
         if (dist < minDistance) {
            minDistance = dist;
            closest = node;
         }
      }

      return closest;
   }

   private static List<Vec3> reconstructPath(NodeData node, BlockPos actualStart, BlockPos actualEnd) {
      List<Vec3> path = new ArrayList();
      path.add(new Vec3((double)actualEnd.getX() + (double)0.5F, (double)actualEnd.getY() + 0.1, (double)actualEnd.getZ() + (double)0.5F));

      for(NodeData curr = node; curr != null; curr = curr.parent) {
         path.add(new Vec3((double)curr.node.pos.getX() + (double)0.5F, (double)curr.node.pos.getY() + 0.1, (double)curr.node.pos.getZ() + (double)0.5F));
      }

      path.add(new Vec3((double)actualStart.getX() + (double)0.5F, (double)actualStart.getY() + 0.1, (double)actualStart.getZ() + (double)0.5F));
      Collections.reverse(path);
      return path;
   }

   public static class GraphNode {
      public final String id;
      public final BlockPos pos;
      public final String name;
      public final Map<String, Double> neighbours = new HashMap();

      public GraphNode(String id, BlockPos pos, String name) {
         this.id = id;
         this.pos = pos;
         this.name = name;
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

      public int compareTo(NodeData o) {
         return Double.compare(this.gCost, o.gCost);
      }
   }
}
