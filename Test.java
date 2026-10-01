package routines;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/**
 * MigUtils v2 - routines de migration Amplitude -> Fluxoon (CCA Bank).
 * Toutes les fonctions sont pures (sauf isNewGroup) : même entrée -> même sortie, ce qui rend les jobs rejouables.
 */
public class MigUtils {

    // ------------------------------------------------------------------ identifiants
    /** UUID déterministe (v3, MD5) à partir d'une clé métier : même clé -> même id, à chaque exécution. */
    public static String detId(String key) {
        if (key == null) key = "";
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
    /** Clé source employé : "CDOS|NMAT" (matricule conservé tel quel, zéros compris). */
    public static String empKey(String cdos, String nmat) { return trim(cdos) + "|" + trim(nmat); }
    /** employee_id Fluxoon d'un salarié Amplitude. Recalculable dans tous les jobs (employee, employment...). */
    public static String employeeId(String cdos, String nmat) { return detId("EMP|" + empKey(cdos, nmat)); }

    public static java.util.Date now() { return new java.util.Date(); }

    // ------------------------------------------------------------------ texte
    public static String trim(String s) { return s == null ? "" : s.trim(); }
    /** null si vide ou réduit à des caractères de remplissage (".", "-", "?", espace insécable...). */
    public static String clean(String s) {
        if (s == null) return null;
        String t = s.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
        if (t.isEmpty() || t.matches("[.\\-_?*/\\\\ ]+")) return null;
        return t;
    }
    /** Nom de famille sans civilité en tête (MME, MLLE, MR, M., DR). "EPSE ..." est conservé. */
    public static String lastName(String nom) {
        String t = clean(nom);
        if (t == null) return "";
        return t.replaceFirst("(?i)^(MME|MLLE|MADAME|MADEMOISELLE|MR|M\\.|MONSIEUR|DR)\\.?\\s+", "").trim();
    }
    public static String firstName(String pren) { String t = clean(pren); return t == null ? "" : t; }

    // ------------------------------------------------------------------ dates
    public static java.util.Date parseAmpDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String t = s.trim();
        String[] fmts = t.matches("\\d{1,2}/\\d{1,2}/\\d{2}") ? new String[]{"dd/MM/yy"} : new String[]{"dd/MM/yyyy", "yyyy-MM-dd"};
        for (String f : fmts) {
            try { SimpleDateFormat d = new SimpleDateFormat(f); d.setLenient(false); return d.parse(t); } catch (Exception e) { }
        }
        return null;
    }
    public static java.util.Date parseAmpDate(java.util.Date d) { return d; }
    public static Integer yearOf(java.util.Date d) {
        if (d == null) return null;
        Calendar c = Calendar.getInstance(); c.setTime(d); return c.get(Calendar.YEAR);
    }
    /** Dates « bouche-trou » d'Amplitude : 01/01 d'une année 1900 ou 2000, ou année hors [1900, 2100]. */
    public static boolean isFakeDate(java.util.Date d) {
        if (d == null) return true;
        Calendar c = Calendar.getInstance(); c.setTime(d);
        int y = c.get(Calendar.YEAR);
        if (y < 1900 || y > 2100) return true;
        return c.get(Calendar.DAY_OF_MONTH) == 1 && c.get(Calendar.MONTH) == 0 && (y == 1900 || y == 2000);
    }
    public static java.util.Date realDate(java.util.Date d) { return isFakeDate(d) ? null : d; }
    /** Dernière année sur 4 chiffres trouvée dans un texte ("2002", "1996-1997" -> 1997). */
    public static Integer yearFromText(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("(19|20)\\d{2}").matcher(s); Integer y = null;
        while (m.find()) y = Integer.valueOf(m.group());
        return y;
    }

    // ------------------------------------------------------------------ e-mail
    public static String extractEmail(String... fields) {
        if (fields == null) return null;
        Pattern p = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
        for (String f : fields) { if (f != null && f.indexOf('@') > 0) { Matcher m = p.matcher(f); if (m.find()) return m.group().toLowerCase(); } }
        return null;
    }

    // ------------------------------------------------------------------ téléphone (Cameroun, plan à 9 chiffres)
    /** Découpe NTEL en numéros : séparateurs "-", "/", ";", "," ou double espace ; sinon découpe par longueur. */
    public static List<String> phones(String ntel) {
        List<String> out = new ArrayList<String>();
        if (ntel == null) return out;
        for (String part : ntel.split("[-/;,]|\\s{2,}")) {
            String d = part.replaceAll("[^0-9]", "");
            while (d.length() > 0) {
                d = stripCountry(d);
                int len = (d.startsWith("6") || d.startsWith("2")) && d.length() >= 9 ? 9 : 8;
                if (d.length() < 8) break;
                String n = normalizeCmPhone(d.substring(0, Math.min(len, d.length())));
                if (n != null && !out.contains(n)) out.add(n);
                d = d.length() > len ? d.substring(len) : "";
            }
        }
        return out;
    }
    private static String stripCountry(String d) {
        if (d.startsWith("00237")) return d.substring(5);
        if (d.startsWith("237") && d.length() >= 11) return d.substring(3);
        return d;
    }
    /** Un numéro -> +237XXXXXXXXX. 9 chiffres commençant par 6 ou 2 : gardé ; 8 chiffres : 2/3 -> fixe (préfixe 2), sinon mobile (préfixe 6). */
    public static String normalizeCmPhone(String digits) {
        if (digits == null) return null;
        String d = stripCountry(digits.replaceAll("[^0-9]", ""));
        if (d.length() == 9 && (d.startsWith("6") || d.startsWith("2"))) return "+237" + d;
        if (d.length() == 8) return "+237" + ((d.startsWith("2") || d.startsWith("3")) ? "2" : "6") + d;
        return null;
    }
    public static String phonePrincipal(String ntel) { List<String> l = phones(ntel); return l.isEmpty() ? null : l.get(0); }
    /** 2e numéro s'il existe, sinon null (on ne duplique pas le numéro principal). */
    public static String phoneSecond(String ntel) { List<String> l = phones(ntel); return l.size() > 1 ? l.get(1) : null; }

