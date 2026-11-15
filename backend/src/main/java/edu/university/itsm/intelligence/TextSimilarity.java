package edu.university.itsm.intelligence;
import java.text.Normalizer;
import java.util.*;
/** Deterministic fallback to pg_trgm: Dice similarity of normalized character trigram sets. */
public final class TextSimilarity {
    private TextSimilarity() {}
    public static String normalize(String text){return text==null ? "" : Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").strip().replaceAll("\\s+"," ");}
    private static Set<String> trigrams(String text){String normalized="  "+normalize(text)+" ";Set<String> result=new HashSet<>();for(int i=0;i+3<=normalized.length();i++)result.add(normalized.substring(i,i+3));return result;}
    public static double score(String left,String right){if(normalize(left).length()<5 || normalize(right).length()<5)return 0;Set<String>a=trigrams(left),b=trigrams(right);long common=a.stream().filter(b::contains).count();return 2.0*common/(a.size()+b.size());}
    public static boolean containsPhrase(String text,String keyword){return (" "+normalize(text)+" ").contains(" "+normalize(keyword)+" ");}
}
