import local.pageturner.LanguagePolicy;
import local.pageturner.Rules;

public class LanguagePolicyTest {
    private static int checks;
    private static void expect(String wanted,String choice,String... tags) {
        checks++; String actual=LanguagePolicy.resolve(choice,tags);
        if(!wanted.equals(actual)) throw new AssertionError(wanted+" != "+actual);
    }
    private static void check(boolean value,String description) { checks++; if(!value) throw new AssertionError(description); }
    public static void main(String[] args) {
        expect("zh","system","zh-CN");
        expect("zh","system","zh-TW");
        expect("zh","system","zh-Hant-HK");
        expect("ja","system","ja-JP","en-US");
        expect("en","system","en-GB","ja-JP");
        expect("ja","system","de-DE","ja-JP","zh-CN");
        expect("en","system","fr-FR");
        expect("en","system");
        expect("en",null,(String)null);
        expect("ja","ja","zh-CN");
        expect("en","en","ja-JP");
        expect("zh","zh","en-US");
        expect("zh","zh_CN","ja-JP");
        expect("ja","invalid","ja-JP");
        expect("en","system","en-US"); // Returning to system follows the current list
        expect("ja","system","ja-JP");
        for(String label:new String[]{"次のページ"," 次ページ ","次の画像","次の写真","Next Page","下一页"}) check(Rules.nextLabel(label),"explicit next-page label");
        for(String label:new String[]{"次へ","次のステップ","次の支払い","購入する","次のページの広告"}) check(!Rules.nextLabel(label),"ambiguous or unrelated Japanese action");
        System.out.println("LanguagePolicyTest: "+checks+" language and next-button checks passed");
    }
}