    // ------------------------------------------------------------------ JSON (colonnes jsonb passées en String)
    public static String jsonEsc(String v) {
        if (v == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : v.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.toString();
    }
    /** Adresse Fluxoon {city,state,street,country,countryCode}. city = ADR1 (ville chez CCA), street = ADR2 + ADR3 (+ BP). Les e-mails sont écartés. */
    public static String jsonAddress(String adr1, String adr2, String adr3, String bpos) {
        String city = noMail(adr1);
        StringBuilder st = new StringBuilder();
        for (String s : new String[]{adr2, adr3}) { String t = noMail(s); if (t != null) { if (st.length() > 0) st.append(", "); st.append(t); } }
        String bp = noMail(bpos);
        if (bp != null) { if (st.length() > 0) st.append(" - "); st.append("BP ").append(bp); }
        if (city == null && st.length() == 0) return null;
        return "{\"city\":\"" + jsonEsc(city == null ? "" : city) + "\",\"state\":\"\",\"street\":\"" + jsonEsc(st.toString())
             + "\",\"country\":\"Cameroon\",\"countryCode\":\"CM\"}";
    }
    private static String noMail(String s) { String t = clean(s); return (t == null || t.indexOf('@') >= 0) ? null : t; }
    /** Contact d'urgence : seulement si un 2e numéro existe. */
    public static String jsonEmergency(String phone) {
        if (phone == null) return null;
        return "[{\"phone\":\"" + jsonEsc(phone) + "\",\"name\":\"\",\"relationship\":\"\"}]";
    }

    // ------------------------------------------------------------------ pièce d'identité (RHPAGENT.ZLI3)
    private static final Pattern ID = Pattern.compile(
        "^\\s*(?:C\\.?N\\.?I\\.?\\s*(?:N[°O]?\\.?)?\\s*)?(REC(?:EPISSE|ÉPISSÉ)?\\.?\\s*(?:N[°O]?\\.?)?)?\\s*([A-Z0-9][A-Z0-9/\\-]{3,})\\s*(?:DU|LE|DELIVRE(?:E)? LE)?\\s*(\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4})?\\s*(?:A|À|AU)?\\s*(.*)$",
        Pattern.CASE_INSENSITIVE);
    /** Type Fluxoon : RECEIPT si le texte commence par REC, sinon NATIONAL_ID ; null si inexploitable. */
    public static String idType(String zli3) { String[] p = idParts(zli3); return p == null ? null : p[0]; }
    public static String idNumber(String zli3) { String[] p = idParts(zli3); return p == null ? null : p[1]; }
    public static java.util.Date idIssuedAt(String zli3) { String[] p = idParts(zli3); return p == null ? null : parseAmpDate(p[2] == null ? null : p[2].replace('.', '/').replace('-', '/')); }
    public static String idAuthority(String zli3) { String[] p = idParts(zli3); if (p == null) return null; String a = clean(p[3]); return (a == null || !a.matches(".*[A-Z]{3,}.*")) ? null : a; }
    private static String[] idParts(String s) {
        String t = clean(s);
        if (t == null) return null;
        Matcher m = ID.matcher(t.toUpperCase());
        if (!m.matches() || m.group(2) == null || !m.group(2).matches(".*\\d.*")) return null;
        return new String[]{ m.group(1) != null ? "RECEIPT" : "NATIONAL_ID", m.group(2), m.group(3), m.group(4) };
    }

    // ------------------------------------------------------------------ NIU (numéro de contribuable)
    /** NIU camerounais : 1 lettre + 12 chiffres + 1 lettre (ex. P078913316391J). */
    public static boolean isNiu(String cont) { String t = clean(cont); return t != null && t.toUpperCase().matches("[A-Z]\\d{12}[A-Z]"); }

    // ------------------------------------------------------------------ comptes de paiement
    /** Référence bancaire normalisée (chiffres et lettres seulement). */
    public static String accountRef(String comp) { String t = clean(comp); return t == null ? null : t.replaceAll("[^A-Za-z0-9]", "").toUpperCase(); }

    // ------------------------------------------------------------------ rupture de groupe (flux trié)
    private static final Map<String, String> LAST = new HashMap<String, String>();
    /** true pour la 1re ligne d'un groupe dans un flux TRIÉ sur ce groupe (canal = nom libre du traitement). */
    public static boolean isNewGroup(String canal, String groupKey) {
        String prev = LAST.put(canal, groupKey);
        return prev == null || !prev.equals(groupKey);
    }
}
