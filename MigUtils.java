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
    // Formats rencontrés dans Amplitude (CCA) :
    //   ancienne CNI    : 108735732 DU 01/08/2008 OU01<3   (n° 9 chiffres, date, centre + clé de contrôle "<n")
    //   CNI biométrique : 20180446075720884 DU 24/10/2018 A LT02   (16 à 18 chiffres)
    //   CNI sécurisée   : AA12378811 LE 04/12/2025 A DGSN          (2 lettres + 8 chiffres)
    //   récépissé       : REC1146559714 DU ..., RECEP / RECEPISSE ..., ou code biométrique LT13185I5J3R1MWC4SG1
    //   passeport       : PASS 01806249 DU ..., PASSEPORT N°20AF34168
    //   centre en tête  : CE11/115733089, OU 01/115225557, CE11 N000221681 du ...
    //   saisies libres  : "CNI N°", "N°", "DU" collé au numéro, dates 23/092003, 16021998, 12 05 2021, 05/05/10...
    private static final String REGION = "(?:CE|LT|OU|SW|NW|NO|EN|AD|SU|ES|MO)";
    private static final Pattern P_SLASH = Pattern.compile("^(" + REGION + ")\\s?O?(\\d{1,3})\\s*/\\s*([A-Z0-9]+)\\s*(.*)$");
    private static final Pattern P_DATE = Pattern.compile(
        "(?:\\b(?:DU|LE|DELIVREE?\\s+LE)\\s*)?\\b(\\d{1,2})\\s*[/.\\- ]?\\s*(\\d{2})\\s*[/.\\- ]?\\s*((?:19|20)\\d{2}|\\d{2})(?!\\d)");
    private static final Pattern P_DATE_KW = Pattern.compile("\\b(?:DU|LE)\\s*\\d");
    private static final Pattern P_CENTRE = Pattern.compile("\\b(" + REGION + ")\\s?([0O]?\\d{1,3})\\b");
    private static final Pattern P_BIO_REC = Pattern.compile("^[A-Z]{2}\\d{2,7}[A-Z][A-Z0-9]{5,}$");
    private static final Pattern P_SECURE = Pattern.compile("^A[A-Z]\\d{8}$");
    private static final Pattern P_DIGITS = Pattern.compile("^\\d{6,18}$");

    /** Analyse ZLI3 -> {type, numero, date jj/MM/aaaa, autorite, anomalie}. null si aucun numéro exploitable. */
    public static String[] idParse(String raw) {
        String t = clean(raw);
        if (t == null) return null;
        t = t.toUpperCase().replace("À", "A ").replace('º', '°');
        t = t.replaceAll("\\s*<+\\s*\\d\\b", " ");          // clé de contrôle : <6, << 9, < 5
        t = t.replaceAll("<+", " ");                          // séparateurs restants
        t = t.replaceAll("(\\d)(DU|LE)(?=\\s*\\d)", "$1 $2 "); // 102163324DU03/01/2002
        t = t.replaceAll("\\b(DU|LE)(?=\\d)", "$1 ");          // DU21/01/2010
        t = t.replaceAll("(\\d{6,})(" + REGION + "\\d{2,3})\\b", "$1 $2"); // 100724593CE02
        t = t.replaceAll("N\\s?°\\s*", " ").replaceAll("(^|\\s)N(?=\\d)", "$1 ");
        t = t.replaceAll("\\s+", " ").trim();
        String type = null, anomalie = null;
        if (t.matches("^PASS(EPORT)?\\b.*")) { type = "PASSPORT"; t = t.replaceFirst("^PASS(EPORT)?\\.?\\s*", ""); }
        else if (t.matches("^REC.*")) { type = "RECEIPT"; t = t.replaceFirst("^REC(EPISSE|EP)?\\.?\\s*", ""); }
        t = t.replaceFirst("^CNI\\.?\\s*", "").trim();
        String autorite = null;
        Matcher ms = P_SLASH.matcher(t);                      // CE11/115733089
        if (ms.matches()) { autorite = ms.group(1) + pad2(ms.group(2)); t = ms.group(3) + " " + ms.group(4); }
        // date
        String date = null; Matcher md = P_DATE.matcher(t);
        while (md.find()) {
            String d = fmtDate(md.group(1), md.group(2), md.group(3));
            if (d != null) { date = d; t = (t.substring(0, md.start()) + " " + t.substring(md.end())).trim(); break; }
        }
        if (date == null && P_DATE_KW.matcher(t).find()) anomalie = "date illisible";
        t = t.replaceAll("\\b(DELIVREE?|DU|LE)\\b", " ").replaceAll("\\s+", " ").trim();
        // numéro = premier jeton qui ressemble à un numéro (et pas à un code de centre)
        String numero = null; List<String> reste = new ArrayList<String>();
        String[] toks = t.split(" ");
        for (int i = 0; i < toks.length; i++) {
            String k = toks[i].replaceAll("[^A-Z0-9]", "");
            if (numero == null && i + 1 < toks.length && k.matches(REGION)
                    && P_BIO_REC.matcher(k + toks[i + 1].replaceAll("[^A-Z0-9]", "")).matches()) {   // "CE 02011I5I..."
                numero = k + toks[i + 1].replaceAll("[^A-Z0-9]", ""); i++; continue;
            }
            if (numero == null && i + 1 < toks.length && k.matches(REGION) && toks[i + 1].matches("[0O]?\\d{1,3}")
                    && !(i + 2 < toks.length)) { reste.add(toks[i]); continue; }
            if (numero == null && !k.matches(REGION + "[0O]?\\d{1,3}") && k.length() >= 6 && k.matches(".*\\d.*")) { numero = k; continue; }
            if (numero == null && k.matches(REGION + "\\d{2,7}[A-Z][A-Z0-9]{5,}")) { numero = k; continue; }
            reste.add(toks[i]);
        }
        if (numero == null) {                                  // "CE 02011I5I..." : centre séparé du code
            for (int i = 0; i + 1 < toks.length; i++) {
                String k = toks[i] + toks[i + 1];
                if (P_BIO_REC.matcher(k).matches()) { numero = k; reste.remove(toks[i]); reste.remove(toks[i + 1]); break; }
            }
        }
        if (numero == null) return null;
        if (type == null) {
            if (P_BIO_REC.matcher(numero).matches()) type = "RECEIPT";
            else if (P_SECURE.matcher(numero).matches() || P_DIGITS.matcher(numero).matches()) type = "NATIONAL_ID";
            else if (numero.matches("\\d{5}")) { type = "NATIONAL_ID"; anomalie = "numero court"; }
            else { type = "OTHER"; anomalie = "format inconnu"; }
        }
        // autorité : code de centre (CE04, LT13...) sinon lieu en clair (BAFANG, YDE, DGSN...)
        String r = String.join(" ", reste).replaceAll("^(A|AU|DU)\\s+", "").trim();
        if (autorite == null) {
            Matcher mc = P_CENTRE.matcher(r); String last = null;
            while (mc.find()) last = mc.group(1) + pad2(mc.group(2));
            if (last != null) autorite = last;
            else {
                String w = r.replaceAll("\\b(A|AU|DU|LE|CE)\\b", " ").replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
                if (w.matches(".*[A-Z]{3,}.*")) autorite = w.equals("YDE") ? "YAOUNDE" : w.equals("DLA") ? "DOUALA" : w;
            }
        }
        return new String[]{type, numero, date, autorite, anomalie};
    }
    private static String pad2(String d) { d = d.replace('O', '0'); return d.length() == 1 ? "0" + d : d; }
    private static String fmtDate(String j, String m, String a) {
        int jj = Integer.parseInt(j), mm = Integer.parseInt(m), aa = Integer.parseInt(a);
        if (a.length() == 2) aa += aa > 30 ? 1900 : 2000;
        if (aa < 1950 || aa > 2030 || mm < 1 || mm > 12 || jj < 1 || jj > 31) return null;
        String s = String.format("%02d/%02d/%04d", jj, mm, aa);
        return parseAmpDate(s) == null ? null : s;
    }
    public static String idType(String zli3) { String[] p = idParse(zli3); return p == null ? null : p[0]; }
    public static String idNumber(String zli3) { String[] p = idParse(zli3); return p == null ? null : p[1]; }
    public static java.util.Date idIssuedAt(String zli3) { String[] p = idParse(zli3); return p == null ? null : parseAmpDate(p[2]); }
    public static String idAuthority(String zli3) { String[] p = idParse(zli3); return p == null ? null : p[3]; }
    /** Anomalie de saisie à signaler (date illisible, numéro court, format inconnu) ; null si la valeur est propre. */
    public static String idAnomaly(String zli3) { String[] p = idParse(zli3); return p == null ? "aucun numero exploitable" : p[4]; }

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
