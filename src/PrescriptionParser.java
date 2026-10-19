import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phân tích văn bản đọc từ đơn thuốc thành danh sách (tên thuốc, số lượng).
 * Chịu được lỗi OCR nhỏ (sai 1-2 ký tự), không phân biệt hoa thường và dấu tiếng Việt.
 * Thuốc không có trong kho vẫn được đưa vào đơn để hệ thống báo "không có trong kho".
 */
public class PrescriptionParser {
    private static final Pattern STRENGTH = Pattern.compile("\\d+(?:[.,]\\d+)?\\s*(?:mg|mcg|g|ml|iu|%)(?![a-z])");
    private static final Pattern QTY_SL   = Pattern.compile("\\b(?:sl|so luong)\\s*[:=]?\\s*(\\d+)");
    private static final Pattern QTY_X    = Pattern.compile("\\bx\\s*(\\d+)\\b");
    private static final Pattern QTY_UNIT = Pattern.compile("\\b(\\d+)\\s*(?:vien|vi|goi|ong|chai|tuyp|hop|lo|tab|capsule|cap|v)\\b");
    private static final Pattern NUMBERING = Pattern.compile("^\\s*\\d+\\s*[.)\\-:]*\\s*");
    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\d+\\s*[.)]");
    private static final String[] DOSAGE_WORDS = {"uong", "lan/", "/lan", "/ngay", "moi lan", "sang", "trua", "chieu", "toi ", "sau an", "truoc an"};

    private final Inventory inventory;

    public PrescriptionParser(Inventory inventory) { this.inventory = inventory; }

    /** Số lượng tìm được trong một dòng, kèm vị trí bắt đầu để cắt lấy tên thuốc. */
    private static class Qty {
        final int value, start;
        final boolean fromUnit;
        Qty(int value, int start, boolean fromUnit) { this.value = value; this.start = start; this.fromUnit = fromUnit; }
    }

    public ScanResult parse(String text) {
        Prescription rx = new Prescription();
        List<String> warnings = new ArrayList<>();
        String[] lines = text == null ? new String[0] : text.split("\\r?\\n");
        String[] clean = new String[lines.length];
        for (int i = 0; i < lines.length; i++) clean[i] = removeStrength(normalize(lines[i]));
        boolean[] used = new boolean[lines.length];
        boolean[] qtyTaken = new boolean[lines.length];   // dòng có số lượng đã gán cho thuốc ở dòng dưới

        for (int i = 0; i < lines.length; i++) {
            if (used[i] || clean[i].isEmpty()) continue;
            Medicine med = findMedicine(clean[i]);
            Qty q = findQuantity(clean[i]);
            boolean dosage = hasDosageWord(clean[i]);

            if (med != null) {
                int qty;
                if (q != null) {
                    qty = q.value;
                    if (dosage && q.fromUnit)
                        warnings.add("Dòng \"" + lines[i].trim() + "\" có cả cách dùng, hãy kiểm tra lại số lượng " + med.getName() + ".");
                } else {
                    Qty next = i + 1 < lines.length ? findQuantity(clean[i + 1]) : null;
                    if (next != null && isQuantityOnly(clean[i + 1], next)) { qty = next.value; used[i + 1] = true; }
                    else {
                        // OCR đôi khi đọc cột "Số lượng" lên dòng trên (dòng tiêu đề "Thuốc điều trị ... X 20 Viên")
                        Qty prev = (i > 0 && !qtyTaken[i - 1] && !NUMBERED_LINE.matcher(clean[i - 1]).find()
                                && !hasDosageWord(clean[i - 1]) && findMedicine(clean[i - 1]) == null)
                                ? findQuantity(clean[i - 1]) : null;
                        if (prev != null) { qty = prev.value; qtyTaken[i - 1] = true; }
                        else { qty = 1; warnings.add("Không thấy số lượng của " + med.getName() + ", tạm lấy 1."); }
                    }
                }
                rx.addItem(med.getName(), qty);
            } else if (q != null && !dosage) {
                String name = extractName(clean[i], q.start);
                if (looksLikeDrugLine(name, clean[i], q)) rx.addItem(name, q.value);   // thuốc lạ: sẽ báo "không có trong kho"
            }
        }
        return new ScanResult(text == null ? "" : text, rx, warnings);
    }

    // ---------- Chuẩn hóa văn bản ----------

    static String normalize(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase();
        return t.replaceAll("[^a-z0-9/:=%.,()\\- ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String removeStrength(String s) {
        return STRENGTH.matcher(s).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static boolean hasDosageWord(String s) {
        for (String w : DOSAGE_WORDS) if ((" " + s + " ").contains(w)) return true;
        return false;
    }

    // ---------- Tìm số lượng ----------

    private static Qty findQuantity(String s) {
        Matcher m = QTY_SL.matcher(s);
        if (m.find()) return make(m, false);
        m = QTY_X.matcher(s);
        if (m.find()) return make(m, false);
        m = QTY_UNIT.matcher(s);
        if (m.find()) return make(m, true);
        return null;
    }

    private static Qty make(Matcher m, boolean fromUnit) {
        int v = Integer.parseInt(m.group(1));
        return (v <= 0 || v > 999) ? null : new Qty(v, m.start(), fromUnit);
    }

    /** Dòng chỉ chứa số lượng (ví dụ "SL: 20 viên"), không có tên thuốc nào khác phía trước. */
    private static boolean isQuantityOnly(String line, Qty q) {
        String prefix = line.substring(0, q.start).replaceAll("\\b(?:sl|so luong|x)\\b", " ").replaceAll("[^a-z]", "");
        return prefix.isEmpty();
    }

    // ---------- Tìm thuốc ----------

    private Medicine findMedicine(String line) {
        String[] words = line.split("[^a-z0-9]+");
        Medicine best = null;
        int bestScore = 0;
        for (Medicine med : inventory.getAllMedicines()) {
            String[] tokens = removeStrength(normalize(med.getName())).split("[^a-z0-9]+");
            int score = 0;
            boolean all = true;
            for (String t : tokens) {
                if (t.isEmpty()) continue;
                boolean found = false;
                for (String w : words) if (wordMatches(w, t)) { found = true; break; }
                if (!found) { all = false; break; }
                score += t.length();
            }
            if (all && score > bestScore) { best = med; bestScore = score; }
        }
        return best;
    }

    /** Hai từ coi là giống nhau nếu bằng nhau, hoặc (với từ dài) chỉ khác 1-2 ký tự do lỗi OCR. */
    private static boolean wordMatches(String w, String t) {
        if (w.equals(t)) return true;
        if (t.length() < 5 || Math.abs(w.length() - t.length()) > 2) return false;
        return levenshtein(w, t) <= (t.length() >= 8 ? 2 : 1);
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

    /**
     * Dòng có đáng tin là một thuốc chưa có trong kho không? Tránh nhận nhầm các dòng rác do OCR đọc
     * từ nền ảnh (ví dụ "Po yttt 3"): cần có số thứ tự đầu dòng ("1.", "2)") hoặc có số lượng kèm đơn vị
     * (viên, ống, tuýp...), và tên phải gồm chủ yếu là chữ cái.
     */
    private static boolean looksLikeDrugLine(String name, String cleanLine, Qty q) {
        if (name.length() < 4) return false;
        int letters = 0;
        for (char c : name.toCharArray()) if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') letters++;
        if (letters < 4 || letters < 0.8 * name.replace(" ", "").length()) return false;
        boolean numbered = NUMBERED_LINE.matcher(cleanLine).find();
        String firstWord = name.split(" ")[0];
        return numbered || (q.fromUnit && firstWord.length() >= 5);
    }

    /** Lấy tên thuốc ở đầu dòng: trước số lượng, trước cách dùng, trước dấu "(" (tên biệt dược, hoạt chất). */
    private static String extractName(String clean, int qtyStart) {
        String s = clean.substring(0, qtyStart);
        String padded = " " + s;
        int cut = s.length();
        for (String w : DOSAGE_WORDS) {
            int idx = padded.indexOf(w);
            if (idx >= 0 && idx - 1 < cut) cut = Math.max(0, idx - 1);
        }
        s = s.substring(0, cut);
        s = NUMBERING.matcher(s).replaceFirst("");
        int paren = s.indexOf('(');
        if (paren > 0) s = s.substring(0, paren);
        StringBuilder sb = new StringBuilder();
        int words = 0;
        for (String w : s.replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")) {
            if (w.isEmpty() || w.matches(".*\\d.*")) continue;      // bỏ hàm lượng, số
            if (words++ == 4) break;
            sb.append(sb.length() == 0 ? "" : " ").append(w);
        }
        s = sb.toString();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
