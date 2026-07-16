import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

public class TestCost {
    public static void main(String[] args) {
        Path dir = Paths.get("E:/Users/frand/Documents/bomboaddons-26.1.2/run/config/bomboaddons/neu_repo/items");
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try (FileReader r = new FileReader(p.toFile())) {
                    JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                    if (obj.has("recipes")) {
                        for (JsonElement el : obj.getAsJsonArray("recipes")) {
                            JsonObject rec = el.getAsJsonObject();
                            if (rec.has("type") && rec.get("type").getAsString().equals("npc_shop")) {
                                if (rec.has("cost")) {
                                    JsonElement cost = rec.get("cost");
                                    if (!cost.isJsonArray() && !cost.isJsonPrimitive()) {
                                        System.out.println(p.getFileName() + " has cost type: " + cost.getClass().getSimpleName());
                                        System.out.println(cost);
                                    }
                                }
                            }
                        }
                    }
                } catch(Exception e){}
            });
        } catch(Exception e){}
    }
}
