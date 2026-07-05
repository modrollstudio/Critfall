package studio.modroll.critfall.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * Pure formatting for the {@code /critfall report} coverage export (PLAN §8.2.4): rows describing
 * every entity/item — whether an explicit profile or a fallback drives it, plus the effective values
 * — rendered as CSV (spreadsheet-friendly) or JSON. The command layer collects the rows from the
 * registries; this class does no registry access, so it stays JVM-testable.
 */
public final class CoverageReport {

    private static final Gson PRETTY =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** {@code source} is {@code "fallback"} or {@code "profile:<winning-id>"}. */
    public record EntityRow(
            String id,
            String source,
            int armorClass,
            int attackBonus,
            String meleeDice,
            int critRange,
            String rangedDice,
            String saveBonus) {}

    public record ItemRow(String id, String source, String dice, String modifierFrom, int critRange) {}

    private CoverageReport() {}

    public static String entitiesCsv(List<EntityRow> rows) {
        StringBuilder sb = new StringBuilder("id,source,armor_class,attack_bonus,melee_dice,crit_range,");
        sb.append("ranged_dice,save_bonus\n");
        for (EntityRow r : rows) {
            sb.append(csv(r.id()))
                    .append(',')
                    .append(csv(r.source()))
                    .append(',')
                    .append(r.armorClass())
                    .append(',')
                    .append(r.attackBonus())
                    .append(',')
                    .append(csv(r.meleeDice()))
                    .append(',')
                    .append(r.critRange())
                    .append(',')
                    .append(csv(r.rangedDice()))
                    .append(',')
                    .append(csv(r.saveBonus()))
                    .append('\n');
        }
        return sb.toString();
    }

    public static String itemsCsv(List<ItemRow> rows) {
        StringBuilder sb = new StringBuilder("id,source,dice,modifier_from,crit_range\n");
        for (ItemRow r : rows) {
            sb.append(csv(r.id()))
                    .append(',')
                    .append(csv(r.source()))
                    .append(',')
                    .append(csv(r.dice()))
                    .append(',')
                    .append(csv(r.modifierFrom()))
                    .append(',')
                    .append(r.critRange())
                    .append('\n');
        }
        return sb.toString();
    }

    public static String entitiesJson(List<EntityRow> rows) {
        JsonArray array = new JsonArray();
        for (EntityRow r : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("source", r.source());
            o.addProperty("armor_class", r.armorClass());
            o.addProperty("attack_bonus", r.attackBonus());
            o.addProperty("melee_dice", r.meleeDice());
            o.addProperty("crit_range", r.critRange());
            o.addProperty("ranged_dice", r.rangedDice());
            o.addProperty("save_bonus", r.saveBonus());
            array.add(o);
        }
        return PRETTY.toJson(array);
    }

    public static String itemsJson(List<ItemRow> rows) {
        JsonArray array = new JsonArray();
        for (ItemRow r : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("source", r.source());
            o.addProperty("dice", r.dice());
            o.addProperty("modifier_from", r.modifierFrom());
            o.addProperty("crit_range", r.critRange());
            array.add(o);
        }
        return PRETTY.toJson(array);
    }

    /** Quotes a field that would otherwise break CSV structure; doubles embedded quotes. */
    private static String csv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
