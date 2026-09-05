import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Translation regressions: missing strings, broken substitutions and unsafe locale declarations. */
public class LocalizationTest {
    private static final Pattern FORMAT=Pattern.compile("%(\\d+)\\$(?:0?\\d*)?([ds])");
    private static Map<String,String> read(String folder) throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        Document document=f.newDocumentBuilder().parse(Paths.get("app/src/main/res",folder,"strings.xml").toFile());
        Map<String,String> values=new TreeMap<>(); NodeList nodes=document.getDocumentElement().getChildNodes();
        for(int i=0;i<nodes.getLength();i++) {
            if(!(nodes.item(i) instanceof Element)) continue; Element element=(Element)nodes.item(i); String key=element.getAttribute("name");
            if(element.getTagName().equals("plurals")) {
                NodeList items=element.getElementsByTagName("item");
                for(int j=0;j<items.getLength();j++) { Element item=(Element)items.item(j); put(values,key+":"+item.getAttribute("quantity"),item.getTextContent()); }
            } else put(values,key,element.getTextContent());
        }
        return values;
    }
    private static void put(Map<String,String> values,String key,String value) {
        if(values.put(key,value)!=null || value.trim().isEmpty()) throw new AssertionError("Duplicate or empty translation: "+key);
    }
    private static Map<Integer,String> signature(String value) {
        Map<Integer,String> result=new TreeMap<>(); Matcher m=FORMAT.matcher(value);
        while(m.find()) result.put(Integer.parseInt(m.group(1)),m.group(2));
        return result;
    }
    public static void main(String[] args) throws Exception {
        Map<String,String> english=read("values");
        for(String folder:new String[]{"values","values-zh","values-ja"}) {
            Map<String,String> translated=read(folder);
            if(!translated.keySet().equals(english.keySet())) throw new AssertionError("Missing or extra resources in "+folder);
            for(Map.Entry<String,String> entry:translated.entrySet()) {
                String key=entry.getKey(),value=entry.getValue(); Map<Integer,String> placeholders=signature(value);
                if(!placeholders.equals(signature(english.get(key)))) throw new AssertionError("Mismatched format: "+folder+"/"+key);
                Object[] example=new Object[]{"example","example","example"};
                for(Map.Entry<Integer,String> p:placeholders.entrySet()) example[p.getKey()-1]=p.getValue().equals("d")?Integer.valueOf(2):"example";
                if(!placeholders.isEmpty()) String.format(Locale.ROOT,value,example);
                if(value.contains("。")) throw new AssertionError("Unwanted sentence punctuation: "+folder+"/"+key);
            }
        }
        if(!read("values-zh").get("author_thanks").equals("由JamieTso制作，感谢使用")) throw new AssertionError("Author's requested Chinese credit changed");
        String manifest=new String(Files.readAllBytes(Paths.get("app/src/main/AndroidManifest.xml")),java.nio.charset.StandardCharsets.UTF_8);
        if(!manifest.contains("android:localeConfig=\"@xml/locales_config\"") || !manifest.contains("android:label=\"@string/app_name\"")) throw new AssertionError("Missing system language integration");
        System.out.println("LocalizationTest: all "+english.size()+" string/plural entries complete and format-safe in English, Chinese and Japanese");
    }
}
